/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.app.admin.PolicyIdentifier
import android.app.admin.RemoteDevicePolicyManager
import android.app.admin.flags.Flags
import android.provider.Settings
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.UsesEnterprisePolicies
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.IntTestParameter
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS)
@UsesEnterprisePolicies(
    scopeUser = AutoTimePolicy_ScopeUser::class,
    scopeDevice = AutoTimePolicy_ScopeDevice::class,
    scopeParentUser = AutoTimePolicy_ScopeParentUser::class,
)
@ApiTest(
    apis =
        [
            "android.app.admin.PolicyIdentifier#AUTO_TIME",
            "android.app.admin.PolicyIdentifier#AUTO_TIME_DISABLED",
            "android.app.admin.PolicyIdentifier#AUTO_TIME_DISABLED_UNENFORCED",
            "android.app.admin.PolicyIdentifier#AUTO_TIME_ENABLED",
            "android.app.admin.PolicyIdentifier#AUTO_TIME_ENABLED_UNENFORCED",
            "android.app.admin.PolicyIdentifier#AUTO_TIME_USER_CHOICE",
        ]
)
class AutoTimeTest {

    private var originalSetting: Int = 0

    @Before
    fun setup() {
        originalSetting = TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0)
    }

    @After
    fun teardown() {
        TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, originalSetting)
    }

    private val policyIdentifier = PolicyIdentifier.AUTO_TIME

    @IntTestParameter(
        PolicyIdentifier.AUTO_TIME_ENABLED_UNENFORCED,
        PolicyIdentifier.AUTO_TIME_ENABLED,
    )
    @Retention(AnnotationRetention.RUNTIME)
    annotation class AutoTimePolicyEnabled

    @IntTestParameter(
        PolicyIdentifier.AUTO_TIME_DISABLED_UNENFORCED,
        PolicyIdentifier.AUTO_TIME_DISABLED,
    )
    @Retention(AnnotationRetention.RUNTIME)
    annotation class AutoTimePolicyDisabled

    @Test
    @RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING_AUTO_TIME)
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_setPolicy_enabledValues_shouldSetGlobalSetting(
        @AutoTimePolicyEnabled policyValue: Int
    ) {
        // If the original setting is 1, we need to disable auto time first to see
        // the effect of the policy.
        if (originalSetting == 1) {
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, 0)
        }

        setPolicy(POLICY_SCOPE_DEVICE, policyValue)

        assertThat(TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0))
            .isEqualTo(1)
    }

    @Test
    @RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING_AUTO_TIME)
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_setPolicy_disabledValues_shouldSetGlobalSetting(
        @AutoTimePolicyDisabled policyValue: Int
    ) {
        // If the original setting is 0, we need to enable auto time first to see
        // the effect of the policy.
        if (originalSetting == 0) {
            TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 1)
        }

        setPolicy(POLICY_SCOPE_DEVICE, policyValue)

        assertThat(TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 1))
            .isEqualTo(0)
    }

    @Test
    @RequireFlagsEnabled(Flags.FLAG_POLICY_STREAMLINING_AUTO_TIME)
    @CanSetPolicyTest(scope = POLICY_SCOPE_DEVICE)
    fun scopeDevice_setPolicy_userChoice_shouldNotChangeGlobalSetting() {
        setPolicy(POLICY_SCOPE_DEVICE, PolicyIdentifier.AUTO_TIME_USER_CHOICE)

        assertThat(TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0))
            .isEqualTo(originalSetting)
    }

    fun setPolicy(scope: Int, policy: Int) {
        dpcDPM.setIntegerPolicy(policyIdentifier.getId(), scope, policy)
    }

    // The DevicePolicyManager of the DPC.
    private val dpcDPM: RemoteDevicePolicyManager
        get() = deviceState.dpc().devicePolicyManager()

    companion object {
        @Rule @ClassRule @JvmField val deviceState: DeviceState = DeviceState()
    }
}
