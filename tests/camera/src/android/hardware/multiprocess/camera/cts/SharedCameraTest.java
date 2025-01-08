/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.multiprocess.camera.cts;

import static junit.framework.Assert.*;

import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.cts.Camera2ParameterizedTestCase;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** Tests for shared camera access where multiple clients can access a camera. */
@RunWith(Parameterized.class)
public final class SharedCameraTest extends Camera2ParameterizedTestCase {
    private static final String TAG = "SharedCameraTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int SETUP_TIMEOUT = 5000; // Remote camera setup timeout (ms).
    private static final int WAIT_TIME = 3000; // Time to wait for process to launch (ms).
    private static final int EVICTION_TIMEOUT = 1000; // Remote camera eviction timeout (ms).
    private ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    private int mProcessPid = -1;
    private Context mContext;
    private ActivityManager mActivityManager;
    private String mCameraId;
    private Messenger mRemoteMessenger;
    private ResultReceiver mResultReceiver;
    protected HashMap<String, StaticMetadata> mAllStaticInfo;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Load validation jni on initialization. */
    static {
        System.loadLibrary("ctscamera2_jni");
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = InstrumentationRegistry.getTargetContext();
        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * <p>Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().toString());

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(mContext);
        mErrorServiceConnection.start();

        mResultReceiver = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == Activity.RESULT_OK) {
                    mRemoteMessenger = (Messenger) resultData.getParcelable(
                            TestConstants.EXTRA_REMOTE_MESSENGER);
                }
            }
        };

        startRemoteProcess(SharedCameraActivity.class, "SharedCameraActivityProcess");
        mAllStaticInfo = new HashMap<String, StaticMetadata>();
        List<String> hiddenPhysicalIds = new ArrayList<>();
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        for (String cameraId : cameraIdsUnderTest) {
            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(cameraId);
            StaticMetadata staticMetadata =
                    new StaticMetadata(props, CheckLevel.ASSERT, /*collector*/ null);
            mAllStaticInfo.put(cameraId, staticMetadata);

            for (String physicalId : props.getPhysicalCameraIds()) {
                if (!Arrays.asList(cameraIdsUnderTest).contains(physicalId)
                        && !hiddenPhysicalIds.contains(physicalId)) {
                    hiddenPhysicalIds.add(physicalId);
                    props = mCameraManager.getCameraCharacteristics(physicalId);
                    staticMetadata =
                            new StaticMetadata(
                                    mCameraManager.getCameraCharacteristics(physicalId),
                                    CheckLevel.ASSERT, /*collector*/
                                    null);
                    mAllStaticInfo.put(physicalId, staticMetadata);
                }
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mProcessPid != -1) {
            android.os.Process.killProcess(mProcessPid);
            mProcessPid = -1;
            Thread.sleep(WAIT_TIME);
        }
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
        mContext = null;
        mActivityManager = null;
        super.tearDown();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testCameraDeviceSharingSupported() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mAllStaticInfo.get(mCameraId).sharedSessionConfigurationPresent()) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            assertTrue("Camera device sharing is only supported for system cameras. Camera "
                    + mCameraId + " is not a system camera.", mAdoptShellPerm);
            assertTrue("Camera characteristic SHARED_SESSION_CONFIGURATION is present and"
                    + " isCameraDeviceSharingSupported should return true for camera id "
                    + mCameraId, mCameraManager.isCameraDeviceSharingSupported(mCameraId));
            Message msg = Message.obtain(null, TestConstants.OP_OPEN_CAMERA_SHARED);
            msg.getData().putString(TestConstants.EXTRA_CAMERA_ID, mCameraId);
            boolean remoteExceptionHit = false;
            try {
                mRemoteMessenger.send(msg);
            } catch (RemoteException e) {
                remoteExceptionHit = true;
            }
            assertFalse("Error in sending open camera command to SharedCameraActivity",
                    remoteExceptionHit);
            List<ErrorLoggingService.LogEvent> events = mErrorServiceConnection.getLog(
                    SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_CONNECT_SHARED_PRIMARY);
            assertNotNull("Camera device not connected in remote process!", events);
            TestUtils.assertOnly(TestConstants.EVENT_CAMERA_CONNECT_SHARED_PRIMARY, events);
            boolean[] outBoolean = new boolean[1];
            assertTrue("testIsCameraDeviceSharingSupportedNative fail, see log for details",
                    testIsCameraDeviceSharingSupportedNative(mCameraId, outBoolean));
            assertTrue("Camera characteristic SHARED_SESSION_CONFIGURATION is present and"
                    + " isCameraDeviceSharingSupported should return true for camera id "
                    + mCameraId, outBoolean[0]);
            long nativeSharedTest = testInitAndOpenSharedCameraNative(mCameraId,
                    /*isPrimaryClient*/ false);
            assertTrue("testInitAndOpenSharedCameraNative fail, see log for details",
                    nativeSharedTest != 0);
            assertTrue("testDeInitAndCloseSharedCameraNative fail, see log for details",
                    testDeInitAndCloseSharedCameraNative(nativeSharedTest));
            // Verify that attempting to open the camera didn't cause anything weird to happen in
            // the other process.
            List<ErrorLoggingService.LogEvent> eventList2 = null;
            boolean timeoutExceptionHit = false;
            try {
                eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
            } catch (TimeoutException e) {
                timeoutExceptionHit = true;
            }
            TestUtils.assertNone("SharedCameraActivity received unexpected events: ", eventList2);
            assertTrue("SharedCameraActivity error service exited early", timeoutExceptionHit);
            msg = Message.obtain(null, TestConstants.OP_CLOSE_CAMERA);
            try {
                mRemoteMessenger.send(msg);
            } catch (RemoteException e) {
                remoteExceptionHit = true;
            }
            assertFalse("Error in sending close camera command to SharedCameraActivity",
                    remoteExceptionHit);
            events = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                    TestConstants.EVENT_CAMERA_CLOSED);
            assertNotNull("Camera device still connected in remote process!", events);
            TestUtils.assertOnly(TestConstants.EVENT_CAMERA_CLOSED, events);
        }
    }

    /**
     * Start an activity of the given class running in a remote process with the given name.
     *
     * @param klass the class of the {@link android.app.Activity} to start.
     * @param processName the remote activity name.
     * @throws InterruptedException
     */
    public void startRemoteProcess(java.lang.Class<?> klass, String processName)
            throws InterruptedException {
        String cameraActivityName = mContext.getPackageName() + ":" + processName;
        // Ensure no running activity process with same name
        List<ActivityManager.RunningAppProcessInfo> list =
                mActivityManager.getRunningAppProcesses();
        assertEquals("Activity " + cameraActivityName + " already running.", -1,
                TestUtils.getPid(cameraActivityName, list));
        Intent activityIntent = new Intent(mContext, klass);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra(TestConstants.EXTRA_RESULT_RECEIVER, mResultReceiver);
        mContext.startActivity(activityIntent);
        Thread.sleep(WAIT_TIME);

        // Fail if activity isn't running
        list = mActivityManager.getRunningAppProcesses();
        mProcessPid = TestUtils.getPid(cameraActivityName, list);
        assertTrue("Activity " + cameraActivityName + " not found in list of running app "
                + "processes.", -1 != mProcessPid);
    }

    private static native boolean testIsCameraDeviceSharingSupportedNative(String cameraId,
            boolean[] outResult);
    private static native long testInitAndOpenSharedCameraNative(String cameraId,
            boolean primaryClient);
    private static native boolean testDeInitAndCloseSharedCameraNative(long sharedTestContext);
}
