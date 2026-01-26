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

import static com.android.cts.pcc.common.StorageTestUtils.writeFile;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.COMMAND_CLEANUP;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.COMMAND_WRITE_CACHE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.COMMAND_WRITE_DE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.COMMAND_WRITE_FILE;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.EXTRA_COMMAND;
import static com.android.cts.pcc.featuretests.services.PccStorageWriteService.EXTRA_FILE_SIZE_BYTES;

import static org.junit.Assert.assertNotNull;
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
import com.android.cts.pcc.featuretests.services.PccStorageWriteService;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    private static final long TEST_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long TEST_BUFFER = 1 * 1024 * 1024; // 1MB
    private static final String TEST_FILE = "test_file.dat";
    private static final String TEST_CACHE_FILE = "test_cache_file.dat";
    private static final String TEST_DE_FILE = "test_de_file.dat";

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
        mPackageName = mContext.getPackageName();
        assertNotNull(mStorageStatsManager);
    }

    @After
    public void tearDown() throws Exception {
        sendCommand(COMMAND_CLEANUP, 0);
    }

    private void sendCommand(String command, long size) throws Exception {
        ComponentName serviceComponent =
                new ComponentName(mPackageName, PccStorageWriteService.class.getName());
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
    public void testPccStorageNotCountedForDefiningAppByPackage() throws Exception {
        // 1. Write files to non-pcc storage
        writeFile(mContext.getFilesDir(), TEST_FILE, TEST_FILE_SIZE);
        writeFile(
                mContext.createDeviceProtectedStorageContext().getFilesDir(),
                TEST_DE_FILE,
                TEST_FILE_SIZE);
        writeFile(mContext.getCacheDir(), TEST_CACHE_FILE, TEST_FILE_SIZE);

        // 2. Get initial storage stats
        StorageStats initialStats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        // 3. Trigger PCC storage writes
        sendCommand(COMMAND_WRITE_FILE, TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_DE_FILE, TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_CACHE_FILE, TEST_FILE_SIZE);

        // 4. Get storage stats again WITHOUT permission
        StorageStats finalStats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        // 5. Assert that PCC data is NOT included
        assertTrue(
                "Data bytes should NOT include PCC data without permission. Initial: "
                        + initialStats.getDataBytes()
                        + ", Final: "
                        + finalStats.getDataBytes(),
                finalStats.getDataBytes() < initialStats.getDataBytes() + TEST_BUFFER);

        assertTrue(
                "Cache bytes should NOT include PCC data without permission. Initial: "
                        + initialStats.getCacheBytes()
                        + ", Final: "
                        + finalStats.getCacheBytes(),
                finalStats.getCacheBytes() < initialStats.getCacheBytes() + TEST_BUFFER);
    }

    @Test
    @EnsureDoesNotHavePermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageNotCountedForDefiningAppByUid() throws Exception {
        // 1. Write files to non-pcc storage
        writeFile(mContext.getFilesDir(), TEST_FILE, TEST_FILE_SIZE);
        writeFile(
                mContext.createDeviceProtectedStorageContext().getFilesDir(),
                TEST_DE_FILE,
                TEST_FILE_SIZE);
        writeFile(mContext.getCacheDir(), TEST_CACHE_FILE, TEST_FILE_SIZE);

        // 2. Get initial storage stats
        StorageStats initialStats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        // 3. Trigger PCC storage writes
        sendCommand(COMMAND_WRITE_FILE, TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_DE_FILE, TEST_FILE_SIZE);
        sendCommand(COMMAND_WRITE_CACHE_FILE, TEST_FILE_SIZE);

        // 4. Get storage stats again WITHOUT permission
        StorageStats finalStats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        // 5. Assert that PCC data is NOT included
        assertTrue(
                "Data bytes should NOT include PCC data without permission. Initial: "
                        + initialStats.getDataBytes()
                        + ", Final: "
                        + finalStats.getDataBytes(),
                finalStats.getDataBytes() < initialStats.getDataBytes() + TEST_BUFFER);

        assertTrue(
                "Cache bytes should NOT include PCC data without permission. Initial: "
                        + initialStats.getCacheBytes()
                        + ", Final: "
                        + finalStats.getCacheBytes(),
                finalStats.getCacheBytes() < initialStats.getCacheBytes() + TEST_BUFFER);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByPackage_CE() throws Exception {
        sendCommand(COMMAND_WRITE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        assertTrue(
                "Storage stats should include PCC CE data with permission. Total data: "
                        + stats.getDataBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByUid_CE() throws Exception {
        sendCommand(COMMAND_WRITE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        assertTrue(
                "Storage stats should include PCC CE data with permission. Total data: "
                        + stats.getDataBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByPackage_DE() throws Exception {
        sendCommand(COMMAND_WRITE_DE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        assertTrue(
                "Storage stats should include PCC DE data with permission. Total data: "
                        + stats.getDataBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByUid_DE() throws Exception {
        sendCommand(COMMAND_WRITE_DE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        assertTrue(
                "Storage stats should include PCC DE data with permission. Total data: "
                        + stats.getDataBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByPackage_Cache() throws Exception {
        sendCommand(COMMAND_WRITE_CACHE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, mPackageName, Process.myUserHandle());

        assertTrue(
                "Storage stats should include PCC cache data with permission. Total data: "
                        + stats.getDataBytes()
                        + ", Cache: "
                        + stats.getCacheBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE && stats.getCacheBytes() >= TEST_FILE_SIZE);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    public void testPccStorageCountedWithPermissionByUid_Cache() throws Exception {
        sendCommand(COMMAND_WRITE_CACHE_FILE, TEST_FILE_SIZE);

        StorageStats stats =
                mStorageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());

        assertTrue(
                "Storage stats should include PCC cache data with permission. Total data: "
                        + stats.getDataBytes()
                        + ", Cache: "
                        + stats.getCacheBytes(),
                stats.getDataBytes() >= TEST_FILE_SIZE && stats.getCacheBytes() >= TEST_FILE_SIZE);
    }
}
