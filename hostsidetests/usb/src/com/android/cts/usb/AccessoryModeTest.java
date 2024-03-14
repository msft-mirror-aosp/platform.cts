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

package com.android.cts.usb;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresDevice;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.helper.aoa.AoaDevice;
import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AccessoryModeTest extends BaseHostJUnit4Test {

    private static final String TAG = AccessoryModeTest.class.getSimpleName();

    private static final Duration ACCESSORY_SWITCH_TIMEOUT = Duration.ofSeconds(5);

    @Test
    @RequiresDevice
    public void testSwitchToAccessoryModeFromOtherUsbModes() throws DeviceNotAvailableException {
        String[] functions = {"mtp", "ptp", "rndis", "midi", "ncm", "uvc", ""};
        for (String function: functions) {
            Log.logAndDisplay(
                    LogLevel.INFO,
                    TAG,
                    "Testing accessory switch from " + function + " mode"
            );
            testSwitchToAccessoryModeFrom(function);
        }

    }

    private void testSwitchToAccessoryModeFrom(String initialFunction)
            throws DeviceNotAvailableException {
        setUsbFunction(initialFunction);
        assertTrue("Device is not available", getDevice().waitForDeviceAvailable());

        String currentFunction = getCurrentUsbFunction();
        if (!currentFunction.equals(initialFunction)) {
            Log.logAndDisplay(
                    LogLevel.INFO,
                    TAG,
                    String.format(
                        "Function %s is not supported, skipping the test for this mode",
                        initialFunction
                    )
            );
            return;
        }

        try (UsbHelper usbHelper = new UsbHelper()) {
            AoaDevice aoaDevice =
                    usbHelper.getAoaDevice(getDevice().getSerialNumber(), ACCESSORY_SWITCH_TIMEOUT);
            assertNotNull("Failed to switch device to accessory mode", aoaDevice);
        }

        // Cleanup, set usb mode to charging.
        setUsbFunction("");
    }

    private void setUsbFunction(String functionName) throws DeviceNotAvailableException {
        Log.logAndDisplay(LogLevel.INFO, TAG, "Switching device usb mode to " + functionName);
        getDevice().executeShellCommand(String.format("svc usb setFunctions %s", functionName));
    }

    private String getCurrentUsbFunction()  throws DeviceNotAvailableException {
        String currentFunction = getDevice().executeShellCommand("svc usb getFunctions").trim();
        Log.logAndDisplay(LogLevel.INFO, TAG, "Current device usb mode : " + currentFunction);

        return currentFunction;
    }
}
