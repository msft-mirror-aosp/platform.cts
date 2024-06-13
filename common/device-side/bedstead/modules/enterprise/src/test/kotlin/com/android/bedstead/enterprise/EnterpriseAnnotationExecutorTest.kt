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
package com.android.bedstead.enterprise

import android.app.admin.DevicePolicyManager
import android.os.Build
import android.os.Bundle
import com.android.bedstead.enterprise.annotations.DEFAULT_DPC_KEY
import com.android.bedstead.enterprise.annotations.DEFAULT_KEY
import com.android.bedstead.enterprise.annotations.EnsureHasDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc
import com.android.bedstead.enterprise.annotations.EnsureHasNoProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnBackgroundDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideManagedProfileUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideOrganizationOwnedProfileUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnFinancedDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnOrganizationOwnedProfileOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfProfileOwnerUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideManagedProfileUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideOrganizationOwnedProfileUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSingleDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSystemDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType
import com.android.bedstead.nene.devicepolicy.ProfileOwner
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME
import com.android.bedstead.remotedpc.RemoteDelegate
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.queryable.annotations.BooleanQuery
import com.android.queryable.annotations.IntegerQuery
import com.android.queryable.annotations.Query
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class EnterpriseAnnotationExecutorTest {

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(
        admin = EnsureHasDelegate.AdminType.DEVICE_OWNER,
        scopes = [DevicePolicyManager.DELEGATION_CERT_INSTALL],
        isPrimary = true
    )
    fun ensureHasPrimaryDelegateAnnotation_dpcReturnsDelegate() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDelegate::class.java)
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(
        admin = EnsureHasDelegate.AdminType.DEVICE_OWNER,
        scopes = [DevicePolicyManager.DELEGATION_CERT_INSTALL],
        isPrimary = false
    )
    fun ensureHasNonPrimaryDelegateAnnotation_dpcReturnsDpc() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDpc::class.java)
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(
        admin = EnsureHasDelegate.AdminType.DEVICE_OWNER,
        scopes = [DevicePolicyManager.DELEGATION_CERT_INSTALL],
        isPrimary = true
    )
    fun ensureHasDelegateAnnotation_dpcCanUseDelegatedFunctionality() {
        assertThat(
            sDeviceState.dpc().devicePolicyManager().getEnrollmentSpecificId()
        ).isNotNull()
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(
        admin = EnsureHasDelegate.AdminType.DEVICE_OWNER,
        scopes = [
            DevicePolicyManager.DELEGATION_CERT_INSTALL,
            DevicePolicyManager.DELEGATION_APP_RESTRICTIONS
        ],
        isPrimary = true
    )
    fun ensureHasDelegateAnnotation_multipleScopes_dpcCanUseAllDelegatedFunctionality() {
        assertThat(
            sDeviceState.dpc().devicePolicyManager().getEnrollmentSpecificId()
        ).isNotNull()
        sDeviceState.dpc()
                .devicePolicyManager()
                .setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName(),
                    Bundle()
                )
    }

    @EnsureHasDeviceOwner(key = EnsureHasDeviceOwner.DEFAULT_KEY, isPrimary = true)
    @AdditionalQueryParameters(
        forTestApp = EnsureHasDeviceOwner.DEFAULT_KEY,
        query = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28))
    )
    @Test
    fun additionalQueryParameters_ensureHasDeviceOwner_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion())
                .isEqualTo(28)
    }

    @Test
    @EnsureHasDeviceOwner
    fun deviceOwner_deviceOwnerIsSet_returnsDeviceOwner() {
        assertThat(sDeviceState.deviceOwner()).isNotNull()
    }

    @Test
    @EnsureHasDeviceOwner(dpc = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28)))
    fun ensureHasDeviceOwnerAnnotation_targetingV28_remoteDpcTargetsV28() {
        val remoteDpc = RemoteDpc.forDevicePolicyController(devicePolicy().getDeviceOwner())
        assertThat(remoteDpc.testApp().pkg().targetSdkVersion()).isEqualTo(28)
    }

    @Test
    @EnsureHasDeviceOwner(dpc = Query(targetSdkVersion = IntegerQuery(isGreaterThanOrEqualTo = 30)))
    fun ensureHasDeviceOwnerAnnoion_targetingGreaterThanOrEqualToV30_remoteDpcTargetsV30() {
        val remoteDpc = RemoteDpc.forDevicePolicyController(devicePolicy().getDeviceOwner())
        assertThat(remoteDpc.testApp().pkg().targetSdkVersion()).isAtLeast(30)
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureHasDelegate(admin = EnsureHasDelegate.AdminType.PRIMARY, scopes = [])
    fun ensureHasDelegateAnnotation_primaryAdminWithoutReplace_dpcReturnsDpc() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDpc::class.java)
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureHasDelegate(admin = EnsureHasDelegate.AdminType.PRIMARY, scopes = [], isPrimary = true)
    fun ensureHasDelegateAnnotation_primaryAdminAndReplace_dpcReturnsDelegate() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDelegate::class.java)
    }

    @EnsureHasDeviceOwner(
        isPrimary = true,
        key = "dpc",
        dpc = Query(isHeadlessDOSingleUser = BooleanQuery(isEqualTo = OptionalBoolean.TRUE))
    )
    @Test
    fun additionalQueryParameters_isHeadlessDOSingleUser_isRespected() {
        assertThat(
            sDeviceState.dpc().testApp().metadata().getString("headless_do_single_user")
        ).isEqualTo("true")
    }

    @EnsureHasDeviceOwner(
        isPrimary = true,
        key = "dpc",
        dpc = Query(isHeadlessDOSingleUser = BooleanQuery(isEqualTo = OptionalBoolean.FALSE))
    )
    @Test
    fun additionalQueryParameters_isNotHeadlessDOSingleUser_isRespected() {
        assertThat(
            sDeviceState.dpc().testApp().metadata().getString("headless_do_single_user")
        ).isNull()
    }

    @EnsureHasDeviceOwner
    @Test
    fun ensureHasDeviceOwnerAnnotation_deviceOwnerIsSet() {
        assertThat(devicePolicy().getDeviceOwner()).isNotNull()
    }

    @Test
    @EnsureHasDeviceOwner
    fun ensureHasDeviceOwnerAnnotation_noQuerySpecified_setsDefaultRemoteDpc() {
        val deviceOwner = devicePolicy().getDeviceOwner()
        assertThat(deviceOwner!!.pkg().packageName())
                .isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
        assertThat(deviceOwner.pkg().targetSdkVersion()).isIn(
            setOf(Build.VERSION.SDK_INT, Build.VERSION_CODES.CUR_DEVELOPMENT)
        )
    }

    @Test
    @EnsureHasNoDeviceOwner
    fun ensureHasNoDeviceOwnerAnnotation_deviceOwnerIsNotSet() {
        assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    @EnsureHasProfileOwner
    fun ensureHasProfileOwnerAnnotation_noQuerySpecified_setsDefaultRemoteDpc() {
        val profileOwner = devicePolicy().getProfileOwner()
        assertThat(
            profileOwner!!.pkg().packageName()
        ).isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
        assertThat(
            profileOwner.pkg().targetSdkVersion()
        ).isIn(setOf(Build.VERSION.SDK_INT, Build.VERSION_CODES.CUR_DEVELOPMENT))
    }

    @Test
    @EnsureHasProfileOwner(dpc = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28)))
    fun ensureHasProfileOwnerAnnotation_targetingV28_remoteDpcTargetsV28() {
        val remoteDpc = RemoteDpc.forDevicePolicyController(devicePolicy().getProfileOwner())
        assertThat(remoteDpc.testApp().pkg().targetSdkVersion()).isEqualTo(28)
    }

    @Test
    @EnsureHasNoDeviceOwner
    fun deviceOwner_deviceOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            sDeviceState.deviceOwner()
        }
    }

    @Test
    @EnsureHasProfileOwner
    fun ensureHasProfileOwnerAnnotation_defaultUser_profileOwnerIsSet() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNotNull()
    }

    @Test
    @EnsureHasNoProfileOwner
    fun ensureHasNoProfileOwnerAnnotation_defaultUser_profileOwnerIsNotSet() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNull()
    }

    @EnsureHasSecondaryUser
    @EnsureHasProfileOwner(onUser = UserType.SECONDARY_USER)
    fun ensureHasProfileOwnerAnnotation_otherUser_setsProfileOwner() {
        assertThat(devicePolicy().getProfileOwner(sDeviceState.secondaryUser())).isNotNull()
    }

    @Test
    @EnsureHasSecondaryUser
    @EnsureHasNoProfileOwner(onUser = UserType.SECONDARY_USER)
    fun ensureHasNoProfileOwnerAnnotation_otherUser_profileOwnerIsNotSet() {
        assertThat(devicePolicy().getProfileOwner(sDeviceState.secondaryUser())).isNull()
    }

    @Test
    @EnsureHasNoProfileOwner
    fun profileOwner_defaultUser_profileOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            sDeviceState.profileOwner()
        }
    }

    @Test
    @EnsureHasProfileOwner
    fun profileOwner_defaultUser_profileOwnerIsSet_returnsProfileOwner() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNotNull()
    }

    @Test
    @EnsureHasSecondaryUser
    @EnsureHasProfileOwner(onUser = UserType.SECONDARY_USER)
    fun profileOwner_otherUser_profileOwnerIsSet_returnsProfileOwner() {
        assertThat(sDeviceState.profileOwner(sDeviceState.secondaryUser())).isNotNull()
    }

    @Test
    @EnsureHasSecondaryUser
    @EnsureHasNoProfileOwner(onUser = UserType.SECONDARY_USER)
    @EnsureHasNoProfileOwner
    fun profileOwner_otherUser_profileOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            sDeviceState.profileOwner()
        }
    }

    @Test
    fun profileOwner_userType_onUserIsNull_throwsException() {
        assertThrows(NullPointerException::class.java) {
            sDeviceState.profileOwner(null as UserType?)
        }
    }

    @Test
    fun profileOwner_userReference_onUserIsNull_throwsException() {
        assertThrows(NullPointerException::class.java) {
            sDeviceState.profileOwner(null as UserReference?)
        }
    }

    @EnsureHasProfileOwner(key = DEFAULT_KEY, isPrimary = true)
    @AdditionalQueryParameters(
        forTestApp = DEFAULT_KEY,
        query = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28))
    )
    @Test
    fun additionalQueryParameters_ensureHasProfileOwner_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28)
    }

    @Test
    @IncludeRunOnBackgroundDeviceOwnerUser
    fun includeRunOnBackgroundDeviceOwnerUserAnnotation_isRunningOnDeviceOwnerUser() {
        assertThat(users().instrumented()).isEqualTo(sDeviceState.dpc().user())
    }

    @Test
    @IncludeRunOnBackgroundDeviceOwnerUser
    fun includeRunOnBackgroundDeviceOwnerUserAnnotation_isNotCurrentUser() {
        assertThat(users().current()).isNotEqualTo(users().instrumented())
    }

    @Test
    @IncludeRunOnCloneProfileAlongsideManagedProfileUsingParentInstance
    fun includeRunOnCloneProfileAlongsideProfileOwnerUsingParentInstance_runsOnCloneProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(CLONE_PROFILE_TYPE_NAME)
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isFalse()
        assertThat(sDeviceState.dpc().isParentInstance).isTrue()
    }

    @Test
    @IncludeRunOnCloneProfileAlongsideOrganizationOwnedProfileUsingParentInstance
    fun includeRunOnCloneProfileAlongsideOrgOwnedProfileOwnerUsingParentInstance_runsOnCloneProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(CLONE_PROFILE_TYPE_NAME)
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isTrue()
        assertThat(sDeviceState.dpc().isParentInstance).isTrue()
    }

    @Test
    @IncludeRunOnFinancedDeviceOwnerUser
    fun includeRunOnFinancedDeviceOwnerUserAnnotation_financedDeviceOwnerTypeSet() {
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isEqualTo(users().instrumented())
        assertThat(devicePolicy().getDeviceOwner()!!.getType()).isEqualTo(DeviceOwnerType.FINANCED)
    }

    @Test
    @IncludeRunOnOrganizationOwnedProfileOwner
    fun includeRunOnOrganizationOwnedProfileOwnerAnnotation_isRunningOnOrganizationOwnedManagedProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner()!!.isOrganizationOwned()).isTrue()
    }

    @Test
    @IncludeRunOnOrganizationOwnedProfileOwner
    fun includeRunOnOrganizationOwnedProfileOwner_isOrganizationOwned() {
        assertThat(
            (sDeviceState.profileOwner(sDeviceState.workProfile())
                    .devicePolicyController() as ProfileOwner).isOrganizationOwned()
        ).isTrue()
    }

    @Test
    @IncludeRunOnParentOfOrganizationOwnedProfileOwner
    fun includeRunOnParentOfOrganizationOwnedProfileOwner_isRunningOnParentOfOrganizationOwnedProfileOwner() {
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isTrue()
        assertThat(sDeviceState.dpc().isParentInstance).isFalse()
    }

    @Test
    @IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance
    fun includeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance_isRunningOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance() {
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isTrue()
        assertThat(sDeviceState.dpc().isParentInstance).isTrue()
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    fun includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_runningOnParentOfProfile() {
        assertThat(sDeviceState.workProfile().parent()).isEqualTo(users().instrumented())
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    fun includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_dpcIsOnProfile() {
        assertThat(sDeviceState.dpc().user()).isEqualTo(sDeviceState.workProfile())
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    fun includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_devicePolicyManagerAffectsParent() {
        val previousRequiredStrongAuthTimeout =
            sLocalDevicePolicyManager.getRequiredStrongAuthTimeout( /* admin= */null)
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredStrongAuthTimeout(
                sDeviceState.dpc().componentName(),
                TIMEOUT
            )
            assertThat(
                sLocalDevicePolicyManager.getRequiredStrongAuthTimeout( /* admin= */null)
            ).isEqualTo(TIMEOUT)
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setRequiredStrongAuthTimeout(
                        sDeviceState.dpc().componentName(),
                        previousRequiredStrongAuthTimeout
                    )
        }
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner
    fun includeRunOnParentOfProfileOwnerAnnotation_isRunningOnParentOfProfileOwner() {
        assertThat(sDeviceState.workProfile()).isNotNull()
    }

    @Test
    @IncludeRunOnPrivateProfileAlongsideManagedProfileUsingParentInstance
    fun includeRunOnPrivateProfileAlongsideProfileOwnerUsingParentInstance_runsOnPrivateProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(PRIVATE_PROFILE_TYPE_NAME)
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isFalse()
        assertThat(sDeviceState.dpc().isParentInstance).isTrue()
    }

    @Test
    @IncludeRunOnPrivateProfileAlongsideOrganizationOwnedProfileUsingParentInstance
    fun includeRunOnPrivateProfileAlongsideOrgOwnedProfileOwnerUsingParentInstance_runsOnPrivateProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(PRIVATE_PROFILE_TYPE_NAME)
        val dpcUser = sDeviceState.dpc().user()
        assertThat(dpcUser.type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME)
        assertThat(devicePolicy().getProfileOwner(dpcUser)!!.isOrganizationOwned()).isTrue()
        assertThat(sDeviceState.dpc().isParentInstance).isTrue()
    }

    @Test
    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
    fun includeRunOnProfileOwnerAnnotation_hasProfileOwner() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNotNull()
    }

    @Test
    @IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile
    fun includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerAnnotation_isRunningOnSecondaryUserInDifferentProfileGroupToProfileOwner() {
        assertThat(
            users().instrumented().type().name()
        ).isEqualTo(com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME)
        assertThat(sDeviceState.workProfile()).isNotEqualTo(users().instrumented())
        assertThat(devicePolicy().getProfileOwner(sDeviceState.workProfile())).isNotNull()

        // TODO(scottjonathan): Assert profile groups are different
    }

    @Test
    @IncludeRunOnSingleDeviceOwnerUser
    fun includeRunOnSingleDeviceOwnerUserAnnotation_isRunningOnSingleDeviceOwnerUser() {
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isEqualTo(users().instrumented())
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isEqualTo(users().main())
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Test")
    @IncludeRunOnSingleDeviceOwnerUser
    fun ensureOnSingleUser_headlessDeviceTypeModeIsSingleUser() {
        assertThat(sDeviceState.dpc().user().isMain()).isTrue()
    }

    @Test
    @IncludeRunOnSystemDeviceOwnerUser
    fun includeRunOnSystemDeviceOwnerUserAnnotation_isRunningOnSystemDeviceOwnerUser() {
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isEqualTo(users().instrumented())
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isEqualTo(users().system())
    }

    @Test
    @IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser
    fun includeRunOnNonAffiliatedDeviceOwnerSecondaryUserAnnotation_isRunningOnNonAffiliatedDeviceOwnerSecondaryUser() {
        assertThat(devicePolicy().getDeviceOwner()!!.user()).isNotEqualTo(users().instrumented())
        assertThat(
            users().instrumented().type().name()
        ).isEqualTo(com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME)
    }

    @Test
    @EnsureHasWorkProfile
    fun workProfile_workProfileProvided_returnsWorkProfile() {
        assertThat(sDeviceState.workProfile()).isNotNull()
    }

    @Test
    @EnsureHasWorkProfile
    fun profile_profileIsProvided_returnsProfile() {
        assertThat(sDeviceState.profile(MANAGED_PROFILE_TYPE_NAME)).isNotNull()
    }

    @Test
    @EnsureHasNoWorkProfile
    fun workProfile_noWorkProfile_throwsException() {
        assertThrows(IllegalStateException::class.java) { sDeviceState.workProfile() }
    }

    @Test
    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    fun workProfile_createdWorkProfile_throwsException() {
        users().createUser()
                .parent(users().instrumented())
                .type(users().supportedType(MANAGED_PROFILE_TYPE_NAME))
                .create().use {
                    assertThrows(IllegalStateException::class.java) {
                        sDeviceState.workProfile()
                    }
                }
    }

    @Test
    @EnsureHasWorkProfile
    fun ensureHasWorkProfileAnnotation_workProfileExists() {
        assertThat(users().findProfileOfType(
            users().supportedType(MANAGED_PROFILE_TYPE_NAME),
            users().instrumented()
        )).isNotNull()
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @EnsureHasNoWorkProfile
    fun ensureHasNoWorkProfileAnnotation_workProfileDoesNotExist() {
        assertThat(users().findProfileOfType(
            users().supportedType(MANAGED_PROFILE_TYPE_NAME),
            users().instrumented()
        )).isNull()
    }

    @Test
    @EnsureHasWorkProfile(
        switchedToParentUser = OptionalBoolean.FALSE
    ) // We don't test the default as it's ANY
    fun ensureHasWorkProfile_specifyNotSwitchedToParentUser_parentIsNotCurrentUser() {
        assertThat(users().current()).isNotEqualTo(sDeviceState.workProfile().parent())
    }

    @Test
    @EnsureHasWorkProfile(switchedToParentUser = OptionalBoolean.TRUE)
    fun ensureHasWorkProfile_specifySwitchedToParentUser_parentIsCurrentUser() {
        assertThat(users().current()).isEqualTo(sDeviceState.workProfile().parent())
    }

    @Test
    @EnsureHasNoProfileOwner
    fun ensureHasNoProfileOwnerAnnotation_currentUserHasNoProfileOwner() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNull()
    }

    @Test
    @EnsureHasNoDeviceOwner
    fun ensureHasNoDeviceOwnerAnnotation_noDeviceOwner() {
        assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    @EnsureHasNoDpc
    fun ensureHasNoDpcAnnotation_currentUserHasNoProfileOwner() {
        assertThat(devicePolicy().getProfileOwner(users().instrumented())).isNull()
    }

    @Test
    @EnsureHasNoDpc
    fun ensureHasNoDpcAnnotation_noDeviceOwner() {
        assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    @EnsureHasNoDpc
    fun ensureHasNoDpcAnnotation_workProfileDoesNotExist() {
        assertThat(users().findProfileOfType(
            users().supportedType(MANAGED_PROFILE_TYPE_NAME),
            users().instrumented()
        )).isNull()
    }

    @Test
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    fun ensureHasWorkProfile_isOrganizationOwned_organizationOwnedIsTrue() {
        assertThat(
            (sDeviceState.profileOwner(
                sDeviceState.workProfile()
            ).devicePolicyController() as ProfileOwner).isOrganizationOwned()
        ).isTrue()
    }

    @Test
    @EnsureHasWorkProfile(isOrganizationOwned = false)
    fun ensureHasWorkProfile_isNotOrganizationOwned_organizationOwnedIsFalse() {
        assertThat((sDeviceState.profileOwner(
            sDeviceState.workProfile()
        )
                .devicePolicyController() as ProfileOwner).isOrganizationOwned())
                .isFalse()
    }

    @EnsureHasWorkProfile(dpcKey = DEFAULT_DPC_KEY, dpcIsPrimary = true)
    @AdditionalQueryParameters(
        forTestApp = DEFAULT_DPC_KEY,
        query = Query(targetSdkVersion = IntegerQuery(isEqualTo = 28))
    )
    @Test
    fun additionalQueryParameters_ensureHasWorkProfile_isRespected() {
        assertThat(sDeviceState.dpc().testApp().targetSdkVersion()).isEqualTo(28)
    }

    companion object {
        @ClassRule
        @Rule
        @JvmField
        val sDeviceState = DeviceState()

        private const val CLONE_PROFILE_TYPE_NAME = "android.os.usertype.profile.CLONE"
        private const val PRIVATE_PROFILE_TYPE_NAME = "android.os.usertype.profile.PRIVATE"
        private const val TIMEOUT: Long = 4000000
        private val sLocalDevicePolicyManager = context()
                .instrumentedContext()
                .getSystemService(DevicePolicyManager::class.java)!!
    }
}
