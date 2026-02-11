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

public data class ParsedArguments(
    val override: Boolean,
    val help: Boolean,
    val stdout: Boolean,
    val all: Boolean,
    val format: Boolean,
    val policies: List<String>,
)

object ArgumentParser {
    fun parse(commandlineArguments: Array<String>): ParsedArguments {
        var override = false
        var help = false
        var stdout = false
        var all = false
        var format = true
        val policies = mutableListOf<String>()
        val remainingArguments = ArrayDeque(commandlineArguments.toList())

        while (!remainingArguments.isEmpty()) {
            val arg = remainingArguments.removeFirst()

            when (arg) {
                "-o",
                "--override" -> {
                    override = true
                }
                "-h",
                "--help" -> {
                    help = true
                }
                "-s",
                "--stdout" -> {
                    stdout = true
                }
                "-a",
                "--all" -> {
                    all = true
                }
                "--no-format",
                "-F" -> {
                    format = false
                }
                else -> {
                    if (arg.startsWith("-")) {
                        throw IllegalArgumentException("Unknown argument $arg")
                    }
                    policies.add(arg)
                }
            }
        }

        return ParsedArguments(
            override = override,
            help = help,
            stdout = stdout,
            policies = policies,
            all = all,
            format = format,
        )
    }

    fun help(): String {
        return """
                  Usage: $SCRIPT_NAME [--override] POLICY_NAME

                  Generates the CTS tests for the given policy.
                  The tests will be written to cts/tests/devicepolicy/src/android/devicepolicy/cts/generated/{PolicyName}GeneratedTest.kt .

                  Arguments:
                    -o/--override: Allows overriding a pre-existing output file.
                    -s/--stdout: Prints to stdout instead.
                    -a/--all: Generate CTS tests for all policies.
                    -F/--no-format: Do not format generated output file.
                    -h/--help: Print this help.
               """
            .trimIndent()
    }
}
