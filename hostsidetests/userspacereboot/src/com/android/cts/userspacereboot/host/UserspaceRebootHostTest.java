/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.userspacereboot.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/**
 * Host side CTS tests verifying userspace reboot functionality.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class UserspaceRebootHostTest extends BaseHostJUnit4Test  {

    private static final String USERSPACE_REBOOT_SUPPORTED_PROP =
            "ro.init.userspace_reboot.is_supported";

    private static final String BASIC_TEST_APP_APK = "BasicUserspaceRebootTestApp.apk";
    private static final String BASIC_TEST_APP_PACKAGE_NAME =
            "com.android.cts.userspacereboot.basic";

    private static final String BOOT_COMPLETED_TEST_APP_APK =
            "BootCompletedUserspaceRebootTestApp.apk";
    private static final String BOOT_COMPLETED_TEST_APP_PACKAGE_NAME =
            "com.android.cts.userspacereboot.bootcompleted";

    private void runDeviceTest(String pkgName, String className, String testName) throws Exception {
        assertThat(runDeviceTests(pkgName, pkgName + "." + className, testName)).isTrue();
    }

    private void installApk(String apkFileName) throws Exception {
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(getBuild());
        getDevice().installPackage(helper.getTestFile(apkFileName), false, true);
    }

    @After
    public void cleanUp() throws Exception {
        getDevice().uninstallPackage(BASIC_TEST_APP_PACKAGE_NAME);
        getDevice().uninstallPackage(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME);
    }

    /**
     * Asserts that only file-based encrypted devices can support userspace reboot.
     */
    @Test
    public void testOnlyFbeDevicesSupportUserspaceReboot() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        assertThat(getDevice().getProperty("ro.crypto.state")).isEqualTo("encrypted");
        assertThat(getDevice().getProperty("ro.crypto.type")).isEqualTo("file");
    }

    /**
     * Tests that on devices supporting userspace reboot {@code
     * PowerManager.isRebootingUserspaceSupported()} returns {@code true}.
     */
    @Test
    public void testDeviceSupportsUserspaceReboot() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        installApk(BASIC_TEST_APP_APK);
        runDeviceTest(BASIC_TEST_APP_PACKAGE_NAME, "BasicUserspaceRebootTest",
                "testUserspaceRebootIsSupported");
    }

    /**
     * Tests that on devices not supporting userspace reboot {@code
     * PowerManager.isRebootingUserspaceSupported()} returns {@code false}.
     */
    @Test
    public void testDeviceDoesNotSupportUserspaceReboot() throws Exception {
        assumeFalse("Userspace reboot supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        installApk(BASIC_TEST_APP_APK);
        // Also verify that PowerManager.isRebootingUserspaceSupported will return true
        runDeviceTest(BASIC_TEST_APP_PACKAGE_NAME, "BasicUserspaceRebootTest",
                "testUserspaceRebootIsNotSupported");
    }

    /**
     * Tests that userspace reboot succeeds and doesn't fall back to full reboot.
     */
    @Test
    public void testUserspaceReboot() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        rebootUserspaceAndWaitForBootComplete();
        assertUserspaceRebootSucceed();
    }

    /**
     * Tests that userspace reboot with fs-checkpointing succeeds and doesn't fall back to full
     * reboot.
     */
    @Test
    public void testUserspaceRebootWithCheckpoint() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        assumeTrue("Device doesn't support fs checkpointing", isFsCheckpointingSupported());
        CommandResult result = getDevice().executeShellV2Command("sm start-checkpoint 1");
        Thread.sleep(500);
        assertWithMessage("Failed to start checkpoint : %s", result.getStderr()).that(
                result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        rebootUserspaceAndWaitForBootComplete();
        assertUserspaceRebootSucceed();
    }

    /**
     * Tests that CE storage is unlocked after userspace reboot.
     */
    @Test
    public void testUserspaceReboot_verifyCeStorageIsUnlocked() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        try {
            getDevice().executeShellV2Command("cmd lock_settings set-pin 1543");
            installApk(BOOT_COMPLETED_TEST_APP_APK);
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "prepareFile");
            rebootUserspaceAndWaitForBootComplete();
            assertUserspaceRebootSucceed();
            // Sleep for 30s to make sure that system_server has sent out BOOT_COMPLETED broadcast.
            Thread.sleep(Duration.ofSeconds(30).toMillis());
            getDevice().executeShellV2Command("am wait-for-broadcast-idle");
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "testVerifyCeStorageUnlocked");
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "testVerifyReceivedBootCompletedBroadcast");
        } finally {
            getDevice().executeShellV2Command("cmd lock_settings clear --old 1543");
        }
    }

    /**
     * Tests that CE storage is unlocked after userspace reboot with fs-checkpointing.
     */
    @Test
    public void testUserspaceRebootWithCheckpoint_verifyCeStorageIsUnlocked() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        assumeTrue("Device doesn't support fs checkpointing", isFsCheckpointingSupported());
        try {
            CommandResult result = getDevice().executeShellV2Command("sm start-checkpoint 1");
            Thread.sleep(500);
            assertWithMessage("Failed to start checkpoint : %s", result.getStderr()).that(
                    result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
            rebootUserspaceAndWaitForBootComplete();
            getDevice().executeShellV2Command("cmd lock_settings set-pin 1543");
            installApk(BOOT_COMPLETED_TEST_APP_APK);
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "prepareFile");
            rebootUserspaceAndWaitForBootComplete();
            assertUserspaceRebootSucceed();
            // Sleep for 30s to make sure that system_server has sent out BOOT_COMPLETED broadcast.
            Thread.sleep(Duration.ofSeconds(30).toMillis());
            getDevice().executeShellV2Command("am wait-for-broadcast-idle");
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "testVerifyCeStorageUnlocked");
            runDeviceTest(BOOT_COMPLETED_TEST_APP_PACKAGE_NAME, "BootCompletedUserspaceRebootTest",
                    "testVerifyReceivedBootCompletedBroadcast");
        } finally {
            getDevice().executeShellV2Command("cmd lock_settings clear --old 1543");
        }
    }

    // TODO(b/135984674): add test case that forces unmount of f2fs userdata.

    /**
     * Returns {@code true} if device supports fs-checkpointing.
     */
    private boolean isFsCheckpointingSupported() throws Exception {
        CommandResult result = getDevice().executeShellV2Command("sm supports-checkpoint");
        assertWithMessage("Failed to check if fs checkpointing is supported : %s",
                result.getStderr()).that(result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        return "true".equals(result.getStdout().trim());
    }

    /**
     * Reboots a device and waits for the boot to complete.
     *
     * <p>Before rebooting, sets a value of sysprop {@code test.userspace_reboot.requested} to 1.
     * Querying this property is then used in {@link #assertUserspaceRebootSucceed()} to assert that
     * userspace reboot succeeded.
     */
    private void rebootUserspaceAndWaitForBootComplete() throws Exception {
        setProperty("test.userspace_reboot.requested", "1");
        getDevice().rebootUserspaceUntilOnline();
        assertWithMessage("Device did not boot withing 2 minutes").that(
                getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
    }

    /**
     * Asserts that userspace reboot succeeded by querying the value of {@code
     * test.userspace_reboot.requested} property.
     */
    private void assertUserspaceRebootSucceed() throws Exception {
        // If userspace reboot fails and fallback to hard reboot is triggered then
        // test.userspace_reboot.requested won't be set.
        final String bootReason = getDevice().getProperty("sys.boot.reason.last");
        final boolean result = getDevice().getBooleanProperty("test.userspace_reboot.requested",
                false);
        assertWithMessage(
                "Userspace reboot failed and fallback to full reboot was triggered. Boot reason: "
                        + "%s", bootReason).that(result).isTrue();
    }

    /**
     * A wrapper over {@code adb shell setprop name value}.
     *
     * This is a temporary workaround until issues with {@code getDevice().setProperty()} API are
     * resolved.
     */
    private void setProperty(String name, String value) throws Exception {
        final String cmd = String.format("\"setprop %s %s\"", name, value);
        final CommandResult result = getDevice().executeShellV2Command(cmd);
        assertWithMessage("Failed to call adb shell %s: %s", cmd, result.getStderr())
            .that(result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
    }
}
