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

import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.DeviceConfig;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The test base to test PackageInstaller CUJs.
 */
public class PackageInstallerCujTestBase {
    public static final String TAG = "PackageInstallerCujTestBase";

    public static final String AUTHORITY_NAME = ".fileprovider";
    public static final String INSTALLER_APK_NAME = "CtsInstallerCujTestInstaller.apk";
    public static final String INSTALLER_APK_V2_NAME = "CtsInstallerCujTestInstallerV2.apk";
    public static final String INSTALLER_LABEL = "CTS CUJ Installer";
    public static final String INSTALLER_PACKAGE_NAME =
            "android.packageinstaller.cts.cuj.installer";
    public static final String TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller/cuj";
    public static final String TEST_APK_NAME = "CtsInstallerCujTestApp.apk";
    public static final String TEST_APK_V2_NAME = "CtsInstallerCujTestAppV2.apk";
    public static final String TEST_APP_LABEL = "Installer CUJ Test App";
    public static final String TEST_APP_PACKAGE_NAME =
            "android.packageinstaller.cts.cuj.app";
    public static final String TEST_NO_LAUNCHER_ACTIVITY_APK_NAME =
            "CtsInstallerCujTestNoLauncherActivityApp.apk";
    public static final String TEST_NO_LAUNCHER_ACTIVITY_APK_V2_NAME =
            "CtsInstallerCujTestNoLauncherActivityAppV2.apk";

    public static final String APP_INSTALLED_LABEL = "App installed";
    public static final String BUTTON_CANCEL_LABEL = "Cancel";
    public static final String BUTTON_DONE_LABEL = "Done";
    public static final String BUTTON_GPP_MORE_DETAILS_LABEL = "More details";
    public static final String BUTTON_GPP_INSTALL_WITHOUT_SCANNING_LABEL =
            "Install without scanning";
    public static final String BUTTON_INSTALL_LABEL = "Install";
    public static final String BUTTON_OK_LABEL = "OK";
    public static final String BUTTON_OPEN_LABEL = "Open";
    public static final String BUTTON_SETTINGS_LABEL = "Settings";
    public static final String BUTTON_UPDATE_LABEL = "Update";
    public static final String BUTTON_UPDATE_ANYWAY_LABEL = "Update anyway";
    public static final String TOGGLE_ALLOW_LABEL = "allow";
    public static final String TOGGLE_ALLOW_FROM_LABEL = "Allow from";
    public static final String TOGGLE_ALLOW_PERMISSION_LABEL = "allow permission";
    public static final String TOGGLE_INSTALL_UNKNOWN_APPS_LABEL = "install unknown apps";
    public static final String INSTALLING_LABEL = "Installing";
    public static final String UNINSTALL_LABEL = "uninstall";
    public static final String TEXTVIEW_WIDGET_CLASSNAME = "android.widget.TextView";

    public static final long FIND_OBJECT_TIMEOUT_MS = 30 * 1000L;
    private static final long WAIT_OBJECT_GONE_TIMEOUT_MS = 3 * 1000L;

    private static final long TEST_APK_VERSION = 1;
    private static final long TEST_APK_V2_VERSION = 2;

    private static final ComponentName TEST_APP_ACTIVITY_COMPONENT = new ComponentName(
            TEST_APP_PACKAGE_NAME, "android.packageinstaller.cts.cuj.app.MainActivity");

    @ClassRule
    public static final DisableAnimationRule sDisableAnimationRule = new DisableAnimationRule();

    private static String sPackageInstallerPackageName = null;

    public static Context sContext;
    public static Instrumentation sInstrumentation;
    public static PackageManager sPackageManager;
    public static UiDevice sUiDevice;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sContext = sInstrumentation.getTargetContext();
        sPackageManager = sContext.getPackageManager();
        sPackageInstallerPackageName = getPackageInstallerPackageName();
        Log.d(TAG, "sPackageInstallerPackageName = " + sPackageInstallerPackageName);

