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

package com.android.cts.devicepolicy;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.base.Preconditions;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Base class for {@link DeviceOwnerTest} and {@link HeadlessSystemUserDeviceOwnerTest} - it
 * provides the common infra, but doesn't have any test method.
 */
abstract class BaseDeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String PROPERTY_STOP_BG_USERS_ON_SWITCH = "fw.stop_bg_users_on_switch";

    private boolean mDeviceOwnerSet;
    private @Nullable String mDeviceOwnerComponent;
    private @Nullable String mDeviceOwnerPkg;

    protected final void installDeviceOwnerApp(String apk) throws Exception {
        installAppAsUser(apk, mDeviceOwnerUserId);
    }

    protected final boolean setDeviceOwner(String deviceOwnerPkg, String adminReceiverClass)
            throws DeviceNotAvailableException {
        mDeviceOwnerPkg = Objects.requireNonNull(deviceOwnerPkg, "deviceOwnerPkg cannot be null");
        Objects.requireNonNull(adminReceiverClass, "adminReceiverClass cannot be null");
        mDeviceOwnerComponent = deviceOwnerPkg + "/" + adminReceiverClass;
        mDeviceOwnerSet = setDeviceOwner(mDeviceOwnerComponent, mDeviceOwnerUserId,
                /*expectFailure= */ false);
        return mDeviceOwnerSet;
    }

    private String getDeviceOwnerPkg() {
        Preconditions.checkState(mDeviceOwnerPkg != null,
                "test didn't call setDeviceOwner(String, String)");
        return mDeviceOwnerPkg;
    }

    protected final void removeDeviceOwnerIfSet() throws DeviceNotAvailableException {
        removeDeviceOwnerIfSet(/* optionallySet= */ true);
    }

    protected final void removeDeviceOwner() throws DeviceNotAvailableException {
        removeDeviceOwnerIfSet(/* optionallySet= */ false);
    }

    protected final void removeDeviceOwnerIfSet(boolean optionallySet)
            throws DeviceNotAvailableException {
        if (optionallySet) {
            if (!mDeviceOwnerSet) {
                CLog.d("removeDeviceOwnerIfSet(%s): ignoring as DO was not set)",
                        mDeviceOwnerComponent);
                return;
            }
        } else {
            assertWithMessage("device owner not set").that(mDeviceOwnerSet).isTrue();
        }
        assertWithMessage("Removed device owner %s on user %s", mDeviceOwnerComponent,
                mDeviceOwnerUserId)
                        .that(removeAdmin(mDeviceOwnerComponent, mDeviceOwnerUserId)).isTrue();
        mDeviceOwnerSet = false;
    }

    protected final void executeDeviceOwnerTest(String testClassName) throws Exception {
        executeDeviceOwnerTestOnSpecificUser(testClassName, mDeviceOwnerUserId);
    }

    protected final void executeDeviceOwnerTestOnDeviceOwnerUser(String testClassName)
            throws Exception {
        executeDeviceOwnerTestOnSpecificUser(testClassName, mDeviceOwnerUserId);
    }

    private void executeDeviceOwnerTestOnSpecificUser(String testClassName, int userId)
            throws Exception {
        String pkg = getDeviceOwnerPkg();
        String testClass = pkg + "." + testClassName;
        runDeviceTestsAsUser(pkg, testClass, userId);
    }

    protected final void executeDeviceOwnerTestMethod(String className, String testName)
            throws Exception {
        executeDeviceOwnerPackageTestMethod(className, testName, mDeviceOwnerUserId);
    }

    protected final String getStopBgUsersOnSwitchProperty() throws Exception {
        return executeShellCommand("getprop %s", PROPERTY_STOP_BG_USERS_ON_SWITCH).trim();
    }

    protected final void setStopBgUsersOnSwitchProperty(String value) throws Exception  {
        CLog.d("Value of %s before: %s", PROPERTY_STOP_BG_USERS_ON_SWITCH,
                getStopBgUsersOnSwitchProperty());
        executeShellCommand("setprop %s '%s'", PROPERTY_STOP_BG_USERS_ON_SWITCH, value);
    }

    private void executeDeviceOwnerPackageTestMethod(String className, String testName,
            int userId) throws Exception {
        runDeviceTestsAsUser(getDeviceOwnerPkg(), className, testName, userId);
    }
}
