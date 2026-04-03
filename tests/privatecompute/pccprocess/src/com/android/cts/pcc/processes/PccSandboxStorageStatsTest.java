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

package com.android.cts.pcc.processes;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.common.StorageTestUtils.writeFile;
import static com.android.cts.pcc.processes.AppDataSetupTest.APP_DATA_FILE_SIZE;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.storage.StorageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

// TODO: b/498514621 - Move PccSandboxStorageStatsTest to featuretests apk
@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxStorageStatsTest {
    private Context mContext;
    private StorageStatsManager mStorageStatsManager;
    private String mPackageName;

    private static final String DATA_INIT_PACKAGE = "com.android.cts.pcc.datainit";
    private static int sDataInitPackageUid = -1;
    private static int sDataInitPackagePccUid = -1;
    private static int sDefiningAppUid = -1;

    private static final long TEST_FILE_SIZE = 5 * 1024 * 1024;
    private static final long TEST_BUFFER = 5 * 1024 * 1024;
    static final String TEST_FILE = "test_file.dat";
    static final String TEST_CACHE_FILE = "test_cache_file.dat";
    static final String TEST_DE_FILE = "test_de_file.dat";

    // App env has 3 files of size APP_DATA_FILE_SIZE in CE, DE and cache dirs
    private static final long PKG_UID_SIZE = 3 * APP_DATA_FILE_SIZE;
    // PCC env has 3 files of size TEST_FILE_SIZE in CE, DE and cache dirs
    private static final long PCC_UID_SIZE = 3 * TEST_FILE_SIZE;
    private static final long DIFF_APP_PKG_UID_SIZE = 3 * APP_DATA_FILE_SIZE;
    private static final long DIFF_APP_PCC_UID_SIZE = 3 * TEST_FILE_SIZE;

    private static final long PKG_UID_CACHE_SIZE = APP_DATA_FILE_SIZE;
    private static final long PCC_UID_CACHE_SIZE = TEST_FILE_SIZE;
    private static final long DIFF_APP_PKG_UID_CACHE_SIZE = APP_DATA_FILE_SIZE;
    private static final long DIFF_APP_PCC_UID_CACHE_SIZE = TEST_FILE_SIZE;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
        mPackageName = mContext.getPackageName();
        assertNotNull(mStorageStatsManager);

        writeFile(mContext.getFilesDir(), TEST_FILE, TEST_FILE_SIZE);
        writeFile(
                mContext.createDeviceProtectedStorageContext().getFilesDir(),
                TEST_DE_FILE,
                TEST_FILE_SIZE);
        writeFile(mContext.getCacheDir(), TEST_CACHE_FILE, TEST_FILE_SIZE);

        // TODO: b/496799258 - Use targetPreparer instead of thread sleeping
        Thread.sleep(2000);

        PackageManager mPackageManager = mContext.getPackageManager();
        sDataInitPackageUid = mPackageManager.getPackageUid(DATA_INIT_PACKAGE, 0);
        sDataInitPackagePccUid = sDataInitPackageUid + 20000;
        sDefiningAppUid = mPackageManager.getAppUidForPrivateComputeCoreUid(Process.myUid());
    }

    @After
    public void tearDown() {
        deleteIgnoreException(new File(mContext.getFilesDir(), TEST_FILE));
        deleteIgnoreException(
                new File(
                        mContext.createDeviceProtectedStorageContext().getFilesDir(),
                        TEST_DE_FILE));
        deleteIgnoreException(new File(mContext.getCacheDir(), TEST_CACHE_FILE));
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForPackage_queryForOwnPackageWithoutPermission_onlyPccStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        long minSize = PCC_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PCC_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForPackage_queryForDiffPackageWithoutPermission_throwsSecurityException()
            throws Exception {
        assertThrows(
                "queryStatsForPackage should throw SecurityException without"
                        + "PACKAGE_USAGE_STATS "
                        + "and querying for a different app",
                SecurityException.class,
                () ->
                        mStorageStatsManager.queryStatsForPackage(
                                StorageManager.UUID_DEFAULT,
                                DATA_INIT_PACKAGE,
                                Process.myUserHandle()));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForPackage_queryForOwnPackageWithPermission_bothPccAndAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        long minSize = PCC_UID_SIZE + PKG_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PCC_UID_CACHE_SIZE + PKG_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForPackage_queryForDiffPackageWithPermission_bothPccAndAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, DATA_INIT_PACKAGE, Process.myUserHandle());

        long minSize = DIFF_APP_PCC_UID_SIZE + DIFF_APP_PKG_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = DIFF_APP_PCC_UID_CACHE_SIZE + DIFF_APP_PKG_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForOwnUidWithoutPermission_onlyPccStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        long minSize = PCC_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PCC_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForOwnUidWithPermission_onlyPccStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        long minSize = PCC_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PCC_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDefiningAppUidWithoutPermission_throwsSecurityException()
            throws Exception {
        assertThrows(
                "queryStatsForUid should throw SecurityException without"
                        + "PACKAGE_USAGE_STATS "
                        + "and querying stats for defining app by UID",
                SecurityException.class,
                () ->
                        mStorageStatsManager.queryStatsForUid(
                                StorageManager.UUID_DEFAULT, sDefiningAppUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDefiningAppUidWithPermission_onlyAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, sDefiningAppUid);

        // Assert that only App data is included
        long minSize = PKG_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PKG_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDiffAppUidWithoutPermission_throwsSecurityException()
            throws Exception {
        assertThrows(
                "queryStatsForUid should throw SecurityException without"
                        + "PACKAGE_USAGE_STATS "
                        + "and querying stats for different app by UID",
                SecurityException.class,
                () ->
                        mStorageStatsManager.queryStatsForUid(
                                StorageManager.UUID_DEFAULT, sDataInitPackageUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDiffAppUidWithPermission_onlyAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(
                        StorageManager.UUID_DEFAULT, sDataInitPackageUid);

        long minSize = DIFF_APP_PKG_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = DIFF_APP_PKG_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDiffAppsPccUidWithoutPermission_throwsSecurityException()
            throws Exception {
        assertThrows(
                "queryStatsForUid should throw SecurityException without"
                        + "PACKAGE_USAGE_STATS "
                        + "and querying stats for different app's PCC UID",
                SecurityException.class,
                () ->
                        mStorageStatsManager.queryStatsForUid(
                                StorageManager.UUID_DEFAULT, sDataInitPackagePccUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForDiffAppsPccUidWithPermission_onlyDiffAppPccStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(
                        StorageManager.UUID_DEFAULT, sDataInitPackagePccUid);

        long minSize = DIFF_APP_PCC_UID_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = DIFF_APP_PCC_UID_CACHE_SIZE;
        assertTrue(
                "Cache Data verification failed. Total data: "
                        + stats.getCacheBytes()
                        + ". Expected data: "
                        + minCacheSize,
                stats.getCacheBytes() >= minCacheSize
                        && stats.getCacheBytes() < minCacheSize + TEST_BUFFER);
    }
}
