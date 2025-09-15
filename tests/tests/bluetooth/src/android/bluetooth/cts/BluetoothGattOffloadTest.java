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

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.UUID;

/**
 * Tests a small part of the offload methods in {@link BluetoothGatt} and {@link
 * BluetoothGattServer} without a real Bluetooth device.
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothGattOffloadTest {
    private static final String TAG = BluetoothGattOffloadTest.class.getSimpleName();
    private static final String TEST_DEVICE_ADDRESS = "99:11:22:AA:BB:CC";
    private static final UUID TEST_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb");
    private static final long TEST_HUB_ID = 1;
    private static final long TEST_ENDPOINT_ID = 2;
    private static final int MOCKITO_TIMEOUT_MS = 1000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final boolean mHasBluetooth =
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    private final BluetoothAdapter mAdapter =
            mContext.getSystemService(BluetoothManager.class).getAdapter();

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattService mBluetoothGattService;

    @Mock private BluetoothGattServerCallback mMockGattServerCallback;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        mBluetoothDevice = mAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS);

        HandlerThread handlerThread = new HandlerThread("BluetoothGattOffloadTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            mBluetoothGatt =
                    mBluetoothDevice.connectGatt(
                            mContext,
                            /* autoConnect= */ true,
                            new BluetoothGattCallback() {},
                            BluetoothDevice.TRANSPORT_LE,
                            BluetoothDevice.PHY_LE_1M,
                            handler);
            assertThat(mBluetoothGatt).isNotNull();
            mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
            mBluetoothGattServer =
                    mBluetoothManager.openGattServer(mContext, mMockGattServerCallback);
            assumeNotNull(mBluetoothGattServer);
        }
    }

    @After
    public void tearDown() {
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
            }
            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.close();
                mBluetoothGattServer = null;
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_GATT_OFFLOAD_API)
    @Test
    public void offloadGattClientCharacteristics_verifyPermissions() {
        assumeTrue(mHasBluetooth);
        assumeTrue(isGattClientOffloadSupported());
        // Get gatt service through gatt server because gatt client can't set attribute handles
        // without real connection.
        createGattService();
        assertThat(mBluetoothGattService.getCharacteristics()).isNotNull();
        Permissions.enforceEachPermissions(
                () ->
                        mBluetoothGatt.offloadCharacteristics(
                                mBluetoothGattService,
                                mBluetoothGattService.getCharacteristics(),
                                TEST_ENDPOINT_ID,
                                TEST_HUB_ID),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            // Verify IllegalArgumentException without an real connection.
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            mBluetoothGatt.offloadCharacteristics(
                                    mBluetoothGattService,
                                    mBluetoothGattService.getCharacteristics(),
                                    TEST_ENDPOINT_ID,
                                    TEST_HUB_ID));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_GATT_OFFLOAD_API)
    @Test
    public void offloadGattServerCharacteristics_verifyPermissions() {
        assumeTrue(mHasBluetooth);
        assumeTrue(isGattServerOffloadSupported());
        createGattService();
        assertThat(mBluetoothGattService.getCharacteristics()).isNotNull();
        Permissions.enforceEachPermissions(
                () ->
                        mBluetoothGattServer.offloadCharacteristics(
                                mBluetoothDevice,
                                mBluetoothGattService,
                                mBluetoothGattService.getCharacteristics(),
                                TEST_ENDPOINT_ID,
                                TEST_HUB_ID),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            // Verify IllegalArgumentException without an real connection.
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            mBluetoothGattServer.offloadCharacteristics(
                                    mBluetoothDevice,
                                    mBluetoothGattService,
                                    mBluetoothGattService.getCharacteristics(),
                                    TEST_ENDPOINT_ID,
                                    TEST_HUB_ID));
        }
    }

    private boolean isGattClientOffloadSupported() {
        boolean result;
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            result = mAdapter.getSupportedGattOffloadCapabilities().isClientOffloadSupported();
        }
        return result;
    }

    private boolean isGattServerOffloadSupported() {
        boolean result;
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            result = mAdapter.getSupportedGattOffloadCapabilities().isServerOffloadSupported();
        }
        return result;
    }

    private void createGattService() {
        BluetoothGattService service =
                new BluetoothGattService(TEST_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(TEST_UUID, 0x12, 0x34));
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            mBluetoothGattServer.addService(service);
        }

        ArgumentCaptor<BluetoothGattService> captor =
                ArgumentCaptor.forClass(BluetoothGattService.class);
        verify(mMockGattServerCallback, timeout(MOCKITO_TIMEOUT_MS))
                .onServiceAdded(eq(BluetoothGatt.GATT_SUCCESS), captor.capture());
        mBluetoothGattService = (BluetoothGattService) captor.getValue();
        assertThat(mBluetoothGattService).isNotNull();
    }
}
