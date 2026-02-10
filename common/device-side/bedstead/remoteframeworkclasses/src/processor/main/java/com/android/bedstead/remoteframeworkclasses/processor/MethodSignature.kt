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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.TypeItem
import com.squareup.javapoet.MethodSpec
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements

/** Represents a minimal representation of a method for comparison purposes */
data class MethodSignature(
    val visibility: Visibility,
    val returnType: String,
    val name: String,
    val parameterTypes: List<String>,
    val exceptions: Set<String>,
) {
    constructor(
        visibility: Visibility,
        returnType: TypeMirror,
        name: String,
        parameterTypes: List<TypeMirror>,
        exceptions: Set<TypeMirror>,
    ) : this(
        visibility,
        returnType.toString(),
        name,
        parameterTypes.map { it.toString() },
        exceptions.map { it.toString() }.toSet(),
    )

    public enum class Visibility {
        PUBLIC,
        PROTECTED,
        PRIVATE,
    }

    companion object {
        @JvmStatic
        fun forHardcoded(
            visibility: Visibility,
            returnType: String,
            name: String,
            parameterTypes: List<String>,
            exceptions: Set<String>,
        ): MethodSignature {
            return MethodSignature(visibility, returnType, name, parameterTypes, exceptions)
        }

        @JvmStatic
        fun forHardcoded(
            returnType: String,
            name: String,
            parameterTypes: List<String>,
        ): MethodSignature {
            return forHardcoded(Visibility.PUBLIC, returnType, name, parameterTypes, setOf())
        }

        @JvmStatic
        fun forHardcoded(
            visibility: Visibility,
            returnType: String,
            name: String,
            parameterTypes: List<String>,
        ): MethodSignature {
            return forHardcoded(visibility, returnType, name, parameterTypes, setOf())
        }

        /** Create a [MethodSignature] for the given [ExecutableElement]. */
        @JvmStatic
        fun forMethod(method: ExecutableElement, elementUtils: Elements): MethodSignature {
            val parameters = method.parameters.map { it.asType() }
            val exceptions = method.thrownTypes
            val name = method.simpleName.toString()

            return MethodSignature(
                getVisibility(method.modifiers),
                method.returnType,
                name,
                parameters,
                exceptions.toSet(),
            )
        }

        private fun hasVarArgs(item: TypeItem): Boolean {
            var foundVarArgs = false
            val visitor =
                object : BaseTypeVisitor() {
                    override fun visitArrayType(arrayType: ArrayTypeItem) {
                        if (arrayType.isVarargs) {
                            foundVarArgs = true
                        }
                    }
                }
            item.accept(visitor)

            return foundVarArgs
        }

        @JvmStatic
        fun forMethodSpec(method: MethodSpec): MethodSignature {
            val parameterTypes = method.parameters.map { it.type.toString() }
            val exceptionTypes = method.exceptions.map { it.toString() }.toSet()
            val returnType = method.returnType.toString()

            return MethodSignature(
                getVisibility(method.modifiers),
                returnType,
                method.name,
                parameterTypes,
                exceptionTypes,
            )
        }

        @JvmStatic
        fun forApi(method: MethodItem, elementUtils: Elements): MethodSignature? {
            // TODO(b/337769574): Add support for var args.
            if (method.parameters().any { hasVarArgs(it.type()) }) {
                return null
            }

            return MethodSignature(
                getVisibility(method.modifiers),
                method.returnType().toTypeString(),
                method.name(),
                method.parameters().map { it.type().toTypeString() },
                method.throwsTypes().map { it.toTypeString() }.toSet(),
            )
        }

        private fun getVisibility(modifiers: ModifierList) =
            when {
                modifiers.isPublic() -> Visibility.PUBLIC
                modifiers.isProtected() -> Visibility.PROTECTED
                modifiers.isPrivate() -> Visibility.PRIVATE
                else -> Visibility.PUBLIC
            }

        private fun getVisibility(modifiers: Set<Modifier>) =
            when {
                modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
                modifiers.contains(Modifier.PROTECTED) -> Visibility.PROTECTED
                modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
                else -> Visibility.PUBLIC
            }
    }
}
