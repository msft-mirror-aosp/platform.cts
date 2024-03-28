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

package com.android.bedstead.permissions;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.appops.AppOpsMode;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.nene.utils.UndoableContext;
import com.android.bedstead.nene.utils.Versions;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Permission manager for tests. */
public final class Permissions {

    public static final AtomicBoolean sIgnorePermissions = new AtomicBoolean(false);
    private static final String LOG_TAG = "Permissions";
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final AppOpsManager sAppOpsManager =
            TestApis.context().instrumentedContext().getSystemService(AppOpsManager.class);
    private static final Package sInstrumentedPackage =
            TestApis.packages().instrumented();
    private static final UserReference sUser = TestApis.users().instrumented();
    private static final Package sShellPackage =
            TestApis.packages().find("com.android.shell");
    private static final Set<String> sCheckedGrantPermissions = new HashSet<>();
    private static final Set<String> sCheckedDenyPermissions = new HashSet<>();
    private static final boolean SUPPORTS_ADOPT_SHELL_PERMISSIONS =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    /**
     * Permissions which cannot be given to shell.
     *
     * <p>Each entry must include a comment with the reason it cannot be added.
     */
    private static final ImmutableSet EXEMPT_SHELL_PERMISSIONS = ImmutableSet.of(

    );

    public static final Permissions sInstance = new Permissions();

    private final List<PermissionContextImpl> mPermissionContexts =
            Collections.synchronizedList(new ArrayList<>());
    private final Set<String> mShellPermissions;
    private final Set<String> mInstrumentedRequestedPermissions;

    public static UndoableContext ignoringPermissions() {
        boolean original = Permissions.sIgnorePermissions.get();
        Permissions.sIgnorePermissions.set(true);

        if (SUPPORTS_ADOPT_SHELL_PERMISSIONS) {
            adoptShellPermissionIdentity();
        }

        return new UndoableContext(() -> {
            if (SUPPORTS_ADOPT_SHELL_PERMISSIONS) {
                dropShellPermissionIdentity();
            }
            Permissions.sIgnorePermissions.set(original);
        });
    }

