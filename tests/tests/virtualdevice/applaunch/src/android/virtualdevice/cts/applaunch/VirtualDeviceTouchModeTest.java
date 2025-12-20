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

package android.virtualdevice.cts.applaunch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SuppressLint("MissingCheckFlagsRule") // TODO: b/463342925 - remove once fixed
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_TOUCH_MODE)
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceTouchModeTest {

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    @Before
    public void setUp() {
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @ApiTest(
            apis = {
                "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayInTouchMode"
            })
    @Test
    public void setDisplayInTouchMode_invalidDisplay_throws() {
        assertThrows(
                SecurityException.class,
                () -> mVirtualDevice.setDisplayInTouchMode(Display.INVALID_DISPLAY, false));
        assertThrows(
                SecurityException.class,
                () -> mVirtualDevice.setDisplayInTouchMode(Display.INVALID_DISPLAY, true));
    }

    @ApiTest(
            apis = {
                "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayInTouchMode"
            })
    @Test
    public void setDisplayInTouchMode_defaultDisplay_throws() {
        assertThrows(
                SecurityException.class,
                () -> mVirtualDevice.setDisplayInTouchMode(Display.DEFAULT_DISPLAY, false));
        assertThrows(
                SecurityException.class,
                () -> mVirtualDevice.setDisplayInTouchMode(Display.DEFAULT_DISPLAY, true));
    }

    @ApiTest(
            apis = {
                "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayInTouchMode"
            })
    @Test
    public void setDisplayInTouchMode_unownedDisplay_throws() {
        VirtualDisplay unownedDisplay = mRule.createManagedUnownedVirtualDisplay();
        assertThrows(
                SecurityException.class,
                () ->
                        mVirtualDevice.setDisplayInTouchMode(
                                unownedDisplay.getDisplay().getDisplayId(), false));
        assertThrows(
                SecurityException.class,
                () ->
                        mVirtualDevice.setDisplayInTouchMode(
                                unownedDisplay.getDisplay().getDisplayId(), true));
    }

    @ApiTest(
            apis = {
                "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayInTouchMode"
            })
    @Test
    public void setDisplayInTouchMode_untrustedDisplay_throws() {
        VirtualDisplay untrustedDisplay =
                mRule.createManagedVirtualDisplayWithFlags(
                        mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertThrows(
                SecurityException.class,
                () ->
                        mVirtualDevice.setDisplayInTouchMode(
                                untrustedDisplay.getDisplay().getDisplayId(), false));
        assertThrows(
                SecurityException.class,
                () ->
                        mVirtualDevice.setDisplayInTouchMode(
                                untrustedDisplay.getDisplay().getDisplayId(), true));
    }

    @ApiTest(
            apis = {
                "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayInTouchMode"
            })
    @Test
    public void setDisplayInTouchMode_setsDisplayInTouchMode() {
        VirtualDisplay virtualDisplay =
                mRule.createManagedVirtualDisplay(
                        mVirtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        Display display = virtualDisplay.getDisplay();
        int displayId = display.getDisplayId();
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        boolean defaultTouchMode = isInTouchMode(activity);

        mVirtualDevice.setDisplayInTouchMode(displayId, !defaultTouchMode);

        // Verify that the new touch mode gets applied on the virtual display
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(!defaultTouchMode, isInTouchMode(activity));
        // Verify that the touch mode remains unaffected on the default display
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(defaultTouchMode, isInTouchMode(activity));

        mVirtualDevice.setDisplayInTouchMode(displayId, defaultTouchMode);

        // Verify that the touch mode again changes on the virtual display
        assertEquals(defaultTouchMode, isInTouchMode(activity));
    }

    private static boolean isInTouchMode(@NonNull Activity activity) {
        return activity.getWindow().getDecorView().isInTouchMode();
    }
}
