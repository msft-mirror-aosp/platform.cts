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

package com.android.cts.pcc.featuretests;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.common.StorageTestUtils.writeFile;
import static com.android.cts.pcc.featuretests.services.PccStorageService.COMMAND_CLEANUP;
import static com.android.cts.pcc.featuretests.services.PccStorageService.COMMAND_WRITE_CACHE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageService.COMMAND_WRITE_DE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageService.COMMAND_WRITE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageService.EXTRA_COMMAND;
import static com.android.cts.pcc.featuretests.services.PccStorageService.EXTRA_FILE_SIZE_BYTES;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.privatecompute.PccClient;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import com.android.cts.pcc.featuretests.services.PccStorageService;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// TODO: b/498514368 - Verify StorageStats API behaviour when querying stats for different package
@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccStorageStatsTest {
    private Context mContext;
    private StorageStatsManager mStorageStatsManager;
    private String mPackageName;

    private final BlockingQueue<IBinder> mBinderQueue = new LinkedBlockingQueue<>();
    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mBinderQueue.offer(service);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };

    private static final long PCC_TEST_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final long APP_TEST_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final long TEST_BUFFER = 10 * 1024 * 1024; // 10 MB
    private static final String TEST_FILE = "test_file.dat";
    private static final String TEST_CACHE_FILE = "test_cache_file.dat";
    private static final String TEST_DE_FILE = "test_de_file.dat";

    // PCC env has 3 files of size TEST_FILE_SIZE in CE, DE and cache dirs
    private static final long PCC_UID_DATA_SIZE = 3 * PCC_TEST_FILE_SIZE;
    // App env has 3 files of size APP_DATA_FILE_SIZE in CE, DE and cache dirs
    private static final long APP_UID_DATA_SIZE = 3 * APP_TEST_FILE_SIZE;
    private static final long PCC_UID_CACHE_SIZE = PCC_TEST_FILE_SIZE;
    private static final long APP_UID_CACHE_SIZE = APP_TEST_FILE_SIZE;
    private static int sPccUid = -1;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
        mPackageName = mContext.getPackageName();
        assertNotNull(mStorageStatsManager);

        writeFile(mContext.getFilesDir(), TEST_FILE, APP_TEST_FILE_SIZE);
        writeFile(
                mContext.createDeviceProtectedStorageContext().getFilesDir(),
                TEST_DE_FILE,
                APP_TEST_FILE_SIZE);
        writeFile(mContext.getCacheDir(), TEST_CACHE_FILE, APP_TEST_FILE_SIZE);

        // Trigger PCC storage writes
        sendCommand(COMMAND_WRITE_FILE, PCC_TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_DE_FILE, PCC_TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_CACHE_FILE, PCC_TEST_FILE_SIZE);
        sPccUid = Process.myUid() + 20000;
    }

    @After
    public void tearDown() throws Exception {
        deleteIgnoreException(new File(mContext.getFilesDir(), TEST_FILE));
        deleteIgnoreException(
                new File(
                        mContext.createDeviceProtectedStorageContext().getFilesDir(),
                        TEST_DE_FILE));
        deleteIgnoreException(new File(mContext.getCacheDir(), TEST_CACHE_FILE));

        sendCommand(COMMAND_CLEANUP, 0);
    }

    private void sendCommand(String command, long size) throws Exception {
        ComponentName serviceComponent =
                new ComponentName(mPackageName, PccStorageService.class.getName());
        Intent intent = new Intent();
        intent.setComponent(serviceComponent);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IBinder binder = mBinderQueue.poll(5000, TimeUnit.MILLISECONDS);
        assertNotNull("Failed to bind to PCC service", binder);
        PccClient pccClient = PccClient.createInstance(mContext, binder);

        Bundle data = new Bundle();
        data.putString(EXTRA_COMMAND, command);
        data.putLong(EXTRA_FILE_SIZE_BYTES, size);

        pccClient.sendData(data);

        mContext.unbindService(mConnection);
        mBinderQueue.clear();

        // Allow some time for file IO to be accounted by system
        Thread.sleep(2000);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForPackage_queryForOwnPackageWithoutPermission_onlyAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        long minSize = APP_UID_DATA_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = APP_UID_CACHE_SIZE;
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
    public void queryStatsForPackage_queryForOwnPackageWithPermission_bothPccAndAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        long minSize = PCC_UID_DATA_SIZE + APP_UID_DATA_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = PCC_UID_CACHE_SIZE + APP_UID_CACHE_SIZE;
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
    public void queryStatsForUid_queryForOwnUidWithoutPermission_onlyAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        long minSize = APP_UID_DATA_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = APP_UID_CACHE_SIZE;
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
    public void queryStatsForUid_queryForOwnUidWithPermission_onlyAppStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        long minSize = APP_UID_DATA_SIZE;
        assertTrue(
                "Data verification failed. Total data: "
                        + stats.getDataBytes()
                        + ". Expected data: "
                        + minSize,
                stats.getDataBytes() >= minSize && stats.getDataBytes() < minSize + TEST_BUFFER);

        long minCacheSize = APP_UID_CACHE_SIZE;
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
    public void queryStatsForUid_queryForPccUidWithoutPermission_throwsSecurityException()
            throws Exception {
        assertThrows(
                "queryStatsForUid should throw SecurityException without PACKAGE_USAGE_STATS "
                        + "and querying stats for PCC by UID",
                SecurityException.class,
                () -> mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, sPccUid));
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void queryStatsForUid_queryForPccUidWithPermission_onlyPccStatsReturned()
            throws Exception {
        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, sPccUid);

        // Assert that only Pcc data is included
        long minSize = PCC_UID_DATA_SIZE;
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
}
