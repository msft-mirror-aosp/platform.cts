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

package com.android.bedstead.testapisreflection.processor;

import static com.android.bedstead.testapisreflection.processor.TypeUtils.convertToKotlinCompatibleType;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.getDeclaredType;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.isParameterizedType;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.parameterizedTypeWrapperName;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.typeForString;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.typePackageName;
import static com.android.bedstead.testapisreflection.processor.TypeUtils.typeSimpleName;

import com.android.bedstead.testapisreflection.processor.annotations.TestApisReflectionTrigger;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.squareup.kotlinpoet.AnnotationSpec;
import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.CodeBlock;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.FunSpec;
import com.squareup.kotlinpoet.KModifier;
import com.squareup.kotlinpoet.ParameterSpec;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.PropertySpec;
import com.squareup.kotlinpoet.TypeName;
import com.squareup.kotlinpoet.TypeSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import kotlin.Suppress;

/**
 * Processor for generating {@code TestApisReflection} class.
 *
 * <p>This is started by including the {@link TestApisReflectionTrigger} annotation.
 *
 * <p>Any Test API that needs to be accessed through reflection should be added to
 * {@code ALLOWLISTED_TEST_METHODS}.
 *
 * <p>Any class that has @TestApi annotation marked at class level for e.g.
 * {@code android.content.pm.UserInfo} and that needs to be accessed
 * through reflection should be added to {@code ALLOWLISTED_TEST_CLASSES} with the api name added
 * to {@code ALLOWLISTED_TEST_METHODS} as well.
 *
 * <p>Any field that is part of an {@code ALLOWLISTED_TEST_CLASS} and that needs to be accessed
 * through reflection should be added to {@code ALLOWLISTED_TEST_FIELDS} for e.g.
 * {@code android.content.pm.UserInfo#id}.
 *
 * <p>For each entry in {@code ALLOWLISTED_TEST_METHODS} the processor will generate a method
 * in {@code android.cts.testapisreflection.TestApisReflection} that allows the user to access the
 * Test API through reflection.
 *
 * <p>For each entry in {@code ALLOWLISTED_TEST_CLASSES} the processor will generate a proxy class
 * which enables access to all methods listed in {@code ALLOWLISTED_TEST_METHODS}.
 *
 * <p><b>Usage:</b>
 *
 * <li> Any method marked as @TestApi at method level can be accessed using the
 * {@code TestApisReflection} kotlin class.
 *
 * <li> Any method that is a member of a class that is marked as @TestApi at class level can be
 * accessed through its respective Proxy class. For e.g. all methods inside
 * {@code android.content.pm.UserInfo} can be accessed using
 * {@code android.cts.testapisreflection.UserInfoProxy}.
 *
 * <li> If the TestApisReflection file is accessed from a java class:
 * <ul>
 *  <li> import android.cts.testapisreflection.TestApisReflectionKt;
 *  <li> Access the method using TestApisReflectionKt.method_name(receiver_object, args…)
 *  <li> Example: {@code android.service.quicksettings.TileService#isQuickSettingsSupported} is a
 *  TestApi and can be accessed using
 *  {@code TestApisReflectionKt.isQuickSettingsSupported(tileServiceInstance)} using the import line
 *  below.
 * </ul>
 *
 * <li> If the TestApisReflection file is accessed from a kotlin class, add the below import line
 * to access any TestApi method normally.
 * <ul>
 *  <li> {@code import android.cts.testspisreflection.*}
 *  <li> Example: {@code android.service.quicksettings.TileService#isQuickSettingsSupported} is
 *   a TestApi and can be accessed using {@code tileService.isQuickSettingsSupported()} using the
 *   mentioned import line.
 * </ul>
 *
 * <b> Note: Generated proxy classes should not be exposed outside of bedstead and must always be
 * hidden and wrapped by Bedstead-specific classes.
 */
