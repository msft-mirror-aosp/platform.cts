/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts.host;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the ScopedStorageTest tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ScopedStorageHostTest extends BaseHostTestCase {
    private boolean mIsExternalStorageSetup;

    private ContentProviderHandler mContentProviderHandler;

    /**
     * Runs the given phase of ScopedStorageTest by calling into the device.
     * Throws an exception if the test phase fails.
     */
    void runDeviceTest(String phase) throws Exception {
        assertThat(runDeviceTests("android.scopedstorage.cts",
                "android.scopedstorage.cts.ScopedStorageTest", phase)).isTrue();
    }

    /**
     * Runs the given phase of ScopedStorageTest by calling into the device with {@code
     * --no-isolated-storage} flag.
     * Throws an exception if the test phase fails.
     */
    void runDeviceTestWithDisabledIsolatedStorage(String phase) throws Exception {
        runDeviceTests(new DeviceTestRunOptions("android.scopedstorage.cts")
            .setDevice(getDevice())
            .setTestClassName("android.scopedstorage.cts.ScopedStorageTest")
            .setTestMethodName(phase)
            .setDisableIsolatedStorage(true));
    }

    private void setupExternalStorage() throws Exception {
        if (!mIsExternalStorageSetup) {
            runDeviceTest("setupExternalStorage");
            mIsExternalStorageSetup = true;
        }
    }

    @Before
    public void setup() throws Exception {
        // Set up content provider. This would install android.tradefed.contentprovider
        // which is used to create and delete files/Dir on device side test.
        mContentProviderHandler = new ContentProviderHandler(getDevice());
        mContentProviderHandler.setUp();

        setupExternalStorage();
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell -m 2770");
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell/files -m 2770");
    }

    @Before
    public void revokeStoragePermissions() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
    }

    @After
    public void tearDown() throws Exception {
        mContentProviderHandler.tearDown();
        executeShellCommand("rm -r /sdcard/Android/data/com.android.shell");
    }


    @Test
    public void testListUnsupportedFileType() throws Exception {
        runDeviceTest("testListUnsupportedFileType");
    }

    @Test
    public void testCantRenameToTopLevelDirectory() throws Exception {
        runDeviceTest("testCantRenameToTopLevelDirectory");
    }

    @Test
    public void testManageExternalStorageCanDeleteOtherAppsContents() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageCanDeleteOtherAppsContents");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testManageExternalStorageCanRenameOtherAppsContents() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageCanRenameOtherAppsContents");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testManageExternalStorageCantReadWriteOtherAppExternalDir() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageCantReadWriteOtherAppExternalDir");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testCheckInstallerAppAccessToObbDirs() throws Exception {
        allowAppOps("android:request_install_packages");
        grantPermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testCheckInstallerAppAccessToObbDirs");
        } finally {
            denyAppOps("android:request_install_packages");
            revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testCantAccessOtherAppsExternalDirs() throws Exception {
        runDeviceTest("testCantAccessOtherAppsExternalDirs");
    }

    @Test
    public void testCanCreateDefaultDirectory() throws Exception {
        runDeviceTest("testCanCreateDefaultDirectory");
    }

    @Test
    public void testManageExternalStorageQueryOtherAppsFile() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageQueryOtherAppsFile");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testManageExternalStorageDoesntSkipScanningDirtyNomediaDir() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageDoesntSkipScanningDirtyNomediaDir");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testScanDoesntSkipDirtySubtree() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testScanDoesntSkipDirtySubtree");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testOpenOtherPendingFilesFromFuse() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testOpenOtherPendingFilesFromFuse");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testAndroidMedia() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testAndroidMedia");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testWallpaperApisNoPermission() throws Exception {
        runDeviceTest("testWallpaperApisNoPermission");
    }

    @Test
    public void testWallpaperApisReadExternalStorage() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testWallpaperApisReadExternalStorage");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testWallpaperApisManageExternalStorageAppOp() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testWallpaperApisManageExternalStorageAppOp");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testWallpaperApisManageExternalStoragePrivileged() throws Exception {
        runDeviceTest("testWallpaperApisManageExternalStoragePrivileged");
    }

    @Test
    public void testNoIsolatedStorageInstrumentationFlag() throws Exception {
        runDeviceTestWithDisabledIsolatedStorage("testNoIsolatedStorageCanCreateFilesAnywhere");
        runDeviceTestWithDisabledIsolatedStorage(
                "testNoIsolatedStorageCantReadWriteOtherAppExternalDir");
        runDeviceTestWithDisabledIsolatedStorage("testNoIsolatedStorageStorageReaddir");
        runDeviceTestWithDisabledIsolatedStorage("testNoIsolatedStorageQueryOtherAppsFile");
    }

    @Test
    public void testRenameFromShell() throws Exception {
        final ITestDevice device = getDevice();
        final boolean isAdbRoot = device.isAdbRoot() ? true : false;
        try {
            if (isAdbRoot) {
                device.disableAdbRoot();
            }
            runDeviceTest("testRenameFromShell");
        } finally {
            if (isAdbRoot) {
                device.enableAdbRoot();
            }
        }
    }

    @Test
    public void testClearPackageData() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testClearPackageData");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    private void grantPermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm grant --user %d android.scopedstorage.cts %s",
                    currentUserId, perm);
        }
    }

    private void revokePermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm revoke --user %d android.scopedstorage.cts %s",
                    currentUserId, perm);
        }
    }

    private void allowAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts " + op + " allow");
        }
    }

    private void denyAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts " + op + " deny");
        }
    }
}
