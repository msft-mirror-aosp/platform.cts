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

package com.android.cts.appdataisolation.appa;

import static com.android.cts.appdataisolation.common.FileUtils.CE_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.DE_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.EXTERNAL_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirDoesNotExist;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsNotAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertFileDoesNotExist;
import static com.android.cts.appdataisolation.common.FileUtils.assertFileExists;
import static com.android.cts.appdataisolation.common.FileUtils.touchFile;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.RemoteException;
import android.support.test.uiautomator.UiDevice;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * This class is a helper class for AppDataIsolationTests to assert and check data stored in app.
 */
@SmallTest
public class AppATests {

    private static final String APPB_PKG = "com.android.cts.appdataisolation.appb";

    private Context mContext;
    private UiDevice mDevice;
    private String mCePath;
    private String mDePath;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mCePath = mContext.getApplicationInfo().dataDir;
        mDePath = mContext.getApplicationInfo().deviceProtectedDataDir;
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testCreateCeDeAppData() throws Exception {
        assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);

        touchFile(mCePath, CE_DATA_FILE_NAME);
        touchFile(mDePath, DE_DATA_FILE_NAME);

        assertFileExists(mCePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testCreateExternalDataDir() throws Exception {
        String externalStoragePath = mContext.getExternalFilesDir("").getAbsolutePath();
        assertFileDoesNotExist(externalStoragePath, EXTERNAL_DATA_FILE_NAME);
        touchFile(externalStoragePath, EXTERNAL_DATA_FILE_NAME);
        assertFileExists(externalStoragePath, EXTERNAL_DATA_FILE_NAME);
    }

    @Test
    public void testAppACeDataExists() {
        assertFileExists(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppACeDataDoesNotExist() {
        assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataExists() {
        assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataDoesNotExist() {
        assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppAExternalDataDoesExist() {
        String externalStoragePath = mContext.getExternalFilesDir("").getAbsolutePath();
        assertFileExists(externalStoragePath, EXTERNAL_DATA_FILE_NAME);
    }

    @Test
    public void testAppAExternalDataDoesNotExist() {
        String externalStoragePath = mContext.getExternalFilesDir("").getAbsolutePath();
        assertFileDoesNotExist(externalStoragePath, EXTERNAL_DATA_FILE_NAME);
    }

    @Test
    public void testAppAExternalDataUnavailable() {
        assertNull(mContext.getExternalFilesDir(""));
    }

    @Test
    public void testAppACurProfileDataAccessible() {
        assertDirIsAccessible("/data/misc/profiles/cur/0/" + mContext.getPackageName());
    }

    @Test
    public void testAppARefProfileDataNotAccessible() {
        assertDirIsNotAccessible("/data/misc/profiles/ref");
    }

    @Test
    public void testCannotAccessAppBDataDir() throws Exception {
        ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(APPB_PKG,
                0);
        assertDirDoesNotExist(applicationInfo.dataDir);
        assertDirDoesNotExist(applicationInfo.deviceProtectedDataDir);
        assertDirDoesNotExist("/data/data/" + APPB_PKG);
        assertDirDoesNotExist("/data/misc/profiles/cur/0/" + APPB_PKG);
        assertDirIsNotAccessible("/data/misc/profiles/ref");
    }

    @Test
    public void testUnlockDevice() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_5);
        mDevice.waitForIdle();
        mDevice.pressEnter();
        mDevice.waitForIdle();
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    @Test
    public void testAppAUnlockDeviceAndVerifyCeDeExternalDataExist() throws Exception {

        final CountDownLatch unlocked = new CountDownLatch(1);
        final CountDownLatch bootCompleted = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()) {
                    case Intent.ACTION_USER_UNLOCKED:
                        unlocked.countDown();
                        break;
                    case Intent.ACTION_BOOT_COMPLETED:
                        bootCompleted.countDown();
                        break;
                }
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));

        testUnlockDevice();

        assertTrue("User not unlocked", unlocked.await(1, TimeUnit.MINUTES));
        assertTrue("No locked boot complete", bootCompleted.await(1, TimeUnit.MINUTES));

        // The test app process should be still running, make sure CE DE now is available
        testAppACeDataExists();
        testAppADeDataExists();
        testAppAExternalDataDoesExist();
        testAppACurProfileDataAccessible();
        testAppARefProfileDataNotAccessible();
    }
}
