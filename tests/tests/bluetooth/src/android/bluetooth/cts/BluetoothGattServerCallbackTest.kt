/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.GattOffloadSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bluetooth.flags.Flags
import com.android.compatibility.common.util.CddTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

@RunWith(AndroidJUnit4::class)
class BluetoothGattServerCallbackTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var device: BluetoothDevice
    @Mock private lateinit var callbacks: BluetoothGattServerCallback
    @Mock private lateinit var gattOffloadSession: GattOffloadSession
    @Mock private lateinit var bluetoothGattService: BluetoothGattService
    @Mock private lateinit var bluetoothGattDescriptor: BluetoothGattDescriptor
    @Mock private lateinit var bluetoothGattCharacteristic: BluetoothGattCharacteristic

    private val context = InstrumentationRegistry.getInstrumentation().context

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Suppress("DirectInvocationOnMock")
    fun fakeCallbackCoverage() {
        assumeTrue(TestUtils.isBleSupported(context))
        callbacks.onConnectionStateChange(device, STATE_CONNECTED, STATE_CONNECTED)
        callbacks.onServiceAdded(GATT_SUCCESS, bluetoothGattService)
        callbacks.onCharacteristicReadRequest(device, 0, 0, bluetoothGattCharacteristic)
        callbacks.onCharacteristicWriteRequest(
            device,
            0,
            bluetoothGattCharacteristic,
            true,
            true,
            0,
            BYTES,
        )
        callbacks.onDescriptorReadRequest(device, 0, 0, bluetoothGattDescriptor)
        callbacks.onDescriptorWriteRequest(device, 0, bluetoothGattDescriptor, true, true, 0, BYTES)
        callbacks.onExecuteWrite(device, 0, true)
        callbacks.onNotificationSent(device, GATT_SUCCESS)
        callbacks.onMtuChanged(device, 0)
        callbacks.onPhyUpdate(
            device,
            BluetoothDevice.PHY_LE_2M,
            BluetoothDevice.PHY_LE_2M,
            GATT_SUCCESS,
        )
        callbacks.onPhyRead(
            device,
            BluetoothDevice.PHY_LE_2M,
            BluetoothDevice.PHY_LE_2M,
            GATT_SUCCESS,
        )

        callbacks.onSubrateChange(device, BluetoothGatt.SUBRATE_MODE_OFF, GATT_SUCCESS)
        callbacks.onSubrateChange(device, BluetoothGatt.SUBRATE_MODE_LOW, GATT_SUCCESS)
        callbacks.onSubrateChange(device, BluetoothGatt.SUBRATE_MODE_BALANCED, GATT_SUCCESS)
        callbacks.onSubrateChange(device, BluetoothGatt.SUBRATE_MODE_HIGH, GATT_SUCCESS)

        if (Flags.gattOffloadApi()) {
            callbacks.onCharacteristicsOffloaded(
                device,
                gattOffloadSession,
                GattOffloadSession.STATUS_SUCCESS,
            )

            callbacks.onCharacteristicsUnoffloaded(
                device,
                TEST_SESSION_ID,
                GattOffloadSession.STATUS_SUCCESS,
            )
        }
    }

    companion object {
        private val BYTES = byteArrayOf()
        private const val TEST_SESSION_ID = 1
    }
}
