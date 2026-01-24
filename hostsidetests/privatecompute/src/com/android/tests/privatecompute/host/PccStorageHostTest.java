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

package com.android.tests.privatecompute.host;

import static com.google.common.truth.Truth.assertThat;

import android.app.privatecompute.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Host side storage tests for pcc processes. */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class PccStorageHostTest extends BaseHostJUnit4Test {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private static final String PCC_TEST_APK = "PccHostTestApp.apk";
    private static final String NON_PCC_TEST_APK = "NonPccHostTestApp.apk";
    private static final String APP_PACKAGE_NAME = "com.example.pcc.host.test";

    private int mInitialUserId = -1;
    private int mUserId = -1;

    @Before
    public void setUp() throws Exception {
        getDevice().enableAdbRoot();
        mInitialUserId = getDevice().getCurrentUser();
        mUserId = getDevice().createUser("test_user");
        getDevice().switchUser(mUserId);
    }

    @After
    public void tearDown() throws Exception {
        if (mUserId != -1) {
            if (isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)) {
                uninstallPackageForUser(APP_PACKAGE_NAME, mUserId);
            }
            if (getDevice().getCurrentUser() != mInitialUserId) {
                getDevice().switchUser(mInitialUserId);
            }
            getDevice().removeUser(mUserId);
        }
        if (isPackageInstalledForUser(APP_PACKAGE_NAME, mInitialUserId)) {
            uninstallPackageForUser(APP_PACKAGE_NAME, mInitialUserId);
        }
        getDevice().disableAdbRoot();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccAppLifecycle() throws Exception {

        // User creation, app install and uninstall are costly operations in terms of time and
        // resources. So, we bundle all the lifecycle tests into a single test.

        // --- Install and Verify ---
        installPackageAsUser(PCC_TEST_APK, true, mUserId);
        assertThat(isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)).isTrue();

        // Verify standard directories exist
        CommandResult ceResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, true));
        CommandResult deResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, false));
        assertThat(ceResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(deResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        assertSeLinuxLabel(mUserId, APP_PACKAGE_NAME);
        assertPccDirectoriesExist(mUserId, APP_PACKAGE_NAME);

        // --- Clear App Data and Verify ---
        getDevice()
                .executeShellV2Command(
                        "touch "
                                + getPccCachePath(mUserId, APP_PACKAGE_NAME, true)
                                + "/dummy_file.txt");
        getDevice()
                .executeShellV2Command(
                        "touch "
                                + getPccCachePath(mUserId, APP_PACKAGE_NAME, false)
                                + "/dummy_file.txt");

        getDevice().executeShellV2Command("pm clear --user " + mUserId + " " + APP_PACKAGE_NAME);

        CommandResult ceCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getPccCachePath(mUserId, APP_PACKAGE_NAME, true));
        CommandResult deCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getPccCachePath(mUserId, APP_PACKAGE_NAME, false));
        assertThat(ceCacheResult.getStdout().trim()).isEmpty();
        assertThat(deCacheResult.getStdout().trim()).isEmpty();

        // --- Uninstall and Verify ---
        uninstallPackageForUser(APP_PACKAGE_NAME, mUserId);
        assertThat(isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)).isFalse();

        // Verify standard directories are deleted
        ceResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, true));
        deResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, false));
        assertThat(ceResult.getStatus()).isNotEqualTo(CommandStatus.SUCCESS);
        assertThat(deResult.getStatus()).isNotEqualTo(CommandStatus.SUCCESS);

        // Verify PCC directories are deleted
        assertPccDirectoriesDontExist(mUserId, APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testInstallNonPccApp_onlyStandardDirectoriesExist() throws Exception {
        installPackageAsUser(NON_PCC_TEST_APK, true, mUserId);
        assertThat(isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)).isTrue();

        // Verify standard directories exist
        CommandResult ceResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, true));
        CommandResult deResult =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getAppDataPath(mUserId, APP_PACKAGE_NAME, false));
        assertThat(ceResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(deResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        // Verify PCC directories don't exist
        assertPccDirectoriesDontExist(mUserId, APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMultiUserIsolation() throws Exception {
        // Install for both users
        installPackageAsUser(PCC_TEST_APK, true, mInitialUserId);
        installPackageAsUser(PCC_TEST_APK, true, mUserId);

        // Create distinct files in each user's CE cache directory
        String initialUserFile = "initial_user_file.txt";
        String testUserFile = "test_user_file.txt";
        getDevice()
                .executeShellV2Command(
                        "touch "
                                + getPccCachePath(mInitialUserId, APP_PACKAGE_NAME, true)
                                + "/"
                                + initialUserFile);
        getDevice()
                .executeShellV2Command(
                        "touch "
                                + getPccCachePath(mUserId, APP_PACKAGE_NAME, true)
                                + "/"
                                + testUserFile);

        // Verify initial user has its file and not the other
        CommandResult initialUserCache =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getPccCachePath(mInitialUserId, APP_PACKAGE_NAME, true));
        assertThat(initialUserCache.getStdout()).contains(initialUserFile);
        assertThat(initialUserCache.getStdout()).doesNotContain(testUserFile);

        // Verify test user has its file and not the other
        CommandResult testUserCache =
                getDevice()
                        .executeShellV2Command(
                                "ls " + getPccCachePath(mUserId, APP_PACKAGE_NAME, true));
        assertThat(testUserCache.getStdout()).contains(testUserFile);
        assertThat(testUserCache.getStdout()).doesNotContain(initialUserFile);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testNonPccToPccUpgrade_pccDirectoryAdded() throws Exception {
        installPackageAsUser(NON_PCC_TEST_APK, true, mUserId);

        assertThat(isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)).isTrue();
        assertPccDirectoriesDontExist(mUserId, APP_PACKAGE_NAME);

        installPackageAsUser(PCC_TEST_APK, true, mUserId, "-r", "-d");
        assertPccDirectoriesExist(mUserId, APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccToNonPccUpgrade_pccDirectoryRemoved() throws Exception {
        installPackageAsUser(PCC_TEST_APK, true, mUserId);

        assertThat(isPackageInstalledForUser(APP_PACKAGE_NAME, mUserId)).isTrue();
        assertPccDirectoriesExist(mUserId, APP_PACKAGE_NAME);

        installPackageAsUser(NON_PCC_TEST_APK, true, mUserId, "-r", "-d");
        assertPccDirectoriesDontExist(mUserId, APP_PACKAGE_NAME);
    }

    private String getAppDataPath(int userId, String packageName, boolean isCe) {
        return (isCe ? "/data/user/" : "/data/user_de/") + userId + "/" + packageName;
    }

    private String getPccDataPath(int userId, String packageName, boolean isCe) {
        return getAppDataPath(userId, packageName, isCe) + "-pcc";
    }

    private String getPccCachePath(int userId, String packageName, boolean isCe) {
        return getPccDataPath(userId, packageName, isCe) + "/cache";
    }

    private String getPccCodeCachePath(int userId, String packageName, boolean isCe) {
        return getPccDataPath(userId, packageName, isCe) + "/code_cache";
    }

    private void assertSeLinuxLabel(int userId, String packageName) throws Exception {
        CommandResult ceResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccDataPath(userId, packageName, true));
        CommandResult deResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccDataPath(userId, packageName, false));
        assertThat(ceResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(ceResult.getStdout()).contains(":pcc_data_file:");
        assertThat(deResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(deResult.getStdout()).contains(":pcc_data_file:");
    }

    private void assertPccDirectoriesExist(int userId, String packageName) throws Exception {
        CommandResult ceCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccCachePath(userId, packageName, true));
        assertThat(ceCacheResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(ceCacheResult.getStdout()).contains(":pcc_data_file:");
        CommandResult ceCodeCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccCodeCachePath(userId, packageName, true));
        assertThat(ceCodeCacheResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(ceCodeCacheResult.getStdout()).contains(":pcc_data_file:");
        CommandResult deCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccCachePath(userId, packageName, false));
        assertThat(deCacheResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(deCacheResult.getStdout()).contains(":pcc_data_file:");
        CommandResult deCodeCacheResult =
                getDevice()
                        .executeShellV2Command(
                                "ls -Zd " + getPccCodeCachePath(userId, packageName, false));
        assertThat(deCodeCacheResult.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(deCodeCacheResult.getStdout()).contains(":pcc_data_file:");
    }

    private void assertPccDirectoriesDontExist(int userId, String packageName) throws Exception {
        CommandResult ceResult =
                getDevice()
                        .executeShellV2Command("ls " + getPccDataPath(userId, packageName, true));
        CommandResult deResult =
                getDevice()
                        .executeShellV2Command("ls " + getPccDataPath(userId, packageName, false));

        assertThat(ceResult.getStatus()).isNotEqualTo(CommandStatus.SUCCESS);
        assertThat(deResult.getStatus()).isNotEqualTo(CommandStatus.SUCCESS);
    }

    private boolean isPackageInstalledForUser(String packageName, int userId) throws Exception {
        CommandResult result =
                getDevice()
                        .executeShellV2Command(
                                "pm list packages --user " + userId + " " + packageName);
        return result.getStdout().contains("package:" + packageName);
    }

    private void uninstallPackageForUser(String packageName, int userId) throws Exception {
        getDevice().executeShellV2Command("pm uninstall --user " + userId + " " + packageName);
    }
}
