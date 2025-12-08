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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.cts.devicepolicy.user.DevicePolicyUsersPreparer;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.CommandResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * These tests cover:
 * 1) general enterprise access to AppSearch data through EnterpriseGlobalSearchSession, and
 * 2) enterprise fields restrictions applied to the Person schema
 *
 * <p>These tests do not cover:
 * 1) the enterprise transformation applied to Person documents, since that only applies to
 * AppSearch's actual contacts corpus, and these tests run using the local AppSearch database
 * 2) the managed profile device policy check for AppSearch's actual contacts corpus as we cannot
 * set the policy in CTS tests
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class EnterpriseContactsMultiUserTest extends AppSearchHostTestBase {

    public static final String FEATURE_MANAGED_USERS = "android.software.managed_users";

    private static int sParentUserId;
    private static int sSecondaryUserId;
    private static int sEnterpriseUserId;
    private static boolean sIsTemporaryEnterpriseUser;
    private static boolean sFeaturesSupported;
    private static ITestDevice sDevice;
    private static final List<SuiteApkInstaller> sInstallers = new ArrayList<>();

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        sDevice = testInfo.getDevice();
        assumeTrue("Multi-user is not supported on this device", sDevice.isMultiUserSupported());
        assumeTrue(
                "Device doesn't have feature " + FEATURE_MANAGED_USERS,
                sDevice.hasFeature(FEATURE_MANAGED_USERS));
        sFeaturesSupported = true;

        sParentUserId = DevicePolicyUsersPreparer.getProfileParentUserId();

        sSecondaryUserId = createSecondaryUser(sDevice);
        CLog.d(
                "setupClass(): sParentUserId=%d, sSecondaryUserId=%d",
                sParentUserId, sSecondaryUserId);
        setUpEnterpriseProfile(sDevice);
        installPackageAsUser(testInfo, sParentUserId);
        installPackageAsUser(testInfo, sSecondaryUserId);
        installPackageAsUser(testInfo, sEnterpriseUserId);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (!sFeaturesSupported) {
            CLog.d("tearDownClass(): skipping because sFeaturesSupported is false");
            return;
        }
        for (SuiteApkInstaller installer : sInstallers) {
            installer.tearDown(testInfo, null);
        }
        ITestDevice device = testInfo.getDevice();
        if (sSecondaryUserId > 0) {
            CLog.d("removing secondary user (%d)", sSecondaryUserId);
            device.removeUser(sSecondaryUserId);
        }
        if (sIsTemporaryEnterpriseUser) {
            CLog.d("removing temporary user profile(%d)", sEnterpriseUserId);
            device.removeUser(sEnterpriseUserId);
        }
    }

    /** Creates a test user and returns the user id. */
    private static int createSecondaryUser(ITestDevice device) throws DeviceNotAvailableException {
        int profileId = device.createUser("Test User");
        assertThat(device.startUser(profileId)).isTrue();
        return profileId;
    }

    /** Gets or creates an enterprise profile and sets the {@link #sEnterpriseUserId user id } */
    private static void setUpEnterpriseProfile(ITestDevice device)
            throws DeviceNotAvailableException {
        // Search for a managed profile
        for (UserInfo userInfo : device.getUserInfos().values()) {
            if (userInfo.isManagedProfile()) {
                sEnterpriseUserId = userInfo.userId();
                CLog.d("Set sEnterpriseUserId as existing user (%d)", sEnterpriseUserId);
                startEnterpriseProfile(device);
                return;
            }
        }
        // If no managed profile, set up a temporary one

        // Create a managed profile "work" under the main user
        String cmd = "pm create-user --profileOf " + sParentUserId + " --managed work";
        CommandResult result = device.executeShellV2Command(cmd);
        CLog.d("Result of command %s: %s", cmd, result);
        String output = result.getStdout();

        if (!output.startsWith("Success:")) {
            assertWithMessage("Command %s failed: %s", cmd, result).fail();
            return;
        }
        try {
            sEnterpriseUserId = Integer.parseInt(output.split(" id ")[1].trim());
            CLog.d("Set sEnterpriseUserId as new user (%d)", sEnterpriseUserId);
        } catch (Exception e) {
            assertWithMessage("Failed to parse output (%s) of command %s: %s", output, cmd, e)
                    .fail();
            return;
        }
        startEnterpriseProfile(device);
        sIsTemporaryEnterpriseUser = true;
    }

    private static void startEnterpriseProfile(ITestDevice device)
            throws DeviceNotAvailableException {
        assertWithMessage("Started user %s", sEnterpriseUserId)
                .that(device.startUser(sEnterpriseUserId, /* waitFlag= */ true))
                .isTrue();
    }

    private static void installPackageAsUser(TestInformation testInfo, int userId)
            throws Exception {
        SuiteApkInstaller installer = new SuiteApkInstaller();
        installer.addTestFileName(TARGET_APK_A);
        installer.setUserId(userId);
        installer.setShouldGrantPermission(true);
        installer.setUp(testInfo);
        sInstallers.add(installer);
    }

    /**
     * As setup, we need the enterprise user to first create some contacts locally. It's ok for this
     * method to run at the beginning of each test without a previous teardown, since it will just
     * overwrite the same contacts.
     */
    private void setUpEnterpriseContacts() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA("setUpEnterpriseContacts",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    private void setUpEnterpriseContactsWithoutEnterprisePermissions() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "setUpEnterpriseContactsWithoutEnterprisePermissions",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    private void setUpEnterpriseContactsWithManagedPermission() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA("setUpEnterpriseContactsWithManagedPermission",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_hasEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testHasEnterpriseAccess",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_hasEnterpriseAccess_withManagedPermission_onUAbove() throws Exception {
        assumeTrue(sDevice.getApiLevel() >= 34);
        // We only run this test if we know that we have managed profile contacts access; this will
        // be the case if we set up a temporary work profile. (It's not guaranteed in the case that
        // there was an existing work profile instead)
        assumeTrue(sIsTemporaryEnterpriseUser);
        setUpEnterpriseContactsWithManagedPermission();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testHasEnterpriseAccess",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccess_withManagedPermission_onTBelow()
            throws Exception {
        assumeTrue(sDevice.getApiLevel() <= 33);
        // We only run this test if we know that we have managed profile contacts access; this will
        // be the case if we set up a temporary work profile. (It's not guaranteed in the case that
        // there was an existing work profile instead)
        assumeTrue(sIsTemporaryEnterpriseUser);
        setUpEnterpriseContactsWithManagedPermission();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessIfEnterpriseProfileIsStopped()
            throws Exception {
        setUpEnterpriseContacts();
        try {
            assertThat(sDevice.stopUser(sEnterpriseUserId, /*waitFlag=*/ true, /*forceFlag=*/
                    true)).isTrue();
            runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                    sParentUserId,
                    Collections.emptyMap());
        } finally {
            sDevice.startUser(sEnterpriseUserId, /*waitFlag=*/ true);
        }
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessToNonEnterpriseSchema() throws Exception {
        setUpEnterpriseContactsWithoutEnterprisePermissions();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testEnterpriseUser_doesNotHaveEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sEnterpriseUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSecondaryUser_doesNotHaveEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sSecondaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact_withProjection",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withProjection",
                sParentUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withFilter() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withFilter",
                sParentUserId,
                Collections.emptyMap());
    }
}
