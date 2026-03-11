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
class BluetoothGattServiceTest {

    private val TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb")

    private lateinit var bluetoothGattService: BluetoothGattService

    @Before
    fun setUp() {
        Assume.assumeTrue(
            TestUtils.isBleSupported(InstrumentationRegistry.getInstrumentation().targetContext)
        )

        bluetoothGattService =
            BluetoothGattService(TEST_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun getInstanceId() {
        assertThat(bluetoothGattService.instanceId).isEqualTo(0)
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun getType() {
        assertThat(bluetoothGattService.type).isEqualTo(BluetoothGattService.SERVICE_TYPE_PRIMARY)
    }
}
