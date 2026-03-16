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

import android.bluetooth.BluetoothClass
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test cases for [BluetoothClass]. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothClassTest {

    private lateinit var bluetoothClassHeadphones: BluetoothClass
    private lateinit var bluetoothClassPhone: BluetoothClass
    private lateinit var bluetoothClassService: BluetoothClass

    private fun createBtClass(deviceClass: Int): BluetoothClass {
        val p = Parcel.obtain()
        p.writeInt(deviceClass)
        p.setDataPosition(0) // reset position of parcel before passing to constructor

        val bluetoothClass = BluetoothClass.CREATOR.createFromParcel(p)
        p.recycle()
        return bluetoothClass
    }

    @Before
    fun setUp() {
        bluetoothClassHeadphones = createBtClass(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
        bluetoothClassPhone = createBtClass(BluetoothClass.Device.Major.PHONE)
        bluetoothClassService = createBtClass(BluetoothClass.Service.NETWORKING)
    }

    @Test
    fun hasService() {
        assertThat(bluetoothClassService.hasService(BluetoothClass.Service.NETWORKING)).isTrue()
        assertThat(bluetoothClassService.hasService(BluetoothClass.Service.TELEPHONY)).isFalse()
    }

    @Test
    fun getMajorDeviceClass() {
        assertThat(bluetoothClassHeadphones.majorDeviceClass)
            .isEqualTo(BluetoothClass.Device.Major.AUDIO_VIDEO)
        assertThat(bluetoothClassPhone.majorDeviceClass)
            .isEqualTo(BluetoothClass.Device.Major.PHONE)
    }

    @Test
    fun getDeviceClass() {
        assertThat(bluetoothClassHeadphones.deviceClass)
            .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
        assertThat(bluetoothClassPhone.deviceClass)
            .isEqualTo(BluetoothClass.Device.PHONE_UNCATEGORIZED)
    }

    @Test
    fun getClassOfDevice() {
        assertThat(bluetoothClassHeadphones.deviceClass)
            .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
        assertThat(bluetoothClassPhone.majorDeviceClass)
            .isEqualTo(BluetoothClass.Device.Major.PHONE)
    }

    @Test
    fun doesClassMatch() {
        assertThat(bluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_A2DP)).isTrue()
        assertThat(bluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_HEADSET))
            .isFalse()

        assertThat(bluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_OPP)).isTrue()
        assertThat(bluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_HEADSET)).isFalse()

        assertThat(bluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_PANU)).isTrue()
        assertThat(bluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_OPP)).isFalse()
    }

    @Test
    fun innerClasses() {
        // Just instantiate static inner classes for exposing constants
        // to make test coverage tool happy.
        BluetoothClass.Device()
        BluetoothClass.Device.Major()
        BluetoothClass.Service()
    }
}
