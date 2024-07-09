/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.media.cts;


import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.UiAutomatorUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


// This is a partial copy of android.view.cts.surfacevalidator.CapturedActivity.
// Common code should be move in a shared library

/** Start this activity to retrieve a MediaProjection through waitForMediaProjection() */
public class MediaProjectionActivity extends Activity {
    private static final String TAG = "MediaProjectionActivity";
    private static final int PERMISSION_CODE = 1;
    public static final int PERMISSION_DIALOG_WAIT_MS = 1000;
    public static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    public static final String CANCEL_RESOURCE_ID = "android:id/button2";
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    public static final String SCREEN_SHARE_OPTIONS_RESOURCE_ID =
            SYSTEM_UI_PACKAGE + ":id/screen_share_mode_options";
    public static final String ENTIRE_SCREEN_STRING_RES_NAME =
            "screen_share_permission_dialog_option_entire_screen";
    public static final String SINGLE_APP_STRING_RES_NAME =
            "screen_share_permission_dialog_option_single_app";

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private CountDownLatch mCountDownLatch;
    private boolean mProjectionServiceBound;

    private int mResultCode;
    private Intent mResultData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // UI automator need the screen ON in dismissPermissionDialog()
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mProjectionManager = getSystemService(MediaProjectionManager.class);
        mCountDownLatch = new CountDownLatch(1);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProjectionServiceBound) {
            mProjectionServiceBound = false;
        }
    }

    protected Intent getScreenCaptureIntent() {
        return mProjectionManager.createScreenCaptureIntent();
    }

    /**
     * Request to start a foreground service with type "mediaProjection",
     * it's free to run in either the same process or a different process in the package;
     * passing a messenger object to send signal back when the foreground service is up.
     */
    private void startMediaProjectionService() {
        ForegroundServiceUtil.requestStartForegroundService(this,
                getForegroundServiceComponentName(),
                this::createMediaProjection, null);
    }

    /**
     * @return the Intent result from navigating the consent dialogs
     */
    public Intent getResultData() {
        return mResultData;
    }

    /**
     * @return The component name of the foreground service for this test.
     */
    public ComponentName getForegroundServiceComponentName() {
        return new ComponentName(this, LocalMediaProjectionService.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            throw new IllegalStateException("Unknown request code: " + requestCode);
        }
        if (resultCode != RESULT_OK) {
            throw new IllegalStateException("User denied screen sharing permission");
        }
        Log.d(TAG, "onActivityResult");
        mResultCode = resultCode;
        mResultData = data;
        startMediaProjectionService();
    }

    private void createMediaProjection() {
        mMediaProjection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
        mCountDownLatch.countDown();
    }

    public MediaProjection waitForMediaProjection() throws InterruptedException {
        final long timeOutMs = 10000;
        final int retryCount = 5;
        int count = 0;
        // Sometimes system decides to rotate the permission activity to another orientation
        // right after showing it. This results in: uiautomation thinks that accept button appears,
        // we successfully click it in terms of uiautomation, but nothing happens,
        // because permission activity is already recreated.
        // Thus, we try to click that button multiple times.
        do {
            assertTrue("Can't get the permission", count <= retryCount);
            dismissPermissionDialog(/* isWatch= */
                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH),
                    getResourceString(this, ENTIRE_SCREEN_STRING_RES_NAME));
            count++;
        } while (!mCountDownLatch.await(timeOutMs, TimeUnit.MILLISECONDS));
        return mMediaProjection;
    }

    /** The permission dialog will be auto-opened by the activity - find it and accept */
    public static void dismissPermissionDialog(boolean isWatch,
            @Nullable String entireScreenString) {
        // Ensure the device is initialized before interacting with any UI elements.
        UiDevice.getInstance(getInstrumentation());
        if (entireScreenString != null && !isWatch) {
            // if not testing on a watch device, then we need to select the entire screen option
            // before pressing "Start recording" button. This is because single app capture is
            // not supported on watches.
            if (!selectEntireScreenOption(entireScreenString)) {
                Log.e(TAG, "Couldn't select entire screen option");
            }
        }
        pressStartRecording(isWatch);
    }

    @Nullable
    private static UiObject2 findUiObject(String resourceId) {
        return findUiObject(By.res(resourceId));
    }

    @Nullable
    private static UiObject2 findUiObject(BySelector selector) {
        // Check if the View can be found on the current screen.
        UiObject2 obj = waitForObject(selector);

        // If the View is not found on the current screen. Try scrolling around to find it.
        if (obj == null) {
            Log.w(TAG, "Couldn't find " + selector + ", now scrolling to it.");
            scrollToGivenResource(SCREEN_SHARE_OPTIONS_RESOURCE_ID);
            obj = waitForObject(selector);
        }
        if (obj == null) {
            Log.w(TAG, "Still couldn't find " + selector + ", now scrolling screen height.");
            try {
                obj = UiAutomatorUtils.waitFindObjectOrNull(selector);
            } catch (UiObjectNotFoundException e) {
                Log.e(TAG, "Error in looking for " + selector, e);
            }
        }

        if (obj == null) {
            Log.e(TAG, "Unable to find " + selector);
        }

        return obj;
    }

    private static boolean selectEntireScreenOption(String entireScreenString) {
        UiObject2 optionSelector = findUiObject(SCREEN_SHARE_OPTIONS_RESOURCE_ID);
        if (optionSelector == null) {
            Log.e(TAG, "Couldn't find option selector to select projection mode, "
                    + "even after scrolling");
            return false;
        }
        optionSelector.click();

        UiObject2 entireScreenOption = waitForObject(By.text(entireScreenString));
        if (entireScreenOption == null) {
            Log.e(TAG, "Couldn't find entire screen option");
            return false;
        }
        entireScreenOption.click();
        return true;
    }

    /**
     * Returns the string for the drop down option to capture the entire screen.
     */
    @Nullable
    public static String getResourceString(@NonNull Context context, String resName) {
        Resources sysUiResources;
        try {
            sysUiResources = context.getPackageManager()
                    .getResourcesForApplication(SYSTEM_UI_PACKAGE);
        } catch (NameNotFoundException e) {
            return null;
        }
        int resourceId =
                sysUiResources.getIdentifier(resName, /* defType= */ "string", SYSTEM_UI_PACKAGE);
        if (resourceId == 0) {
            // Resource id not found
            return null;
        }
        return sysUiResources.getString(resourceId);
    }

    private static void pressStartRecording(boolean isWatch) {
        // May need to scroll down to the start button on small screen devices.
        UiObject2 startRecordingButton = findUiObject(ACCEPT_RESOURCE_ID);
        if (startRecordingButton != null) {
            startRecordingButton.click();
        }
    }

    /** When testing on a small screen device, scrolls to a given UI element. */
    private static void scrollToGivenResource(String resourceId) {
        // Scroll down the dialog; on a device with a small screen the elements may not be visible.
        final UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            if (!scrollable.scrollIntoView(new UiSelector().resourceId(resourceId))) {
                Log.e(TAG, "Didn't find " + resourceId + " when scrolling");
                return;
            }
            Log.d(TAG, "We finished scrolling down to the ui element " + resourceId);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "There was no scrolling (UI may not be scrollable");
        }
    }

    private static UiObject2 waitForObject(BySelector selector) {
        UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
        return uiDevice.wait(Until.findObject(selector), PERMISSION_DIALOG_WAIT_MS);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }
}
