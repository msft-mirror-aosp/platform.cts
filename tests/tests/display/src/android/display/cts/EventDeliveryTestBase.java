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

package android.display.cts;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;

import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Messenger;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.server.wm.MultiDisplayTestBase;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public abstract class EventDeliveryTestBase extends MultiDisplayTestBase {
    protected static final int MESSAGE_LAUNCHED = 1;
    protected static final int MESSAGE_CALLBACK = 2;

    protected static final long EVENT_TIMEOUT_MSEC = 100;
    protected static final long TEST_FAILURE_TIMEOUT_MSEC = 10000;

    private static final String TEST_MESSENGER = "MESSENGER";

    protected DisplayManager mDisplayManager;
    private ActivityManager mActivityManager;
    private ActivityManager.OnUidImportanceListener mUidImportanceListener;
    protected CountDownLatch mLatchActivityLaunch;
    private CountDownLatch mLatchActivityCached;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Messenger mMessenger;
    protected int mPid;
    protected int mUid;

    protected abstract String getTag();

    protected abstract Handler getHandler(Looper looper);

    protected abstract String getTestPackage();

    protected abstract String getTestActivity();

    protected abstract void putExtra(Intent intent);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mLatchActivityLaunch = new CountDownLatch(1);
        mLatchActivityCached = new CountDownLatch(1);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mUidImportanceListener =
                (uid, importance) -> {
                    if (uid == mUid && importance == IMPORTANCE_CACHED) {
                        Log.d(getTag(), "Listener " + uid + " becomes " + importance);
                        mLatchActivityCached.countDown();
                    }
                };
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mActivityManager.addOnUidImportanceListener(
                                mUidImportanceListener, IMPORTANCE_CACHED));
        mHandlerThread = new HandlerThread("handler");
        mHandlerThread.start();
        mHandler = getHandler(mHandlerThread.getLooper());
        mMessenger = new Messenger(mHandler);
        mPid = 0;
    }

    @After
    public void tearDown() throws Exception {
        mActivityManager.removeOnUidImportanceListener(mUidImportanceListener);
        mHandlerThread.quitSafely();
        SystemUtil.runShellCommand(mInstrumentation, "am force-stop " + getTestPackage());
    }

    /** Launch the test activity that would listen to events. Return its process ID. */
    protected int launchTestActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(getTestPackage(), getTestActivity());
        intent.putExtra(TEST_MESSENGER, mMessenger);
        putExtra(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SystemUtil.runWithShellPermissionIdentity(
                () -> TestApis.activities().startActivity(intent),
                android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);
        waitLatch(mLatchActivityLaunch);

        try {
            String cmd = "pidof " + getTestPackage();
            String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
            return Integer.parseInt(result.trim());
        } catch (IOException e) {
            fail("failed to get pid of test package");
            return 0;
        } catch (NumberFormatException e) {
            fail("failed to parse pid " + e);
            return 0;
        }
    }

    /** Bring the test activity back to top */
    protected void bringTestActivityTop() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(getTestPackage(), getTestActivity());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        SystemUtil.runWithShellPermissionIdentity(
                () -> TestApis.activities().startActivity(intent),
                android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);
    }

    /** Bring the test activity into cached mode by launching another 2 apps */
    protected void makeTestActivityCached() {
        // Launch another activity to bring the test activity into background
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mContext, SimpleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        // Launch another activity to bring the test activity into cached mode
        Intent intent2 = new Intent(Intent.ACTION_MAIN);
        intent2.setClass(mContext, SimpleActivity2.class);
        intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mInstrumentation.startActivitySync(intent);
                    mInstrumentation.startActivitySync(intent2);
                },
                android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);
        waitLatch(mLatchActivityCached);
    }

    /** Wait for CountDownLatch with timeout */
    private void waitLatch(CountDownLatch latch) {
        try {
            latch.await(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
