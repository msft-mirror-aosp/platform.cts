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

package android.media.audio.cts;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host-side tests for {@link android.media.AudioService} that require rebooting the device.
 * Verifies that volume and mute states are persisted across a reboot.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AudioServiceRebootHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APK = "CtsAudioHostTestApp.apk";
    private static final String TEST_PKG = "android.media.audio.app";
    private static final String TEST_CLASS = TEST_PKG + ".AudioServiceRebootTest";

    // Device rebooting over tradefed isn't "safe", since it uses a shell reboot.
    // As such, give a wide margin to allow handlers to flush/pending work to complete
    // etc. to avoid flake.
    private static final int SETTINGS_WRITE_TIMEOUT_MS = 5_000;

    @Before
    public void setUp() throws Exception {
        installPackage(TEST_APK);
    }

    @After
    public void tearDown() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testPersistence_teardown");
        uninstallPackage(TEST_APK);
    }

    @Test
    public void testVolumePersists_AfterReboot() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testVolumePersistence_preReboot");
        waitForSettingsWrite();
        getDevice().reboot();
        runDeviceTests(TEST_PKG, TEST_CLASS, "testVolumePersistence_postReboot");
    }

    @Test
    public void testRingerModeImpliedMutePersistsAcrossReboot() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testRingerModeImpliedMute_preReboot");
        waitForSettingsWrite();
        getDevice().reboot();
        runDeviceTests(TEST_PKG, TEST_CLASS, "testRingerModeImpliedMute_postReboot");
    }

    private void waitForSettingsWrite() {
        RunUtil.getDefault().sleep(SETTINGS_WRITE_TIMEOUT_MS);
    }
}
