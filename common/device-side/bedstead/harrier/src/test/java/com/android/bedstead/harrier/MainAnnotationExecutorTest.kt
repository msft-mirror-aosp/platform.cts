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
package com.android.bedstead.harrier

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.contentsuggestions.ContentSuggestionsManager
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.provider.Settings
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceDisabled
import com.android.bedstead.harrier.annotations.EnsureDefaultContentSuggestionsServiceEnabled
import com.android.bedstead.harrier.annotations.EnsureDemoMode
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet
import com.android.bedstead.harrier.annotations.EnsureHasAccount
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator
import com.android.bedstead.harrier.annotations.EnsureHasAccounts
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.EnsureInstrumented
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePropertySet
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled
import com.android.bedstead.harrier.annotations.EnsureWillNotTakeQuickBugReports
import com.android.bedstead.harrier.annotations.EnsureWillTakeQuickBugReports
import com.android.bedstead.harrier.annotations.InstrumentationComponent
import com.android.bedstead.harrier.annotations.RequireAospBuild
import com.android.bedstead.harrier.annotations.RequireCnGmsBuild
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireGmsBuild
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser
import com.android.bedstead.harrier.annotations.RequireInstantApp
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireNotCnGmsBuild
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequirePackageInstalled
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable
import com.android.bedstead.harrier.annotations.TestTag
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters
import com.android.bedstead.harrier.annotations.parameterized.IncludeDarkMode
import com.android.bedstead.harrier.annotations.parameterized.IncludeLandscapeOrientation
import com.android.bedstead.harrier.annotations.parameterized.IncludeLightMode
import com.android.bedstead.harrier.annotations.parameterized.IncludePortraitOrientation
import com.android.bedstead.nene.TestApis.accounts
import com.android.bedstead.nene.TestApis.bluetooth
import com.android.bedstead.nene.TestApis.bugReports
import com.android.bedstead.nene.TestApis.content
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.device
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.properties
import com.android.bedstead.nene.TestApis.resources
import com.android.bedstead.nene.TestApis.roles
import com.android.bedstead.nene.TestApis.settings
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.TestApis.wifi
import com.android.bedstead.nene.appops.AppOpsMode
import com.android.bedstead.nene.display.Display.getDisplayTheme
import com.android.bedstead.nene.display.Display.getScreenOrientation
import com.android.bedstead.nene.display.DisplayProperties
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.utils.Tags.hasTag
import com.android.bedstead.permissions.CommonPermissions
import com.android.bedstead.testapp.NotFoundException
import com.android.bedstead.testapp.TestApp
import com.android.queryable.annotations.IntegerQuery
import com.android.queryable.annotations.Query
import com.android.queryable.annotations.StringQuery
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MainAnnotationExecutorTest {

    @Test
    @RequirePackageInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    fun requirePackageInstalledAnnotation_anyUser_packageIsInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isNotEmpty()
    }

    @Test
    @RequirePackageInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    fun requirePackageInstalledAnnotation_currentUser_packageIsInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUser()
        ).isTrue()
    }

    @Test
    @RequirePackageNotInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    fun requirePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isEmpty()
    }

    @Test
    @RequirePackageNotInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    fun requirePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        val pkg = packages().find(RequireAospBuild.GMS_CORE_PACKAGE)

        assertThat(pkg.installedOnUser()).isFalse()
    }

    @Test
    @EnsurePackageNotInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    // TODO(b/367300481): Restore this with a package which can be uninstalled
    @Ignore("this package can't be uninstalled")
    fun ensurePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isEmpty()
    }

    @Test
    @EnsurePackageNotInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    // TODO(b/367300481): Restore this with a package which can be uninstalled
    @Ignore("this package can't be uninstalled")
    fun ensurePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        val pkg = packages().find(RequireAospBuild.GMS_CORE_PACKAGE)

        assertThat(pkg.installedOnUser()).isFalse()
    }

    @Test
    @RequireAospBuild
    fun requireAospBuildAnnotation_isRunningOnAospBuild() {
        assertThat(packages().find(RequireAospBuild.GMS_CORE_PACKAGE).exists()).isFalse()
    }

    @Test
    @RequireGmsBuild
    fun requireGmsBuildAnnotation_isRunningOnGmsbuild() {
        assertThat(packages().find(RequireAospBuild.GMS_CORE_PACKAGE).exists()).isTrue()
    }

    @Test
    @RequireCnGmsBuild
    fun requireCnGmsBuildAnnotation_isRunningOnCnGmsBuild() {
        assertThat(
            packages().features()
        ).contains(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireNotCnGmsBuild
    fun requireNotCnGmsBuildAnnotation_isNotRunningOnCnGmsBuild() {
        assertThat(
            packages().features()
        ).doesNotContain(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireFeature(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    fun requireHasFeatureAnnotation_doesNotHaveFeature() {
        assertThat(
            packages().features()
        ).contains(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireDoesNotHaveFeature(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    fun requireDoesNotHaveFeatureAnnotation_doesNotHaveFeature() {
        assertThat(
            packages().features()
        ).doesNotContain(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireLowRamDevice(reason = "Test")
    fun requireLowRamDeviceAnnotation_isLowRamDevice() {
        assertThat(
            context()
                .instrumentedContext()
                .getSystemService(ActivityManager::class.java)
                .isLowRamDevice
        ).isTrue()
    }

    @Test
    @RequireNotLowRamDevice(reason = "Test")
    fun requireNotLowRamDeviceAnnotation_isNotLowRamDevice() {
        assertThat(
            context()
                .instrumentedContext()
                .getSystemService(ActivityManager::class.java)
                .isLowRamDevice
        ).isFalse()
    }

    @Test
    @TestTag("TestTag")
    fun testTagAnnotation_testTagIsSet() {
        assertThat(hasTag("TestTag")).isTrue()
    }

    @Test
    @EnsureScreenIsOn
    fun ensureScreenIsOnAnnotation_screenIsOn() {
        assertThat(device().isScreenOn).isTrue()
    }

    @Test
    @EnsurePasswordNotSet
    fun requirePasswordNotSetAnnotation_passwordNotSet() {
        assertThat(users().instrumented().hasLockCredential()).isFalse()
    }

    @Test
    @EnsureBluetoothEnabled
    fun ensureBluetoothEnabledAnnotation_bluetoothIsEnabled() {
        assertThat(bluetooth().isEnabled).isTrue()
    }

    @Test
    @EnsureBluetoothDisabled
    fun ensureBluetoothDisabledAnnotation_bluetoothIsDisabled() {
        assertThat(bluetooth().isEnabled).isFalse()
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    fun testApps_testAppsAreAvailableToMultipleTests_1() {
        assertThat(
            deviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()
        ).isNotNull()
    }

    @Test
    fun testApps_testAppsAreAvailableToMultipleTests_2() {
        assertThat(
            deviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()
        ).isNotNull()
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    fun testApps_staticTestAppsAreNotReleased_1() {
        assertThrows(NotFoundException::class.java) {
            deviceState.testApps()
                .query()
                .wherePackageName()
                .isEqualTo(TEST_APP_USED_IN_FIELD_NAME)
                .get()
        }
    }

    @Test
    fun testApps_staticTestAppsAreNotReleased_2() {
        assertThrows(NotFoundException::class.java) {
            deviceState.testApps()
                .query()
                .wherePackageName()
                .isEqualTo(TEST_APP_USED_IN_FIELD_NAME)
                .get()
        }
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @Test
    fun ensureTestAppInstalledAnnotation_testAppIsInstalled() {
        assertThat(packages().find(TEST_APP_PACKAGE_NAME).installedOnUser()).isTrue()
    }

    @EnsureHasSecondaryUser
    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)),
        onUser = UserType.SECONDARY_USER
    )
    @Test
    fun ensureTestAppInstalledAnnotation_testAppIsInstalledOnCorrectUser() {
        assertThat(
            packages()
                .find(TEST_APP_PACKAGE_NAME)
                .installedOnUser(deviceState.secondaryUser())
        ).isTrue()
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @Test
    fun testApp_returnsTestApp() {
        assertThat(deviceState.testApp().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun testApp_noHarrierManagedTestApp_throwsException() {
        deviceState.testApps().any().install().use {
            assertThrows(NeneException::class.java) {
                deviceState.testApp()
            }
        }
    }

    @EnsureTestAppInstalled(
        key = "testApp1",
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppInstalled(
        key = "testApp2",
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME2))
    )
    @Test
    fun testApp_withKey_returnsCorrectTestApp() {
        assertThat(
            deviceState.testApp("testApp1").packageName()
        ).isEqualTo(TEST_APP_PACKAGE_NAME)
        assertThat(
            deviceState.testApp("testApp2").packageName()
        ).isEqualTo(TEST_APP_PACKAGE_NAME2)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)),
        isPrimary = true
    )
    @Test
    fun dpc_primaryTestApp_returnsTestApp() {
        assertThat(deviceState.dpc().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppHasPermission(CommonPermissions.READ_CONTACTS)
    @Test
    fun ensureTestAppHasPermissionAnnotation_testAppHasPermission() {
        assertThat(
            deviceState.testApp()
                .context()
                .checkSelfPermission(CommonPermissions.READ_CONTACTS)
        ).isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppDoesNotHavePermission(CommonPermissions.READ_CONTACTS)
    @Test
    fun ensureTestAppDoesNotHavePermissionAnnotation_testAppDoesNotHavePermission() {
        assertThat(
            deviceState.testApp()
                .context()
                .checkSelfPermission(CommonPermissions.READ_CONTACTS)
        ).isNotEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppHasAppOp(AppOpsManager.OPSTR_START_FOREGROUND)
    @Test
    fun ensureTestAppHasAppOpAnnotation_testAppHasAppOp() {
        assertThat(
            deviceState.testApp().testApp().pkg().appOps()[AppOpsManager.OPSTR_START_FOREGROUND]
        ).isEqualTo(AppOpsMode.ALLOWED)
    }

    // TODO(b/300218365): Test that settings are returned to their original values in teardown.

    @EnsureSecureSettingSet(key = "testSecureSetting", value = "testValue")
    @Test
    fun ensureSecureSettingSetAnnotation_secureSettingIsSet() {
        assertThat(
            settings().secure().getString("testSecureSetting")
        ).isEqualTo("testValue")
    }

    @EnsureGlobalSettingSet(key = "testGlobalSetting", value = "testValue")
    @Test
    fun ensureGlobalSettingSetAnnotation_globalSettingIsSet() {
        assertThat(
            settings().global().getString("testGlobalSetting")
        ).isEqualTo("testValue")
    }

    @EnsureDemoMode
    @Test
    fun ensureDemoModeAnnotation_deviceIsInDemoMode() {
        assertThat(
            settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE)
        ).isEqualTo(1)
    }

    @EnsureNotDemoMode
    @Test
    fun ensureNotDemoModeAnnotation_deviceIsNotInDemoMode() {
        assertThat(
            settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE)
        ).isEqualTo(0)
    }

    @RequireInstantApp(reason = "Testing RequireInstantApp")
    @Test
    fun requireInstantAppAnnotation_isInstantApp() {
        assertThat(packages().instrumented().isInstantApp).isTrue()
    }

    @RequireNotInstantApp(reason = "Testing RequireNotInstantApp")
    @Test
    fun requireNotInstantAppAnnotation_isNotInstantApp() {
        assertThat(packages().instrumented().isInstantApp).isFalse()
    }

    @EnsureHasAccountAuthenticator
    @Test
    fun ensureHasAccountAuthenticatorAnnotation_accountAuthenticatorIsInstalled() {
        assertThat(
            deviceState
                .accounts()
                .testApp()
                .pkg()
                .installedOnUser()
        ).isTrue()
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasAccountAuthenticator(onUser = UserType.ADDITIONAL_USER)
    fun ensureHasAccountAuthenticatorAnnotation_differentUser_accountAuthenticatorIsInstalledOnDifferentUser() {
        assertThat(
            deviceState
                .accounts(deviceState.additionalUser())
                .testApp()
                .pkg()
                .installedOnUser(deviceState.additionalUser())
        ).isTrue()
    }

    @EnsureHasAccount
    @Test
    fun ensureHasAccountAnnotation_accountExists() {
        assertThat(deviceState.accounts().allAccounts()).isNotEmpty()
    }

    @EnsureHasAccount
    @Test
    fun account_returnsAccount() {
        assertThat(deviceState.account()).isNotNull()
    }

    @EnsureHasAccount(key = "testKey")
    @Test
    fun account_withKey_returnsAccount() {
        assertThat(deviceState.account("testKey")).isNotNull()
    }

    @EnsureHasNoAccounts
    @Test
    fun ensureHasNoAccountsAnnotation_hasNoAccounts() {
        assertThat(accounts().all()).isEmpty()
    }

    @EnsureWifiEnabled
    @Test
    fun ensureWifiEnabledAnnotation_wifiIsEnabled() {
        assertThat(wifi().isEnabled).isTrue()
    }

    @EnsureWifiDisabled
    @Test
    fun ensureWifiDisabledAnnotation_wifiIsNotEnabled() {
        assertThat(wifi().isEnabled).isFalse()
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @Test
    fun requireSystemServiceAvailable_systemServiceIsAvailable() {
        assertThat(
            context().instrumentedContext().getSystemService(ContentSuggestionsManager::class.java)
        ).isNotNull()
    }

    @EnsureTestAppInstalled(key = EnsureTestAppInstalled.DEFAULT_KEY, isPrimary = true)
    @AdditionalQueryParameters(
        forTestApp = EnsureTestAppInstalled.DEFAULT_KEY,
        query = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28))
    )
    @Test
    fun additionalQueryParameters_ensureTestAppInstalled_isRespected() {
        assertThat(deviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28)
    }

    @EnsureHasAccounts(EnsureHasAccount(), EnsureHasAccount())
    @Test
    fun ensureHasAccountsAnnotation_hasMultipleAccounts() {
        assertThat(deviceState.accounts().allAccounts().size).isGreaterThan(1)
    }

    @Test
    @RequireHasDefaultBrowser
    fun requireHasDefaultBrowser_roleBrowserIsNotEmpty() {
        assertThat(roles().getRoleHolders(RoleManager.ROLE_BROWSER)).isNotEmpty()
    }

    @Test
    @EnsurePropertySet(key = "dumpstate.key", value = "value")
    fun ensurePropertySetAnnotation_propertyIsSet() {
        assertThat(properties().get("dumpstate.key")).isEqualTo("value")
    }

    @Test
    @EnsureWillTakeQuickBugReports
    fun ensureWillTakeQuickBugReportsAnnotation_willTakeQuickBugReports() {
        assertThat(bugReports().willTakeQuickBugReports()).isTrue()
    }

    @Test
    @EnsureWillNotTakeQuickBugReports
    fun ensureWillNotTakeQuickBugReportsAnnotation_willNotTakeQuickBugReports() {
        assertThat(bugReports().willTakeQuickBugReports()).isFalse()
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = true)
    @Test
    fun requireResourcesBooleanValueIsTrue_resourceValueIsTrue() {
        assertThat(
            resources().system().getBoolean("config_enableMultiUserUI")
        ).isTrue()
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = false)
    @Test
    fun requireResourcesBooleanValueIsFalse_resourceValueIsFalse() {
        assertThat(
            resources().system().getBoolean("config_enableMultiUserUI")
        ).isFalse()
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.LANDSCAPE)
    fun ensureUsingScreenOrientation_landscape_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE)
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.PORTRAIT)
    fun ensureUsingScreenOrientation_portrait_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT)
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.DARK)
    fun ensureUsingDisplayTheme_setDark_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK)
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.LIGHT)
    fun ensureUsingDisplayTheme_setLight_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT)
    }

    @Test
    @IncludeLandscapeOrientation
    fun includeRunOnLandscapeOrientationDevice_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE)
    }

    @Test
    @IncludePortraitOrientation
    fun includeRunOnPortraitOrientationDevice_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT)
    }

    @Test
    @IncludeDarkMode
    fun includeRunOnDarkModeDevice_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK)
    }

    @Test
    @IncludeLightMode
    fun includeRunOnLightModeDevice_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT)
    }

    @Test
    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureInstrumented(
        [InstrumentationComponent(
            packageName = TEST_APP_PACKAGE_NAME,
            runnerClass = "androidx.test.runner.AndroidJUnitRunner"
        )]
    )
    fun ensureTestAppInstrumented_testAppIsInstrumented() {
        // This test does not assert anything. But will run successfully only when the test app
        // given by [TEST_APP_PACKAGE_NAME] is successfully instrumented.
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @EnsureDefaultContentSuggestionsServiceDisabled
    @Test
    fun ensureDefaultContentSuggestionsServiceDisabledAnnotation_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(content().suggestions().defaultServiceEnabled()).isFalse()
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @EnsureDefaultContentSuggestionsServiceEnabled
    @Test
    fun ensureDefaultContentSuggestionsServiceEnabledAnnotation_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(content().suggestions().defaultServiceEnabled()).isTrue()
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceEnabled(onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureDefaultContentSuggestionsServiceEnabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(
            content().suggestions().defaultServiceEnabled(deviceState.additionalUser())
        ).isTrue()
    }

    // TODO(b/366175813) fix Bedstead to make this test green
    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceDisabled(onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureDefaultContentSuggestionsServiceDisabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(
            content().suggestions().defaultServiceEnabled(deviceState.additionalUser())
        ).isFalse()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        // Expects that this package name matches an actual test app
        private const val TEST_APP_PACKAGE_NAME: String = "com.android.bedstead.testapp.LockTaskApp"
        private const val TEST_APP_PACKAGE_NAME2: String = "com.android.bedstead.testapp.SmsApp"
        private const val TEST_APP_USED_IN_FIELD_NAME: String =
            "com.android.bedstead.testapp.NotEmptyTestApp"

        // This is not used but is depended on by testApps_staticTestAppsAreNotReleased_1 and
        // testApps_staticTestAppsAreNotReleased_2 which test that this testapp isn't released
        private val sTestApp: TestApp = deviceState.testApps().query()
            .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get()
    }
}
