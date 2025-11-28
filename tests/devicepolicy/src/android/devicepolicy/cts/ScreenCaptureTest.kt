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

import android.app.admin.PolicyIdentifier
import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_GLOBALLY
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.Permission
import com.android.bedstead.enterprise.annotations.UsesEnterprisePolicies
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_ACROSS_USERS
import com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE
import com.android.compatibility.common.util.ApiTest
import org.junit.runner.RunWith

// Version that runs with POLICY_SCOPE_USER.
@EnterprisePolicy(
    dpc =
        [
            APPLIED_BY_AFFILIATED_PROFILE_OWNER or APPLIES_TO_OWN_USER,
            APPLIED_BY_DEVICE_OWNER or APPLIES_TO_OWN_USER,
            APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE or APPLIES_TO_OWN_USER,
            APPLIED_BY_PROFILE_OWNER_USER or APPLIES_TO_OWN_USER,
            APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO or APPLIES_TO_OWN_USER,
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE or APPLIES_TO_OWN_USER,
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER or APPLIES_TO_OWN_USER,
        ],
    permissions =
        [
            Permission(
                appliedWith = [MANAGE_DEVICE_POLICY_SCREEN_CAPTURE],
                appliesTo = APPLIES_TO_OWN_USER,
            )
        ],
)
public final class ScreenCapturePolicy_ScopeUser {}

// Version that runs with POLICY_SCOPE_DEVICE.
@EnterprisePolicy(
    dpc =
        [
            APPLIED_BY_DEVICE_OWNER or APPLIES_GLOBALLY,
            APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE or APPLIES_GLOBALLY,
        ],
    permissions =
        [
            EnterprisePolicy.Permission(
                appliedWith =
                    [MANAGE_DEVICE_POLICY_SCREEN_CAPTURE, MANAGE_DEVICE_POLICY_ACROSS_USERS],
                appliesTo = APPLIES_GLOBALLY,
            )
        ],
)
public final class ScreenCapturePolicy_ScopeDevice {}

// Version that runs with POLICY_SCOPE_PARENT_USER.
@EnterprisePolicy(dpc = [], permissions = [])
public final class ScreenCapturePolicy_ScopeParentUser {}

@RunWith(BedsteadJUnit4::class)
@UsesEnterprisePolicies(
    scopeUser = ScreenCapturePolicy_ScopeUser::class,
    scopeDevice = ScreenCapturePolicy_ScopeDevice::class,
    scopeParentUser = ScreenCapturePolicy_ScopeParentUser::class,
)
@ApiTest(
    apis =
        [
            "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE",
            "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE_ALLOWED",
            "android.app.admin.PolicyIdentifier#SCREEN_CAPTURE_DISALLOWED",
        ]
)
class ScreenCaptureTest : CommonPolicyTests<Int>() {

    override val policyIdentifier = PolicyIdentifier.SCREEN_CAPTURE

    override val validValues: List<Int> =
        listOf(PolicyIdentifier.SCREEN_CAPTURE_ALLOWED, PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED)

    override val invalidValueTestCases =
        listOf(
            InvalidValueTestCase(0), // Lower than lowest enum
            InvalidValueTestCase(3), // Higher than highest enum
            InvalidValueTestCase(-1), // Negative number
            InvalidValueTestCase(123), // High number
        )
}
