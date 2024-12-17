/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.security.cts.BUG_356117796;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    @Test
    public void testPocBUG_356117796() {
        try {
            // Launch 'AppInfoDashboardFragment' of the test application
            final Context context = getApplicationContext();
            context.startActivity(
                    new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts(
                                            "package" /* scheme */,
                                            context.getPackageName() /* ssp */,
                                            null /* fragment */))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Click UiObject corresponding to 'Screen time'
            final UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
            assume().withMessage("UiObject corresponding to 'Screen time' not found")
                    .that(
                            clickUiObject(
                                    uiDevice,
                                    By.text(
                                            Pattern.compile(
                                                    querySettingsResource(
                                                            context,
                                                            "time_spent_in_app_pref_title"),
                                                    Pattern.CASE_INSENSITIVE))))
                    .isTrue();

            // Without fix, the helper application is visible in 'ChooserActivity'
            final String helperPackageName = "android.security.cts.BUG_356117796_helper";
            assertWithMessage(
                            "Device is vulnerable to b/356117796 !! Arbitrary activities can be "
                                    + "launched using 'Screen time'")
                    .that(
                            uiDevice.wait(
                                    Until.hasObject(
                                            By.text(
                                                    Pattern.compile(
                                                            helperPackageName,
                                                            Pattern.CASE_INSENSITIVE))),
                                    10_000L /* timeout */))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean clickUiObject(UiDevice uiDevice, BySelector selector) throws Exception {
        final UiObject2 uiobject = uiDevice.wait(Until.findObject(selector), 10_000L /* timeout */);
        if (uiobject != null) {
            assume().withMessage("UI Object is not enabled")
                    .that(poll(() -> (uiobject.isEnabled())))
                    .isTrue();
            uiobject.click();
            return true;
        }
        return false;
    }

    private String querySettingsResource(Context context, String resourceIdentifier) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            final String settingPkgName =
                    new Intent(Settings.ACTION_SETTINGS)
                            .resolveActivity(packageManager)
                            .getPackageName();
            final Resources settingsResources =
                    packageManager.getResourcesForApplication(settingPkgName);
            return settingsResources.getString(
                    settingsResources.getIdentifier(resourceIdentifier, "string", settingPkgName));
        } catch (Exception e) {
            return "Screen time";
        }
    }
}
