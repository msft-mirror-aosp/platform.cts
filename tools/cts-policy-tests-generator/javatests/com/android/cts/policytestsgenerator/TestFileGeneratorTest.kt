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

import android.processor.devicepolicy.protos.FullyQualifiedClassName
import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadata.DpcType
import android.processor.devicepolicy.protos.PolicyMetadata.PolicyScope
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.BooleanPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.LongPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.StringPolicyMetadata
import com.google.common.truth.Truth.assertThat
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class TestFileGeneratorTest {

    val anyCrossUserPermission =
        "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL"

    val integerType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("Integer")
            .setPackageName("java.lang")
            .build()

    val stringType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("String")
            .setPackageName("java.lang")
            .build()

    val booleanType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("Boolean")
            .setPackageName("java.lang")
            .build()

    val longType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("Long")
            .setPackageName("java.lang")
            .build()

    val stringListType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("List<java.lang.String>")
            .setPackageName("java.util")
            .build()

    val integerListType =
        FullyQualifiedClassName.newBuilder()
            .setClassName("List<java.lang.Integer>")
            .setPackageName("java.util")
            .build()

    val allDpcTypes =
        DpcType.values().filter { it != DpcType.DPC_TYPE_UNSPECIFIED && it != DpcType.UNRECOGNIZED }

    @Test
    fun integerPolicy_generatesTestClass() {
        val metadata = integerPolicyMetadata("MY_POLICY_NAME")

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyPolicyNameTest : CommonPolicyTests<Int>() {

                    override val policyIdentifier = PolicyIdentifier.MY_POLICY_NAME

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            -1,
                            0,
                            1,
                            12345,
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases = listOf()
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun longPolicy_generatesTestClass() {
        val metadata = longPolicyMetadata("MY_LONG_POLICY")

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyLongPolicyTest : CommonPolicyTests<Long>() {

                    override val policyIdentifier = PolicyIdentifier.MY_LONG_POLICY

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            -1L,
                            0L,
                            1L,
                            12345L,
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases = listOf()
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeUser_notAllowed_generatesEmptyEnterprisePolicy() {
        val metadata =
            policyMetadata(
                "MY_POLICY_NAME",
                allowedScopes =
                    listOf(PolicyScope.POLICY_SCOPE_DEVICE, PolicyScope.POLICY_SCOPE_PARENT_USER),
                allowedDpcTypes = listOf(DpcType.DPC_TYPE_DEVICE_OWNER),
                permission = "thePermission",
                crossUserPermission = anyCrossUserPermission,
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_USER))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc = [],
                    permissions = []
                )
                public final class MyPolicyNamePolicy_ScopeUser {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeParentUser_notAllowed_generatesEmptyEnterprisePolicy() {
        val metadata =
            policyMetadata(
                "MY_POLICY_NAME",
                allowedScopes =
                    listOf(PolicyScope.POLICY_SCOPE_DEVICE, PolicyScope.POLICY_SCOPE_USER),
                allowedDpcTypes = listOf(DpcType.DPC_TYPE_DEVICE_OWNER),
                permission = "thePermission",
                crossUserPermission = anyCrossUserPermission,
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_PARENT_USER))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc = [],
                    permissions = []
                )
                public final class MyPolicyNamePolicy_ScopeParentUser {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeDevice_notAllowed_generatesEmptyEnterprisePolicy() {
        val metadata =
            policyMetadata(
                "MY_POLICY_NAME",
                allowedScopes =
                    listOf(PolicyScope.POLICY_SCOPE_USER, PolicyScope.POLICY_SCOPE_PARENT_USER),
                allowedDpcTypes = listOf(DpcType.DPC_TYPE_DEVICE_OWNER),
                permission = "thePermission",
                crossUserPermission = anyCrossUserPermission,
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_DEVICE))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc = [],
                    permissions = []
                )
                public final class MyPolicyNamePolicy_ScopeDevice {}
                """
                    .trimIndent()
            )
    }

    @Test
    @Parameters(
        "DPC_TYPE_DEVICE_OWNER, APPLIED_BY_DEVICE_OWNER",
        "DPC_TYPE_FINANCED_DEVICE_OWNER, APPLIED_BY_FINANCED_DEVICE_OWNER",
        "DPC_TYPE_MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE, APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE",
        "DPC_TYPE_PROFILE_OWNER_ON_USER0, APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO",
        "DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE",
        "DPC_TYPE_UNAFFILIATED_FULL_USER_PROFILE_OWNER, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER",
        "DPC_TYPE_AFFILIATED_FULL_USER_PROFILE_OWNER, APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER",
    )
    fun scopeUser_allowedDpcTypes_generatesCorrespondingAppliedByStatement(
        dpcType: DpcType,
        applied_by: String,
    ) {
        val metadata =
            policyMetadata(
                "MY_POLICY_NAME",
                allowedScopes = listOf(PolicyScope.POLICY_SCOPE_USER),
                allowedDpcTypes = listOf(dpcType),
                permission = "thePermission",
                crossUserPermission = anyCrossUserPermission,
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_USER))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc =
                        [
                            ${applied_by} or APPLIES_TO_OWN_USER,
                        ],
                    permissions =
                        [
                            Permission(
                                appliedWith =
                                    [
                                        "thePermission",
                                    ],
                                appliesTo = APPLIES_TO_OWN_USER,
                            ),
                        ]
                )
                public final class MyPolicyNamePolicy_ScopeUser {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeDevice_allowedDpcTypes_manageDevicePolicyAcrossUsers_generatesFilteredDpcList() {
        val metadata =
            policyMetadata(
                "TESTING_DEVICE_SCOPE",
                allowedScopes = listOf(PolicyScope.POLICY_SCOPE_DEVICE),
                allowedDpcTypes = allDpcTypes,
                permission = "theNormalPermission",
                crossUserPermission = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_DEVICE))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc =
                        [
                            APPLIED_BY_DEVICE_OWNER or APPLIES_GLOBALLY,
                            APPLIED_BY_FINANCED_DEVICE_OWNER or APPLIES_GLOBALLY,
                            APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE or APPLIES_GLOBALLY,
                        ],
                    permissions =
                        [
                            Permission(
                                appliedWith =
                                    [
                                        "theNormalPermission",
                                        "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                                    ],
                                appliesTo = APPLIES_GLOBALLY,
                            ),
                        ]
                )
                public final class TestingDeviceScopePolicy_ScopeDevice {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeDevice_allowedDpcTypes_manageDevicePolicyAcrossUsersFull_generatesFilteredDpcList() {
        val metadata =
            policyMetadata(
                "TESTING_DEVICE_SCOPE",
                allowedScopes = listOf(PolicyScope.POLICY_SCOPE_DEVICE),
                allowedDpcTypes = allDpcTypes,
                permission = "theNormalPermission",
                crossUserPermission = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_DEVICE))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc =
                        [
                            APPLIED_BY_DEVICE_OWNER or APPLIES_GLOBALLY,
                            APPLIED_BY_FINANCED_DEVICE_OWNER or APPLIES_GLOBALLY,
                        ],
                    permissions =
                        [
                            Permission(
                                appliedWith =
                                    [
                                        "theNormalPermission",
                                        "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
                                    ],
                                appliesTo = APPLIES_GLOBALLY,
                            ),
                        ]
                )
                public final class TestingDeviceScopePolicy_ScopeDevice {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeDevice_allowedDpcTypes_manageDevicePolicyAcrossUsersSecurityCritical_generatesFilteredDpcList() {
        val metadata =
            policyMetadata(
                "TESTING_DEVICE_SCOPE",
                allowedScopes = listOf(PolicyScope.POLICY_SCOPE_DEVICE),
                allowedDpcTypes = allDpcTypes,
                permission = "theNormalPermission",
                crossUserPermission =
                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL",
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_DEVICE))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc =
                        [
                            APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER or APPLIES_GLOBALLY,
                            APPLIED_BY_DEVICE_OWNER or APPLIES_GLOBALLY,
                            APPLIED_BY_FINANCED_DEVICE_OWNER or APPLIES_GLOBALLY,
                            APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE or APPLIES_GLOBALLY,
                            APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO or APPLIES_GLOBALLY,
                            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE or APPLIES_GLOBALLY,
                            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER or APPLIES_GLOBALLY,
                        ],
                    permissions =
                        [
                            Permission(
                                appliedWith =
                                    [
                                        "theNormalPermission",
                                        "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL",
                                    ],
                                appliesTo = APPLIES_GLOBALLY,
                            ),
                        ]
                )
                public final class TestingDeviceScopePolicy_ScopeDevice {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun scopeParentUser_allowedDpcTypes_manageDevicePolicyAcrossUsersFull_generatesFilteredDpcList() {
        val metadata =
            policyMetadata(
                "TESTING_PARENT_USER_SCOPE",
                allowedScopes = listOf(PolicyScope.POLICY_SCOPE_PARENT_USER),
                allowedDpcTypes = allDpcTypes,
                permission = "theNormalPermission",
                crossUserPermission = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getEnterprisePolicy(PolicyScope.POLICY_SCOPE_PARENT_USER))
            .isEqualTo(
                """
                @EnterprisePolicy(
                    dpc =
                        [
                            APPLIED_BY_DEVICE_OWNER or APPLIES_TO_PARENT,
                            APPLIED_BY_FINANCED_DEVICE_OWNER or APPLIES_TO_PARENT,
                        ],
                    permissions =
                        [
                            Permission(
                                appliedWith =
                                    [
                                        "theNormalPermission",
                                        "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
                                    ],
                                appliesTo = APPLIES_TO_PARENT,
                            ),
                        ]
                )
                public final class TestingParentUserScopePolicy_ScopeParentUser {}
                """
                    .trimIndent()
            )
    }

    @Test
    fun stringPolicy_emptyStringAllowed_generatesTestClass() {
        val metadata =
            stringPolicyMetadata(
                "MY_STRING_POLICY_NAME",
                StringPolicyMetadata.newBuilder().setEmptyStringAllowed(true),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyStringPolicyNameTest : CommonPolicyTests<String>() {

                    override val policyIdentifier = PolicyIdentifier.MY_STRING_POLICY_NAME

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            "",
                            "a-value",
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases = listOf()
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun stringPolicy_emptyStringNotAllowed_generatesTestClass() {
        val metadata =
            stringPolicyMetadata(
                "THE_NAME",
                StringPolicyMetadata.newBuilder().setEmptyStringAllowed(false),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class TheNameTest : CommonPolicyTests<String>() {

                    override val policyIdentifier = PolicyIdentifier.THE_NAME

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            "a-value",
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases =
                        listOf(
                            InvalidValueTestCase("", expectedError="Empty string is not allowed"),
                        )
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun enumPolicy_generatesTestClass() {
        val metadata =
            enumPolicyMetadata(
                "AN_ENUM_POLICY",
                EnumPolicyMetadata.newBuilder()
                    .addValue(fullyQualifiedFieldName("ENUM_VALUE_1"), 1)
                    .addValue(fullyQualifiedFieldName("ENUM_VALUE_5"), 5)
                    .addValue(
                        fullyQualifiedFieldName(
                            "ENUM_VALUE_10",
                            className = "TheClass",
                            packageName = "com.package",
                        ),
                        10,
                    ),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class AnEnumPolicyTest : CommonPolicyTests<Int>() {

                    override val policyIdentifier = PolicyIdentifier.AN_ENUM_POLICY

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            PolicyIdentifier.ENUM_VALUE_1,
                            PolicyIdentifier.ENUM_VALUE_5,
                            com.package.TheClass.ENUM_VALUE_10,
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases =
                        listOf(
                            InvalidValueTestCase(0), // Lower than lowest value
                            InvalidValueTestCase(11), // Higher than highest value
                        )
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun booleanPolicy_generatesTestClass() {
        val metadata = booleanPolicyMetadata("MY_BOOLEAN")

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyBooleanTest : CommonPolicyTests<Boolean>() {

                    override val policyIdentifier = PolicyIdentifier.MY_BOOLEAN

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            true,
                            false,
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases = listOf()
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun listOfStringPolicy_emptyListAllowed_generatesTestClass() {
        val metadata =
            listPolicyMetadata(
                "MY_LIST_POLICY",
                stringListType,
                ListPolicyMetadata.newBuilder()
                    .setEmptyListAllowed(true)
                    .setStringMetadata(
                        StringPolicyMetadata.newBuilder().setEmptyStringAllowed(false)
                    ),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyListPolicyTest : CommonPolicyTests<List<String>>() {

                    override val policyIdentifier = PolicyIdentifier.MY_LIST_POLICY

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            [],
                            ["a-value"],
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases =
                        listOf(
                            InvalidValueTestCase([""], expectedError="Empty string is not allowed"),
                        )
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun listOfStringPolicy_emptyListNotAllowed_generatesTestClass() {
        val metadata =
            listPolicyMetadata(
                "MY_LIST_POLICY",
                stringListType,
                ListPolicyMetadata.newBuilder()
                    .setEmptyListAllowed(false)
                    .setStringMetadata(
                        StringPolicyMetadata.newBuilder().setEmptyStringAllowed(true)
                    ),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyListPolicyTest : CommonPolicyTests<List<String>>() {

                    override val policyIdentifier = PolicyIdentifier.MY_LIST_POLICY

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            ["", "a-value"],
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases =
                        listOf(
                            InvalidValueTestCase([], expectedError="Empty list is not allowed"),
                        )
                }
                """
                    .trimIndent()
            )
    }

    @Test
    fun listOfEnumPolicy_generatesTestClass() {
        val metadata =
            listPolicyMetadata(
                "MY_LIST_POLICY",
                integerListType,
                ListPolicyMetadata.newBuilder()
                    .setEmptyListAllowed(true)
                    .setEnumMetadata(
                        EnumPolicyMetadata.newBuilder()
                    .addValue(fullyQualifiedFieldName("ENUM_VALUE_4"), 4)
                    .addValue(fullyQualifiedFieldName("ENUM_VALUE_7"), 7)
                    ),
            )

        val output_file = TestFileGenerator(metadata).generate()

        assertThat(output_file.getTestClass())
            .isEqualTo(
                """
                class MyListPolicyTest : CommonPolicyTests<List<Int>>() {

                    override val policyIdentifier = PolicyIdentifier.MY_LIST_POLICY

                    // TODO: Add other meaningful valid values here (if any). Remove this TODO before committing.
                    override val validValues =
                        listOf(
                            [],
                            [PolicyIdentifier.ENUM_VALUE_4, PolicyIdentifier.ENUM_VALUE_7],
                        )

                    // TODO: Add other meaningful invalid values here (if any). Remove this TODO before committing.
                    override val invalidValueTestCases =
                        listOf(
                            InvalidValueTestCase([3]), // Lower than lowest value
                            InvalidValueTestCase([8]), // Higher than highest value
                        )
                }
                """
                    .trimIndent()
            )
    }

    // Find the test class in the output file.
    private fun String.getTestClass() =
        // Strip everything before the `class XYZ : ... {` line
        this.trimBefore("\nclass ").trim()

    // Find the EnterprisePolicy in the output file.
    private fun String.getEnterprisePolicy(scope: PolicyScope): String {
        val scopeString =
            when (scope) {
                PolicyScope.POLICY_SCOPE_USER -> "User"
                PolicyScope.POLICY_SCOPE_DEVICE -> "Device"
                PolicyScope.POLICY_SCOPE_PARENT_USER -> "ParentUser"
                else -> throw IllegalArgumentException("Invalid policy scope $scope")
            }
        val regex =
            ("// Policy definition that runs with ${scope}" + // first line
                    ".*?" + // body
                    "class .*Policy_Scope${scopeString} \\{\\}" // last line
                )
                .toRegex(RegexOption.DOT_MATCHES_ALL)

        return regex
            .find(this)
            ?.value
            // Remove the comments before the @EnterprisePolicy
            ?.trimBefore("@EnterprisePolicy")
            ?: """
             EnterprisePolicy NOT FOUND in:
             -----------------------------
             $this
            """
                .trimIndent()
    }

    private fun EnumPolicyMetadata.Builder.addValue(name: FullyQualifiedFieldName, value: Int) =
        this.addValues(
            EnumPolicyMetadata.EnumValue.newBuilder().setFieldName(name).setIntValue(value)
        )

    private fun String.trimBefore(delimiter: String) =
        this.replaceBefore(delimiter, replacement = "")

    private fun fullyQualifiedFieldName(
        name: String,
        className: String = "PolicyIdentifier",
        packageName: String = "android.app.admin",
    ) =
        FullyQualifiedFieldName.newBuilder()
            .setFieldName(name)
            .setClassName(className)
            .setPackageName(packageName)
            .build()

    private fun policyMetadata(
        identifier: String,
        allowedScopes: List<PolicyScope>,
        allowedDpcTypes: List<DpcType>,
        permission: String,
        crossUserPermission: String,
    ) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(stringType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setStringMetadata(StringPolicyMetadata.newBuilder())
            )
            .addAllAllowedScopes(allowedScopes)
            .addAllAllowedDpcTypes(allowedDpcTypes)
            .setRequiredPermission(permission)
            .setRequiredCrossUserPermission(crossUserPermission)
            .build()

    private fun stringPolicyMetadata(
        identifier: String,
        typeSpecificMetadata: StringPolicyMetadata.Builder,
    ) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(stringType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder().setStringMetadata(typeSpecificMetadata)
            )
            .build()

    private fun enumPolicyMetadata(
        identifier: String,
        typeSpecificMetadata: EnumPolicyMetadata.Builder,
    ) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(integerType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder().setEnumMetadata(typeSpecificMetadata)
            )
            .build()

    private fun booleanPolicyMetadata(identifier: String) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(booleanType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setBooleanMetadata(BooleanPolicyMetadata.newBuilder())
            )
            .build()

    private fun longPolicyMetadata(identifier: String) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(longType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setLongMetadata(TypeSpecificPolicyMetadata.LongPolicyMetadata.newBuilder())
            )
            .build()

    private fun integerPolicyMetadata(identifier: String) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(integerType)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setIntegerMetadata(
                        TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                    )
            )
            .build()

    private fun listPolicyMetadata(
        identifier: String,
        type: FullyQualifiedClassName,
        typeSpecificMetadata: ListPolicyMetadata.Builder,
    ) =
        PolicyMetadata.newBuilder()
            .setIdentifier(fullyQualifiedFieldName(identifier))
            .setType(type)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(typeSpecificMetadata)
            )
            .build()
}
