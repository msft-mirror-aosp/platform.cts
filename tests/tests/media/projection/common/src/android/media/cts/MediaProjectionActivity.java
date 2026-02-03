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
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.UiAutomatorUtils2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

// This is a partial copy of android.view.cts.surfacevalidator.CapturedActivity.
// Common code should be move in a shared library

/** Start this activity to retrieve a MediaProjection through waitForMediaProjection() */
public class MediaProjectionActivity extends Activity {
    private static final int PERMISSION_CODE = 1;
    private static final int PERMISSION_DIALOG_WAIT_MS = 2000;
    private static final int TIMEOUT_MS = 10000;
    private static final String TAG = "MediaProjectionActivity";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SYSTEM_UI_PACKAGE_WEAR =
            "com.google.android.apps.wearable.systemui";

    // Builds from 24Q3 and earlier will have screen_share_mode_spinner, while builds from
    // 24Q4 onwards will have screen_share_mode_options, so need to check both options here
    private static final String SCREEN_SHARE_OPTIONS_REGEX =
            String.format(
                    "(%s|%s):id/screen_share_mode_(options|spinner)",
                    SYSTEM_UI_PACKAGE, SYSTEM_UI_PACKAGE_WEAR);

    // Extra used to specify a foreground service to use for the MediaProjection session.
    // If unset MediaProjection will default to the LocalMediaProjectionService implementation.
    public static final String EXTRA_FOREGROUND_SERVICE_CLASS = "extra_foreground_service_class";
    public static final String EXTRA_MP_CONFIG = "extra_mp_config";
    public static final String EXTRA_LAUNCH_COOKIE = "extra_launch_cookie";
    public static final String EXTRA_SKIP_CONSENT = "extra_skip_consent";
    public static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    public static final String CANCEL_RESOURCE_ID = "android:id/button2";
    public static final Pattern SCREEN_SHARE_OPTIONS_RES_PATTERN =
            Pattern.compile(SCREEN_SHARE_OPTIONS_REGEX);
    public static final String ENTIRE_SCREEN_STRING_RES_NAME =
            "screen_share_permission_dialog_option_entire_screen";
    public static final String SINGLE_APP_STRING_RES_NAME =
            "screen_share_permission_dialog_option_single_app";
    public static final String CONNECTED_DISPLAY_STRING_RES_NAME =
            "screen_share_permission_dialog_option_text_entire_screen_for_display";

    public static final String SHARE_TAB_TEST_TAG = "ShareTabOption";
    public static final String SHARE_APP_WINDOW_TEST_TAG = "ShareAppWindowOption";
    public static final String SHARE_ENTIRE_SCREEN_TEST_TAG = "ShareEntireScreenOption";
    public static final String SHARE_BUTTON_TEST_TAG = "ShareButton";
    public static final String SHARE_CONTENT_LIST_TEST_TAG = "ShareContentList";

    private boolean mHandleActivityResult = false;

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

        if (getIntent().hasExtra(EXTRA_SKIP_CONSENT)) {
            mHandleActivityResult = true;
        }

        if (savedInstanceState != null) {
            return;
        }

        startActivityForResult(createRequestIntent(), PERMISSION_CODE);
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

    private Intent createRequestIntent() {
        if (getIntent().hasExtra(EXTRA_MP_CONFIG)) {
            MediaProjectionConfig config =
                    getIntent().getParcelableExtra(EXTRA_MP_CONFIG, MediaProjectionConfig.class);
            return mProjectionManager.createScreenCaptureIntent(config);
        }
        if (getIntent().hasExtra(EXTRA_LAUNCH_COOKIE)) {
            ActivityOptions.LaunchCookie launchCookie =
                    getIntent()
                            .getParcelableExtra(
                                    EXTRA_LAUNCH_COOKIE, ActivityOptions.LaunchCookie.class);
            return mProjectionManager.createScreenCaptureIntent(launchCookie);
        }

        return mProjectionManager.createScreenCaptureIntent();
    }

