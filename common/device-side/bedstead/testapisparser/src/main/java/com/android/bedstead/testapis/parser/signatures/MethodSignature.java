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

package com.android.bedstead.testapis.parser.signatures;


import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/**
 * Represents a minimal representation of a method for comparison purposes.
 */
public final class MethodSignature {

    private final String mFrameworkClass;
    private final ReturnType mReturnType;

    public String getFrameworkClass() {
        return mFrameworkClass;
    }

    private final String mName;
    private final ImmutableList<String> mParameterTypes;
    private final boolean mIsStatic;
    private final boolean isGetter;

    private static final List<String> FIELD_ANNOTATIONS_TO_IGNORE =
            ImmutableList.of("@NonNull", "@Nullable");

    public MethodSignature(
            String frameworkClass,
            String name,
            ReturnType returnType,
            ImmutableList<String> parameterTypes,
            boolean isStatic,
            boolean isGetter) {
        this.mFrameworkClass = frameworkClass;
        this.mName = name;
        this.mReturnType = returnType;
        this.mParameterTypes = parameterTypes;
        this.mIsStatic = isStatic;
        this.isGetter = isGetter;
    }
    public ReturnType getReturnType() {
        return mReturnType;
    }

    public String getName() {
        return mName;
    }

    public ImmutableList<String> getParameterTypes() {
        return mParameterTypes;
    }

    public boolean isStatic() {
        return mIsStatic;
    }

    public boolean isGetter() {
        return isGetter;
    }

    public static class ReturnType {
        private final String type;

        private String proxyType;

        public ReturnType(String type, String proxyType) {
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

        @Override
        public String toString() {
            return "ReturnType{" +
                    "type='" + type + '\'' +
                    ", proxyType='" + proxyType + '\'' +
                    '}';
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
