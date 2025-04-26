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

package android.content.broadcast.cts;

import static android.content.pm.Flags.FLAG_MERGE_PACKAGE_CHANGED_BROADCAST;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.os.Process.myUserHandle;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@AppModeFull
@AppModeNonSdkSandbox
@RunWith(AndroidJUnit4.class)
public class PackageChangedBroadcastGroupPolicyTest {
    private static final String TAG = "PackageChangedBroadcastGroupPolicyTest";
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long LONG_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(50);
    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_READY = 1;
    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_FAILED = 2;
    private static final int EXTRA_REMOTE_CALLBACK_RESULT_TYPE_BROADCAST_NUM = 3;
    private static final int EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE_WAKEUP = 1;

    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT_TYPE =
            "extra_remote_callback_result_type";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT_VALUE =
            "extra_remote_callback_result_value";
    private static final String EXTRA_ACTIVITY_REMOTE_CALLBACK = "extra_activity_remote_callback";
    private static final String EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE =
            "extra_activity_remote_callback_result_type";
    private static final String EXTRA_TEST_PACKAGE_NAME = "extra_test_package_name";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsPackageChangedBroadcastReceiverApp.apk";
    private static final String PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME =
            "android.content.cts.packagechangedbroadcastreceiverapp";
    private static final String PACKAGE_CHANGED_BROADCAST_RECEIVER_MAIN_ACTIVITY =
            PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME + ".MainActivity";
    private static final String PACKAGE_CHANGED_TEST_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsPackageChangedTestApp.apk";
    private static final String PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME =
            "android.content.cts.packagechangedtestapp";
    private static final String SLEEP_ACTION = "android.content.broadcast.cts.SLEEP_ACTION";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private PackageManager mPackageManager;
    private int mUserId;
    private RemoteCallback mActivityRemoteCallback;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        mUserId = myUserHandle().getIdentifier();

        assertFalse(
                isAppInstalledForUser(
                        PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME, mUserId));
        assertFalse(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME, mUserId));

        installPackageAsUser(PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_APK_PATH, mUserId);
        assertTrue(
                isAppInstalledForUser(
                        PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME, mUserId));

        installPackageAsUser(PACKAGE_CHANGED_TEST_APP_APK_PATH, mUserId);
        assertTrue(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME, mUserId));
    }

    @After
    public void teardown() {
        uninstallPackage(PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME);
        assertThat(
                        isAppInstalledForUser(
                                PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME, mUserId))
                .isFalse();

        uninstallPackage(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME);
        assertThat(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME, mUserId)).isFalse();
    }

    @RequiresFlagsEnabled(FLAG_MERGE_PACKAGE_CHANGED_BROADCAST)
    @Test
    public void changeWholePackageState_enableDisableFourTimes_shouldReceiveLastBroadcastOnce()
            throws Exception {
        enableAndDisableWholePackageStateContinuously(
                4 /* numSentBroadcasts */, 1 /* numReceivedBroadcasts */);
    }

    @RequiresFlagsDisabled(FLAG_MERGE_PACKAGE_CHANGED_BROADCAST)
    @Test
    public void changeWholePackageState_enableDisableFourTimes_shouldReceiveFourBroadcasts()
            throws Exception {
        enableAndDisableWholePackageStateContinuously(
                4 /* numSentBroadcasts */, 4 /* numReceivedBroadcasts */);
    }

    private void enableAndDisableWholePackageStateContinuously(
            int numSentBroadcasts, int numReceivedBroadcasts) throws Exception {
        // Launch the package changed broadcast test app to receive the PACKAGE_CHANGED broadcast.
        // The test app will send the number of receiving the PACKAGE_CHANGED broadcast.
        final Intent intent =
                new Intent(Intent.ACTION_MAIN)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setComponent(
                                new ComponentName(
                                        PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME,
                                        PACKAGE_CHANGED_BROADCAST_RECEIVER_MAIN_ACTIVITY));
        CompletableFuture<Void> activityFuture = new CompletableFuture<>();
        CompletableFuture<Integer> broadcastFuture = new CompletableFuture<>();
        final RemoteCallback callback =
                new RemoteCallback(
                        result -> {
                            Log.d(
                                    TAG,
                                    "Get callback from activity : "
                                            + PACKAGE_CHANGED_BROADCAST_RECEIVER_MAIN_ACTIVITY);
                            switch (result.getInt(EXTRA_REMOTE_CALLBACK_RESULT_TYPE)) {
                                case EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_READY:
                                    mActivityRemoteCallback =
                                            result.getParcelable(EXTRA_ACTIVITY_REMOTE_CALLBACK);
                                    activityFuture.complete(null);
                                    Log.d(TAG, "startActivity ready");
                                    break;
                                case EXTRA_REMOTE_CALLBACK_RESULT_TYPE_ACTIVITY_FAILED:
                                    fail("return failure from the activity of receiver app");
                                    break;
                                case EXTRA_REMOTE_CALLBACK_RESULT_TYPE_BROADCAST_NUM:
                                    broadcastFuture.complete(
                                            result.getInt(EXTRA_REMOTE_CALLBACK_RESULT_VALUE, -1));
                                    break;
                            }
                        });
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        intent.putExtra(EXTRA_TEST_PACKAGE_NAME, PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME);
        mContext.startActivity(intent);
        Log.d(TAG, "startActivity : " + intent);

        // Wait the package changed broadcast receiver app ready.
        activityFuture.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Create artificial broadcast traffic to the receiver app as a way to delay the
        // package_changed one. Basically send a broadcast to the receiver app and ask it to block
        // all the broadcast receivers in the app. It is guaranteed that until the broadcast
        // receivers are unblocked, any broadcasts to this app are delayed.
        final Intent sleepIntent = new Intent(SLEEP_ACTION);
        sleepIntent.setPackage(PACKAGE_CHANGED_BROADCAST_RECEIVER_APP_PACKAGE_NAME);
        mContext.sendOrderedBroadcast(sleepIntent, null);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    // Start changing test app state multiple times in a row.
                    for (int i = 0; i < numSentBroadcasts; i++) {
                        setApplicationEnabledSetting(
                                PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                                i % 2 == 0
                                        ? COMPONENT_ENABLED_STATE_ENABLED
                                        : COMPONENT_ENABLED_STATE_DISABLED,
                                0 /* flags */);
                    }

                    // Now tell the receiver app to unblock the receivers.
                    final Bundle activityBundle = new Bundle();
                    activityBundle.putInt(
                            EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE,
                            EXTRA_ACTIVITY_REMOTE_CALLBACK_RESULT_TYPE_WAKEUP);
                    mActivityRemoteCallback.sendResult(activityBundle);
                });

        int broadcastNumber = broadcastFuture.get(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(broadcastNumber).isEqualTo(numReceivedBroadcasts);
    }

    private void setApplicationEnabledSetting(String packageName, int newState, int flags) {
        mPackageManager.setApplicationEnabledSetting(packageName, newState, flags);
        SystemUtil.runShellCommand("pm wait-for-handler --timeout 2000");
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(String.format("pm uninstall %s", packageName));
    }

    private static void installPackageAsUser(String apkPath, int userId) {
        assertThat(
                        SystemUtil.runShellCommand(
                                String.format("pm install -t -g --user %s %s", userId, apkPath)))
                .isEqualTo(String.format("Success\n"));
    }

    private static boolean isAppInstalledForUser(String packageName, int userId) {
        return Arrays.stream(
                        SystemUtil.runShellCommand(
                                        String.format(
                                                "pm list packages --user %s %s",
                                                userId, packageName))
                                .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }
}
