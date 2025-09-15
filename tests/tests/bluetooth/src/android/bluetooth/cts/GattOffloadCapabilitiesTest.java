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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.GattOffloadCapabilities;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GattOffloadCapabilitiesTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final BluetoothAdapter mAdapter =
            mContext.getSystemService(BluetoothManager.class).getAdapter();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_GATT_OFFLOAD_API)
    @Test
    public void getSupportedCapabilities() {
        assertThrows(SecurityException.class, () -> mAdapter.getSupportedGattOffloadCapabilities());

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            GattOffloadCapabilities capabilities = mAdapter.getSupportedGattOffloadCapabilities();
            assertThat(capabilities).isNotNull();
            if (capabilities.isClientOffloadSupported()) {
                assertThat(capabilities.getSupportedClientProperties()).isNotEqualTo(0);

            } else {
                assertThat(capabilities.getSupportedClientProperties()).isEqualTo(0);
            }
            if (capabilities.isServerOffloadSupported()) {
                assertThat(capabilities.getSupportedServerProperties()).isNotEqualTo(0);

            } else {
                assertThat(capabilities.getSupportedServerProperties()).isEqualTo(0);
            }
        }
    }
}
