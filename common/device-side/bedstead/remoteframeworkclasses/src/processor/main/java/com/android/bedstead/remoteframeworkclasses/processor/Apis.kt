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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import com.google.common.io.Resources
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * A collection of [MethodSignature] for accessible methods.
 */
class Apis private constructor(private val mMethods: Set<MethodSignature>) {
    /**
     * Get methods in the API set.
     */
    fun methods(): Set<MethodSignature> {
        return mMethods
    }

    companion object {
        private val API_FILES = arrayOf<String>(
            "current.txt",
            "wifi-current.txt",
            "bluetooth-current.txt",
            "system-current.txt"
        )

        private val CODEBASE = initializeCodebase()

        private fun initializeCodebase(): Codebase {
            val apis = API_FILES.map {
                try {
                     it to Resources.toString(
                        Processor::class.java.getResource("/apis/$it"),
                        StandardCharsets.UTF_8
                    )
                } catch (e: IOException) {
                    throw IllegalStateException("Could not read file $it", e)
                }
            }.map { (name, content) ->
                SignatureFile.fromText(name, content)
            }

            return ApiFile.parseApi(apis)
        }

        /**
         * Get public and test APIs for a given class name.
         */
        @JvmStatic
        fun forClass(className: String, types: Types, elements: Elements): Apis {
            val parents: MutableSet<String> = HashSet()
            findParents(parents, className, elements)

            val methods: List<MethodSignature> = parents.flatMap {
                getMethodsForClass(it, types, elements)
            }

            return Apis(methods.toSet())
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

            val superClass = element.superclass
            if (superClass.kind != TypeKind.NONE) {
                findParents(parents, superClass.toString(), elements)
            }

            element.interfaces
                .map { obj: TypeMirror? -> obj.toString() }
                .forEach { c: String -> findParents(parents, c, elements) }
        }

        private fun getMethodsForClass(
            className: String,
            types: Types,
            elements: Elements
        ): List<MethodSignature> {
            val clazz = CODEBASE.findClass(className)

            if (clazz == null) {
                println("Failed to find $className")
                return listOf()
            }

            return clazz.methods().mapNotNull { method ->
                MethodSignature.forApi(method, types, elements)
            }
        }
    }
}
