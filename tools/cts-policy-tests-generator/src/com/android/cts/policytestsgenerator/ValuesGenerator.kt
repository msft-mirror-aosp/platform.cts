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
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.BooleanPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.IntegerPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.LongPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata.ListElementMetadataCase
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.StringPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.TypeMetadataCase

// Generates valid and invalid values for a given policy.
object ValuesGenerator {
    fun generateValidValues(metadata: PolicyMetadata): List<String> {
        val type = metadata.typeSpecificMetadata
        when (type.typeMetadataCase) {
            TypeMetadataCase.STRING_METADATA -> return generateValidStrings(type.stringMetadata)
            TypeMetadataCase.INTEGER_METADATA -> return generateValidIntegers(type.integerMetadata)
            TypeMetadataCase.LONG_METADATA -> return generateValidLongs(type.longMetadata)
            TypeMetadataCase.BOOLEAN_METADATA -> return generateValidBooleans(type.booleanMetadata)
            TypeMetadataCase.ENUM_METADATA -> return generateValidEnums(type.enumMetadata)
            TypeMetadataCase.LIST_METADATA -> return generateValidLists(type.listMetadata)
            else -> throw IllegalArgumentException("Unsupported type ${type.typeMetadataCase}")
        }
    }

    fun generateInvalidValues(metadata: PolicyMetadata): List<InvalidValueTestCase> {
        val type = metadata.typeSpecificMetadata
        when (type.typeMetadataCase) {
            TypeMetadataCase.STRING_METADATA -> return generateInvalidStrings(type.stringMetadata)
            TypeMetadataCase.INTEGER_METADATA ->
                return generateInvalidIntegers(type.integerMetadata)
            TypeMetadataCase.LONG_METADATA ->
                return generateInvalidLongs(type.longMetadata)
            TypeMetadataCase.BOOLEAN_METADATA ->
                return generateInvalidBooleans(type.booleanMetadata)
            TypeMetadataCase.ENUM_METADATA -> return generateInvalidEnums(type.enumMetadata)
            TypeMetadataCase.LIST_METADATA -> return generateInvalidLists(type.listMetadata)
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    private fun generateValidStrings(metadata: StringPolicyMetadata): List<String> {
        if (metadata.emptyStringAllowed) {
            return listOf("", "a-value").quoteAll()
        }
        return listOf("a-value").quoteAll()
    }

    private fun generateInvalidStrings(metadata: StringPolicyMetadata): List<InvalidValueTestCase> {
        if (!metadata.emptyStringAllowed) {
            return listOf(InvalidValueTestCase("".quote(), "Empty string is not allowed"))
        }

        return listOf()
    }

    private fun generateValidBooleans(metadata: BooleanPolicyMetadata): List<String> {
        return listOf("true", "false")
    }

    private fun generateInvalidBooleans(
        metadata: BooleanPolicyMetadata
    ): List<InvalidValueTestCase> {
        return listOf()
    }

    private fun generateValidIntegers(metadata: IntegerPolicyMetadata): List<String> {
        return listOf("-1", "0", "1", "12345")
    }

    private fun generateInvalidIntegers(
        metadata: IntegerPolicyMetadata
    ): List<InvalidValueTestCase> {
        return listOf()
    }

    private fun generateValidLongs(metadata: LongPolicyMetadata): List<String> {
        return listOf("-1L", "0L", "1L", "12345L")
    }

    private fun generateInvalidLongs(
        metadata: LongPolicyMetadata
    ): List<InvalidValueTestCase> {
        return listOf()
    }

    private fun generateValidEnums(metadata: EnumPolicyMetadata): List<String> {
        return metadata.valuesList.map {
            // The test file already imports `PolicyIdentifier`, so we can
            // safely strip the `android.app.admin.` prefix if the enum values
            // are defined in `PolicyIdentifier`.
            it.fieldName
                .format()
                .replacePrefixes("android.app.admin.PolicyIdentifier" to "PolicyIdentifier")
        }
    }

    private fun generateInvalidEnums(metadata: EnumPolicyMetadata): List<InvalidValueTestCase> {
        val lowest = metadata.valuesList.minBy { it.intValue }.intValue
        val highest = metadata.valuesList.maxBy { it.intValue }.intValue
        return listOf(
            InvalidValueTestCase((lowest - 1).toString(), description = "Lower than lowest value"),
            InvalidValueTestCase(
                (highest + 1).toString(),
                description = "Higher than highest value",
            ),
        )
    }

    private fun generateValidLists(metadata: ListPolicyMetadata): List<String> {
        val validElementValues = generateValidListElementValues(metadata)
        if (metadata.emptyListAllowed) {
            return listOf("[]", "[${validElementValues .joinToString(", ")}]")
        }
        return listOf("[${validElementValues .joinToString(", ")}]")
    }

    private fun generateInvalidLists(metadata: ListPolicyMetadata): List<InvalidValueTestCase> {
        val cases = mutableListOf<InvalidValueTestCase>()
        cases.addAll(
            generateInvalidListElementValues(metadata).map {
                it.copy(value = "[${it.value}]")
            }
        )

        if (!metadata.emptyListAllowed) {
            cases.add(InvalidValueTestCase("[]", "Empty list is not allowed"))
        }
        return cases
    }

    fun generateInvalidListElementValues(metadata: ListPolicyMetadata): List<InvalidValueTestCase> {
        when (metadata.listElementMetadataCase) {
            ListElementMetadataCase.STRING_METADATA ->
                return generateInvalidStrings(metadata.stringMetadata)
            ListElementMetadataCase.INTEGER_METADATA ->
                return generateInvalidIntegers(metadata.integerMetadata)
            ListElementMetadataCase.ENUM_METADATA ->
                return generateInvalidEnums(metadata.enumMetadata)
            else ->
                throw IllegalArgumentException(
                    "Unsupported list type ${metadata.listElementMetadataCase}"
                )
        }
    }

    fun generateValidListElementValues(metadata: ListPolicyMetadata): List<String> {
        when (metadata.listElementMetadataCase) {
            ListElementMetadataCase.STRING_METADATA ->
                return generateValidStrings(metadata.stringMetadata)
            ListElementMetadataCase.INTEGER_METADATA ->
                return generateValidIntegers(metadata.integerMetadata)
            ListElementMetadataCase.ENUM_METADATA ->
                return generateValidEnums(metadata.enumMetadata)
            else ->
                throw IllegalArgumentException(
                    "Unsupported list type ${metadata.listElementMetadataCase}"
                )
        }
    }
}
