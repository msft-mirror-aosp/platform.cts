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

package com.android.bedstead.remoteframeworkclasses.processor;

import com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses;
import com.android.bedstead.testapis.parser.TestApisParser;
import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Processor for generating {@code RemoteSystemService} classes.
 *
 * <p>This is started by including the {@link RemoteFrameworkClasses} annotation.
 *
 * <p>For each entry in {@code FRAMEWORK_CLASSES} this will generate an interface including all
 * public and test APIs with the {@code CrossUser} annotation. This interface will be named the same
 * as the framework class except with a prefix of "Remote", and will be in the same package.
 *
 * <p>This will also generate an implementation of the interface which takes an instance of the
 * framework class in the constructor, and each method proxying calls to the framework class.
 */
@SupportedAnnotationTypes({
    "com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {

    // Set to true to write a copy of the generated files in the /tmp/remoteframeworkclasses folder.
    // Useful for debugging.
    private static final boolean WRITE_DEBUG_COPY_OF_GENERATED_FILES = false;

    private static final ImmutableSet<String> FRAMEWORK_CLASSES =
            loadList("/apis/framework-classes.txt");

    private static final MethodSignature PARENT_PROFILE_INSTANCE =
            MethodSignature.forHardcoded(
                    MethodSignature.Visibility.PUBLIC,
                    "android.app.admin.DevicePolicyManager",
                    "getParentProfileInstance",
                    List.of("android.content.ComponentName"));
    private static final MethodSignature GET_CONTENT_RESOLVER =
            MethodSignature.forHardcoded(
                    MethodSignature.Visibility.PUBLIC,
                    "android.content.ContentResolver",
                    "getContentResolver",
                    List.of());
    private static final MethodSignature GET_ADAPTER =
            MethodSignature.forHardcoded(
                    MethodSignature.Visibility.PUBLIC,
                    "android.bluetooth.BluetoothAdapter",
                    "getAdapter",
                    List.of());
    private static final MethodSignature GET_DEFAULT_ADAPTER =
            MethodSignature.forHardcoded(
                    MethodSignature.Visibility.PUBLIC,
                    "android.bluetooth.BluetoothAdapter",
                    "getDefaultAdapter",
                    List.of());

    // Methods of which the return value is modified in the generated framework interface and
    // framework impl files.
    private static Map<MethodSignature, ClassName> FRAMEWORK_SIGNATURE_RETURN_OVERRIDES =
            Map.ofEntries(
                    Map.entry(
                            PARENT_PROFILE_INSTANCE,
                            ClassName.get("android.app.admin", "RemoteDevicePolicyManager")),
                    Map.entry(
                            GET_CONTENT_RESOLVER,
                            ClassName.get("android.content", "RemoteContentResolver")),
                    Map.entry(
                            GET_ADAPTER,
                            ClassName.get("android.bluetooth", "RemoteBluetoothAdapter")),
                    Map.entry(
                            GET_DEFAULT_ADAPTER,
                            ClassName.get("android.bluetooth", "RemoteBluetoothAdapter")));

    private static final ImmutableSet<String> BLOCKLISTED_TYPES =
            loadList("/apis/type-blocklist.txt");

    // TODO(b/436548677): Remove once templates are supported.
    private static final Set<MethodSignature> ALLOWLISTED_METHODS =
            Set.of(
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "android.accounts.AccountManagerFuture<android.os.Bundle>",
                            "addAccount",
                            List.of(
                                    "java.lang.String",
                                    "java.lang.String",
                                    "java.lang.String[]",
                                    "android.os.Bundle",
                                    "android.app.Activity",
                                    "android.accounts.AccountManagerCallback<android.os.Bundle>",
                                    "android.os.Handler")),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "android.accounts.AccountManagerFuture<android.os.Bundle>",
                            "removeAccount",
                            List.of(
                                    "android.accounts.Account",
                                    "android.app.Activity",
                                    "android.accounts.AccountManagerCallback<android.os.Bundle>",
                                    "android.os.Handler")),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "android.app.admin.DevicePolicyManager",
                            "getParentProfileInstance",
                            List.of("android.content.ComponentName")),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "android.accounts.AccountManagerFuture<android.os.Bundle>",
                            "updateCredentials",
                            List.of(
                                    "android.accounts.Account",
                                    "java.lang.String",
                                    "android.os.Bundle",
                                    "android.app.Activity",
                                    "android.accounts.AccountManagerCallback<android.os.Bundle>",
                                    "android.os.Handler")),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "android.content.ContentResolver",
                            "getContentResolver",
                            List.of()),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "java.security.PrivateKey",
                            "getPrivateKey",
                            List.of("android.content.Context", "java.lang.String"),
                            Set.of(
                                    "java.lang.InterruptedException",
                                    "android.security.KeyChainException")),
                    MethodSignature.forHardcoded(
                            MethodSignature.Visibility.PUBLIC,
                            "java.lang.String",
                            "getSystemServiceName",
                            List.of("java.lang.Class<?>")));

    /** A set of all classes listed in test-current.txt. */
    static final ImmutableSet<ClassSignature> CLASSES_LISTED_IN_TEST_CURRENT_FILE =
            loadClassesListedInTestCurrentFile();

    /**
     * The TestApisReflection module generates proxy classes used to access TestApi classes and
     * methods through reflection. These proxy classes are then processed like other framework
     * classes in this processor.
     */
    static final String TEST_APIS_REFLECTION_PACKAGE = "android.cts.testapisreflection";

    private static final String TEST_APIS_REFLECTION_FILE =
            TEST_APIS_REFLECTION_PACKAGE + ".TestApisReflectionKt";

    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteContentResolver");
    private static final ClassName NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteBluetoothAdapter");

    // TODO(b/205562849): These only support passing null, which is fine for existing tests but
    //  will be misleading
    private static final ClassName NULL_PARCELABLE_ACTIVITY_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses", "NullParcelableActivity");
    private static final ClassName NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableAccountManagerCallback");
    private static final ClassName NULL_HANDLER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses", "NullParcelableHandler");

    private static final ClassName PARCELABLE_POLICY_IDENTIFIER =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "ParcelablePolicyIdentifier");

    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");

    private static final ClassName ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "AccountManagerFutureWrapper");

    private Elements mElementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mElementUtils = processingEnv.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.getElementsAnnotatedWith(RemoteFrameworkClasses.class).isEmpty()) {
            for (String systemService : FRAMEWORK_CLASSES) {
                TypeElement typeElement = mElementUtils.getTypeElement(systemService);
                generateRemoteSystemService(typeElement);
            }

            generateWrappers();
        }

        return true;
    }

    private void generateWrappers() {
        generateWrapper(NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACTIVITY_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME);
        generateWrapper(NULL_HANDLER_CALLBACK_CLASSNAME);
        generateWrapper(PARCELABLE_POLICY_IDENTIFIER);
    }

    private void generateWrapper(ClassName className) {
        String contents = null;
        try {
            URL url =
                    Processor.class.getResource(
                            "/parcelablewrappers/" + className.simpleName() + ".java.txt");
            contents = Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse wrapper " + className, e);
        }

        String qualifiedClassName = className.packageName() + "." + className.simpleName();

        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler().createSourceFile(qualifiedClassName);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.write(contents);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }

        if (WRITE_DEBUG_COPY_OF_GENERATED_FILES) {
            writeCopyOfFileForDebugging(qualifiedClassName, contents);
        }
    }

    private void generateRemoteSystemService(TypeElement frameworkClass) {
        List<Api> apis =
                filterMethods(
                                frameworkClass,
                                getMethods(frameworkClass),
                                Apis.forClass(
                                        frameworkClass.getQualifiedName().toString(),
                                        mElementUtils))
                        .stream()
                        .filter(api -> !usesBlocklistedType(api))
                        .filter(api -> !parametersHaveWildcards(api.method))
                        .map(api -> expandTemplatedMethods(api))
                        .flatMap(Collection::stream)
                        .sorted(Comparator.comparing(api -> api.method.name))
                        .collect(Collectors.toList());

        generateFrameworkInterface(frameworkClass, apis);
        generateFrameworkImpl(frameworkClass, apis);

        if (frameworkClass.getSimpleName().contentEquals("DevicePolicyManager")) {
            // Special case, we need to support the .getParentProfileInstance method
            generateDpmParent(frameworkClass, apis);
        }
    }

    private boolean hasWildcard(TypeName type) {
        return type.toString().contains("?");
    }

    private boolean parametersHaveWildcards(MethodSpec method) {
        if (ALLOWLISTED_METHODS.contains(MethodSignature.forMethodSpec(method))) {
            return false; // Special case hacked in methods
        }

        for (ParameterSpec parameter : method.parameters) {
            if (hasWildcard(parameter.type)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlocklistedType(TypeName typeName) {
        if (BLOCKLISTED_TYPES.contains(typeName.toString())) {
            return true;
        }

        if (typeName instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            if (BLOCKLISTED_TYPES.contains(parameterizedTypeName.rawType.toString())) {
                return true;
            }
            for (TypeName typeArgument : parameterizedTypeName.typeArguments) {
                if (isBlocklistedType(typeArgument)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesBlocklistedType(Api api) {
        MethodSpec method = api.method;

        if (ALLOWLISTED_METHODS.contains(MethodSignature.forMethodSpec(method))) {
            return false; // Special case hacked in methods
        }

        if (isBlocklistedType(method.returnType)) {
            return true;
        }

        for (int i = 0; i < method.parameters.size(); i++) {
            if (i == 0 && api.isTestApi) {
                // if it is a TestApi, ignore the first parameter as that is the kotlin
                // extension receiver parameter.
                continue;
            }
            if (isBlocklistedType(method.parameters.get(i).type)) {
                return true;
            }
        }

        for (TypeName exception : method.exceptions) {
            if (isBlocklistedType(exception)) {
                return true;
            }
        }

        return false;
    }

    private void generateFrameworkInterface(TypeElement frameworkClass, List<Api> apis) {
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName().toString());
        ClassName implClassName =
                ClassName.get(
                        packageName, "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(className).addModifiers(Modifier.PUBLIC);

        classBuilder.addJavadoc(
                "Public, test, and system interface for {@link $T}.\n\n", frameworkClass);
        classBuilder.addJavadoc(
                "<p>All methods are annotated {@link $T} for compatibility with the"
                        + " Connected Apps SDK.\n\n",
                CrossUser.class);
        classBuilder.addJavadoc("<p>For implementation see {@link $T}.\n", implClassName);

        classBuilder
                .addAnnotation(
                        AnnotationSpec.builder(Generated.class)
                                .addMember("value", "$S", Processor.class.getName())
                                .build())
                .addAnnotation(
                        AnnotationSpec.builder(CrossUser.class)
                                .addMember(
                                        "parcelableWrappers",
                                        "{$T.class, $T.class, $T.class, $T.class, $T.class,"
                                                + " $T.class, $T.class}",
                                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                                        NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                                        NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                                        NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                                        NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                                        NULL_HANDLER_CALLBACK_CLASSNAME,
                                        PARCELABLE_POLICY_IDENTIFIER)
                                .addMember(
                                        "futureWrappers",
                                        "$T.class",
                                        ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME)
                                .build());

        for (Api api : apis) {
            MethodSpec method = api.method;
            MethodSignature signature = MethodSignature.forMethodSpec(method);

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.name)
                            .returns(method.returnType)
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossUser.class)
                            .addExceptions(method.exceptions);

            if (FRAMEWORK_SIGNATURE_RETURN_OVERRIDES.containsKey(signature)) {
                methodBuilder.returns(FRAMEWORK_SIGNATURE_RETURN_OVERRIDES.get(signature));
            }

            methodBuilder.addJavadoc(
                    "See {@link $T#$L}.", ClassName.get(frameworkClass.asType()), method.name);

            List<ParameterSpec> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.parameters.subList(1, method.parameters.size());
            } else {
                parameters = method.parameters;
            }

            methodBuilder.addParameters(parameters);

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateDpmParent(TypeElement frameworkClass, List<Api> apis) {
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName() + "Parent");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        classBuilder.addAnnotation(
                AnnotationSpec.builder(CrossUser.class)
                        .addMember(
                                "parcelableWrappers",
                                "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class,"
                                        + " $T.class}",
                                NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                                NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                                NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                                NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                                NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                                NULL_HANDLER_CALLBACK_CLASSNAME,
                                PARCELABLE_POLICY_IDENTIFIER)
                        .build());

        classBuilder.addField(
                ClassName.get(frameworkClass), "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build());

        for (Api api : apis) {
            MethodSpec method = api.method;
            MethodSignature signature = MethodSignature.forMethodSpec(method);

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.name)
                            .returns(method.returnType)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossUser.class)
                            .addExceptions(method.exceptions);

            methodBuilder.addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName");

            List<ParameterSpec> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.parameters.subList(1, method.parameters.size());
            } else {
                parameters = method.parameters;
            }

            methodBuilder.addParameters(parameters);

            List<String> paramNames = toMutableList(parameters.stream().map(p -> p.name));
            if (api.isTestApi) {
                paramNames.add(
                        0, "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName)");
            }

            String frameworkClassName;
            if (api.isTestApi) {
                frameworkClassName = TEST_APIS_REFLECTION_FILE;
            } else {
                frameworkClassName =
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName)";
            }

            String methodName = method.name;
            if (api.originalMethod != null) {
                methodName = api.originalMethod.name;
            }

            if (signature.equals(PARENT_PROFILE_INSTANCE)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(
                        ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
                methodBuilder.addStatement(
                        "throw new $T($S)",
                        UnsupportedOperationException.class,
                        "TestApp does not support calling .getParentProfileInstance() on a parent"
                                + ".");
            } else if (method.returnType.equals(TypeName.VOID)) {
                methodBuilder.addStatement(
                        "$L.$L($L)", frameworkClassName, methodName, String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return $L.$L($L)",
                        frameworkClassName,
                        methodName,
                        String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateFrameworkImpl(TypeElement frameworkClass, List<Api> apis) {
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName interfaceClassName =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName().toString());
        ClassName className =
                ClassName.get(
                        packageName, "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder
                .addAnnotation(
                        AnnotationSpec.builder(Generated.class)
                                .addMember("value", "$S", Processor.class.getName())
                                .build())
                .addAnnotation(
                        AnnotationSpec.builder(SuppressWarnings.class)
                                .addMember("value", "$S", "CheckSignatures")
                                .build());

        classBuilder.addField(
                ClassName.get(frameworkClass), "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build());

        for (Api api : apis) {
            MethodSpec method = api.method;
            MethodSignature signature = MethodSignature.forMethodSpec(method);

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.name)
                            .returns(method.returnType)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .addExceptions(method.exceptions);

            List<ParameterSpec> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.parameters.subList(1, method.parameters.size());
            } else {
                parameters = method.parameters;
            }

            methodBuilder.addParameters(parameters);

            List<String> paramNames = toMutableList(parameters.stream().map(p -> p.name));
            if (api.isTestApi) {
                paramNames.add(0, "mFrameworkClass");
            }

            String frameworkClassName;
            if (api.isTestApi) {
                frameworkClassName = TEST_APIS_REFLECTION_FILE;
            } else if (method.modifiers.contains(Modifier.STATIC)) {
                frameworkClassName = frameworkClass.getQualifiedName().toString();
            } else {
                frameworkClassName = "mFrameworkClass";
            }

            String methodName = method.name;
            if (api.originalMethod != null) {
                methodName = api.originalMethod.name;
            }

            if (FRAMEWORK_SIGNATURE_RETURN_OVERRIDES.containsKey(signature)) {
                methodBuilder.returns(FRAMEWORK_SIGNATURE_RETURN_OVERRIDES.get(signature));
                // We assume all replacements are null-only
                methodBuilder.addStatement("return null");
            } else if (method.returnType.equals(TypeName.VOID)) {
                methodBuilder.addStatement(
                        "$L.$L($L)", frameworkClassName, methodName, String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return $L.$L($L)",
                        frameworkClassName,
                        methodName,
                        String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private Set<Api> filterMethods(
            TypeElement frameworkClass, Set<ExecutableElement> allMethods, Apis validApis) {
        Set<Api> filteredMethods = new HashSet<>();

        for (ExecutableElement method : allMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, mElementUtils);
            if (validApis.methods().contains(methodSignature)) {
                if (method.getModifiers().contains(Modifier.PROTECTED)) {
                    System.out.println(methodSignature + " is protected. Dropping");
                } else {
                    filteredMethods.add(new Api(toMethodSpec(method), /* isTestApi= */ false));
                }
            }
        }

        filterValidTestApis(filteredMethods, frameworkClass);

        return filteredMethods;
    }

    private void filterValidTestApis(Set<Api> filteredMethods, TypeElement frameworkClass) {
        Set<ExecutableElement> testMethods = new HashSet<>();
        TypeElement testApisReflectionTypeElement =
                mElementUtils.getTypeElement(TEST_APIS_REFLECTION_FILE);

        testApisReflectionTypeElement.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> testMethods.add(e));

        for (ExecutableElement method : testMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, mElementUtils);

            if (!methodSignature
                    .getParameterTypes()
                    .get(0)
                    .equals(frameworkClass.getQualifiedName().toString())) {
                continue;
            }

            Api testApi = new Api(toMethodSpec(method), /* isTestApi= */ true);
            if (filteredMethods.contains(testApi)) {
                System.out.println(
                        "Api "
                                + methodSignature.getName()
                                + " is already added, "
                                + "probably because it is marked as another type of Api as well.");
                continue;
            }

            filteredMethods.add(testApi);
        }
    }

    /*
     * If the input method is templated, expand it into multiple type-specific methods.
     * If not, this simply returns the input method.
     *
     * Example:
     *  Input:
     *    <T> void setPolicy(PolicyIdentifier<T> key, T value)
     *  Output:
     *    void setPolicy_string(PolicyIdentifier<String> key, String value);
     *    void setPolicy_integer(PolicyIdentifier<Integer> key, Integer value);
     *    ... and more
     */
    private List<Api> expandTemplatedMethods(Api method) {
        if (method.method.typeVariables.isEmpty()) {
            // Not a templated method
            return List.of(method);
        }

        return new TemplatedMethodExpander(method).expand();
    }

    private void writeClassToFile(String packageName, TypeSpec clazz) {
        String qualifiedClassName =
                packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

        JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
        try {
            JavaFileObject builderFile =
                    processingEnv.getFiler().createSourceFile(qualifiedClassName);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                javaFile.writeTo(out);
            }

            if (WRITE_DEBUG_COPY_OF_GENERATED_FILES) {
                writeCopyOfFileForDebugging(qualifiedClassName, javaFile.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
        }
    }

    /**
     * Write a copy of the given file to /tmp/remoteframeworkclasses. Useful during debugging, since
     * generated files that do not compile are otherwise never written to the disk.
     */
    private void writeCopyOfFileForDebugging(String qualifiedName, String content) {
        if (!WRITE_DEBUG_COPY_OF_GENERATED_FILES) {
            return;
        }
        try {
            String debugFileName = qualifiedName.replace('.', '/') + ".java";
            File debugFile = new File("/tmp/remoteframeworkclasses/", debugFileName);

            // Ensure directory exists
            debugFile.getParentFile().mkdirs();

            try (FileWriter fw = new FileWriter(debugFile)) {
                fw.write(content);
            }

            System.err.println("[Debug] Wrote generated file to: " + debugFile.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write debug file: " + e);
        }
    }

    private Set<ExecutableElement> getMethods(TypeElement interfaceClass) {
        Map<String, ExecutableElement> methods = new HashMap<>();
        getMethods(methods, interfaceClass);
        return new HashSet<>(methods.values());
    }

    private void getMethods(Map<String, ExecutableElement> methods, TypeElement interfaceClass) {
        interfaceClass.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> !methods.containsKey(e.getSimpleName().toString()))
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> methods.put(methodHash(e), e));

        interfaceClass.getInterfaces().stream()
                .map(m -> mElementUtils.getTypeElement(m.toString()))
                .forEach(m -> getMethods(methods, m));

        TypeElement superclassElement =
                (TypeElement)
                        processingEnv.getTypeUtils().asElement(interfaceClass.getSuperclass());

        if (superclassElement != null) {
            getMethods(methods, superclassElement);
        }
    }

    private String methodHash(ExecutableElement method) {
        return method.getSimpleName()
                + "("
                + method.getParameters().stream()
                        .map(p -> p.asType().toString())
                        .collect(Collectors.joining(","))
                + ")";
    }

    private static ImmutableSet<String> loadList(String filename) {
        try {
            return ImmutableSet.copyOf(
                    Resources.toString(
                                    Processor.class.getResource(filename), StandardCharsets.UTF_8)
                            .split("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file", e);
        }
    }

    private static ImmutableSet<ClassSignature> loadClassesListedInTestCurrentFile() {
        return ImmutableSet.copyOf(
                TestApisParser.parse().stream()
                        .flatMap(p -> p.getClassSignatures().stream())
                        .collect(Collectors.toSet()));
    }

    public static class Api {
        public final MethodSpec method;
        // Templated methods get expanded into multiple type-specific methods. In that case, this
        // points to the original templated method. Otherwise, this will be null.
        public final MethodSpec originalMethod;
        public final boolean isTestApi;

        public Api(MethodSpec method, boolean isTestApi) {
            this.method = method;
            this.originalMethod = null;
            this.isTestApi = isTestApi;
        }

        public Api(MethodSpec method, MethodSpec originalMethod, boolean isTestApi) {
            this.method = method;
            this.originalMethod = originalMethod;
            this.isTestApi = isTestApi;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Api)) return false;
            Api that = (Api) o;

            if (!Objects.equals(this.method.name, that.method.name)) {
                return false;
            }

            List<ParameterSpec> thisParams = this.method.parameters;
            List<ParameterSpec> thatParams = that.method.parameters;

            int thisStart = this.isTestApi ? 1 : 0;
            int thatStart = that.isTestApi ? 1 : 0;

            int thisEffectiveSize = thisParams.size() - thisStart;
            int thatEffectiveSize = thatParams.size() - thatStart;

            if (thisEffectiveSize != thatEffectiveSize) {
                return false;
            }

            for (int i = 0; i < thisEffectiveSize; i++) {
                TypeName thisType = thisParams.get(i + thisStart).type;
                TypeName thatType = thatParams.get(i + thatStart).type;
                if (!thisType.equals(thatType)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            StringBuilder params = new StringBuilder();
            int index = 0;
            if (isTestApi) {
                // if it is a TestApi we need to ignore the first
                // parameter in the TestApi as that is the kotlin extension receiver
                // parameter
                index = 1;
            }
            for (int i = index; i < method.parameters.size(); i++) {
                params.append(method.parameters.get(i).type.toString());
            }

            return Objects.hash(method.name.toString(), params.toString());
        }
    }

    // Convert the {@code ExecutableElement} to a {@code MethodSpec}.
    private MethodSpec toMethodSpec(ExecutableElement element) {
        var builder =
                MethodSpec.methodBuilder(element.getSimpleName().toString())
                        .addModifiers(element.getModifiers())
                        .returns(TypeName.get(element.getReturnType()));

        for (TypeParameterElement typeParam : element.getTypeParameters()) {
            builder.addTypeVariable(TypeVariableName.get(typeParam));
        }

        for (VariableElement parameter : element.getParameters()) {
            builder.addParameter(ParameterSpec.get(parameter));
        }

        for (TypeMirror thrownType : element.getThrownTypes()) {
            builder.addException(TypeName.get(thrownType));
        }

        return builder.build();
    }

    private static <T> List<T> toMutableList(Stream<T> stream) {
        return stream.collect(Collectors.toCollection(ArrayList::new));
    }
}
