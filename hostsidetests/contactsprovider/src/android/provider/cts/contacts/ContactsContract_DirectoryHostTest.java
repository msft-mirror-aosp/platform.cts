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

package android.provider.cts.contacts;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.util.CommandResult;

import com.google.common.collect.ImmutableSet;

public class ContactsContract_DirectoryHostTest extends CompatChangeGatingTestCase {

    private static final String TEST_APP_PKG = "android.provider.cts.contacts.app";
    private static final String TEST_CLASS =
            "android.provider.cts.contacts.ContactsContract_DirectoryDeviceTest";

    private static final long REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES = 157720069L;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Make sure the directory is recognized by the provider before running tests.
        grantPermission(TEST_APP_PKG, "android.permission.WRITE_CONTACTS");
        grantPermission(TEST_APP_PKG, "android.permission.READ_CALL_LOG");
    }

    public void testRequirePermissionsForDirectoryQueriesChangeEnabled() throws Exception {
        if (!checkFlagIsEnabled()) {
            LogUtil.CLog.i(
                    "Skipping testRequirePermissionsForDirectoryQueriesChangeEnabled. "
                            + " Required flag is not enabled.");
            // pass since the test requires the flag to be enabled.
            return;
        }
        revokePermission(TEST_APP_PKG, "android.permission.WRITE_CONTACTS");
        revokePermission(TEST_APP_PKG, "android.permission.READ_CALL_LOG");

        runDeviceCompatTest(
                TEST_APP_PKG,
                TEST_CLASS,
                "testQueryDirectoryWithoutPermissions",
                /*enabledChanges*/ ImmutableSet.of(REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES),
                /*disabledChanges*/ ImmutableSet.of());

        grantPermission(TEST_APP_PKG, "android.permission.WRITE_CONTACTS");
        runDeviceCompatTest(
                TEST_APP_PKG,
                TEST_CLASS,
                "testQueryDirectoryWithWriteContactsPermission",
                /*enabledChanges*/ ImmutableSet.of(REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES),
                /*disabledChanges*/ ImmutableSet.of());

        grantPermission(TEST_APP_PKG, "android.permission.READ_CALL_LOG");
        runDeviceCompatTest(
                TEST_APP_PKG,
                TEST_CLASS,
                "testQueryDirectoryWithWriteContactsAndReadCallLogPermissions",
                /*enabledChanges*/ ImmutableSet.of(REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES),
                /*disabledChanges*/ ImmutableSet.of());

        revokePermission(TEST_APP_PKG, "android.permission.WRITE_CONTACTS");
        runDeviceCompatTest(
                TEST_APP_PKG,
                TEST_CLASS,
                "testQueryDirectoryWithReadCallLogPermissions",
                /*enabledChanges*/ ImmutableSet.of(REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testRequirePermissionsForDirectoryQueriesChangeDisabled() throws Exception {
        if (!checkFlagIsEnabled()) {
            LogUtil.CLog.i(
                    "Skipping testRequirePermissionsForDirectoryQueriesChangeDisabled. "
                            + " Required flag is not enabled.");
            // pass since the test requires the flag to be enabled.
            return;
        }
        revokePermission(TEST_APP_PKG, "android.permission.WRITE_CONTACTS");
        revokePermission(TEST_APP_PKG, "android.permission.READ_CALL_LOG");

        runDeviceCompatTest(
                TEST_APP_PKG,
                TEST_CLASS,
                "testQueryDirectoryWithoutPermissions_"
                        + "requirePermissionsForDirectoryQueriesDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES));
    }

    private void grantPermission(String pkg, String permission) throws Exception {
        getDevice().executeShellCommand("pm grant " + pkg + " " + permission);
    }

    private void revokePermission(String pkg, String permission) throws Exception {
        getDevice().executeShellCommand("pm revoke " + pkg + " " + permission);
    }

    // Workaround copied from b/324474892#comment2. The RequiresFlagEnabled annotation needs
    // CheckFlagRule but that doesn't work in a
    // CompatChangeGatingTestCase because it is Junit3 and rules are a JUnit4 thing.
    private boolean checkFlagIsEnabled() throws DeviceNotAvailableException {
        CommandResult commandResult =
                getDevice()
                        .executeShellV2Command(
                                "aflags list | grep com.android.providers.contacts.flags"
                                        + ".directory_provider_query_permission_check");
        return commandResult.getStdout().contains("enabled");
    }
}
