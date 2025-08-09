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
package com.android.bedstead.testapis.parser

import com.android.bedstead.testapis.parser.signatures.ClassSignature
import com.android.bedstead.testapis.parser.signatures.ConstructorSignature
import com.android.bedstead.testapis.parser.signatures.MethodSignature
import com.android.bedstead.testapis.parser.signatures.PackageSignature
import com.google.common.io.Resources
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Deque
import java.util.LinkedList

/**
 * Helper class to parse `test-current.txt` and fetch TestApis.
 */
object TestApisParser {
    private const val API_FILE = "test-current.txt"
    private val API_TXT: String = initialiseApiTxts()

    private fun initialiseApiTxts(): String {
        try {
            return Resources.toString(
                TestApisParser::class.java.getResource("/apis/" + API_FILE),
                StandardCharsets.UTF_8
            )
        } catch (e: IOException) {
            throw IllegalStateException("Could not read file " + API_FILE)
        }
    }

    /**
     * Parse all TestApis into a `List` of [PackageSignature].
     */
    @JvmStatic
    fun parse(): MutableList<PackageSignature?> {
        val packageSignatures: MutableList<PackageSignature?> = ArrayList<PackageSignature?>()

        val lines = API_TXT.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val stack: Deque<Char?> = LinkedList<Char?>()

        var packageName: String? = null
        var className: String? = null
        var classSignatures: MutableList<ClassSignature?>? = null
        var constructorSignature: ConstructorSignature? = null
        var methodSignatures: MutableList<MethodSignature?>? = null

        // Marks a class to be ignored as it has an unhandled case
        // TODO(b/337769574): add support for generic types and enums
        var ignoringClass = false
        for (line in lines) {
            try {
                if (line.contains("enum ")) {
                    stack.addFirst('{')
                    continue
                }
                if (line.contains("enum_constant ")) {
                    continue
                }

                if (line.startsWith("package ")) {
                    stack.addFirst('{')

                    // package declaration
                    packageName =
                        line.replace("package".toRegex(), "")
                            .replace("\\{".toRegex(), "")
                            .trim { it <= ' ' }

                    classSignatures = ArrayList<ClassSignature?>()
                } else if (line.contains("class ")) {
                    stack.addFirst('{')

                    // class declaration
                    className =
                        packageName + "." + line.substring(line.indexOf("class ") + 6)
                            .split(" ".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()[0]

                    if (className.contains("<")) {
                        ignoringClass = true
                        continue
                    }

                    methodSignatures = ArrayList<MethodSignature?>()
                } else if (line.contains("interface ")) {
                    stack.addFirst('{')

                    // interface declaration
                    className =
                        packageName + "." + line.substring(line.indexOf("interface ") + 10)
                            .split(" ".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()[0]

                    if (className.contains("<")) {
                        ignoringClass = true
                        continue
                    }

                    methodSignatures = ArrayList<MethodSignature?>()
                } else if (line.contains("ctor ")) {
                    if (ignoringClass) {
                        continue
                    }

                    // We only care about public constructors for our case
                    if (line.contains("public ")) {
                        constructorSignature = ConstructorSignature.forString(className, line)
                    }
                } else if (line.contains("method ")) {
                    if (ignoringClass) {
                        continue
                    }

                    // We only care about non-abstract methods and methods that do not return
                    // generic types for our case
                    if (line.contains("public ") &&
                            !line.contains("abstract ") &&
                            !line.contains("?")) {
                        val methodSignature = MethodSignature.forApiString(
                            className,
                            line
                        )
                        if (!methodSignatures!!.contains(methodSignature)) {
                            methodSignatures.add(methodSignature)
                        }
                    }
                } else if (line.endsWith("}")) {
                    stack.removeFirst()

                    if (stack.isEmpty()) {
                        val packageSignature = PackageSignature(
                            packageName,
                            classSignatures
                        )
                        if (!packageSignatures.contains(packageSignature)) {
                            packageSignatures.add(packageSignature)
                        }
                    } else {
                        if (ignoringClass) {
                            ignoringClass = false
                            continue
                        }

                        val classSignature = ClassSignature(
                            packageName,
                            className,
                            constructorSignature,
                            methodSignatures
                        )

                        if (!classSignatures!!.contains(classSignature)) {
                            classSignatures.add(classSignature)
                        }

                        constructorSignature = null
                    }
                }
            } catch (e: NullPointerException) {
                println(
                    ("Invalid test-current.txt detected. Parsing test-current.txt to load " +
                            "TestApis failed for the line: '" + line + "'. Run update-apis.sh" +
                            " to reset the file.")
                )
            }
        }

        return packageSignatures
    }
}
