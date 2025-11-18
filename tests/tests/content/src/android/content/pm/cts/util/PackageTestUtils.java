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
import android.os.Process;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.TimeUnit;

public final class PackageTestUtils {

    private static final int ROLE_CHANGE_TIMEOUT_SECONDS = 5;

    private PackageTestUtils() {}

    public static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    public static final String APP_LOCK_SUPPORTED_APK =
            SAMPLE_APK_BASE + "CtsAppLockSupportedTestApp.apk";
    public static final String APP_LOCK_SUPPORTED_PACKAGE_NAME =
            "android.content.cts.applocksupportedtestapp";

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
}
