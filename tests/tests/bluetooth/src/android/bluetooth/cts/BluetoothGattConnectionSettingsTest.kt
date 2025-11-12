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
package android.bluetooth.cts

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bluetooth.flags.Flags
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

/** Test for Bluetooth Socket Settings [BluetoothGattConnectionSettingsTest]. */
@SmallTest
class BluetoothGattConnectionSettingsTest {
    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @JvmField @Rule val expect = Expect.create()
    @JvmField @Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var executor: Executor
    @Mock private lateinit var callback: BluetoothGattCallback

    @Before
    fun setUp() {
        assumeTrue(TestUtils.isBleSupported(context))
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GATT_CONN_SETTINGS)
    fun validateBuilder() {
        val settings =
            BluetoothGattConnectionSettings.Builder(executor, callback)
                .setTransport(BluetoothDevice.TRANSPORT_AUTO)
                .setAutoConnectEnabled(true)
                .setOpportunisticEnabled(true)
                .setAutomaticMtuEnabled(false)
                .build()
        expect.that(settings.getTransport()).isEqualTo(BluetoothDevice.TRANSPORT_AUTO)
        expect.that(settings.isAutoConnectEnabled()).isTrue()
        expect.that(settings.isOpportunisticEnabled()).isTrue()
        expect.that(settings.getBluetoothGattCallback()).isEqualTo(callback)
        expect.that(settings.isAutomaticMtuEnabled()).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GATT_CONN_SETTINGS)
    fun validateDefaultBuilderValue() {
        val settings = BluetoothGattConnectionSettings.Builder(executor, callback).build()

        expect.that(settings.getTransport()).isEqualTo(BluetoothDevice.TRANSPORT_LE)
        expect.that(settings.isAutoConnectEnabled()).isFalse()
        expect.that(settings.isOpportunisticEnabled()).isFalse()
        expect.that(settings.getBluetoothGattCallback()).isEqualTo(callback)
        expect.that(settings.isAutomaticMtuEnabled()).isTrue()
    }

    companion object {
        private val context = InstrumentationRegistry.getInstrumentation().getContext()
        private val device = BlockingBluetoothAdapter.adapter.getRemoteDevice("00:11:22:AA:BB:CC")
    }
}