    private Permissions() {
        // Packages requires using INTERACT_ACROSS_USERS_FULL but we don't want it to rely on
        // Permissions or it'll recurse forever - so we disable permission checks and just use
        // shell permission adoption directly while initialising
        try (UndoableContext c = ignoringPermissions()) {
            if (SUPPORTS_ADOPT_SHELL_PERMISSIONS) {
                mShellPermissions = sShellPackage.requestedPermissions();
            } else {
                mShellPermissions = new HashSet<>();
            }
            mInstrumentedRequestedPermissions = sInstrumentedPackage.requestedPermissions();
        }
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withPermission(PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions goes here
     * }
     * }
     */
    public PermissionContextImpl withPermission(String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermission(permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted only when running
     * on the given version or above.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>If the version does not match, the permission context will not change.
     */
    public PermissionContextImpl withPermissionOnVersionAtLeast(
            int minSdkVersion, String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermissionOnVersionAtLeast(minSdkVersion, permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted only when running
     * on the given version or below.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>If the version does not match, the permission context will not change.
     */
    public PermissionContextImpl withPermissionOnVersionAtMost(
            int maxSdkVersion, String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermissionOnVersionAtMost(maxSdkVersion, permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted only when running
     * on the range of versions given (inclusive).
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>If the version does not match, the permission context will not change.
     */
    public PermissionContextImpl withPermissionOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermissionOnVersionBetween(minSdkVersion, maxSdkVersion, permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted only when running
     * on the given version.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>If the version does not match, the permission context will not change.
     */
    public PermissionContextImpl withPermissionOnVersion(int sdkVersion, String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermissionOnVersion(sdkVersion, permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are granted.
     *
     * <p>If the appOps cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withAppOps(APP_OP1, APP_OP2) {
     * // Code which needs the app ops goes here
     * }
     * }
     */
    public PermissionContextImpl withAppOp(String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withAppOp(appOps);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are granted.
     *
     * <p>If the appOps cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withAppOps(APP_OP1, APP_OP2) {
     * // Code which needs the app ops goes here
     * }
     * }
     *
     * <p>If the version does not match the appOp will not be granted.
     */
    public PermissionContextImpl withAppOpOnVersion(int sdkVersion, String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withAppOpOnVersion(sdkVersion, appOps);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are granted.
     *
     * <p>If the appOps cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withAppOps(APP_OP1, APP_OP2) {
     * // Code which needs the app ops goes here
     * }
     * }
     *
     * <p>If the version does not match the appOp will not be granted.
     */
    public PermissionContextImpl withAppOpOnVersionAtLeast(int sdkVersion, String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withAppOpOnVersionAtLeast(sdkVersion, appOps);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are granted.
     *
     * <p>If the appOps cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withAppOps(APP_OP1, APP_OP2) {
     * // Code which needs the app ops goes here
     * }
     * }
     *
     * <p>If the version does not match the appOp will not be granted.
     */
    public PermissionContextImpl withAppOpOnVersionAtMost(int sdkVersion, String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withAppOpOnVersionAtMost(sdkVersion, appOps);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are granted.
     *
     * <p>If the appOps cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withAppOps(APP_OP1, APP_OP2) {
     * // Code which needs the app ops goes here
     * }
     * }
     *
     * <p>If the version does not match the appOp will not be granted.
     */
    public PermissionContextImpl withAppOpOnVersionBetween(
            int minSdkVersion, int maxSdkVersion, String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withAppOpOnVersionBetween(minSdkVersion, maxSdkVersion, appOps);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are not granted.
     *
     * <p>If the permissions cannot be denied, and are not already denied, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p =
     * mTestApis.permissions().withoutPermission(PERMISSION1, PERMISSION2) {
     * // Code which needs the permissions to be denied goes here
     * }
     */
    public PermissionContextImpl withoutPermission(String... permissions) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withoutPermission(permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given appOps are not granted.
     *
     * <p>If the appOps cannot be denied, and are not already denied, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p =
     * mTestApis.permissions().withoutappOp(APP_OP1, APP_OP2) {
     * // Code which needs the appOp to be denied goes here
     * }
     * }
     */
    public PermissionContextImpl withoutAppOp(String... appOps) {
        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withoutAppOp(appOps);

        return permissionContext;
    }

    void undoPermission(PermissionContext permissionContext) {
        mPermissionContexts.remove(permissionContext);
        applyPermissions();
    }

    void applyPermissions() {
        if (sIgnorePermissions.get()) {
            return;
        }

        Set<String> grantedPermissions = new HashSet<>();
        Set<String> deniedPermissions = new HashSet<>();
        Set<String> grantedAppOps = new HashSet<>();
        Set<String> deniedAppOps = new HashSet<>();

        synchronized (mPermissionContexts) {
            for (PermissionContextImpl permissionContext : mPermissionContexts) {
                for (String permission : permissionContext.grantedPermissions()) {
                    grantedPermissions.add(permission);
                    deniedPermissions.remove(permission);
                }

                for (String permission : permissionContext.deniedPermissions()) {
                    grantedPermissions.remove(permission);
                    deniedPermissions.add(permission);
                }

                for (String appOp : permissionContext.grantedAppOps()) {
                    grantedAppOps.add(appOp);
                    deniedAppOps.remove(appOp);
                }

                for (String appOp : permissionContext.deniedAppOps()) {
                    grantedAppOps.remove(appOp);
                    deniedAppOps.add(appOp);
                }
            }
        }

        setPermissionState(
                TestApis.packages().instrumented(),
                TestApis.users().instrumented(),
                grantedPermissions,
                deniedPermissions);
        setAppOpState(
                hasAdoptedShellPermissionIdentity ? sShellPackage : sInstrumentedPackage,
                TestApis.users().instrumented(),
                grantedAppOps,
                deniedAppOps
        );
    }

    /**
     * Throw an exception including permission contextual information.
     */
    public void throwPermissionException(
            String message, String permission) {
        String protectionLevel = "Permission not found";
        try {
            protectionLevel = Integer.toString(sPackageManager.getPermissionInfo(
                    permission, /* flags= */ 0).protectionLevel);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Permission not found", e);
        }



        try (UndoableContext c = ignoringPermissions()){
            throw new NeneException(message + "\n\nRunning On User: " + sUser
                    + "\nPermission: " + permission
                    + "\nPermission protection level: " + protectionLevel
                    + "\nPermission state: " + sContext.checkSelfPermission(permission)
                    + "\nInstrumented Package: " + sInstrumentedPackage.packageName()
                    + "\n\nRequested Permissions:\n"
                    + sInstrumentedPackage.requestedPermissions()
                    + "\n\nCan adopt shell permissions: " + SUPPORTS_ADOPT_SHELL_PERMISSIONS
                    + "\nShell permissions:"
                    + mShellPermissions
                    + "\nExempt Shell permissions: " + EXEMPT_SHELL_PERMISSIONS);
        }
    }

    void clearPermissions() {
        mPermissionContexts.clear();
        applyPermissions();
    }

    /**
     * Returns all of the permissions which can be adopted.
     */
    public Set<String> adoptablePermissions() {
        return mShellPermissions;
    }

    /**
     * Returns all of the permissions which are currently able to be used.
     */
    public Set<String> usablePermissions() {
        Set<String> usablePermissions = new HashSet<>();
        usablePermissions.addAll(mShellPermissions);
        usablePermissions.addAll(mInstrumentedRequestedPermissions);
        return usablePermissions;
    }

    private void removePermissionContextsUntilCanApply() {
        try {
            mPermissionContexts.remove(mPermissionContexts.size() - 1);
            applyPermissions();
        } catch (NeneException e) {
            // Suppress NeneException here as we may get a few as we pop through the stack
        }
    }

    private boolean canGrantPermission(String permission) {
        try {
            PermissionInfo p = sPackageManager.getPermissionInfo(permission, /* flags= */ 0);
            if ((p.protectionLevel & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) > 0) {
                return true;
            }
            return (p.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) > 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** True if the current process has the given permission. */
    public boolean hasPermission(String permission) {
        return sContext.checkSelfPermission(permission) == PERMISSION_GRANTED;
    }

    /**
     * True if the current process has the given appOp set to ALLOWED.
     *
     * <p>This accounts for shell identity being adopted (in which case it will check the appop
     * status of the shell identity).
     */
    public boolean hasAppOpAllowed(String appOp) {
        Package appOpPackage = sInstrumentedPackage;
        if (hasAdoptedShellPermissionIdentity) {
            // We care about the shell package
            appOpPackage = sShellPackage;
        }

        return appOpPackage.appOps().get(appOp) == AppOpsMode.ALLOWED;
    }

    /**
     * Sets a permission state for a given package on a given user.
     *
     * Generally tests should not use this method directly. They should instead used the
     * {@link #withPermission} and {@link #withoutPermission} methods.
     *
     * When this is used while executing a test which uses the RequireAdbRoot annotation, and using
     * Android 15+, it will have access to all permissions for all apps.
     *
     * Otherwise, when applying to the instrumented package, shell permission adoption will
     * be used.
     *
     * Otherwise, if the permission is able to be granted/denied by ADB then that will be done.
     *
     * Otherwise an error will be thrown.
     */
    public void setPermissionState(Package pkg, UserReference user, Collection<String> permissionsToGrant, Collection<String> permissionsToDeny) {
        // TODO: replace with dependency on bedstead-root when properly modularised
//        if (Tags.hasTag("adb-root") && Versions.meetsMinimumSdkVersionRequirement(Versions.V)) {
//            for (String grantedPermission : permissionsToGrant) {
//                forceRootPermissionState(pkg, user, grantedPermission, true);
//            }
//            for (String deniedPermission : permissionsToDeny) {
//                forceRootPermissionState(pkg, user, deniedPermission, false);
//            }
//
//            return;
//        }

        setPermissionStateToPackageWithoutRoot(pkg, user, permissionsToGrant, permissionsToDeny);
    }

    /**
     * Sets an appOp state for a given package on a given user.
     *
     * Generally tests should not use this method directly. They should instead used the
     * {@link #withAppOp} and {@link #withoutAppOp} methods.
     *
     * Note that if shell permission identity is adopted, then the app op state will not be queried
     * for the package - and the shell package should have its app op state set instead.
     */
    public void setAppOpState(Package pkg, UserReference user, Collection<String> grantedAppOps, Collection<String> deniedAppOps) {
        // Filter so we get just the appOps which require a state that they are not currently in
        Set<String> filteredGrantedAppOps = grantedAppOps.stream()
                .filter(o -> pkg.appOps().get(o) != AppOpsMode.ALLOWED)
                .collect(Collectors.toSet());
        Set<String> filteredDeniedAppOps = deniedAppOps.stream()
                .filter(o -> pkg.appOps().get(o) != AppOpsMode.IGNORED)
                .collect(Collectors.toSet());

        if (!filteredGrantedAppOps.isEmpty() || !filteredDeniedAppOps.isEmpty()) {
            // We need MANAGE_APP_OPS_MODES to change app op permissions - but don't want to
            // infinite loop so won't use .appOps().set()
            Set<String> previousAdoptedShellPermissions = ShellCommandUtils.uiAutomation().getAdoptedShellPermissions();
            adoptShellPermissionIdentity(CommonPermissions.MANAGE_APP_OPS_MODES);
            for (String appOp : filteredGrantedAppOps) {
                sAppOpsManager.setMode(appOp, pkg.uid(sUser),
                        pkg.packageName(), AppOpsMode.ALLOWED.value());
            }
            for (String appOp : filteredDeniedAppOps) {
                sAppOpsManager.setMode(appOp, pkg.uid(sUser),
                        pkg.packageName(), AppOpsMode.IGNORED.value());
            }

            adoptShellPermissionIdentity(previousAdoptedShellPermissions);
        }
    }

    private void setPermissionStateToPackageWithoutAdoption(Package pkg, UserReference user, Collection<String> permissionsToGrant, Collection<String> permissionsToDeny) {
        for (String permission : permissionsToGrant) {
            if (canGrantPermission(permission)) {
                pkg.grantPermission(user, permission);
            } else {
                removePermissionContextsUntilCanApply();
                throwPermissionException("Requires granting permission " + permission + " but cannot.", permission);
            }
        }

        for (String permission : permissionsToDeny) {
            if (pkg.equals(TestApis.packages().instrumented()) && user.equals(TestApis.users().instrumented())) {
                // We can't deny permissions from ourselves or it'll kill the process
                removePermissionContextsUntilCanApply();
                throwPermissionException("Requires granting permission " + permission + " but cannot.", permission);
            } else {
                pkg.denyPermission(user, permission);
            }
        }
    }

    private void setPermissionStateToPackageWithoutRoot(Package pkg, UserReference user, Collection<String> permissionsToGrant, Collection<String> permissionsToDeny) {
        if (!pkg.equals(TestApis.packages().instrumented()) || !SUPPORTS_ADOPT_SHELL_PERMISSIONS) {
            // We can't adopt...
            setPermissionStateToPackageWithoutAdoption(pkg, user, permissionsToGrant, permissionsToDeny);
            return;
        }

        if (TestApis.packages().instrumented().isInstantApp()) {
            // Instant Apps aren't able to know the permissions of shell so we can't know if we can
            // adopt it - we'll assume we can adopt and log
            Log.i(LOG_TAG,
                    "Adopting all shell permissions as can't check shell: " + mPermissionContexts);
            adoptShellPermissionIdentity();
            return;
        }

        dropShellPermissionIdentity();
        // We first try to use shell permissions, because they can be revoked/etc. much more easily

        Set<String> adoptedShellPermissions = new HashSet<>();
        Set<String> grantedPermissions = new HashSet<>();
        Set<String> deniedPermissions = new HashSet<>();
        for (String permission : permissionsToGrant) {
            Log.d(LOG_TAG, "Trying to grant " + permission);
            if (sInstrumentedPackage.hasPermission(user, permission)) {
                // Already granted, can skip
                Log.d(LOG_TAG, permission + " already granted at runtime");
            } else if (mInstrumentedRequestedPermissions.contains(permission)
                    && sContext.checkSelfPermission(permission) == PERMISSION_GRANTED) {
                // Already granted, can skip
                Log.d(LOG_TAG, permission + " already granted from manifest");
            } else if (mShellPermissions.contains(permission)) {
                adoptedShellPermissions.add(permission);
            } else {
                grantedPermissions.add(permission);
            }
        }

        for (String permission : permissionsToDeny) {
            if (!sInstrumentedPackage.hasPermission(sUser, permission)) {
                // Already denied, can skip
            } else if (!mShellPermissions.contains(permission)) {
                adoptedShellPermissions.add(permission);
            } else {
                deniedPermissions.add(permission);
            }
        }

        if (!adoptedShellPermissions.isEmpty()) {
            adoptShellPermissionIdentity(adoptedShellPermissions);
        }
        if (!grantedPermissions.isEmpty()) {
            setPermissionStateToPackageWithoutAdoption(pkg, user, grantedPermissions, deniedPermissions);
        }
    }

    private void forceRootPermissionState(Package pkg, UserReference user, String permission, boolean granted) {
//        ShellCommandUtils.uiAutomation().addOverridePermissionState(pkg.uid(user), permission, granted ? PERMISSION_GRANTED : PERMISSION_DENIED);
    }

    public void resetRootPermissionState(Package pkg, UserReference user, String permission) {
//        ShellCommandUtils.uiAutomation().removeOverridePermissionState(pkg.uid(user), permission);
    }

    private static boolean hasAdoptedShellPermissionIdentity = false;
    private static void adoptShellPermissionIdentity(Collection<String> permissions) {
        adoptShellPermissionIdentity(permissions.toArray(new String[0]));
    }

    private static void adoptShellPermissionIdentity(String... permissions) {
        if (permissions.length == 0) {
            dropShellPermissionIdentity();
            return;
        }

        Log.d(LOG_TAG, "Adopting " + Arrays.toString(permissions));
        hasAdoptedShellPermissionIdentity = true;
        ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity(permissions);
    }

    private static void adoptShellPermissionIdentity() {
        Log.d(LOG_TAG, "Adopting all shell permissions");
        hasAdoptedShellPermissionIdentity = true;
        ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity();
    }

    private static void dropShellPermissionIdentity() {
        Log.d(LOG_TAG, "Dropping shell permissions");
        hasAdoptedShellPermissionIdentity = false;
        ShellCommandUtils.uiAutomation().dropShellPermissionIdentity();
    }
}
