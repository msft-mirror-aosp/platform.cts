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

data class InvalidValueTestCase(
    val value: String,
    val expectedError: String? = null,
    val description: String? = null,
)

class TestFileGenerator(val metadata: PolicyMetadata) {

    fun generate(): String {
        return generateTestFile(
            identifier = metadata.identifier.fieldName,
            valueType = metadata.type.toKotlinType(),
            userDpcs = AppliedByGenerator.generateForUserScope(metadata),
            deviceDpcs = AppliedByGenerator.generateForDeviceScope(metadata),
            parentUserDpcs = AppliedByGenerator.generateForParentUserScope(metadata),
            userPermissions = PermissionsGenerator.generateForUserScope(metadata),
            parentUserPermissions = PermissionsGenerator.generateForParentUserScope(metadata),
            devicePermissions = PermissionsGenerator.generateForDeviceScope(metadata),
            permission = metadata.requiredPermission,
            crossUserPermission = metadata.requiredCrossUserPermission,
            validValues = ValuesGenerator.generateValidValues(metadata),
            invalidValues = ValuesGenerator.generateInvalidValues(metadata),
            apisUnderTest = ApisUnderTestGenerator.generateApiList(metadata),
        )
    }
}

private fun generateTestFile(
    identifier: String,
    valueType: String,
    userDpcs: List<String>,
    deviceDpcs: List<String>,
    parentUserDpcs: List<String>,
    userPermissions: List<String>,
    parentUserPermissions: List<String>,
    devicePermissions: List<String>,
    permission: String,
    crossUserPermission: String,
    validValues: List<String>,
    invalidValues: List<InvalidValueTestCase>,
    apisUnderTest: List<String>,
): String {
    val year = java.time.LocalDate.now().year

    val policyName = identifier.toCamelCase()

    val policyScopeUserDpcs = userDpcs.map { "${it} or APPLIES_TO_OWN_USER" }
    val policyScopeParentUserDpcs = parentUserDpcs.map { "${it} or APPLIES_TO_PARENT" }
    val policyScopeDeviceDpcs = deviceDpcs.map { "${it} or APPLIES_GLOBALLY" }

    val policyScopeUserPermissions = formatPermissions(userPermissions, "APPLIES_TO_OWN_USER")
    val policyScopeParentUserPermissions =
        formatPermissions(parentUserPermissions, "APPLIES_TO_PARENT")
    val policyScopeDevicePermissions = formatPermissions(devicePermissions, "APPLIES_GLOBALLY")
    val invalidValueTestCases = formatInvalidValueTestCases(invalidValues)

    val enterprisePolicyImports =
        formatEnterprisePolicyImports(
            userDpcs,
            deviceDpcs,
            parentUserDpcs,
            userPermissions,
            devicePermissions,
            parentUserPermissions,
            // Other `...EnterprisePolicy.X` imports that we want to be correctly sorted.
            listOf("Permission"),
        )

    return """
      /*
       * Copyright (C) ${year} The Android Open Source Project
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

      package android.devicepolicy.cts

      import android.app.admin.PolicyIdentifier
      import android.app.admin.flags.Flags
      import com.android.bedstead.enterprise.annotations.EnterprisePolicy
      ${enterprisePolicyImports.joinToString("\n      ")}
      import com.android.bedstead.enterprise.annotations.UsesEnterprisePolicies
      import com.android.bedstead.flags.annotations.RequireFlagsEnabled
      import com.android.bedstead.harrier.BedsteadJUnit4
      import com.android.bedstead.harrier.DeviceState
      import com.android.compatibility.common.util.ApiTest
      import org.junit.ClassRule
      import org.junit.Rule
      import org.junit.runner.RunWith

      // Policy definition that runs with POLICY_SCOPE_USER.
      // Generated by running `$SCRIPT_NAME $identifier`.
      @EnterprisePolicy(
          dpc = ${formatArray(policyScopeUserDpcs, indent="              ")},
          permissions = ${formatArray(policyScopeUserPermissions, indent="              ")}
      )
      public final class ${policyName}Policy_ScopeUser {}

      // Policy definition that runs with POLICY_SCOPE_DEVICE.
      // Generated by running `$SCRIPT_NAME $identifier`.
      @EnterprisePolicy(
          dpc = ${formatArray(policyScopeDeviceDpcs, indent="              ")},
          permissions = ${formatArray(policyScopeDevicePermissions, indent="              ")}
      )
      public final class ${policyName}Policy_ScopeDevice {}

      // Policy definition that runs with POLICY_SCOPE_PARENT_USER.
      // Generated by running `$SCRIPT_NAME $identifier`.
      @EnterprisePolicy(
          dpc = ${formatArray(policyScopeParentUserDpcs, indent="              ")},
          permissions = ${formatArray(policyScopeParentUserPermissions, indent="              ")}
      )
      public final class ${policyName}Policy_ScopeParentUser {}

      @RunWith(BedsteadJUnit4::class)
      @RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS)
      @UsesEnterprisePolicies(
          scopeUser = ${policyName}Policy_ScopeUser::class,
          scopeDevice = ${policyName}Policy_ScopeDevice::class,
          scopeParentUser = ${policyName}Policy_ScopeParentUser::class,
      )
      @ApiTest(
          apis = ${formatArray(apisUnderTest.quoteAll().sorted(), "              ")}
      )
      class ${policyName}GeneratedTest : CommonPolicyTests<${valueType}>() {

          override val policyIdentifier = PolicyIdentifier.${identifier}

          override val validValues = ${formatList(validValues, indent="              ")}

          override val invalidValueTestCases = ${formatList(invalidValueTestCases, indent="              ", addCommas=false)}

          override fun getDeviceState() = deviceState

          companion object {
              @Rule @ClassRule @JvmField val deviceState = DeviceState()
          }
      }
    """.multiLineTrimEnd().trimIndent()
}

