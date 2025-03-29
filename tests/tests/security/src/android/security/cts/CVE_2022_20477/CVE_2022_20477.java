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
package android.security.cts.CVE_2022_20477;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20477 extends StsExtraBusinessLogicTestCase {
    private static final int TIMEOUT_MS = 5000;

    @AsbSecurityTest(cveBugId = 241611867)
    @Test
    public void testPocCVE_2022_20477() {
        Instrumentation instrumentation = null;
        try {
            instrumentation =
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
            UiDevice uiDevice = UiDevice.getInstance(instrumentation);
            Context context = instrumentation.getTargetContext();
            KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);

            // Create notification channel
            NotificationChannel channel =
                    new NotificationChannel(
                            "CVE_2022_20477_notification_channel_id",
                            "CVE_2022_20477_notification_channel_name",
                            NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            // Fetch and add the flag 'RECEIVER_EXPORTED' for 'TIRAMISU' and above versions
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_EXPORTED").get(context)
                            : 0;

            // Check if any exception occurred in PocActivity
            CompletableFuture<Exception> pocActivityException = new CompletableFuture<>();

            // Register broadcast receiver to receive broadcast from PocActivity
            BroadcastReceiver broadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                pocActivityException.complete(
                                        (Exception)
                                                getSerializableExtra(
                                                        intent,
                                                        "exceptionMessageKey",
                                                        Exception.class));
                            } catch (Exception e) {
                                // Ignoring unintended exceptions here
                            }
                        }
                    };
            context.registerReceiver(
                    broadcastReceiver, new IntentFilter("CVE_2022_20477_action"), requiredFlag);

            // Launch PocActivity which will be visible on lockscreen
            context.startActivity(
                    new Intent(context, PocActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Blocking while the complete is called on variable pocActivityException
            assume().that(pocActivityException.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isNull();

            // Wait for PocActivity to be visible
            assume().that(isTextVisible(uiDevice, "CVE_2022_20477_PocActivity_is_visible"))
                    .isTrue();

            // Post a secret notification with text CVE_2022_20477_notification_text
            Notification notification =
                    new Notification.Builder(context, "CVE_2022_20477_notification_channel_id")
                            .setContentText("CVE_2022_20477_notification_text")
                            .setSmallIcon(Icon.createWithData(new byte[0], 0, 0)) // No icon
                            .setVisibility(Notification.VISIBILITY_SECRET)
                            .build();
            notificationManager.notify(1 /* id */, notification);

            // Lock the screen and verify the notification visibility behavior
            try (AutoCloseable withLockScreenCloseable =
                    new LockSettingsUtil(context).withLockScreen()) {
                // Lock and wake the device to lock keyguard
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_POWER");
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_WAKEUP");

                // Wait for keyguard to get locked
                poll(() -> keyguardManager.isKeyguardLocked());
                assume().that(keyguardManager.isKeyguardLocked()).isTrue();

                // Check if PocActivity is visible on lockscreen
                assume().that(isTextVisible(uiDevice, "CVE_2022_20477_PocActivity_is_visible"))
                        .isTrue();

                // Expand status bar
                context.getSystemService(StatusBarManager.class).expandNotificationsPanel();

                // Verify that secret notification text is NOT visible
                assertWithMessage(
                                "Device is vulnerable to b/241611867!!! Secret notification text"
                                        + " is visible on lockscreen.")
                        .that(isTextVisible(uiDevice, "CVE_2022_20477_notification_text"))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                // Go to HOME from lockscreen
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_POWER");
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_WAKEUP");
                SystemUtil.runShellCommand(instrumentation, "wm dismiss-keyguard");
            } catch (Exception e) {
                // Ignoring exceptions as test has been completed
            }
        }
    }

    private boolean isTextVisible(UiDevice uiDevice, String textPattern) {
        return uiDevice.wait(
                Until.hasObject(By.text(Pattern.compile(textPattern, Pattern.CASE_INSENSITIVE))),
                TIMEOUT_MS);
    }

    private Object getSerializableExtra(Intent intent, String key, Class valueClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            return Intent.class
                    .getDeclaredMethod("getSerializableExtra", String.class, Class.class)
                    .invoke(intent, key, valueClass);
        }
        return Intent.class
                .getDeclaredMethod("getSerializableExtra", String.class)
                .invoke(intent, key);
    }
}