        // Unblock UI
        sUiDevice = UiDevice.getInstance(sInstrumentation);
        if (!sUiDevice.isScreenOn()) {
            sUiDevice.wakeUp();
        }
        sUiDevice.executeShellCommand("wm dismiss-keyguard");
    }

    @Before
    public void setup() throws Exception {
        assumeFalse("The device is not supported", isNotSupportedDevice());

        assumeFalse("The device doesn't have package installer",
                sPackageInstallerPackageName == null);

        uninstallTestPackage();
        assertTestPackageNotInstalled();
    }

    @After
    public void tearDown() throws Exception {
        uninstallTestPackage();
        // to avoid any UI is still on the screen
        pressBack();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        sPackageManager = null;
        sContext = null;
        sUiDevice = null;
        sInstrumentation = null;
    }

    /**
     * Assert the test package that is the version 1 is installed.
     */
    public static void assertTestPackageInstalled() {
        assertThat(isInstalledAndVerifyVersionCode(
                TEST_APP_PACKAGE_NAME, TEST_APK_VERSION)).isTrue();
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

    /**
     * Wait for the device idle.
     */
    public static void waitForUiIdle() {
        // Make sure the application is idle and input windows is up-to-date.
        sInstrumentation.getUiAutomation().syncInputTransactions();
        sUiDevice.waitForIdle();
    }

    /**
     * Press the back key.
     */
    public static void pressBack() {
        sUiDevice.pressBack();
        waitForUiIdle();
    }

    /**
     * Click the object and wait for the new window content is changed
     */
    public static void clickAndWaitForNewWindow(UiObject2 uiObject2) {
        uiObject2.clickAndWait(Until.newWindow(), WAIT_OBJECT_GONE_TIMEOUT_MS);
    }

    /**
     * Assert the title of the install dialog is {@link #TEST_APP_LABEL}.
     */
    public static void assertTitleIsTestAppLabel() throws Exception {
        findPackageInstallerObject(TEST_APP_LABEL);
    }

    /**
     * Assert the content includes the installer label {@link #INSTALLER_LABEL}.
     */
    public static void assertContentIncludesInstallerLabel() throws Exception {
        findPackageInstallerObject(By.textContains(INSTALLER_LABEL), /* checkNull= */ true);
    }

    /**
     * Assert the title of the install dialog is {@link #INSTALLER_LABEL}.
     */
    public static void assertTitleIsInstallerLabel() throws Exception {
        findPackageInstallerObject(INSTALLER_LABEL);
    }

    /**
     * Click the Cancel button and wait for the dialog to disappear.
     */
    public static void clickCancelButton() throws Exception {
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_CANCEL_LABEL));
    }

    /**
     * Touch outside of the PackageInstaller dialog.
     */
    public static void touchOutside() {
        DisplayMetrics displayMetrics = sContext.getResources().getDisplayMetrics();
        sUiDevice.click(displayMetrics.widthPixels / 3, displayMetrics.heightPixels / 10);
        waitForUiIdle();
    }

    /**
     * Log some values about the {@code uiObject}
     */
    public static void logUiObject(@NonNull UiObject2 uiObject) {
        Log.d(TAG, "Found bounds: " + uiObject.getVisibleBounds()
                + " of object: " + uiObject + ", text: " + uiObject.getText()
                + ", package: " + uiObject.getApplicationPackage() + ", className: "
                + uiObject.getClassName());
    }

    /**
     * Get the new BySelector with the package name is {@link #sPackageInstallerPackageName}.
     */
    public static BySelector getPackageInstallerBySelector(BySelector bySelector) {
        return bySelector.pkg(sPackageInstallerPackageName);
    }

    /**
     * Find the UiObject2 with the {@code name} and the object's package name is
     * {@link #sPackageInstallerPackageName}.
     */
    public static UiObject2 findPackageInstallerObject(String name) throws Exception {
        final Pattern namePattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
        return findPackageInstallerObject(By.text(namePattern), /* checkNull= */ true);
    }

    /**
     * Find the UiObject2 with the {@code name} and the object's package name is
     * {@link #sPackageInstallerPackageName}. If {@code checkNull} is true, also check the object
     * is not null.
     */
    public static UiObject2 findPackageInstallerObject(BySelector bySelector, boolean checkNull)
            throws Exception {
        return findObject(getPackageInstallerBySelector(bySelector), checkNull);
    }

    /**
     * Find the UiObject2 with the {@code name}.
     */
    public static UiObject2 findObject(String name) throws Exception {
        final Pattern namePattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
        return findObject(By.text(namePattern), /* checkNull= */ true);
    }

    /**
     * Find the UiObject2 with the {@code bySelector}. If {@code checkNull} is true, also
     * check the object is not null.
     */
    @Nullable
    public static UiObject2 findObject(BySelector bySelector, boolean checkNull) throws Exception {
        return findObject(bySelector, checkNull, FIND_OBJECT_TIMEOUT_MS);
    }

    /**
     * Find the UiObject2 with the {@code bySelector}. If {@code checkNull} is true, also
     * check the object is not null. The {@code timeoutMs} is the value for waiting time.
     */
    @Nullable
    public static UiObject2 findObject(BySelector bySelector, boolean checkNull, long timeoutMs)
            throws Exception {
        waitForUiIdle();

        UiObject2 object = null;
        long startTime = System.currentTimeMillis();
        while (startTime + timeoutMs > System.currentTimeMillis()) {
            try {
                object = sUiDevice.wait(Until.findObject(bySelector), /* timeout= */ 10 * 1000);
                if (object != null) {
                    Log.d(TAG, "Found bounds: " + object.getVisibleBounds()
                            + " of object: " + bySelector + ", text: " + object.getText()
                            + " package: " + object.getApplicationPackage() + ", enabled: "
                            + object.isEnabled() + ", clickable: " + object.isClickable()
                            + ", contentDescription: " + object.getContentDescription()
                            + ", resourceName: " + object.getResourceName() + ", visibleCenter: "
                            + object.getVisibleCenter());
                    waitForUiIdle();
                    return object;
                } else {
                    // Maybe the screen is small. Scroll forward and attempt to click
                    new UiScrollable(new UiSelector().scrollable(true)).scrollForward();
                }
            } catch (Exception ignored) {
                // do nothing
            }
        }

        // dump window hierarchy for debug
        if (object == null) {
            dumpWindowHierarchy();
        }

        if (checkNull) {
            assertWithMessage("Can't find object " + bySelector).that(object).isNotNull();
        }
        return object;
    }

    /**
     * Wait for the UiObject2 with the {@code bySelector} is gone.
     */
    public static void waitUntilObjectGone(BySelector bySelector) throws Exception {
        if (!sUiDevice.wait(Until.gone(bySelector), WAIT_OBJECT_GONE_TIMEOUT_MS)) {
            // dump window hierarchy for debug
            dumpWindowHierarchy();
            fail("The Object: " + bySelector + "did not disappear within "
                    + WAIT_OBJECT_GONE_TIMEOUT_MS + " milliseconds");
        }
        waitForUiIdle();
    }

    /**
     * Dump current window hierarchy to help debug UI
     */
    public static void dumpWindowHierarchy() throws InterruptedException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sUiDevice.dumpWindowHierarchy(outputStream);
        String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name());

        Log.w(TAG, "Window hierarchy:");
        for (String line : windowHierarchy.split("\n")) {
            Thread.sleep(10);
            Log.w(TAG, line);
        }
    }

    /**
     * Uninstall the test package {@link #TEST_APP_PACKAGE_NAME}.
     */
    public static void uninstallTestPackage() {
        uninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    /**
     * Uninstall the package with {@code packageName}.
     */
    public static void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(String.format("pm uninstall %s", packageName));
    }

    /**
     * Install the test apk with update-ownership.
     */
    public static void installTestPackageWithUpdateOwnership() throws Exception {
        SystemUtil.runShellCommand(String.format("pm install -t  --update-ownership -i %s %s",
                sContext.getPackageName(),
                new File(TEST_APK_LOCATION, TEST_APK_NAME).getCanonicalPath()));
        assertTestPackageInstalled();

        // assert the updateOwner package name is sContext.getPackageName()
        final String updateOwnerPackageName = sPackageManager.getInstallSourceInfo(
                TEST_APP_PACKAGE_NAME).getUpdateOwnerPackageName();
        assertThat(updateOwnerPackageName).isEqualTo(sContext.getPackageName());
    }

    /**
     * Install the test apk {@link #TEST_APK_NAME} and set the installer to be
     * the package name of the test case.
     */
    public static void installTestPackageWithInstallerPackageName() throws IOException {
        installPackage(TEST_APK_NAME, sContext.getPackageName());
        assertTestPackageInstalled();
    }

    /**
     * Install the test apk {@link #TEST_APK_NAME}.
     */
    public static void installTestPackage() throws IOException {
        installPackage(TEST_APK_NAME);
        assertTestPackageInstalled();
    }

    /**
     * Install the test apk that has no launcher activity
     * {@link #TEST_NO_LAUNCHER_ACTIVITY_APK_NAME}.
     */
    public static void installNoLauncherActivityTestPackage() throws IOException {
        installPackage(TEST_NO_LAUNCHER_ACTIVITY_APK_NAME);
        assertTestPackageInstalled();
    }

    /**
     * Return the value of the device config of the {@code name} in PackageManagerService
     * namespace.
     */
    @Nullable
    public static String getPackageManagerDeviceProperty(@NonNull String name)
            throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() ->
                DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name));
    }

    /**
     * Set the {@code value} to the device config with the {@code name} in PackageManagerService
     * namespace.
     */
    public static void setPackageManagerDeviceProperty(@NonNull String name,
            @Nullable String value) throws Exception {
        SystemUtil.callWithShellPermissionIdentity(() -> DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name, value,
                /* makeDefault= */ false));
    }

    /**
     * Install the test apk {@code apkName} and set the installer is {@code installerPackageName}.
     */
    public static void installPackage(@NonNull String apkName, @NonNull String installerPackageName)
            throws IOException {
        Log.d(TAG, "installPackage(): apkName= " + apkName + " installerPackageName= "
                + installerPackageName);
        SystemUtil.runShellCommand(String.format("pm install -i %s -t %s", installerPackageName,
                new File(TEST_APK_LOCATION, apkName).getCanonicalPath()));
    }

    /**
     * Install the test apk {@code apkName}.
     */
    public static void installPackage(@NonNull String apkName) throws IOException {
        Log.d(TAG, "installPackage(): apkName= " + apkName);
        SystemUtil.runShellCommand("pm install -t "
                + new File(TEST_APK_LOCATION, apkName).getCanonicalPath());
    }

    /**
     * If the test package {@link #TEST_APP_PACKAGE_NAME} is installed, return true. Otherwise,
     * return false.
     */
    public static boolean isTestPackageInstalled() {
        return isInstalled(TEST_APP_PACKAGE_NAME);
    }

    /**
     * If the test package {@code packageName} is installed, return true. Otherwise,
     * return false.
     */
    public static boolean isInstalled(@NonNull String packageName) {
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

    /**
     * If the test package {@link #TEST_APP_PACKAGE_NAME} with version {@link #TEST_APK_V2_VERSION}
     * is installed, return true. Otherwise, return false.
     */
    public static boolean isTestPackageVersion2Installed() {
        return isInstalledAndVerifyVersionCode(TEST_APP_PACKAGE_NAME, TEST_APK_V2_VERSION);
    }

    /**
     * If the test package {@code packageName} with version {@code versionCode}
     * is installed, return true. Otherwise, return false.
     */
    public static boolean isInstalledAndVerifyVersionCode(@NonNull String packageName,
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

    /**
     * Disable the launcher activity of the test app.
     */
    public static void disableTestPackageLauncherActivity() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> sPackageManager.setComponentEnabledSetting(TEST_APP_ACTIVITY_COMPONENT,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP), CHANGE_COMPONENT_ENABLED_STATE);
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
