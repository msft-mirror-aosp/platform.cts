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

val ANDROID_BUILD_TOP = System.getenv("ANDROID_BUILD_TOP")
val ANDROID_HOST_OUT = System.getenv("ANDROID_HOST_OUT")

// Lunch doesn't provide a variable that points to the out directory itself,
// only a bunch that point to other subdirectories in there than the one we need. :/
val BUILD_OUTPUT_DIR = "$ANDROID_HOST_OUT/../../"
val TEST_OUTPUT_DIR =
    "$ANDROID_BUILD_TOP/cts/tests/devicepolicy/src/android/devicepolicy/cts/generated"

// `ktfmt` is present in the aosp checkout
val KTFMT_JAR = "$ANDROID_BUILD_TOP/prebuilts/build-tools/common/framework/ktfmt.jar"
val KTFMT_COMMAND = arrayOf("java", "-jar", KTFMT_JAR, "--kotlinlang-style")

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

    if (ANDROID_BUILD_TOP == null || ANDROID_HOST_OUT == null) {
        System.err.println("Error: 'lunch' is required before running this tool.")
        System.exit(2)
    }

    val textProto = readTextProto(BUILD_OUTPUT_DIR)
    if (textProto == null) {
        System.err.println(
            "Error: policies.textproto not found. " +
                "Try compiling the framework-minus-apex target."
        )
        System.exit(3)
    }

    val policies = parsePolicies(textProto!!)

    val printer =
        if (arguments.stdout) {
            Printer.ToStdOut()
        } else {
            Printer.ToFile(
                outputDir = TEST_OUTPUT_DIR,
                allowOverride = arguments.override,
                format = arguments.format,
            )
        }

    if (arguments.all) {
        if (arguments.policies.size != 0) {
            System.err.println("Error: Cannot both specify a policy name and '-a'")
            System.out.println(ArgumentParser.help())
            System.exit(4)
        }

        policies.keys.forEach { p -> generateTestsForPolicy(p, policies, printer) }
    } else {
        if (arguments.policies.size != 1) {
            System.err.println("Error: Must specify exactly one policy name")
            System.out.println(ArgumentParser.help())
            System.exit(5)
        }

        val policyName = arguments.policies.get(0)!!
        generateTestsForPolicy(policyName, policies, printer)
    }

    System.exit(0)
}

private fun generateTestsForPolicy(
    policyName: String,
    policies: Map<String, PolicyMetadata>,
    printer: Printer,
) {
    if (!policies.containsKey(policyName)) {
        System.err.println("Policy not found: \"$policyName\"")
        System.err.println(
            "Try compiling the framework-minus-apex target if you recently added the policy."
        )
        System.err.println("Available policies:\n    ${policies.keys.joinToString(", \n    ")}")
        System.exit(6)
    }

    val metadata = policies[policyName]!!
    val output = TestFileGenerator(metadata).generate()

    printer.print(policyName, output)
}

private fun readTextProto(outDir: String): String? {
    val path = findPoliciesTextProtoFile(outDir)
    if (path == null) {
        return null
    }
    System.out.println("Found policies text proto file: ${path}")
    return path.toFile().readText()
}

private fun parsePolicies(textproto: String): Map<String, PolicyMetadata> {
    val policies = TextFormat.parse(textproto, PolicyMetadataList::class.java)
    return policies.policyMetadataList.associateBy { it.identifier.fieldName }
}

sealed interface Printer {

    fun print(policyName: String, content: String)

    class ToStdOut : Printer {
        override fun print(policyName: String, content: String) {
            println("-----------------------------------------------------------------------------")
            println("--- ${policyName}")
            println("-----------------------------------------------------------------------------")
            println(content)
        }
    }

    class ToFile(val outputDir: String, val allowOverride: Boolean, val format: Boolean) : Printer {
        override fun print(policyName: String, content: String) {
            val output_file_path = Path("$outputDir/${policyName.toCamelCase()}GeneratedTest.kt")

            if (output_file_path.exists() && !allowOverride) {
                System.err.println(
                    "Error: Output file ${output_file_path} exists. " +
                        "Use '--override' if you want to override it."
                )
                System.exit(7)
            }

            output_file_path.toFile().writeText(content)
            System.out.println("Wrote CTS tests to ${output_file_path}")

            if (format) {
                val process =
                    ProcessBuilder(*KTFMT_COMMAND, output_file_path.toString())
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                process.waitFor()
            }
        }
    }
}
