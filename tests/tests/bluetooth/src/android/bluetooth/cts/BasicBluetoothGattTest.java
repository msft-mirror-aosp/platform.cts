/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.Permissions;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;

/**
 * Tests a small part of the {@link BluetoothGatt} methods without a real Bluetooth device. Other
 * tests that run with real bluetooth connections are located in CtsVerifier.
 */
@RunWith(AndroidJUnit4.class)
public class BasicBluetoothGattTest {
    private static final String TAG = BasicBluetoothGattTest.class.getSimpleName();
    private static final String TEST_DEVICE_ADDRESS = "99:11:22:AA:BB:CC";
    private static final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final boolean mHasBluetooth =
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    private final boolean mHasCompanionDevice =
            mContext.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP);
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);

        mBluetoothAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            assertThat(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext)).isTrue();
        }
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS);

        HandlerThread handlerThread = new HandlerThread("BluetoothGattTest");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        mBluetoothGatt =
                mBluetoothDevice.connectGatt(
                        mContext,
                        /* autoConnect= */ true,
                        new BluetoothGattCallback() {},
                        BluetoothDevice.TRANSPORT_LE,
                        BluetoothDevice.PHY_LE_1M,
                        handler);
        if (mBluetoothGatt == null) {
            try {
                Thread.sleep(500); // Bt is not binded yet. Wait and retry
            } catch (InterruptedException e) {
                Log.e(TAG, "delay connectGatt interrupted");
            }
            mBluetoothGatt =
                    mBluetoothDevice.connectGatt(
                            mContext,
                            /* autoConnect= */ true,
                            new BluetoothGattCallback() {},
                            BluetoothDevice.TRANSPORT_LE,
                            BluetoothDevice.PHY_LE_1M,
                            handler);
        }
        assertThat(mBluetoothGatt).isNotNull();
    }

    @After
    public void tearDown() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        removeAssociation();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getServices() throws Exception {
        // getServices() returns an empty list if service discovery has not yet been performed.
        assertThat(mBluetoothGatt.getServices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void connect() throws Exception {
        mBluetoothGatt.connect();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPreferredPhy() throws Exception {
        mBluetoothGatt.setPreferredPhy(
                BluetoothDevice.PHY_LE_1M,
                BluetoothDevice.PHY_LE_1M,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThrows(
                UnsupportedOperationException.class, () -> mBluetoothGatt.getConnectedDevices());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThrows(
                UnsupportedOperationException.class, () -> mBluetoothGatt.getConnectionState(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mBluetoothGatt.getDevicesMatchingConnectionStates(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void writeCharacteristic_withValueOverMaxLength() {
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(TEST_UUID, 0x0A, 0x11);
        BluetoothGattService service =
                new BluetoothGattService(TEST_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(characteristic);

        // 512 is the max attribute length
        byte[] value = new byte[513];
        Arrays.fill(value, (byte) 0x01);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mBluetoothGatt.writeCharacteristic(
                                characteristic,
                                value,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LE_SUBRATE_API)
    public void requestSubrateMode_withDisabledAdapter() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Remove Associated
        removeAssociation();

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertThat(source.getPackageName()).isEqualTo("android.bluetooth.cts");

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    "BluetoothGatt.requestSubrateMode without"
                            + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                    SecurityException.class,
                    () -> mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_BALANCED));
        }

        assertThat(BTAdapterUtils.disableAdapter(mBluetoothAdapter, mContext)).isTrue();

        // Verify error with Bluetooth disabled
        assertThat(mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_BALANCED))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);

        // Re-enable Adapter
        if (!mBluetoothAdapter.isEnabled()) {
            assertThat(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext)).isTrue();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LE_SUBRATE_API)
    public void requestSubrateMode_verifyPermissionsAndParameters() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Remove Associated
        removeAssociation();

        int userId = mContext.getUser().getIdentifier();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertThat(source.getPackageName()).isEqualTo("android.bluetooth.cts");

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    "BluetoothGatt.requestSubrateMode without"
                            + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                    SecurityException.class,
                    () -> mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_BALANCED));
        }

        associateDevice();

        // Check API return values under difference parameters
        assertThat(mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_HIGH))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);

        assertThat(mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_BALANCED))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);

        assertThat(mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_LOW))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);

        assertThat(mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_OFF))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);

        assertThrows(
                IllegalArgumentException.class,
                () -> mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_HIGH + 1));

        assertThrows(
                IllegalArgumentException.class,
                () -> mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_SYSTEM_UPDATE));

        // Remove Associated
        removeAssociation();

        // This should throw a SecurityException because there is no CDM association
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    "BluetoothGatt.requestSubrateMode without"
                            + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                    SecurityException.class,
                    () -> mBluetoothGatt.requestSubrateMode(BluetoothGatt.SUBRATE_MODE_BALANCED));
        }
    }

    private void associateDevice() {
        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();
        runShellCommand(
                String.format(
                        "cmd companiondevice associate %d %s %s",
                        userId, packageName, TEST_DEVICE_ADDRESS));
        String output = runShellCommand("dumpsys companiondevice");
        assertThat(output).contains(packageName);
        assertThat(output).ignoringCase().contains(TEST_DEVICE_ADDRESS);
    }

    private void removeAssociation() {
        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();
        runShellCommand(
                String.format(
                        "cmd companiondevice disassociate %d %s %s",
                        userId, packageName, TEST_DEVICE_ADDRESS));
    }
}
