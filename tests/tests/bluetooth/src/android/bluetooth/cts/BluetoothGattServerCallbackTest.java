/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.GattOffloadSession;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.CddTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class BluetoothGattServerCallbackTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private BluetoothDevice mDevice;
    @Mock private BluetoothGattServerCallback mCallbacks;
    @Mock private GattOffloadSession mGattOffloadSession;
    @Mock private BluetoothGattService mBluetoothGattService;
    @Mock private BluetoothGattDescriptor mBluetoothGattDescriptor;
    @Mock private BluetoothGattCharacteristic mBluetoothGattCharacteristic;

    private static final byte[] BYTES = new byte[] {};
    private static final int TEST_SESSION_ID = 1;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        assumeTrue(TestUtils.isBleSupported(mContext));
        mCallbacks.onConnectionStateChange(mDevice, STATE_CONNECTED, STATE_CONNECTED);
        mCallbacks.onServiceAdded(GATT_SUCCESS, mBluetoothGattService);
        mCallbacks.onCharacteristicReadRequest(mDevice, 0, 0, mBluetoothGattCharacteristic);
        mCallbacks.onCharacteristicWriteRequest(
                mDevice, 0, mBluetoothGattCharacteristic, true, true, 0, BYTES);
        mCallbacks.onDescriptorReadRequest(mDevice, 0, 0, mBluetoothGattDescriptor);
        mCallbacks.onDescriptorWriteRequest(
                mDevice, 0, mBluetoothGattDescriptor, true, true, 0, BYTES);
        mCallbacks.onExecuteWrite(mDevice, 0, true);
        mCallbacks.onNotificationSent(mDevice, GATT_SUCCESS);
        mCallbacks.onMtuChanged(mDevice, 0);
        mCallbacks.onPhyUpdate(
                mDevice, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_2M, GATT_SUCCESS);
        mCallbacks.onPhyRead(
                mDevice, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_2M, GATT_SUCCESS);

        mCallbacks.onSubrateChange(mDevice, BluetoothGatt.SUBRATE_MODE_OFF, GATT_SUCCESS);
        mCallbacks.onSubrateChange(mDevice, BluetoothGatt.SUBRATE_MODE_LOW, GATT_SUCCESS);
        mCallbacks.onSubrateChange(mDevice, BluetoothGatt.SUBRATE_MODE_BALANCED, GATT_SUCCESS);
        mCallbacks.onSubrateChange(mDevice, BluetoothGatt.SUBRATE_MODE_HIGH, GATT_SUCCESS);

        if (Flags.gattOffloadApi()) {
            mCallbacks.onCharacteristicsOffloaded(
                    mDevice, mGattOffloadSession, GattOffloadSession.STATUS_SUCCESS);

            mCallbacks.onCharacteristicsUnoffloaded(
                    mDevice, TEST_SESSION_ID, GattOffloadSession.STATUS_SUCCESS);
        }
    }
}
