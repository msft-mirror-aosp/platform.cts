/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.harrier;

import static android.app.AppOpsManager.OPSTR_START_FOREGROUND;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.UserType.SECONDARY_USER;
import static com.android.bedstead.harrier.annotations.RequireAospBuild.GMS_CORE_PACKAGE;
import static com.android.bedstead.harrier.annotations.RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_BLUETOOTH;
import static com.android.bedstead.permissions.CommonPermissions.READ_CONTACTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.contentsuggestions.ContentSuggestionsManager;
import android.app.role.RoleManager;
import android.os.Build;
import android.provider.Settings;
import android.view.contentcapture.ContentCaptureManager;

import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest;
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceDisabled;
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceEnabled;
import com.android.bedstead.harrier.annotations.EnsureDemoMode;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureInstrumented;
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode;
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePropertySet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet;
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme;
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation;
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.EnsureWillNotTakeQuickBugReports;
import com.android.bedstead.harrier.annotations.EnsureWillTakeQuickBugReports;
import com.android.bedstead.harrier.annotations.InstrumentationComponent;
import com.android.bedstead.harrier.annotations.RequireAospBuild;
import com.android.bedstead.harrier.annotations.RequireCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireGmsBuild;
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser;
import com.android.bedstead.harrier.annotations.RequireInstantApp;
import com.android.bedstead.harrier.annotations.RequireLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireNotCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice;
import com.android.bedstead.harrier.annotations.RequirePackageInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled;
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.TestTag;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.parameterized.IncludeDarkMode;
import com.android.bedstead.harrier.annotations.parameterized.IncludeLandscapeOrientation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeLightMode;
import com.android.bedstead.harrier.annotations.parameterized.IncludePortraitOrientation;
import com.android.bedstead.harrier.policies.DisallowBluetooth;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.display.Display;
import com.android.bedstead.nene.display.DisplayProperties;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.testapp.NotFoundException;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceStateTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // Expects that this package name matches an actual test app
    private static final String TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.LockTaskApp";
    private static final String TEST_APP_PACKAGE_NAME2 = "com.android.bedstead.testapp.SmsApp";
    private static final String TEST_APP_USED_IN_FIELD_NAME =
            "com.android.bedstead.testapp.NotEmptyTestApp";

    // This is not used but is depended on by testApps_staticTestAppsAreNotReleased_1 and
    // testApps_staticTestAppsAreNotReleased_2 which test that this testapp isn't released
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get();

    @Test
    @RequirePackageInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageInstalledAnnotation_anyUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isNotEmpty();
    }

    @Test
    @RequirePackageInstalled(GMS_CORE_PACKAGE)
    public void requirePackageInstalledAnnotation_currentUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUser())
                .isTrue();
    }

    @Test
    @RequirePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @RequirePackageNotInstalled(GMS_CORE_PACKAGE)
    public void requirePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @EnsurePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @EnsurePackageNotInstalled(GMS_CORE_PACKAGE)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @RequireAospBuild
    public void requireAospBuildAnnotation_isRunningOnAospBuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isFalse();
    }

    @Test
    @RequireGmsBuild
    public void requireGmsBuildAnnotation_isRunningOnGmsbuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isTrue();
    }

    @Test
    @RequireCnGmsBuild
    public void requireCnGmsBuildAnnotation_isRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireNotCnGmsBuild
    public void requireNotCnGmsBuildAnnotation_isNotRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);

    }

    @Test
    @RequireFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireHasFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireDoesNotHaveFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireDoesNotHaveFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireSdkVersion(min = 27)
    public void requireSdkVersionAnnotation_min_minIsMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
    }

    @Test
    @RequireSdkVersion(max = 30)
    public void requireSdkVersionAnnotation_max_maxIsMet() {
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireSdkVersion(min = 27, max = 30)
    public void requireSdkVersionAnnotation_minAndMax_bothAreMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireLowRamDevice(reason = "Test")
    public void requireLowRamDeviceAnnotation_isLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isTrue();
    }

    @Test
    @RequireNotLowRamDevice(reason = "Test")
    public void requireNotLowRamDeviceAnnotation_isNotLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isFalse();
    }

    @Test
    @TestTag("TestTag")
    public void testTagAnnotation_testTagIsSet() {
        assertThat(Tags.hasTag("TestTag")).isTrue();
    }


    @Test
    @EnsureScreenIsOn
    public void ensureScreenIsOnAnnotation_screenIsOn() {
        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @Test
    @EnsurePasswordNotSet
    public void requirePasswordNotSetAnnotation_passwordNotSet() {
        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsureBluetoothEnabled
    public void ensureBluetoothEnabledAnnotation_bluetoothIsEnabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isTrue();
    }

    @Test
    @EnsureBluetoothDisabled
    public void ensureBluetoothDisabledAnnotation_bluetoothIsDisabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isFalse();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_1() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_2() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_staticTestAppsAreNotReleased_1() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @Test
    public void testApps_staticTestAppsAreNotReleased_2() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalled() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME).installedOnUser()).isTrue();
    }

    @EnsureHasSecondaryUser
    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)), onUser = SECONDARY_USER)
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalledOnCorrectUser() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME)
                .installedOnUser(sDeviceState.secondaryUser())).isTrue();
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @Test
    public void testApp_returnsTestApp() {
        assertThat(sDeviceState.testApp().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void testApp_noHarrierManagedTestApp_throwsException() {
        try (TestAppInstance testApp = sDeviceState.testApps().any().install()) {
            assertThrows(NeneException.class, sDeviceState::testApp);
        }
    }

    @EnsureTestAppInstalled(key = "testApp1", query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppInstalled(key = "testApp2", query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME2)))
    @Test
    public void testApp_withKey_returnsCorrectTestApp() {
        assertThat(sDeviceState.testApp("testApp1").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(sDeviceState.testApp("testApp2").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME2);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)), isPrimary = true)
    @Test
    public void dpc_primaryTestApp_returnsTestApp() {
        assertThat(sDeviceState.dpc().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppHasPermission(READ_CONTACTS)
    @Test
    public void ensureTestAppHasPermissionAnnotation_testAppHasPermission() {
        assertThat(sDeviceState.testApp().context().checkSelfPermission(READ_CONTACTS))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppDoesNotHavePermission(READ_CONTACTS)
    @Test
    public void ensureTestAppDoesNotHavePermissionAnnotation_testAppDoesNotHavePermission() {
        assertThat(sDeviceState.testApp().context().checkSelfPermission(READ_CONTACTS))
                .isNotEqualTo(PERMISSION_GRANTED);
    }

    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureTestAppHasAppOp(OPSTR_START_FOREGROUND)
    @Test
    public void ensureTestAppHasAppOpAnnotation_testAppHasAppOp() {
        assertThat(sDeviceState.testApp()
                .testApp().pkg().appOps().get(OPSTR_START_FOREGROUND)).isEqualTo(ALLOWED);
    }

    //TODO(b/300218365): Test that settings are returned to their original values in teardown.

    @EnsureSecureSettingSet(key = "testSecureSetting", value = "testValue")
    @Test
    public void ensureSecureSettingSetAnnotation_secureSettingIsSet() {
        assertThat(TestApis.settings().secure().getString("testSecureSetting"))
                .isEqualTo("testValue");
    }

    @EnsureGlobalSettingSet(key = "testGlobalSetting", value = "testValue")
    @Test
    public void ensureGlobalSettingSetAnnotation_globalSettingIsSet() {
        assertThat(TestApis.settings().global().getString("testGlobalSetting"))
                .isEqualTo("testValue");
    }

    @EnsureDemoMode
    @Test
    public void ensureDemoModeAnnotation_deviceIsInDemoMode() {
        assertThat(TestApis.settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE))
                .isEqualTo(1);
    }

    @EnsureNotDemoMode
    @Test
    public void ensureNotDemoModeAnnotation_deviceIsNotInDemoMode() {
        assertThat(TestApis.settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE))
                .isEqualTo(0);
    }

    @RequireInstantApp(reason = "Testing RequireInstantApp")
    @Test
    public void requireInstantAppAnnotation_isInstantApp() {
        assertThat(TestApis.packages().instrumented().isInstantApp()).isTrue();
    }

    @RequireNotInstantApp(reason = "Testing RequireNotInstantApp")
    @Test
    public void requireNotInstantAppAnnotation_isNotInstantApp() {
        assertThat(TestApis.packages().instrumented().isInstantApp()).isFalse();
    }

    @EnsureHasAccountAuthenticator
    @Test
    public void ensureHasAccountAuthenticatorAnnotation_accountAuthenticatorIsInstalled() {
        assertThat(sDeviceState.accounts().testApp().pkg().installedOnUser()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasAccountAuthenticator(onUser = ADDITIONAL_USER)
    public void ensureHasAccountAuthenticatorAnnotation_differentUser_accountAuthenticatorIsInstalledOnDifferentUser() {
        assertThat(sDeviceState.accounts(sDeviceState.additionalUser())
                .testApp().pkg().installedOnUser(sDeviceState.additionalUser())).isTrue();
    }

    @EnsureHasAccount
    @Test
    public void ensureHasAccountAnnotation_accountExists() {
        assertThat(sDeviceState.accounts().allAccounts()).isNotEmpty();
    }

    @EnsureHasAccounts({
            @EnsureHasAccount, @EnsureHasAccount
    })
    @Test
    public void ensureHasAccountsAnnotation_hasMultipleAccounts() {
        assertThat(sDeviceState.accounts().allAccounts().size()).isGreaterThan(1);
    }

    @EnsureHasAccount
    @Test
    public void account_returnsAccount() {
        assertThat(sDeviceState.account()).isNotNull();
    }

    @EnsureHasAccount(key = "testKey")
    @Test
    public void account_withKey_returnsAccount() {
        assertThat(sDeviceState.account("testKey")).isNotNull();
    }

    @EnsureHasNoAccounts
    @Test
    public void ensureHasNoAccountsAnnotation_hasNoAccounts() {
        assertThat(TestApis.accounts().all()).isEmpty();
    }

    @EnsureWifiEnabled
    @Test
    public void ensureWifiEnabledAnnotation_wifiIsEnabled() {
        assertThat(TestApis.wifi().isEnabled()).isTrue();
    }

    @EnsureWifiDisabled
    @Test
    public void ensureWifiDisabledAnnotation_wifiIsNotEnabled() {
        assertThat(TestApis.wifi().isEnabled()).isFalse();
    }

    @RequireSystemServiceAvailable(ContentCaptureManager.class)
    @Test
    public void requireSystemServiceAvailable_systemServiceIsAvailable() {
        assertThat(TestApis.context().instrumentedContext()
                .getSystemService(ContentCaptureManager.class)).isNotNull();
    }

    @EnsureTestAppInstalled(key = EnsureTestAppInstalled.DEFAULT_KEY, isPrimary = true)
    @AdditionalQueryParameters(
            forTestApp = EnsureTestAppInstalled.DEFAULT_KEY,
            query = @Query(targetSdkVersion = @IntegerQuery(isEqualTo = 28))
    )
    @Test
    public void additionalQueryParameters_ensureTestAppInstalled_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28);
    }

    @SuppressWarnings("JUnit4TestNotRun")
    @MostImportantCoexistenceTest(policy = DisallowBluetooth.class)
    public void mostImportantCoexistenceTestAnnotation_hasDpcsWithPermission() {
        assertThat(sDeviceState.testApp(MostImportantCoexistenceTest.MORE_IMPORTANT)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
        assertThat(sDeviceState.testApp(MostImportantCoexistenceTest.LESS_IMPORTANT)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();

    }

    @SuppressWarnings("JUnit4TestNotRun")
    @MostRestrictiveCoexistenceTest(policy = DisallowBluetooth.class)
    public void mostRestrictiveCoexistenceTestAnnotation_hasDpcsWithPermission() {
        assertThat(sDeviceState.testApp(MostRestrictiveCoexistenceTest.DPC_1)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
        assertThat(sDeviceState.testApp(MostRestrictiveCoexistenceTest.DPC_2)
                .testApp().pkg().hasPermission(MANAGE_DEVICE_POLICY_BLUETOOTH)).isTrue();
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureDefaultContentSuggestionsServiceDisabled
    @Test
    public void ensureDefaultContentSuggestionsServiceDisabledAnnotation_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled()).isFalse();
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureDefaultContentSuggestionsServiceEnabled
    @Test
    public void ensureDefaultContentSuggestionsServiceEnabledAnnotation_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled()).isTrue();
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceDisabled(onUser = ADDITIONAL_USER)
    @Test
    public void ensureDefaultContentSuggestionsServiceDisabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled(
                sDeviceState.additionalUser()
        )).isFalse();
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceEnabled(onUser = ADDITIONAL_USER)
    @Test
    public void ensureDefaultContentSuggestionsServiceEnabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.content().suggestions().defaultServiceEnabled(
                sDeviceState.additionalUser()
        )).isTrue();
    }

    @Test
    @RequireHasDefaultBrowser
    public void requireHasDefaultBrowser_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(TestApis.roles().getRoleHolders(RoleManager.ROLE_BROWSER)).isNotEmpty();
    }

    @Test
    @EnsurePropertySet(key = "dumpstate.key", value = "value")
    public void ensurePropertySetAnnotation_propertyIsSet() {
        assertThat(TestApis.properties().get("dumpstate.key")).isEqualTo("value");
    }

    @Test
    @EnsureWillTakeQuickBugReports
    public void ensureWillTakeQuickBugReportsAnnotation_willTakeQuickBugReports() {
        assertThat(TestApis.bugReports().willTakeQuickBugReports()).isTrue();
    }

    @Test
    @EnsureWillNotTakeQuickBugReports
    public void ensureWillNotTakeQuickBugReportsAnnotation_willNotTakeQuickBugReports() {
        assertThat(TestApis.bugReports().willTakeQuickBugReports()).isFalse();
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = true)
    @Test
    public void requireResourcesBooleanValueIsTrue_resourceValueIsTrue() {
        assertThat(TestApis
                .resources()
                .system()
                .getBoolean("config_enableMultiUserUI")
        ).isTrue();
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = false)
    @Test
    public void requireResourcesBooleanValueIsFalse_resourceValueIsFalse() {
        assertThat(TestApis
                .resources()
                .system()
                .getBoolean("config_enableMultiUserUI")
        ).isFalse();
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.LANDSCAPE)
    public void ensureUsingScreenOrientation_landscape_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE);
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.PORTRAIT)
    public void ensureUsingScreenOrientation_portrait_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT);
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.DARK)
    public void ensureUsingDisplayTheme_setDark_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK);
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.LIGHT)
    public void ensureUsingDisplayTheme_setLight_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT);
    }

    @Test
    @IncludeLandscapeOrientation
    public void includeRunOnLandscapeOrientationDevice_orientationIsSet() {
        assertThat(
                Display.INSTANCE.getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE);
    }

    @Test
    @IncludePortraitOrientation
    public void includeRunOnPortraitOrientationDevice_orientationIsSet() {
        assertThat(Display.INSTANCE.getScreenOrientation())
                .isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT);
    }

    @Test
    @IncludeDarkMode
    public void includeRunOnDarkModeDevice_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK);
    }

    @Test
    @IncludeLightMode
    public void includeRunOnLightModeDevice_themeIsSet() {
        assertThat(Display.INSTANCE.getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT);
    }

    @Test
    @EnsureTestAppInstalled(
            query = @Query(packageName = @StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)))
    @EnsureInstrumented({
            @InstrumentationComponent(
                    packageName = TEST_APP_PACKAGE_NAME,
                    runnerClass = "androidx.test.runner.AndroidJUnitRunner")
    })
    public void ensureTestAppInstrumented_testAppIsInstrumented() {
        // This test does not assert anything. But will run successfully only when the test app
        // given by [TEST_APP_PACKAGE_NAME] is successfully instrumented.
    }
}
