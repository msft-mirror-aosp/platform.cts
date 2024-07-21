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

package com.android.bedstead.testapisreflection.processor;

import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.TypeName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A utility class for managing data types.
 */
public final class TypeUtils {

    private TypeUtils() {}

    private static final Map<String, String> sJavaToKotlinDataTypesMap = new HashMap<>();

    static {
        sJavaToKotlinDataTypesMap.put("byte", "Byte");
        sJavaToKotlinDataTypesMap.put("short", "Short");
        sJavaToKotlinDataTypesMap.put("long", "Long");
        sJavaToKotlinDataTypesMap.put("int", "Int");
        sJavaToKotlinDataTypesMap.put("char", "Char");
        sJavaToKotlinDataTypesMap.put("float", "Float");
        sJavaToKotlinDataTypesMap.put("double", "Double");
        sJavaToKotlinDataTypesMap.put("boolean", "Boolean");
        sJavaToKotlinDataTypesMap.put("java.lang.String", "String");
        sJavaToKotlinDataTypesMap.put("byte[]", "ByteArray");
        sJavaToKotlinDataTypesMap.put("short[]", "ShortArray");
        sJavaToKotlinDataTypesMap.put("long[]", "LongArray");
        sJavaToKotlinDataTypesMap.put("int[]", "IntArray");
        sJavaToKotlinDataTypesMap.put("char[]", "CharArray");
        sJavaToKotlinDataTypesMap.put("float[]", "FloatArray");
        sJavaToKotlinDataTypesMap.put("double[]", "DoubleArray");
        sJavaToKotlinDataTypesMap.put("boolean[]", "BooleanArray");
        sJavaToKotlinDataTypesMap.put("java.lang.String[]", "Array<String>");
    }

    static TypeName convertToKotlinCompatibleType(TypeMirror typeMirror,
            boolean returnNonParameterizedType) {
        if (typeMirror.getKind() == TypeKind.VOID) {
            return null;
        }

        String type = typeMirror.toString();
        if (sJavaToKotlinDataTypesMap.containsKey(type)) {
            String kotlinType = sJavaToKotlinDataTypesMap.get(type);

            if (kotlinType.contains("<")) {
                // if kotlin type is a parameterized type
                String rawType = "kotlin." + kotlinType.substring(0, kotlinType.indexOf("<"));
                String wrappedType =
                        "kotlin." + kotlinType.substring(
                                kotlinType.indexOf("<") + 1, kotlinType.indexOf(">"));
                return getDeclaredType(rawType, wrappedType,
                        /* returnNonParameterizedType= */ false);
            }

            return new ClassName("kotlin", kotlinType);
        } else if (typeMirror instanceof DeclaredType) {
            String wrappedTypeQualifiedName = typeQualifiedName(extractTypeArgument(typeMirror));

            if (wrappedTypeQualifiedName != null) {
                // if it is a parameterized type
                return getDeclaredType(type, wrappedTypeQualifiedName, returnNonParameterizedType);
            }

        } else if (typeMirror instanceof ArrayType) {
            return getDeclaredType(
                    /* type= */ "kotlin.Array",
                    /* wrappedTypeQualifiedName= */ type.substring(0, type.length() - 2),
                    /* returnNonParameterizedType= */ false);
        }

        return new ClassName(typePackageName(type), typeSimpleName(type));
    }

    static TypeName getDeclaredType(String type, String wrappedTypeQualifiedName,
            boolean returnNonParameterizedType) {
        String rawTypeQualifiedName = typeQualifiedName(type);
        String rawTypePackage = typePackageName(rawTypeQualifiedName);
        String rawType = typeSimpleName(rawTypeQualifiedName);

        if (returnNonParameterizedType) {
            return new ClassName(rawTypePackage, rawType);
        }

        return ParameterizedTypeName.get(new ClassName(rawTypePackage, rawType),
                Collections.singletonList(
                        new ClassName(typePackageName(wrappedTypeQualifiedName),
                                typeSimpleName(wrappedTypeQualifiedName))));
    }

    static TypeMirror typeForString(String typeName, Types types, Elements elements) {
        if (typeName.equals("void")) {
            return types.getNoType(TypeKind.VOID);
        }

        if (typeName.contains("<")) {
            TypeElement element = elements.getTypeElement(typeName.split("<", 2)[0]);
            TypeMirror typeArgs = typeForString(typeName.substring(
                    typeName.indexOf("<") + 1, typeName.indexOf(">")), types, elements);

            return types.getDeclaredType(element, typeArgs);
        }

        if (typeName.endsWith("[]")) {
            return types.getArrayType(
                    typeForString(typeName.substring(0, typeName.length() - 2), types, elements));
        }

        try {
            return types.getPrimitiveType(TypeKind.valueOf(typeName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            // Not a primitive
        }

        TypeElement element = elements.getTypeElement(typeName);
        if (element == null) {
            // It could be java.lang
            element = elements.getTypeElement("java.lang." + typeName);
        }

        if (element == null) {
            throw new IllegalStateException("Unknown type: " + typeName);
        }

        return element.asType();
    }

    static String typeQualifiedName(TypeMirror typeMirror) {
        if (typeMirror == null) {
            return null;
        }

        return typeQualifiedName(typeMirror.toString());
    }

    static String typeQualifiedName(String type) {
        // Change java package name to valid kotlin package
        if (type.startsWith("java.util")) {
            type = type.replace("java.util", "kotlin.collections");
        } else if (type.startsWith("java.lang")) {
            type = type.replace("java.lang", "kotlin");
        }

        // If type is parameterized, erase parameter, for e.g. kolin.collections.List<String>
        // would be converted into kotlin.collections.List
        return type.split("<", 2)[0];
    }

    static String typePackageName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf("."));
    }

    static String typeSimpleName(String clazz) {
        return clazz.substring(clazz.lastIndexOf(".") + 1);
    }

    static String parameterizedTypeWrapperName(String clazz) {
        return clazz.replaceAll("<", "")
                .replaceAll(">", "")
                .substring(clazz.lastIndexOf("."));
    }

    static boolean isParameterizedType(String type) {
        return type.contains("<");
    }

    private static TypeMirror extractTypeArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }

        if (((DeclaredType) type).getTypeArguments().isEmpty()) {
            return null;
        }

        return ((DeclaredType) type).getTypeArguments().get(0);
    }

}
