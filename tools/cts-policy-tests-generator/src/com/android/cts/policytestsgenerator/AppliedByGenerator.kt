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

package com.android.cts.policytestsgenerator

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadata.DpcType
import android.processor.devicepolicy.protos.PolicyMetadata.PolicyScope

// Maps allowedDpcTypes to the list of `APPLIED_BY_...` strings used in `EnterprisePolicy`.
object AppliedByGenerator {

    fun generateForUserScope(metadata: PolicyMetadata): List<String> {
        if (!metadata.allowedScopesList.contains(PolicyScope.POLICY_SCOPE_USER)) {
            return listOf()
        }

        return toAppliedByStrings(metadata.allowedDpcTypesList)
    }

    fun generateForDeviceScope(metadata: PolicyMetadata): List<String> {
        if (!metadata.allowedScopesList.contains(PolicyScope.POLICY_SCOPE_DEVICE)) {
            return listOf()
        }

        return toAppliedByStrings(getAllowedDpcTypesWithRequiredCrossUserPermission(metadata))
    }

    fun generateForParentUserScope(metadata: PolicyMetadata): List<String> {
        if (!metadata.allowedScopesList.contains(PolicyScope.POLICY_SCOPE_PARENT_USER)) {
            return listOf()
        }

        return toAppliedByStrings(getAllowedDpcTypesWithRequiredCrossUserPermission(metadata))
    }

    // Returns the DPC types in `metadata.allowedDpcTypesList` that have the
    // `metadata.requiredCrossUserPermission` permission.
    private fun getAllowedDpcTypesWithRequiredCrossUserPermission(
        metadata: PolicyMetadata
    ): Collection<DpcType> {
        return metadata.allowedDpcTypesList.intersect(
            getDpcTypesWithCrossUserPermission(metadata.requiredCrossUserPermission)
        )
    }

    private fun toAppliedByStrings(dpcTypes: Collection<DpcType>): List<String> =
        dpcTypes
            .mapNotNull { getAppliedByStringsForDpcType(it) }
            .flatten()
            .toSet() // `toSet()` removes duplicates.
            .sorted()

    // Which DPCs have which cross-user permission granted.
    // Scraped from
    // frameworks/base/services/devicepolicy/java/com/android/server/devicepolicy/PermissionChecker.java
    private fun getDpcTypesWithCrossUserPermission(crossUserPermission: String) =
        when (crossUserPermission) {
            "" -> DpcType.values().toList()
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS" ->
                listOf(
                    DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER,
                    DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                    DpcType.DPC_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL" ->
                listOf(
                    DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER,
                    DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL" ->
                listOf(
                    DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER,
                    DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                    DpcType.DPC_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                    DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER0,
                    DpcType.DPC_TYPE_PROFILE_OWNER,
                    DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER,
                    DpcType.DPC_TYPE_AFFILIATED_PROFILE_OWNER_ON_USER,
                )
            else ->
                throw IllegalArgumentException(
                    "Unknown cross-user permission: $crossUserPermission"
                )
        }

    private fun getAppliedByStringsForDpcType(dpcType: DpcType) =
        when (dpcType) {
            DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER -> listOf("APPLIED_BY_DEVICE_OWNER")
            DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER -> listOf("APPLIED_BY_FINANCED_DEVICE_OWNER")
            DpcType.DPC_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE ->
                listOf("APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE")
            DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER0 ->
                listOf("APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO")
            DpcType.DPC_TYPE_PROFILE_OWNER ->
                listOf("APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE")
            DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER ->
                listOf("APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER")
            DpcType.DPC_TYPE_AFFILIATED_PROFILE_OWNER_ON_USER ->
                listOf("APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER")
            else -> throw IllegalArgumentException("Unknown DpcType: $dpcType")
        }
}
