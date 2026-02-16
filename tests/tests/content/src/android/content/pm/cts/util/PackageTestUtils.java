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
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.TimeUnit;

public final class PackageTestUtils {

    private static final String TAG = "PackageTestUtils";
    private static final int ROLE_CHANGE_TIMEOUT_SECONDS = 5;
    private static final int UI_TIMEOUT_MS = 5000;
    private static final String LOCK_SCREEN_PIN = "1234";

    private PackageTestUtils() {}

    public static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    public static final String APP_LOCK_SUPPORTED_APK =
            SAMPLE_APK_BASE + "CtsAppLockSupportedTestApp.apk";
    public static final String APP_LOCK_SUPPORTED_APP_LABEL = "CtsAppLockSupportedTestApp";
    public static final String APP_LOCK_SUPPORTED_PACKAGE_NAME =
            "android.content.cts.applocksupportedtestapp";
    public static final String EMPTY_TEST_APP_APK = SAMPLE_APK_BASE + "CtsEmptyTestApp.apk";
    public static final String EMPTY_TEST_APP_PACKAGE_NAME =
            "android.packageinstaller.emptytestapp.cts";

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
     * Removes the provided {@link Context}'s app the {@link RoleManager#ROLE_HOME} for the duration
     * of the {@link AutoCloseable}, and grants it afterward.
     *
     * <p>This is used to remove the {@code LOCK_APPS} permission from the test app.
     *
     * @param context the {@link Context} of the test.
     * @return an {@link AutoCloseable} that will grant the role.
     */
    public static AutoCloseable clearHomeRoleHolderScoped(Context context) throws Exception {
        final RoleManager roleManager = context.getSystemService(RoleManager.class);
        assertThat(roleManager.isRoleHeld(RoleManager.ROLE_HOME)).isTrue();

        // Remove role ROLE_HOME from the test app.
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
        assertThat(roleRemovedFuture.get(ROLE_CHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Grant role ROLE_HOME to the test app.
        return () -> {
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

    /**
     * Sets a PIN lock for the device for the duration of the {@link AutoCloseable}, and clears it
     * afterward.
     *
     * <p>This is used for tests that require a secure lock screen (LSKF).
     *
     * @return an {@link AutoCloseable} that will clear the PIN.
     */
    public static AutoCloseable setLskfScoped() {
        try {
            SystemUtil.runShellCommand("locksettings set-pin " + LOCK_SCREEN_PIN);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set LSKF.", e);
        }
        return () -> {
            try {
                SystemUtil.runShellCommand("locksettings clear --old " + LOCK_SCREEN_PIN);
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear LSKF", e);
            }
        };
    }

    /**
     * Clears any lock from the device for the duration of the {@link AutoCloseable}.
     *
     * <p>This ensures the device is in an insecure state for the duration of the scope.
     *
     * @return an {@link AutoCloseable}.
     */
    public static AutoCloseable clearLskfScoped() {
        try {
            SystemUtil.runShellCommand("locksettings clear --old " + LOCK_SCREEN_PIN);
            SystemUtil.runShellCommand("locksettings clear");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear LSKF", e);
        }
        return () -> {};
    }

    /**
     * Waits for a UI object to appear and returns whether it was found within the timeout.
     *
     * @param uiDevice the {@link UiDevice} instance to use for waiting.
     * @param selector the {@link BySelector} used to find the object.
     * @return {@code true} if the object was found within {@link #UI_TIMEOUT_MS}, {@code false}
     * otherwise.
     */
    public static boolean waitForUiObject(UiDevice uiDevice, BySelector selector) {
        return uiDevice.wait(Until.hasObject(selector), UI_TIMEOUT_MS);
    }

    /**
     * Returns retrieved resource name for a framework ID.
     *
     * @param idName the name of the ID.
     * @return the fully qualified resource name.
     */
    public static String getSystemResourceName(String idName) {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final int resId = context.getResources().getIdentifier(idName, "id", "android");
        if (resId == 0) {
            throw new RuntimeException("Could not find system resource: " + idName);
        }
        return context.getResources().getResourceName(resId);
    }

   /**
     * Launches the given {@link PendingIntent} with background activity start allowed..
     *
     * @param pendingIntent the {@link PendingIntent} to launch.
     * @throws PendingIntent.CanceledException if the PendingIntent is no longer valid.
     */
    public static AutoCloseable launchPendingIntentWithBgStart(UiDevice uiDevice,
            PendingIntent pendingIntent) throws PendingIntent.CanceledException {
        uiDevice.pressHome();
        uiDevice.waitForIdle();

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        pendingIntent.send(/* context= */ null, /* code= */ 0, /* intent= */ null,
                /* onFinished= */ null, /* handler= */ null, /* requiredPermission= */ null,
                /* options= */ options.toBundle());

        return () -> {
            uiDevice.pressHome();
            uiDevice.waitForIdle();
        };
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
}
