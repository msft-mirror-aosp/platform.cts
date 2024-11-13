/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static com.android.input.flags.Flags.FLAG_INCLUDE_RELATIVE_AXIS_VALUES_FOR_CAPTURED_TOUCHPADS;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeFalse;

import android.hardware.cts.R;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.cts.kernelinfo.KernelInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SonyDualshock4BluetoothTest extends InputHidTestCase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    // Simulates the behavior of PlayStation DualShock4 gamepad (model CUH-ZCT1U)
    public SonyDualshock4BluetoothTest() {
        super(R.raw.sony_dualshock4_bluetooth_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.sony_dualshock4_bluetooth_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        assumeFalse("b/337286136 - Broken since kernel 6.2 from driver changes",
                KernelInfo.isKernelVersionGreaterThan("6.2"));
        testInputEvents(R.raw.sony_dualshock4_bluetooth_motioneventtests);
    }

    @Test
    public void testVibrator() throws Exception {
        assumeFalse("b/337286136 - Broken since kernel 6.2 from driver changes",
                KernelInfo.isKernelVersionGreaterThan("6.2"));
        assumeTrue(KernelInfo.isKernelVersionGreaterThan("4.19"));
        testInputVibratorEvents(R.raw.sony_dualshock4_bluetooth_vibratortests);
    }

    @Test
    public void testBattery() {
        testInputBatteryEvents(R.raw.sony_dualshock4_bluetooth_batteryeventtests);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_INCLUDE_RELATIVE_AXIS_VALUES_FOR_CAPTURED_TOUCHPADS)
    public void testAllTouch() throws Throwable {
        assumeFalse("b/337286136 - Broken since kernel 6.2 from driver changes",
                KernelInfo.isKernelVersionGreaterThan("6.2"));
        try (PointerCaptureSession session = new PointerCaptureSession()) {
            testInputEvents(R.raw.sony_dualshock4_toucheventtests);
        }
    }

    @Test
    public void testLights() throws Exception {
        testInputLightsManager(R.raw.sony_dualshock4_bluetooth_lighttests);
    }
}
