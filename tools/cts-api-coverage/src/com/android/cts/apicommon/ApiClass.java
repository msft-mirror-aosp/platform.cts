/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.apicommon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Representation of a class in the API with constructors and methods. */
public class ApiClass implements Comparable<ApiClass>, HasCoverage {

    private static final String VOID = "void";

    private final String mName;

    private final boolean mDeprecated;

    private final boolean mAbstract;

    private final List<ApiConstructor> mApiConstructors = Collections.synchronizedList(
            new ArrayList<>());

    private final List<ApiMethod> mApiMethods = Collections.synchronizedList(new ArrayList<>());

    private final String mSuperClassName;

    private final String mPackageName;

    private ApiClass mSuperClass;

    private final Map<String, ApiClass> mInterfaceMap = new HashMap<>();

    // A map storing methods inherited from superclasses and interfaces.
    private Map<ApiClass, List<ApiMethod>> mInheritedMethods = null;

    /**
     * @param name The name of the class
     * @param deprecated true iff the class is marked as deprecated
     * @param classAbstract true iff the class is abstract
     * @param superClassName The fully qualified name of the super class
     */
    public ApiClass(
            String packageName,
            String name,
            boolean deprecated,
            boolean classAbstract,
            String superClassName) {
        mPackageName = packageName;
        mName = name;
        mDeprecated = deprecated;
        mAbstract = classAbstract;
        mSuperClassName = superClassName;
    }

    @Override
    public int compareTo(ApiClass another) {
        return mName.compareTo(another.mName);
    }

