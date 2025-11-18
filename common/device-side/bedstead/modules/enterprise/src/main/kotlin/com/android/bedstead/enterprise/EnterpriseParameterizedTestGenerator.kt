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
package com.android.bedstead.enterprise

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest
import com.android.bedstead.enterprise.annotations.UsesEnterprisePolicies
import com.android.bedstead.harrier.ParameterizedTestGenerator
import com.google.common.collect.ImmutableSet
import kotlin.reflect.KClass

/** [ParameterizedTestGenerator] for Enterprise */
@Suppress("unused")
class EnterpriseParameterizedTestGenerator : ParameterizedTestGenerator {

    override fun generateReplacementAnnotations(
        annotation: Annotation,
        classAnnotations: List<Annotation>,
    ): List<Annotation> =
        when (annotation) {
            is PolicyAppliesTest -> Policy.policyAppliesStates(unionPolicies(annotation.policy))
            is PolicyDoesNotApplyTest ->
                Policy.policyDoesNotApplyStates(unionPolicies(annotation.policy))

            is CannotSetPolicyTest ->
                Policy.cannotSetPolicyStates(
                    unionPolicies(annotation.policy),
                    annotation.includeDeviceAdminStates,
                    annotation.includeNonDeviceAdminStates,
                )

            is CanSetPolicyTest -> annotation.logic(classAnnotations)
            else -> emptyList()
        }

    private fun CanSetPolicyTest.logic(classAnnotations: List<Annotation>): List<Annotation> {
        validate()

        val enterprisePolicy =
            if (policy.isNotEmpty()) {
                unionPolicies(policy)
            } else if (policyUnion.isNotEmpty()) {
                unionPolicies(policyUnion)
            } else if (policyIntersection.isNotEmpty()) {
                intersectPolicies(policyIntersection.map { it.java }.toTypedArray())
            } else {
                check(scope != 0) { "validate() should have rejected this annotation" }
                getPolicyWithScope(classAnnotations, scope)
            }

        return Policy.canSetPolicyStates(enterprisePolicy, singleTestOnly)
    }

    private fun CanSetPolicyTest.validate() {
        var numberOfUniquePolicySet = 0
        if (policy.isNotEmpty()) numberOfUniquePolicySet++
        if (policyUnion.isNotEmpty()) numberOfUniquePolicySet++
        if (policyIntersection.isNotEmpty()) numberOfUniquePolicySet++
        if (scope != 0) numberOfUniquePolicySet++
        check(numberOfUniquePolicySet == 1) {
            "Exactly 1 of policy/policyUnion/policyIntersection/scope must be set"
        }
    }

    private fun unionPolicies(policies: Array<KClass<*>>): EnterprisePolicy {
        return unionPolicies(policies.map { it.java }.toTypedArray())
    }

    /**
     * Create a new [EnterprisePolicy] by merging a group of policies.
     *
     * Example usage:
     *
     * Policy 1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER |
     * APPLIES_TO_OWN_USER
     *
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     *
     * EnterprisePolicy.dpc(): APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER
     * | APPLIES_TO_OWN_USER
     *
     * Each policy will have flags validated.
     *
     * If policies support different delegation scopes, then they cannot be merged and an exception
     * will be thrown. These policies require separate tests.
     */
    private fun unionPolicies(policies: Array<Class<*>>): EnterprisePolicy {
        check(policies.isNotEmpty()) { "Cannot union 0 policies" }
        if (policies.size == 1) {
            // Nothing to merge, just return the only one
            return policies[0].getAnnotation(EnterprisePolicy::class.java)!!
        }

        val dpc: MutableSet<Int> = mutableSetOf()
        val permissions: MutableSet<EnterprisePolicy.Permission> = mutableSetOf()
        val appOps: MutableSet<EnterprisePolicy.AppOp> = mutableSetOf()
        var delegatedScopes: Set<String?> = mutableSetOf()

        for (policy in policies) {
            val enterprisePolicy = policy.getAnnotation(EnterprisePolicy::class.java)!!
            Policy.validateFlags(policy.name, enterprisePolicy.dpc)

            dpc.addAll(enterprisePolicy.dpc.toTypedArray())
            permissions.addAll(enterprisePolicy.permissions)
            appOps.addAll(enterprisePolicy.appOps)

            if (enterprisePolicy.delegatedScopes.isNotEmpty()) {
                val newDelegatedScopes = ImmutableSet.copyOf(enterprisePolicy.delegatedScopes)
                check(
                    delegatedScopes.isEmpty() || delegatedScopes.containsAll(newDelegatedScopes)
                ) {
                    "Cannot merge multiple policies which define different delegated scopes. " +
                        "You should separate this into multiple tests."
                }

                delegatedScopes = newDelegatedScopes
            }
        }

        return Policy.enterprisePolicy(
            dpc.toIntArray(),
            permissions.toTypedArray(),
            appOps.toTypedArray(),
            delegatedScopes.toTypedArray(),
        )
    }

