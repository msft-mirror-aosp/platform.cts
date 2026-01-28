/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.enterprise;

import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_CONTROLLER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_FINANCED_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_SINGLE_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_SYSTEM_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_USER_CONTROLLER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_IN_BACKGROUND;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_AFFILIATED_OTHER_USERS;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_PARENT;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_CHILD_PROFILES_WITHOUT_INHERITANCE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_OTHER_USERS;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.CAN_BE_DELEGATED;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.DO_NOT_APPLY_TO_CANNOT_SET_POLICY_TESTS;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.INHERITABLE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.NO;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnAdditionalUserWithDeviceControllerKt.includeRunOnAdditionalUserWithDeviceController;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnAdditionalUserWithInitialUserControllerKt.includeRunOnAdditionalUserWithInitialUserController;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnAffiliatedDeviceOwnerSecondaryUserKt.includeRunOnAffiliatedDeviceOwnerSecondaryUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnBackgroundDeviceOwnerUserKt.includeRunOnBackgroundDeviceOwnerUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideManagedProfileKt.includeRunOnCloneProfileAlongsideManagedProfile;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideManagedProfileUsingParentInstanceKt.includeRunOnCloneProfileAlongsideManagedProfileUsingParentInstance;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideOrganizationOwnedProfileKt.includeRunOnCloneProfileAlongsideOrganizationOwnedProfile;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnCloneProfileAlongsideOrganizationOwnedProfileUsingParentInstanceKt.includeRunOnCloneProfileAlongsideOrganizationOwnedProfileUsingParentInstance;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnDevicePolicyManagementRoleHolderProfileKt.includeRunOnDevicePolicyManagementRoleHolderProfile;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnDevicePolicyManagementRoleHolderSecondaryUserKt.includeRunOnDevicePolicyManagementRoleHolderSecondaryUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnDevicePolicyManagementRoleHolderUserKt.includeRunOnDevicePolicyManagementRoleHolderUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnFinancedDeviceOwnerUserKt.includeRunOnFinancedDeviceOwnerUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideManagedProfileKt.includeRunOnPrivateProfileAlongsideManagedProfile;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideManagedProfileUsingParentInstanceKt.includeRunOnPrivateProfileAlongsideManagedProfileUsingParentInstance;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideOrganizationOwnedProfileKt.includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrivateProfileAlongsideOrganizationOwnedProfileUsingParentInstanceKt.includeRunOnPrivateProfileAlongsideOrganizationOwnedProfileUsingParentInstance;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSingleDeviceOwnerUserKt.includeRunOnSingleDeviceOwnerUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSystemDeviceOwnerUserKt.includeRunOnSystemDeviceOwnerUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSystemUserWithDeviceControllerKt.includeRunOnSystemUserWithDeviceController;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUserKt.includeRunOnUnaffiliatedDeviceOwnerSecondaryUser;
import static com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnUserControllerKt.includeRunOnUserController;
import static com.android.bedstead.harrier.UserType.INSTRUMENTED_USER;
import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_KEY;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_APP_RESTRICTIONS;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_BLOCK_UNINSTALL;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_CERT_INSTALL;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_CERT_SELECTION;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_ENABLE_SYSTEM_APP;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_INSTALL_EXISTING_PACKAGE;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_KEEP_UNINSTALLED_PACKAGES;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_NETWORK_LOGGING;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_PACKAGE_ACCESS;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_PERMISSION_GRANT;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_SECURITY_LOGGING;
import static com.android.bedstead.testapp.TestAppQueryBuilder.queryBuilder;
import static com.android.bedstead.testapps.TestAppsComponent.DELEGATE_KEY;
import static com.android.xts.root.annotations.RequireRootInstrumentationKt.requireRootInstrumentation;

import com.android.bedstead.enterprise.annotations.EnsureHasDelegate;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDelegate;
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.enterprise.annotations.EnsureTestAppInstalledAsPrimaryDPC;
import com.android.bedstead.enterprise.annotations.EnterprisePolicy;
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.AppOp;
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.Permission;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnAffiliatedProfileOwnerAdditionalUser;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnOrganizationOwnedProfileOwner;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwner;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfProfileOwnerUsingParentInstance;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToOrganizationOwnedProfileOwnerProfileUsingParentInstance;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnUnaffiliatedProfileOwnerAdditionalUser;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DynamicParameterizedAnnotation;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.queryable.annotations.Query;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for enterprise policy tests.
 */
public final class Policy {

    private static final String DELEGATE_PACKAGE_NAME = "com.android.Delegate";

    // Delegate scopes to be used for a "CannotSet" state. All delegate scopes except the ones which
    // should allow use of the API will be granted
    private static final ImmutableSet<String> ALL_DELEGATE_SCOPES = ImmutableSet.of(
            DELEGATION_CERT_INSTALL,
            DELEGATION_APP_RESTRICTIONS,
            DELEGATION_BLOCK_UNINSTALL,
            DELEGATION_PERMISSION_GRANT,
            DELEGATION_PACKAGE_ACCESS,
            DELEGATION_ENABLE_SYSTEM_APP,
            DELEGATION_INSTALL_EXISTING_PACKAGE,
            DELEGATION_KEEP_UNINSTALLED_PACKAGES,
            DELEGATION_NETWORK_LOGGING,
            DELEGATION_CERT_SELECTION,
            DELEGATION_SECURITY_LOGGING
    );

