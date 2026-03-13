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

import static com.android.tradefed.device.UserInfo.USER_NULL;

import com.android.compatibility.common.util.BaseSwitchFullUserTargetPreparer;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.SwitchUserTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Convenience {@link SwitchUserTargetPreparer} that provides a {@link UsersOracle} singleton.
 *
 * <p><b>NOTE:</b> callers <b>MUST</b> include a {@code DevicePolicyUsersPreparer} in their {@code
 * AndroidTest.xml} before calling {@link #getUsersOracleInstance(TestInformation)}.
 */
public final class DevicePolicyUsersPreparer extends BaseSwitchFullUserTargetPreparer {

    // Singleton - set once, never reset
    private static final AtomicReference<UsersOracle> sOracle = new AtomicReference<>();

    private @Nullable UsersOracle mOracle;

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        super.setUp(testInformation);
        mOracle = sOracle.get();
        if (mOracle != null) {
            CLog.w("sOracle singleton already set, using it instead: %s", mOracle);
        } else {
            mOracle = createAndSetOracleInstance(testInformation);
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        CLog.d("Resetting mOracle (but sOracle will remain)");
        mOracle = null;
        super.tearDown(testInformation, e);
    }

    /**
     * Gets the id of the current user when the test module started.
     *
     * @deprecated should call {@link UsersOracle#getInitialCurrentUserId()} instead.
     */
    @Deprecated
    public static int getInitialCurrentUserId() {
        return getOracle().getInitialCurrentUserId();
    }

    /**
     * Gets the ids of the users that existed before the test module started.
     *
     * @deprecated should call {@link UsersOracle#getPreExistingUserIds()} instead.
     */
    @Deprecated
    public static ImmutableSet<Integer> getPreExistingUserIds() {
        return getOracle().getPreExistingUserIds();
    }

    /**
     * Gets the id of the user that *should* be used by tests to set the device's {@code
     * DeviceOwner}.
     *
     * <p>Notice the *should* - it doesn't return which user is the *actual* {@code DeviceOwner}.
     *
     * @deprecated should call {@link UsersOracle#getDeviceOwnerUserId(TestInformation)} instead.
     */
    @Deprecated
    public static int getDeviceOwnerUserId() {
        return getOracle().getDeviceOwnerUserId();
    }

    /**
     * Gets the id of a user that *should* be used by tests to set a {@code ProfileOwner} on.
     *
     * @deprecated should call {@link UsersOracle#getProfileOwnerUserId()} instead.
     */
    @Deprecated
    public static int getProfileOwnerUserId() {
        return getOracle().getProfileOwnerUserId();
    }

    /**
     * Gets whether device owner could be set on any full user.
     *
     * @deprecated should call {@link UsersOracle#getProfileOwnerUserId()} instead.
     */
    @Deprecated
    public static boolean isDeviceOwnerSupportedOnAnyFullUsers() {
        return getOracle().isDeviceOwnerSupportedOnAnyFullUsers();
    }

    /**
     * Gets the id of the user that *could* be used as parent of profiles (created by the test), or
     * {@link USER_NULL} if none could be used.
     *
     * @deprecated should call {@link UsersOracle#getProfileParentUserId()} instead.
     */
    @Deprecated
    public static int getProfileParentUserId() {
        return getOracle().getProfileParentUserId();
    }

    @FormatMethod
    private static void logAndDisplay(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        CLog.logAndDisplay(LogLevel.INFO, msgFmt, msgArgs);
    }

    private static UsersOracle getOracle() {
        var oracle = sOracle.get();
        Preconditions.checkState(
                oracle != null,
                "Not initialized yet - did you include "
                        + "DevicePolicyUsersTargetPreparer in your AndroidTest.xml?");
        return oracle;
    }

    /**
     * Gets the {@link UsersOracle} singleton.
     *
     * @return singleton created on {@code setUp()}, or a new one if {@code setUp()} was not called
     *     (or if it {@code #tearDown()} was called afterwards).
     */
    public static UsersOracle getUsersOracleInstance(TestInformation testInformation)
            throws DeviceNotAvailableException {
        var oracle = sOracle.get();
        if (oracle == null) {
            // Should have been set on setUp()
            CLog.w(
                    "getUsersOracleInstance(): static Oracle not set yet - did you include "
                            + "DevicePolicyUsersTargetPreparer in your AndroidTest.xml?");
            oracle = createAndSetOracleInstance(testInformation);
        }
        return oracle;
    }

    private static UsersOracle createAndSetOracleInstance(TestInformation testInformation)
            throws DeviceNotAvailableException {
        var oracle = UsersOracle.createInstance(testInformation);
        if (sOracle.compareAndSet(null, oracle)) {
            logAndDisplay("Set sOracle singleton to %s", oracle);
        } else {
            // Not a big deal, but better log it..
            CLog.w(
                    "sOracle (%s) was set by another thread in parallel after a new one (%s) was"
                            + " created",
                    sOracle, oracle);
        }
        return oracle;
    }
}
