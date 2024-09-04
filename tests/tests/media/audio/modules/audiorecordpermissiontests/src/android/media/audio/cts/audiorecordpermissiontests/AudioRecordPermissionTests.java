/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests;

import static android.media.audio.cts.audiorecordpermissiontests.common.ActionsKt.*;
import static android.app.AppOpsManager.OP_RECORD_AUDIO;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.media.mediatestutils.TestUtils.getFutureForIntent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.AppOpsManager;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.audio.cts.audiorecordpermissiontests.common.IAttrProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@AppModeFull(reason = "Test requires intents between multiple apps")
@RunWith(AndroidJUnit4.class)
public class AudioRecordPermissionTests extends StsExtraBusinessLogicTestCase {
    static final String TAG = "AudioRecordPermissionTests";
    // Keep in sync with test apps
    static final String API_34_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi34";
    // Behavior changes with targetSdk >= 34, so test both cases
    static final String API_33_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi33";
    static final String API_34_NO_CAP_PACKAGE =
            "android.media.audio.cts.CtsRecordServiceApi34NoCap";

    static final String SERVICE_NAME = ".RecordService";

    static final int FUTURE_WAIT_SECS = 8;
    static final int FALSE_NEG_SECS = 5;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    // Used in teardown
    private Set<String> mServiceStartedPackages = new HashSet<>();
    private Set<String> mActivityStartedPackages = new HashSet<>();

    @Before
    public void setup() throws Exception {
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
    }

    @After
    public void teardown() throws Exception {
        // Clean up any left-over activities, services
        for (var pack : Set.copyOf(mActivityStartedPackages)) {
            Log.i(TAG, "Stopping  leftover activity: " + pack);
            stopActivity(pack);
        }
        for (var pack : Set.copyOf(mServiceStartedPackages)) {
            Log.i(TAG, "Stopping  leftover service : " + pack);
            stopService(pack);
        }
    }

    @Test
    public void testRecordAudioNoRuntimePermission_fails() throws Exception {
        // The native audio record doesn't transmit security exceptions, it just fails to build
        assertThrows(UnsupportedOperationException.class, this::buildRecord);
    }

