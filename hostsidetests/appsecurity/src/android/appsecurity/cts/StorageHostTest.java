/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests that exercise various storage APIs.
 */
public class StorageHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String PKG_STATS = "com.android.cts.storagestatsapp";
    private static final String PKG_A = "com.android.cts.storageapp_a";
    private static final String PKG_B = "com.android.cts.storageapp_b";
    private static final String APK_STATS = "CtsStorageStatsApp.apk";
    private static final String APK_A = "CtsStorageAppA.apk";
    private static final String APK_B = "CtsStorageAppB.apk";
    private static final String CLASS_STATS = "com.android.cts.storagestatsapp.StorageStatsTest";
    private static final String CLASS = "com.android.cts.storageapp.StorageTest";

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    private int[] mUsers;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUsers = Utils.createUsersForTest(getDevice());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);

        Utils.removeUsersForTest(getDevice(), mUsers);
        mUsers = null;
    }

    private void prepareTestApps() throws Exception {
        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_STATS), false));
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_A), false));
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_B), false));

        for (int user : mUsers) {
            getDevice().executeShellCommand("appops set --user " + user + " " + PKG_STATS
                    + " android:get_usage_stats allow");
        }
    }

    public void testEverything() throws Exception {
        prepareTestApps(); doVerifyQuota();
        prepareTestApps(); doVerifyAppStats();
        prepareTestApps(); doVerifyAppQuota();
        prepareTestApps(); doVerifyAppAllocate();
        prepareTestApps(); doVerifySummary();
        prepareTestApps(); doVerifyStats();
        prepareTestApps(); doVerifyStatsMultiple();
        prepareTestApps(); doVerifyStatsExternal();
        prepareTestApps(); doVerifyStatsExternalConsistent();
        prepareTestApps(); doVerifyCategory();
        prepareTestApps(); doCache();
        prepareTestApps(); doFullDisk();
    }

    public void doVerifyQuota() throws Exception {
        runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyQuota", Utils.USER_OWNER);
    }

    public void doVerifyAppStats() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
        }

        // TODO: remove this once 34723223 is fixed
        getDevice().executeShellCommand("sync");

        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceManual", user);
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceApi", user);
        }
    }

    public void doVerifyAppQuota() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyQuotaApi", user);
        }
    }

    public void doVerifyAppAllocate() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyAllocateApi", user);
        }
    }

    public void doVerifySummary() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifySummary", user);
        }
    }

    public void doVerifyStats() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStats", user);
        }
    }

    public void doVerifyStatsMultiple() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);

            runDeviceTests(PKG_B, CLASS, "testAllocate", user);
        }

        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsMultiple", user);
        }
    }

    public void doVerifyStatsExternal() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternal", user);
        }
    }

    public void doVerifyStatsExternalConsistent() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternalConsistent", user);
        }
    }

    public void doVerifyCategory() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyCategory", user);
        }
    }

    public void doCache() throws Exception {
        // To make the cache clearing logic easier to verify, ignore any cache
        // and low space reserved space.
        getDevice().executeShellCommand("settings put global sys_storage_threshold_max_bytes 0");
        getDevice().executeShellCommand("settings put global sys_storage_cache_max_bytes 0");
        try {
            for (int user : mUsers) {
                // Clear all other cached data to give ourselves a clean slate
                getDevice().executeShellCommand("pm trim-caches 4096G");
                runDeviceTests(PKG_STATS, CLASS_STATS, "testCacheClearing", user);

                getDevice().executeShellCommand("pm trim-caches 4096G");
                runDeviceTests(PKG_STATS, CLASS_STATS, "testCacheBehavior", user);
            }
        } finally {
            getDevice().executeShellCommand("settings delete global sys_storage_threshold_max_bytes");
            getDevice().executeShellCommand("settings delete global sys_storage_cache_max_bytes");
        }
    }

    public void doFullDisk() throws Exception {
        // Clear all other cached and external storage data to give ourselves a
        // clean slate to test against
        getDevice().executeShellCommand("pm trim-caches 4096G");
        getDevice().executeShellCommand("rm -rf /sdcard/*");

        // We're interested in any crashes while disk full
        final String lastEvent = getDevice().executeShellCommand("logcat -d -b events -t 1");
        final String sinceTime = lastEvent.trim().substring(0, 18);

        // Try our hardest to fill up the entire disk
        runDeviceTests(PKG_A, CLASS, "testFullDisk", Utils.USER_OWNER);
        runDeviceTests(PKG_A, CLASS, "testTweakComponent", Utils.USER_OWNER);

        // Try poking around a couple of settings apps
        getDevice().executeShellCommand("input keyevent KEY_HOME");
        Thread.sleep(1000);
        getDevice().executeShellCommand("am start -a android.settings.SETTINGS");
        Thread.sleep(2000);
        getDevice().executeShellCommand("input keyevent KEY_BACK");
        Thread.sleep(1000);
        getDevice().executeShellCommand("am start -a android.os.storage.action.MANAGE_STORAGE");
        Thread.sleep(2000);
        getDevice().executeShellCommand("input keyevent KEY_BACK");
        Thread.sleep(1000);

        // Our misbehaving app above shouldn't have caused anything else to
        // think the disk was full
        String troubleLogs = getDevice().executeShellCommand(
                "logcat -d -t '" + sinceTime + "' -e '(ENOSPC|No space left on device)'");

        if (troubleLogs == null) troubleLogs = "";
        troubleLogs = troubleLogs.trim().replaceAll("\\-+ beginning of [a-z]+", "");

        if (troubleLogs.length() > 4) {
            fail("Unexpected crashes while disk full: " + troubleLogs);
        }
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName,
            int userId) throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }
}
