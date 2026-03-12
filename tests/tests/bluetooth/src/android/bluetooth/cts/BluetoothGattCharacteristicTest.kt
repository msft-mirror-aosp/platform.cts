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

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothGattCharacteristicTest {
    private val TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothGattCharacteristic: BluetoothGattCharacteristic

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(TestUtils.isBleSupported(context))
        bluetoothGattCharacteristic = BluetoothGattCharacteristic(TEST_UUID, 0x0A, 0x11)
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun getInstanceId() {
        assertThat(bluetoothGattCharacteristic.instanceId).isEqualTo(0)
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun getService() {
        // Service is null after initialization with public constructor
        assertThat(bluetoothGattCharacteristic.service).isNull()
        val service = BluetoothGattService(TEST_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        service.addCharacteristic(bluetoothGattCharacteristic)

        assertThat(bluetoothGattCharacteristic.service).isEqualTo(service)
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun setWriteType() {
        bluetoothGattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED

        assertThat(bluetoothGattCharacteristic.writeType)
            .isEqualTo(BluetoothGattCharacteristic.WRITE_TYPE_SIGNED)
    }
}
