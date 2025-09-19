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

package com.android.bedstead.enterprise.policies

import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_GLOBALLY
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER

/**
 * Policy for toggling Common Criteria Mode.
 */
@EnterprisePolicy(
    dpc = [
        APPLIED_BY_DEVICE_OWNER
                or APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE
                or APPLIES_GLOBALLY
                or CANNOT_BE_APPLIED_BY_ROLE_HOLDER
    ]
)
class CommonCriteriaMode
