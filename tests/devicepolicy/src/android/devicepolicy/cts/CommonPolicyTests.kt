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

package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.PolicyIdentifier
import android.app.admin.RemoteDevicePolicyManager
import android.app.admin.flags.Flags
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.GeneratedPolicyMetadata
import android.app.admin.metadata.IntegerPolicyMetadata
import android.app.admin.metadata.StringPolicyMetadata
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.DeviceState
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.MatcherUtils.hasMessageThat
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.test.fail
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Base class containing tests that are common for every policy under test.
 *
 * This requires that the derived class is annotated with the {@code UsesEnterprisePolicies}
 * annotation.
 *
 * @param T The value type of the policy under test.
 */
@RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS)
@ApiTest(
    apis =
        [
            "android.app.admin.DevicePolicyManager#setPolicy",
            "android.app.admin.DevicePolicyManager#getPolicy",
        ]
)
public abstract class CommonPolicyTests<T> {

    /** The policy identifier for the policy under test. */
    abstract val policyIdentifier: PolicyIdentifier<T>

    /**
     * A list of valid values for the policy under test. This is used to test that the policy can be
     * set to each of these values.
     *
     * This list must contain at least one value.
     */
    abstract val validValues: List<T>

    data class InvalidValueTestCase<T>(
        val value: T,
        // A substring of the expected error message.
        val expectedError: String = "Unsupported value",
    )

