/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.host.multiuser;

import static com.android.tradefed.device.UserInfo.USER_SYSTEM;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.RunUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(DeviceJUnit4ClassRunner.class)
public final class UserManagerHostTest extends BaseMultiUserTest {

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    public void getPreviousForegroundUser_correctAfterReboot() throws Exception {
        assumeNotInteractiveHsum();
        assumeNewUsersCanBeAdded(2);

        final int userId1 = getDevice().createUser("test_user_1");
        assertSwitchToUser(userId1);

        final int userId2 = getDevice().createUser("test_user_2");
        assertSwitchToUser(userId2);
        assertPreviousUserIs(userId1);

        // Wait to allow user xml to be written.
        RunUtil.getDefault().sleep(5000);

        getDevice().reboot();
        if (getDevice().getCurrentUser() == userId2) {
            assertPreviousUserIs(userId1);
        } else {
            assertPreviousUserIs(userId2);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    public void getPreviousForegroundUser_interactiveHsum_correctAfterReboot() throws Exception {
        assumeInteractiveHsum();
        assumeNewUsersCanBeAdded(2);
        assertSwitchToUser(USER_SYSTEM);

        int userId1 = getDevice().createUser("test_user_1");
        assertSwitchToUser(userId1);
        assertPreviousUserIs(USER_SYSTEM);

        int userId2 = getDevice().createUser("test_user_2");
        assertSwitchToUser(userId2);
        assertPreviousUserIs(userId1);

        // Wait to allow user xml to be written.
        RunUtil.getDefault().sleep(5000);

        getDevice().reboot();

        // The foreground user right after reboot differs depending on HSUM boot strategy.
        // TODO(b/411696141): Directly check HSUM boot strategy instead of the current hard-coded
        // check on automotive.
        if (isAutomotive()) {
            assertCurrentUser("after reboot", userId2);
            assertPreviousUserIs(userId1);
        } else {
            assertCurrentUser("after reboot", USER_SYSTEM);
            assertPreviousUserIs(userId2);

            // Although previous user is 2, current user is system, so let's explicitly switch to 2
            // first.
            assertSwitchToUser(userId2);
            assertPreviousUserIs(USER_SYSTEM);
        }

        assertSwitchToUser(userId1);
        assertPreviousUserIs(userId2);

        assertSwitchToUser(userId2);
        assertPreviousUserIs(userId1);
    }

    private boolean isAutomotive() throws DeviceNotAvailableException {
        return getDevice().hasFeature("android.hardware.type.automotive");
    }

    private boolean isInteractiveHsum() throws DeviceNotAvailableException {
        return getDevice().isHeadlessSystemUserMode()
                && getDevice().canSwitchToHeadlessSystemUser();
    }

    private void assumeInteractiveHsum() throws DeviceNotAvailableException {
        assumeTrue("device is not interactive HSUM", isInteractiveHsum());
    }

    private void assumeNotInteractiveHsum() throws DeviceNotAvailableException {
        assumeFalse("device is interactive HSUM", isInteractiveHsum());
    }

    private void assertPreviousUserIs(int expected) throws Exception {
        final DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PKG_NAME)
                .setDevice(getDevice())
                .setApkFileName(TEST_APP_PKG_APK)
                .setTestClassName(TEST_APP_PKG_NAME + ".UserManagerAppTest")
                .setTestMethodName("getPreviousForegroundUserReturnsExpected")
                .addInstrumentationArg("expectedResult", String.valueOf(expected));
        installPackage(options);
        final boolean testPassed = runDeviceTests(options);
        if (!testPassed) {
            // should never happen as runDeviceTests() itself would throw...
            throw new IllegalStateException("Device-side test failed but didn't throw!");
        }
    }

    private void assumeNewUsersCanBeAdded(int noOfUsers) throws DeviceNotAvailableException {
        assumeTrue("Cannot allow adding " + noOfUsers + " new users.",
                noOfUsers <= remainingUsersAllowedToBeCreated());
    }

    private int remainingUsersAllowedToBeCreated() throws DeviceNotAvailableException {
        int nonGuestUsersCount =  (int) getDevice().getUserInfos().values().stream()
                .filter(userInfo -> !userInfo.isGuest())
                .count();
        return getDevice().getMaxNumberOfUsersSupported() - nonGuestUsersCount;
    }
}
