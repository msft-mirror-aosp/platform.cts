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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;

import static org.junit.Assert.assertThrows;

import android.companion.AssociationRequest;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests to verify the custom policies of SENSOR_DEVICE_STREAMING role. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIMITED_VDM_ROLE)
public class SensorDeviceStreamingBehaviorTest {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withDeviceProfile(
            AssociationRequest.DEVICE_PROFILE_SENSOR_DEVICE_STREAMING);

    private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() throws Exception {
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @Test
    public void createVirtualDisplay_public_throwsException() {
        // DisplayManager creates an auto-mirror display by default for public virtual displays.
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC));
    }

    @Test
    public void createVirtualDisplay_autoMirror_throwsException() {
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    @Test
    public void createVirtualDisplay_publicAutoMirror_throwsException() {
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    @Test
    public void createVirtualDisplay_trusted_throwsException() {
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplay(mVirtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()));
    }

    @Test
    public void createVirtualDevice_withCustomClipboardPolicy_throwsException() {
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDevice(new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM)
                        .build()));
    }

    @Test
    public void dynamicallySetCustomClipboardPolicy_throwsException() {
        assertThrows(SecurityException.class,
                () -> mVirtualDevice.setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM));
    }

    @Test
    public void createVirtualDevice_alwaysUnlocked_throwsException() {
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDevice(new VirtualDeviceParams.Builder()
                        .setLockState(LOCK_STATE_ALWAYS_UNLOCKED)
                        .build()));
    }
}
