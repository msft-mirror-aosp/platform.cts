/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.testapis.parser.signatures

import javax.lang.model.util.Elements

/**
 * Represents a minimal representation of a class for comparison purposes.
 */
data class ClassSignature(
    val packageName: String,
    val name: String,
    private val mConstructorSignature: ConstructorSignature?,
    val methodSignatures: List<MethodSignature>
) {
    /**
     * Checks if this is a "Test class" (a class marked as @TestApi).
     *
     *
     * Note: We are parsing `test-current.txt` and there is not enough information in the
     * text file to know if a class defined here is a "Test class". We assume it is a "Test class"
     * if it is present in test-current.txt and is inaccessible when test sdk is disabled.
     */
    fun isTestClass(elements: Elements): Boolean {
        return elements.getTypeElement(this.name) == null
    }
}
