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

package android.virtualdevice.cts.core;

import static org.junit.Assert.assertThrows;

import android.companion.AssociationRequest;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests to verify the custom policies of NEARBY_DEVICE_STREAMING role. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class NearbyDeviceStreamingBehaviorTest {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withDeviceProfile(
            AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING);

    private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() throws Exception {
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with just
     * VIRTUAL_DISPLAY_FLAG_PUBLIC flag, as DisplayManagerService creates an auto-mirror
     * display by default for public virtual displays.
     */
    @Test
    public void createVirtualDisplay_public_throwsException() {
        // Try creating public display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC));
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flag.
     */
    @Test
    public void createVirtualDisplay_autoMirror_throwsException() {
        // Try creating auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_PUBLIC or VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flags.
     */
    @Test
    public void createVirtualDisplay_publicAutoMirror_throwsException() {
        // Try creating public auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }
}
