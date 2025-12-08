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

import static android.app.admin.flags.Flags.FLAG_DEVICE_OWNER_FOR_ALL;
import static android.multiuser.Flags.FLAG_PROFILES_FOR_ALL;

import static com.android.compatibility.common.util.UserUtil.CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER;
import static com.android.tradefed.device.UserInfo.USER_NULL;
import static com.android.tradefed.device.UserInfo.USER_SYSTEM;

import com.android.compatibility.common.util.BaseSwitchFullUserTargetPreparer;
import com.android.compatibility.common.util.FlagsUtil;
import com.android.compatibility.common.util.UserUtil;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * Class responsible for "predicting" which users should be used for DevicePolicy purposes.
 *
 * <p>In other words, it defines which user should be something (like the device owner), but doesn't
 * set it
 *
 * <p><b>NOTE:</b> callers <b>MUST</b> include {@link DevicePolicyUsersTargetPreparer} in their
 * {@code AndroidTest.xml}.
 */
public final class DevicePolicyUsersPreparer extends BaseSwitchFullUserTargetPreparer {

    private static @Nullable UsersOracle sOracle;

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        super.setUp(testInformation);
        sOracle = new UsersOracle(testInformation.getDevice(), getPreparedUserId());
        try {
            // Log what it will return...
            logAndDisplay(
                    "preview: getInitialCurrentUserId()=%s, getDeviceOwnerUserId()=%s, "
                            + "getProfileParentUserIds()=%s, "
                            + "getProfileParentUserId()=%s, getPreExistingUserIds()=%s,"
                            + "isDeviceOwnerSupportedOnAnyFullUsers()=%s",
                    safeToString(DevicePolicyUsersPreparer::getInitialCurrentUserId),
                    safeToString(DevicePolicyUsersPreparer::getDeviceOwnerUserId),
                    safeToString(DevicePolicyUsersPreparer::getProfileParentUserIds),
                    safeToString(DevicePolicyUsersPreparer::getProfileParentUserId),
                    safeToString(DevicePolicyUsersPreparer::getPreExistingUserIds),
                    safeToString(DevicePolicyUsersPreparer::isDeviceOwnerSupportedOnAnyFullUsers));
        } catch (Exception e) {
            // ... but don't fail
            CLog.e("Failed to log initial state: %s", e);
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        sOracle = null;
        super.tearDown(testInformation, e);
    }

    /** Gets the id of the current user when the test module started. */
    public static int getInitialCurrentUserId() {
        return getOracle().mInitialCurrentUserId;
    }

    /** Gets the ids of the users that existed before the test module started. */
    public static ImmutableSet<Integer> getPreExistingUserIds() {
        return ImmutableSet.copyOf(getOracle().mPreExistingUserIds);
    }

    /**
     * Gets the id of the user that *should* be used by tests to set the device's {@code
     * DeviceOwner}.
     *
     * <p>Notice the *should* - it doesn't return which user is the *actual* {@code DeviceOwner}.
     */
    public static int getDeviceOwnerUserId() {
        return getOracle().getDeviceOwnerUserId();
    }

    /** Gets the id of a user that *should* be used by tests to set a {@code ProfileOwner} on. */
    public static int getProfileOwnerUserId() {
        return getOracle().getProfileOwnerUserId();
    }

    /** Gets whether device owner could be set on any full user. */
    public static boolean isDeviceOwnerSupportedOnAnyFullUsers() {
        return getOracle().isDeviceOwnerSupportedOnAnyFullUsers();
    }

    /**
     * Gets the ids of any user that *could* be used as parent of profiles (created by the test).
     *
     * @deprecated use {@link #getProfileParentUserId()} instead.
     */
    @Deprecated
    public static ImmutableList<Integer> getProfileParentUserIds() {
        return getOracle().getProfileParentUserIds();
    }

    /**
     * Gets the id of the user that *could* be used as parent of profiles (created by the test), or
     * {@link USER_NULL} if none could be used.
     */
    public static int getProfileParentUserId() {
        return getOracle().getProfileParentUserId();
    }

    @FormatMethod
    private static void logAndDisplay(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        CLog.logAndDisplay(LogLevel.INFO, msgFmt, msgArgs);
    }

