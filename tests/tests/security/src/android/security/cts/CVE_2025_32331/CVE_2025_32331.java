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

package android.security.cts.CVE_2025_32331;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.sts.common.DumpsysUtils.getParsedDumpsys;
import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.DEFAULT_MAX_POLL_TIME_MS;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.activities.Activities;
import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_32331 extends StsExtraBusinessLogicTestCase {
    public static final String TAG = "cve-2025-32331";
    public static String mBroadcastAction = "cve_2025_32331_action";
    public static String mIsAppPinned = "cve_2025_32331_isAppPinned";
    public static String mPocActivityOnPause = "cve_2025_32331_onPause";
    public static String mTaskId = "cve_2025_32331_taskId";
    public static String mHelperActivityLaunched = "cve_2025_32331_helperActivityLaunched";
    public static String mUniqueString = "CVE_2025_32331_unique_text_view";
    public static KeyguardManager mKeyguardManager = null;
    public static Instrumentation mInstrumentation = null;
    public static Context mContext = null;
    public static UiDevice mUiDevice = null;

    @Test
    @AsbSecurityTest(cveBugId = 404252173)
    @SuppressLint("MissingFail")
    public void testPocCVE_2025_32331() {
        try {
            // Skip the test if DUT is not foldable.
            mInstrumentation = getInstrumentation();
            assume().withMessage("Skipping the test as DUT is not foldable")
                    .that(isDeviceFoldable())
                    .isTrue();

            // Set lockscreen.
            // Enable secure settings to pin an app.
            // Enable secure settings to secure device when an app is unpinned.
            // Secure the device-state.
            mContext = mInstrumentation.getContext();
            mUiDevice = UiDevice.getInstance(mInstrumentation);
            try (AutoCloseable withLockScreen = new LockSettingsUtil(mContext).withLockScreen();
                    AutoCloseable withPinAppEnabled =
                            SystemUtil.withSetting(
                                    mInstrumentation, "secure", "lock_to_app_enabled", "1");
                    AutoCloseable withSecuredAppPinning =
                            SystemUtil.withSetting(
                                    mInstrumentation, "secure", "lock_to_app_exit_locked", "1");
                    AutoCloseable withSecuredDeviceState = withSecuredDeviceState()) {
                // Register a BroadcastReceiver.
                final CompletableFuture<Boolean> isOnPauseRunning = new CompletableFuture();
                final CompletableFuture<Boolean> helperActivityLaunched = new CompletableFuture();
                final CompletableFuture<Integer> taskId = new CompletableFuture();
                final CompletableFuture<Boolean> isAppPinned = new CompletableFuture();
                mContext.registerReceiver(
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                try {
                                    // Broadcast from 'PocActivity' confirming app was
                                    // pinned.
                                    if (intent.hasExtra(mIsAppPinned)) {
                                        isAppPinned.complete(
                                                intent.getBooleanExtra(mIsAppPinned, false));
                                    }

                                    // Broadcast from 'PocActivity' to fetch its taskId.
                                    final int fetchedTaskId = intent.getIntExtra(mTaskId, -1);
                                    if (fetchedTaskId != -1) {
                                        taskId.complete(fetchedTaskId);
                                    }

                                    // Broadcast from 'PocActivity' confirming app is in background.
                                    if (intent.getBooleanExtra(mPocActivityOnPause, false)) {
                                        isOnPauseRunning.complete(true);
                                    }

                                    // Broadcast from 'HelperActivity' confirming that it was
                                    // launched.
                                    if (intent.getBooleanExtra(mHelperActivityLaunched, false)) {
                                        helperActivityLaunched.complete(true);
                                    }
                                } catch (Exception e) {
                                    Log.d(
                                            TAG,
                                            String.format(
                                                    "Exception occurred in BroadcastReceiver: %s",
                                                    e.getMessage()));
                                }
                            }
                        },
                        new IntentFilter(mBroadcastAction),
                        Context.RECEIVER_EXPORTED);

                // Start 'PocActivity'.
                final long timeout = 15L;
                final String pocActivityname = PocActivity.class.getName();
                final Activities activity = TestApis.activities();
                activity.startActivity(
                        new Intent(mContext, PocActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                assume().withMessage("'PocActivity' failed to launch")
                        .that(poll(() -> isActivityVisible(pocActivityname)))
                        .isTrue();

                // Send 'PocActivity' to background by pressing 'KEYCODE_HOME'.
                mUiDevice.pressKeyCode(KeyEvent.KEYCODE_HOME);
                assume().withMessage("Failed to send 'PocActivity' to background")
                        .that(
                                poll(
                                        () ->
                                                isOnPauseRunning.getNow(false)
                                                        && !isActivityVisible(pocActivityname)))
                        .isTrue();

                // Fetch the taskId of 'PocActivity' and pin it.
                // When app is pinned, 'onResume' gets invoked.
                runShellCommand(mInstrumentation, String.format("am task lock %d", taskId.get()));
                assume().withMessage("Failed to pin 'PocActivity'")
                        .that(poll(() -> isAppPinned.getNow(false) && isPocActivityPinned()))
                        .isTrue();

                // Simulate folding the device.
                // id=0 :: device-state=CLOSED
                // id=1 :: device-state=HALF_OPENED
                // id=2 :: device-state=OPENED
                // id=3 :: device-state=REAR_DISPLAY_MODE
                // id=4 :: device-state=CONCURRENT_INNER_DEFAULT
                // id=5 :: device-state=REAR_DISPLAY_OUTER_DEFAULT
                // To reproduce the behavior, fold and unfold the device multiple
                // times until the device gets locked.
                assume().withMessage("Device failed to get into locked state")
                        .that(
                                poll(
                                        () -> {
                                            try {
                                                // Fold the device
                                                changeDeviceState(0 /* closed */);

                                                // Check if the device is in locked state.
                                                if (poll(() -> isKeyguardLocked(), 500L, 2_000L)) {
                                                    return true;
                                                } else {
                                                    // Press 'KEYCODE_LOCK' to bring lock screen.
                                                    // Note: When any app is pinned, device does
                                                    // not go to lock screen.
                                                    Field keycodeLock = null;
                                                    try {
                                                        keycodeLock =
                                                                KeyEvent.class.getDeclaredField(
                                                                        "KEYCODE_LOCK");
                                                        keycodeLock.setAccessible(true);
                                                    } catch (NoSuchFieldException exception) {
                                                        Log.d(
                                                                TAG,
                                                                String.format(
                                                                        "Failed to fetch"
                                                                                + " 'KEYCODE_LOCK'"
                                                                                + " field: %s",
                                                                        exception.getMessage()));
                                                    }
                                                    mUiDevice.pressKeyCode(
                                                            keycodeLock == null
                                                                    ? KeyEvent.KEYCODE_SLEEP
                                                                    : (int) keycodeLock.get(null));

                                                    // If device is still unlocked, unfold the
                                                    // device
                                                    changeDeviceState(2 /* opened */);
                                                    return false;
                                                }
                                            } catch (Exception e) {
                                                throw new IllegalStateException(e);
                                            }
                                        },
                                        2_000L,
                                        DEFAULT_MAX_POLL_TIME_MS))
                        .isTrue();

                // Unlock the device and unpin the activity.
                assume().withMessage("'PocActivity' is not visible")
                        .that(
                                poll(
                                        () -> {
                                            mUiDevice.pressMenu();
                                            return isActivityVisible(pocActivityname);
                                        }))
                        .isTrue();
                runShellCommand(mInstrumentation, "am task lock stop");
                assume().withMessage("Failed to unpin the 'PocActivity'")
                        .that(poll(() -> !isPocActivityPinned()))
                        .isTrue();

                // Start 'HelperActivity'.
                // Without fix, the app gets unpinned without security pin, and the 'HelperActivity'
                // is visible on the screen.
                // With fix, a security screen appears and 'HelperActivity' is not visible.
                if (isKeyguardLocked()) {
                    runShellCommand(mInstrumentation, "wm dismiss-keyguard");
                }
                activity.startActivity(
                        new Intent(mContext, HelperActivity.class)
                                .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                assertWithMessage(
                                "Device is vulnerable to b/404252173!! App can be unpinned without"
                                        + " authentication.")
                        .that(
                                poll(
                                        () -> {
                                            mUiDevice.pressMenu();
                                            return helperActivityLaunched.getNow(false)
                                                    && mUiDevice.wait(
                                                                    Until.findObject(
                                                                            By.text(mUniqueString)),
                                                                    timeout)
                                                            != null;
                                        }))
                        .isFalse();
            }

            // With fix, the lock screen stays on screen.
            poll(
                    () -> {
                        try {
                            if (!isKeyguardLocked()) {
                                return true;
                            }
                            runShellCommand(mInstrumentation, "wm dismiss-keyguard");
                        } catch (Exception e) {
                            Log.d(
                                    TAG,
                                    String.format(
                                            "Exception occurred during cleanup: %s",
                                            e.getMessage()));
                        }
                        return false;
                    });
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withSecuredDeviceState() throws Exception {
        // Fetch the current device-state
        final int deviceState =
                Integer.parseInt(
                        runShellCommand(mInstrumentation, "cmd device_state print-state").trim());

        // Restore the original device-state
        return () ->
                runShellCommand(
                        mInstrumentation, String.format("cmd device_state state %d", deviceState));
    }

    private void changeDeviceState(final int state) throws Exception {
        runShellCommand(mInstrumentation, String.format("cmd device_state state %d", state));
        final String currentDeviceStateCommand = "cmd device_state print-state";
        assume().withMessage("Device-state was not set")
                .that(
                        poll(
                                () -> {
                                    try {
                                        final int currentState =
                                                Integer.parseInt(
                                                        runShellCommand(
                                                                        mInstrumentation,
                                                                        currentDeviceStateCommand)
                                                                .trim());
                                        return currentState == state;
                                    } catch (Exception e) {
                                        throw new IllegalStateException(e);
                                    }
                                },
                                1_000L,
                                2_000L))
                .isTrue();
    }

    private boolean isPocActivityPinned() {
        final Matcher matcher =
                getParsedDumpsys(
                        "activity" /* service */,
                        Pattern.compile(
                                "mLockTaskModeState=(?<taskPinStatus>(PINNED|NONE))" /* regex */,
                                Pattern.CASE_INSENSITIVE));
        assume().withMessage("Failed to find pinned status").that(matcher.find()).isTrue();

        // Check for all the findings.
        do {
            if (matcher.group("taskPinStatus").equals("PINNED")) {
                return true;
            }
        } while (matcher.find());
        return false;
    }

    private boolean isKeyguardLocked() {
        try {
            if (mKeyguardManager == null) {
                mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
            }
            return mKeyguardManager.isKeyguardLocked();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isDeviceFoldable() throws Exception {
        // Fetch the supporting device states
        return Arrays.stream(
                                runShellCommand(
                                                mInstrumentation,
                                                "cmd device_state print-states-simple")
                                        .trim()
                                        .split(","))
                        .mapToInt(Integer::parseInt)
                        .count()
                > 1;
    }
}
