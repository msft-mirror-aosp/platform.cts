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

    // A map of methods defined in this class with the method signature as the key.
    private final Map<String, MethodProfile> mMethods = new HashMap<>();

    // A map of test methods defined in this class with the method signature as the key.
    private Map<String, MethodProfile> mTestMethods = null;

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
        /** A non-test and non-annotation class.*/
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

    public Map<String, MethodProfile> getMethods() {
        return mMethods;
    }

    /** Creates a class method. */
    public MethodProfile getOrCreateMethod(
            String methodName, List<String> params) {
        String methodSignature = Utils.getMethodSignature(methodName, params);
        if (!mMethods.containsKey(methodSignature)) {
            mMethods.put(methodSignature, new MethodProfile(this, methodName, params));
        }
        return mMethods.get(methodSignature);
    }

    /**
     * Adds a supper method call when the method is extended from super classes. If the "super"
     * keyword is not explicitly added, the java bytecode will not show which super class is called.
     * In this case, find the nearest method along the super class chain and add an additionally
     * call to that method.
     */
    public void resolveExtendedMethods() {
        for (MethodProfile method : mMethods.values()) {
            if (method.isDirectMember() || mSuperClass == null) {
                continue;
            }
            MethodProfile superMethod = mSuperClass.findMethod(
                    method.getMethodName(), method.getMethodParams());
            if (superMethod != null) {
                method.addMethodCall(superMethod);
            }
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

    /** Finds the given method from the class or its super classes. */
    private MethodProfile findMethod(String methodName, List<String> params) {
        if (isApiClass()) {
            return getOrCreateMethod(methodName, params);
        }
        String methodSignature = Utils.getMethodSignature(methodName, params);
        if (mMethods.containsKey(methodSignature)) {
            return mMethods.get(methodSignature);
        }
        return mSuperClass == null ? null : mSuperClass.findMethod(methodName, params);
    }

    private boolean matchAnyTypes(int typesValue) {
        return (mClassType & typesValue) != 0;
    }

    private boolean matchAllTypes(int typesValue) {
        return (mClassType & typesValue) == typesValue;
    }
}
