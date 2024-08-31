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
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class MediaProjectionTests {

    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    private static final String CANCEL_RESOURCE_ID = "android:id/button2";
    private static final String SPINNER_RESOURCE_ID =
            SYSTEM_UI_PACKAGE + ":id/screen_share_mode_options";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static String sSingleAppString;

    public static class MediaProjectionActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
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

        UiObject2 cancelButton =
                mDevice.wait(Until.findObject(By.res(CANCEL_RESOURCE_ID)), 5000L);
        cancelButton.click();
    }

    @Test
    public void testMediaProjectionShowAppSelector() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mActivityRule.launchActivity(null);
        mDevice.waitForIdle();

        // OEMs aren't guaranteed to support partial screenshare, so we only attempt
        // to reach the app selector if possible, and end the test prematurely if it isn't
        boolean hasModeSpinner = mDevice.hasObject(By.res(SPINNER_RESOURCE_ID));
        if (!hasModeSpinner) {
            Log.i("MediaProjectionTests", "Unable to find a screen share mode spinner");
            return;
        }

        UiObject2 modeSpinner =
                mDevice.wait(Until.findObject(By.res(SPINNER_RESOURCE_ID)), 5000L);
        modeSpinner.click();

        boolean hasSingleAppOption = mDevice.hasObject(By.text(sSingleAppString));
        if (!hasSingleAppOption) {
            Log.i("MediaProjectionTests", "Unable to find single app option in spinner");
            return;
        }

        UiObject2 singleAppOption =
                mDevice.wait(Until.findObject(By.text(sSingleAppString)), 5000L);
        singleAppOption.click();

        // Go to app selector page
        UiObject2 startRecordingButton =
                mDevice.wait(Until.findObject(By.res(ACCEPT_RESOURCE_ID)), 5000L);
        startRecordingButton.click();
    }
}
