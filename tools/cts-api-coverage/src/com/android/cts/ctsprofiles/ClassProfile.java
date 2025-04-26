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

package com.android.cts.ctsprofiles;

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiMethod;

import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Representation of a class included in the CTS package. */
public class ClassProfile {

    public final AnnotationManagement annotationManagement = new AnnotationManagement();

    private final String mModule;

    private final String mPackage;

    private final String mClass;

    private int mClassType = 0;

    private ClassProfile mSuperClass = null;

    // A list of interfaces implemented by this class.
    private final List<ClassProfile> mInterfaces = new ArrayList<>();

    // Methods defined in this class.
    private final Map<String, MethodProfile> mMethods = new HashMap<>();

    // Test methods defined in this class.
    private Map<String, MethodProfile> mTestMethods = null;

    // Methods inherited from apis.
    private Map<String, Pair<ClassProfile, MethodProfile>> mInheritedApiMethods = null;

    // A map of API classes extended/implemented by this class with the API class signature as
    // the key.
    private Map<String, ClassProfile> mInheritedApiClasses = null;


    private static final Set<String> JUNIT4_ANNOTATION_PATTERNS = new HashSet<>(
            List.of(
                    "org.junit.*",
                    "com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4"
            ));

    private static final Set<String> JUNIT3_CLASS_PATTERNS = new HashSet<>(
            List.of(
                    "junit.framework.TestCase",
                    "android.test.AndroidTestCase",
                    "android.test.InstrumentationTestCase"
            ));

    public ClassProfile(String moduleName, String packageName, String className, boolean apiClass) {
        mModule = moduleName;
        mClass = className;
        mPackage = packageName;
        if (apiClass) {
            mClassType |= ClassType.API.getValue();
        }
    }

    /** Representation of the class type. */
    public enum ClassType {
        INTERFACE(1),
        ABSTRACT(2),
        JUNIT3(4),
        JUNIT4(8),
        ANNOTATION(16),
        /** A non-test and non-annotation class. */
        COMMON(32),
        API(64);

        private final int mValue;

        ClassType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public String getClassSignature() {
        return Utils.getClassSignature(mPackage, mClass);
    }

    public String getClassName() {
        return mClass;
    }

    public String getPackageName() {
        return mPackage;
    }

    public String getModuleName() {
        return mModule;
    }

    public List<ClassProfile> getInterfaces() {
        return mInterfaces;
    }

    public Map<String, MethodProfile> getMethods() {
        return mMethods;
    }

    public List<Pair<ClassProfile, MethodProfile>> getInheritedApiMethods() {
        return mInheritedApiMethods.values().stream().toList();
    }

    /** Creates a class method. */
    public MethodProfile getOrCreateMethod(String methodName, List<String> params) {
        String methodSignature = Utils.getMethodSignature(methodName, params);
        if (!mMethods.containsKey(methodSignature)) {
            mMethods.put(
                    methodSignature,
                    new MethodProfile(this, methodName, Utils.formatParams(params)));
        }
        return mMethods.get(methodSignature);
    }

    /**
     * Retrieves a map of API methods that are overridden by a method with the given name and
     * parameter types in this class, including methods from superclasses and interfaces.
     */
    public Map<ClassProfile, MethodProfile> getOverriddenApiMethods(String methodSignature) {
        MethodProfile method = mMethods.get(methodSignature);
        if (method == null
                || !method.isDirectMember()
                || !method.getOverriddenApiMethods().isEmpty()) {
            return new HashMap<>();
        }
        Map<ClassProfile, MethodProfile> overriddenMethods = new HashMap<>();
        if (mSuperClass != null) {
            MethodProfile superMethod = mSuperClass.getMethods().get(methodSignature);
            if (superMethod != null) {
                overriddenMethods.putIfAbsent(mSuperClass, superMethod);
            }
        }
        for (ClassProfile interfaceClass : mInterfaces) {
            MethodProfile superMethod = interfaceClass.getMethods().get(methodSignature);
            if (superMethod != null) {
                overriddenMethods.putIfAbsent(interfaceClass, superMethod);
            }
        }
        return overriddenMethods;
    }

    /** Gets API classes extended/implemented by the class. */
    public Map<String, ClassProfile> getInheritedApiClasses() {
        if (mInheritedApiClasses == null) {
            resolveInheritedApiClasses();
        }
        return mInheritedApiClasses;
    }

    /** Resolves all API classes extended/implemented by the class. */
    public void resolveInheritedApiClasses() {
        mInheritedApiClasses = new HashMap<>();
        if (mSuperClass != null) {
            addInheritedApiClasses(mSuperClass);
        }
        for (ClassProfile interfaceClass : mInterfaces) {
            addInheritedApiClasses(interfaceClass);
        }
    }