    @Test
    public void testStartRecordTop_isNotSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        startActivity(TEST_PACKAGE);
        // TODO(b/297259825) we never started recording unsilenced, due to avd sometime
        // providing only silenced mic data.
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
    }


    @Test
    public void testStartRecordForegroundServiceWithMicCapabilities_isNotSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        startForeground(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
    }

    @Test
    public void testStartRecordForegroundServiceWithoutMicCapabilities_isSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_NO_CAP_PACKAGE;
        startForeground(TEST_PACKAGE);
        assertFalse(startServiceRecording(TEST_PACKAGE));
        assertFalse(getOpState(TEST_PACKAGE));
    }

    // Verify our custom pre-34 behavior doesn't incorrectly permit too many bypasses
    @Test
    public void testStartRecordForegroundServiceWithoutMicCapabilities_whenApi33_isSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_33_PACKAGE;
        startForeground(TEST_PACKAGE);
        assertFalse(startServiceRecording(TEST_PACKAGE));
        assertFalse(getOpState(TEST_PACKAGE));
    }

    @Test
    public void testStartRecordWhenTopSleeping_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        startActivity(TEST_PACKAGE);
        try {
            // Move out of TOP to TOP_SLEEPING
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_SLEEP");
            // TODO(b/355497694), these should be false, seems currently broken
            assumeTrue(startServiceRecording(TEST_PACKAGE));
            assertTrue(getOpState(TEST_PACKAGE));
        } finally {
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_WAKEUP");
            SystemUtil.runShellCommand(mInstrumentation, "wm dismiss-keyguard");
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToBackground_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        // TODO(b/297259825) we never started recording unsilenced, due to avd sometime
        // providing only silenced mic data.
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        assertFalse(getOpState(TEST_PACKAGE));
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToSleep_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        try {
            // Move out of TOP to TOP_SLEEPING
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_SLEEP");
            // Future completes when silenced. If not, timeout and throw
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
            assertFalse(getOpState(TEST_PACKAGE));
        } finally {
            // Wait for unsilence after return to TOP
            final var receiveFuture = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_AUDIO);
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_WAKEUP");
            SystemUtil.runShellCommand(mInstrumentation, "wm dismiss-keyguard");
            receiveFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithMicCapabilities_isNotSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a fgs with mic caps
        startActivity(TEST_PACKAGE);
        startForeground(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
        assertTrue(getOpState(TEST_PACKAGE));
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithoutMicCapabilities_isSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_NO_CAP_PACKAGE;
        // Start an activity, then start recording in a fgs WITHOUT mic caps
        startActivity(TEST_PACKAGE);
        startForeground(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future is completed when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        assertFalse(getOpState(TEST_PACKAGE));
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testIfTargetPre34_MovingFromTopToBackground_isNotSilenced() throws Exception {
        final var TEST_PACKAGE = API_33_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        assertTrue(getOpState(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
        assertTrue(getOpState(TEST_PACKAGE));
    }


    @Test
    public void testStartRecording_whenBottomMissingCapabilities_isSilenced() throws Exception {
        final var ATTRIBUTED_PACKAGE = API_34_NO_CAP_PACKAGE;
        final var RECORDING_PACKAGE = API_34_PACKAGE;

        // Missing caps still
        startForeground(ATTRIBUTED_PACKAGE);
        final var attr = getAttributionProvider(ATTRIBUTED_PACKAGE);

        // recording package should always have caps since TOP
        startActivity(RECORDING_PACKAGE);

        // expect silence, since the attributed package lacks caps
        assertFalse(startServiceRecording(RECORDING_PACKAGE, attr));
        assertFalse(getOpState(RECORDING_PACKAGE));
        assertFalse(getOpState(ATTRIBUTED_PACKAGE));
    }

    @Test
    public void testStartRecording_whenTopMissingCapabilities_isSilenced() throws Exception {
        final var ATTRIBUTED_PACKAGE = API_34_PACKAGE;
        final var RECORDING_PACKAGE = API_34_NO_CAP_PACKAGE;

        startForeground(ATTRIBUTED_PACKAGE);
        final var attr = getAttributionProvider(ATTRIBUTED_PACKAGE);

        // No caps for recording package
        startForeground(RECORDING_PACKAGE);
        assertFalse(startServiceRecording(RECORDING_PACKAGE, attr));
        assertFalse(getOpState(RECORDING_PACKAGE));
        assertFalse(getOpState(ATTRIBUTED_PACKAGE));
    }

    @Test
    public void testBottomOfChainLosesCapabilities_isSilenced() throws Exception {
        final var ATTRIBUTED_PACKAGE = API_34_PACKAGE;
        final var RECORDING_PACKAGE = API_34_NO_CAP_PACKAGE;
        // Bottom of chain starts with caps
        startForeground(ATTRIBUTED_PACKAGE);
        final var attr = getAttributionProvider(ATTRIBUTED_PACKAGE);

        // recording package should always have caps since TOP
        startActivity(RECORDING_PACKAGE);
        assumeTrue(startServiceRecording(RECORDING_PACKAGE, attr));
        assertTrue(getOpState(RECORDING_PACKAGE));
        // TODO(b/355499272) op state not correctly reported for bottom of chain
        // assertTrue(getOpState(ATTRIBUTED_PACKAGE));

        final var silenceFuture =
                getFutureForIntent(mContext, RECORDING_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // remove capabilities from bottom of chain
        mContext.startService(getIntentForAction(ATTRIBUTED_PACKAGE, ACTION_STOP_FOREGROUND));

        // Chain loses rights, so we should go silent
        silenceFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        assertFalse(getOpState(RECORDING_PACKAGE));
        assertFalse(getOpState(ATTRIBUTED_PACKAGE));
    }

    @Test
    public void testTopOfChainLosesCapabilities_isSilenced() throws Exception {
        final var ATTRIBUTED_PACKAGE = API_34_PACKAGE;
        final var RECORDING_PACKAGE = API_34_NO_CAP_PACKAGE;

        startForeground(ATTRIBUTED_PACKAGE);

        final var attr = getAttributionProvider(ATTRIBUTED_PACKAGE);

        startActivity(RECORDING_PACKAGE);
        assumeTrue(startServiceRecording(RECORDING_PACKAGE, attr));
        assertTrue(getOpState(RECORDING_PACKAGE));
        // assertTrue(getOpState(ATTRIBUTED_PACKAGE));

        final var silenceFuture =
                getFutureForIntent(mContext, RECORDING_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // remove capabilities from top of chain
        stopActivity(RECORDING_PACKAGE);

        // Chain loses rights, so we should go silent
        silenceFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        assertFalse(getOpState(RECORDING_PACKAGE));
        assertFalse(getOpState(ATTRIBUTED_PACKAGE));

    }

    private IBinder getAttributionProvider(String packageName) throws Exception {
        final var attrFuture = getFutureForIntent(mContext, packageName + ACTION_SEND_ATTRIBUTION);
        mContext.startService(getIntentForAction(packageName, ACTION_REQUEST_ATTRIBUTION));
        mServiceStartedPackages.add(packageName);
        var provider = attrFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
                .getExtras()
                .getBinder(EXTRA_ATTRIBUTION);
        assertEquals(provider.getInterfaceDescriptor(), IAttrProvider.DESCRIPTOR);
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        return provider;
    }

    private void buildRecord() throws Exception {
        new AudioRecord.Builder()
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setSampleRate(48000)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                .build();
    }

    private void startForeground(String packageName) throws Exception {
        // To get around background foreground-service-launch restrictions, the app has to be
        // visible to launch an fgs: so temporarily make it TOP
        final boolean shouldLaunch =  !mActivityStartedPackages.contains(packageName);
        if (shouldLaunch) startActivity(packageName);
        mContext.startService(getIntentForAction(packageName, ACTION_START_FOREGROUND));
        mServiceStartedPackages.add(packageName);
        // We have to wait until the app is actually running to mark it freezer ineligible
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        if (shouldLaunch) stopActivity(packageName);
    }

    private void startActivity(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_ACTIVITY_STARTED);
        final var intent =
                new Intent(Intent.ACTION_MAIN)
                        .setClassName(packageName, packageName + ".SimpleActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getTargetContext().startActivity(intent);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        mActivityStartedPackages.add(packageName);
    }

    private void stopActivity(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_ACTIVITY_FINISHED);
        mContext.sendBroadcast(
                new Intent(packageName + ACTION_FINISH_ACTIVITY).setPackage(packageName));
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mActivityStartedPackages.remove(packageName);
    }

    private boolean startServiceRecording(String packageName) throws Exception {
        return startServiceRecording(packageName, null);
    }

    // return true iff track starts unsilenced
    private boolean startServiceRecording(String packageName, IBinder attrProvider)
            throws Exception {
        final var future =
                getFutureForIntent(
                        mContext,
                        List.of(
                                packageName + ACTION_BEGAN_RECEIVE_AUDIO,
                                packageName + ACTION_BEGAN_RECEIVE_SILENCE),
                        x -> true);

        final Intent intent = getIntentForAction(packageName, ACTION_START_RECORD);
        final var extras = new Bundle();
        extras.putBinder(EXTRA_ATTRIBUTION, attrProvider);
        if (attrProvider != null) intent.putExtras(extras);

        mContext.startService(intent);
        final var result =
                future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
                        .getAction()
                        .equals(packageName + ACTION_BEGAN_RECEIVE_AUDIO);
        mServiceStartedPackages.add(packageName);
        // We have to wait until the app is actually running to mark it freezer ineligible
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        return result;
    }

    private boolean getOpState(String packageName) throws Exception {
        final var uid = mContext.getPackageManager().getPackageUid(packageName, /* flags= */ 0);
        return runWithShellPermissionIdentity(() ->mContext.getSystemService(AppOpsManager.class)
                .isOperationActive(OP_RECORD_AUDIO, uid, packageName));
    }

    private Future makeFuture(String action) {
        return getFutureForIntent(mContext, action);
    }

    private Intent getIntentForAction(String packageName, String action) {
        return new Intent()
                .setClassName(packageName, packageName + SERVICE_NAME)
                .setAction(packageName + action);
    }

    private void stopService(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_TEARDOWN_FINISHED);
        mContext.startService(getIntentForAction(packageName, ACTION_TEARDOWN));
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mServiceStartedPackages.remove(packageName);
        // Just in case
        mInstrumentation
                .getTargetContext()
                .stopService(new Intent().setClassName(packageName, packageName + SERVICE_NAME));
    }
}
