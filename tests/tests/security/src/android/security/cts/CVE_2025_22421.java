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

package android.security.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_22421 extends StsExtraBusinessLogicTestCase {
    private final String mNotificationDescription = "cve_2025_22421_notification_description";

    @AsbSecurityTest(cveBugId = 338024220)
    @Test
    public void testPocCVE_2025_22421() {
        try {
            // Fetch 'SystemUi' package name.
            final PackageManager packageManager =
                    getInstrumentation().getTargetContext().getPackageManager();
            final String systemUiPkg =
                    new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG)
                            .resolveActivity(packageManager)
                            .getPackageName();

            // Create a context for 'SystemUi' package.
            final Context systemUiContext =
                    getInstrumentation()
                            .getTargetContext()
                            .createPackageContext(
                                    systemUiPkg,
                                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);

            // Load the 'NotificationContentDescription' class.
            final Class notificationContentDescriptionClass =
                    systemUiContext
                            .getClassLoader()
                            .loadClass(
                                    "com.android.systemui.statusbar.notification."
                                            + "NotificationContentDescription");

            // Load the vulnerable method 'contentDescForNotification'.
            final Method contentDescForNotification =
                    notificationContentDescriptionClass.getDeclaredMethod(
                            "contentDescForNotification", Context.class, Notification.class);
            contentDescForNotification.setAccessible(true);

            // Invoke the vulnerable method 'contentDescForNotification'.
            final Notification notification = buildTestNotification(systemUiContext);
            final String fetchedValue =
                    (String) contentDescForNotification.invoke(null, systemUiContext, notification);

            // Without the fix, there is a leak of 'mNotificationDescription' in 'fetchedValue'
            // through vulnerable method.
            // With fix, the vulnerable method no longer returns 'mNotificationDescription' in
            // 'fetchedValue'.
            assertWithMessage(
                            "Device is vulnerable to b/338024220!!! contentDescForNotification "
                                    + "leaked notification text")
                    .that(fetchedValue)
                    .doesNotContain(mNotificationDescription);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Notification buildTestNotification(Context context) throws Exception {
        // Build a test notification.
        final Notification.Builder builder =
                new Notification.Builder(context, "cve_2025_22421_channel_id");

        // Set content title and content description.
        final String appName = builder.loadHeaderAppName();
        builder.setContentTitle(appName)
                .setContentText(mNotificationDescription)
                .setSmallIcon(Icon.createWithData(new byte[0], 0, 0));

        return builder.build();
    }
}
