/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.remoteframeworkclasses.processor

import com.google.common.collect.ImmutableSet
import com.google.common.io.Resources
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.function.Function
import java.util.stream.Collectors
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * A collection of [MethodSignature] for accessible methods.
 */
class Apis private constructor(private val mMethods: ImmutableSet<MethodSignature?>?) {
    /**
     * Get methods in the API set.
     */
    fun methods(): ImmutableSet<MethodSignature?>? {
        return mMethods
    }

    companion object {
        private val API_FILES = arrayOf<String?>(
            "current.txt",
            "wifi-current.txt",
            "bluetooth-current.txt",
            "system-current.txt"
        )

        private val API_TXTS: MutableMap<String?, String?> = initialiseApiTxts()
        private val sPackageToApi: MutableMap<String?, Apis?> = HashMap<String?, Apis?>()

        private fun initialiseApiTxts(): MutableMap<String?, String?> {
            return Arrays.stream<String?>(API_FILES)
                .collect(
                    Collectors.toMap(
                        Function { f: String? -> f },
                        Function toMap@{ f: String? ->
                            try {
                                return@toMap Resources.toString(
                                    Processor::class.java.getResource("/apis/" + f),
                                    StandardCharsets.UTF_8
                                )
                            } catch (e: IOException) {
                                throw IllegalStateException("Could not read file " + f)
                            }
                        }
                    )
                )
        }

        /**
         * Get public and test APIs for a given class name.
         */
        @JvmStatic
        fun forClass(className: String, types: Types, elements: Elements): Apis? {
            if (sPackageToApi.containsKey(className)) {
                return sPackageToApi.get(className)
            }
            val methods = ImmutableSet.builder<MethodSignature?>()
            val parents: MutableSet<String> = HashSet<String>()
            findParents(parents, className, elements)
            for (c in parents) {
                for (apiTxt in API_TXTS.entries) {
                    methods.addAll(
                        Companion.parseApiTxt(apiTxt.key, apiTxt.value!!, c, types, elements)
                    )
                }
            }

            return Apis(methods.build())
        }

        private fun findParents(
            parents: MutableSet<String>,
            className: String,
            elements: Elements
        ) {
            parents.add(className)

            if (className == "java.lang.Object") {
                return
            }

            val element = elements.getTypeElement(className)
            println("Checking " + className + " got " + element)

            val superClass = element.getSuperclass()
            if (superClass.getKind() != TypeKind.NONE) {
                findParents(parents, superClass.toString(), elements)
            }

            element.getInterfaces()
                .stream()
                .map<String?> { obj: TypeMirror? -> obj.toString() }
                .forEach { c: String? -> Companion.findParents(parents, c!!, elements) }
        }

        private fun parseApiTxt(
            filename: String?,
            apiTxt: String,
            className: String,
            types: Types,
            elements: Elements
        ): MutableSet<MethodSignature?> {
            println("Parsing for " + className)

            val separatorPosition = className.lastIndexOf(".")
            val packageName = className.substring(0, separatorPosition)
            val simpleClassName = className.substring(separatorPosition + 1)

            val packageSplit: Array<String?> =
                apiTxt.split(("package " + packageName + " \\{").toRegex(), limit = 2)
                    .toTypedArray()
            if (packageSplit.size < 2) {
                println("Package " + packageName + " not in file " + filename)
                // Package not in this file
                return HashSet<MethodSignature?>()
            }
            val classSplit: Array<String?> = packageSplit[1]!!.split(
                ("class " + simpleClassName + " .*?\n").toRegex(),
                limit = 2
            ).toTypedArray()
            if (classSplit.size < 2) {
                println("Class " + simpleClassName + " not in file " + filename)
                // Class not in this file
                return HashSet<MethodSignature?>()
            }
            val lines =
                classSplit[1]!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val methodSignatures: MutableSet<MethodSignature?> = HashSet<MethodSignature?>()

            for (line in lines) {
                var methodLine = line.trim { it <= ' ' }
                if (methodLine.isEmpty()) {
                    continue
                }

                if (methodLine.startsWith("ctor")) {
                    // Skip constructors
                    continue
                }

                if (!methodLine.startsWith("method")) {
                    return methodSignatures
                }

                try {
                    // Strip "method" and semicolon
                    methodLine = methodLine.substring(7, methodLine.length - 1)
                    val signature = MethodSignature.forApiString(methodLine, types, elements)
                    if (signature != null) {
                        methodSignatures.add(signature)
                    }
                } catch (e: RuntimeException) {
                    throw IllegalStateException("Error parsing method " + line, e)
                }
            }

            return methodSignatures
        }
    }
}
