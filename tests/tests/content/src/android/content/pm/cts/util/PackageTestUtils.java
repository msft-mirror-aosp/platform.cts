/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.content.pm.cts.util;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.SettableFuture;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public final class PackageTestUtils {

    private static final int ROLE_CHANGE_TIMEOUT_SECONDS = 5;

    private PackageTestUtils() {}

    public static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    public static final String APP_LOCK_SUPPORTED_APK =
            SAMPLE_APK_BASE + "CtsAppLockSupportedTestApp.apk";
    public static final String APP_LOCK_SUPPORTED_PACKAGE_NAME =
            "android.content.cts.applocksupportedtestapp";
    public static final String HEADLESS_APK = SAMPLE_APK_BASE + "CtsHeadlessApp.apk";
    public static final String HEADLESS_APP_PACKAGE_NAME = "com.android.cts.headlessapp";

    /**
     * Installs a package for the duration of the {@link AutoCloseable}, and uninstalls it afterward
     *
     * @param apkPath the path to the APK file to install.
     * @param packageName the package name to uninstall when the scope is closed.
     * @return an {@link AutoCloseable} that will uninstall the package.
     */
    public static AutoCloseable installPackageScoped(String apkPath, String packageName) {
        assertThat(SystemUtil.runShellCommand("pm install -t " + apkPath)).isEqualTo("Success\n");
        return () -> SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    /**
     * Installs a package for the given user for the duration of the {@link AutoCloseable}, and
     * uninstalls it afterward.
     *
     * @param apkPath the path to the APK file to install.
     * @param packageName the package name to uninstall when the scope is closed.
     * @param user the user to install the package for.
     * @return an {@link AutoCloseable} that will uninstall the package for the user.
     */
    public static AutoCloseable installPackageScopedForUser(String apkPath, String packageName,
            UserHandle user) {
        assertThat(SystemUtil.runShellCommand("pm install -t " + apkPath)).isEqualTo("Success\n");
        assertThat(SystemUtil.runShellCommand("pm install-existing --user " + user.getIdentifier()
                + " " + packageName)).contains("installed for user");
        return () -> SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    /**
     * Grants the provided {@link Context}'s app the {@link RoleManager#ROLE_HOME} for the duration
     * of the {@link AutoCloseable}, and revokes it afterward.
     *
     * <p>This is used to grant the test app the {@code LOCK_APPS} permission.
     *
     * @param context the {@link Context} of the test.
     * @return an {@link AutoCloseable} that will revoke the role.
     */
    public static AutoCloseable setHomeRoleHolderScoped(Context context) throws Exception {
        final RoleManager roleManager = context.getSystemService(RoleManager.class);

        // Grant role ROLE_HOME to the test app.
        SettableFuture<Boolean> roleAddedFuture = SettableFuture.create();
        runWithShellPermissionIdentity(
                () -> {
                    roleManager.addRoleHolderAsUser(
                            RoleManager.ROLE_HOME,
                            context.getPackageName(),
                            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                            Process.myUserHandle(),
                            context.getMainExecutor(),
                            (successful) -> roleAddedFuture.set(successful));
                },
                Manifest.permission.MANAGE_ROLE_HOLDERS);
        assertThat(roleAddedFuture.get(ROLE_CHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Remove role ROLE_HOME from the test app.
        return () -> {
            SettableFuture<Boolean> roleRemovedFuture = SettableFuture.create();
            runWithShellPermissionIdentity(
                    () -> {
                        roleManager.removeRoleHolderAsUser(
                                RoleManager.ROLE_HOME,
                                context.getPackageName(),
                                RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                                Process.myUserHandle(),
                                context.getMainExecutor(),
                                (successful) -> roleRemovedFuture.set(successful));
                    },
                    Manifest.permission.MANAGE_ROLE_HOLDERS);
            assertThat(roleRemovedFuture.get(ROLE_CHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        };
    }

    /**
     * Creates a user of type {@link UserManager#USER_TYPE_PROFILE_SUPERVISING} for the duration of
     * the {@link AutoCloseable}, and removes it afterward.
     *
     * @param context the {@link Context} of the test.
     * @return a {@link ScopedSupervisedUser} that allows retrieving the user and closing it.
     */
    public static ScopedSupervisedUser createSupervisedUserScoped(Context context) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        final UserHandle user = SystemUtil.runWithShellPermissionIdentity(() -> {
            final UserInfo userInfo = userManager.createUser("Supervised",
                    UserManager.USER_TYPE_PROFILE_SUPERVISING, 0);
            return userInfo != null ? userInfo.getUserHandle() : null;
        });

        if (user == null) {
            throw new IllegalStateException("Failed to create supervised user");
        }

        SystemUtil.runShellCommand("am start-user -w " + user.getIdentifier());

        return new ScopedSupervisedUser(user);
    }

    /**
     * Creates a managed profile of type {@link UserManager#USER_TYPE_PROFILE_MANAGED} for the
     * duration of the {@link AutoCloseable}, and removes it afterward.
     *
     * @param context the {@link Context} of the test.
     * @return a {@link ScopedManagedProfile} that allows retrieving the user and closing it.
     */
    public static ScopedManagedProfile createManagedProfileScoped(Context context) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        final UserHandle user = SystemUtil.runWithShellPermissionIdentity(() -> {
            return userManager.createProfile("Managed", UserManager.USER_TYPE_PROFILE_MANAGED,
                    new HashSet<>());
        });

        if (user == null) {
            throw new IllegalStateException("Failed to create managed profile");
        }

        SystemUtil.runShellCommand("am start-user -w " + user.getIdentifier());

        return new ScopedManagedProfile(user);
    }

    /**
     * Returns {@code true} if the app context has been granted the {@link
     * android.Manifest.permission#LOCK_APPS} permission.
     */
    public static boolean hasLockAppsPermission(Context context) {
        return context.checkPermission(
                        Manifest.permission.LOCK_APPS, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns {@code true} if the device supports App Lock, {@code false} otherwise.
     *
     * <p>App Lock is not supported on Watch, Auto, and TV.
     */
    public static boolean shouldTestAppLock(Context context) {
        PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false;
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return false;
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false;
        }
        return true;
    }

    public static class ScopedSupervisedUser implements AutoCloseable {
        private final UserHandle mUser;

        ScopedSupervisedUser(UserHandle user) {
            mUser = user;

            SystemUtil.runShellCommand("cmd supervision enable 0");
        }

        public UserHandle getUser() {
            return mUser;
        }

        @Override
        public void close() {
            SystemUtil.runShellCommand("cmd supervision disable 0");
            SystemUtil.runShellCommand("pm remove-user " + mUser.getIdentifier());
        }
    }

    public static class ScopedManagedProfile implements AutoCloseable {
        private final UserHandle mUser;

        ScopedManagedProfile(UserHandle user) {
            mUser = user;
        }

        public UserHandle getUser() {
            return mUser;
        }

        @Override
        public void close() {
            SystemUtil.runShellCommand("pm remove-user " + mUser.getIdentifier());
        }
    }
}
