/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothClass;
import android.os.Parcel;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

/**
 * Unit test cases for {@link BluetoothClass}.
 *
 * <p>To run this test, use adb shell am instrument -e class 'android.bluetooth.BluetoothClassTest'
 * -w 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
public class BluetoothClassTest extends AndroidTestCase {

    private BluetoothClass mBluetoothClassHeadphones;
    private BluetoothClass mBluetoothClassPhone;
    private BluetoothClass mBluetoothClassService;

    private BluetoothClass createBtClass(int deviceClass) {
        Parcel p = Parcel.obtain();
        p.writeInt(deviceClass);
        p.setDataPosition(0); // reset position of parcel before passing to constructor

        BluetoothClass bluetoothClass = BluetoothClass.CREATOR.createFromParcel(p);
        p.recycle();
        return bluetoothClass;
    }

    @Override
    protected void setUp() {
        mBluetoothClassHeadphones = createBtClass(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        mBluetoothClassPhone = createBtClass(BluetoothClass.Device.Major.PHONE);
        mBluetoothClassService = createBtClass(BluetoothClass.Service.NETWORKING);
    }

    @SmallTest
    public void testHasService() {
        assertThat(mBluetoothClassService.hasService(BluetoothClass.Service.NETWORKING)).isTrue();
        assertThat(mBluetoothClassService.hasService(BluetoothClass.Service.TELEPHONY)).isFalse();
    }

    @SmallTest
    public void testGetMajorDeviceClass() {
        assertThat(mBluetoothClassHeadphones.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.AUDIO_VIDEO);
        assertThat(mBluetoothClassPhone.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.PHONE);
    }

    @SmallTest
    public void testGetDeviceClass() {
        assertThat(mBluetoothClassHeadphones.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        assertThat(mBluetoothClassPhone.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.PHONE_UNCATEGORIZED);
    }

    @SmallTest
    public void testGetClassOfDevice() {
        assertThat(mBluetoothClassHeadphones.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        assertThat(mBluetoothClassPhone.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.PHONE);
    }

    @SmallTest
    public void testDoesClassMatch() {
        assertThat(mBluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_A2DP)).isTrue();
        assertThat(mBluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_HEADSET))
                .isFalse();

        assertThat(mBluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_OPP)).isTrue();
        assertThat(mBluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_HEADSET)).isFalse();

        assertThat(mBluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_PANU)).isTrue();
        assertThat(mBluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_OPP)).isFalse();
    }

    @SmallTest
    public void testInnerClasses() {
        // Just instantiate static inner classes for exposing constants
        // to make test coverage tool happy.
        BluetoothClass.Device device = new BluetoothClass.Device();
        BluetoothClass.Device.Major major = new BluetoothClass.Device.Major();
        BluetoothClass.Service service = new BluetoothClass.Service();
    }
}
