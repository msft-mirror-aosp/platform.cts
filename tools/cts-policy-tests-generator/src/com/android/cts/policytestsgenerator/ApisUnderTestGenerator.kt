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

import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.TypeMetadataCase

// Returns the APIs under test.
// These APIs will be listed in an `@ApiTest` annotation.
object ApisUnderTestGenerator {

    fun generateApiList(metadata: PolicyMetadata): List<String> {
        return sharedApis(metadata) + typeSpecificApis(metadata)
    }

    private fun sharedApis(metadata: PolicyMetadata) = sharedApis(metadata.identifier)

    private fun sharedApis(identifier: FullyQualifiedFieldName) =
        listOf("${identifier.packageName}.${identifier.className}#${identifier.fieldName}")

    private fun typeSpecificApis(metadata: PolicyMetadata) =
        when (metadata.typeSpecificMetadata.typeMetadataCase) {
            TypeMetadataCase.ENUM_METADATA ->
                generateEnumList(metadata.typeSpecificMetadata.enumMetadata)
            else -> listOf()
        }

    private fun generateEnumList(metadata: EnumPolicyMetadata) =
        metadata.valuesList.map { it.fieldName.formatApi() }

    private fun FullyQualifiedFieldName.formatApi() = "${packageName}.${className}#${fieldName}"
}
