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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.DumpsysUtils.isActivityLaunched;
import static com.android.sts.common.DumpsysUtils.isActivityResumed;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_22422 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 339532378)
    @Test
    public void testPocCVE_2025_22422() {
        final Context context = getApplicationContext();

        // Create a lock screen.
        try (AutoCloseable withPinLockScreen = new LockSettingsUtil(context).withLockScreen()) {
            // Launch 'ConfirmDeviceCredentialActivity'.
            final Intent credentialActivityIntent =
                    context.getSystemService(KeyguardManager.class)
                            .createConfirmDeviceCredentialIntent(
                                    "cve_2025_22422_title", "cve_2025_22422_subtitle")
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(credentialActivityIntent);

            // Check if 'ConfirmDeviceCredentialActivity' is launched successfully.
            final PackageManager packageManager = context.getPackageManager();
            final String credentialActivityName =
                    fetchActivityNameFromIntent(packageManager, credentialActivityIntent);
            assume().that(poll(() -> isActivityLaunched(credentialActivityName))).isTrue();

            // Check if 'cve_2025_22422_title' of credential activity appears on screen.
            final UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
            assume().withMessage("Credential activity titile not visible !!")
                    .that(uiDevice.wait(Until.hasObject(By.text("cve_2025_22422_title")), 5_000L))
                    .isNotNull();

            // Launch the settings activity.
            final Intent settingIntent =
                    new Intent(Settings.ACTION_SETTINGS)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(settingIntent);

            // Check if settings activity is resumed.
            final String settingsActivityName =
                    fetchActivityNameFromIntent(packageManager, settingIntent);
            assume().that(poll(() -> isActivityResumed(settingsActivityName))).isTrue();

            // Check if 'cve_2025_22422_title' of credential activity stays over settings
            boolean isNotVulnerable =
                    poll(
                            () -> {
                                if (uiDevice.findObject(By.text("cve_2025_22422_title")) != null) {
                                    return false;
                                }
                                return true;
                            });

            // With fix, settings activity is launched and keyguard is not visible on screen and
            // test passes.
            if (isNotVulnerable) {
                return;
            }

            // Check if settings activity is still resumed.
            assume().that(poll(() -> isActivityResumed(settingsActivityName))).isTrue();

            // Without fix, lockscreen will be on top of settings activity.
            assertWithMessage(
                            "Device is vulnerable to b/339532378 !!, The lock screen is "
                                    + "launched over settings activity")
                    .that(isNotVulnerable)
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private String fetchActivityNameFromIntent(PackageManager packageManager, Intent intent) {
        final ActivityInfo activityInfo =
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        .activityInfo;
        return activityInfo.packageName + "/" + activityInfo.name;
    }
}