    /**
     * Adds a supper method call when the method is inherited from super classes. If the "super"
     * keyword is not explicitly added, the java bytecode will not show which super class is called.
     * In this case, find the nearest method along the super class chain and add an additionally
     * call to that method.
     */
    public void resolveInheritedMethods(ApiCoverage apiCoverage) {
        for (MethodProfile method : mMethods.values()) {
            if (method.isDirectMember()) {
                continue;
            }
            MethodProfile inheritedMethod =
                    findInheritedMethod(
                            method.getMethodName(), method.getMethodParams(), apiCoverage);
            if (inheritedMethod != null) {
                method.addMethodCall(inheritedMethod);
            }
        }
    }

    /**
     * Filters out methods that are overriding abstract API methods defined in extended API classes
     * or implemented API interfaces. An additional method link to corresponding abstract API
     * methods will be recorded to ensure they will be included in the API coverage measurement.
     */
    public void resolveOverriddenAbstractApiMethods(ApiCoverage apiCoverage) {
        for (MethodProfile method : mMethods.values()) {
            if (method.isAbstract() || !method.isDirectMember()) {
                continue;
            }
            for (ClassProfile inheritedApiClass : getInheritedApiClasses().values()) {
                ApiMethod apiMethod =
                        apiCoverage.getMethod(
                                inheritedApiClass.getPackageName(),
                                inheritedApiClass.getClassName(),
                                method.getMethodName(),
                                method.getMethodParams());
                if (apiMethod == null || !apiMethod.isAbstractMethod()) {
                    continue;
                }
                MethodProfile overriddenApiMethod =
                        inheritedApiClass.getOrCreateMethod(
                                method.getMethodName(), method.getMethodParams());
                overriddenApiMethod.addMethodType(MethodProfile.MethodType.ABSTRACT);
                // The corresponding abstract API method should be regarded as covered.
                method.addOverriddenApiMethod(overriddenApiMethod);
            }
        }
    }

    /** Resolves all inherited methods from super API classes and API interfaces. */
    public void resolveInheritedApiMethods(ApiCoverage apiCoverage) {
        if (mInheritedApiMethods != null) {
            return;
        }
        mInheritedApiMethods = new HashMap<>();
        if (mSuperClass != null) {
            addInheritedApiMethods(mSuperClass, apiCoverage);
        }
        for (ClassProfile interfaceClass : mInterfaces) {
            addInheritedApiMethods(interfaceClass, apiCoverage);
        }
    }

    /** Adds an interface implemented by the class. */
    public void addInterface(ClassProfile interfaceProfile) {
        mInterfaces.add(interfaceProfile);
    }

    /** Adds a class type for the class. */
    public void addClassType(ClassType classType) {
        mClassType |= classType.getValue();
    }

    public void setSuperClass(ClassProfile superClass) {
        mSuperClass = superClass;
    }

    /** Collects all test methods contained in the class. */
    public Map<String, MethodProfile> getTestMethods() {
        if (mTestMethods != null) {
            return mTestMethods;
        }
        mTestMethods = new HashMap<>();
        mMethods.forEach((methodKey, method) -> {
            if (method.isTestMethod()) {
                mTestMethods.put(methodKey, method);
            }
        });
        // Test methods defined in the super class will also be collected.
        if (mSuperClass != null) {
            mSuperClass.getTestMethods().forEach(mTestMethods::putIfAbsent);
        }
        return mTestMethods;
    }

    /** Returns true if the class is a test class. */
    public boolean isTestClass() {
        if (matchAnyTypes(ClassType.ANNOTATION.getValue() | ClassType.API.getValue())) {
            return false;
        }
        if (!isJunit4Class() && !isJunit3Class()) {
            addClassType(ClassType.COMMON);
            return false;
        }
        return true;
    }

    /** Returns true if the class is an API class. */
    public boolean isApiClass() {
        return matchAllTypes(ClassType.API.getValue());
    }

    /** Returns true if the class is a test class but not an abstract class. */
    public boolean isNonAbstractTestClass() {
        return (isTestClass() && !matchAnyTypes(
                ClassType.ABSTRACT.getValue() | ClassType.INTERFACE.getValue()));
    }


    /** Returns true if it is decided that whether this is a test class or not. */
    private boolean testClassResolved() {
        return matchAnyTypes(
                ClassType.JUNIT3.getValue()
                        | ClassType.JUNIT4.getValue()
                        | ClassType.COMMON.getValue()
                        | ClassType.ANNOTATION.getValue()
                        | ClassType.API.getValue()
        );
    }

    /** Returns true if the class is a Junit4 test class. */
    protected boolean isJunit4Class() {
        if (testClassResolved()) {
            return matchAllTypes(ClassType.JUNIT4.getValue());
        }
        // Check if the class is marked by a Junit4 runner.
        for (ClassProfile annotation : annotationManagement.getAnnotations()) {
            for (String pattern : JUNIT4_ANNOTATION_PATTERNS) {
                if (annotation.getClassSignature().matches(pattern)) {
                    addClassType(ClassType.JUNIT4);
                    return true;
                }
            }
        }
        // Check if any methods are marked by a Junit4 annotation.
        for (MethodProfile method : mMethods.values()) {
            if (method.isJunit4Method()) {
                addClassType(ClassType.JUNIT4);
                return true;
            }
        }
        // Check if the class is extended from a Junit4 class.
        if (mSuperClass != null && mSuperClass.isJunit4Class()) {
            addClassType(ClassType.JUNIT4);
            return true;
        }
        return false;
    }

