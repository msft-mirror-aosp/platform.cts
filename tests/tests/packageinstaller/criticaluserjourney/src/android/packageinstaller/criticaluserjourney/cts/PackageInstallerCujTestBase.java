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

package android.packageinstaller.criticaluserjourney.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED;
import static android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION;
import static android.content.pm.PackageInstaller.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The test base to test PackageInstaller CUJs.
 */
public class PackageInstallerCujTestBase {
    private static final String TAG = "PackageInstallerCujTestBase";

    private static final String CONTENT_AUTHORITY =
            "android.packageinstaller.criticaluserjourney.cts.fileprovider";
    private static final String TEST_APK_LABEL = "Installer CUJ Test App";
    private static final String TEST_APK_NAME = "CtsInstallerCujTestApp.apk";
    private static final String TEST_APK_V2_NAME = "CtsInstallerCujTestAppV2.apk";
    private static final String TEST_APK_PACKAGE_NAME =
            "android.packageinstaller.cts.cuj.app";
    private static final String TEST_INSTALLER_LABEL = "CTS CUJ Installer";
    private static final String TEST_INSTALLER_PACKAGE_NAME =
            "android.packageinstaller.cts.cuj.installer";
    private static final String TEST_INSTALLER_APK_NAME = "CtsInstallerCujTestInstaller.apk";
    private static final String TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller/cuj";
    private static final String APP_INSTALLED_LABEL = "App installed";
    private static final String BUTTON_CANCEL_LABEL = "Cancel";
    private static final String BUTTON_DONE_LABEL = "Done";
    private static final String BUTTON_GPP_MORE_DETAILS_LABEL = "More details";
    private static final String BUTTON_GPP_INSTALL_WITHOUT_SCANNING_LABEL =
            "Install without scanning";
    private static final String BUTTON_INSTALL_LABEL = "Install";
    private static final String BUTTON_OPEN_LABEL = "Open";
    private static final String BUTTON_SETTINGS_LABEL = "Settings";
    private static final String BUTTON_UPDATE_LABEL = "Update";
    private static final String TOGGLE_ALLOW_LABEL = "allow";
    private static final String TOGGLE_ALLOW_FROM_LABEL = "Allow from";
    private static final String TOGGLE_ALLOW_PERMISSION_LABEL = "allow permission";
    private static final String TOGGLE_INSTALL_UNKNOWN_APPS_LABEL = "install unknown apps";
    private static final String INSTALLING_LABEL = "Installing";
    private static final String TEXTVIEW_WIDGET_CLASSNAME = "android.widget.TextView";

    private static final String ACTION_LAUNCH_INSTALLER =
            "android.packageinstaller.cts.cuj.installer.action.LAUNCH_INSTALLER";

    private static final String ACTION_REQUEST_INSTALLER =
            "android.packageinstaller.cts.cuj.installer.action.REQUEST_INSTALLER";

    private static final String ACTION_RESPONSE_INSTALLER =
            "android.packageinstaller.cts.cuj.installer.action.RESPONSE_INSTALLER";

    private static final String EXTRA_EVENT = "extra_event";
    private static final String EXTRA_TEST_APK_URI = "extra_test_apk_uri";
    private static final String EXTRA_TEST_APK_V2_URI = "extra_test_apk_v2_uri";
    private static final String EXTRA_USE_APK_V2 = "extra_use_apk_v2";

    private static final int EVENT_REQUEST_INSTALLER_CLEAN_UP = -1;
    private static final int EVENT_REQUEST_INSTALLER_SESSION = 0;
    private static final int EVENT_REQUEST_INSTALLER_INTENT = 1;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_FOR_RESULT = 2;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI = 3;
    private static final int EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI_FOR_RESULT = 4;

    private static final int STATUS_CUJ_INSTALLER_READY = 1000;
    private static final int STATUS_CUJ_INSTALLER_START_ACTIVITY_READY = 1001;

    private static final long FIND_OBJECT_TIMEOUT_MS = 30 * 1000L;
    private static final long WAIT_OBJECT_GONE_TIMEOUT_MS = 3 * 1000L;

    private static final long TEST_APK_VERSION = 1;
    private static final long TEST_APK_V2_VERSION = 2;