    /**
     * Create a new [EnterprisePolicy] with DPC that fulfills all the requirements of all the
     * provided policies.
     *
     * Example usage:
     *
     * Policy 1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER |
     * APPLIES_TO_OWN_USER
     *
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     *
     * EnterprisePolicy.dpc(): APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     *
     * Each policy will have flags validated.
     */
    private fun intersectPolicies(policies: Array<Class<*>>): EnterprisePolicy {
        check(policies.isNotEmpty()) { "Cannot intersect 0 policies" }
        if (policies.size == 1) {
            // Nothing to intersect, just return the only one
            return policies[0].getAnnotation(EnterprisePolicy::class.java)!!
        }

        val permissions: MutableSet<EnterprisePolicy.Permission> = HashSet()
        val appOps: MutableSet<EnterprisePolicy.AppOp> = HashSet()
        var delegatedScopes: Set<String?> = HashSet()

        var intersectDpc = 0.inv()

        for (policy in policies) {
            val enterprisePolicy = policy.getAnnotation(EnterprisePolicy::class.java)!!
            Policy.validateFlags(policy.name, enterprisePolicy.dpc)

            for (dpcPolicy in enterprisePolicy.dpc) {
                intersectDpc = intersectDpc and dpcPolicy
            }

            // TODO: (b/331606832) support permissions intersection
            permissions.addAll(enterprisePolicy.permissions)

            // TODO: (b/341401594) support appOps intersection
            appOps.addAll(enterprisePolicy.appOps)

            if (enterprisePolicy.delegatedScopes.isNotEmpty()) {
                val newDelegatedScopes = ImmutableSet.copyOf(enterprisePolicy.delegatedScopes)
                check(
                    delegatedScopes.isEmpty() || delegatedScopes.containsAll(newDelegatedScopes)
                ) {
                    ("Cannot intersect multiple policies which define different delegated scopes." +
                        " You should separate this into multiple tests.")
                }

                delegatedScopes = newDelegatedScopes
            }
        }

        return Policy.enterprisePolicy(
            intArrayOf(intersectDpc),
            permissions.toTypedArray(),
            appOps.toTypedArray(),
            delegatedScopes.toTypedArray(),
        )
    }

    /**
     * Finds the [UsesEnterprisePolicies] annotation from the parent class's annotations, and
     * returns the [EnterprisePolicy] for the given [scope].
     */
    private fun getPolicyWithScope(
        classAnnotations: List<Annotation>,
        scope: Int,
    ): EnterprisePolicy {
        val policyUnderTest =
            classAnnotations.find { it is UsesEnterprisePolicies } as? UsesEnterprisePolicies?
        if (policyUnderTest == null) {
            throw IllegalStateException(
                "UsesEnterprisePolicies annotation must be present on the parent class if scope is used"
            )
        }
        val policyClass =
            when (scope) {
                POLICY_SCOPE_USER -> policyUnderTest.scopeUser
                POLICY_SCOPE_DEVICE -> policyUnderTest.scopeDevice
                POLICY_SCOPE_PARENT_USER -> policyUnderTest.scopeParentUser
                else ->
                    throw IllegalStateException(
                        "Invalid scope $scope. " +
                            "Must be one of POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER"
                    )
            }
        return policyClass.java.getAnnotation(EnterprisePolicy::class.java)!!
    }
}
