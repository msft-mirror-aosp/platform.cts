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

package com.android.server.cts.device.statsdatom;


import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.regex.Pattern;

public class MediaProjectionTests {
    private static final String TAG = "MediaProjectionTests";

    private static final Long TIMEOUT = 5000L;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    private static final String CANCEL_RESOURCE_ID = "android:id/button2";
    private static final String SHARE_TAB_TEST_TAG = "ShareTabOption";
    private static final Pattern MEDIA_PROJECTION_CONSENT_DIALOG =
            Pattern.compile(
                    SHARE_TAB_TEST_TAG
                            + "|"
                            + SYSTEM_UI_PACKAGE
                            + ":id/screen_share_permission_dialog");
    private static final String SHARE_APP_WINDOW_TEST_TAG = "ShareAppWindowOption";

    // Builds from 24Q3 and earlier will have screen_share_mode_spinner, while builds from
    // 24Q4 onwards will have screen_share_mode_options, so need to check both options here
    private static final Pattern SCREEN_SHARE_OPTIONS_RES_PATTERN =
            Pattern.compile(SYSTEM_UI_PACKAGE + ":id/screen_share_mode_(options|spinner)");

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static String sSingleAppString;

    public static class MediaProjectionActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Avoid re-launching the intent to prevent duplicate logs (b/469848466).
            if (savedInstanceState != null) {
                return;
            }
            MediaProjectionManager service = getSystemService(MediaProjectionManager.class);
            startActivityForResult(service.createScreenCaptureIntent(), 0);
        }
    }

    @Rule
    public ActivityTestRule<MediaProjectionActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionActivity.class, false, false);


    /** Get relevant text strings from SysUI Resources */
    @BeforeClass
    public static void setUp() throws PackageManager.NameNotFoundException {
        Resources sysUiResources;
        sysUiResources = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().getResourcesForApplication(SYSTEM_UI_PACKAGE);

        final String singleAppResName = "screen_share_permission_dialog_option_single_app";

        int singleAppResId = sysUiResources.getIdentifier(
                singleAppResName, "string", SYSTEM_UI_PACKAGE);

        sSingleAppString = sysUiResources.getString(singleAppResId);
    }

    @After
    public void tearDown() {
        mActivityRule.finishActivity();
    }

    @Test
    public void testMediaProjectionPermissionDialogCancel() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mActivityRule.launchActivity(null);
        mDevice.waitForIdle();

        // Wait for either the new UI or the old UI to appear
        UiObject2 dialog = mDevice.wait(Until.findObject(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
        if (dialog == null) {
            Log.e(TAG, "Media projection consent dialog not found");
            return;
        }

        // Dismiss the dialog using the back gesture.
        mDevice.waitForIdle();
        mDevice.pressBack();
        mDevice.wait(Until.gone(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
    }

    @Test
    public void testMediaProjectionShowAppSelector() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mActivityRule.launchActivity(null);
        mDevice.waitForIdle();

        // Check for the new Large Screen UI (ShareTabOption)
        boolean isLargeScreenUi =
                mDevice.wait(Until.hasObject(By.res(SHARE_TAB_TEST_TAG)), TIMEOUT);
        if (isLargeScreenUi) {
            Log.d(TAG, "Compose permission UI detected via testTag: " + SHARE_TAB_TEST_TAG);
            UiObject2 appWindowOption =
                    mDevice.wait(
                            Until.findObject(By.res(SHARE_APP_WINDOW_TEST_TAG).clickable(true)),
                            TIMEOUT);

            if (appWindowOption == null) {
                Log.e(
                        TAG,
                        "Could not find 'App Window' option with testTag: "
                                + SHARE_APP_WINDOW_TEST_TAG);
                return;
            }

            if (appWindowOption.isChecked()) {
                Log.d(TAG, "ShareAppWindowOption is already checked. Success.");
                return;
            }

            Log.d(TAG, "Found ShareAppWindowOption, clicking it.");

            appWindowOption.click();
            mDevice.waitForIdle();

            if (mDevice.wait(
                    Until.hasObject(By.res(SHARE_APP_WINDOW_TEST_TAG).checked(true)), TIMEOUT)) {
                Log.d(TAG, "ShareAppWindowOption is checked. Success.");

            } else {
                Log.w(TAG, "Failed to select ShareAppWindowOption within timeout.");
            }
            return;
        }

        // OEMs aren't guaranteed to support partial screenshare, so we only attempt
        // to reach the app selector if possible, and end the test prematurely if it isn't
        boolean hasModeSpinner = mDevice.hasObject(By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN));
        if (!hasModeSpinner) {
            Log.i(TAG, "Unable to find a screen share mode spinner");
            return;
        }

        UiObject2 modeSpinner =
                mDevice.wait(Until.findObject(By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN)), TIMEOUT);
        modeSpinner.click();

        boolean hasSingleAppOption = mDevice.hasObject(By.text(sSingleAppString));
        if (!hasSingleAppOption) {
            Log.i(TAG, "Unable to find single app option in spinner");
            return;
        }

        UiObject2 singleAppOption =
                mDevice.wait(Until.findObject(By.text(sSingleAppString)), TIMEOUT);
        singleAppOption.click();

        // Go to app selector page
        UiObject2 consentDialog = mDevice.wait(
                Until.findObject(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
        if (consentDialog == null) {
            Log.e(TAG, "Media projection consent dialog not found");
            return;
        }
        UiObject2 startRecordingButton =
                consentDialog.scrollUntil(
                        Direction.DOWN, Until.findObject(By.res(ACCEPT_RESOURCE_ID)));
        startRecordingButton.click();
        mDevice.wait(Until.gone(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
    }
}
