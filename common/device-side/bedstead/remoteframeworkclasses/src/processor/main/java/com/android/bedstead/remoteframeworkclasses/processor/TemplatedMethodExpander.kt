/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.bedstead.remoteframeworkclasses.processor.Processor.Api
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName

/*
 * Expand the templated input method into multiple concrete type-specific methods.
 *
 * Example:
 *  Input:
 *    <T> void setPolicy(PolicyIdentifier<T> key, T value)
 *  Output:
 *    void setPolicy_string(PolicyIdentifier<String> key, String value);
 *    void setPolicy_integer(PolicyIdentifier<Integer> key, Integer value);
 *    ... and more
 *
 * This class uses an allowlist to decide which templated methods to expand.
 * Methods not in the allowlist will be dropped (meaning `expand()` will return an empty list).
 *
 * This allowlist also contains the information on which type-specific methods must be created.
 *
 * Note that this code currently only supports templated input methods with a single templated
 * parameter, so it will not be able to handle `<V,T> void myFancyMethod(V value, T other);`
 */
class TemplatedMethodExpander(private val originalApi: Api) {

    private val originalMethod = originalApi.method

    fun expand(): List<Api> {
        var signature = MethodSignature.forMethodSpec(originalApi.method)
        var concreteTypes = supportedMethods.get(signature)
        if (concreteTypes == null) {
            // The templated method is not supported.
            return emptyList()
        }
        return concreteTypes
            .map { (suffix, type) -> generateConcreteMethod(suffix, type) }
            .toList()
    }

    private fun generateConcreteMethod(suffix: String, concreteType: TypeName): Api {
        val newName = "${originalMethod.name}_$suffix"
        return Api(
            MethodSpec.methodBuilder(newName)
                .addModifiers(originalMethod.modifiers)
                .addParameters(originalMethod.parameters.map { p -> fixParameter(p, concreteType) })
                .returns(fixType(originalMethod.returnType, concreteType))
                .addExceptions(originalMethod.exceptions.map { e -> fixType(e, concreteType) })
                .build(),
            originalApi.method,
            originalApi.isTestApi,
        )
    }

    private fun fixParameter(original: ParameterSpec, concreteType: TypeName): ParameterSpec {
        val newType = fixType(original.type, concreteType)
        return ParameterSpec.builder(newType, original.name)
            .addModifiers(original.modifiers)
            .build()
    }

    private fun fixType(originalType: TypeName, concreteType: TypeName): TypeName {
        return when (originalType) {
            is TypeVariableName ->
                // The type parameter is directly used, for example `T value`.
                concreteType
            is ParameterizedTypeName -> {
                // A class uses the type parameter, for example `Map<String, T> values`.
                val typeArgs =
                    originalType.typeArguments
                        .map { typeArg ->
                            when (typeArg) {
                                is TypeVariableName -> concreteType
                                else -> fixType(typeArg, concreteType)
                            }
                        }
                        .toTypedArray()
                ParameterizedTypeName.get(originalType.rawType, *typeArgs)
            }
            else -> originalType
        }
    }

    companion object {
        // Concrete types that are supported by the policy streamlining APIs.
        val policyStreamliningConcreteTypes: Map<String, TypeName> =
            mapOf(
                "string" to ClassName.get(String::class.java),
                "integer" to ClassName.get(java.lang.Integer::class.java),
                "long" to ClassName.get(java.lang.Long::class.java),
                "listOfString" to
                    ParameterizedTypeName.get(
                        ClassName.get(java.util.List::class.java),
                        ClassName.get(String::class.java),
                    ),
            )

        // Maps supported templated methods to the concrete types that must be generated for this
        // method.
        // The key for the map of concrete types is added as a suffix to the method name.
        val supportedMethods: Map<MethodSignature, Map<String, TypeName>> =
            mapOf(
                MethodSignature.forHardcoded(
                    "void",
                    "setPolicy",
                    listOf("android.app.admin.PolicyIdentifier<T>", "int", "T"),
                ) to policyStreamliningConcreteTypes,
                MethodSignature.forHardcoded(
                    "T",
                    "getPolicy",
                    listOf("android.app.admin.PolicyIdentifier<T>", "int"),
                ) to policyStreamliningConcreteTypes,
                MethodSignature.forHardcoded(
                    "T",
                    "getResolvedPerUserPolicy",
                    listOf("android.app.admin.PolicyIdentifier<T>"),
                ) to policyStreamliningConcreteTypes,
                MethodSignature.forHardcoded(
                    "T",
                    "getResolvedDeviceWidePolicy",
                    listOf("android.app.admin.PolicyIdentifier<T>"),
                ) to policyStreamliningConcreteTypes,
            )
    }
}