    private static String safeToString(Supplier<Object> supplier) {
        try {
            return supplier.get().toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static UsersOracle getOracle() {
        Preconditions.checkState(
                sOracle != null,
                "Not initialized yet - did you include "
                        + "DevicePolicyUsersTargetPreparer in your AndroidTest.xml?");
        return sOracle;
    }

    private static final class UsersOracle {

        private final boolean mIsHsum;
        private final int mInitialCurrentUserId;
        private final Set<Integer> mPreExistingUserIds;
        private final @Nullable Integer mMainUserId;
        private final boolean mSupportsProfilesForAll;
        private final boolean mSupportsDeviceOwnerForAll;
        // TODO(b/35372278): temporary workaround until flag is ramped up
        private final boolean mIsAutomotive;

        private UsersOracle(ITestDevice device, int initialCurrentUserId)
                throws DeviceNotAvailableException {
            mIsHsum = device.isHeadlessSystemUserMode();
            mInitialCurrentUserId = initialCurrentUserId;
            mPreExistingUserIds = device.getUserInfos().keySet();
            mMainUserId = device.getMainUserId();
            mSupportsProfilesForAll = new UserUtil(device).isProfilesOnNonMainUserSupported();
            mIsAutomotive = device.hasFeature("android.hardware.type.automotive");
            FlagsUtil flagsUtil = new FlagsUtil(device);
            mSupportsDeviceOwnerForAll = flagsUtil.getBooleanFlag(FLAG_DEVICE_OWNER_FOR_ALL);
            logAndDisplay(
                    "setUp(): mIsHsum=%b, mInitialCurrentUserId=%d, mMainUserId=%s, "
                            + "mSupportsProfilesForAll(flag %s=%b)=%B, "
                            + "mSupportsDeviceOwnerForAll(flag %s)=%B, "
                            + "mIsAutomotive=%b, mPreExistingUserIds=%s",
                    mIsHsum,
                    mInitialCurrentUserId,
                    mMainUserId,
                    FLAG_PROFILES_FOR_ALL,
                    flagsUtil.getBooleanFlag(FLAG_PROFILES_FOR_ALL),
                    mSupportsProfilesForAll,
                    FLAG_DEVICE_OWNER_FOR_ALL,
                    mSupportsDeviceOwnerForAll,
                    mIsAutomotive,
                    mPreExistingUserIds);
        }

        private boolean isDeviceOwnerSupportedOnAnyFullUsers() {
            return mSupportsDeviceOwnerForAll;
        }

        private int getDeviceOwnerUserId() {
            if (!mIsHsum) {
                return USER_SYSTEM;
            }
            if (mSupportsDeviceOwnerForAll) {
                return mInitialCurrentUserId;
            }
            if (mMainUserId == null && mIsAutomotive) {
                CLog.d("getDeviceOwnerUserId(): returning initial user (%d) on automotive build");
                return mInitialCurrentUserId;
            }
            Preconditions.checkState(
                    mMainUserId != null,
                    "DO not supported on mainless-user device (most likely flag %s is disabled - "
                            + "check logs)",
                    FLAG_DEVICE_OWNER_FOR_ALL);
            return mMainUserId;
        }

        private int getProfileOwnerUserId() {
            // TODO(b/374832167): for now it's hard-coding USER_SYSTEM on non-HSUM devices, but in
            // the long term it should simply return the current user as well.
            return mIsHsum ? mInitialCurrentUserId : USER_SYSTEM;
        }

        private int getProfileParentUserId() {
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
                    mSupportsProfilesForAll,
                    "PO not supported on mainless-user device (either flag %s is disabled or "
                            + "device doesn't define %s - check logs)",
                    FLAG_PROFILES_FOR_ALL,
                    CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER);
            return mInitialCurrentUserId;
        }

        /**
         * @deprecated use {@link #getProfileParentUserId()} instead.
         */
        @Deprecated
        private ImmutableList<Integer> getProfileParentUserIds() {
            if (!mIsHsum) {
                // TODO(b/374832167): in theory we don't need this check - the logic below should
                // apply to non-HSUM devices as well as it checks for mSupportsProfilesForAll - but
                // given that the whole point of this class is to support HSUM device, it would be
                // safer (to avoid potential breakages) to simplify its logic for non-HSUM devices
                return ImmutableList.of(USER_SYSTEM);
            }
            if (mMainUserId != null) {
                return ImmutableList.of(mMainUserId);
            }
            if (mIsAutomotive) {
                CLog.d(
                        "getProfileParentUserIds(): returning current user (%d) on automotive"
                                + " build",
                        mInitialCurrentUserId);
                return ImmutableList.of(mInitialCurrentUserId);
            }
            Preconditions.checkState(
                    mSupportsProfilesForAll,
                    "PO not supported on mainless-user device (either flag %s is disabled or "
                            + "device doesn't define %s - check logs)",
                    FLAG_PROFILES_FOR_ALL,
                    CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER);
            return ImmutableList.of(mInitialCurrentUserId);
        }
    }
}
