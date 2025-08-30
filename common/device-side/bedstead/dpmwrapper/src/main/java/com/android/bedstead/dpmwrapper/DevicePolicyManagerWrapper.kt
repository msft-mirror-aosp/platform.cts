/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.bedstead.dpmwrapper

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.ServiceManagerWrapper
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.stubbing.Answer

internal class DevicePolicyManagerWrapper : ServiceManagerWrapper<DevicePolicyManager>() {
    companion object {
        private val TAG: String = DevicePolicyManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, DevicePolicyManager?>()
    }

    @SuppressLint("MissingPermission")
    override fun getWrapper(
        context: Context,
        manager: DevicePolicyManager,
        answer: Answer<*>,
    ): DevicePolicyManager {
        val userId = context.userId
        val cachedSpy: DevicePolicyManager? = sSpies.get(context)
        if (cachedSpy != null) {
            Log.d(TAG, "getWrapper(): returning cached spy for user $userId")
            return cachedSpy
        }

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        val devicePolicyManagerSpy = spy(manager) { setUpStubs(answer) }

        val identificationString =
            "DevicePolicyManagerWrapper#${System.identityHashCode(devicePolicyManagerSpy)}"
        devicePolicyManagerSpy.stub { on { toString() } doReturn identificationString }
        Log.d(TAG, "get(): created spy for user " + context.userId + ": " + identificationString)

        sSpies.put(context, devicePolicyManagerSpy)
        Log.d(
            TAG,
            "getWrapper(): returning new spy for context " +
                "$context (${context.getPackageName()}) and user $userId",
        )

        return devicePolicyManagerSpy
    }

    private fun KStubbing<DevicePolicyManager>.setUpStubs(answer: Answer<*>) {
        // Please search for existing methods before adding new ones. A potential duplicate
        // may already exist.
        // Note: Ref. b/441373957 - this method is (artificially) split into individual methods
        // because otherwise it is right at the edge of the dex method size limit. Adding something
        // like coverage sends it over the edge, resulting in [MethodTooLargeException].
        setUpCommonMethods(answer)
        setUpTimeTestMethods(answer)
        setUpUserControllerDisabledPackagesTestMethods(answer)
        setUpDeviceOwnerProvisioningTestMethods(answer)
        setUpCtsVerifierTestMethods(answer)
        setUpDevicePolicySafetyCheckerIntegrationTestMethods(answer)
        setUpListForegroundAffiliatedUsersTestMethods(answer)
        setUpUserSessionTestMethods(answer)
        setUpSuspendPackageTestMethods(answer)
        setUpPrivacyDeviceOwnerTestMethods(answer)
        setUpAdminActionBookkeepingTestMethods(answer)
        setUpPrivateDnsPolicyTestMethods(answer)
        setUpStorageEncryptionTestMethods(answer)
        setUpAdminConfiguredNetworksTestMethods(answer)
        setUpSecurityLoggingTestMethods(answer)
        setUpWifiTestMethods(answer)
        setUpFactoryResetProtectionPolicyTestMethods(answer)
        setUpDefaultSmsApplicationTestMethods(answer)
        setUpOverrideApnTestMethods(answer)
        setUpDevicePolicyLoggingTestMethods(answer)
        setUpPasswordRequirementsTestMethods(answer)
        setUpAccessibilityServicesTestMethods(answer)
        setUpInputMethodsTestMethods(answer)
        setUpCommonCriteriaModeTestMethods(answer)
        setUpApplicationRestrictionsDelegateTestMethods(answer)
        setUpPackageAccessDelegateTestMethods(answer)
        setUpPermissionGrantDelegateTestMethods(answer)
        setUpBlockUninstallDelegateTestMethods(answer)
        setUpDelegationTestMethods(answer)
        setUpTrustAgentInfoTestMethods(answer)
        setUpBackupServiceActiveTestMethods(answer)
        setUpPendingSystemUpdateTestMethods(answer)
        setUpAffiliationTestMethods(answer)
        setUpLockScreenInfoTestMethods(answer)
        // TODO(b/176993670): add more methods below as tests are converted
    }

