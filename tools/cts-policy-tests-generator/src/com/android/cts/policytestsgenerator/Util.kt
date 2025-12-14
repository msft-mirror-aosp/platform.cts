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

import android.processor.devicepolicy.protos.FullyQualifiedClassName
import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import com.google.common.base.CaseFormat
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

val SCRIPT_NAME = "cts-policy-tests-generator"

// Converts SCREEN_CAPTURE_ALLOWED to ScreenCaptureAllowed
fun String.toCamelCase(): String =
    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this)

fun FullyQualifiedClassName.toKotlinType(): String {
    return when (this.format()) {
        "java.lang.String" -> "String"
        "java.lang.Boolean" -> "Boolean"
        "java.lang.Integer" -> "Int"
        "java.util.List<java.lang.String>" -> "List<String>"
        "java.util.List<java.lang.Integer>" -> "List<Int>"
        else -> throw IllegalArgumentException("Unsupported type: ${this.format()}")
    }
}

fun FullyQualifiedClassName.format(): String = "${this.packageName}.${this.className}"

fun FullyQualifiedFieldName.format(): String =
    "${this.packageName}.${this.className}.${this.fieldName}"

fun String.quote(): String = "\"$this\""

fun Collection<String>.quoteAll() = this.map { "\"$it\"" }

fun <T> Collection<T>.filterDuplicates(): Collection<T> = this.toSet()

// Remove trailing whitespaces from every line in the string
fun String.multiLineTrimEnd() = this.split("\n").map { it.trimEnd() }.joinToString("\n")

// If the string starts with any of the passed in prefixes,
// it will be replaced by the given replacement value.
//
// Example:
//    "a.b.c.d".replacePrefixes("a.b.c" to "X") -> "X.d"
fun String.replacePrefixes(vararg prefixes: Pair<String, String>): String {
    for ((prefix, replacement) in prefixes) {
        if (this.startsWith(prefix)) {
            return this.replaceFirst(prefix, replacement)
        }
    }
    return this
}

// We don't know exactly where the text proto is thanks to sharding.
fun findPoliciesTextProtoFile(outDir: String): Path? {
    val common_directory =
        Path("$outDir/soong/.intermediates/frameworks/base/framework-minus-apex/android_common/javac/")
    if (!common_directory.exists()) {
        // The caller needs to compile!
        return null
    }
    val shard_directories = common_directory.listDirectoryEntries("shard*")
    val possible_files =
        shard_directories.map {
            it.resolve("anno/android/processor/devicepolicy/policies.textproto")
        }
    val actual_files = possible_files.filter { it.exists() }

    if (actual_files.isEmpty()) {
        // The caller needs to compile!
        return null
    }
    require(actual_files.size == 1) {
        "SHOULD NOT HAPPEN - Multiple policies.textproto entries found? \n    ${actual_files.joinToString("\n    ")}"
    }
    return actual_files.get(0).normalize()
}
