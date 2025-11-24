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
package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.annotations.meta.BedsteadTest
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4
import com.google.auto.value.AutoAnnotation
import kotlin.reflect.KClass

/**
 * Mark a test class as testing the given {@code EnterprisePolicy}.
 *
 * This annotation is used when the test annotations use the `scope` parameter.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RequiresBedsteadJUnit4
@BedsteadTest
annotation class UsesEnterprisePolicies(
    /**
     * The {@link EnterprisePolicy} used when the test annotations use scope {@code
     * POLICY_SCOPE_USER}.
     */
    val scopeUser: KClass<*>,
    /**
     * The {@link EnterprisePolicy} used when the test annotations use scope {@code
     * POLICY_SCOPE_DEVICE}.
     */
    val scopeDevice: KClass<*>,
    /**
     * The {@link EnterprisePolicy} used when the test annotations use scope {@code
     * POLICY_SCOPE_PARENT_USER}.
     */
    val scopeParentUser: KClass<*>,
)

@AutoAnnotation
fun usesEnterprisePolicies(
    scopeUser: Class<*>,
    scopeDevice: Class<*>,
    scopeParentUser: Class<*>,
): UsesEnterprisePolicies {
    return AutoAnnotation_UsesEnterprisePoliciesKt_usesEnterprisePolicies(
        scopeUser,
        scopeDevice,
        scopeParentUser,
    )
}
