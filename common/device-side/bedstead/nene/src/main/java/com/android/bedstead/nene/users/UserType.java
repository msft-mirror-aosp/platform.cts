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

package com.android.bedstead.nene.users;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Set;

/**
 * Represents information about an Android User type.
 *
 * <p>Only supported on Android 11 and above.
 */
@RequiresApi(Build.VERSION_CODES.R)
public final class UserType {

    public static final int UNLIMITED = -1;

    public enum BaseType {
        SYSTEM, PROFILE, FULL
    }

    static final class MutableUserType {
        String mName;
        Set<BaseType> mBaseType;
        Boolean mEnabled;
        Integer mMaxAllowed;
        Integer mMaxAllowedPerParent;
    }

    private final MutableUserType mMutableUserType;

    UserType(MutableUserType mutableUserType) {
        mMutableUserType = mutableUserType;
    }

    public String name() {
        return mMutableUserType.mName;
    }

    public Set<BaseType> baseType() {
        return mMutableUserType.mBaseType;
    }

    public Boolean enabled() {
        return mMutableUserType.mEnabled;
    }

    /**
     * The maximum number of this user type allowed on the device.
     *
     * <p>This value will be {@link #UNLIMITED} if there is no limit.
     */
    public Integer maxAllowed() {
        return mMutableUserType.mMaxAllowed;
    }

    /**
     * The maximum number of this user type allowed for a single parent profile
     *
     * <p>This value will be {@link #UNLIMITED} if there is no limit.
     */
    public Integer maxAllowedPerParent() {
        return mMutableUserType.mMaxAllowedPerParent;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("UserType{");
        stringBuilder.append("name=" + mMutableUserType.mName);
        stringBuilder.append(", baseType=" + mMutableUserType.mBaseType);
        stringBuilder.append(", enabled=" + mMutableUserType.mEnabled);
        stringBuilder.append(", maxAllowed=" + mMutableUserType.mMaxAllowed);
        stringBuilder.append(", maxAllowedPerParent=" + mMutableUserType.mMaxAllowedPerParent);
        return stringBuilder.toString();
    }
}
