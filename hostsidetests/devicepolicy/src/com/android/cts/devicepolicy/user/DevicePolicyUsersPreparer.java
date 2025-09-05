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

import static com.android.tradefed.device.UserInfo.USER_SYSTEM;

import android.platform.test.flag.junit.host.DeviceFlags;

import com.android.compatibility.common.util.BaseSwitchFullUserTargetPreparer;
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
                            + "getProfileParentUserIds()=%s, getPreExistingUserIds()=%s",
                    safeToString(DevicePolicyUsersPreparer::getInitialCurrentUserId),
                    safeToString(DevicePolicyUsersPreparer::getDeviceOwnerUserId),
                    safeToString(DevicePolicyUsersPreparer::getProfileParentUserIds),
                    safeToString(DevicePolicyUsersPreparer::getPreExistingUserIds));
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

    /**
     * Gets the ids of any user that *could* be used as parent of profiles (created by the test).
     */
    public static ImmutableList<Integer> getProfileParentUserIds() {
        return ImmutableList.of(getOracle().getProfileParentUserId());
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

        private UsersOracle(ITestDevice device, int initialCurrentUserId)
                throws DeviceNotAvailableException {
            mIsHsum = device.isHeadlessSystemUserMode();
            mInitialCurrentUserId = initialCurrentUserId;
            mPreExistingUserIds = device.getUserInfos().keySet();
            mMainUserId = device.getMainUserId();
            mSupportsProfilesForAll = new UserUtil(device).isProfilesOnNonMainUserSupported();
            var flags = DeviceFlags.createDeviceFlags(device);
            String supportsDoForAllFlag = flags.getFlagValue(FLAG_DEVICE_OWNER_FOR_ALL);
            if (true) {
                // TODO(b/383180991): temporary hac^H^H^H workaround as `device_config list` is
                // returning `false` even when it's enabled; probably because the flag is new and
                // hasn't being ramped-up to staging yet, so it's not in the build.
                // But before removing this workaround, we need to make sure it would also work
                // if developers manually change the flag (and same goes for
                // UserUtils.isProfilesOnNonMainUserSupported())
                String hackyFlagValue = device.executeShellCommand(
                        "aflags list | grep android.app.admin.flags.device_owner_for_all");
                mSupportsDeviceOwnerForAll = hackyFlagValue.trim().contains("enabled");
            } else {
                mSupportsDeviceOwnerForAll = Boolean.valueOf(supportsDoForAllFlag);
            }
            logAndDisplay(
                    "setUp(): isHsum=%b, initialCurrentUser=%d, mainUserId=%s, "
                            + "supportsProfilesForAll=%b, supportsDeviceOwnerForAll=%b "
                            + "(flag %s=%s), mPreExistingUserIds=%s",
                    mIsHsum,
                    mInitialCurrentUserId,
                    mMainUserId,
                    mSupportsProfilesForAll,
                    mSupportsDeviceOwnerForAll,
                    FLAG_DEVICE_OWNER_FOR_ALL,
                    supportsDoForAllFlag,
                    mPreExistingUserIds);
        }

        private int getDeviceOwnerUserId() {
            if (!mIsHsum) {
                return USER_SYSTEM;
            }
            if (mSupportsDeviceOwnerForAll) {
                return mInitialCurrentUserId;
            }
            Preconditions.checkState(mMainUserId != null,
                    "DO not supported on mainless-user device");
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

            // TODO(b/374832167): we could add all full users, but given that no test is currently
            // setting profiles in more than one user (and mostly likely tests that do so would be
            // added on CtsDevicePolicyTestCases), we're just returning one user.
            return mainOrCurrentUserId();
        }

        private int mainOrCurrentUserId() {
            if (mMainUserId != null) {
                return mMainUserId;
            }
            Preconditions.checkState(mSupportsProfilesForAll, "device does not have main user");
            return mInitialCurrentUserId;
        }
    }
}
