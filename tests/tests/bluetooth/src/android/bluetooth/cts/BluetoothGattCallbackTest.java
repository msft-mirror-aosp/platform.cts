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

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.GattOffloadSession;
import android.content.Context;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class BluetoothGattCallbackTest {
    private static final String TAG = BluetoothGattCallbackTest.class.getSimpleName();
    private static final int TEST_SESSION_ID = 1;
    private final byte[] mBytes = new byte[] {};
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private BluetoothGatt mBluetoothGatt;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private BluetoothGattCallback mCallbacks;
    @Mock private BluetoothGattDescriptor mBluetoothGattDescriptor;
    @Mock private BluetoothGattCharacteristic mBluetoothGattCharacteristic;
    @Mock private GattOffloadSession mGattOffloadSession;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        mCallbacks.onPhyUpdate(
                mBluetoothGatt,
                BluetoothDevice.PHY_LE_2M,
                BluetoothDevice.PHY_LE_2M,
                BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onPhyRead(
                mBluetoothGatt,
                BluetoothDevice.PHY_LE_2M,
                BluetoothDevice.PHY_LE_2M,
                BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onConnectionStateChange(
                mBluetoothGatt, BluetoothGatt.GATT_SUCCESS, STATE_CONNECTED);
        mCallbacks.onServicesDiscovered(mBluetoothGatt, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onCharacteristicRead(
                mBluetoothGatt, mBluetoothGattCharacteristic, mBytes, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onCharacteristicWrite(
                mBluetoothGatt, mBluetoothGattCharacteristic, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onCharacteristicChanged(mBluetoothGatt, mBluetoothGattCharacteristic, mBytes);
        mCallbacks.onDescriptorRead(
                mBluetoothGatt, mBluetoothGattDescriptor, BluetoothGatt.GATT_SUCCESS, mBytes);
        mCallbacks.onDescriptorWrite(
                mBluetoothGatt, mBluetoothGattDescriptor, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onReliableWriteCompleted(mBluetoothGatt, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onReadRemoteRssi(mBluetoothGatt, 0, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onMtuChanged(mBluetoothGatt, 0, BluetoothGatt.GATT_SUCCESS);
        mCallbacks.onServiceChanged(mBluetoothGatt);

        mCallbacks.onSubrateChange(
                mBluetoothGatt, BluetoothGatt.SUBRATE_MODE_OFF, BluetoothStatusCodes.SUCCESS);

        if (Flags.gattOffloadApi()) {
            mCallbacks.onCharacteristicsOffloaded(
                    mBluetoothGatt, mGattOffloadSession, GattOffloadSession.STATUS_SUCCESS);
        }

        if (Flags.gattOffloadApi()) {
            mCallbacks.onCharacteristicsUnoffloaded(
                    mBluetoothGatt, TEST_SESSION_ID, GattOffloadSession.STATUS_SUCCESS);
        }
    }
}
