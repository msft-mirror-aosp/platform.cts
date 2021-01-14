/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.cts;

import static android.app.cts.NotificationManagerTest.toggleListenerAccess;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.FutureServiceConnection;
import android.app.cts.android.app.cts.tools.NotificationHelper;
import android.app.stubs.TestNotificationListener;
import android.app.stubs.shared.FakeView;
import android.app.stubs.shared.ICloseSystemDialogsTestsService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CloseSystemDialogsTest {
    private static final String TEST_SERVICE =
            "android.app.stubs.shared.CloseSystemDialogsTestService";
    private static final String APP_COMPAT_ENABLE = "enable";
    private static final String APP_COMPAT_DISABLE = "disable";
    private static final String APP_COMPAT_RESET = "reset";
    private static final String ACTION_SENTINEL = "sentinel";
    private static final String REASON = "test";
    private static final long TIMEOUT_MS = 3000;

    /**
     * This test is not self-instrumenting, so we need to bind to the service in the instrumentation
     * target package (instead of our package).
     */
    private static final String APP_SELF = "android.app.stubs";

    /**
     * Use com.android.app1 instead of android.app.stubs because the latter is the target of
     * instrumentation, hence it also has shell powers for {@link
     * Intent#ACTION_CLOSE_SYSTEM_DIALOGS} and we don't want those powers under simulation.
     */
    private static final String APP_HELPER = "com.android.app1";

    private Instrumentation mInstrumentation;
    private FutureServiceConnection mConnection;
    private Context mContext;
    private ContentResolver mResolver;
    private ICloseSystemDialogsTestsService mService;
    private volatile WindowManager mSawWindowManager;
    private volatile Context mSawContext;
    private volatile CompletableFuture<Void> mCloseSystemDialogsReceived;
    private volatile ConditionVariable mSentinelReceived;
    private volatile FakeView mFakeView;
    private IntentReceiver mIntentReceiver;
    private Handler mMainHandler;
    private TestNotificationListener mNotificationListener;
    private NotificationHelper mNotificationHelper;
    private String mPreviousHiddenApiPolicy;


    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mResolver = mContext.getContentResolver();
        mMainHandler = new Handler(Looper.getMainLooper());
        toggleListenerAccess(mContext, true);
        mNotificationListener = TestNotificationListener.getInstance();
        mNotificationHelper = new NotificationHelper(mContext, () -> mNotificationListener);
        compat(APP_COMPAT_ENABLE, ActivityManager.DROP_CLOSE_SYSTEM_DIALOGS, APP_HELPER);
        setTargetCurrent();

        // We need to test that a few hidden APIs are properly protected in the helper app. The
        // helper app we're using doesn't have the checks disabled because it's not the target of
        // instrumentation, see comment on APP_HELPER for details.
        mPreviousHiddenApiPolicy = setHiddenApiPolicy("1");

        // Add a receiver that will verify if the intent was sent or not
        mIntentReceiver = new IntentReceiver();
        mCloseSystemDialogsReceived = new CompletableFuture<>();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(ACTION_SENTINEL);
        mContext.registerReceiver(mIntentReceiver, filter);

        // Add a view to verify if the view got the callback or not
        mSawContext = getContextForSaw(mContext);
        mSawWindowManager = mSawContext.getSystemService(WindowManager.class);
        mMainHandler.post(() -> {
            mFakeView = new FakeView(mSawContext);
            mSawWindowManager.addView(mFakeView, new LayoutParams(TYPE_APPLICATION_OVERLAY));
        });
    }

    @After
    public void tearDown() throws Exception {
        if (mConnection != null) {
            mContext.unbindService(mConnection);
        }
        mMainHandler.post(() -> mSawWindowManager.removeViewImmediate(mFakeView));
        mContext.unregisterReceiver(mIntentReceiver);
        setHiddenApiPolicy(mPreviousHiddenApiPolicy);
        compat(APP_COMPAT_RESET, ActivityManager.DROP_CLOSE_SYSTEM_DIALOGS, APP_HELPER);
        compat(APP_COMPAT_RESET, ActivityManager.LOCK_DOWN_CLOSE_SYSTEM_DIALOGS, APP_HELPER);
        compat(APP_COMPAT_RESET, "NOTIFICATION_TRAMPOLINE_BLOCK", APP_HELPER);
        mNotificationListener.resetData();
    }

    /** Intent.ACTION_CLOSE_SYSTEM_DIALOGS */

    @Test
    public void testCloseSystemDialogs_whenTargetSdkCurrent_isBlockedAndThrows() throws Exception {
        mService = getService(APP_HELPER);

        assertThrows(SecurityException.class, () -> mService.sendCloseSystemDialogsBroadcast());

        assertCloseSystemDialogsNotReceived();
    }

    @Test
    public void testCloseSystemDialogs_whenTargetSdk30_isBlockedButDoesNotThrow() throws Exception {
        setTargetSdk30();
        mService = getService(APP_HELPER);

        mService.sendCloseSystemDialogsBroadcast();

        assertCloseSystemDialogsNotReceived();
    }

    @Test
    public void testCloseSystemDialogs_whenTestInstrumentedViaShell_isSent() throws Exception {
        mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        assertCloseSystemDialogsReceived();
    }

    @Test
    public void testCloseSystemDialogs_whenRunningAsShell_isSent() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)));

        assertCloseSystemDialogsReceived();
    }

    @Test
    public void testCloseSystemDialogs_inTrampolineWhenTargetSdkCurrent_isBlockedAndThrows()
            throws Exception {
        int notificationId = 42;
        CompletableFuture<Integer> result = new CompletableFuture<>();
        mService = getService(APP_HELPER);

        mService.postNotification(notificationId, new FutureReceiver(result));

        mNotificationHelper.clickNotification(notificationId, /* searchAll */ true);
        assertThat(result.get()).isEqualTo(
                ICloseSystemDialogsTestsService.RESULT_SECURITY_EXCEPTION);
        assertCloseSystemDialogsNotReceived();
    }

    @Test
    public void testCloseSystemDialogs_inTrampolineWhenTargetSdk30_isSent() throws Exception {
        setTargetSdk30();
        int notificationId = 43;
        CompletableFuture<Integer> result = new CompletableFuture<>();
        mService = getService(APP_HELPER);

        mService.postNotification(notificationId, new FutureReceiver(result));

        mNotificationHelper.clickNotification(notificationId, /* searchAll */ true);
        assertThat(result.get()).isEqualTo(
                ICloseSystemDialogsTestsService.RESULT_OK);
        assertCloseSystemDialogsReceived();
    }

    /** IWindowManager.closeSystemDialogs() */

    @Test
    public void testCloseSystemDialogsViaWindowManager_whenTestInstrumentedViaShell_isSent()
            throws Exception {
        mService = getService(APP_SELF);

        mService.closeSystemDialogsViaWindowManager(REASON);

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(REASON);
    }

    @Test
    public void testCloseSystemDialogsViaWindowManager_whenRunningAsShell_isSent()
            throws Exception {
        mService = getService(APP_SELF);

        SystemUtil.runWithShellPermissionIdentity(
                () -> mService.closeSystemDialogsViaWindowManager(REASON));

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(REASON);
    }

    @Test
    public void testCloseSystemDialogsViaWindowManager_whenTargetSdkCurrent_isBlockedAndThrows()
            throws Exception {
        mService = getService(APP_HELPER);

        assertThrows(SecurityException.class,
                () -> mService.closeSystemDialogsViaWindowManager(REASON));

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(null);
    }


    @Test
    public void testCloseSystemDialogsViaWindowManager_whenTargetSdk30_isBlockedButDoesNotThrow()
            throws Exception {
        setTargetSdk30();
        mService = getService(APP_HELPER);

        mService.closeSystemDialogsViaWindowManager(REASON);

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(null);
    }

    /** IActivityManager.closeSystemDialogs() */

    @Test
    public void testCloseSystemDialogsViaActivityManager_whenTestInstrumentedViaShell_isSent()
            throws Exception {
        mService = getService(APP_SELF);

        mService.closeSystemDialogsViaActivityManager(REASON);

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(REASON);
        assertCloseSystemDialogsReceived();
    }

    @Test
    public void testCloseSystemDialogsViaActivityManager_whenRunningAsShell_isSent()
            throws Exception {
        mService = getService(APP_SELF);

        SystemUtil.runWithShellPermissionIdentity(
                () -> mService.closeSystemDialogsViaActivityManager(REASON));

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(REASON);
        assertCloseSystemDialogsReceived();
    }

    @Test
    public void testCloseSystemDialogsViaActivityManager_whenTargetSdkCurrent_isBlockedAndThrows()
            throws Exception {
        mService = getService(APP_HELPER);

        assertThrows(SecurityException.class,
                () -> mService.closeSystemDialogsViaActivityManager(REASON));

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(null);
        assertCloseSystemDialogsNotReceived();
    }

    @Test
    public void testCloseSystemDialogsViaActivityManager_whenTargetSdk30_isBlockedButDoesNotThrow()
            throws Exception {
        setTargetSdk30();
        mService = getService(APP_HELPER);

        mService.closeSystemDialogsViaActivityManager(REASON);

        assertThat(mFakeView.getNextCloseSystemDialogsCallReason(TIMEOUT_MS)).isEqualTo(null);
        assertCloseSystemDialogsNotReceived();
    }

    private void setTargetSdk30() {
        // TODO(b/159105552): For now we emulate targetSdk 30 by force-disabling the feature.
        //   Remove this once the feature is enabled and use another app with lower targetSdk.
        compat(APP_COMPAT_DISABLE, ActivityManager.LOCK_DOWN_CLOSE_SYSTEM_DIALOGS, APP_HELPER);
        compat(APP_COMPAT_DISABLE, "NOTIFICATION_TRAMPOLINE_BLOCK", APP_HELPER);
    }

    private void setTargetCurrent() {
        // TODO(b/159105552): For now we emulate current targetSdk by force-enabling the feature.
        //   Remove this once the feature is enabled by default.
        compat(APP_COMPAT_ENABLE, ActivityManager.LOCK_DOWN_CLOSE_SYSTEM_DIALOGS, APP_HELPER);
    }

    private void assertCloseSystemDialogsNotReceived() {
        // If both broadcasts are sent, they will be received in order here since they are both
        // registered receivers in the "bg" queue in system_server and belong to the same app.
        // This is guaranteed by a series of handlers that are the same in both cases and due to the
        // fact that the binder that system_server uses to call into the app is the same (since the
        // app is the same) and one-way calls on the same binder object are ordered.
        mSentinelReceived = new ConditionVariable(false);
        Intent intent = new Intent(ACTION_SENTINEL);
        intent.setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);
        mSentinelReceived.block();
        assertThat(mCloseSystemDialogsReceived.isDone()).isFalse();
    }

    private void assertCloseSystemDialogsReceived() throws Exception {
        mCloseSystemDialogsReceived.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // No TimeoutException thrown
    }

    private ICloseSystemDialogsTestsService getService(String packageName) throws Exception {
        return ICloseSystemDialogsTestsService.Stub.asInterface(
                connect(packageName).get(TIMEOUT_MS));
    }

    private FutureServiceConnection connect(String packageName) {
        if (mConnection != null) {
            return mConnection;
        }
        mConnection = new FutureServiceConnection();
        Intent intent = new Intent();
        intent.setComponent(ComponentName.createRelative(packageName, TEST_SERVICE));
        assertTrue(mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE));
        return mConnection;
    }

    private String setHiddenApiPolicy(String policy) throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() -> {
            String previous = Settings.Global.getString(mResolver,
                    Settings.Global.HIDDEN_API_POLICY);
            Settings.Global.putString(mResolver, Settings.Global.HIDDEN_API_POLICY, policy);
            return previous;
        });
    }

    private static void compat(String command, String changeId, String packageName) {
        SystemUtil.runShellCommand(
                String.format("am compat %s %s %s", command, changeId, packageName));
    }

    private static void compat(String command, long changeId, String packageName) {
        compat(command, Long.toString(changeId), packageName);
    }

    private static Context getContextForSaw(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Context displayContext = context.createDisplayContext(display);
        return displayContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null);
    }

    private class IntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_CLOSE_SYSTEM_DIALOGS:
                    mCloseSystemDialogsReceived.complete(null);
                    break;
                case ACTION_SENTINEL:
                    mSentinelReceived.open();
                    break;
            }
        }
    }

    private class FutureReceiver extends ResultReceiver {
        private final CompletableFuture<Integer> mFuture;

        FutureReceiver(CompletableFuture<Integer> future) {
            super(mMainHandler);
            mFuture = future;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mFuture.complete(resultCode);
        }
    }
}
