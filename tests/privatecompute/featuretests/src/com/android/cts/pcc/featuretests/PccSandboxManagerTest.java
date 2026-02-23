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

package com.android.cts.pcc.featuretests;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.privatecompute.MigrationException;
import android.app.privatecompute.MigrationRequestResult;
import android.app.privatecompute.PccSandboxManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.cts.pcc.featuretests.services.MigrationTestService;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerTest {
    private PccSandboxManager mPccSandboxManager;
    private Context mContext;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPccSandboxManager = mContext.getSystemService(PccSandboxManager.class);
        assertNotNull("PccSandboxManager service not available", mPccSandboxManager);
    }

    @Test
    public void testIsPccTrustedSystemComponent_onlyTrustsValidComponents() {
        Set<String> expectedTrustedPackages = getPccTrustedPackages();

        // Iterate through ALL installed packages to ensure only expected packages return true.
        List<PackageInfo> allPackages = mContext.getPackageManager().getInstalledPackages(0);
        for (PackageInfo pkgInfo : allPackages) {
            String packageName = pkgInfo.packageName;
            int uid = pkgInfo.applicationInfo.uid;

            boolean isTrusted = mPccSandboxManager.isPccTrustedSystemComponent(uid, packageName);

            if (isTrusted) {
                boolean isValidReason = false;

                int appId = UserHandle.getAppId(uid);
                if (appId == Process.SYSTEM_UID
                        || appId == Process.BLUETOOTH_UID
                        || appId == Process.PHONE_UID) {
                    isValidReason = true;
                } else if (mPccSandboxManager.isPrivateComputeServicesUid(uid)) {
                    isValidReason = true;
                } else if (expectedTrustedPackages.contains(packageName)) {
                    // If it's trusted via config/role, it MUST be a system app
                    boolean isSystemApp =
                            (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                                    || (pkgInfo.applicationInfo.flags
                                                    & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                                            != 0;
                    assertTrue(
                            "Trusted package " + packageName + " must be a system app",
                            isSystemApp);

                    isValidReason = true;
                }

                assertTrue(
                        "Package "
                                + packageName
                                + " returned true for isPccTrustedSystemComponent but does not "
                                + "hold a valid role, config, or system UID.",
                        isValidReason);
            }
        }
    }

    /** Replicate PccSandboxManagerInternal#populatePccTrustedPackages. */
    private Set<String> getPccTrustedPackages() {
        Set<String> packages = new HashSet<>();

        String settingsIntelligencePackage =
                mContext.getString(
                        Resources.getSystem()
                                .getIdentifier(
                                        "config_systemSettingsIntelligence", "string", "android"));
        if (!settingsIntelligencePackage.isEmpty()) {
            packages.add(settingsIntelligencePackage);
        }

        String systemUiPackage =
                mContext.getString(
                        Resources.getSystem()
                                .getIdentifier("config_systemUi", "string", "android"));
        if (!systemUiPackage.isEmpty()) {
            packages.add(systemUiPackage);
        }

        String recentsUiComponent =
                mContext.getString(
                        Resources.getSystem()
                                .getIdentifier("config_recentsComponentName", "string", "android"));
        String recentsUiPackage = null;
        if (!recentsUiComponent.isEmpty()) {
            ComponentName cn = ComponentName.unflattenFromString(recentsUiComponent);
            if (cn != null) {
                recentsUiPackage = cn.getPackageName();
            }
        }
        if (recentsUiPackage != null && !recentsUiPackage.isEmpty()) {
            packages.add(recentsUiPackage);
        }

        String[] authorities = {
            android.provider.MediaStore.AUTHORITY,
            "telephony",
            android.provider.ContactsContract.AUTHORITY,
            android.provider.CalendarContract.AUTHORITY,
            "downloads",
            "com.android.externalstorage.documents",
            android.provider.Settings.AUTHORITY,
            android.provider.BlockedNumberContract.AUTHORITY
        };

        for (String auth : authorities) {
            String pkg = resolveProviderPackageName(auth);
            if (pkg != null) {
                packages.add(pkg);
            }
        }

        Intent setupWizardIntent = new Intent(Intent.ACTION_MAIN);
        setupWizardIntent.addCategory("android.intent.category.SETUP_WIZARD");
        List<ResolveInfo> setupMatches =
                mContext.getPackageManager()
                        .queryIntentActivities(
                                setupWizardIntent,
                                PackageManager.MATCH_SYSTEM_ONLY
                                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                                        | PackageManager.MATCH_DISABLED_COMPONENTS);

        for (ResolveInfo info : setupMatches) {
            if (info.activityInfo != null && info.activityInfo.packageName != null) {
                packages.add(info.activityInfo.packageName);
            }
        }

        return packages;
    }

    private String resolveProviderPackageName(String authority) {
        final PackageManager pm = mContext.getPackageManager();
        int flags =
                PackageManager.MATCH_SYSTEM_ONLY
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        ProviderInfo providerInfo = pm.resolveContentProvider(authority, flags);
        return providerInfo != null ? providerInfo.packageName : null;
    }

    @Test
    public void testIsPrivateComputeServicesUid_forNonAppUid_returnsFalse() {
        assertFalse(
                "PCS UID must be in the Application UID range",
                mPccSandboxManager.isPrivateComputeServicesUid(Process.LAST_APPLICATION_UID + 1));
        assertFalse(
                "PCS UID must be in the Application UID range",
                mPccSandboxManager.isPrivateComputeServicesUid(Process.FIRST_APPLICATION_UID - 1));
    }

    @Test
    public void testIsPrivateComputeServicesUid_forSystemUid_returnsFalse() {
        int currentUserId = UserHandle.myUserId();
        int systemUidForUser = UserHandle.getUid(currentUserId, Process.SYSTEM_UID);
        assertFalse(
                "PCC UID should not be a PCS process",
                mPccSandboxManager.isPrivateComputeServicesUid(systemUidForUser));
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES)
    public void testIsPrivateComputeServicesUid_withoutPermission_returnsFalse() {
        int testAppUid = Process.myUid();
        assertFalse(
                "isPrivateComputeServicesUid should return false for UID without the permission",
                mPccSandboxManager.isPrivateComputeServicesUid(testAppUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES)
    public void testIsPrivateComputeServicesUid_withPermission_returnsTrue() {
        int testAppUid = Process.myUid();
        assertTrue(
                "isPrivateComputeServicesUid should return true for UID with the permission",
                mPccSandboxManager.isPrivateComputeServicesUid(testAppUid));
    }

    @Test
    public void testWriteToAuditLog_exampleBundle_doesNotThrowsException() {
        PersistableBundle data = new PersistableBundle();
        data.putString("test_key", "test_value");

        mPccSandboxManager.writeToAuditLog(data);

        // By design, this API does not provide any feedback to the caller.
        // We are just checking that it does not throw an exception.
    }

    @Test
    public void testWriteToAuditLog_emptyBundle_doesNotThrowsException() {
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());

        // By design, this API does not provide any feedback to the caller.
        // We are just checking that it does not throw an exception.
    }

    @Test
    public void testStartNonPccProcessForDataMigration_triggersService() throws Exception {
        MigrationTestService.reset();
        CountDownLatch resultLatch = new CountDownLatch(1);

        mPccSandboxManager.startNonPccProcessForDataMigration(
                mContext.getMainExecutor(),
                new OutcomeReceiver<MigrationRequestResult, MigrationException>() {
                    @Override
                    public void onResult(MigrationRequestResult result) {
                        PersistableBundle extras = result.getExtras();
                        assertNotNull("Extras should not be null", extras);
                        assertEquals("test_value", extras.getString("test_key"));
                        if (result.getStatus()
                                == MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED) {
                            resultLatch.countDown();
                        }
                    }

                    @Override
                    public void onError(MigrationException error) {}
                });

        assertTrue(
                "Migration service should have been triggered",
                MigrationTestService.waitForMigration(5, TimeUnit.SECONDS));
        assertTrue(
                "Migration result should have been received",
                resultLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testStartNonPccProcessForDataMigration_serviceConnectionFailed() throws Exception {
        ComponentName componentName = new ComponentName(mContext, MigrationTestService.class);
        int originalState = mContext.getPackageManager().getComponentEnabledSetting(componentName);
        mContext.getPackageManager()
                .setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);

        try {
            CountDownLatch errorLatch = new CountDownLatch(1);
            final int[] errorCode = new int[1];
            mPccSandboxManager.startNonPccProcessForDataMigration(
                    mContext.getMainExecutor(),
                    new OutcomeReceiver<MigrationRequestResult, MigrationException>() {
                        @Override
                        public void onResult(MigrationRequestResult result) {}

                        @Override
                        public void onError(MigrationException error) {
                            errorCode[0] = error.getErrorCode();
                            errorLatch.countDown();
                        }
                    });

            assertTrue("Should receive an error", errorLatch.await(5, TimeUnit.SECONDS));
            assertEquals(
                    "Received unexpected error code",
                    MigrationException.ERROR_INVOCATION_FAILED,
                    errorCode[0]);
        } finally {
            mContext.getPackageManager()
                    .setComponentEnabledSetting(
                            componentName, originalState, PackageManager.DONT_KILL_APP);
        }
    }

    @Test
    public void testStartNonPccProcessForDataMigration_serviceRejects() throws Exception {
        MigrationTestService.reset();
        MigrationTestService.sResponseStatus = MigrationRequestResult.MIGRATION_REQUEST_REJECTED;
        CountDownLatch resultLatch = new CountDownLatch(1);

        mPccSandboxManager.startNonPccProcessForDataMigration(
                mContext.getMainExecutor(),
                new OutcomeReceiver<MigrationRequestResult, MigrationException>() {
                    @Override
                    public void onResult(MigrationRequestResult result) {
                        if (result.getStatus()
                                == MigrationRequestResult.MIGRATION_REQUEST_REJECTED) {
                            resultLatch.countDown();
                        }
                    }

                    @Override
                    public void onError(MigrationException error) {}
                });

        assertTrue(
                "Migration service should have been triggered",
                MigrationTestService.waitForMigration(5, TimeUnit.SECONDS));
        assertTrue(
                "Migration rejection should have been received",
                resultLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testIsPccTrustedSystemComponent_returnsFalse() {
        int testAppUid = Process.myUid();
        String testPackageName = mContext.getPackageName();
        assertFalse(
                "isPccTrustedSystemComponent should return false for untrusted app",
                mPccSandboxManager.isPccTrustedSystemComponent(testAppUid, testPackageName));
    }

    @Test
    public void testIsPccTrustedSystemComponent_forSystemUid_returnsTrue() {
        // System UID is always trusted
        int currentUserId = UserHandle.myUserId();
        int systemUidForUser = UserHandle.getUid(currentUserId, Process.SYSTEM_UID);
        boolean isTrusted =
                mPccSandboxManager.isPccTrustedSystemComponent(systemUidForUser, "android");
        assertTrue("System UID should be trusted", isTrusted);
    }

    @Test
    public void testIsPccTrustedSystemComponent_forBluetoothUid_returnsTrue() {
        // Bluetooth UID is always trusted
        int currentUserId = UserHandle.myUserId();
        int bluetoothUidForUser = UserHandle.getUid(currentUserId, Process.BLUETOOTH_UID);
        String[] packages = mContext.getPackageManager().getPackagesForUid(bluetoothUidForUser);
        if (packages != null && packages.length > 0) {
            boolean isTrusted =
                    mPccSandboxManager.isPccTrustedSystemComponent(
                            bluetoothUidForUser, packages[0]);
            assertTrue("Bluetooth UID should be trusted", isTrusted);
        }
    }

    @Test
    public void testIsPccTrustedSystemComponent_forPhoneUid_returnsTrue() {
        // Phone UID is always trusted
        int currentUserId = UserHandle.myUserId();
        int phoneUidForUser = UserHandle.getUid(currentUserId, Process.PHONE_UID);
        String[] packages = mContext.getPackageManager().getPackagesForUid(phoneUidForUser);
        if (packages != null && packages.length > 0) {
            boolean isTrusted =
                    mPccSandboxManager.isPccTrustedSystemComponent(phoneUidForUser, packages[0]);
            assertTrue("Phone UID should be trusted", isTrusted);
        }
    }
}

