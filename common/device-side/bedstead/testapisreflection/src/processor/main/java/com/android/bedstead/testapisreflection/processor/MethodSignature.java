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

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Represents a minimal representation of a method for comparison purposes
 */
final class MethodSignature {

    private final String mFrameworkClass;
    private final ReturnType mReturnType;

    public String getFrameworkClass() {
        return mFrameworkClass;
    }

    private final String mName;
    private final ImmutableList<String> mParameterTypes;
    private final boolean mIsStatic;
    private final boolean isGetter;

    ReturnType getReturnType() {
        return mReturnType;
    }

    String getName() {
        return mName;
    }

    ImmutableList<String> getParameterTypes() {
        return mParameterTypes;
    }

    public boolean isStatic() {
        return mIsStatic;
    }

    public boolean isGetter() {
        return isGetter;
    }

    private MethodSignature(String frameworkClass,
            String name, ReturnType returnType, ImmutableList<String> parameterTypes,
            boolean isStatic, boolean isGetter) {
        this.mFrameworkClass = frameworkClass;
        this.mName = name;
        this.mReturnType = returnType;
        this.mParameterTypes = parameterTypes;
        this.mIsStatic = isStatic;
        this.isGetter = isGetter;
    }

    /** Create a {@link MethodSignature} for the given {@code apiString}. */
    static MethodSignature forApiString(String apiString) {
        try {
            String frameworkClass = apiString.substring(0, apiString.indexOf(","));
            String method = apiString.substring(apiString.indexOf(",") + 1);
            String[] methodParts = method.trim().replaceAll(" +", " ").split(" ");

            if (method.contains("static")) {
                String name = methodParts[3].substring(0, methodParts[3].indexOf("("));
                String returnType = methodParts[2];

                return new MethodSignature(frameworkClass, name, new ReturnType(returnType,
                        /* proxyType= */ null), parameterTypes(method), /* isStatic= */ true,
                        /* isGetter= */ false);
            } else {
                String name = methodParts[2].substring(0, methodParts[2].indexOf("("));
                String returnType = methodParts[1];
                ImmutableList<String> parameterTypes = parameterTypes(method);

                return new MethodSignature(frameworkClass, name, new ReturnType(returnType,
                        /* proxyType= */ null), parameterTypes, /* isStatic= */ false,
                        /* isGetter= */ isGetterInternal(name, parameterTypes));
            }
        } catch (Exception e) {
            throw new RuntimeException("TestApisReflection: unable to parse Test Api: " + apiString,
                    e);
        }
    }

    private static ImmutableList<String> parameterTypes(String method) {
        ImmutableList<String> parameterTypes = ImmutableList.of();
        String params = method.substring(method.indexOf("(") + 1, method.indexOf(")"))
                .replaceAll(" +", "");

        if (params.length() > 0) {
            parameterTypes = ImmutableList.copyOf(params.split(","));
        }

        return parameterTypes;
    }

    private static boolean isGetterInternal(String methodName,
            ImmutableList<String> parameterTypes) {
        // We only consider methods with zero parameters as true 'getters' since this is to
        // convert them to a kotlin get property later on.
        if (!parameterTypes.isEmpty()) {
            return false;
        }

        if (methodName.startsWith("get")) {
            return Character.isUpperCase(methodName.charAt(3));
        }
        if (methodName.startsWith("is")) {
            return Character.isUpperCase(methodName.charAt(2));
        }

        return false;
    }

    static class ReturnType {
        private final String type;

        private String proxyType;

        ReturnType(String type, String proxyType) {
            this.type = type;
            this.proxyType = proxyType;
        }

        public void setProxyType(String proxyType) {
            this.proxyType = proxyType;
        }

        public String getType() {
            return type;
        }

        public String getProxyType() {
            return proxyType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReturnType)) return false;
            ReturnType that = (ReturnType) o;
            return Objects.equals(type, that.type) && Objects.equals(proxyType,
                    that.proxyType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, proxyType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodSignature)) return false;
        MethodSignature that = (MethodSignature) o;
        return mIsStatic == that.mIsStatic && Objects.equals(mFrameworkClass,
                that.mFrameworkClass) && Objects.equals(mReturnType, that.mReturnType)
                && Objects.equals(mName, that.mName) && Objects.equals(
                mParameterTypes, that.mParameterTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFrameworkClass, mReturnType, mName, mParameterTypes, mIsStatic);
    }

    @Override
    public String toString() {
        return "MethodSignature{" +
                "mFrameworkClass='" + mFrameworkClass + '\'' +
                ", mReturnType=" + mReturnType +
                ", mName='" + mName + '\'' +
                ", mParameterTypes=" + mParameterTypes +
                ", mIsStatic=" + mIsStatic +
                '}';
    }
}
