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
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import java.util.Arrays
import java.util.Locale
import java.util.Objects
import java.util.stream.Collectors
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * Represents a minimal representation of a method for comparison purposes
 */
class MethodSignature(
    private val mVisibility: Visibility?,
    returnType: TypeMirror,
    val name: String,
    parameterTypes: MutableList<TypeMirror?>,
    exceptions: MutableSet<TypeMirror?>
) {
    enum class Visibility {
        PUBLIC, PROTECTED;

        companion object {
            fun ofMethod(method: ExecutableElement): Visibility {
                if (method.getModifiers().contains(Modifier.PUBLIC)) {
                    return Visibility.PUBLIC
                } else if (method.getModifiers().contains(Modifier.PROTECTED)) {
                    return Visibility.PROTECTED
                }

                throw IllegalArgumentException("Only public and protected are visible in APIs")
            }
        }
    }

    val returnType: String

    @JvmField
    val mParameterTypes: ImmutableList<String?>
    val mExceptions: ImmutableSet<String?>

    init {
        this.returnType = returnType.toString()
        mParameterTypes = ImmutableList.copyOf<String?>(
            parameterTypes.stream()
            .map<String?> { obj: TypeMirror? -> obj.toString() }
            .collect(Collectors.toList()))
        mExceptions =
            ImmutableSet.copyOf<String?>(
                exceptions.stream()
                    .map<String?> { obj: TypeMirror? -> obj.toString() }
                    .collect(
                        Collectors.toSet()
                    ))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is MethodSignature) return false
        val that = o
        return mVisibility == that.mVisibility &&
                this.returnType == that.returnType &&
                this.name == that.name &&
                mParameterTypes == that.mParameterTypes &&
                mExceptions == that.mExceptions
    }

    override fun hashCode(): Int {
        return Objects.hash(
            mVisibility,
            this.returnType,
            this.name,
            mParameterTypes,
            mExceptions
        )
    }

    override fun toString(): String {
        return ("MethodSignature{" +
                "mVisibility=" + mVisibility +
                ", mReturnType='" + this.returnType + '\'' +
                ", mName='" + this.name + '\'' +
                ", mParameterTypes=" + mParameterTypes +
                ", mExceptions=" + mExceptions + '}')
    }

    companion object {
        /** Create a [MethodSignature] for the given [ExecutableElement].  */
        @JvmStatic
        fun forMethod(method: ExecutableElement, elements: Elements): MethodSignature {
            val parameters =
                method.getParameters()
                    .stream()
                    .map<TypeMirror?> { obj: Element? -> obj!!.asType() }
                    .map<TypeMirror?> { m: TypeMirror? -> rawType(m, elements) }
                    .collect(Collectors.toList())

            val exceptions =
                method.getThrownTypes()
                    .stream()
                    .map<TypeMirror?> { m: TypeMirror? -> rawType(m, elements) }
                    .collect(Collectors.toSet())

            return MethodSignature(
                Visibility.Companion.ofMethod(method),
                MethodSignature.Companion.rawType(
                    method.getReturnType(),
                    elements
                )!!,
                method.getSimpleName().toString(),
                parameters,
                exceptions
            )
        }

        private fun rawType(type: TypeMirror?, elements: Elements): TypeMirror? {
            var type = type
            if (type is DeclaredType) {
                val t = type
                if (!t.getTypeArguments().isEmpty()) {
                    type = elements.getTypeElement(
                        t
                                .toString()
                                .split("<".toRegex(), limit = 2)
                                .toTypedArray()[0]
                    ).asType()
                }
            }
            return type
        }

        /**
         * Create a [MethodSignature] for the given string from an API file.
         */
        @JvmStatic
        fun forApiString(
            string: String,
            types: Types,
            elements: Elements
        ): MethodSignature? {
            var string = string
            try {
                // Strip annotations
                string = string.replace("@\\w+?\\(.+?\\) ".toRegex(), "")
                string = string.replace("@.+? ".toRegex(), "")

                var parts = string.split(" ".toRegex(), limit = 2).toTypedArray()
                val visibility: Visibility?
                try {
                    visibility = Visibility.valueOf(parts[0].uppercase(Locale.getDefault()))
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException("Error finding visibility in string " + string)
                }
                string = parts[1]
                parts = string.split(" ".toRegex(), limit = 2).toTypedArray()

                val returnType: TypeMirror
                while (parts[0] == "abstract" || parts[0] == "final" || parts[0] == "static") {
                    // These don't affect the signature in ways we care about
                    string = parts[1]
                    parts = string.split(" ".toRegex(), limit = 2).toTypedArray()
                }

                if (string.startsWith("<")) {
                    // This includes type arguments, for now we ignore this method
                    return null
                }

                returnType = typeForString(parts[0], types, elements)

                string = parts[1]
                parts = string.split("\\(".toRegex(), limit = 2).toTypedArray()
                val methodName = parts[0]
                string = parts[1]
                parts = string.split("\\)".toRegex(), limit = 2).toTypedArray()
                // Remove generic types as we don't need to care about them at this point
                var parametersString = parts[0].replace("<.*>".toRegex(), "")
                // Remove varargs
                parametersString = parametersString.replace("\\.\\.\\.".toRegex(), "")
                val parameters: MutableList<TypeMirror?>?
                try {
                    parameters = Arrays.stream<String?>(
                        parametersString.split(", ".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray())
                        .map<String?> { obj: String? -> obj!!.trim { it <= ' ' } }
                        .filter { t: String? -> !t!!.isEmpty() }
                        .map<TypeMirror?> { t: String? ->
                            Companion.typeForString(
                                t!!, types, elements
                            )
                        }
                        .collect(Collectors.toList())
                } catch (e: IllegalStateException) {
                    throw IllegalStateException(
                        "Error parsing types from string " + parametersString,
                        e
                    )
                }
                string = parts[1]
                var exceptions: MutableSet<TypeMirror?> = HashSet<TypeMirror?>()
                if (string.contains("throws")) {
                    exceptions = Arrays.stream<String?>(
                        string.split("throws ".toRegex(), limit = 2)
                        .toTypedArray()[1].split(",".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray())
                        .map<String?> { t: String? -> t!!.trim { it <= ' ' } }
                        .filter { t: String? -> !t!!.isEmpty() }
                        .map<TypeMirror?> { t: String? ->
                            Companion.typeForString(
                                t!!, types, elements
                            )
                        }
                        .collect(Collectors.toSet())
                }

                return MethodSignature(visibility, returnType, methodName, parameters, exceptions)
            } catch (e: Exception) {
                throw RuntimeException("TestApisReflection: unable to parse method: " + string, e)
            }
        }

        private fun typeForString(typeName: String, types: Types, elements: Elements): TypeMirror {
            var typeName = typeName
            if (typeName == "void") {
                return types.getNoType(TypeKind.VOID)
            }

            if (isTestClass(typeName, elements)) {
                // Use the proxy type instead
                typeName = proxyType(typeName)
            }

            if (typeName.contains("<")) {
                // Because of type erasure we can just drop the type argument
                return typeForString(
                    typeName.split("<".toRegex(), limit = 2).toTypedArray()[0],
                    types,
                    elements
                )
            }

            if (typeName.endsWith("[]")) {
                return types.getArrayType(
                    typeForString(typeName.substring(0, typeName.length - 2), types, elements)
                )
            }

            try {
                return types.getPrimitiveType(
                    TypeKind.valueOf(typeName.uppercase(Locale.getDefault()))
                )
            } catch (e: IllegalArgumentException) {
                // Not a primitive
            }

            var element = elements.getTypeElement(typeName)
            if (element == null) {
                // It could be java.lang
                element = elements.getTypeElement("java.lang." + typeName)
            }

            checkNotNull(element) { "Unknown type: " + typeName }

            return element.asType()
        }

        /**
         * A "Test Class" is a class marked as @TestApi at class level.
         *
         *
         * Since such classes are not available on environments where test sdk is disabled. We
         * identify them so that we can replace them with proxy classes generated by the
         * TestApisReflection module.
         */
        private fun isTestClass(typeName: String?, elements: Elements): Boolean {
            val isListedInTestCurrentFile =
                Processor.CLASSES_LISTED_IN_TEST_CURRENT_FILE.stream()
                    .anyMatch { c: ClassSignature? -> c!!.name == typeName }
            // wouldn't be accessible when test sdk is disabled.
            val isNotAccessible = elements.getTypeElement(typeName) == null
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
