/*
 * Copyright 2024 The Android Open Source Project
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

package android.keystore.cts;

import static android.os.UserHandle.USER_ALL;
import static android.server.wm.ShellCommandHelper.executeShellCommand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.server.wm.LockScreenSession;
import android.server.wm.UiDeviceUtils;
import android.server.wm.WindowManagerStateHelper;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.UserHelper;

class DeviceLockSession implements AutoCloseable {
    protected static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME --user "
                    + Process.myUserHandle().getIdentifier();
    private static final String AM_BROADCAST_CLOSE_SYSTEM_DIALOGS =
            "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS --user " + USER_ALL;

    @NonNull
    private final LockScreenSession mLockCredential;

    private final Context mContext;

    DeviceLockSession(Instrumentation instrumentation) throws Exception {
        mContext = instrumentation.getContext();
        UiDeviceUtils.wakeUpAndUnlock(mContext);
        launchHomeActivity();
        assumeFalseOnVisibleBackgroundUser(mContext,
                "Keyguard not supported for visible background users");

        final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
        mLockCredential = new LockScreenSession(instrumentation, wmState);
        mLockCredential.setLockCredential();
    }

    public void performDeviceLock() {
        mLockCredential.sleepDevice();
        var keyguardManager = mContext.getSystemService(KeyguardManager.class);
        for (int i = 0; i < 25 && !keyguardManager.isDeviceLocked(); i++) {
            SystemClock.sleep(200);
        }
    }

    public void performDeviceUnlock() throws Exception {
        mLockCredential.gotoKeyguard();
        UiDeviceUtils.pressUnlockButton();
        mLockCredential.enterAndConfirmLockCredential();
        launchHomeActivity();
        var keyguardManager = mContext.getSystemService(KeyguardManager.class);
        for (int i = 0; i < 25 && keyguardManager.isDeviceLocked(); i++) {
            SystemClock.sleep(200);
        }
        assertFalse(keyguardManager.isDeviceLocked());
    }

    @Override
    public void close() throws Exception {
        mLockCredential.close();
    }

    /** Launches the home activity directly with waiting for it to be visible. */
    private void launchHomeActivity() {
        // dismiss all system dialogs before launch home.
        closeSystemDialogs();
        executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);
    }

    private static void closeSystemDialogs() {
        executeShellCommand(AM_BROADCAST_CLOSE_SYSTEM_DIALOGS);
    }

    /** Skips the test on visible background users. */
    private void assumeFalseOnVisibleBackgroundUser(
            @NonNull Context context, @NonNull String message) {
        final UserHelper userHelper = new UserHelper(context);
        assumeFalse(message, userHelper.isVisibleBackgroundUser());
    }
}