    /**
     * Request to start a foreground service with type "mediaProjection", it's free to run in either
     * the same process or a different process in the package; passing a messenger object to send
     * signal back when the foreground service is up.
     */
    public MediaProjection startMediaProjection() throws InterruptedException, TimeoutException {
        CountDownLatch latch = new CountDownLatch(1);
        ForegroundServiceUtil.requestStartForegroundService(
                this,
                getForegroundServiceComponentName(),
                () -> {
                    createMediaProjection();
                    latch.countDown();
                },
                null);

        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Timed out starting MediaProjection");
        }
        return mMediaProjection;
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
    private ComponentName getForegroundServiceComponentName() {
        if (getIntent().hasExtra(EXTRA_FOREGROUND_SERVICE_CLASS)) {
            String fgsClass = getIntent().getStringExtra(EXTRA_FOREGROUND_SERVICE_CLASS);
            return new ComponentName(this, fgsClass);
        }

        return new ComponentName(this, android.media.cts.LocalMediaProjectionService.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Only handle onActivityResult if the caller actually tries to start
        Log.d(TAG, "onActivityResult mHandleActivityResult=" + mHandleActivityResult);
        if (!mHandleActivityResult) {
            return;
        }

        IllegalStateException exception = null;
        if (requestCode != PERMISSION_CODE) {
            exception = new IllegalStateException("Unknown request code: " + requestCode);
        }
        if (resultCode != RESULT_OK) {
            exception = new IllegalStateException("User denied screen sharing permission");
        }

        if (exception != null) {
            Log.e(TAG, exception.getMessage(), exception);
        }
        mResultCode = resultCode;
        mResultData = data;
        mCountDownLatch.countDown();
    }

    private void createMediaProjection() {
        mMediaProjection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    /** Perform the steps required to pass the MediaProjection consent flow */
    public void performMediaProjectionConsent(String displayName) throws InterruptedException {
        // If MediaProjection consent was skipped, instantly return
        if (mCountDownLatch.getCount() == 0) {
            return;
        }

        mHandleActivityResult = true;
        final int retryCount = 5;
        int count = 0;
        UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());

        // Sometimes system decides to rotate the permission activity to another orientation
        // right after showing it. This results in: uiautomation thinks that accept button appears,
        // we successfully click it in terms of uiautomation, but nothing happens,
        // because permission activity is already recreated.
        // Thus, we try to click that button multiple times.
        do {
            assertTrue("Can't get the permission", count <= retryCount);
            dismissPermissionDialog(this, uiDevice, displayName);
            count++;
        } while (!mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    /** The permission dialog will be auto-opened by the activity - find it and accept */
    private static void dismissPermissionDialog(
            Context context, UiDevice uiDevice, @Nullable String displayName) {
        uiDevice.waitForIdle();

        BySelector shareTabSelector = By.res(SHARE_TAB_TEST_TAG);
        boolean isNewUiPresent =
                uiDevice.wait(Until.hasObject(shareTabSelector), PERMISSION_DIALOG_WAIT_MS);

        if (isNewUiPresent) {
            Log.d(TAG, "Compose permission UI detected via testTag: " + SHARE_TAB_TEST_TAG);
            dismissNewComposePermissionDialog(uiDevice, displayName);
        } else {
            Log.d(TAG, "Old AlertDialog permission UI detected.");
            String optionString =
                    displayName != null
                            ? getResourceString(
                                    context, CONNECTED_DISPLAY_STRING_RES_NAME, displayName)
                            : getResourceString(context, ENTIRE_SCREEN_STRING_RES_NAME);
            dismissOldAlertDialog(uiDevice, optionString);
        }
    }

    private static void dismissNewComposePermissionDialog(
            UiDevice uiDevice, @Nullable String displayName) {
        UiObject2 shareButton =
                findAndEnableNewShareButton(uiDevice, displayName, PERMISSION_DIALOG_WAIT_MS);
        if (shareButton == null) {
            Log.e(
                    TAG,
                    "Could not find Compose Share button with testTag: " + SHARE_BUTTON_TEST_TAG);
            return;
        }

        // Wait for the button to be enabled after the display selection.
        boolean enabled = shareButton.wait(Until.enabled(true), PERMISSION_DIALOG_WAIT_MS);
        if (enabled) {
            shareButton.click();
            Log.d(TAG, "Clicked enabled Compose Share button.");
            uiDevice.wait(Until.gone(By.res(SHARE_TAB_TEST_TAG)), PERMISSION_DIALOG_WAIT_MS);
        } else {
            Log.e(
                    TAG,
                    "The 'Share' button did not become enabled after attempting to select a"
                            + " display. The test will likely fail.");
        }
    }

    /**
     * Finds and enables the "Share" button on the new (Compose) permission dialog.
     *
     * <p>This involves selecting the "Entire Screen" option and then selecting a display, which
     * should enable the share button.
     *
     * @param uiDevice The {@link UiDevice} instance to use.
     * @param displayName The name of the display to select. If {@code null}, the first available
     *     display will be selected.
     * @param timeoutMs The timeout in milliseconds to wait for UI elements.
     * @return The "Share" button as a {@link UiObject2}, or {@code null} if not found.
     */
    @Nullable
    public static UiObject2 findAndEnableNewShareButton(
            UiDevice uiDevice, @Nullable String displayName, int timeoutMs) {
        UiObject2 shareEntireScreenButton =
                uiDevice.wait(Until.findObject(By.res(SHARE_ENTIRE_SCREEN_TEST_TAG)), timeoutMs);

        if (shareEntireScreenButton == null) {
            Log.e(
                    TAG,
                    "Could not find 'Entire Screen' option with testTag: "
                            + SHARE_ENTIRE_SCREEN_TEST_TAG);
            return null;
        }

        Log.d(TAG, "Found 'Entire Screen' option, clicking it.");
        shareEntireScreenButton.click();
        uiDevice.waitForIdle();

        UiObject2 contentList =
                uiDevice.wait(Until.findObject(By.res(SHARE_CONTENT_LIST_TEST_TAG)), timeoutMs);

        if (contentList == null) {
            Log.e(TAG, "Could not find content list with testTag: " + SHARE_CONTENT_LIST_TEST_TAG);
            return null;
        }

        Log.d(TAG, "Found content list with testTag: " + SHARE_CONTENT_LIST_TEST_TAG);
        UiObject2 displayToClick = null;

        // Attempt specific search if name is provided.
        if (displayName != null) {
            displayToClick = contentList.findObject(By.textContains(displayName));
        }

        // Fallback to first clickable if name search failed or was skipped.
        if (displayToClick == null) {
            if (displayName != null) {
                Log.w(TAG, "Could not find display with name: " + displayName + ". Falling back.");
            }
            displayToClick = contentList.findObject(By.clickable(true));
        }

        // Final safety check.
        if (displayToClick == null) {
            Log.e(TAG, "No clickable items found in content list.");
            return null;
        }

        Log.d(TAG, "Clicking item: " + displayToClick.getText());
        displayToClick.click();

        uiDevice.waitForIdle();
        // Find and click the "Share" button, which should now be enabled.
        return uiDevice.wait(Until.findObject(By.res(SHARE_BUTTON_TEST_TAG)), timeoutMs);
    }

    private static void dismissOldAlertDialog(UiDevice uiDevice, @Nullable String optionString) {
        if (optionString != null) {
            if (!selectScreenOption(uiDevice, optionString)) {
                Log.e(TAG, "Couldn't select screen option: " + optionString);
        }
        }
        pressStartRecording(uiDevice);
    }

    @Nullable
    private static UiObject2 findUiObject(
            UiDevice uiDevice, BySelector selector, UiSelector uiSelector) {
        // Check if the View can be found on the current screen.
        UiObject2 obj = waitForObject(uiDevice, selector);

        // If the View is not found on the current screen. Try scrolling around to find it.
        if (obj == null) {
            Log.w(TAG, "Couldn't find " + selector + ", now scrolling to it.");
            scrollToGivenResource(uiSelector);
            obj = waitForObject(uiDevice, selector);
        }
        if (obj == null) {
            Log.w(TAG, "Still couldn't find " + selector + ", now scrolling screen height.");
            try {
                obj = UiAutomatorUtils2.waitFindObjectOrNull(selector);
            } catch (UiObjectNotFoundException e) {
                Log.e(TAG, "Error in looking for " + selector, e);
            }
        }

        if (obj == null) {
            Log.e(TAG, "Unable to find " + selector);
        }

        return obj;
    }

    private static boolean selectScreenOption(UiDevice uiDevice, String optionString) {
        UiObject2 optionSelector =
                findUiObject(
                        uiDevice,
                        By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN),
                        new UiSelector().resourceIdMatches(SCREEN_SHARE_OPTIONS_REGEX));
        if (optionSelector == null) {
            Log.e(
                    TAG,
                    "Couldn't find option selector to select projection mode, "
                            + "even after scrolling");
            return false;
        }
        optionSelector.click();

        uiDevice.waitForWindowUpdate(null, PERMISSION_DIALOG_WAIT_MS);
        UiObject2 optionItem = waitForObject(uiDevice, By.text(optionString));
        if (optionItem == null) {
            Log.e(TAG, "Couldn't find entire screen option");
            return false;
        }
        optionItem.click();
        return true;
    }

    /** Returns the string for the drop down option to capture the entire screen. */
    @Nullable
    public static String getResourceString(
            @NonNull Context context, String resName, Object... args) {
        boolean isWatch =
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        String systemUiPackage = isWatch ? SYSTEM_UI_PACKAGE_WEAR : SYSTEM_UI_PACKAGE;
        return getResourceString(context, systemUiPackage, resName, args);
    }

    @Nullable
    private static String getResourceString(
            Context context, String packageName, String resName, Object[] args) {
        Resources sysUiResources;
        try {
            sysUiResources = context.getPackageManager().getResourcesForApplication(packageName);
        } catch (NameNotFoundException e) {
            return null;
        }
        int resourceId =
                sysUiResources.getIdentifier(resName, /* defType= */ "string", packageName);
        if (resourceId == 0) {
            // Resource id not found
            return null;
        }
        return sysUiResources.getString(resourceId, args);
    }

    private static void pressStartRecording(UiDevice uiDevice) {
        // May need to scroll down to the start button on small screen devices.
        UiObject2 startRecordingButton =
                findUiObject(
                        uiDevice,
                        By.res(ACCEPT_RESOURCE_ID),
                        new UiSelector().resourceId(ACCEPT_RESOURCE_ID));
        if (startRecordingButton != null) {
            startRecordingButton.click();
        }
    }

    /** When testing on a small screen device, scrolls to a given UI element. */
    private static void scrollToGivenResource(UiSelector uiSelector) {
        // Scroll down the dialog; on a device with a small screen the elements may not be visible.
        final UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            if (!scrollable.scrollIntoView(uiSelector)) {
                Log.e(TAG, "Didn't find " + uiSelector + " when scrolling");
                return;
            }
            Log.d(TAG, "We finished scrolling down to the ui element " + uiSelector);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "There was no scrolling (UI may not be scrollable");
        }
    }

    private static UiObject2 waitForObject(UiDevice uiDevice, BySelector selector) {
        return uiDevice.wait(Until.findObject(selector), PERMISSION_DIALOG_WAIT_MS);
    }

    /** Returns true if the MediaProjection permission dialog is using the new Compose UI. */
    public static boolean isComposeUI(UiDevice device) {
        return device.hasObject(By.res(SHARE_TAB_TEST_TAG));
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
