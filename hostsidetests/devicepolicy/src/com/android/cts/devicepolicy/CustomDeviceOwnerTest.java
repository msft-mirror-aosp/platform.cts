/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.cts.devicepolicy.BaseDevicePolicyTest.Settings;
import com.android.cts.migration.MigrationHelper;

import java.io.File;
import java.lang.Exception;

/**
 * This class is used for tests that need to do something special before setting the device
 * owner, so they cannot use the regular DeviceOwnerTest class
 */
public class CustomDeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_ADMIN
            = DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";
    private static final String DEVICE_OWNER_ADMIN_COMPONENT
            = DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN;

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String TEST_APP_APK = "CtsSimpleApp.apk";
    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String TEST_APP_LOCATION = "/data/local/tmp/";

    private static final String PACKAGE_INSTALLER_PKG = "com.android.cts.packageinstaller";
    private static final String PACKAGE_INSTALLER_APK = "CtsPackageInstallerApp.apk";
    private static final String PACKAGE_INSTALLER_ADMIN_COMPONENT =
            PACKAGE_INSTALLER_PKG + "/" + ".ClearDeviceOwnerTest$BasicAdminReceiver";

    private static final String ACCOUNT_MANAGEMENT_PKG
            = "com.android.cts.devicepolicy.accountmanagement";
    protected static final String ACCOUNT_MANAGEMENT_APK
            = "CtsAccountManagementDevicePolicyApp.apk";

    @Override
    public void tearDown() throws Exception {
        if (mHasFeature) {
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            getDevice().uninstallPackage(ACCOUNT_MANAGEMENT_PKG);
        }

        super.tearDown();
    }

    public void testOwnerChangedBroadcast() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            installAppAsUser(INTENT_RECEIVER_APK, mPrimaryUserId);

            String testClass = INTENT_RECEIVER_PKG + ".OwnerChangedBroadcastTest";

            // Running this test also gets the intent receiver app out of the stopped state, so it
            // can receive broadcast intents.
            assertTrue(runDeviceTestsAsUser(INTENT_RECEIVER_PKG, testClass,
                    "testOwnerChangedBroadcastNotReceived", mPrimaryUserId));

            // Setting the device owner should send the owner changed broadcast.
            assertTrue(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ false));

            assertTrue(runDeviceTestsAsUser(INTENT_RECEIVER_PKG, testClass,
                    "testOwnerChangedBroadcastReceived", mPrimaryUserId));
        } finally {
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
            assertTrue("Failed to remove device owner.",
                    removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId));
        }
    }

    public void testCannotSetDeviceOwnerWhenSecondaryUserPresent() throws Exception {
        if (!mHasFeature || getMaxNumberOfUsersSupported() < 2) {
            return;
        }
        int userId = -1;
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            userId = createUser();
            assertFalse(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            removeUser(userId);
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId);
        }
    }

    public void testCannotSetDeviceOwnerWhenAccountPresent() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mPrimaryUserId);
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            assertTrue(runDeviceTestsAsUser(ACCOUNT_MANAGEMENT_PKG, ".AccountUtilsTest",
                    "testAddAccountExplicitly", mPrimaryUserId));
            assertFalse(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId);
            assertTrue(runDeviceTestsAsUser(ACCOUNT_MANAGEMENT_PKG, ".AccountUtilsTest",
                    "testRemoveAccountExplicitly", mPrimaryUserId));
        }
    }

    public void testSilentPackageInstall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        final File apk = MigrationHelper.getTestFile(mCtsBuild, TEST_APP_APK);
        try {
            // Install the test and prepare the test apk.
            installAppAsUser(PACKAGE_INSTALLER_APK, mPrimaryUserId);
            assertTrue(setDeviceOwner(PACKAGE_INSTALLER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ false));

            getDevice().uninstallPackage(TEST_APP_PKG);
            assertTrue(getDevice().pushFile(apk, TEST_APP_LOCATION + apk.getName()));
            assertTrue(runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG,
                    PACKAGE_INSTALLER_PKG + ".SilentPackageInstallTest", mPrimaryUserId));
        } finally {
            assertTrue("Failed to remove device owner.",
                    removeAdmin(PACKAGE_INSTALLER_ADMIN_COMPONENT, mPrimaryUserId));
            String command = "rm " + TEST_APP_LOCATION + apk.getName();
            String commandOutput = getDevice().executeShellCommand(command);
            getDevice().uninstallPackage(TEST_APP_PKG);
            getDevice().uninstallPackage(PACKAGE_INSTALLER_PKG);
        }
    }

    public void testIsProvisioningAllowed() throws Exception {
        // Must install the apk since the test runs in the DO apk.
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            // When CTS runs, setupwizard is complete. Expects it has to return false as DO can
            // only be provisioned before setupwizard is completed.

            assertTrue(runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PreDeviceOwnerTest",
                    "testIsProvisioningAllowedFalse", /* deviceOwnerUserId */ 0));
        } finally {
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        }
    }
}