    @ClassRule
    public static final DisableAnimationRule sDisableAnimationRule = new DisableAnimationRule();

    private static Context sContext;
    private static PackageManager sPackageManager;
    private static InstallerResponseReceiver sInstallerResponseReceiver;
    private static Instrumentation sInstrumentation;
    private static UiDevice sUiDevice;
    private static String sToggleLabel = null;
    private static String sPackageInstallerPackageName = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sContext = sInstrumentation.getTargetContext();
        sPackageManager = sContext.getPackageManager();
        sInstallerResponseReceiver = new InstallerResponseReceiver();
        sPackageInstallerPackageName = getPackageInstallerPackageName();
        Log.d(TAG, "sPackageInstallerPackageName = " + sPackageInstallerPackageName);

        copyTestFiles();

        // Unblock UI
        sUiDevice = UiDevice.getInstance(sInstrumentation);
        if (!sUiDevice.isScreenOn()) {
            sUiDevice.wakeUp();
        }
        sUiDevice.executeShellCommand("wm dismiss-keyguard");

        sContext.registerReceiver(sInstallerResponseReceiver,
                new IntentFilter(ACTION_RESPONSE_INSTALLER), Context.RECEIVER_EXPORTED);
    }

    @Before
    public void setup() throws Exception {
        assumeFalse("The device is not supported", isNotSupportedDevice());

        assumeFalse("The device doesn't have package installer",
                sPackageInstallerPackageName == null);

        uninstallTestPackage();
        assertTestPackageNotInstalled();

        uninstallInstallerPackage();
        assertInstallerNotInstalled();

        sInstallerResponseReceiver.resetResult();

        // install the test installer before the test case is running everytime to make sure the
        // AppOps permission mode is the default mode.
        installPackage(TEST_INSTALLER_APK_NAME);
        assertThat(isInstalled(TEST_INSTALLER_PACKAGE_NAME)).isTrue();
        startInstallerActivity();

        waitForUiIdle();
        assertCUJInstallerReady();
    }

    @After
    public void tearDown() throws Exception {
        requestInstallerCleanUp();

        uninstallTestPackage();
        uninstallInstallerPackage();
        // to avoid any UI is still on the screen
        pressBack();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        sInstallerResponseReceiver.unregisterReceiver(sContext);
        sInstallerResponseReceiver = null;
        sPackageManager = null;
        sContext = null;
        sUiDevice = null;
        sInstrumentation = null;
    }

    /**
     * Grant the REQUEST_INSTALL_PACKAGES AppOps permission to the CUJ Installer.
     */
    public static void grantRequestInstallPackagesPermission() throws Exception {
        AppOpsUtils.setOpMode(TEST_INSTALLER_PACKAGE_NAME, OPSTR_REQUEST_INSTALL_PACKAGES,
                MODE_ALLOWED);
    }

    private static void copyTestFiles() throws Exception {
        final File apkFile = new File(TEST_APK_LOCATION, TEST_APK_NAME);
        final File dstFile = new File(sContext.getFilesDir(), TEST_APK_NAME);
        copyFile(apkFile, dstFile);

        final File apkV2File = new File(TEST_APK_LOCATION, TEST_APK_V2_NAME);
        final File dstV2File = new File(sContext.getFilesDir(), TEST_APK_V2_NAME);
        copyFile(apkV2File, dstV2File);
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream source = new FileInputStream(src);
                OutputStream target = new FileOutputStream(dst)) {
            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }

    private static void startInstallerActivity() {
        final File apkFile = new File(sContext.getFilesDir(), TEST_APK_NAME);
        final File apkV2File = new File(sContext.getFilesDir(), TEST_APK_V2_NAME);
        final Intent intent = new Intent();
        intent.setPackage(TEST_INSTALLER_PACKAGE_NAME);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        intent.setAction(ACTION_LAUNCH_INSTALLER);
        Uri testApkUri = FileProvider.getUriForFile(sContext, CONTENT_AUTHORITY, apkFile);
        Uri testApkV2Uri = FileProvider.getUriForFile(sContext, CONTENT_AUTHORITY, apkV2File);
        intent.putExtra(EXTRA_TEST_APK_URI, testApkUri.toString());
        intent.putExtra(EXTRA_TEST_APK_V2_URI, testApkV2Uri.toString());

        // grant read uri permission to the installer
        sContext.grantUriPermission(TEST_INSTALLER_PACKAGE_NAME, testApkUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sContext.grantUriPermission(TEST_INSTALLER_PACKAGE_NAME, testApkV2Uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sContext.startActivity(intent);
    }

    private static void requestInstallerCleanUp() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_CLEAN_UP);
    }

    /**
     * Start the installation via PackageInstaller.Session APIs.
     */
    public static void startInstallationViaPackageInstallerSession() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_SESSION);
        assertInstallPendingUserAction();
    }

    /**
     * Start the installation to update the test apk from version 1 to version 2 via
     * PackageInstaller.Session APIs.
     */
    public static void startInstallationUpdateViaPackageInstallerSession() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_SESSION, /* useV2= */ true);
        assertInstallPendingUserAction();
    }

    private static void sendRequestInstallerBroadcast(int event) throws Exception {
        sendRequestInstallerBroadcast(event, /* useV2= */ false);
    }

    /**
     * Start the installation via startActivity.
     */
    public static void startInstallationViaIntent() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation to update the test apk from version 1 to version 2
     * via startActivity.
     */
    public static void startInstallationUpdateViaIntent() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT, /* useV2= */ true);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation via startActivity with Package uri.
     */
    public static void startInstallationViaIntentWithPackageUri() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation via startActivityForResult.
     */
    public static void startInstallationViaIntentForResult() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_FOR_RESULT);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation to update the test apk from version 1 to version 2
     * via startActivityForResult.
     */
    public static void startInstallationUpdateViaIntentForResult() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_FOR_RESULT,
                /* useV2= */ true);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation via startActivityForResult with Package uri.
     */
    public static void startInstallationViaIntentWithPackageUriForResult() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_WITH_PACKAGE_URI_FOR_RESULT);
        assertCUJInstallerStartActivityReady();
    }

    private static void sendRequestInstallerBroadcast(int event, boolean useV2) throws Exception {
        final Intent intent = new Intent(ACTION_REQUEST_INSTALLER);
        intent.setPackage(TEST_INSTALLER_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(EXTRA_EVENT, event);
        intent.putExtra(EXTRA_USE_APK_V2, useV2);
        sContext.sendBroadcast(intent);
    }

    private static int getInstallerResponseResult() throws Exception {
        return sInstallerResponseReceiver.getInstallerResponseResult();
    }

    /**
     * Assert the install status is Activity#RESULT_OK).
     */
    public static void assertInstallerResponseActivityResultOK() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(Activity.RESULT_OK);
        sInstallerResponseReceiver.resetResult();
    }

    /**
     * Assert the install status is Activity#RESULT_CANCELED.
     */
    public static void assertInstallerResponseActivityResultCanceled() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(Activity.RESULT_CANCELED);
        sInstallerResponseReceiver.resetResult();
    }

    /**
     * Assert the install status is PackageInstaller#STATUS_SUCCESS.
     */
    public static void assertInstallSuccess() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(STATUS_SUCCESS);
        sInstallerResponseReceiver.resetResult();
    }

    /**
     * Assert the install status is PackageInstaller#STATUS_FAILURE_ABORTED.
     */
    public static void assertInstallFailureAborted() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(STATUS_FAILURE_ABORTED);
        sInstallerResponseReceiver.resetResult();
    }

    private static void assertInstallPendingUserAction() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(STATUS_PENDING_USER_ACTION);
        sInstallerResponseReceiver.resetResult();
    }

    private static void assertCUJInstallerReady() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(STATUS_CUJ_INSTALLER_READY);
        sInstallerResponseReceiver.resetResult();
    }

    private static void assertCUJInstallerStartActivityReady() throws Exception {
        assertThat(getInstallerResponseResult()).isEqualTo(
                STATUS_CUJ_INSTALLER_START_ACTIVITY_READY);
        sInstallerResponseReceiver.resetResult();
    }

    /**
     * Assert the test package that is the version 1 is installed.
     */
    public static void assertTestPackageInstalled() {
        assertThat(isInstalledAndVerifyVersionCode(
                TEST_APK_PACKAGE_NAME, TEST_APK_VERSION)).isTrue();
    }

    /**
     * Assert the test package that is the version 2 is installed.
     */
    public static void assertTestPackageVersion2Installed() {
        assertThat(isTestPackageVersion2Installed()).isTrue();
    }

    /**
     * Assert the test package is NOT installed.
     */
    public static void assertTestPackageNotInstalled() {
        assertThat(isTestPackageInstalled()).isFalse();
    }

    private static void assertInstallerNotInstalled() {
        assertThat(isInstallerInstalled()).isFalse();
    }

    /**
     * Wait for the device idle.
     */
    public static void waitForUiIdle() {
        sUiDevice.waitForIdle();
    }

    /**
     * Press the back key.
     */
    public static void pressBack() {
        sUiDevice.pressBack();
        waitForUiIdle();
    }

    private static void clickAndWaitForNewWindow(UiObject2 uiObject2) {
        uiObject2.clickAndWait(Until.newWindow(), WAIT_OBJECT_GONE_TIMEOUT_MS);
    }

    private static void allowInstallIfGPPDialogExists() {
        final Pattern morePattern = Pattern.compile(BUTTON_GPP_MORE_DETAILS_LABEL,
                Pattern.CASE_INSENSITIVE);
        UiObject2 more = sUiDevice.findObject(By.text(morePattern));
        if (more != null) {
            more.click();
            waitForUiIdle();

            BySelector installWithoutScanningSelector = By.textContains(
                    BUTTON_GPP_INSTALL_WITHOUT_SCANNING_LABEL);
            UiObject2 installAnyway = findObject(installWithoutScanningSelector,
                    /* checkNull= */ false);
            if (installAnyway != null) {
                Rect rect = installAnyway.getVisibleBounds();
                sUiDevice.click(rect.left, rect.bottom - 10);
                // wait for the dialog disappear
                waitUntilObjectGone(installWithoutScanningSelector);
            }
        }
        waitForUiIdle();
    }

    /**
     * Assert the title of the install dialog is {@link #TEST_APK_LABEL}.
     */
    private static void assertTitleIsTestApkLabel() throws Exception {
        findPackageInstallerObject(TEST_APK_LABEL);
    }

    /**
     * Assert the title of the install dialog is {@link #TEST_INSTALLER_LABEL}.
     */
    private static void assertTitleIsTestInstallerLabel() throws Exception {
        findPackageInstallerObject(TEST_INSTALLER_LABEL);
    }

    /**
     * Assert the Install button of the install dialog exists.
     */
    private static void assertInstallButton() throws Exception {
        findPackageInstallerObject(BUTTON_INSTALL_LABEL);
    }

    /**
     * Assert the Update button of the install dialog exists.
     */
    private static void assertUpdateButton() throws Exception {
        findPackageInstallerObject(BUTTON_UPDATE_LABEL);
    }

    /**
     * Assert the install dialog for installing the test app.
     */
    public static void assertTestAppInstallDialog() throws Exception {
        assertTitleIsTestApkLabel();
        assertInstallButton();
    }

    /**
     * Assert the update dialog for installing the test app.
     */
    public static void assertTestAppUpdateDialog() throws Exception {
        assertTitleIsTestApkLabel();
        assertUpdateButton();
    }

    /**
     * Assert the install success dialog and launch the test app. Assert the label of test
     * app is {@link #TEST_APK_LABEL}.
     */
    public static void assertInstallSuccessDialogAndLaunchTestApp() throws Exception {
        // Assert the label and Done button exists
        findPackageInstallerObject(By.textContains(APP_INSTALLED_LABEL), /* checkNull= */ true);
        findPackageInstallerObject(BUTTON_DONE_LABEL);

        // Click the Open button to launch the test app
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_OPEN_LABEL));

        // Assert the activity is launched successfully
        findObject(By.text(TEST_APK_LABEL).pkg(TEST_APK_PACKAGE_NAME), /* checkNull= */ true);

        // Press back to leave the test app
        pressBack();
    }

    /**
     * Click the Install button and wait for the dialog to disappear. Also allow install if the
     * GPP dialog exists.
     */
    public static void clickInstallButton() throws Exception {
        clickInstallButton(/* checkInstallingDialog= */ false);
    }

    /**
     * Click the Install button and wait for the dialog to disappear. Also allow install if the
     * GPP dialog exists. If {@code checkInstallingDialog} is true, check the Installing dialog.
     * Otherwise, don't check the Installing dialog. E.g. The installation via intent triggers
     * the Installing dialog.
     */
    public static void clickInstallButton(boolean checkInstallingDialog) throws Exception {
        assertTitleIsTestApkLabel();

        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_INSTALL_LABEL));

        if (checkInstallingDialog) {
            waitForInstallingDialogGone();
        }

        if (!isTestPackageInstalled()) {
            allowInstallIfGPPDialogExists();
        }
    }

    /**
     * Click the Update button and wait for the dialog to disappear. Also allow install if the
     * GPP dialog exists.
     */
    public static void clickUpdateButton() throws Exception {
        clickUpdateButton(/* checkInstallingDialog= */ false);
    }

    /**
     * Click the Update button and wait for the dialog to disappear. Also allow install if the
     * GPP dialog exists. If {@code checkInstallingDialog} is true, check the Installing dialog.
     * Otherwise, don't check the Installing dialog. E.g. The installation via intent triggers
     * the Installing dialog.
     */
    public static void clickUpdateButton(boolean checkInstallingDialog) throws Exception {
        clickUpdateButton(checkInstallingDialog, /* isUpdatedViaPackageUri= */ false);
    }

    /**
     * Click the Update button, assert the title is {@link #TEST_APK_LABEL} and wait for the
     * dialog to disappear. If {@code checkInstallingDialog} is true, check the Installing
     * dialog. Otherwise, don't check the Installing dialog. E.g. The installation via intent
     * triggers Installing dialog. If {@code isUpdatedViaPackageUri} is true, do NOT check the
     * GPP dialog. Otherwise, check the GPP dialog. The installation via intent with package
     * uri doesn't trigger the GPP dialog.
     */
    public static void clickUpdateButton(boolean checkInstallingDialog,
            boolean isUpdatedViaPackageUri) throws Exception {
        assertTitleIsTestApkLabel();

        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_UPDATE_LABEL));

        if (checkInstallingDialog) {
            waitForInstallingDialogGone();
        }

        if (!isUpdatedViaPackageUri && !isTestPackageVersion2Installed()) {
            allowInstallIfGPPDialogExists();
        }
    }

    /**
     * Click the Cancel button and wait for the dialog to disappear.
     */
    public static void clickCancelButton() throws Exception {
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_CANCEL_LABEL));
    }

    /**
     * Click the Settings button and wait for the dialog to disappear. Also assert the title of
     * the dialog is {@link #TEST_INSTALLER_LABEL}.
     */
    public static void clickSettingsButton() throws Exception {
        assertTitleIsTestInstallerLabel();
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_SETTINGS_LABEL));
    }

    /**
     * Toggle to grant the AppOps permission REQUEST_INSTALL_PACKAGES to the CUJ Installer.
     */
    public static void toggleToGrantRequestInstallPackagesPermission() {
        // Already know which toggle label on the device, find it and click it directly
        if (sToggleLabel != null) {
            clickAndWaitForNewWindow(findObject(sToggleLabel));
            return;
        }

        // Start to find the objects, find the checkable items first
        final List<UiObject2> uiObjects = sUiDevice.wait(
                Until.findObjects(By.checkable(true).checked(false)), FIND_OBJECT_TIMEOUT_MS);

        if (uiObjects == null || uiObjects.isEmpty()) {
            fail("No toggle to grant permission");
        }

        Log.d(TAG, "The count of checkable objects is " + uiObjects.size());

        // Only one item, find the text object
        if (uiObjects.size() == 1) {
            UiObject2 toggle = uiObjects.get(0);
            logUiObject(toggle);
            UiObject2 text = findSiblingTextObject(toggle);
            if (text != null) {
                sToggleLabel = text.getText();
                clickAndWaitForNewWindow(text);
                return;
            }
            clickAndWaitForNewWindow(toggle);
            return;
        }

        UiObject2 text = null;
        for (int i = 0; i < uiObjects.size(); i++) {
            UiObject2 toggle = uiObjects.get(i);
            text = findSiblingTextObject(toggle);
            if (text != null) {
                break;
            }
        }
        if (text != null) {
            sToggleLabel = text.getText();
            clickAndWaitForNewWindow(text);
        } else {
            fail("Do NOT find the suitable toggle to grant permission!");
        }
    }

    /**
     * Exit the grant permission settings and wait for it to disappear.
     */
    public static void exitGrantPermissionSettings() {
        pressBack();
        waitForUiIdle();
        if (sToggleLabel != null) {
            // wait for exiting the grant permission settings
            waitUntilObjectGone(By.text(sToggleLabel));
        }
    }

    /**
     * Touch outside of the PackageInstaller dialog.
     */
    public static void touchOutside() {
        DisplayMetrics displayMetrics = sContext.getResources().getDisplayMetrics();
        sUiDevice.click(displayMetrics.widthPixels / 3, displayMetrics.heightPixels / 10);
        waitForUiIdle();
    }

    private static void waitForInstallingDialogGone() {
        BySelector installingSelector =
                getPackageInstallerBySelector(By.textContains(INSTALLING_LABEL));
        UiObject2 installing = sUiDevice.findObject(installingSelector);
        if (installing != null) {
            waitUntilObjectGone(installingSelector);
        }
    }

    @Nullable
    private static UiObject2 findSiblingTextObject(@NonNull UiObject2 uiObject) {
        UiObject2 parent = uiObject.getParent();
        if (parent == null) {
            return null;
        }

        // If the child count is 1, it means the parent object only has the uiObject.
        // Try to find the parent's parent that has more than two children.
        while (parent.getChildCount() <= 1) {
            parent = parent.getParent();
            if (parent == null) {
                return null;
            }
        }

        // Find all TextViews to match the label
        final List<UiObject2> uiObjects = parent.findObjects(By.clazz(TEXTVIEW_WIDGET_CLASSNAME));
        Log.d(TAG, "The count of findSiblingTextObject objects is " + uiObjects.size());
        for (int i = 0; i < uiObjects.size(); i++) {
            UiObject2 uiObject2 = uiObjects.get(i);
            if (uiObject2 != null) {
                logUiObject(uiObject2);
                if (uiObject2.getText() != null) {
                    String label = uiObject2.getText().toLowerCase(Locale.ROOT);
                    if (label.contains(TOGGLE_ALLOW_FROM_LABEL)
                            || label.contains(TOGGLE_ALLOW_PERMISSION_LABEL)
                            || label.contains(TOGGLE_INSTALL_UNKNOWN_APPS_LABEL)
                            || label.contains(TOGGLE_ALLOW_LABEL)) {
                        return uiObject2;
                    }
                }
            }
        }
        return null;
    }

    private static void logUiObject(@NonNull UiObject2 uiObject) {
        Log.d(TAG, "Found bounds: " + uiObject.getVisibleBounds()
                + " of object: " + uiObject + ", text: " + uiObject.getText()
                + ", package: " + uiObject.getApplicationPackage() + ", className: "
                + uiObject.getClassName());
    }

    private static BySelector getPackageInstallerBySelector(BySelector bySelector) {
        return bySelector.pkg(sPackageInstallerPackageName);
    }

    private static UiObject2 findPackageInstallerObject(String name) {
        final Pattern namePattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
        return findPackageInstallerObject(By.text(namePattern), /* checkNull= */ true);
    }

    private static UiObject2 findPackageInstallerObject(BySelector bySelector, boolean checkNull) {
        return findObject(getPackageInstallerBySelector(bySelector), checkNull);
    }

    private static UiObject2 findObject(String name) {
        final Pattern namePattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
        return findObject(By.text(namePattern), /* checkNull= */ true);
    }

    @Nullable
    private static UiObject2 findObject(BySelector bySelector, boolean checkNull) {
        return findObject(bySelector, checkNull, FIND_OBJECT_TIMEOUT_MS);
    }

    @Nullable
    private static UiObject2 findObject(BySelector bySelector, boolean checkNull, long timeoutMs) {
        waitForUiIdle();

        UiObject2 object = null;
        long startTime = System.currentTimeMillis();
        while (startTime + timeoutMs > System.currentTimeMillis()) {
            try {
                object = sUiDevice.wait(Until.findObject(bySelector), /* timeout= */ 10 * 1000);
                if (object != null) {
                    Log.d(TAG, "Found bounds: " + object.getVisibleBounds()
                            + " of object: " + bySelector + ", text: " + object.getText()
                            + " package: " + object.getApplicationPackage());
                    return object;
                } else {
                    // Maybe the screen is small. Scroll forward and attempt to click
                    new UiScrollable(new UiSelector().scrollable(true)).scrollForward();
                }
            } catch (Exception ignored) {
                // do nothing
            }
        }
        if (checkNull) {
            assertWithMessage("Can't find object " + bySelector).that(object).isNotNull();
        }
        return object;
    }

    private static void waitUntilObjectGone(BySelector bySelector) {
        if (!sUiDevice.wait(Until.gone(bySelector), WAIT_OBJECT_GONE_TIMEOUT_MS)) {
            fail("The Object: " + bySelector + "did not disappear within "
                    + WAIT_OBJECT_GONE_TIMEOUT_MS + " milliseconds");
        }
        waitForUiIdle();
    }

    private static void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(String.format("pm uninstall %s", packageName));
    }

    private static void uninstallTestPackage() {
        uninstallPackage(TEST_APK_PACKAGE_NAME);
    }

    private static void uninstallInstallerPackage() {
        uninstallPackage(TEST_INSTALLER_PACKAGE_NAME);
    }

    /**
     * Install the test apk.
     */
    public static void installTestPackage() throws IOException {
        installPackage(TEST_APK_NAME);
        assertTestPackageInstalled();
    }

    private static void installPackage(@NonNull String apkName) throws IOException {
        Log.d(TAG, "installPackage(): apkName= " + apkName);
        SystemUtil.runShellCommand("pm install -t "
                + new File(TEST_APK_LOCATION, apkName).getCanonicalPath());
    }

    private static boolean isTestPackageInstalled() {
        return isInstalled(TEST_APK_PACKAGE_NAME);
    }

    private static boolean isInstallerInstalled() {
        return isInstalled(TEST_INSTALLER_PACKAGE_NAME);
    }

    private static boolean isInstalled(@NonNull String packageName) {
        Log.d(TAG, "Testing if package " + packageName + " is installed for user "
                + sContext.getUser());
        try {
            sPackageManager.getPackageInfo(packageName, /* flags= */ 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(TAG, "Package " + packageName + " not installed for user "
                    + sContext.getUser() + ": " + e);
            return false;
        }
    }

    private static boolean isTestPackageVersion2Installed() {
        return isInstalledAndVerifyVersionCode(TEST_APK_PACKAGE_NAME, TEST_APK_V2_VERSION);
    }

    private static boolean isInstalledAndVerifyVersionCode(@NonNull String packageName,
            long versionCode) {
        Log.d(TAG, "Testing if package " + packageName + " is installed for user "
                + sContext.getUser() + ", with version code " + versionCode);
        try {
            PackageInfo packageInfo = sPackageManager.getPackageInfo(packageName, /* flags= */ 0);
            return packageInfo.getLongVersionCode() == versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(TAG, "Package " + packageName + " not installed for user "
                    + sContext.getUser() + ": " + e);
            return false;
        }
    }

    private static class InstallerResponseReceiver extends BroadcastReceiver {
        private CompletableFuture<Integer> mInstallerResponseResult = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            final int status = intent.getIntExtra(EXTRA_STATUS, -1);
            Log.i(TAG, "InstallerResponseReceiver received status: " + status);
            mInstallerResponseResult.complete(status);
        }

        public void unregisterReceiver(Context context) {
            context.unregisterReceiver(this);
        }
        public int getInstallerResponseResult() throws Exception {
            return mInstallerResponseResult.get(10, TimeUnit.SECONDS);
        }

        public void resetResult() {
            mInstallerResponseResult = new CompletableFuture();
        }
    }

    @Nullable
    private static String getPackageInstallerPackageName() {
        final Intent intent = new Intent(
                Intent.ACTION_INSTALL_PACKAGE).setData(Uri.parse("content:"));
        final ResolveInfo ri = sPackageManager.resolveActivity(intent, /* flags= */ 0);
        return ri != null ? ri.activityInfo.packageName : null;
    }

    private static boolean isNotSupportedDevice() {
        return FeatureUtil.isArc()
                || FeatureUtil.isAutomotive()
                || FeatureUtil.isTV()
                || FeatureUtil.isWatch()
                || FeatureUtil.isVrHeadset();
    }
}
