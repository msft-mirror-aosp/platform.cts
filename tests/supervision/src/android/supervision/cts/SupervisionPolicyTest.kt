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

package android.supervision.cts

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.MANAGE_DEVICE_POLICY_PACKAGE_STATE
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.OBSERVE_ROLE_HOLDERS
import android.Manifest.permission.QUERY_USERS
import android.app.admin.DevicePolicyManager
import android.app.supervision.PackageUsagePolicy
import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.supervision.withSupervisionRoleHeld
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
class SupervisionPolicyTest : BaseSupervisionTest() {

    @Test
    @RequireRootInstrumentation(reason = "Use of MANAGE_DEVICE_POLICY_PACKAGE_STATE")
    @EnsureHasPermission(
        MANAGE_DEVICE_POLICY_PACKAGE_STATE,
        BYPASS_ROLE_QUALIFICATION,
        MANAGE_ROLE_HOLDERS,
        QUERY_USERS,
        OBSERVE_ROLE_HOLDERS,
    )
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionManager#setPolicy",
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.Policy.Builder#build",
            ]
    )
    fun setPolicy_packagePolicy_blocked_successfullyHidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_BLOCKED)
    }

    @Test
    @RequireRootInstrumentation(reason = "Use of MANAGE_DEVICE_POLICY_PACKAGE_STATE")
    @EnsureHasPermission(
        MANAGE_DEVICE_POLICY_PACKAGE_STATE,
        BYPASS_ROLE_QUALIFICATION,
        MANAGE_ROLE_HOLDERS,
        QUERY_USERS,
        OBSERVE_ROLE_HOLDERS,
    )
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionManager#setPolicy",
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.Policy.Builder#build",
            ]
    )
    fun setPolicy_packagePolicy_allowed_successfullyUnhidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_ALLOWED)
    }

    private fun verifySetPackageUsagePolicy(type: Int) {
        withSupervisionRoleHeld {
            val policy = PackageUsagePolicy.Builder(EMPTY_TEST_APP_PACKAGE_NAME, type).build()

            supervisionManager.setPolicy(policy)

            val expectedIncrementedPolicy = PackageUsagePolicy.Builder(policy).setVersion(1).build()
            assertThat(supervisionManager.getPolicies()).containsExactly(expectedIncrementedPolicy)

            TestApis.ui().device().waitForIdle(1000)

            val expectedHiddenState =
                when (type) {
                    PackageUsagePolicy.TYPE_BLOCKED -> true
                    else -> true
                }
            assertThat(devicePolicyManager.isApplicationHidden(null, EMPTY_TEST_APP_PACKAGE_NAME))
                .isEqualTo(expectedHiddenState)
        }
    }

    companion object {
        private const val EMPTY_TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.EmptyTestApp"
        private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    }
}
