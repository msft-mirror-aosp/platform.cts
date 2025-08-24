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
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.stubbing.Answer

internal class DevicePolicyManagerWrapper : ServiceManagerWrapper<DevicePolicyManager?>() {
    companion object {
        private val TAG: String = DevicePolicyManagerWrapper::class.java.getSimpleName()

        private val sSpies = HashMap<Context?, DevicePolicyManager?>()
    }

    @SuppressLint("MissingPermission")
    override fun getWrapper(
        context: Context,
        dpm: DevicePolicyManager?,
        answer: Answer<*>,
    ): DevicePolicyManager? {
        val userId = context.userId
        var spy: DevicePolicyManager? = sSpies.get(context)
        if (spy != null) {
            Log.d(TAG, "getWrapper(): returning cached spy for user $userId")
            return spy
        }

        spy = Mockito.spy<DevicePolicyManager?>(dpm)
        val spyString = "DevicePolicyManagerWrapper#" + System.identityHashCode(spy)
        Log.d(TAG, "get(): created spy for user " + context.userId + ": " + spyString)

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            Mockito.doReturn(spyString).`when`<DevicePolicyManager?>(spy).toString()

            // Basic methods used by most tests
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isAdminActive(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isDeviceOwnerApp(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isManagedProfile(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isProfileOwnerApp(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isAffiliatedUser()

            // Used by SetTimeTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setTime(any(), anyLong())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setTimeZone(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setGlobalSetting(any(), any(), any())

            // Used by UserControlDisabledPackagesTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setUserControlDisabledPackages(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getUserControlDisabledPackages(any())

            // Used by DeviceOwnerProvisioningTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .enableSystemApp(any<ComponentName>(), any<String>())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .enableSystemApp(any<ComponentName>(), any<Intent>())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).canAdminGrantSensorsPermissions()

            // Used by CtsVerifier
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).addUserRestriction(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).clearUserRestriction(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).clearDeviceOwnerApp(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setKeyguardDisabledFeatures(any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setPasswordQuality(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setMaximumTimeToLock(any(), anyInt().toLong())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPermittedAccessibilityServices(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPermittedInputMethods(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setDeviceOwnerLockScreenInfo(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setKeyguardDisabled(any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setAutoTimeRequired(any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setStatusBarDisabled(any(), anyBoolean())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setOrganizationName(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setSecurityLoggingEnabled(any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPermissionGrantState(any(), any(), any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .clearPackagePersistentPreferredActivities(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setAlwaysOnVpnPackage(any(), any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setRecommendedGlobalProxy(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).uninstallCaCert(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setMaximumFailedPasswordsForWipe(any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setSecureSetting(any(), any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setAffiliationIds(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setStartUserSessionMessage(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setEndUserSessionMessage(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setLogoutEnabled(any(), anyBoolean())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).removeUser(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setMinimumRequiredWifiSecurityLevel(anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setWifiSsidPolicy(any())

            // Used by DevicePolicySafetyCheckerIntegrationTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .createAndManageUser(any(), any(), any(), any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).lockNow()
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).lockNow(anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).logoutUser(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).reboot(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).removeActiveAdmin(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).removeKeyPair(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).requestBugreport(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setAlwaysOnVpnPackage(any(), any(), anyBoolean(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setApplicationHidden(any(), any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setApplicationRestrictions(any(), any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setCameraDisabled(any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setFactoryResetProtectionPolicy(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setGlobalPrivateDnsModeOpportunistic(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setKeepUninstalledPackages(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setLockTaskFeatures(any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setLockTaskPackages(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setMasterVolumeMuted(any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setOverrideApnsEnabled(any(), anyBoolean())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setPermissionPolicy(any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setRestrictionsProvider(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setSystemUpdatePolicy(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setTrustAgentConfiguration(any(), any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).startUserInBackground(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).stopUser(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).switchUser(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).wipeData(anyInt(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).wipeData(anyInt())

            // Used by ListForegroundAffiliatedUsersTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).listForegroundAffiliatedUsers()

            // Used by UserSessionTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getStartUserSessionMessage(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setStartUserSessionMessage(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getEndUserSessionMessage(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setEndUserSessionMessage(any(), any())

            // Used by SuspendPackageTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPolicyExemptApps()

            // Used by PrivacyDeviceOwnerTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getDeviceOwner()

            // Used by AdminActionBookkeepingTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).deviceOwnerOrganizationName
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).setOrganizationName(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).retrieveSecurityLogs(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).lastSecurityLogRetrievalTime
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).lastBugReportRequestTime
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isDeviceManaged
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isCurrentInputMethodSetByOwner
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).installCaCert(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getOwnerInstalledCaCerts(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).retrievePreRebootSecurityLogs(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).lastNetworkLogRetrievalTime

            // Used by PrivateDnsPolicyTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getGlobalPrivateDnsHost(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getGlobalPrivateDnsMode(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setGlobalPrivateDnsModeSpecifiedHost(any(), any())

            // Used by StorageEncryptionTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getStorageEncryptionStatus()
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setStorageEncryption(any(), anyBoolean())

            // Used by AdminConfiguredNetworksTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setConfiguredNetworksLockdownState(any(), anyBoolean())

            // Used by SecurityLoggingTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isSecurityLoggingEnabled(any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setDelegatedScopes(any(), any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).retrieveSecurityLogs(any())

            // Used by WifiTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getWifiMacAddress(any())

            // Used by AdminConfiguredNetworksTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .hasLockdownAdminConfiguredNetworks(any())

            // Used by DevicePolicyLoggingTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getAutoTimeEnabled(any())

            // Used by FactoryResetProtectionPolicyTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getFactoryResetProtectionPolicy(any())

            // Used by DefaultSmsApplicationTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setDefaultSmsApplication(any(), any())

            // Used by OverrideApnTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).addOverrideApn(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .updateOverrideApn(any(), anyInt(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).removeOverrideApn(any(), anyInt())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getOverrideApns(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isOverrideApnEnabled(any())

            // Used for DevicePolicyLoggingTest.
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumLength(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumNumeric(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumNonLetter(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumLetters(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumLowerCase(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumUpperCase(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordMinimumSymbols(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setRequiredPasswordComplexity(anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setUninstallBlocked(any(), any(), anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPreferentialNetworkServiceEnabled(anyBoolean())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPersonalAppsSuspended(any(), anyBoolean())

            // Used by PasswordRequirementsTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumLength(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumNumeric(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumLetters(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumUpperCase(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumLowerCase(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumNonLetter(any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPasswordMinimumSymbols(any())

            // Used by SecurityLoggingTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getDelegatedScopes(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordExpirationTimeout(any(), anyLong())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPasswordHistoryLength(any(), anyInt())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setMaximumFailedPasswordsForWipe(any(), anyInt())

            // Used by AccessibilityServicesTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getPermittedAccessibilityServices(any<ComponentName>())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getPermittedAccessibilityServices(any<Int>())

            // Used by InputMethodsTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPermittedInputMethods(any())

            // Used by CommonCriteriaModeTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setCommonCriteriaModeEnabled(any(), anyBoolean())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isCommonCriteriaModeEnabled(any())

            // Used by AppRestrictionsDelegateTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getApplicationRestrictions(any(), any())

            // Used by PackageAccessDelegateTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isApplicationHidden(any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isPackageSuspended(any(), any())
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setPackagesSuspended(any(), any(), anyBoolean())

            // Used by PermissionGrantDelegateTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getPermissionGrantState(any(), any(), any())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPermissionPolicy(any())

            // Used by BlockUninstallDelegateTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isUninstallBlocked(any(), any())

            // Used By DelegationTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getDelegatePackages(any(), any())

            // Used by TrustAgentInfoTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .getTrustAgentConfiguration(any(), any())

            // Used by BackupServiceActiveTest
            doAnswer(answer)
                .`when`<DevicePolicyManager?>(spy)
                .setBackupServiceEnabled(any(), anyBoolean())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).isBackupServiceEnabled(any())

            // Used by PendingSystemUpdateTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).notifyPendingSystemUpdate(anyLong())
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getPendingSystemUpdate(any())

            // Used by AffiliationTest (GTS)
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getAffiliationIds(any())

            // Used by LockScreenInfoTest
            doAnswer(answer).`when`<DevicePolicyManager?>(spy).getDeviceOwnerLockScreenInfo()

            // TODO(b/176993670): add more methods below as tests are converted
        } catch (e: Exception) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e)
        }

        sSpies.put(context, spy)
        Log.d(
            TAG,
            "getWrapper(): returning new spy for context " +
                "$context (${context.getPackageName()}) and user $userId",
        )

        return spy
    }
}