    @Override
    public String getName() {
        return mName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean isDeprecated() {
        return mDeprecated;
    }

    public String getSuperClassName() {
        return mSuperClassName;
    }

    public Map<ApiClass, List<ApiMethod>> getInheritedMethods() {
        return mInheritedMethods;
    }

    public boolean isAbstract() {
        return mAbstract;
    }

    public void setSuperClass(ApiClass superClass) {
        mSuperClass = superClass;
    }

    public void addInterface(String interfaceName) {
        mInterfaceMap.putIfAbsent(interfaceName, null);
    }

    public void resolveInterface(String interfaceName, ApiClass apiInterface) {
        mInterfaceMap.replace(interfaceName, apiInterface);
    }

    public Set<String> getInterfaceNames() {
        return mInterfaceMap.keySet();
    }

    public void addConstructor(ApiConstructor constructor) {
        if (getConstructor(constructor.getParameterTypes()).isEmpty()) {
            mApiConstructors.add(constructor);
        }
    }

    public Collection<ApiConstructor> getConstructors() {
        return Collections.unmodifiableList(mApiConstructors);
    }

    public void addMethod(ApiMethod method) {
        if (getDeclaredMethod(method.getName(), method.getParameterTypes()).isEmpty()) {
            mApiMethods.add(method);
        }
    }

    /** Look for a matching constructor and mark it as covered by the given test method */
    public void markConstructorCoveredTest(
            List<String> parameterTypes, TestMethodInfo testMethodInfo) {
        if (mSuperClass != null) {
            // Mark matching constructors in the superclass
            mSuperClass.markConstructorCoveredTest(parameterTypes, testMethodInfo);
        }
        Optional<ApiConstructor> apiConstructor = getConstructor(parameterTypes);
        apiConstructor.ifPresent(constructor -> constructor.setCoveredTest(testMethodInfo));
    }


    /** Look for a matching method and if found and mark it as covered by the given test method */
    public void markMethodCoveredTest(
            String name, List<String> parameterTypes, TestMethodInfo testMethodInfo) {
        if (mSuperClass != null) {
            // Mark matching methods in the super class
            // TODO(b/390548806): Only abstract method in the super class should be marked.
            mSuperClass.markMethodCoveredTest(name, parameterTypes, testMethodInfo);
        }
        if (!mInterfaceMap.isEmpty()) {
            // Mark matching methods in the interfaces
            for (ApiClass mInterface : mInterfaceMap.values()) {
                if (mInterface != null) {
                    mInterface.markMethodCoveredTest(name, parameterTypes, testMethodInfo);
                }
            }
        }
        Optional<ApiMethod> apiMethod = getDeclaredMethod(name, parameterTypes);
        apiMethod.ifPresent(method -> method.setCoveredTest(testMethodInfo));
    }

    /** Look for a matching constructor and mark it as covered */
    public void markConstructorCovered(List<String> parameterTypes, String coveredbyApk) {
        if (mSuperClass != null) {
            // Mark matching constructors in the superclass
            mSuperClass.markConstructorCovered(parameterTypes, coveredbyApk);
        }
        Optional<ApiConstructor> apiConstructor = getConstructor(parameterTypes);
        apiConstructor.ifPresent(constructor -> constructor.setCovered(coveredbyApk));
    }

    /** Look for a matching method and if found and mark it as covered */
    public void markMethodCovered(String name, List<String> parameterTypes, String coveredbyApk) {
        if (mSuperClass != null) {
            // Mark matching methods in the super class
            // TODO(b/390548806): Only abstract method in the super class should be marked.
            mSuperClass.markMethodCovered(name, parameterTypes, coveredbyApk);
        }
        if (!mInterfaceMap.isEmpty()) {
            // Mark matching methods in the interfaces
            for (ApiClass mInterface : mInterfaceMap.values()) {
                if (mInterface != null) {
                    mInterface.markMethodCovered(name, parameterTypes, coveredbyApk);
                }
            }
        }
        Optional<ApiMethod> apiMethod = getDeclaredMethod(name, parameterTypes);
        apiMethod.ifPresent(method -> method.setCovered(coveredbyApk));
    }

    public int getNumCoveredMethods() {
        int numCovered = 0;
        for (ApiConstructor constructor : mApiConstructors) {
            if (constructor.isCovered()) {
                numCovered++;
            }
        }
        for (ApiMethod method : mApiMethods) {
            if (method.isCovered()) {
                numCovered++;
            }
        }
        return numCovered;
    }

    public int getTotalMethods() {
        return mApiConstructors.size() + mApiMethods.size();
    }

    @Override
    public float getCoveragePercentage() {
        if (getTotalMethods() == 0) {
            return 100;
        } else {
            return (float) getNumCoveredMethods() / getTotalMethods() * 100;
        }
    }

    @Override
    public int getMemberSize() {
        return getTotalMethods();
    }

    public Collection<ApiMethod> getDeclaredMethods() {
        return Collections.unmodifiableList(mApiMethods);
    }

    /** Finds the given API method. */
    public Optional<ApiMethod> getDeclaredMethod(String name, List<String> parameterTypes) {
        return mApiMethods.stream()
                .filter(method -> compareMethod(name, parameterTypes, method))
                .findFirst();
    }

    /** Finds the given inherited API methods. */
    public Map<ApiClass, ApiMethod> getInheritedMethods(String name, List<String> parameterTypes) {
        Map<ApiClass, ApiMethod> inheritedMethod = new HashMap<>();
        mInheritedMethods.forEach(
                (apiClass, apiMethods) ->
                        apiMethods.stream()
                                .filter(method -> compareMethod(name, parameterTypes, method))
                                .forEach(method -> inheritedMethod.put(apiClass, method)));
        return inheritedMethod;
    }

    /** Finds the given API method including both inherited and directly declared methods. */
    public Optional<ApiMethod> getMethod(String name, List<String> parameterTypes) {
        Map<ApiClass, ApiMethod> methods = getInheritedMethods(name, parameterTypes);
        if (methods.isEmpty()) {
            return getDeclaredMethod(name, parameterTypes);
        }
        return methods.values().stream().findFirst();
    }

    /**
     * Retrieves a map of methods that are overridden by a method with the given name and parameter
     * types in this class, including methods from superclasses and interfaces.
     *
     * @param name The name of the method to check for overriding.
     * @param parameterTypes The parameter types of the method.
     * @return A map where the key is the superclass or interface, and the value is the overriding
     *     method.
     */
    public Map<ApiClass, ApiMethod> getOverriddenMethods(String name, List<String> parameterTypes) {
        Map<ApiClass, ApiMethod> overriddenMethods = new HashMap<>();
        // If the method is not directly declared in this class, it cannot override anything.
        if (getDeclaredMethod(name, parameterTypes).isEmpty()) {
            return overriddenMethods;
        }
        if (mSuperClass != null) {
            Optional<ApiMethod> method = mSuperClass.getMethod(name, parameterTypes);
            method.ifPresent(apiMethod -> overriddenMethods.put(mSuperClass, apiMethod));
        }
        for (ApiClass interfaceClass : mInterfaceMap.values()) {
            if (interfaceClass != null) {
                Optional<ApiMethod> method = interfaceClass.getMethod(name, parameterTypes);
                method.ifPresent(apiMethod -> overriddenMethods.put(interfaceClass, apiMethod));
            }
        }
        return overriddenMethods;
    }

    /** Resolves all inherited methods from superclasses and interfaces. */
    public void resolveInheritedMethods() {
        if (mInheritedMethods != null) {
            return;
        }
        mInheritedMethods = new HashMap<>();
        if (mSuperClass != null) {
            resolveInheritedMethods(mSuperClass);
        }
        mInterfaceMap
                .values()
                .forEach(
                        interfaceClass -> {
                            if (interfaceClass != null) {
                                resolveInheritedMethods(interfaceClass);
                            }
                        });
    }

    /**
     * Adds an inherited method from a superclass or interface only if a method with the same
     * signature does not exist in this ApiClass.
     *
     * @param superClass The superclass or interface from which the method is inherited.
     * @param inheritedMethod The inherited method.
     */
    private void addInheritedMethod(ApiClass superClass, ApiMethod inheritedMethod) {
        if (getDeclaredMethod(inheritedMethod.getName(), inheritedMethod.getParameterTypes())
                .isEmpty()) {
            mInheritedMethods.putIfAbsent(superClass, new ArrayList<>());
            mInheritedMethods.get(superClass).add(inheritedMethod);
        }
    }

    private void addInheritedMethods(ApiClass superClass, List<ApiMethod> inheritedMethods) {
        inheritedMethods.forEach(method -> addInheritedMethod(superClass, method));
    }

    /**
     * Recursively resolves the inherited methods from a superclass or interface.
     *
     * @param superClass The superclass or interface to resolve inherited methods from.
     */
    private void resolveInheritedMethods(ApiClass superClass) {
        // Skip java.lang.Object, which can make the report large.
        if (String.format("%s.%s", superClass.getPackageName(), superClass.getName())
                .startsWith("java.lang.Object")) {
            return;
        }
        superClass.resolveInheritedMethods();
        superClass.getDeclaredMethods().forEach(method -> addInheritedMethod(superClass, method));
        superClass
                .getInheritedMethods()
                .values()
                .forEach(methods -> addInheritedMethods(superClass, methods));
    }

    /**
     * Compares a given method name and parameter types with another ApiMethod for equality.
     *
     * @param name The name of the method to compare.
     * @param parameterTypes The parameter types of the method to compare.
     * @param another The ApiMethod to compare against.
     * @return true if the names and parameter types match.
     */
    private boolean compareMethod(String name, List<String> parameterTypes, ApiMethod another) {
        boolean methodNameMatch = name.equals(another.getName());
        boolean parameterTypeMatch =
                compareParameterTypes(another.getParameterTypes(), parameterTypes);
        return methodNameMatch && parameterTypeMatch;
    }

    /**
     * The method compares two lists of parameters. If the {@code apiParameterTypeList} contains
     * generic types, test parameter types are ignored.
     *
     * @param apiParameterTypeList The list of parameter types from the API
     * @param testParameterTypeList The list of parameter types used in a test
     * @return true iff the list of types are the same.
     */
    private static boolean compareParameterTypes(
            List<String> apiParameterTypeList, List<String> testParameterTypeList) {
        if (apiParameterTypeList.equals(testParameterTypeList)) {
            return true;
        }
        if (apiParameterTypeList.size() != testParameterTypeList.size()) {
            return false;
        }

        for (int i = 0; i < apiParameterTypeList.size(); i++) {
            String apiParameterType = apiParameterTypeList.get(i);
            String testParameterType = testParameterTypeList.get(i);
            if (!compareType(apiParameterType, testParameterType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true iff the parameter is a var arg parameter.
     */
    private static boolean isVarArg(String parameter) {
        return parameter.endsWith("...");
    }

    /**
     * Compare class types.
     * @param apiType The type as reported by the api
     * @param testType The type as found used in a test
     * @return true iff the strings are equal,
     * or the apiType is generic and the test type is not void
     */
    private static boolean compareType(String apiType, String testType) {
        return apiType.equals(testType)
                || (isGenericType(apiType) && !testType.equals(VOID))
                || (isGenericArrayType(apiType) && isArrayType(testType))
                || (isVarArg(apiType) && isArrayType(testType)
                    && apiType.startsWith(testType.substring(0, testType.indexOf("["))));
    }

    /**
     * @return true iff the given parameterType is a generic type.
     */
    private static boolean isGenericType(String type) {
        return type.length() == 1
                && type.charAt(0) >= 'A'
                && type.charAt(0) <= 'Z';
    }

    /**
     * @return true iff {@code type} ends with an [].
     */
    private static boolean isArrayType(String type) {
        return type.endsWith("[]");
    }

    /**
     * @return true iff the given parameterType is an array of generic type.
     */
    private static boolean isGenericArrayType(String type) {
        return type.length() == 3 && isGenericType(type.substring(0, 1)) && isArrayType(type);
    }

    private Optional<ApiConstructor> getConstructor(List<String> parameterTypes) {
        for (ApiConstructor constructor : mApiConstructors) {
            if (compareParameterTypes(constructor.getParameterTypes(), parameterTypes)) {
                return Optional.of(constructor);
            }
        }
        return Optional.empty();
    }
}