private fun formatPermissions(permissions: List<String>, appliesTo: String): List<String> {
    if (permissions.isEmpty()) return listOf()

    return listOf(
        """
            Permission(
                appliedWith =${formatArray(permissions.quoteAll(), indent="                    ")},
                appliesTo = ${appliesTo},
            )
          """
            .trimIndent()
    )
}

private fun formatInvalidValueTestCases(invalidValues: List<InvalidValueTestCase>) =
    invalidValues.map {
        if (it.description != null && it.expectedError != null) {
            "InvalidValueTestCase(${it.value}, expectedError=\"${it.expectedError}\"), // ${it.description}"
        } else if (it.expectedError != null) {
            "InvalidValueTestCase(${it.value}, expectedError=\"${it.expectedError}\"),"
        } else if (it.description != null) {
            "InvalidValueTestCase(${it.value}), // ${it.description}"
        } else {
            "InvalidValueTestCase(${it.value}),"
        }
    }

private fun formatEnterprisePolicyImports(
    userDpcs: List<String>,
    deviceDpcs: List<String>,
    parentUserDpcs: List<String>,
    userPermissions: List<String>,
    devicePermissions: List<String>,
    parentUserPermissions: List<String>,
    otherImports: List<String>,
): List<String> {
    val appliedByStatements = userDpcs + deviceDpcs + parentUserDpcs

    val appliesToStatements = mutableListOf<String>()
    if (!userDpcs.isEmpty() || !userPermissions.isEmpty()) {
        appliesToStatements.add("APPLIES_TO_OWN_USER")
    }
    if (!deviceDpcs.isEmpty() || !devicePermissions.isEmpty()) {
        appliesToStatements.add("APPLIES_GLOBALLY")
    }
    if (!parentUserDpcs.isEmpty() || !parentUserPermissions.isEmpty()) {
        appliesToStatements.add("APPLIES_TO_PARENT")
    }

    return (appliedByStatements + appliesToStatements + otherImports)
        .filterDuplicates()
        .sorted()
        .map { "import com.android.bedstead.enterprise.annotations.EnterprisePolicy.$it" }
}

// Formats an array with the given elements. The opening and closing '[]' will use the
// given indent, every element inside the array is indented one level more.
private fun formatArray(elements: List<String>, indent: String): String {
    if (elements.isEmpty()) return "[]"

    return elements
        .map { it.prependIndent(indent + "    ") }
        .joinToString(",\n", prefix = "\n${indent}[\n", postfix = ",\n${indent}]")
}

// Formats a 'listOf()' with the given elements. If the list is not empty the 'listOf(' and ')'
// statements will each be on their own lines, as well as each element.
private fun formatList(elements: List<String>, indent: String, addCommas: Boolean = true): String {
    if (elements.isEmpty()) return "listOf()"

    val comma = if (addCommas) "," else ""

    return elements
        .map { it.prependIndent(indent + "    ") }
        .joinToString("${comma}\n", prefix = "\n${indent}listOf(\n", postfix = "${comma}\n${indent})")
}
