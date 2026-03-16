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
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.GattOffloadSession
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bluetooth.flags.Flags
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

@RunWith(AndroidJUnit4::class)
class BluetoothGattCallbackTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock private lateinit var callbacks: BluetoothGattCallback
    @Mock private lateinit var bluetoothGattDescriptor: BluetoothGattDescriptor
    @Mock private lateinit var bluetoothGattCharacteristic: BluetoothGattCharacteristic
    @Mock private lateinit var gattOffloadSession: GattOffloadSession
    @Mock private lateinit var bluetoothGatt: BluetoothGatt

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val bytes = byteArrayOf()

    @Before
    fun setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(context))
    }

    @Test
    fun fakeCallbackCoverage() {
        callbacks.onPhyUpdate(
            bluetoothGatt,
            BluetoothDevice.PHY_LE_2M,
            BluetoothDevice.PHY_LE_2M,
            BluetoothGatt.GATT_SUCCESS,
        )
        callbacks.onPhyRead(
            bluetoothGatt,
            BluetoothDevice.PHY_LE_2M,
            BluetoothDevice.PHY_LE_2M,
            BluetoothGatt.GATT_SUCCESS,
        )
        callbacks.onConnectionStateChange(
            bluetoothGatt,
            BluetoothGatt.GATT_SUCCESS,
            STATE_CONNECTED,
        )
        callbacks.onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_SUCCESS)
        callbacks.onCharacteristicRead(
            bluetoothGatt,
            bluetoothGattCharacteristic,
            bytes,
            BluetoothGatt.GATT_SUCCESS,
        )
        callbacks.onCharacteristicWrite(
            bluetoothGatt,
            bluetoothGattCharacteristic,
            BluetoothGatt.GATT_SUCCESS,
        )
        callbacks.onCharacteristicChanged(bluetoothGatt, bluetoothGattCharacteristic, bytes)
        callbacks.onDescriptorRead(
            bluetoothGatt,
            bluetoothGattDescriptor,
            BluetoothGatt.GATT_SUCCESS,
            bytes,
        )
        callbacks.onDescriptorWrite(
            bluetoothGatt,
            bluetoothGattDescriptor,
            BluetoothGatt.GATT_SUCCESS,
        )
        callbacks.onReliableWriteCompleted(bluetoothGatt, BluetoothGatt.GATT_SUCCESS)
        callbacks.onReadRemoteRssi(bluetoothGatt, 0, BluetoothGatt.GATT_SUCCESS)
        callbacks.onMtuChanged(bluetoothGatt, 0, BluetoothGatt.GATT_SUCCESS)
        callbacks.onServiceChanged(bluetoothGatt)

        callbacks.onSubrateChange(
            bluetoothGatt,
            BluetoothGatt.SUBRATE_MODE_OFF,
            BluetoothStatusCodes.SUCCESS,
        )

        if (Flags.gattOffloadApi()) {
            callbacks.onCharacteristicsOffloaded(
                bluetoothGatt,
                gattOffloadSession,
                GattOffloadSession.STATUS_SUCCESS,
            )
        }

        if (Flags.gattOffloadApi()) {
            callbacks.onCharacteristicsUnoffloaded(
                bluetoothGatt,
                TEST_SESSION_ID,
                GattOffloadSession.STATUS_SUCCESS,
            )
        }
    }

    companion object {
        private const val TEST_SESSION_ID = 1
    }
}
