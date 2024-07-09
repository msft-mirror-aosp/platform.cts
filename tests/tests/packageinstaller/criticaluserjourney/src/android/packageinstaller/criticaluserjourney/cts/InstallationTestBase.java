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

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AppOpsUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The test base to test PackageInstaller Installation CUJs
 */
public class InstallationTestBase extends PackageInstallerCujTestBase {

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
    private static final int EVENT_REQUEST_INSTALLER_INTENT_WITH_ACTION_VIEW = 5;

    private static final int STATUS_CUJ_INSTALLER_READY = 1000;
    private static final int STATUS_CUJ_INSTALLER_START_ACTIVITY_READY = 1001;

    private static InstallerResponseReceiver sInstallerResponseReceiver;
    private static String sToggleLabel = null;

    @BeforeClass
    public static void setUpInstallationClass() throws Exception {
        setUpClass();
        copyTestFiles();

        sInstallerResponseReceiver = new InstallerResponseReceiver();
        sContext.registerReceiver(sInstallerResponseReceiver,
                new IntentFilter(ACTION_RESPONSE_INSTALLER), Context.RECEIVER_EXPORTED);
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();

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
    @Override
    public void tearDown() throws Exception {
        requestInstallerCleanUp();
        uninstallInstallerPackage();
        super.tearDown();
    }

    @AfterClass
    public static void tearDownInstallationClass() throws Exception {
        sInstallerResponseReceiver.unregisterReceiver(sContext);
        sInstallerResponseReceiver = null;
        tearDownClass();
    }

    /**
     * Grant the REQUEST_INSTALL_PACKAGES AppOps permission to the CUJ Installer.
     */
    public static void grantRequestInstallPackagesPermission() throws Exception {
        AppOpsUtils.setOpMode(TEST_INSTALLER_PACKAGE_NAME, OPSTR_REQUEST_INSTALL_PACKAGES,
                MODE_ALLOWED);
    }

    private static void copyTestFiles() throws Exception {
        final File dstFile = new File(sContext.getFilesDir(), TEST_APK_NAME);
        if (!dstFile.exists()) {
            final File apkFile = new File(TEST_APK_LOCATION, TEST_APK_NAME);
            copyFile(apkFile, dstFile);
        }

        final File dstV2File = new File(sContext.getFilesDir(), TEST_APK_V2_NAME);
        if (!dstV2File.exists()) {
            final File apkV2File = new File(TEST_APK_LOCATION, TEST_APK_V2_NAME);
            copyFile(apkV2File, dstV2File);
        }
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
     * Start the installation via startActivity with ACTION_VIEW.
     */
    public static void startInstallationViaIntentActionView() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_WITH_ACTION_VIEW);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation to update the test apk from version 1 to version 2
     * via startActivity with ACTION_VIEW.
     */
    public static void startInstallationUpdateViaIntentActionView() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT_WITH_ACTION_VIEW,
                /* useV2= */ true);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation via startActivity with ACTION_INSTALL_PACKAGE
     */
    public static void startInstallationViaIntent() throws Exception {
        sendRequestInstallerBroadcast(EVENT_REQUEST_INSTALLER_INTENT);
        assertCUJInstallerStartActivityReady();
    }

    /**
     * Start the installation to update the test apk from version 1 to version 2
     * via startActivity with ACTION_INSTALL_PACKAGE
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

    private static void assertInstallerNotInstalled() {
        assertThat(isInstallerInstalled()).isFalse();
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
     * Assert the Update anyway button of the install dialog exists.
     */
    private static void assertUpdateAnywayButton() throws Exception {
        findPackageInstallerObject(BUTTON_UPDATE_ANYWAY_LABEL);
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
     * Assert the update anyway dialog for installing the test app.
     */
    public static void assertTestAppUpdateAnywayDialog() throws Exception {
        assertTitleIsTestApkLabel();
        assertContentIncludesTestInstallerLabel();
        assertUpdateAnywayButton();
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
     * Click the Update anyway button and wait for the dialog to disappear. Also allow install if
     * the GPP dialog exists.
     */
    public static void clickUpdateAnywayButton() throws Exception {
        clickUpdateAnywayButton(/* checkInstallingDialog= */ false);
    }

    /**
     * Click the Update anyway button, assert the title is {@link #TEST_APK_LABEL} and wait for the
     * dialog to disappear. If {@code checkInstallingDialog} is true, check the Installing
     * dialog. Otherwise, don't check the Installing dialog. E.g. The installation via intent
     * triggers Installing dialog.
     */
    private static void clickUpdateAnywayButton(boolean checkInstallingDialog) throws Exception {
        assertTitleIsTestApkLabel();

        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_UPDATE_ANYWAY_LABEL));

        if (checkInstallingDialog) {
            waitForInstallingDialogGone();
        }

        if (!isTestPackageVersion2Installed()) {
            allowInstallIfGPPDialogExists();
        }
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
            UiObject2 text = findAllowFromSourceSiblingTextObject(toggle);
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
            text = findAllowFromSourceSiblingTextObject(toggle);
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

    private static void waitForInstallingDialogGone() {
        BySelector installingSelector =
                getPackageInstallerBySelector(By.textContains(INSTALLING_LABEL));
        UiObject2 installing = sUiDevice.findObject(installingSelector);
        if (installing != null) {
            waitUntilObjectGone(installingSelector);
        }
    }

    @Nullable
    private static UiObject2 findAllowFromSourceSiblingTextObject(@NonNull UiObject2 uiObject) {
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

    private static void uninstallInstallerPackage() {
        uninstallPackage(TEST_INSTALLER_PACKAGE_NAME);
    }

    private static boolean isInstallerInstalled() {
        return isInstalled(TEST_INSTALLER_PACKAGE_NAME);
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
}
