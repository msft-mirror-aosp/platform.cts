/*
 * Copyright 2024 The Android Open Source Project
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

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import com.android.bedstead.enterprise.Policy.includeRunOnAffiliatedProfileOwnerAdditionalUser
import com.android.bedstead.enterprise.Policy.includeRunOnOrganizationOwnedProfileOwner
import com.android.bedstead.enterprise.Policy.includeRunOnParentOfOrganizationOwnedProfileOwner
import com.android.bedstead.enterprise.Policy.includeRunOnProfileOwnerPrimaryUser
import com.android.bedstead.enterprise.Policy.includeRunOnProfileOwnerProfileWithNoDeviceOwner
import com.android.bedstead.enterprise.Policy.includeRunOnUnaffiliatedProfileOwnerAdditionalUser
import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_FINANCED_DEVICE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_SYSTEM_DEVICE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER
import com.android.bedstead.enterprise.annotations.canSetPolicyTest
import com.android.bedstead.enterprise.annotations.cannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnCloneProfileAlongsideOrganizationOwnedProfile
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnFinancedDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnAffiliatedDeviceOwnerSecondaryUser
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnFinancedDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnSingleDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnSystemDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.policyAppliesTest
import com.android.bedstead.enterprise.annotations.policyDoesNotApplyTest
import com.android.bedstead.enterprise.annotations.usesEnterprisePolicies
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnUserController
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnAdditionalUserWithDeviceController
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnAdditionalUserWithInitialUserController
import com.android.bedstead.enterprise.annotations.parameterized.includeRunOnSystemUserWithDeviceController
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.annotations.parameterized.includeNone
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@EnterprisePolicy(dpc = [APPLIED_BY_SYSTEM_DEVICE_OWNER or APPLIES_TO_OWN_USER])
class AppliedBySystemDeviceOwner

@EnterprisePolicy(dpc = [APPLIED_BY_FINANCED_DEVICE_OWNER or APPLIES_TO_OWN_USER])
class AppliedByFinancedDeviceOwner

@EnterprisePolicy(
    dpc = [APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE or APPLIES_TO_OWN_USER]
)
class AppliedByOrganizationOwnedProfileOwnerProfile

@RunWith(JUnit4::class)
class BedsteadJUnit4AnnotationTest {

    @Test
    fun canSetPolicyTest_policy_returnsUnionParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf()
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun canSetPolicyTest_policyUnion_returnsUnionParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyUnion = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun canSetPolicyTest_policyIntersection_returnsIntersectParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByAffiliatedProfileOwnerProfileOrSystemDeviceOwnerOrAffiliatedProfileOwnerUserAppliesToParentPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser())

    }

    @Test
    fun canSetPolicyTest_policyIntersection_singlePolicy_returnsIntersectParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun canSetPolicyTest_missingUsesEnterprisePoliciesAnnotationOnParentClass_throws() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(canSetPolicyTest(scope = POLICY_SCOPE_USER)),
                    /* classAnnotations= */ listOf(),
                )
            }

        assertThat(error)
            .hasMessageThat()
            .contains(
                "UsesEnterprisePolicies annotation must be present on the parent class if scope is used"
            )
    }

    @Test
    fun canSetPolicyTest_scopeUser_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(canSetPolicyTest(scope = POLICY_SCOPE_USER)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )
        assertThat(parameterizedAnnotations).containsExactly(includeRunOnSystemDeviceOwnerUser())
    }

    @Test
    fun canSetPolicyTest_scopeDevice_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(canSetPolicyTest(scope = POLICY_SCOPE_DEVICE)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )
        assertThat(parameterizedAnnotations).containsExactly(includeRunOnFinancedDeviceOwnerUser())
    }

    @Test
    fun canSetPolicyTest_scopeParentUser_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(canSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )
        assertThat(parameterizedAnnotations)
            .containsExactly(includeRunOnOrganizationOwnedProfileOwner())
    }

    @Test
    fun canSetPolicyTest_invalidScope_throws() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(canSetPolicyTest(scope = 123)),
                    /* classAnnotations= */ listOf(
                        usesEnterprisePolicies(
                            scopeUser = AppliedBySystemDeviceOwner::class.java,
                            scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                            scopeParentUser =
                                AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                        )
                    ),
                )
            }

        assertThat(error).hasMessageThat().contains("Invalid scope 123")
    }

    @Test
    fun canSetPolicyTest_policyIntersection_noIntersectPolicy_returnsIncludeNone() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByAffiliatedProfileOwnerAppliesToParentPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(includeNone())
    }

    @Test
    fun canSetPolicyTest_noPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(canSetPolicyTest()),
                /* classAnnotations= */ listOf(),
            )
        }
    }

    @Test
    fun canSetPolicyTest_multiplePolicy_throws() {
        val policyIntersectPolicies = arrayOf(
            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
        )

        val policyUnionPolicies =
            arrayOf(
                AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
            )

        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = policyIntersectPolicies,
                        policyUnion = policyUnionPolicies
                    )
                ),
                /* classAnnotations= */ listOf(),
            )
        }
    }

    @Test
    fun canSetPolicyTest_scopeAndPolicy_throws() {
        val policy = AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java

        val exception =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(canSetPolicyTest(policy = arrayOf(policy), scope = POLICY_SCOPE_USER)),
                    /* classAnnotations= */ listOf(),
                )
            }

        assertThat(exception)
            .hasMessageThat()
            .contains("Exactly 1 of policy/policyUnion/policyIntersection/scope must be set")
    }

    @Test
    fun policyAppliesTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyAppliesTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnUnaffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun policyAppliesTest_hasNoPolicy_returnsNoAnnotations() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(policyAppliesTest(policy = arrayOf())),
                /* classAnnotations= */ listOf(),
            )
        }
    }

    @Test
    fun policyDoesNotApplyTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyDoesNotApplyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnAffiliatedDeviceOwnerSecondaryUser(),
            includeRunOnCloneProfileAlongsideOrganizationOwnedProfile(),
            includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile(),
            includeRunOnParentOfOrganizationOwnedProfileOwner()
        )
    }

    @Test
    fun policyDoesNotApplyTest_hasNoPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(policyDoesNotApplyTest(policy = arrayOf())),
                /* classAnnotations= */ listOf(),
            )
        }
    }

    @Test
    fun cannotSetPolicyTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    cannotSetPolicyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations.size).isEqualTo(3)

        val expectedAnnotationTypes = arrayOf(
            IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance::class.java,
            IncludeRunOnFinancedDeviceOwnerUser::class.java,
            DynamicParameterizedAnnotation::class.java
        )

        for (type in expectedAnnotationTypes) {
            val containsType = parameterizedAnnotations.stream().anyMatch { type.isInstance(it) }
            assertThat(containsType).isTrue()
        }
    }

    @Test
    fun cannotSetPolicyTest_hasNoPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(cannotSetPolicyTest(policy = arrayOf())),
                /* classAnnotations= */ listOf(),
            )
        }
    }

    @Test
    fun cannotSetPolicyTest_missingUsesEnterprisePoliciesAnnotationOnParentClass_throws() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(cannotSetPolicyTest(scope = POLICY_SCOPE_USER)),
                    /* classAnnotations= */ listOf(),
                )
            }

        assertThat(error)
            .hasMessageThat()
            .contains(
                "UsesEnterprisePolicies annotation must be present on the parent class if scope is used"
            )
    }

    @Test
    fun cannotSetPolicyTest_scopeUser_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(cannotSetPolicyTest(scope = POLICY_SCOPE_USER)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )

        // It would be unmaintainable to check for all possible test cases here, so we check that
        // there are some annotations and that the one of the scope is not there.
        assertThat(parameterizedAnnotations).isNotEmpty()
        assertThat(parameterizedAnnotations).doesNotContain(includeRunOnSystemDeviceOwnerUser())
    }

    @Test
    fun cannotSetPolicyTest_scopeDevice_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(cannotSetPolicyTest(scope = POLICY_SCOPE_DEVICE)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )

        // It would be unmaintainable to check for all possible test cases here, so we check that
        // there are some annotations and that the one of the scope is not there.
        assertThat(parameterizedAnnotations).isNotEmpty()
        assertThat(parameterizedAnnotations).doesNotContain(includeRunOnFinancedDeviceOwnerUser())
    }

    @Test
    fun cannotSetPolicyTest_scopeParentUser_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(cannotSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)),
                /* classAnnotations= */ listOf(
                    usesEnterprisePolicies(
                        scopeUser = AppliedBySystemDeviceOwner::class.java,
                        scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                        scopeParentUser = AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                    )
                ),
            )

        // It would be unmaintainable to check for all possible test cases here, so we check that
        // there are some annotations and that the one of the scope is not there.
        assertThat(parameterizedAnnotations).isNotEmpty()
        assertThat(parameterizedAnnotations)
            .doesNotContain(includeRunOnOrganizationOwnedProfileOwner())
    }

    @Test
    fun cannotSetPolicyTest_invalidScope_throws() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(cannotSetPolicyTest(scope = 123)),
                    /* classAnnotations= */ listOf(
                        usesEnterprisePolicies(
                            scopeUser = AppliedBySystemDeviceOwner::class.java,
                            scopeDevice = AppliedByFinancedDeviceOwner::class.java,
                            scopeParentUser =
                                AppliedByOrganizationOwnedProfileOwnerProfile::class.java,
                        )
                    ),
                )
            }

        assertThat(error).hasMessageThat().contains("Invalid scope 123")
    }

    @Test
    fun cannotSetPolicyTest_scopeAndPolicy_throws() {
        val policy = AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java

        val exception =
            assertThrows(IllegalStateException::class.java) {
                BedsteadJUnit4.getParameterizedAnnotations(
                    arrayOf(
                        cannotSetPolicyTest(policy = arrayOf(policy), scope = POLICY_SCOPE_USER)
                    ),
                    /* classAnnotations= */ listOf(),
                )
            }

        assertThat(exception).hasMessageThat().contains("Exactly 1 of policy/scope must be set")
    }

    @Test
    fun policyAppliesTest_userControllerAppliesToOwnUser_userControllerAppliesToOwnUser() {
        assertThat(
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyAppliesTest(
                        policy = arrayOf(
                            AppliedByUserControllerToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )
        ).containsExactly(
            includeRunOnUserController(),
        )
    }

    @Test
    fun policyDoesNotApplyTest_userControllerAppliesToOwnUser_returnsUserControllerRunOnAdditionalUser() {
        assertThat(
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyDoesNotApplyTest(
                        policy = arrayOf(
                            AppliedByUserControllerToOwnUserPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )
        ).containsExactly(
            includeRunOnAdditionalUserWithInitialUserController(),
        )
    }

    @Test
    fun cannotSetPolicyTest_noUserControllerInDpc_doesNotReturnUserControllerState() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    cannotSetPolicyTest(
                        policy = arrayOf(
                            AppliedByAffiliatedProfileOwnerAppliesToParentPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).doesNotContain(
            includeRunOnUserController(),
        )
        assertThat(parameterizedAnnotations).doesNotContain(
            includeRunOnAdditionalUserWithInitialUserController(),
        )
    }

    @Test
    fun policyAppliesTest_deviceControllerAppliesToSystemUser_returnsIncludeRunOnSystemUserWithDeviceController() {
        assertThat(
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyAppliesTest(
                        policy = arrayOf(
                            AppliedByDeviceControllerToAppliesSystemUser::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )
        ).containsExactly(
            includeRunOnSystemUserWithDeviceController(),
        )
    }

    @Test
    fun policyDoesNotApplyTest_deviceControllerAppliesToSystemUser_returnsIncludeRunOnAdditionalUserWithDeviceController() {
        assertThat(
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyDoesNotApplyTest(
                        policy = arrayOf(
                            AppliedByDeviceControllerToAppliesSystemUser::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )
        ).containsExactly(
            includeRunOnAdditionalUserWithDeviceController(),
        )
    }

    @Test
    fun cannotSetPolicyTest_noDeviceControllerInDpc_doesNotReturnDeviceControllerState() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    cannotSetPolicyTest(
                        policy = arrayOf(
                            AppliedByAffiliatedProfileOwnerAppliesToParentPolicy::class.java
                        )
                    )
                ),
                /* classAnnotations= */ listOf(),
            )

        assertThat(parameterizedAnnotations).doesNotContain(
            includeRunOnSystemUserWithDeviceController(),
        )
        assertThat(parameterizedAnnotations).doesNotContain(
            includeRunOnAdditionalUserWithDeviceController(),
        )
    }
}