    /**
     * A list of invalid values for the policy under test. This is used to test that setting the
     * policy to these values fails.
     */
    abstract val invalidValueTestCases: List<InvalidValueTestCase<T>>

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_USER)
    fun scopeUser_setValueToDefault_accepted() {
        setInitialValue(POLICY_SCOPE_USER, getValidValue())
        testSetAndGet(POLICY_SCOPE_USER, null)
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    open fun scopeDevice_setValueToDefault_accepted() {
        setInitialValue(POLICY_SCOPE_DEVICE, getValidValue())
        testSetAndGet(POLICY_SCOPE_DEVICE, null)
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)
    fun scopeParentUser_setValueToDefault_accepted() {
        setInitialValue(POLICY_SCOPE_PARENT_USER, getValidValue())
        testSetAndGet(POLICY_SCOPE_PARENT_USER, null)
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_USER)
    fun scopeUser_setAndGetValue_accepted() {
        testSetAndGet(POLICY_SCOPE_USER, getValidValue())
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_setAndGetValue_accepted() {
        testSetAndGet(POLICY_SCOPE_DEVICE, getValidValue())
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)
    fun scopeParentUser_setAndGetValue_accepted() {
        testSetAndGet(POLICY_SCOPE_PARENT_USER, getValidValue())
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_USER, singleTestOnly = true)
    fun scopeUser_validValues_accepted() {
        for (value in validValues) {
            testSetAndGet(POLICY_SCOPE_USER, value)
        }
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE, singleTestOnly = true)
    fun scopeDevice_validValues_accepted() {
        for (value in validValues) {
            testSetAndGet(POLICY_SCOPE_DEVICE, value)
        }
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER, singleTestOnly = true)
    fun scopeParentUser_validValues_accepted() {
        for (value in validValues) {
            testSetAndGet(POLICY_SCOPE_PARENT_USER, value)
        }
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_USER, singleTestOnly = true)
    fun scopeUser_invalidValues_rejected() {
        for (testCase in invalidValueTestCases) {
            testInvalidValue(POLICY_SCOPE_USER, testCase.value, testCase.expectedError)
        }
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE, singleTestOnly = true)
    fun scopeDevice_invalidValues_rejected() {
        for (testCase in invalidValueTestCases) {
            testInvalidValue(POLICY_SCOPE_DEVICE, testCase.value, testCase.expectedError)
        }
    }

    @Test
    @CanSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER, singleTestOnly = true)
    fun scopeParentUser_invalidValues_rejected() {
        for (testCase in invalidValueTestCases) {
            testInvalidValue(POLICY_SCOPE_PARENT_USER, testCase.value, testCase.expectedError)
        }
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_USER)
    fun scopeUser_cannotSetPolicy_setPolicy_rejected() {
        testSetNotAllowed(POLICY_SCOPE_USER)
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_cannotSetPolicy_setPolicy_rejected() {
        testSetNotAllowed(POLICY_SCOPE_DEVICE)
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)
    fun scopeParentUser_cannotSetPolicy_setPolicy_rejected() {
        testSetNotAllowed(POLICY_SCOPE_PARENT_USER)
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_USER)
    fun scopeUser_cannotSetPolicy_getPolicy_rejected() {
        testGetNotAllowed(POLICY_SCOPE_USER)
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_cannotSetPolicy_getPolicy_rejected() {
        testGetNotAllowed(POLICY_SCOPE_DEVICE)
    }

    @Test
    @CannotSetPolicyTest(scope = POLICY_SCOPE_PARENT_USER)
    fun scopeParentUser_cannotSetPolicy_getPolicy_rejected() {
        testGetNotAllowed(POLICY_SCOPE_PARENT_USER)
    }

    private fun testSetAndGet(scope: Int, value: T?) {
        setPolicy(scope, value)
        assertThat(getPolicy(scope)).isEqualTo(value)
    }

    private fun testInvalidValue(scope: Int, value: T, expectedError: String) {
        val exception =
            assertFailsWith<IllegalArgumentException>("Error while testing value \"$value\"") {
                setPolicy(scope, value)
            }
        assertThat(exception).hasMessageThat().contains(expectedError)
    }

    private fun testSetNotAllowed(scope: Int) {
        if (isParentInstance) {
            // TODO: 456426561 - remove this check once we can access the non-parent DPM.
            return
        }
        try {
            setPolicy(scope, getValidValue())
            fail("setPolicy should have been rejected")
        } catch (exception: SecurityException) {
            // This is hit when the scope is supported for the policy, but not
            // for the current DPC.
            assertThat(exception)
                .hasMessageThat()
                .contains("Caller does not have the required permissions")
        } catch (exception: IllegalArgumentException) {
            // This is hit when the scope is not supported for the policy.
            assertThat(exception).hasMessageThat().contains("only supports scopes")
        }
    }

    private fun testGetNotAllowed(scope: Int) {
        if (isParentInstance) {
            // TODO: 456426561 - remove this check once we can access the non-parent DPM.
            return
        }
        try {
            getPolicy(scope)
            fail("getPolicy should have been rejected")
        } catch (exception: SecurityException) {
            // This is hit when the scope is supported for the policy, but not
            // for the current DPC.
            assertThat(exception)
                .hasMessageThat()
                .contains("Caller does not have the required permissions")
        } catch (exception: IllegalArgumentException) {
            // This is hit when the scope is not supported for the policy.
            assertThat(exception).hasMessageThat().contains("only supports scopes")
        }
    }

    // Returns a valid value for the policy under test.
    private fun getValidValue() = validValues.first()

    // Sets the given value as an initial value for the policy under test.
    // Used when setting the value to default, to ensure there is a change.
    private fun setInitialValue(scope: Int, value: T) {
        setPolicy(scope, value)
        assertThat(getPolicy(scope)).isEqualTo(value)
    }

    // This uses the derived classes of the `PolicyMetadata` to get the value type.
    // We need this since the type of `T` is not available due to type erasure.
    // TODO: 454277430 - Not needed once we can call DPM.setPolicy/DPM.getPolicy.
    @Suppress("UNCHECKED_CAST")
    protected fun setPolicy(scope: Int, value: T?) {
        if (value == null) {
            dpcDpm.clearPolicy(policyIdentifier.getId(), scope)
            return
        }

        when (GeneratedPolicyMetadata.getPolicyMetadata(policyIdentifier)) {
            is IntegerPolicyMetadata,
            is EnumPolicyMetadata ->
                dpcDpm.setIntegerPolicy(policyIdentifier.getId(), scope, value as Int)
            is StringPolicyMetadata ->
                dpcDpm.setStringPolicy(policyIdentifier.getId(), scope, value as String)
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    // This uses the derived classes of the `PolicyMetadata` to get the value type.
    // We need this since the type of `T` is not available due to type erasure.
    // TODO: 454277430 - Not needed once we can call DPM.setPolicy/DPM.getPolicy.
    @Suppress("UNCHECKED_CAST")
    protected fun getPolicy(scope: Int): T? {
        return when (GeneratedPolicyMetadata.getPolicyMetadata(policyIdentifier)) {
            is IntegerPolicyMetadata,
            is EnumPolicyMetadata -> {
                val result = dpcDpm.getIntegerPolicy(policyIdentifier.getId(), scope)
                return if (result == -1) null else result as T
            }
            is StringPolicyMetadata ->
                return dpcDpm.getStringPolicy(policyIdentifier.getId(), scope) as T?
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    // The DevicePolicyManager of the DPC.
    private val dpcDpm: RemoteDevicePolicyManager
        get() = deviceState.dpc().devicePolicyManager()

    private val isParentInstance: Boolean
        get() = deviceState.dpc().isParentInstance

    companion object {
        @Rule @ClassRule @JvmField val deviceState = DeviceState()
    }
}
