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

import android.bluetooth.BluetoothHidDeviceAppQosSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothHidDeviceAppQosSettingsTest {

    private val TEST_SERVICE_TYPE = BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT
    private val TEST_TOKEN_RATE = 800
    private val TEST_TOKEN_BUCKET_SIZE = 9
    private val TEST_PEAK_BANDWIDTH = 10
    private val TEST_LATENCY = 11250
    private val TEST_DELAY_VARIATION = BluetoothHidDeviceAppQosSettings.MAX

    @Test
    fun allMethods() {
        val bluetoothHidDeviceAppQosSettings =
            BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                TEST_TOKEN_RATE,
                TEST_TOKEN_BUCKET_SIZE,
                TEST_PEAK_BANDWIDTH,
                TEST_LATENCY,
                TEST_DELAY_VARIATION,
            )
        assertThat(bluetoothHidDeviceAppQosSettings.serviceType).isEqualTo(TEST_SERVICE_TYPE)
        assertThat(bluetoothHidDeviceAppQosSettings.latency).isEqualTo(TEST_LATENCY)
        assertThat(bluetoothHidDeviceAppQosSettings.tokenRate).isEqualTo(TEST_TOKEN_RATE)
        assertThat(bluetoothHidDeviceAppQosSettings.peakBandwidth).isEqualTo(TEST_PEAK_BANDWIDTH)
        assertThat(bluetoothHidDeviceAppQosSettings.delayVariation).isEqualTo(TEST_DELAY_VARIATION)
        assertThat(bluetoothHidDeviceAppQosSettings.tokenBucketSize)
            .isEqualTo(TEST_TOKEN_BUCKET_SIZE)
    }
}
