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
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import com.google.common.collect.ImmutableList
import com.google.common.io.Resources
import java.io.IOException
import java.nio.charset.StandardCharsets

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

    private fun isGetter(
        methodName: String,
        parameterTypes: List<String>
    ): Boolean {
        // We only consider methods with zero parameters as true 'getters' since this is to
        // convert them to a kotlin get property later on.
        if (!parameterTypes.isEmpty()) {
            return false
        }

        if (methodName.startsWith("get")) {
            return Character.isUpperCase(methodName[3])
        }
        if (methodName.startsWith("is")) {
            return Character.isUpperCase(methodName[2])
        }

        return false
    }

    private fun hasWildcard(item: TypeItem): Boolean {
        var foundWildcard = false
        val visitor = object : BaseTypeVisitor() {
           override fun visitWildcardType(wildcardType: WildcardTypeItem) {
                foundWildcard = true
           }
        }
        item.accept(visitor)

        return foundWildcard
    }

    private fun hasVarArgs(item: TypeItem): Boolean {
        var foundVarArgs = false
        val visitor = object : BaseTypeVisitor() {
            override fun visitArrayType(arrayType: ArrayTypeItem) {
                if (arrayType.isVarargs) {
                    foundVarArgs = true
                }
            }
        }
        item.accept(visitor)

        return foundVarArgs
    }

    /**
     * Parse all TestApis into a `List` of [PackageSignature].
     */
    @JvmStatic
    fun parse(): List<PackageSignature> {
        val signatureFile = SignatureFile.fromText(API_FILE, API_TXT)
        val codebase = ApiFile.parseApi(listOf(signatureFile))

        return codebase.getPackages().packages.map { pack ->
            val packageName = pack.qualifiedName()

            val classSignatures = pack.allClasses().mapNotNull classLoop@{ clazz ->
                // TODO(b/436548677): Just use fullName()
                // ClassSignature already contains the package, but maintaining compatibility.
                val className = packageName + "." + clazz.fullName()

                // TODO(b/337769574): Add support for generic types.
                if (clazz.typeParameterList.isNotEmpty()) {
                    return@classLoop null
                }

                val methods = clazz.methods().mapNotNull methodLoop@{ method ->
                    val methodName = method.name()

                    if (!method.modifiers.isPublic()) {
                        return@methodLoop null
                    }

                    if (method.modifiers.isAbstract()) {
                        return@methodLoop null
                    }

                    // TODO(b/337769574): Add support for wildcards.
                    if (hasWildcard(method.returnType())) {
                        return@methodLoop null
                    }

                    if (method.parameters().any {hasWildcard(it.type())}) {
                        return@methodLoop null
                    }

                    // TODO(b/337769574): Add support for var args.
                    if (method.parameters().any {hasVarArgs(it.type())}) {
                        return@methodLoop null
                    }

                    val parameterTypes = method.parameters().map{ it.type().toTypeString() }
                    val returnTypeString = method.returnType().toTypeString()
                    val returnType = MethodSignature.ReturnType(returnTypeString, null)
                    val isStatic = method.modifiers.isStatic()
                    val isGetter = isGetter(methodName, parameterTypes)

                    MethodSignature(
                        className,
                        methodName,
                        returnType,
                        ImmutableList.copyOf(parameterTypes),
                        isStatic,
                        isGetter
                    )
                }

                val constructor = clazz.constructors().firstOrNull { it.isPublic }
                val constructorSignature = constructor?.let {
                    val parameterTypes = constructor.parameters().map{ it.type().toTypeString() }

                    ConstructorSignature(className, ImmutableList.copyOf(parameterTypes))
                }

                ClassSignature(packageName, className, constructorSignature, methods)
            }.toList()

            PackageSignature(
                packageName,
                classSignatures
            )
        }
    }
}
