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
package com.android.bedstead.permissions

import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.ParameterizedTestGenerator
import com.android.bedstead.permissions.annotations.PermissionTest
import com.android.bedstead.permissions.annotations.ensureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.ensureHasPermission

/**
 * [ParameterizedTestGenerator] for permissions
 */
@Suppress("unused")
class PermissionsParameterizedTestGenerator : ParameterizedTestGenerator {

    override fun generateReplacementAnnotations(
        annotation: Annotation
    ): List<DynamicParameterizedAnnotation> {
        if (annotation is PermissionTest) {
            return generatePermissionAnnotations(annotation)
        }
        return emptyList()
    }

    private fun generatePermissionAnnotations(annotation: PermissionTest): List<DynamicParameterizedAnnotation> {
        val allPermissions: MutableSet<String> = annotation.value.toHashSet()
        val replacementAnnotations = mutableListOf<DynamicParameterizedAnnotation>()

        for (permission in annotation.value) {
            allPermissions.remove(permission)
            replacementAnnotations.add(
                DynamicParameterizedAnnotation(
                    permission,
                    arrayOf(
                        ensureHasPermission(permission),
                        ensureDoesNotHavePermission(allPermissions.toTypedArray())
                    ),
                    annotation.priority
                )
            )
            allPermissions.add(permission)
        }

        return replacementAnnotations
    }
}
