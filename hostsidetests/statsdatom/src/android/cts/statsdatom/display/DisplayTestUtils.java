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

package android.cts.statsdatom.display;

import com.android.tradefed.device.ITestDevice;

import java.util.concurrent.TimeUnit;

public class DisplayTestUtils {

    public static final String DISPLAY_TEST_PKG = "android.display.cts";
    public static final String DISPLAY_TEST_APK = "CtsDisplayTestCases.apk";
    public static final String TEST_CLASS_DISPLAY_EVENT = "android.display.cts.DisplayEventTest";
    public static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    /** Returns the current brightness level of the given device. */
    public static int getCurrentBrightnessLevel(ITestDevice device) throws Exception {
        return Integer.parseInt(
                device.executeShellCommand("settings get system screen_brightness").trim());
    }

    /** Sets the brightness level of the given device. */
    public static void setScreenBrightnessLevel(ITestDevice device, int newBrightness)
            throws Exception {
        device.executeShellCommand("settings put system screen_brightness " + newBrightness);
    }

    /** Returns the current brightness mode of the given device. */
    public static int getCurrentBrightnessMode(ITestDevice device) throws Exception {
        return Integer.parseInt(
                device.executeShellCommand("settings get system screen_brightness_mode").trim());
    }

    /** Sets the brightness mode of the given device. */
    public static void setAutoBrightnessMode(ITestDevice device, int mode) throws Exception {
        device.executeShellCommand("settings put system screen_brightness_mode " + mode);
    }

    /** Gets the user rotation mode of the given device. */
    public static int getCurrentUserRotationMode(ITestDevice device) throws Exception {
        return Integer.parseInt(
                device.executeShellCommand("settings get system user_rotation").trim());
    }

    /** Sets the user rotation mode of the given device. */
    public static void setUserRotationMode(ITestDevice device, int mode) throws Exception {
        device.executeShellCommand("settings put system user_rotation " + mode);
    }

    /** Gets the accelerometer rotation mode of the given device. */
    public static int getCurrentAccelerometerRotationMode(ITestDevice device) throws Exception {
        return Integer.parseInt(
                device.executeShellCommand("settings get system accelerometer_rotation").trim());
    }

    /** Sets the accelerometer rotation mode of the given device. */
    public static void setAccelerometerRotationMode(ITestDevice device, int mode) throws Exception {
        device.executeShellCommand("settings put system accelerometer_rotation " + mode);
    }
}