    private fun KStubbing<DevicePolicyManager>.setUpLockScreenInfoTestMethods(answer: Answer<*>) {
        // Used by LockScreenInfoTest
        on { getDeviceOwnerLockScreenInfo() } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpAffiliationTestMethods(answer: Answer<*>) {
        // Used by AffiliationTest (GTS)
        on { getAffiliationIds(anyOrNull()) } doAnswer answer
    }

    @SuppressLint("MissingPermission")
    private fun KStubbing<DevicePolicyManager>.setUpPendingSystemUpdateTestMethods(
        answer: Answer<*>
    ) {
        // Used by PendingSystemUpdateTest
        on { notifyPendingSystemUpdate(any()) } doAnswer answer
        on { getPendingSystemUpdate(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpBackupServiceActiveTestMethods(
        answer: Answer<*>
    ) {
        // Used by BackupServiceActiveTest
        on { setBackupServiceEnabled(anyOrNull(), any()) } doAnswer answer
        on { isBackupServiceEnabled(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpTrustAgentInfoTestMethods(answer: Answer<*>) {
        // Used by TrustAgentInfoTest
        on { getTrustAgentConfiguration(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpDelegationTestMethods(answer: Answer<*>) {
        // Used By DelegationTest
        on { getDelegatePackages(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpBlockUninstallDelegateTestMethods(
        answer: Answer<*>
    ) {
        // Used by BlockUninstallDelegateTest
        on { isUninstallBlocked(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpPermissionGrantDelegateTestMethods(
        answer: Answer<*>
    ) {
        // Used by PermissionGrantDelegateTest
        on { getPermissionGrantState(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
        on { getPermissionPolicy(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpPackageAccessDelegateTestMethods(
        answer: Answer<*>
    ) {
        // Used by PackageAccessDelegateTest
        on { isApplicationHidden(anyOrNull(), anyOrNull()) } doAnswer answer
        on { isPackageSuspended(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setPackagesSuspended(anyOrNull(), anyOrNull(), any()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpApplicationRestrictionsDelegateTestMethods(
        answer: Answer<*>
    ) {
        // Used by AppRestrictionsDelegateTest
        on { getApplicationRestrictions(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpCommonCriteriaModeTestMethods(
        answer: Answer<*>
    ) {
        // Used by CommonCriteriaModeTest
        on { setCommonCriteriaModeEnabled(anyOrNull(), any()) } doAnswer answer
        on { isCommonCriteriaModeEnabled(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpInputMethodsTestMethods(answer: Answer<*>) {
        // Used by InputMethodsTest
        on { getPermittedInputMethods(anyOrNull()) } doAnswer answer
    }

    @SuppressLint("MissingPermission")
    private fun KStubbing<DevicePolicyManager>.setUpAccessibilityServicesTestMethods(
        answer: Answer<*>
    ) {
        // Used by AccessibilityServicesTest
        on { getPermittedAccessibilityServices(any<ComponentName>()) } doAnswer answer
        on { getPermittedAccessibilityServices(any<Int>()) } doAnswer answer
    }

    // Suppressing deprecation warnings since we are setting stub responses on deprecated methods.
    @Suppress("DEPRECATION")
    private fun KStubbing<DevicePolicyManager>.setUpPasswordRequirementsTestMethods(
        answer: Answer<*>
    ) {
        // Used by PasswordRequirementsTest
        on { getPasswordMinimumLength(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumNumeric(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumLetters(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumUpperCase(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumLowerCase(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumNonLetter(anyOrNull()) } doAnswer answer
        on { getPasswordMinimumSymbols(anyOrNull()) } doAnswer answer
    }

    // Suppressing deprecation warnings since we are setting stub responses on deprecated methods.
    @Suppress("DEPRECATION")
    private fun KStubbing<DevicePolicyManager>.setUpDevicePolicyLoggingTestMethods(
        answer: Answer<*>
    ) {
        // Used for DevicePolicyLoggingTest
        on { getAutoTimeEnabled(anyOrNull()) } doAnswer answer
        on { setPasswordMinimumLength(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumNumeric(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumNonLetter(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumLetters(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumLowerCase(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumUpperCase(anyOrNull(), any()) } doAnswer answer
        on { setPasswordMinimumSymbols(anyOrNull(), any()) } doAnswer answer
        on { setRequiredPasswordComplexity(any()) } doAnswer answer
        on { setUninstallBlocked(anyOrNull(), anyOrNull(), any()) } doAnswer answer
        on { setPreferentialNetworkServiceEnabled(any()) } doAnswer answer
        on { setPersonalAppsSuspended(anyOrNull(), any()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpOverrideApnTestMethods(answer: Answer<*>) {
        // Used by OverrideApnTest
        on { addOverrideApn(anyOrNull(), anyOrNull()) } doAnswer answer
        on { updateOverrideApn(anyOrNull(), any(), anyOrNull()) } doAnswer answer
        on { removeOverrideApn(anyOrNull(), any()) } doAnswer answer
        on { getOverrideApns(anyOrNull()) } doAnswer answer
        on { isOverrideApnEnabled(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpDefaultSmsApplicationTestMethods(
        answer: Answer<*>
    ) {
        // Used by DefaultSmsApplicationTest
        on { setDefaultSmsApplication(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpFactoryResetProtectionPolicyTestMethods(
        answer: Answer<*>
    ) {
        // Used by FactoryResetProtectionPolicyTest
        on { getFactoryResetProtectionPolicy(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpWifiTestMethods(answer: Answer<*>) {
        // Used by WifiTest
        on { getWifiMacAddress(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpSecurityLoggingTestMethods(answer: Answer<*>) {
        // Used by SecurityLoggingTest
        on { isSecurityLoggingEnabled(anyOrNull()) } doAnswer answer
        on { setDelegatedScopes(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
        on { retrieveSecurityLogs(anyOrNull()) } doAnswer answer
        on { getDelegatedScopes(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setPasswordExpirationTimeout(anyOrNull(), any()) } doAnswer answer
        on { setPasswordHistoryLength(anyOrNull(), any()) } doAnswer answer
        on { setMaximumFailedPasswordsForWipe(anyOrNull(), any()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpAdminConfiguredNetworksTestMethods(
        answer: Answer<*>
    ) {
        // Used by AdminConfiguredNetworksTest
        on { setConfiguredNetworksLockdownState(anyOrNull(), any()) } doAnswer answer
        on { hasLockdownAdminConfiguredNetworks(anyOrNull()) } doAnswer answer
    }

    // Suppressing deprecation warnings since we are setting stub responses on deprecated methods.
    @Suppress("DEPRECATION")
    private fun KStubbing<DevicePolicyManager>.setUpStorageEncryptionTestMethods(
        answer: Answer<*>
    ) {
        // Used by StorageEncryptionTest
        on { getStorageEncryptionStatus() } doAnswer answer
        on { setStorageEncryption(anyOrNull(), any()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpPrivateDnsPolicyTestMethods(answer: Answer<*>) {
        // Used by PrivateDnsPolicyTest
        on { getGlobalPrivateDnsHost(anyOrNull()) } doAnswer answer
        on { getGlobalPrivateDnsMode(anyOrNull()) } doAnswer answer
        on { setGlobalPrivateDnsModeSpecifiedHost(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpAdminActionBookkeepingTestMethods(
        answer: Answer<*>
    ) {
        // Used by AdminActionBookkeepingTest
        on { deviceOwnerOrganizationName } doAnswer answer
        on { setOrganizationName(anyOrNull(), anyOrNull()) } doAnswer answer
        on { retrieveSecurityLogs(anyOrNull()) } doAnswer answer
        on { lastSecurityLogRetrievalTime } doAnswer answer
        on { lastBugReportRequestTime } doAnswer answer
        on { isDeviceManaged } doAnswer answer
        on { isCurrentInputMethodSetByOwner } doAnswer answer
        on { installCaCert(anyOrNull(), anyOrNull()) } doAnswer answer
        on { getOwnerInstalledCaCerts(anyOrNull()) } doAnswer answer
        on { retrievePreRebootSecurityLogs(anyOrNull()) } doAnswer answer
        on { lastNetworkLogRetrievalTime } doAnswer answer
    }

    @SuppressLint("MissingPermission")
    private fun KStubbing<DevicePolicyManager>.setUpPrivacyDeviceOwnerTestMethods(
        answer: Answer<*>
    ) {
        // Used by PrivacyDeviceOwnerTest
        on { getDeviceOwner() } doAnswer answer
    }

    @SuppressLint("MissingPermission")
    private fun KStubbing<DevicePolicyManager>.setUpSuspendPackageTestMethods(answer: Answer<*>) {
        // Used by SuspendPackageTest
        on { getPolicyExemptApps() } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpUserSessionTestMethods(answer: Answer<*>) {
        // Used by UserSessionTest
        on { getStartUserSessionMessage(anyOrNull()) } doAnswer answer
        on { setStartUserSessionMessage(anyOrNull(), anyOrNull()) } doAnswer answer
        on { getEndUserSessionMessage(anyOrNull()) } doAnswer answer
        on { setEndUserSessionMessage(anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpListForegroundAffiliatedUsersTestMethods(
        answer: Answer<*>
    ) {
        // Used by ListForegroundAffiliatedUsersTest
        on { listForegroundAffiliatedUsers() } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpDevicePolicySafetyCheckerIntegrationTestMethods(
        answer: Answer<*>
    ) {
        // Used by DevicePolicySafetyCheckerIntegrationTest
        on {
            createAndManageUser(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        } doAnswer answer
        on { lockNow() } doAnswer answer
        on { lockNow(any()) } doAnswer answer
        on { logoutUser(anyOrNull()) } doAnswer answer
        on { reboot(anyOrNull()) } doAnswer answer
        on { removeActiveAdmin(anyOrNull()) } doAnswer answer
        on { removeKeyPair(anyOrNull(), anyOrNull()) } doAnswer answer
        on { requestBugreport(anyOrNull()) } doAnswer answer
        on { setAlwaysOnVpnPackage(anyOrNull(), anyOrNull(), any(), anyOrNull()) } doAnswer answer
        on { setApplicationHidden(anyOrNull(), anyOrNull(), any()) } doAnswer answer
        on { setApplicationRestrictions(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
        on { setCameraDisabled(anyOrNull(), any()) } doAnswer answer
        on { setFactoryResetProtectionPolicy(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setGlobalPrivateDnsModeOpportunistic(anyOrNull()) } doAnswer answer
        on { setKeepUninstalledPackages(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setLockTaskFeatures(anyOrNull(), any()) } doAnswer answer
        on { setMasterVolumeMuted(anyOrNull(), any()) } doAnswer answer
        on { setOverrideApnsEnabled(anyOrNull(), any()) } doAnswer answer
        on { setPermissionPolicy(anyOrNull(), any()) } doAnswer answer
        on { setRestrictionsProvider(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setSystemUpdatePolicy(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setTrustAgentConfiguration(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
        on { startUserInBackground(anyOrNull(), anyOrNull()) } doAnswer answer
        on { stopUser(anyOrNull(), anyOrNull()) } doAnswer answer
        on { switchUser(anyOrNull(), anyOrNull()) } doAnswer answer
        on { wipeData(any(), anyOrNull()) } doAnswer answer
        on { wipeData(any()) } doAnswer answer
    }

    // Suppressing deprecation warnings since we are setting stub responses on deprecated methods.
    @Suppress("DEPRECATION")
    private fun KStubbing<DevicePolicyManager>.setUpCtsVerifierTestMethods(answer: Answer<*>) {
        // Used by CtsVerifier
        on { addUserRestriction(anyOrNull(), anyOrNull()) } doAnswer answer
        on { clearUserRestriction(anyOrNull(), anyOrNull()) } doAnswer answer
        on { clearDeviceOwnerApp(anyOrNull()) } doAnswer answer
        on { setKeyguardDisabledFeatures(anyOrNull(), any()) } doAnswer answer
        on { setPasswordQuality(anyOrNull(), any()) } doAnswer answer
        on { setMaximumTimeToLock(anyOrNull(), any()) } doAnswer answer
        on { setPermittedAccessibilityServices(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setPermittedInputMethods(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setDeviceOwnerLockScreenInfo(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setKeyguardDisabled(anyOrNull(), any()) } doAnswer answer
        on { setAutoTimeRequired(anyOrNull(), any()) } doAnswer answer
        on { setStatusBarDisabled(anyOrNull(), any()) } doAnswer answer
        on { setOrganizationName(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setSecurityLoggingEnabled(anyOrNull(), any()) } doAnswer answer
        on { setPermissionGrantState(anyOrNull(), anyOrNull(), anyOrNull(), any()) } doAnswer answer
        on { clearPackagePersistentPreferredActivities(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setAlwaysOnVpnPackage(anyOrNull(), anyOrNull(), any()) } doAnswer answer
        on { setRecommendedGlobalProxy(anyOrNull(), anyOrNull()) } doAnswer answer
        on { uninstallCaCert(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setSecureSetting(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
        on { setAffiliationIds(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setStartUserSessionMessage(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setEndUserSessionMessage(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setLogoutEnabled(anyOrNull(), any()) } doAnswer answer
        on { removeUser(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setMinimumRequiredWifiSecurityLevel(any()) } doAnswer answer
        on { setWifiSsidPolicy(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpDeviceOwnerProvisioningTestMethods(
        answer: Answer<*>
    ) {
        // Used by DeviceOwnerProvisioningTest
        on { enableSystemApp(any<ComponentName>(), any<String>()) } doAnswer answer
        on { enableSystemApp(any<ComponentName>(), any<Intent>()) } doAnswer answer
        on { canAdminGrantSensorsPermissions() } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpUserControllerDisabledPackagesTestMethods(
        answer: Answer<*>
    ) {
        // Used by UserControlDisabledPackagesTest
        on { setUserControlDisabledPackages(anyOrNull(), anyOrNull()) } doAnswer answer
        on { getUserControlDisabledPackages(anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpTimeTestMethods(answer: Answer<*>) {
        // Used by SetTimeTest
        on { setTime(anyOrNull(), any()) } doAnswer answer
        on { setTimeZone(anyOrNull(), anyOrNull()) } doAnswer answer
        on { setGlobalSetting(anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer answer
    }

    private fun KStubbing<DevicePolicyManager>.setUpCommonMethods(answer: Answer<*>) {
        on { isAdminActive(anyOrNull()) } doAnswer answer
        on { isDeviceOwnerApp(anyOrNull()) } doAnswer answer
        on { isManagedProfile(anyOrNull()) } doAnswer answer
        on { isProfileOwnerApp(anyOrNull()) } doAnswer answer
        on { isAffiliatedUser(anyOrNull()) } doAnswer answer
    }
}
