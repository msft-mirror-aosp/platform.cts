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

package android.security.cts.advancedprotection;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseAdvancedProtectionTest {
    private static final String TAG = "BaseAdvancedProtectionTest";

    protected final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    protected AdvancedProtectionManager mManager;

    private AppOpsManager mAppOpsManager;
    private IPackageManager mIPackageManager;
    private UserManager mUserManager;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    private boolean mInitialApmState;
    private long mInitialAllowedNetworks;
    private HashMap<Integer, HashMap<String, Integer>> mInitialOpRequestInstallPackages =
            new HashMap<>();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        assumeTrue(shouldTestAdvancedProtection(mInstrumentation.getContext()));
        mManager = (AdvancedProtectionManager) mInstrumentation
                .getContext().getSystemService(Context.ADVANCED_PROTECTION_SERVICE);

        setupInitialAllowedNetworks();
        setupInitialOpRequestInstallPackages();

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);

        mInitialApmState = mManager.isAdvancedProtectionEnabled();
    }

    private static boolean shouldTestAdvancedProtection(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false;
        }
        return true;
    }

    @After
    public void teardown() throws InterruptedException {
        if (mManager == null) {
            return;
        }

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mManager.setAdvancedProtectionEnabled(mInitialApmState);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        Thread.sleep(1000);

        teardownInitialAllowedNetworks();
        teardownInitialOpRequestInstallPackages();
    }

    private void setupInitialAllowedNetworks() {
        mTelephonyManager = mInstrumentation.getContext().getSystemService(TelephonyManager.class);
        mInitialAllowedNetworks =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager,
                        (tm) ->
                                tm.getAllowedNetworkTypesForReason(
                                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    private void teardownInitialAllowedNetworks() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) ->
                        tm.setAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                                mInitialAllowedNetworks),
                Manifest.permission.MODIFY_PHONE_STATE);
    }

    private void setupInitialOpRequestInstallPackages() {
        mAppOpsManager = mInstrumentation.getContext().getSystemService(AppOpsManager.class);
        mIPackageManager = AppGlobals.getPackageManager();
        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        // Shell is not allowed have MANAGE_USERS permission, hence using CREATE_USERS here.
        List<UserInfo> userInfoList = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mUserManager, UserManager::getAliveUsers, Manifest.permission.CREATE_USERS);
        for (UserInfo userInfo : userInfoList) {
            try {
                final int userId = userInfo.id;
                final String[] packagesWithRequestInstallPermission = mIPackageManager
                        .getAppOpPermissionPackages(
                                Manifest.permission.REQUEST_INSTALL_PACKAGES, userId);
                for (String packageName : packagesWithRequestInstallPermission) {
                    try {
                        final int uid = mPackageManager.getPackageUidAsUser(packageName, userId);
                        final int mode = mAppOpsManager.checkOpNoThrow(
                                AppOpsManager.OP_REQUEST_INSTALL_PACKAGES, uid, packageName);
                        if (!mInitialOpRequestInstallPackages.containsKey(userId)) {
                            mInitialOpRequestInstallPackages.put(userId, new HashMap<>());
                        }
                        HashMap<String, Integer> map = mInitialOpRequestInstallPackages.get(userId);
                        map.put(packageName, mode);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.e(TAG, "Couldn't retrieve uid for a package: " + e);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't retrieve packages with REQUEST_INSTALL_PACKAGES."
                        + " getAppOpPermissionPackages() threw the following exception: " + e);
            }
        }
    }

    private void teardownInitialOpRequestInstallPackages() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_APP_OPS_MODES);

        for (Map.Entry<Integer, HashMap<String, Integer>> userToMap :
                mInitialOpRequestInstallPackages.entrySet()) {
            final int userId = userToMap.getKey();
            for (Map.Entry<String, Integer> packageToMode : userToMap.getValue().entrySet()) {
                try {
                    int uid = mPackageManager.getPackageUidAsUser(packageToMode.getKey(), userId);
                    mAppOpsManager.setMode(AppOpsManager.OP_REQUEST_INSTALL_PACKAGES,
                            uid, packageToMode.getKey(), packageToMode.getValue());
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Couldn't retrieve uid for a package: " + e);
                }
            }
        }

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }
}
