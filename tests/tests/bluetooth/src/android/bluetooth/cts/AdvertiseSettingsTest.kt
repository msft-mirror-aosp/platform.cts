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

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetParameters
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [AdvertiseSettings]. */
@RunWith(AndroidJUnit4::class)
class AdvertiseSettingsTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(TestUtils.isBleSupported(context))
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun defaultSettings() {
        val settings = AdvertiseSettings.Builder().build()
        assertThat(settings.mode).isEqualTo(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        assertThat(settings.txPowerLevel).isEqualTo(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        assertThat(settings.timeout).isEqualTo(0)
        assertThat(settings.isConnectable).isTrue()
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun describeContents() {
        val settings = AdvertiseSettings.Builder().build()
        assertThat(settings.describeContents()).isEqualTo(0)
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun readWriteParcel() {
        val timeoutMillis = 60 * 1000
        val parcel = Parcel.obtain()
        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setDiscoverable(false)
                .setTimeout(timeoutMillis)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT)
                .build()
        settings.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val settingsFromParcel = AdvertiseSettings.CREATOR.createFromParcel(parcel)
        assertThat(settingsFromParcel.mode).isEqualTo(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        assertThat(settingsFromParcel.txPowerLevel)
            .isEqualTo(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        assertThat(settingsFromParcel.timeout).isEqualTo(timeoutMillis)
        assertThat(settings.isConnectable).isFalse()
        assertThat(settings.isDiscoverable).isFalse()
        assertThat(settings.ownAddressType).isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT)
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun illegalTimeout() {
        val builder = AdvertiseSettings.Builder()
        builder.setTimeout(0).build()
        builder.setTimeout(180 * 1000).build()
        // Maximum timeout is 3 minutes.
        assertThrows(IllegalArgumentException::class.java) {
            builder.setTimeout(180 * 1000 + 1).build()
        }
        // Negative time out is not allowed.
        assertThrows(IllegalArgumentException::class.java) { builder.setTimeout(-1).build() }
    }
}