    /** Returns true if the class is a Junit3 test class. */
    protected boolean isJunit3Class() {
        if (testClassResolved()) {
            return matchAllTypes(ClassType.JUNIT3.getValue());
        }
        if (mSuperClass != null) {
            // Check if the class is extended from a Junit3 base class.
            for (String pattern : JUNIT3_CLASS_PATTERNS) {
                if (mSuperClass.getClassSignature().matches(pattern)) {
                    addClassType(ClassType.JUNIT3);
                    return true;
                }
            }
            // Check if the class is extended from a Junit3 class.
            if (mSuperClass.isJunit3Class()) {
                addClassType(ClassType.JUNIT3);
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an inherited API method from a superclass or interface only if a method with the same
     * signature does not exist in this class.
     */
    private void addInheritedApiMethod(
            ClassProfile superClass,
            String methodName,
            List<String> methodParams,
            boolean isAbstract) {
        String methodSignature = Utils.getMethodSignature(methodName, methodParams);
        if (mMethods.get(methodSignature) != null
                && mMethods.get(methodSignature).isDirectMember()) {
            return;
        }
        MethodProfile inheritedMethod = superClass.getOrCreateMethod(methodName, methodParams);
        if (isAbstract) {
            inheritedMethod.addMethodType(MethodProfile.MethodType.ABSTRACT);
        }
        mInheritedApiMethods.putIfAbsent(methodSignature, new Pair<>(superClass, inheritedMethod));
    }

    private void addInheritedApiMethods(ClassProfile superClass, ApiCoverage apiCoverage) {
        // Skip java.lang.Object, which can make the runtime very long.
        if (superClass.isApiClass()
                && !superClass.getClassSignature().startsWith("java.lang.Object")) {
            ApiClass apiClass =
                    apiCoverage.getClass(superClass.getPackageName(), superClass.getClassName());
            for (Pair<ApiClass, ApiMethod> classMethod : apiClass.getInheritedMethods()) {
                ApiMethod method = classMethod.getSecond();
                addInheritedApiMethod(
                        superClass,
                        method.getName(),
                        method.getParameterTypes(),
                        method.isAbstractMethod());
            }
            for (ApiMethod method : apiClass.getDeclaredMethods()) {
                addInheritedApiMethod(
                        superClass,
                        method.getName(),
                        method.getParameterTypes(),
                        method.isAbstractMethod());
            }
            return;
        }
        superClass.resolveInheritedApiMethods(apiCoverage);
        for (Pair<ClassProfile, MethodProfile> classMethod : superClass.getInheritedApiMethods()) {
            MethodProfile method = classMethod.getSecond();
            addInheritedApiMethod(
                    superClass,
                    method.getMethodName(),
                    method.getMethodParams(),
                    method.isAbstract());
        }
    }

    private void addInheritedApiClasses(ClassProfile superClass) {
        // Skip java.lang.Object, which can make the runtime very long.
        if (superClass.isApiClass()
                && !superClass.getClassSignature().startsWith("java.lang.Object")) {
            mInheritedApiClasses.put(superClass.getClassSignature(), superClass);
        } else {
            mInheritedApiClasses.putAll(superClass.getInheritedApiClasses());
        }
    }

    /**
     * Finds the inherited method corresponding to the given method signature, searching through the
     * current class, its superclass, and implemented interfaces.
     *
     * @param methodName The name of the method to find.
     * @param params The list of parameter types for the method.
     * @param apiCoverage The {@link ApiCoverage} object containing API information.
     * @return The {@link MethodProfile} of the inherited method, or {@code null} if not found.
     */
    private MethodProfile findInheritedMethod(
            String methodName, List<String> params, ApiCoverage apiCoverage) {
        if (isApiClass()) {
            ApiMethod apiMethods = apiCoverage.getMethod(mPackage, mClass, methodName, params);
            return apiMethods == null ? null : getOrCreateMethod(methodName, params);
        }
        String methodSignature = Utils.getMethodSignature(methodName, params);
        MethodProfile inheritedMethod = mMethods.get(methodSignature);
        if (inheritedMethod != null && inheritedMethod.isDirectMember()) {
            return inheritedMethod;
        }
        if (mSuperClass != null) {
            inheritedMethod = mSuperClass.findInheritedMethod(methodName, params, apiCoverage);
            if (inheritedMethod != null) {
                return inheritedMethod;
            }
        }
        for (ClassProfile interfaceClass : getInterfaces()) {
            inheritedMethod = interfaceClass.findInheritedMethod(methodName, params, apiCoverage);
            if (inheritedMethod != null) {
                return inheritedMethod;
            }
        }
        return null;
    }

    private boolean matchAnyTypes(int typesValue) {
        return (mClassType & typesValue) != 0;
    }

    private boolean matchAllTypes(int typesValue) {
        return (mClassType & typesValue) == typesValue;
    }
}
