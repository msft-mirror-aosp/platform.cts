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

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import com.google.protobuf.TextFormat
import kotlin.io.path.Path
import kotlin.io.path.exists

val OUTPUT_DIR = "/cts/tests/devicepolicy/src/android/devicepolicy/cts"

fun main(args: Array<String>) {
    val arguments: ParsedArguments =
        try {
            ArgumentParser.parse(args)
        } catch (e: IllegalArgumentException) {
            System.err.println("Error: ${e}")
            System.err.println("")
            System.out.println(ArgumentParser.help())
            System.exit(1)
            return
        }

    if (arguments.help) {
        System.out.println(ArgumentParser.help())
        System.exit(0)
    }

    if (arguments.policies.size != 1) {
        System.err.println("Error: Must specify exactly one policy name")
        System.out.println(ArgumentParser.help())
        System.exit(2)
    }

    val rootDir = System.getenv("ANDROID_BUILD_TOP")
    val hostOutDir = System.getenv("ANDROID_HOST_OUT")
    if (rootDir == null || hostOutDir == null) {
        System.err.println("Error: 'lunch' is required before running this tool.")
        System.exit(3)
    }
    // Lunch doesn't provide a variable that points to the out directory itself,
    // only a bunch that point to other subdirectories in there than the one we need. :/
    val outDir = hostOutDir + "/../../"

    val textProto = readTextProto(outDir)
    if (textProto == null) {
        System.err.println(
            "Error: policies.textproto not found. " +
                "Try compiling the framework-minus-apex target."
        )
        System.exit(4)
    }

    val policyName = arguments.policies.get(0)!!
    val policies = parsePolicies(textProto!!)

    if (!policies.containsKey(policyName)) {
        System.err.println("Policy not found: \"$policyName\"")
        System.err.println(
            "Try compiling the framework-minus-apex target if you recently added the policy."
        )
        System.err.println("Available policies:\n    ${policies.keys.joinToString(", \n    ")}")
        System.exit(5)
    }

    val metadata = policies[policyName]!!
    val output = TestFileGenerator(metadata).generate()

    if (arguments.stdout) {
        println("--------------------------------------------------------------------------------")
        println("--- ${policyName}")
        println("--------------------------------------------------------------------------------")
        println(output)
    } else {
        val output_file_path = Path("$rootDir/$OUTPUT_DIR/${policyName.toCamelCase()}Test.kt")

        if (output_file_path.exists() && !arguments.override) {
            System.err.println(
                "Error: Output file ${output_file_path} exists. Use '--override' if you want to override it."
            )
            System.exit(6)
        }

        output_file_path.toFile().writeText(output)
        System.out.println("Wrote CTS tests to ${output_file_path}")
    }
    System.exit(0)
}

private fun readTextProto(outDir: String): String? {
    return findPoliciesTextProtoFile(outDir)?.readText()
}

private fun parsePolicies(textproto: String): Map<String, PolicyMetadata> {
    val policies = TextFormat.parse(textproto, PolicyMetadataList::class.java)
    return policies.policyMetadataList.associateBy { it.identifier.fieldName }
}
