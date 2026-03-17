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

package com.android.cts.policytestsgenerator

import android.processor.devicepolicy.protos.PolicyMetadata

// Generates the list of feature flags that must be enabled for the test to run.
object FlagsGenerator {

    val commonFlags =
        listOf("Flags.FLAG_POLICY_STREAMLINING", "Flags.FLAG_POLICY_STREAMLINING_TESTS")

    fun generateFlags(metadata: PolicyMetadata): List<String> {
        return if (metadata.hasFlag()) {
            commonFlags + formatFlag(metadata.flag)
        } else {
            commonFlags
        }
    }

    // A flag the.namespace.of.the_flag_name is reference in the code as
    // the.namespace.of.Flags.FLAG_THE_FLAG_NAME.
    fun formatFlag(flag: String): String {
        val (namespace, flagname) = flag.splitNamespace()
        return "${namespace}.Flags.FLAG_${flagname.uppercase()}"
    }

    // Splits the namespace and the name.
    // Example:
    //    "a.b.c.d" -> "a.b.c" to "d"
    fun String.splitNamespace(): Pair<String?, String> =
        this.substringBeforeLast('.', missingDelimiterValue = "") to this.substringAfterLast('.')
}
