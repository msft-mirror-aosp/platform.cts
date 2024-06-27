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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Representation of a method included in the CTS package. */
public class MethodProfile {

    public final AnnotationManagement annotationManagement = new AnnotationManagement();

    private final String mMethod;

    private final ClassProfile mClass;

    private final List<String> mParams;

    private int mMethodType = 0;

    // A map of non-api methods called by this method with the method signature as the key.
    private final HashMap<String, MethodProfile> mCommonMethodCalls = new HashMap<>();

    // A map of api methods called by this method with the method signature as the key.
    private final HashMap<String, MethodProfile> mApiMethodCalls = new HashMap<>();

    // A map of api constructors called by this method with the method signature as the key.
    private final HashMap<String, MethodProfile> mApiConstructorCalls = new HashMap<>();

    private static final Set<String> JUNIT4_ANNOTATION_PATTERNS = new HashSet<>(
            List.of("org.junit.*")
    );

    public enum MethodType {
        JUNIT3(1),
        JUNIT4(2),
        /** A non-test method.*/
        COMMON(4),
        // TODO(slotus): Solve the override case.
        /** A method that is not extended from the super class. */
        DIRECT_MEMBER(8);

        private final int mValue;

        MethodType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public MethodProfile(
            ClassProfile classProfile, String methodName, List<String> params) {
        mClass = classProfile;
        mMethod = methodName;
        mParams = params;
    }

    public String getMethodName() {
        return mMethod;
    }

    public String getModuleName() {
        return mClass.getModuleName();
    }

    public String getPackageName() {
        return mClass.getPackageName();
    }

    public String getClassName() {
        return mClass.getClassName();
    }

    public List<String> getMethodParams() {
        return mParams;
    }

    public Map<String, MethodProfile> getApiMethodCalls() {
        return mApiMethodCalls;
    }

    public Map<String, MethodProfile> getApiConstructorCalls() {
        return mApiConstructorCalls;
    }

    public Map<String, MethodProfile> getCommonMethodCalls() {
        return mCommonMethodCalls;
    }

    public String getMethodSignatureWithClass() {
        return Utils.getMethodSignatureWithClass(
                mClass.getPackageName(), mClass.getClassName(), mMethod, mParams);
    }

    /** Adds an API method called by the method. **/
    public void addApiMethodCall(MethodProfile apiMethod) {
        mApiMethodCalls.putIfAbsent(apiMethod.getMethodSignatureWithClass(), apiMethod);
    }

    /** Adds an API constructor called by the method. **/
    public void addApiConstructorCall(MethodProfile apiConstructor) {
        mApiConstructorCalls.putIfAbsent(
                apiConstructor.getMethodSignatureWithClass(), apiConstructor);
    }

    /** Adds a non-API method called by the method. **/
    public void addCommonMethodCall(MethodProfile method) {
        mCommonMethodCalls.putIfAbsent(method.getMethodSignatureWithClass(), method);
    }

    /** Adds a method type for the method. */
    public void addMethodType(MethodType methodType) {
        mMethodType |= methodType.getValue();
    }

    /** Returns true if it is decided that whether this is a test method or not. */
    private boolean testMethodResolved() {
        return matchAnyTypes(
                MethodType.JUNIT3.getValue()
                        | MethodType.JUNIT4.getValue()
                        | MethodType.COMMON.getValue());
    }

    /** Returns true if the method is a test method. */
    public boolean isTestMethod() {
        if (!isJunit4Method() && !isJunit3Method()) {
            addMethodType(MethodType.COMMON);
            return false;
        }
        return true;
    }

    /** Returns true if the method is a JUnit3 test method. */
    protected boolean isJunit3Method() {
        if (testMethodResolved()) {
            return matchAllTypes(MethodType.JUNIT3.getValue());
        }
        if (mClass.isJunit3Class() && mMethod.startsWith("test")) {
            addMethodType(MethodType.JUNIT3);
            return true;
        }
        return false;
    }

    /** Returns true if the method is a JUnit4 test method. */
    protected boolean isJunit4Method() {
        if (testMethodResolved()) {
            return matchAllTypes(MethodType.JUNIT4.getValue());
        }
        for (ClassProfile annotation : annotationManagement.getAnnotations()) {
            for (String pattern : JUNIT4_ANNOTATION_PATTERNS) {
                if (annotation.getClassSignature().matches(pattern)) {
                    addMethodType(MethodType.JUNIT4);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchAnyTypes(int typesValue) {
        return (mMethodType & typesValue) != 0;
    }

    private boolean matchAllTypes(int typesValue) {
        return (mMethodType & typesValue) == typesValue;
    }
}