@SupportedAnnotationTypes({
        "com.android.bedstead.testapisreflection.processor.annotations.TestApisReflectionTrigger",
})
@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

    private static final ImmutableSet<String> ALLOWLISTED_TEST_METHODS =
            loadList("/apis/allowlisted-test-methods.txt");
    private static final ImmutableSet<String> ALLOWLISTED_TEST_CLASSES =
            loadList("/apis/allowlisted-test-classes.txt");
    private static final ImmutableSet<String> ALLOWLISTED_TEST_FIELDS =
            loadList("/apis/allowlisted-test-fields.txt");
    private static final String PACKAGE_NAME = "android.cts.testapisreflection";
    private static final String FILE_NAME = "TestApisReflection";
    private static final Map<String, String> SERVICES_ALIAS = ImmutableMap.of(
            "android.app.DreamManager", "dream",
            "android.app.ActivityTaskManager", "activity_task"
    );

    private static ImmutableSet<String> loadList(String filename) {
        try {
            return ImmutableSet.copyOf(Resources.toString(
                    Processor.class.getResource(filename),
                    StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file", e);
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.getElementsAnnotatedWith(TestApisReflectionTrigger.class).isEmpty()) {
            return true;
        }

        Set<MethodSignature> allowlistedTestMethods = ALLOWLISTED_TEST_METHODS.stream()
                .map(MethodSignature::forApiString)
                .filter(t -> !ALLOWLISTED_TEST_CLASSES.contains(t.getFrameworkClass()))
                .collect(Collectors.toUnmodifiableSet());

        FileSpec.Builder reflectionFileBuilder = FileSpec.builder(PACKAGE_NAME, FILE_NAME);

        generateMethods(reflectionFileBuilder, allowlistedTestMethods);

        generatedMethodsForTestApiClasses();

        FileSpec reflectionFile = reflectionFileBuilder.build();
        try {
            reflectionFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new IllegalStateException("Error writing TestApisReflection to file", e);
        }

        return true;
    }

    private void generateMethods(FileSpec.Builder classBuilder, Set<MethodSignature> testMethods) {
        for (MethodSignature method : testMethods) {
            TypeName returnTypeName = returnTypeOrProxy(method);

            ClassName receiverClass = new ClassName(typePackageName(method.getFrameworkClass()),
                    typeSimpleName(method.getFrameworkClass()));

            if (method.isGetter()) {
                generateGetterProperty(classBuilder, method, receiverClass, returnTypeName);
            } else {
                FunSpec.Builder reflectMethodBuilder = FunSpec.builder(method.getName())
                        .addModifiers(KModifier.PUBLIC)
                        .receiver(receiverClass)
                        .addAnnotation(AnnotationSpec.builder(Suppress.class)
                                .addMember("%S", "UNCHECKED_CAST")
                                .build());
                if (returnTypeName != null) {
                    reflectMethodBuilder.returns(returnTypeName);
                }

                String paramNames = null;
                String paramTypes = null;
                if (!method.getParameterTypes().isEmpty()) {
                    String[] parametersInfo = buildParametersCode(method, reflectMethodBuilder);
                    paramNames = parametersInfo[0];
                    paramTypes = parametersInfo[1];
                }

                reflectMethodBuilder.addCode(
                        buildReflectionMethodCode(reflectMethodBuilder, receiverClass,
                                method.getName(), returnTypeName,
                                /* isReturnTypeProxy= */
                                method.getReturnType().getProxyType() != null,
                                method.isStatic(), paramTypes, paramNames)
                                .build());
                classBuilder.addFunction(reflectMethodBuilder.build());
            }
        }
    }

    /**
     * Generate methods for framework classes that have @TestApi annotation at class level,
     * for e.g. android.content.pm.UserInfo
     */
    private void generatedMethodsForTestApiClasses() {
        for (String testApiClass : ALLOWLISTED_TEST_CLASSES) {
            String testApiClassSimpleName = typeSimpleName(testApiClass);
            String proxyClassName = testApiClassSimpleName + "Proxy";

            TypeSpec.Builder proxyClassBuilder = TypeSpec.classBuilder(proxyClassName)
                    .addSuperinterface(new ClassName("android.os", "Parcelable"),
                            CodeBlock.builder().build())
                    .primaryConstructor(FunSpec.constructorBuilder().build())
                    .addModifiers(KModifier.OPEN);

            Set<FieldSignature> allowlistedTestFields = getTestFieldsForClass(
                    testApiClassSimpleName);

            // Every newly created proxy class must implement Parcelable
            implementParcelable(proxyClassBuilder, testApiClassSimpleName, proxyClassName,
                    allowlistedTestFields);

            Set<MethodSignature> testMethods = ALLOWLISTED_TEST_METHODS.stream()
                    .map(MethodSignature::forApiString)
                    .filter(t -> t.getFrameworkClass().equals(testApiClass))
                    .collect(Collectors.toUnmodifiableSet());
            for (MethodSignature method : testMethods) {
                FunSpec.Builder methodBuilder = FunSpec.builder(method.getName())
                        .addModifiers(KModifier.PUBLIC);

                TypeName returnTypeName = returnTypeOrProxy(method);
                if (returnTypeName != null) {
                    methodBuilder.returns(returnTypeName);
                }

                String[] parametersInfo = buildParametersCode(method, methodBuilder);
                String paramNames = parametersInfo[0];
                String paramTypes = parametersInfo[1];

                CodeBlock.Builder codeBuilder = CodeBlock.builder();

                beginTryBlock(codeBuilder);

                if (SERVICES_ALIAS.containsKey(testApiClass)) {
                    // if TestApi is a service class
                    codeBuilder
                            .addStatement("val clazz = Class.forName(%S)", testApiClass)
                            .addStatement(
                                    "val obj = androidx.test.platform.app."
                                            + "InstrumentationRegistry.getInstrumentation()"
                                            + ".getTargetContext().getSystemService(%S)",
                                    SERVICES_ALIAS.get(testApiClass));
                } else {
                    codeBuilder
                            .addStatement("val clazz = Class.forName(%S)", testApiClass)
                            .addStatement("val pc: java.lang.reflect.Constructor<*> = "
                                    + "clazz.getDeclaredConstructor()")
                            .addStatement("pc.setAccessible(true)")
                            .addStatement("val obj = pc.newInstance()");
                }

                for (FieldSignature field : allowlistedTestFields) {
                    codeBuilder.addStatement(
                            "clazz.getField(%S).set(obj, this.%L)",
                            field.getName(), field.getName());
                }

                if (returnTypeName != null) {
                    if (paramNames != null) {
                        codeBuilder.addStatement(
                                "return clazz.getMethod(%S, %L).invoke("
                                        + "obj, %L) as %L", method.getName(), paramTypes,
                                paramNames, returnTypeName);
                    } else {
                        codeBuilder.addStatement(
                                "return clazz.getMethod(%S).invoke(obj) as %L",
                                method.getName(), returnTypeName);
                    }
                } else {
                    if (paramNames != null) {
                        codeBuilder.addStatement(
                                "clazz.getMethod(%S, %L).invoke(obj, %L)",
                                method.getName(), paramTypes, paramNames);
                    } else {
                        codeBuilder.addStatement(
                                        "clazz.getMethod(%S).invoke(obj)",
                                        method.getName());
                    }
                }

                closeTryBlockAndBuildCatchBlock(codeBuilder);

                methodBuilder.addCode(codeBuilder.build());

                proxyClassBuilder.addFunction(methodBuilder.build());
            }

            FileSpec.Builder proxyClassFileBuilder = FileSpec.builder(PACKAGE_NAME,
                    proxyClassName).addType(proxyClassBuilder.build());
            FileSpec proxyClassFile = proxyClassFileBuilder.build();

            try {
                proxyClassFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "TestApisReflection: Error writing proxy file for " + testApiClass, e);
            }
        }
    }

    private void generateGetterProperty(FileSpec.Builder classBuilder, MethodSignature method,
            ClassName receiverClass, TypeName returnTypeName) {
        CodeBlock.Builder getterCodeBuilder = CodeBlock.builder();
        boolean isReturnTypeProxy = method.getReturnType().getProxyType() != null;
        String classReference = method.isStatic() ? "null" : "this";

        if (isReturnTypeProxy) {
            getterCodeBuilder.addStatement("val obj = %L::class.java.getMethod(%S).invoke(%L)",
                    receiverClass, method.getName(), classReference);

            buildReturnTypeProxyCode(getterCodeBuilder, returnTypeName);
        } else {
            getterCodeBuilder.addStatement(
                    "return %L::class.java.getMethod(%S).invoke(%L) as %L",
                    receiverClass, method.getName(), classReference, returnTypeName);
        }

        classBuilder.addProperty(
                PropertySpec.builder(removeGetPrefix(method.getName()), returnTypeName)
                        .receiver(receiverClass)
                        .getter(FunSpec.getterBuilder()
                                .addCode(getterCodeBuilder.build()).build())
                        .build());
    }

    private CodeBlock.Builder buildReflectionMethodCode(FunSpec.Builder methodBuilder,
            ClassName receiverClassName, String methodName, TypeName returnTypeName,
            boolean isReturnTypeProxy, boolean isStatic, String paramTypes, String paramNames) {
        CodeBlock.Builder codeBuilder = CodeBlock.builder();

        beginTryBlock(codeBuilder);

        String classReference = isStatic ? "null" : "this";

        if (methodBuilder.getParameters().isEmpty()) {
            if (returnTypeName == null) {
                codeBuilder.addStatement(
                        "%L::class.java.getMethod(%S).invoke(%L)", receiverClassName,
                        methodName, classReference);
            } else {
                if (isReturnTypeProxy) {
                    codeBuilder.addStatement("val obj = %L::class.java.getMethod(%S).invoke(%L)",
                            receiverClassName, methodName, classReference);

                    buildReturnTypeProxyCode(codeBuilder, returnTypeName);
                } else {
                    codeBuilder.addStatement(
                            "return %L::class.java.getMethod(%S).invoke(%L) as %L",
                            receiverClassName, methodName, classReference, returnTypeName);
                }
            }
        } else {
            if (returnTypeName == null) {
                codeBuilder.addStatement(
                        "%L::class.java.getMethod(%S, %L).invoke(%L, %L)", receiverClassName,
                        methodName, paramTypes, classReference, paramNames);
            } else {
                if (isReturnTypeProxy) {
                    codeBuilder.addStatement(
                            "val obj = %L::class.java.getMethod(%S, %L).invoke(%L, %L)",
                            receiverClassName, methodName, paramTypes, classReference, paramNames);

                    buildReturnTypeProxyCode(codeBuilder, returnTypeName);
                } else {
                    codeBuilder.addStatement(
                            "return %L::class.java.getMethod(%S, %L).invoke(%L, %L) as %L",
                            receiverClassName, methodName, paramTypes, classReference, paramNames,
                            returnTypeName);
                }
            }
        }

        closeTryBlockAndBuildCatchBlock(codeBuilder);

        return codeBuilder;
    }

    private void implementParcelable(TypeSpec.Builder proxyClassBuilder, String testApiClassName,
            String proxyClassName, Set<FieldSignature> allowlistedTestFields) {
        FunSpec.Builder parcelConstructorBuilder = FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(new ParameterSpec("source",
                        new ClassName("android.os", "Parcel")))
                .callThisConstructor(CodeBlock.builder().build());

        TypeName intTypeName = convertToKotlinCompatibleType(typeForString("int",
                        processingEnv.getTypeUtils(), processingEnv.getElementUtils()),
                /* returnNonParameterizedType= */ true);

        // Create describeContents method
        proxyClassBuilder.addFunction(FunSpec.builder("describeContents")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addCode(CodeBlock.builder().addStatement("return 0").build())
                .returns(intTypeName)
                .build());

        // Create writeToParcel method
        List<ParameterSpec> writeToParcelParameters = new ArrayList<>();
        writeToParcelParameters.add(new ParameterSpec("dest",
                new ClassName("android.os", "Parcel")));
        writeToParcelParameters.add(new ParameterSpec("flags", intTypeName));

        FunSpec.Builder writeToParcelMethod = FunSpec.builder("writeToParcel")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameters(writeToParcelParameters);

        for (FieldSignature field : allowlistedTestFields) {
            TypeName fieldTypeName = convertToKotlinCompatibleType(field.getType(),
                    /* returnNonParameterizedType= */ false);

            proxyClassBuilder.addProperty(
                    PropertySpec.builder(field.getName(),
                                    fieldTypeName.copy(true, Collections.emptyList()))
                            .addAnnotation(AnnotationSpec.builder(
                                            new ClassName("kotlin.jvm", "JvmField"))
                                    .build())
                            .mutable(true)
                            .initializer("null")
                            .build());

            switch (fieldTypeName.toString()) {
                case "kotlin.Int":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeInt(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder().addStatement("%L = source.readInt()",
                                    field.getName()).build());
                    break;
                case "kotlin.Long":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeLong(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder().addStatement("%L = source.readLong()",
                                    field.getName()).build());
                    break;
                case "kotlin.String":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeString(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder()
                                    .addStatement("%L = source.readString()", field.getName())
                                    .build());
                    break;
                case "kotlin.Boolean":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeBoolean(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder()
                                    .addStatement("%L = source.readBoolean()", field.getName())
                                    .build());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "TestApisReflection: Generating Parcel constructor and "
                                    + "writeToParcel method for "
                                    + testApiClassName
                                    + ". Data type "
                                    + fieldTypeName
                                    + " is not implemented yet.");
            }
        }
        proxyClassBuilder.addFunction(writeToParcelMethod.build());
        proxyClassBuilder.addFunction(parcelConstructorBuilder.build());

        // Create static field CREATOR
        TypeName nullableProxyClass = new ClassName(PACKAGE_NAME, proxyClassName)
                .copy(true, Collections.emptyList());
        TypeSpec parcelableCreator = TypeSpec.classBuilder("ParcelableCreator")
                .addModifiers(KModifier.OPEN)
                .addSuperinterface(
                        ParameterizedTypeName.get(
                                new ClassName("android.os.Parcelable", "Creator"),
                                Collections.singletonList(nullableProxyClass)),
                        CodeBlock.builder().build())
                .addFunction(FunSpec.builder("createFromParcel")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(new ParameterSpec("arg0",
                                new ClassName("android.os", "Parcel")))
                        .returns(nullableProxyClass)
                        .addCode(CodeBlock.builder()
                                .addStatement("return %L(arg0)", proxyClassName).build())
                        .build())
                .addFunction(FunSpec.builder("newArray")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(new ParameterSpec("size",
                                new ClassName("kotlin", "Int")))
                        .returns(ParameterizedTypeName.get(
                                new ClassName("kotlin", "Array"),
                                Collections.singletonList(nullableProxyClass)))
                        .addCode(CodeBlock.builder()
                                .addStatement("return arrayOfNulls(size)", proxyClassName)
                                .build())
                        .build())
                .build();
        proxyClassBuilder.addType(parcelableCreator);
        proxyClassBuilder.addType(TypeSpec.companionObjectBuilder("CREATOR")
                .superclass(
                        new ClassName(PACKAGE_NAME + "." + proxyClassName, "ParcelableCreator"))
                .build());
    }

    private String[] buildParametersCode(MethodSignature method,
            FunSpec.Builder methodBuilder) {
        StringBuilder parameterNamesBuilder = new StringBuilder();
        StringBuilder parameterTypesBuilder = new StringBuilder();
        int count = 0;

        for (String paramType : method.getParameterTypes()) {
            TypeMirror parameterTypeMirror = typeForString(paramType,
                    processingEnv.getTypeUtils(), processingEnv.getElementUtils());

            TypeName parameterTypeName = convertToKotlinCompatibleType(parameterTypeMirror,
                    /* returnNonParameterizedType= */ false);

            String paramName = "arg" + count++;
            ParameterSpec parameterSpec = new ParameterSpec(paramName, parameterTypeName);
            methodBuilder.addParameter(parameterSpec);

            TypeName reflectMethodParameterType = convertToKotlinCompatibleType(
                    parameterTypeMirror, /* returnNonParameterizedType= */ true);
            parameterTypesBuilder.append(reflectMethodParameterType).append("::class.java, ");
            parameterNamesBuilder.append(paramName).append(", ");
        }

        String parameterNamesConcatenated = null, parameterTypesConcatenated = null;
        if (!method.getParameterTypes().isEmpty()) {
            parameterNamesConcatenated = parameterNamesBuilder.substring(
                    0, parameterNamesBuilder.toString().length() - 2);
            parameterTypesConcatenated = parameterTypesBuilder.substring(
                    0, parameterTypesBuilder.toString().length() - 2);
        }

        return new String[]{parameterNamesConcatenated, parameterTypesConcatenated};
    }

    private void buildReturnTypeProxyCode(CodeBlock.Builder codeBuilder, TypeName returnTypeName) {
        String returnType = returnTypeName.toString();
        String returnTypeSimpleName;
        if (isParameterizedType(returnType)) {
            returnTypeSimpleName = parameterizedTypeWrapperName(returnTypeName.toString());
        } else {
            returnTypeSimpleName = typeSimpleName(returnTypeName.toString());
        }

        Set<FieldSignature> allowlistedTestFields = getTestFieldsForClass(
                returnTypeSimpleName.replaceAll("Proxy", ""));

        if (isParameterizedType(returnType)) {
            // If return type is parameterized
            String rawType = returnType.substring(0, returnType.indexOf("<"));
            String wrappedType = returnType.substring(returnType.indexOf("<") + 1,
                    returnType.indexOf(">"));

            if (rawType.contains("List")) {
                codeBuilder.addStatement("val objAsList = obj as List<*>");
                codeBuilder.addStatement("val list = arrayListOf<%L>()", wrappedType);

                codeBuilder.addStatement("for (o in objAsList) {", wrappedType);
                codeBuilder.addStatement("\tval i = %L()", wrappedType);

                for (FieldSignature field : allowlistedTestFields) {
                    TypeName type = convertToKotlinCompatibleType(field.getType(),
                            /* returnNonParameterizedType= */ false);
                    codeBuilder.addStatement(
                            "\ti.%L = o?.javaClass?.getField(%S)?.get(o) as %L?", field.getName(),
                            field.getName(), type.toString());
                }
                codeBuilder.addStatement("\tlist.add(i)");
                codeBuilder.addStatement("}");

                codeBuilder.addStatement("return list");
            } else if (rawType.contains("Set")) {
                codeBuilder.addStatement("val objAsSet = obj as Set<*>");
                codeBuilder.addStatement("val set = mutableSetOf<%L>()", wrappedType);

                codeBuilder.addStatement("for (o in objAsSet) {", wrappedType);
                codeBuilder.addStatement("\tval i = %L()", wrappedType);

                for (FieldSignature field : allowlistedTestFields) {
                    TypeName type = convertToKotlinCompatibleType(field.getType(),
                            /* returnNonParameterizedType= */ false);
                    codeBuilder.addStatement(
                            "\ti.%L = o?.javaClass?.getField(%S)?.get(o) as %L?", field.getName(),
                            field.getName(), type.toString());
                }

                codeBuilder.addStatement("\tset.add(i)");
                codeBuilder.addStatement("}");

                codeBuilder.addStatement("return set");
            }
        } else {
            codeBuilder.addStatement("val i = %L()", returnType);

            for (FieldSignature field : allowlistedTestFields) {
                TypeName type = convertToKotlinCompatibleType(field.getType(),
                        /* returnNonParameterizedType= */ false);
                codeBuilder.addStatement(
                        "i.%L = obj.javaClass.getDeclaredField(%S).get(obj) as %L", field.getName(),
                        field.getName(), type.toString());
            }

            codeBuilder.addStatement("return i");
        }
    }

    /**
     * If return type is an "allowlisted test class", return proxy type, otherwise return original
     * type.
     */
    private TypeName returnTypeOrProxy(MethodSignature method) {
        TypeName returnTypeName = null;

        if (ALLOWLISTED_TEST_CLASSES.contains(method.getReturnType().getType())) {
            // if return type is an "allowlisted test class"
            method.getReturnType().setProxyType(PACKAGE_NAME + "." +
                    typeSimpleName(method.getReturnType().getType()) + "Proxy");

            returnTypeName = getDeclaredType(method.getReturnType().getProxyType(),
                    /* wrappedTypeQualifiedName= */ null,
                    /* returnNonParameterizedType= */ true);
        }

        if (isParameterizedType(method.getReturnType().getType())) {
            // if return type is parameterized
            // and if wrapped type here is an "allowlisted test class",
            // then convert wrapped type to proxy type
            String wrappedType = method.getReturnType().getType().substring(
                    method.getReturnType().getType().indexOf("<") + 1,
                    method.getReturnType().getType().indexOf(">"));

            if (ALLOWLISTED_TEST_CLASSES.contains(wrappedType)) {
                String rawType = method.getReturnType().getType().substring(
                        0, method.getReturnType().getType().indexOf("<"));
                String wrappedProxyType = PACKAGE_NAME + "." + typeSimpleName(wrappedType)
                        + "Proxy";
                method.getReturnType().setProxyType(rawType + "<" + wrappedProxyType + ">");

                returnTypeName = getDeclaredType(rawType, wrappedProxyType,
                        /* returnNonParameterizedType= */ false);
            }
        }

        if (method.getReturnType().getProxyType() == null) {
            TypeMirror returnTypeMirror = typeForString(method.getReturnType().getType(),
                    processingEnv.getTypeUtils(), processingEnv.getElementUtils());
            returnTypeName = convertToKotlinCompatibleType(returnTypeMirror,
                    /* returnNonParameterizedType= */ false);
        }

        return returnTypeName;
    }

    private Set<FieldSignature> getTestFieldsForClass(String frameworkClass) {
        return ALLOWLISTED_TEST_FIELDS.stream()
                .map(f -> FieldSignature.forFieldString(f, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils()))
                .filter(t -> typeSimpleName(t.getFrameworkClass()).equals(frameworkClass))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void beginTryBlock(CodeBlock.Builder codeBuilder) {
        codeBuilder.beginControlFlow("try {");
    }

    private static void closeTryBlockAndBuildCatchBlock(CodeBlock.Builder codeBuilder) {
        // close try block
        codeBuilder.endControlFlow();
        // catch InvocationTargetException and throw actual cause
        codeBuilder.beginControlFlow("catch (e: Exception) {");
        codeBuilder.addStatement("throw e.cause!!");
        codeBuilder.endControlFlow();
    }

    private static String removeGetPrefix(String name) {
        if (!name.startsWith("get")) {
            return name;
        }

        char c[] = name.replaceAll("get", "").toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }
}
