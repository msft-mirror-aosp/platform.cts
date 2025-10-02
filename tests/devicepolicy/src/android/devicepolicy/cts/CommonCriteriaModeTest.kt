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

import android.app.admin.DevicePolicyManager
import android.app.admin.flags.Flags
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.policies.CommonCriteriaMode
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import java.lang.AutoCloseable
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class CommonCriteriaModeTest {
    @PolicyAppliesTest(policy = [CommonCriteriaMode::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setCommonCriteriaModeEnabled"])
    fun setCommonCriteriaModeEnabled_enabled_success() {
        assumeTrue(
            deviceState.dpc().componentName() != null ||
                    Flags.commonCriteriaModeCoexistence()
        )

        CommonCriteriaModeCleanup().use {
            deviceState.dpc().devicePolicyManager()
                .setCommonCriteriaModeEnabled(deviceState.dpc().componentName(), true)

            assertThat(localDevicePolicyManager.isCommonCriteriaModeEnabled(null)).isTrue()
        }
    }

    @PolicyAppliesTest(policy = [CommonCriteriaMode::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setCommonCriteriaModeEnabled"])
    fun setCommonCriteriaModeEnabled_disabled_success() {
        assumeTrue(
            deviceState.dpc().componentName() != null ||
                    Flags.commonCriteriaModeCoexistence()
        )

        CommonCriteriaModeCleanup().use {
            deviceState.dpc().devicePolicyManager()
                .setCommonCriteriaModeEnabled(deviceState.dpc().componentName(), false)

            assertThat(localDevicePolicyManager.isCommonCriteriaModeEnabled(null)).isFalse()
        }
    }

    @CannotSetPolicyTest(policy = [CommonCriteriaMode::class], includeNonDeviceAdminStates = false)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setCommonCriteriaModeEnabled"])
    fun setCommonCriteriaModeEnabled_enabled_throwsException() {
        assumeTrue(
            deviceState.dpc().componentName() != null ||
                    Flags.commonCriteriaModeCoexistence()
        )

        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setCommonCriteriaModeEnabled(deviceState.dpc().componentName(), true)
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        val localDevicePolicyManager =
            requireNotNull(
                context().instrumentedContext()
                    .getSystemService(DevicePolicyManager::class.java)
            )

        private class CommonCriteriaModeCleanup : AutoCloseable {
            override fun close() {
                deviceState.dpc().devicePolicyManager()
                    .setCommonCriteriaModeEnabled(deviceState.dpc().componentName(), false)

                assertThat(localDevicePolicyManager.isCommonCriteriaModeEnabled(null)).isFalse()
            }
        }
    }
}
