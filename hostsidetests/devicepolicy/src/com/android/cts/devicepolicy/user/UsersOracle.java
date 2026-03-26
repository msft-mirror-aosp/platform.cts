/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.cts.devicepolicy.user;

import static android.multiuser.Flags.FLAG_PROFILES_FOR_ALL;

import static com.android.compatibility.common.util.UserUtil.CONFIG_SUPPORT_MANAGED_PROFILE_ON_NON_MAIN_USER;
import static com.android.tradefed.device.UserInfo.USER_NULL;
import static com.android.tradefed.device.UserInfo.USER_SYSTEM;
import static com.android.tradefed.targetprep.BaseSwitchUserTargetPreparer.PROPERTY_PREPARED_USER;

import com.android.compatibility.common.util.FlagsUtil;
import com.android.compatibility.common.util.UserUtil;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.SwitchUserTargetPreparer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * Class responsible for "predicting" which users should be used for DevicePolicy purposes.
 *
 * <p>In other words, it defines which user should be something (like the device owner), but doesn't
 * set it
 *
 * <p>Note: this class can be used "by itself" (with an instance obtained through {@link
 * #createInstance(TestInformation)}, but we recommend getting the singleton from {@link
 * DevicePolicyUsersPreparer#getUsersOracleInstance(TestInformation)} instead, otherwise the {@link
 * #getInitialCurrentUserId() initial user id} might not have been set correctly.
 */
public final class UsersOracle {

    private static final AtomicInteger sNextId = new AtomicInteger();

    private final int mId = sNextId.incrementAndGet();
    private final FlagsUtil mFlagsUtil;
    private final boolean mIsHsum;
    private final int mInitialCurrentUserId;
    private final Set<Integer> mPreExistingUserIds;
    private final @Nullable Integer mMainUserId;
    private final boolean mSupportsManagedProfilesForAll;
    // TODO(b/374832167): temporary workaround until profiles_for_all flag is ramped up
    private final boolean mIsAutomotive;

    /** Factory method. */
    public static UsersOracle createInstance(TestInformation testInformation)
            throws DeviceNotAvailableException {
        Objects.requireNonNull(testInformation, "testInformation cannot be null");

        return new UsersOracle(testInformation.getDevice(), getPreparedUserId(testInformation));
    }

    private UsersOracle(ITestDevice device, int initialCurrentUserId)
            throws DeviceNotAvailableException {
        mIsHsum = device.isHeadlessSystemUserMode();
        mInitialCurrentUserId = initialCurrentUserId;
        mPreExistingUserIds = device.getUserInfos().keySet();
        mMainUserId = device.getMainUserId();
        mSupportsManagedProfilesForAll =
                new UserUtil(device).isManagedProfileOnNonMainUserSupported();
        mIsAutomotive = device.hasFeature("android.hardware.type.automotive");
        mFlagsUtil = new FlagsUtil(device);
    }

    /**
     * Gets the id of the current user when the test module started.
     *
     * <p>It first tries to get the id set by a {@link SwitchUserTargetPreparer} preparer (such as
     * {@link DevicePolicyUsersPreparer}), falling back to the current user when that information is
     * not available.
     */
    public int getInitialCurrentUserId() {
        return mInitialCurrentUserId;
    }

    /** Gets the ids of the users that existed before the test module started. */
    public ImmutableSet<Integer> getPreExistingUserIds() {
        return ImmutableSet.copyOf(mPreExistingUserIds);
    }

    /**
     * Gets the id of the user that *should* be used by tests to set the device's {@code
     * DeviceOwner}.
     *
     * <p>Notice the *should* - it doesn't return which user is the *actual* {@code DeviceOwner}.
     */
    public int getDeviceOwnerUserId() {
        if (!mIsHsum) {
            return USER_SYSTEM;
        }
        return mInitialCurrentUserId;
    }

    /** Gets the id of a user that *should* be used by tests to set a {@code ProfileOwner} on. */
    public int getProfileOwnerUserId() {
        // TODO(b/374832167): for now it's hard-coding USER_SYSTEM on non-HSUM devices, but in
        // the long term it should simply return the current user as well.
        return mIsHsum ? mInitialCurrentUserId : USER_SYSTEM;
    }

    /**
     * Gets the id of the user that *could* be used as parent of profiles (created by the test), or
     * {@link USER_NULL} if none could be used.
     */
    public int getProfileParentUserId() {
        if (!mIsHsum) {
            // TODO(b/374832167): in theory we don't need this check - the logic below should
            // apply to non-HSUM devices as well as it checks for mSupportsProfilesForAll - but
            // given that the whole point of this class is to support HSUM device, it would be
            // safer (to avoid potential breakages) to simplify its logic for non-HSUM devices
            return USER_SYSTEM;
        }
        if (mMainUserId != null) {
            return mMainUserId;
        }
        if (mIsAutomotive) {
            CLog.d("getProfileParentUserId(): returning USER_NULL on automotive build");
            return USER_NULL;
        }
        Preconditions.checkState(
                mSupportsManagedProfilesForAll,
                "PO not supported on mainless-user device (either flag %s is disabled or "
                        + "device doesn't define %s - check logs)",
                FLAG_PROFILES_FOR_ALL,
                CONFIG_SUPPORT_MANAGED_PROFILE_ON_NON_MAIN_USER);
        return mInitialCurrentUserId;
    }

    private String getFlagValueForDebuggingPurposes(String flag) {
        try {
            return Boolean.toString(mFlagsUtil.getBooleanFlag(flag));
        } catch (Exception e) {
            return e.toString();
        }
    }

    @Override
    public String toString() {
        return String.format(
                "UsersOracle[mId=%d, mIsHsum=%b, mInitialCurrentUserId=%d, "
                        + "mMainUserId=%s, mSupportsProfilesForAll(flag %s=%b)=%B, "
                        + "mIsAutomotive=%b, mPreExistingUserIds=%s]",
                mId,
                mIsHsum,
                mInitialCurrentUserId,
                mMainUserId,
                FLAG_PROFILES_FOR_ALL,
                getFlagValueForDebuggingPurposes(FLAG_PROFILES_FOR_ALL),
                mSupportsManagedProfilesForAll,
                mIsAutomotive,
                mPreExistingUserIds);
    }

    private static int getPreparedUserId(TestInformation testInformation)
            throws DeviceNotAvailableException {
        int preparedUserId;
        String preparedUserFromProp = testInformation.properties().get(PROPERTY_PREPARED_USER);
        if (preparedUserFromProp != null) {
            preparedUserId = Integer.parseInt(preparedUserFromProp);
        } else {
            CLog.w("Property %s not set; will use current user instead", PROPERTY_PREPARED_USER);
            preparedUserId = testInformation.getDevice().getCurrentUser();
        }
        CLog.d("getPreparedUserId(): returning %d", preparedUserId);
        return preparedUserId;
    }
}