    // This is a map containing all Include* annotations and the flags which lead to them
    // This is not validated - every state must have a single APPLIED_BY annotation
    private static final ImmutableMap<Integer, Function<EnterprisePolicy, Set<Annotation>>>
            STATE_ANNOTATIONS =
            ImmutableMap.<Integer, Function<EnterprisePolicy, Set<Annotation>>>builder()
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnSystemDeviceOwnerUser()))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnSystemDeviceOwnerUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND, singleAnnotation(includeRunOnBackgroundDeviceOwnerUser()))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnBackgroundDeviceOwnerUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnUnaffiliatedDeviceOwnerSecondaryUser()))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_UNAFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnUnaffiliatedDeviceOwnerSecondaryUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_AFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnAffiliatedDeviceOwnerSecondaryUser()))
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_TO_AFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnAffiliatedDeviceOwnerSecondaryUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_SINGLE_DEVICE_OWNER | APPLIES_TO_OWN_USER, singleAnnotation(
                            includeRunOnSingleDeviceOwnerUser()))
                    .put(APPLIED_BY_SINGLE_DEVICE_OWNER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED,
                            generateDelegateAnnotation(
                            includeRunOnSingleDeviceOwnerUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_SINGLE_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND, singleAnnotation(includeRunOnBackgroundDeviceOwnerUser()))
                    .put(APPLIED_BY_SINGLE_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnBackgroundDeviceOwnerUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_DPM_ROLE_HOLDER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnDevicePolicyManagementRoleHolderUser()))
                    .put(APPLIED_BY_DPM_ROLE_HOLDER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnDevicePolicyManagementRoleHolderSecondaryUser()))
                    .put(APPLIED_BY_DPM_ROLE_HOLDER | APPLIES_TO_UNAFFILIATED_CHILD_PROFILES_WITHOUT_INHERITANCE, singleAnnotation(includeRunOnDevicePolicyManagementRoleHolderProfile()))

                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnAffiliatedProfileOwnerAdditionalUser()))
                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnAffiliatedProfileOwnerAdditionalUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnUnaffiliatedProfileOwnerAdditionalUser()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnUnaffiliatedProfileOwnerAdditionalUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnProfileOwnerPrimaryUser()))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnProfileOwnerPrimaryUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner(), /* isPrimary= */ true))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnOrganizationOwnedProfileOwner()))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnOrganizationOwnedProfileOwner(), /* isPrimary= */ true))

                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT, singleAnnotation(includeRunOnParentOfProfileOwnerWithNoDeviceOwner()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnParentOfProfileOwnerWithNoDeviceOwner(), /* isPrimary= */ true))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT, singleAnnotation(includeRunOnParentOfOrganizationOwnedProfileOwner()))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnParentOfOrganizationOwnedProfileOwner(), /* isPrimary= */ true))

                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile(), /* isPrimary= */ true))

                    .put(APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnSecondaryUserInDifferentProfileGroupToOrganizationOwnedProfileOwnerProfileUsingParentInstance()))

                    // The model here is that APPLIED_BY_PARENT + APPLIES_TO_OWN_USER means it applies to the parent of the DPC - I'm not sure this is the best model (APPLIES_TO_PARENT would also be reasonable)
                    .put(APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE
                            | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnParentOfProfileOwnerUsingParentInstance()))
                    .put(APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance()))

                    .put(APPLIED_BY_FINANCED_DEVICE_OWNER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnFinancedDeviceOwnerUser()))

                    .put(APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER | INHERITABLE, multipleAnnotations(includeRunOnCloneProfileAlongsideManagedProfileUsingParentInstance(), includeRunOnPrivateProfileAlongsideManagedProfileUsingParentInstance()))
                    .put(APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER | INHERITABLE, multipleAnnotations(includeRunOnCloneProfileAlongsideOrganizationOwnedProfileUsingParentInstance(), includeRunOnPrivateProfileAlongsideOrganizationOwnedProfileUsingParentInstance()))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | INHERITABLE, multipleAnnotations(includeRunOnCloneProfileAlongsideOrganizationOwnedProfile(), includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | INHERITABLE, multipleAnnotations(includeRunOnCloneProfileAlongsideManagedProfile(), includeRunOnPrivateProfileAlongsideManagedProfile()))

                    // Multi-User Management states.
                    .put(APPLIED_BY_USER_CONTROLLER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnUserController()))
                    .put(APPLIED_BY_USER_CONTROLLER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnAdditionalUserWithInitialUserController()))
                    .put(APPLIED_BY_DEVICE_CONTROLLER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnSystemUserWithDeviceController()))
                    .put(APPLIED_BY_DEVICE_CONTROLLER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnAdditionalUserWithDeviceController()))
                    .build();
    // This must contain one key for every APPLIED_BY that is being used, and maps to the
    // "default" for testing that DPC type
    // in general this will be a state which runs on the same user as the dpc.
    // The key is the APPLIED_BY annotation and the value is a function which takes the policy and
    // a boolean indicating if this is the "can set" state (if it is false - it must be the
    // "cannot set" state). It should return a set of annotations to use.
    private static final ImmutableMap<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>>
            DPC_STATE_ANNOTATIONS_BASE =
            ImmutableMap.<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>>builder()
                    .put(APPLIED_BY_SYSTEM_DEVICE_OWNER, (flags, canSet) -> hasFlag(flags.dpc(), APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIES_IN_BACKGROUND) ? singleAnnotation(includeRunOnBackgroundDeviceOwnerUser()).apply(flags) : singleAnnotation(
                            includeRunOnSystemDeviceOwnerUser()).apply(flags))
                    .put(APPLIED_BY_SINGLE_DEVICE_OWNER, (flags, canSet) -> hasFlag(flags.dpc(), APPLIED_BY_SINGLE_DEVICE_OWNER
                            | APPLIES_IN_BACKGROUND) ? singleAnnotation(includeRunOnBackgroundDeviceOwnerUser()).apply(flags) : singleAnnotation(
                            includeRunOnSingleDeviceOwnerUser()).apply(flags))
                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER, (flags, canSet) -> singleAnnotation(includeRunOnAffiliatedProfileOwnerAdditionalUser()).apply(flags))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER, (flags, canSet) -> singleAnnotation(includeRunOnProfileOwnerPrimaryUser()).apply(flags))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO, (flags, canSet) -> singleAnnotation(includeRunOnProfileOwnerPrimaryUser()).apply(flags))
                    .put(APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE, (flags, canSet) -> singleAnnotation(includeRunOnOrganizationOwnedProfileOwner()).apply(flags))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE, (flags, canSet) -> singleAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner()).apply(flags))
                    .put(APPLIED_BY_FINANCED_DEVICE_OWNER, (flags, canSet) -> singleAnnotation(includeRunOnFinancedDeviceOwnerUser()).apply(flags))
                    .put(APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE, (flags, canSet) -> singleAnnotation(includeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance()).apply(flags))
                    .put(APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE, (flags, canSet) -> singleAnnotation(includeRunOnParentOfProfileOwnerUsingParentInstance()).apply(flags))
                    .put(APPLIED_BY_DPM_ROLE_HOLDER, (flags, canSet) -> singleAnnotation(includeRunOnDevicePolicyManagementRoleHolderUser()).apply(flags))
                    .put(APPLIED_BY_USER_CONTROLLER, (flags, canSet) -> userControllerIfCanSet().apply(flags, canSet))
                    .put(APPLIED_BY_DEVICE_CONTROLLER, (flags, canSet) -> singleAnnotation(includeRunOnSystemUserWithDeviceController()).apply(flags))
                    .build();

    private static BiFunction<EnterprisePolicy, Boolean, Set<Annotation>> userControllerIfCanSet() {
        return (enterprisePolicy, canSet) -> {
            if (!canSet) {
                // Do not include `APPLIED_BY_USER_CONTROLLER` in CannotSetPolicyTest to not
                // accidentally break existing tests.
                // TODO (b/463924962): Include this in cannotSetPolicyTest once existing tests
                // do not fail.
                return ImmutableSet.of();
            }

            return singleAnnotation(includeRunOnUserController()).apply(enterprisePolicy);
        };
    }

    private static final Map<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>>
            DPC_STATE_ANNOTATIONS = DPC_STATE_ANNOTATIONS_BASE.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Policy::addGeneratedStates));
    private static final int APPLIED_BY_FLAGS =
            APPLIED_BY_SYSTEM_DEVICE_OWNER | APPLIED_BY_SINGLE_DEVICE_OWNER
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_FINANCED_DEVICE_OWNER
                    | APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_DPM_ROLE_HOLDER
                    | APPLIED_BY_USER_CONTROLLER
                    | APPLIED_BY_DEVICE_CONTROLLER;
    private static final Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>>
            ANNOTATIONS_MAP = calculateAnnotationsMap(STATE_ANNOTATIONS);

    private Policy() {

    }

    @AutoAnnotation
    public static EnterprisePolicy enterprisePolicy(int[] dpc, Permission[] permissions,
            AppOp[] appOps, String[] delegatedScopes) {
        return new AutoAnnotation_Policy_enterprisePolicy(
                dpc, permissions, appOps, delegatedScopes);
    }

    @AutoAnnotation
    private static EnsureTestAppInstalledAsPrimaryDPC ensureTestAppInstalledAsPrimaryDPC(
            String key, Query query, UserType onUser) {
        return new AutoAnnotation_Policy_ensureTestAppInstalledAsPrimaryDPC(
                key, query, onUser);
    }

    @AutoAnnotation
    private static EnsureTestAppHasPermission ensureTestAppHasPermission(
            String testAppKey, String[] value, FailureMode failureMode) {
        return new AutoAnnotation_Policy_ensureTestAppHasPermission(testAppKey, value, failureMode);
    }

    @AutoAnnotation
    private static EnsureTestAppDoesNotHavePermission ensureTestAppDoesNotHavePermission(
            String testAppKey, String[] value, FailureMode failureMode) {
        return new AutoAnnotation_Policy_ensureTestAppDoesNotHavePermission(
                testAppKey, value, failureMode);
    }

    @AutoAnnotation
    private static EnsureTestAppHasAppOp ensureTestAppHasAppOp(String testAppKey, String[] value) {
        return new AutoAnnotation_Policy_ensureTestAppHasAppOp(testAppKey, value);
    }

    @AutoAnnotation
    private static IncludeNone includeNone() {
        return new AutoAnnotation_Policy_includeNone();
    }

    @AutoAnnotation
    public static IncludeRunOnAffiliatedProfileOwnerAdditionalUser includeRunOnAffiliatedProfileOwnerAdditionalUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedProfileOwnerAdditionalUser();
    }

    @AutoAnnotation
    public static IncludeRunOnUnaffiliatedProfileOwnerAdditionalUser includeRunOnUnaffiliatedProfileOwnerAdditionalUser() {
        return new AutoAnnotation_Policy_includeRunOnUnaffiliatedProfileOwnerAdditionalUser();
    }

    @AutoAnnotation
    public static IncludeRunOnProfileOwnerProfileWithNoDeviceOwner includeRunOnProfileOwnerProfileWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerProfileWithNoDeviceOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile() {
        return new AutoAnnotation_Policy_includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile();
    }

    @AutoAnnotation
    public static IncludeRunOnSecondaryUserInDifferentProfileGroupToOrganizationOwnedProfileOwnerProfileUsingParentInstance includeRunOnSecondaryUserInDifferentProfileGroupToOrganizationOwnedProfileOwnerProfileUsingParentInstance() {
        return new AutoAnnotation_Policy_includeRunOnSecondaryUserInDifferentProfileGroupToOrganizationOwnedProfileOwnerProfileUsingParentInstance();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner includeRunOnParentOfProfileOwnerWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwnerWithNoDeviceOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnOrganizationOwnedProfileOwner includeRunOnOrganizationOwnedProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnOrganizationOwnedProfileOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnParentOfOrganizationOwnedProfileOwner includeRunOnParentOfOrganizationOwnedProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfOrganizationOwnedProfileOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnProfileOwnerPrimaryUser includeRunOnProfileOwnerPrimaryUser() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerPrimaryUser();
    }

    @AutoAnnotation
    private static EnsureHasDelegate ensureHasDelegate(EnsureHasDelegate.AdminType admin,
            String[] scopes, boolean isPrimary) {
        return new AutoAnnotation_Policy_ensureHasDelegate(admin, scopes, isPrimary);
    }

    @AutoAnnotation
    private static EnsureHasNoWorkProfile ensureHasNoWorkProfile() {
        return new AutoAnnotation_Policy_ensureHasNoWorkProfile();
    }

    @AutoAnnotation
    private static EnsureHasDeviceOwner ensureHasDeviceOwner(String key, Query dpc) {
        return new AutoAnnotation_Policy_ensureHasDeviceOwner(key, dpc);
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfProfileOwnerUsingParentInstance includeRunOnParentOfProfileOwnerUsingParentInstance() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwnerUsingParentInstance();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance
            includeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance() {
        return new AutoAnnotation_Policy_includeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance();
    }

    @AutoAnnotation
    private static RequireRunOnSystemUser requireRunOnSystemUser(OptionalBoolean switchedToUser) {
        return new AutoAnnotation_Policy_requireRunOnSystemUser(switchedToUser);
    }

    private static Function<EnterprisePolicy, Set<Annotation>> singleAnnotation(
            Annotation annotation) {
        return (i) -> ImmutableSet.of(annotation);
    }

    private static Function<EnterprisePolicy, Set<Annotation>> multipleAnnotations(
            Annotation... annotations) {
        return (i) -> ImmutableSet.copyOf(annotations);
    }

    private static Function<EnterprisePolicy, Set<Annotation>> generateDelegateAnnotation(
            Annotation annotation, boolean isPrimary) {
        return (policy) -> {
            Annotation[] existingAnnotations = filterAnnotations(
                    annotation.annotationType().getAnnotations(), EnsureHasNoDelegate.class);
            return Arrays.stream(policy.delegatedScopes())
                    .map(scope -> {
                        Annotation[] newAnnotations = Arrays.copyOf(existingAnnotations,
                                existingAnnotations.length + 1);
                        newAnnotations[newAnnotations.length - 1] = ensureHasDelegate(
                                EnsureHasDelegate.AdminType.PRIMARY, new String[]{scope},
                                isPrimary);

                        return new DynamicParameterizedAnnotation(
                                annotation.annotationType().getSimpleName() + "Delegate_" + scope,
                                newAnnotations);
                    }).collect(Collectors.toSet());
        };
    }

    private static Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> calculateAnnotationsMap(
            Map<Integer, Function<EnterprisePolicy, Set<Annotation>>> annotations) {
        Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> b = new HashMap<>();

        for (Map.Entry<Integer, Function<EnterprisePolicy, Set<Annotation>>> i :
                annotations.entrySet()) {
            if (!b.containsKey(i.getValue())) {
                b.put(i.getValue(), new HashSet<>());
            }

            b.get(i.getValue()).add(i.getKey());
        }

        return b;
    }

    private static BiFunction<EnterprisePolicy, Boolean, Set<Annotation>> addGeneratedStates(
            ImmutableMap.Entry<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>> entry) {
        return (policy, canSet) -> {
            if (hasFlag(policy.dpc(), entry.getKey() | CAN_BE_DELEGATED)) {
                Set<Annotation> results = new HashSet<>(entry.getValue().apply(policy, canSet));
                results.addAll(results.stream().flatMap(
                        t -> generateDelegateAnnotation(t, /* isPrimary= */ true).apply(
                                policy).stream())
                        .collect(Collectors.toSet()));
                return results;
            }

            return entry.getValue().apply(policy, canSet);
        };
    }

    /**
     * Get annotations that were derived against each passed in {@link EnterprisePolicy}
     */
    public static Map<String, Set<Annotation>> getAnnotationsForPolicies(
            List<EnterprisePolicyWrapper> enterprisePolicies) {
        Map<String, Set<Annotation>> policyClassToAnnotationsMap = new HashMap<>();

        for (EnterprisePolicyWrapper enterprisePolicyWrapper : enterprisePolicies) {
            EnterprisePolicy enterprisePolicyAnnotation = enterprisePolicy(
                    enterprisePolicyWrapper.dpc(),
                    enterprisePolicyWrapper.permissions(),
                    enterprisePolicyWrapper.appOps(),
                    enterprisePolicyWrapper.delegatedScopes());
            Set<Annotation> annotations = new HashSet<>();

            for (Map.Entry<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> annotation :
                    ANNOTATIONS_MAP.entrySet()) {
                if (policyWillApply(enterprisePolicyAnnotation.dpc(), annotation.getValue())) {
                    annotations.addAll(annotation.getKey().apply(enterprisePolicyAnnotation));
                }
            }

            for (Permission permission : enterprisePolicyAnnotation.permissions()) {
                annotations.add(generateParameterizedPermissionAnnotation(permission));
            }

            if (policyClassToAnnotationsMap.get(enterprisePolicyWrapper.policyClass()) == null) {
                policyClassToAnnotationsMap.put(enterprisePolicyWrapper.policyClass(), annotations);
            } else {
                policyClassToAnnotationsMap
                        .get(enterprisePolicyWrapper.policyClass())
                        .addAll(annotations);
            }
        }

        return policyClassToAnnotationsMap;
    }

    /**
     * Get {@link EnterprisePolicy} values along with the class it was annotated on
     */
    public static List<EnterprisePolicyWrapper> getEnterprisePolicyWithCallingClass(
            Class<?>[] policies) {
        if (policies.length == 0) {
            throw new IllegalStateException("Cannot get EnterprisePolicy for zero policy classes");
        }

        List<EnterprisePolicyWrapper> enterprisePolicies = new ArrayList<>();

        for (Class<?> policy : policies) {
            EnterprisePolicy enterprisePolicy = policy.getAnnotation(EnterprisePolicy.class);
            if (enterprisePolicy == null) {
                throw new IllegalStateException(
                        "Policy class must be annotated with EnterprisePolicy");
            }

            Policy.validateFlags(policy.getName(), enterprisePolicy.dpc());

            enterprisePolicies.add(
                    new EnterprisePolicyWrapper(
                            policy.getName(),
                            enterprisePolicy.dpc(),
                            enterprisePolicy.permissions(),
                            enterprisePolicy.appOps(),
                            enterprisePolicy.delegatedScopes()));
        }

        return enterprisePolicies;
    }

    /**
     * Get parameterized test runs for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> policyAppliesStates(EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        for (Map.Entry<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> annotation :
                ANNOTATIONS_MAP.entrySet()) {
            if (policyWillApply(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.addAll(annotation.getKey().apply(enterprisePolicy));
            }
        }

        for (AppOp appOp : enterprisePolicy.appOps()) {
            // TODO(b/219750042): Currently we only test that app ops apply to the current user
            Annotation[] withAppOpAnnotations = new Annotation[]{
                    ensureTestAppInstalledAsPrimaryDPC(DELEGATE_KEY, queryBuilder()
                                    .wherePackageName().isEqualTo(DELEGATE_PACKAGE_NAME)
                                    .toAnnotation(),
                            INSTRUMENTED_USER),
                    ensureTestAppHasAppOp(DELEGATE_KEY, new String[]{appOp.appliedWith()})
            };
            annotations.add(
                    new DynamicParameterizedAnnotation(
                            "AppOp_" + appOp.appliedWith(), withAppOpAnnotations));
        }

        for (Permission permission : enterprisePolicy.permissions()) {
            annotations.add(generateParameterizedPermissionAnnotation(permission));
        }

        removeShadowingAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static boolean policyWillApply(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(policyFlags, annotationFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean policyWillNotApply(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(annotationFlag,
                    DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS, /* nonMatchingFlag= */ NO)) {
                return false; // We don't support using this annotation for PolicyDoesNotApply tests
            }

            int appliedByFlag = APPLIED_BY_FLAGS & annotationFlag;
            int otherFlags = annotationFlag ^ appliedByFlag; // remove the appliedByFlag
            if (hasFlag(policyFlags, /* matchingFlag= */ appliedByFlag, /* nonMatchingFlag= */
                    otherFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get parameterized test runs for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> policyDoesNotApplyStates(EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        for (Map.Entry<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> annotation :
                ANNOTATIONS_MAP.entrySet()) {
            if (policyWillNotApply(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.addAll(annotation.getKey().apply(enterprisePolicy));
            }
        }

        removeShadowedAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    /**
     * Get parameterized test runs where the policy cannot be set for the given policy.
     */
    public static List<Annotation> cannotSetPolicyStates(
            EnterprisePolicy enterprisePolicy,
            boolean includeDeviceAdminStates, boolean includeNonDeviceAdminStates) {

        Set<Annotation> annotations = new HashSet<>();
        if (includeDeviceAdminStates) {
            int allFlags = 0;
            for (int p : enterprisePolicy.dpc()) {
                allFlags = allFlags | p;
            }

            for (Map.Entry<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>> appliedByFlag :
                    DPC_STATE_ANNOTATIONS.entrySet()) {

                if ((appliedByFlag.getKey()
                        & APPLIED_BY_DPM_ROLE_HOLDER) == APPLIED_BY_DPM_ROLE_HOLDER) {
                    if (!includeNonDeviceAdminStates) {
                        // Temp fix to avoid random failing tests
                        continue;
                    }
                }

                if (hasFlag(appliedByFlag.getKey(),
                        DO_NOT_APPLY_TO_CANNOT_SET_POLICY_TESTS, /* nonMatchingFlag= */ NO)) {
                    continue;
                }

                if ((appliedByFlag.getKey() & allFlags) == 0) {
                    annotations.addAll(appliedByFlag.getValue().apply(enterprisePolicy, /* canSet= */ false));
                }
            }
        }

        if (includeNonDeviceAdminStates) {
            Set<String> validScopes = ImmutableSet.copyOf(enterprisePolicy.delegatedScopes());
            String[] scopes =
                    ALL_DELEGATE_SCOPES.stream()
                            .filter(i -> !validScopes.contains(i))
                            .toArray(String[]::new);
            String[] validPermissions =
                    Arrays.stream(enterprisePolicy.permissions())
                            .flatMap(permission -> Arrays.stream(permission.appliedWith()))
                            .toArray(String[]::new);

            if (BedsteadJUnit4.isDebug()) {
                // Add a non-DPC with no delegate scopes
                annotations.add(
                        createTestCaseWithoutPermissionsWithScopes(
                                "DelegateWithNoScopes", validPermissions, new String[] {}));

                for (String scope : scopes) {
                    annotations.add(
                            createTestCaseWithoutPermissionsWithScopes(
                                    "DelegateWithScope:" + scope,
                                    validPermissions,
                                    new String[] {scope}));
                }
                annotations.add(
                        createTestCaseWithoutPermissions(
                                "TestAppWithoutPermission", validPermissions));
            } else {
                // TODO: We should add @RequireRootInstrumentation if the permission is root-only
                //  - but we need to be able to determine that from the host
                annotations.add(
                        createTestCaseWithoutPermissionsWithScopes(
                                "DelegateWithoutValidScope", validPermissions, scopes));
                annotations.add(
                        createTestCaseWithoutPermissions(
                                "TestAppWithoutPermission", validPermissions));
            }
        }

        removeShadowedAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static Annotation createTestCaseWithoutPermissions(String name, String[] permissions) {
        Annotation[] testCaseAnnotations = {
            requireRunOnSystemUser(OptionalBoolean.ANY),
            ensureHasNoWorkProfile(),
            ensureTestAppInstalledAsPrimaryDPC(
                    DEFAULT_KEY,
                    queryBuilder()
                            .wherePackageName()
                            .isEqualTo(DELEGATE_PACKAGE_NAME)
                            .toAnnotation(),
                    INSTRUMENTED_USER),
            ensureTestAppDoesNotHavePermission(DEFAULT_KEY, permissions, FailureMode.SKIP)
        };
        return new DynamicParameterizedAnnotation(name, testCaseAnnotations);
    }

    private static Annotation createTestCaseWithoutPermissionsWithScopes(
            String name, String[] permissions, String[] scopes) {
        Annotation[] testCaseAnnotations = {
            requireRunOnSystemUser(OptionalBoolean.ANY),
            ensureHasNoWorkProfile(),
            ensureHasDeviceOwner("dpc", queryBuilder().toAnnotation()),
            ensureHasDelegate(EnsureHasDelegate.AdminType.PRIMARY, scopes, /* isPrimary= */ true),
            ensureTestAppDoesNotHavePermission(DELEGATE_KEY, permissions, FailureMode.SKIP)
        };
        return new DynamicParameterizedAnnotation(name, testCaseAnnotations);
    }

    /**
     * /** Return {@code annotations} excluding any which are of type {@code
     * filteredAnnotationClass}.
     */
    private static Annotation[] filterAnnotations(
            Annotation[] annotations, Class<? extends Annotation> filteredAnnotationClass) {
        return Arrays.stream(annotations).filter(
                f -> !f.annotationType().equals(filteredAnnotationClass))
                .toArray(Annotation[]::new);
    }

    /**
     * Get state annotations where the policy can be set for the given policy.
     */
    public static List<Annotation> canSetPolicyStates(
            EnterprisePolicy enterprisePolicy, boolean singleTestOnly) {
        Set<Annotation> annotations = new HashSet<>();

        int allFlags = 0;
        for (int p : enterprisePolicy.dpc()) {
            allFlags = allFlags | p;
        }

        for (Map.Entry<Integer, BiFunction<EnterprisePolicy, Boolean, Set<Annotation>>> appliedByFlag :
                DPC_STATE_ANNOTATIONS.entrySet()) {
            if ((appliedByFlag.getKey() & allFlags) == appliedByFlag.getKey()) {
                annotations.addAll(appliedByFlag.getValue().apply(enterprisePolicy, /* canSet= */ true));
            }
        }

        for (AppOp appOp : enterprisePolicy.appOps()) {
            // TODO(b/219750042): Currently we only test that app ops can be set as the primary user
            Annotation[] withAppOpAnnotations = new Annotation[]{
                    ensureTestAppInstalledAsPrimaryDPC(DELEGATE_KEY,
                            queryBuilder()
                                    .wherePackageName().isEqualTo(DELEGATE_PACKAGE_NAME)
                                    .toAnnotation(), INSTRUMENTED_USER),
                    ensureTestAppHasAppOp(DELEGATE_KEY, new String[]{appOp.appliedWith()})
            };
            annotations.add(
                    new DynamicParameterizedAnnotation(
                            "AppOp_" + appOp.appliedWith(), withAppOpAnnotations));
        }

        for (Permission permission : enterprisePolicy.permissions()) {
            annotations.add(generateParameterizedPermissionAnnotation(permission));
        }

        removeShadowingAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        List<Annotation> annotationList = new ArrayList<>(annotations);

        if (singleTestOnly) {
            // We select one annotation in an arbitrary but deterministic way
            annotationList.sort(Comparator.comparing(
                    a -> a instanceof DynamicParameterizedAnnotation
                            ? "DynamicParameterizedAnnotation" : a.annotationType().getName()));

            // We don't want a delegate to be the representative test
            Annotation firstAnnotation = annotationList.stream()
                    .filter(i -> !(i instanceof DynamicParameterizedAnnotation))
                    .findFirst().get();
            annotationList.clear();
            annotationList.add(firstAnnotation);
        }

        return annotationList;
    }

    /** Validate flags used by a DPC policy. */
    public static void validateFlags(String policyName, int[] values) {
        int usedAppliedByFlags = 0;

        for (int value : values) {
            validateFlags(policyName, value);
            int newUsedAppliedByFlags = usedAppliedByFlags | (value & APPLIED_BY_FLAGS);
            if (newUsedAppliedByFlags == usedAppliedByFlags) {
                throw new IllegalStateException(
                        "Cannot have more than one policy flag APPLIED by the same component. "
                                + "Error in policy " + policyName);
            }
            usedAppliedByFlags = newUsedAppliedByFlags;
        }
    }

    private static void validateFlags(String policyName, int value) {
        int matchingAppliedByFlags = APPLIED_BY_FLAGS & value;

        if (matchingAppliedByFlags == 0) {
            throw new IllegalStateException(
                    "All policy flags must specify 1 APPLIED_BY flag. Policy " + policyName
                            + " did not.");
        }
    }

    private static boolean hasFlag(int[] values, int matchingFlag) {
        return hasFlag(values, matchingFlag, /* nonMatchingFlag= */ NO);
    }

    private static boolean hasFlag(int[] values, int matchingFlag, int nonMatchingFlag) {
        for (int value : values) {
            if (hasFlag(value, matchingFlag, nonMatchingFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlag(int value, int matchingFlag, int nonMatchingFlag) {
        if (!((value & matchingFlag) == matchingFlag)) {
            return false;
        }

        if (nonMatchingFlag != NO) {
            return (value & nonMatchingFlag) != nonMatchingFlag;
        }

        return true;
    }

    /**
     * Remove entries from {@code annotations} which are shadowed by another entry
     * in {@code annotations} (directly or indirectly).
     */
    private static void removeShadowedAnnotations(Set<Annotation> annotations) {
        Set<Class<? extends Annotation>> shadowedAnnotations = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof DynamicParameterizedAnnotation) {
                continue; // Doesn't shadow anything
            }

            ParameterizedAnnotation parameterizedAnnotation =
                    annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);

            if (parameterizedAnnotation == null) {
                continue; // Not parameterized
            }

            for (Class<? extends Annotation> shadowedAnnotationClass
                    : parameterizedAnnotation.shadows()) {
                addShadowed(shadowedAnnotations, shadowedAnnotationClass);
            }
        }
        annotations.removeIf(a -> shadowedAnnotations.contains(a.annotationType()));
    }

    private static void addShadowed(Set<Class<? extends Annotation>> shadowedAnnotations,
            Class<? extends Annotation> annotationClass) {
        shadowedAnnotations.add(annotationClass);
        ParameterizedAnnotation parameterizedAnnotation =
                annotationClass.getAnnotation(ParameterizedAnnotation.class);

        if (parameterizedAnnotation == null) {
            return;
        }

        for (Class<? extends Annotation> shadowedAnnotationClass
                : parameterizedAnnotation.shadows()) {
            addShadowed(shadowedAnnotations, shadowedAnnotationClass);
        }
    }

    // This maps classes to classes which shadow them - we just need to ensure it contains all
    // annotation classes we encounter
    private static Map<Class<? extends Annotation>, Set<Class<? extends Annotation>>>
            sReverseShadowMap = new HashMap<>();

    /**
     * Remove entries from {@code annotations} which are shadowing another entry
     * in {@code annotatipns} (directly or indirectly).
     */
    private static void removeShadowingAnnotations(Set<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            recordInReverseShadowMap(annotation);
        }

        Set<Class<? extends Annotation>> shadowingAnnotations = new HashSet<>();

        for (Annotation annotation : annotations) {
            shadowingAnnotations.addAll(
                    sReverseShadowMap.getOrDefault(annotation.annotationType(), new HashSet<>()));
        }

        annotations.removeIf(a -> shadowingAnnotations.contains(a.annotationType()));
    }

    private static void recordInReverseShadowMap(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return; // Not shadowed by anything
        }

        ParameterizedAnnotation parameterizedAnnotation =
                annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);

        if (parameterizedAnnotation == null) {
            return; // Not parameterized
        }

        if (parameterizedAnnotation.shadows().length == 0) {
            return; // Doesn't shadow anything
        }

        recordShadowedInReverseShadowMap(annotation.annotationType(), parameterizedAnnotation);
    }

    private static void recordShadowedInReverseShadowMap(Class<? extends Annotation> annotation,
            ParameterizedAnnotation parameterizedAnnotation) {
        for (Class<? extends Annotation> shadowedAnnotation : parameterizedAnnotation.shadows()) {
            ParameterizedAnnotation shadowedParameterizedAnnotation =
                    shadowedAnnotation.getAnnotation(ParameterizedAnnotation.class);

            if (shadowedParameterizedAnnotation == null) {
                continue; // Not parameterized
            }

            if (!sReverseShadowMap.containsKey(shadowedAnnotation)) {
                sReverseShadowMap.put(shadowedAnnotation, new HashSet<>());
            }

            sReverseShadowMap.get(shadowedAnnotation).add(annotation);

            recordShadowedInReverseShadowMap(annotation, shadowedParameterizedAnnotation);
        }
    }

    /**
     * Generate and return the annotations for parameterizing permission tests based on the passed
     * {@code permission}.
     */
    private static Annotation generateParameterizedPermissionAnnotation(Permission permission) {
        // TODO(b/219750042): Currently we only test that permissions apply to the current user
        Annotation[] replacementPermissionAnnotations =
                new Annotation[] {
                    ensureTestAppInstalledAsPrimaryDPC(
                            DELEGATE_KEY,
                            queryBuilder()
                                    .wherePackageName()
                                    .isEqualTo(DELEGATE_PACKAGE_NAME)
                                    .toAnnotation(),
                            INSTRUMENTED_USER),
                    ensureTestAppHasPermission(
                            DELEGATE_KEY, permission.appliedWith(), FailureMode.SKIP),
                    requireRootInstrumentation("Use of device policy permission", FailureMode.SKIP)
                };
        return new DynamicParameterizedAnnotation(
                formatPermissionsForTestName(Arrays.asList(permission.appliedWith())),
                replacementPermissionAnnotations);
    }

    private static String formatPermissionsForTestName(List<String> permissions) {
        return permissions.stream()
                .map(
                        permission -> {
                            final String permissionNamePrefix = "Permission_";

                            if (permission.startsWith("android.permission.")) {
                                return permissionNamePrefix + permission.substring(19);
                            }
                            return permissionNamePrefix + permission;
                        })
                .collect(Collectors.joining(","));
    }
}
