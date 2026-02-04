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

import com.android.bedstead.testapis.parser.signatures.ClassSignature
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeItem
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

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
            visibility: Visibility,
            returnType: String,
            name: String,
            parameterTypes: List<String>,
        ): MethodSignature {
            return MethodSignature(visibility, returnType, name, parameterTypes, setOf())
        }

        /** Create a [MethodSignature] for the given [ExecutableElement]. */
        @JvmStatic
        fun forMethod(method: ExecutableElement, elementUtils: Elements): MethodSignature {
            val parameters = method.parameters.map { it.asType() }.map { rawType(it, elementUtils) }

            val exceptions = method.thrownTypes.map { rawType(it, elementUtils) }

            val visibility =
                when {
                    method.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
                    method.modifiers.contains(Modifier.PROTECTED) -> Visibility.PROTECTED
                    else ->
                        throw IllegalArgumentException("Method $method must be public or private")
                }

            val returnType = rawType(method.returnType, elementUtils = elementUtils)

            val name = method.simpleName.toString()

            return MethodSignature(visibility, returnType, name, parameters, exceptions.toSet())
        }

        private fun rawType(type: TypeMirror?, elementUtils: Elements): TypeMirror {
            var type = type
            if (type is DeclaredType) {
                val t = type
                if (!t.typeArguments.isEmpty()) {
                    type =
                        elementUtils
                            .getTypeElement(
                                t.toString().split("<".toRegex(), limit = 2).toTypedArray()[0]
                            )
                            .asType()
                }
            }
            return type!!
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

        // Bug-compatible with how `forMethod` handles templated arguments:
        //      List<String> -> List<V>.
        // Should eventually be replaced by simply `typeName.toString()`.
        private fun typeNameToString(typeName: TypeName): String {
            return when (typeName) {
                is ParameterizedTypeName -> {
                    val rawType = typeName.rawType.toString()
                    "$rawType<V>"
                }
                else -> typeName.toString()
            }
        }

        @JvmStatic
        fun forMethodSpec(method: MethodSpec): MethodSignature {
            val parameterTypes = method.parameters.map { typeNameToString(it.type) }
            val exceptionTypes = method.exceptions.map { typeNameToString(it) }.toSet()
            val returnType = typeNameToString(method.returnType)

            val visibility =
                when {
                    method.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
                    method.modifiers.contains(Modifier.PROTECTED) -> Visibility.PROTECTED
                    else ->
                        throw IllegalArgumentException("Method $method must be public or protected")
                }

            return MethodSignature(
                visibility,
                returnType,
                method.name,
                parameterTypes,
                exceptionTypes,
            )
        }

        @JvmStatic
        fun forApi(method: MethodItem, typeUtils: Types, elementUtils: Elements): MethodSignature? {
            // TODO(b/337769574): Add support for generic typeUtils.
            if (method.typeParameterList.isNotEmpty()) {
                return null
            }

            // TODO(b/337769574): Add support for var args.
            if (method.parameters().any { hasVarArgs(it.type()) }) {
                return null
            }

            val visibility: Visibility =
                when {
                    method.modifiers.isPublic() -> Visibility.PUBLIC
                    method.modifiers.isProtected() -> Visibility.PROTECTED
                    else ->
                        throw IllegalStateException("Method $method must be public or protected")
                }

            val name = method.name()

            val returnType = typeForApi(method.returnType(), typeUtils, elementUtils)
            val parameters =
                method.parameters().map { typeForApi(it.type(), typeUtils, elementUtils) }
            val throws = method.throwsTypes().map { typeForApi(it, typeUtils, elementUtils) }

            return MethodSignature(visibility, returnType, name, parameters, throws.toSet())
        }

        private fun typeForApi(type: PrimitiveTypeItem, typeUtils: Types): TypeMirror {
            val kind =
                when (type.kind) {
                    PrimitiveTypeItem.Primitive.BOOLEAN -> TypeKind.BOOLEAN
                    PrimitiveTypeItem.Primitive.BYTE -> TypeKind.BYTE
                    PrimitiveTypeItem.Primitive.CHAR -> TypeKind.CHAR
                    PrimitiveTypeItem.Primitive.DOUBLE -> TypeKind.DOUBLE
                    PrimitiveTypeItem.Primitive.FLOAT -> TypeKind.FLOAT
                    PrimitiveTypeItem.Primitive.INT -> TypeKind.INT
                    PrimitiveTypeItem.Primitive.LONG -> TypeKind.LONG
                    PrimitiveTypeItem.Primitive.SHORT -> TypeKind.SHORT
                    PrimitiveTypeItem.Primitive.VOID -> return typeUtils.getNoType(TypeKind.VOID)
                }

            return typeUtils.getPrimitiveType(kind)
        }

        private fun typeForApi(
            type: TypeItem,
            typeUtils: Types,
            elementUtils: Elements,
        ): TypeMirror {
            return when (type) {
                is PrimitiveTypeItem -> typeForApi(type, typeUtils)
                is ArrayTypeItem ->
                    typeUtils.getArrayType(typeForApi(type.componentType, typeUtils, elementUtils))
                is ReferenceTypeItem -> {
                    var typeName = type.toErasedTypeString()

                    if (isTestClass(typeName, elementUtils)) {
                        typeName = proxyType(typeName)
                    }

                    val typeElement = elementUtils.getTypeElement(typeName)
                    checkNotNull(typeElement) { "Unknown type: $typeName" }
                    typeElement.asType()
                }
                else -> throw IllegalArgumentException("Could not convert $type")
            }
        }

        /**
         * A "Test Class" is a class marked as @TestApi at class level.
         *
         * Since such classes are not available on environments where test sdk is disabled. We
         * identify them so that we can replace them with proxy classes generated by the
         * TestApisReflection module.
         */
        private fun isTestClass(typeName: String?, elementUtils: Elements): Boolean {
            val isListedInTestCurrentFile =
                Processor.CLASSES_LISTED_IN_TEST_CURRENT_FILE.stream().anyMatch { c: ClassSignature?
                    ->
                    c!!.name == typeName
                }
            // wouldn't be accessible when test sdk is disabled.
            val isNotAccessible = elementUtils.getTypeElement(typeName) == null
            return isListedInTestCurrentFile && isNotAccessible
        }

        private fun proxyType(typeName: String): String {
            val parts =
                typeName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val simpleName = StringBuilder()
            for (p in parts) {
                if (Character.isUpperCase(p.get(0))) {
                    simpleName.append(p)
                }
            }

            return Processor.TEST_APIS_REFLECTION_PACKAGE + "." + simpleName + "Proxy"
        }
    }
}
