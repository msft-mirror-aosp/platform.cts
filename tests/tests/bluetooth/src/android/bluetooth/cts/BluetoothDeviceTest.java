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

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothDevice.ACCESS_ALLOWED;
import static android.bluetooth.BluetoothDevice.ACCESS_REJECTED;
import static android.bluetooth.BluetoothDevice.ACCESS_UNKNOWN;
import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_BREDR;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevice.BluetoothAddress;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattConnectionSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BondStatus;
import android.bluetooth.EncryptionStatus;
import android.bluetooth.OobData;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.CddTest;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.hamcrest.MockitoHamcrest;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothDeviceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();

    private boolean mHasBluetooth;
    private boolean mHasCompanionDevice;

    private final String mFakeDeviceAddress = "00:11:22:AA:BB:CC";
    private BluetoothDevice mFakeDevice;
    private int mFakePsm = 100;
    private UUID mFakeUuid = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB");
    private int mFakeKeySize = 16;

    @Before
    public void setUp() throws Exception {
        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

        mHasCompanionDevice =
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP);

        if (mHasBluetooth && mHasCompanionDevice) {
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();
            mFakeDevice = mAdapter.getRemoteDevice(mFakeDeviceAddress);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth && mHasCompanionDevice) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void setAlias_getAlias() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertThat(source.getPackageName()).isEqualTo("android.bluetooth.cts");

        // Verifies that when there is no alias, we return the device name
        assertThat(mFakeDevice.getAlias()).isNull();

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.setAlias(""));

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        assertThrows(
                "BluetoothDevice.setAlias without"
                        + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.setAlias(testDeviceAlias));

        runShellCommand(
                String.format(
                        "cmd companiondevice associate %d %s %s",
                        userId, packageName, mFakeDeviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertThat(output).contains(packageName);
        assertThat(output.toLowerCase()).contains(mFakeDeviceAddress.toLowerCase());

        // Takes time to update the CDM cache, so sleep to ensure the association is cached
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertThat(mFakeDevice.setAlias(testDeviceAlias))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        runShellCommand(
                String.format(
                        "cmd companiondevice disassociate %d %s %s",
                        userId, packageName, mFakeDeviceAddress));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.getAlias()).isNull();
        assertThat(mFakeDevice.setAlias(testDeviceAlias))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
    }

    @Test
    public void getAddressType() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_PUBLIC);
    }

    @Test
    public void getIdentityAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getIdentityAddress());
    }

    @Test
    public void getIdentityAddressWithType() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getIdentityAddressWithType());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void testBluetoothAddress() {
        int addressType = BluetoothDevice.ADDRESS_TYPE_PUBLIC;
        BluetoothAddress bluetoothAddress = new BluetoothAddress(mFakeDeviceAddress, addressType);

        assertThat(bluetoothAddress.getAddress()).isEqualTo(mFakeDeviceAddress);
        assertThat(bluetoothAddress.getAddressType()).isEqualTo(addressType);
    }

    @Test
    public void getConnectionHandle() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getConnectionHandle(TRANSPORT_LE));

        // but it should work after we get the permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(mFakeDevice.getConnectionHandle(TRANSPORT_LE)).isEqualTo(BluetoothDevice.ERROR);
    }

    @Test
    public void getAnonymizedAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getAnonymizedAddress()).isEqualTo("XX:XX:XX:XX:BB:CC");
    }

    @Test
    public void getBatteryLevel() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getBatteryLevel()).isEqualTo(BluetoothDevice.BATTERY_LEVEL_UNKNOWN);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getBatteryLevel());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.getBatteryLevel())
                .isEqualTo(BluetoothDevice.BATTERY_LEVEL_BLUETOOTH_OFF);
    }

    @Test
    public void isBondingInitiatedLocally() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.isBondingInitiatedLocally()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isBondingInitiatedLocally());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.isBondingInitiatedLocally()).isFalse();
    }

    @Test
    public void prepareToEnterProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mFakeDevice.prepareToEnterProcess(null);
    }

    @Test
    public void setPin() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.setPin((String) null)).isFalse();
        assertThat(mFakeDevice.setPin("12345678901234567")).isFalse(); // check PIN too big

        assertThat(mFakeDevice.setPin("123456")).isFalse(); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.setPin("123456"));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.setPin("123456")).isFalse();
    }

    @Test
    public void connect_disconnect() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.connect());
        assertThrows(SecurityException.class, () -> mFakeDevice.disconnect());
    }

    @Test
    public void cancelBondProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.cancelBondProcess());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.cancelBondProcess()).isFalse();
    }

    @Test
    public void createBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.createBond(TRANSPORT_AUTO));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.createBond(TRANSPORT_AUTO)).isFalse();
    }

    @Test
    public void createBondOutOfBand() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        OobData data = new OobData.ClassicBuilder(new byte[16], new byte[2], new byte[7]).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> mFakeDevice.createBondOutOfBand(TRANSPORT_AUTO, null, null));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.createBondOutOfBand(TRANSPORT_AUTO, data, null));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @Test
    public void getUuids() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getUuids()).isNull();
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getUuids());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.getUuids()).isNull();
    }

    @Test
    public void isEncrypted() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not connected
        assertThat(mFakeDevice.isEncrypted()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isEncrypted());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.isEncrypted()).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_APAIRING_26Q2_PERMISSION_IMPROVEMENTS)
    @Test
    public void removeBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mFakeDevice.removeBond()).isFalse();
        }

        mUiAutomation.dropShellPermissionIdentity();

        if (Build.VERSION.SDK_INT >= 37) {
            Permissions.enforceEachPermissions(
                    () -> mFakeDevice.removeBond(),
                    List.of(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED));
        } else {
            assertThrows(SecurityException.class, () -> mFakeDevice.removeBond());
        }

        // Starting with API level 37, removeBond() requires BLUETOOTH_PRIVILEGED permission
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mFakeDevice.removeBond()).isFalse();
        }
    }

    // TODO(delwiche): Remove this test once the flag is fully rolled out.
    @RequiresFlagsDisabled(Flags.FLAG_APAIRING_26Q2_PERMISSION_IMPROVEMENTS)
    @Test
    public void removeBondLegacy() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded
        assertThat(mFakeDevice.removeBond()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.removeBond());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.removeBond()).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_APAIRING_26Q2_PERMISSION_IMPROVEMENTS)
    @Test
    public void setPinByteArray() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Starting with API level 37, setPin() requires BLUETOOTH_PRIVILEGED permission
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThrows(NullPointerException.class, () -> mFakeDevice.setPin((byte[]) null));

            // check PIN too big
            assertThat(mFakeDevice.setPin(convertPinToBytes("12345678901234567"))).isFalse();
            assertThat(mFakeDevice.setPin(convertPinToBytes("123456")))
                    .isFalse(); // device is not bonding
        }

        mUiAutomation.dropShellPermissionIdentity();
        if (Build.VERSION.SDK_INT >= 37) {
            Permissions.enforceEachPermissions(
                    () -> mFakeDevice.setPin(convertPinToBytes("123456")),
                    List.of(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED));
        } else {
            assertThrows(
                    SecurityException.class, () -> mFakeDevice.setPin(convertPinToBytes("123456")));
        }

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mFakeDevice.setPin(convertPinToBytes("123456"))).isFalse();
        }
    }

    // TODO(delwiche): Remove this test once the flag is fully rolled out.
    @RequiresFlagsDisabled(Flags.FLAG_APAIRING_26Q2_PERMISSION_IMPROVEMENTS)
    @Test
    public void setPinByteArrayLegacy() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(NullPointerException.class, () -> mFakeDevice.setPin((byte[]) null));

        // check PIN too big
        assertThat(mFakeDevice.setPin(convertPinToBytes("12345678901234567"))).isFalse();
        assertThat(mFakeDevice.setPin(convertPinToBytes("123456")))
                .isFalse(); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setPin(convertPinToBytes("123456")));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.setPin(convertPinToBytes("123456"))).isFalse();
    }

    @Test
    public void connectGatt() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(
                NullPointerException.class,
                () ->
                        mFakeDevice.connectGatt(
                                mContext,
                                false,
                                null,
                                TRANSPORT_AUTO,
                                BluetoothDevice.PHY_LE_1M_MASK));

        assertThrows(
                NullPointerException.class,
                () ->
                        mFakeDevice.connectGatt(
                                mContext,
                                false,
                                null,
                                TRANSPORT_AUTO,
                                BluetoothDevice.PHY_LE_1M_MASK,
                                null));
    }

    /**
     * Test method for {@link BluetoothDevice#fetchUuids(int)}. This test requires the
     * FLAG_EXPLICIT_UUID_TRANSPORT_API to be enabled.
     */
    @RequiresFlagsEnabled(Flags.FLAG_EXPLICIT_UUID_TRANSPORT_API)
    @Test
    public void fetchUuids() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // --- Tests below run WITH BLUETOOTH_CONNECT ---
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {

            // 2. Test with different Transports
            assertThat(mFakeDevice.fetchUuids(TRANSPORT_AUTO)).isTrue();
            assertThat(mFakeDevice.fetchUuids(TRANSPORT_BREDR)).isTrue();
            assertThat(mFakeDevice.fetchUuids(TRANSPORT_LE)).isTrue();

            // 3. Test with Bluetooth Disabled
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(mFakeDevice.fetchUuids(TRANSPORT_AUTO)).isFalse();
            assertThat(mFakeDevice.fetchUuids(TRANSPORT_BREDR)).isFalse();
        } // Permissions.withPermissions() restores original permission state here.
    }

    /**
     * Test method for {@link BluetoothDevice#fetchUuids(int)}. This test requires the
     * FLAG_EXPLICIT_UUID_TRANSPORT_API to be enabled.
     */
    @RequiresFlagsEnabled(Flags.FLAG_EXPLICIT_UUID_TRANSPORT_API)
    @Test
    public void fetchUuidsInvalidTransport() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {

            // Test with a clearly out-of-range negative value
            int invalidTransportNegative = -1;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> mFakeDevice.fetchUuids(invalidTransportNegative));

            // Test with a value greater than defined transports
            int invalidTransportPositive = 3; // Assuming valid are 0, 1, 2
            assertThrows(
                    IllegalArgumentException.class,
                    () -> mFakeDevice.fetchUuids(invalidTransportPositive));

            // Example of another out-of-range value
            int anotherInvalidTransport = 100;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> mFakeDevice.fetchUuids(anotherInvalidTransport));
        }
    }

    /**
     * Test method for {@link BluetoothDevice#fetchUuids(int)}. Tests that SecurityException is
     * thrown when calling fetchUuids without BLUETOOTH_CONNECT permission.
     */
    @RequiresFlagsEnabled(Flags.FLAG_EXPLICIT_UUID_TRANSPORT_API)
    @Test
    public void fetchUuidsPermissionEnforcement() {
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // setUp() adopts BLUETOOTH_CONNECT, so drop it to test enforcement.
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuids(TRANSPORT_BREDR));
    }

    @Test
    public void fetchUuidsWithSdp() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.fetchUuidsWithSdp()).isTrue();

        // TRANSPORT_AUTO doesn't need BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO)).isTrue();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_BREDR));
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_LE));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO)).isFalse();
    }

    @Test
    public void messageAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if MAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.MAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void phonebookAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if PBAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.PBAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void simAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if SAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.SAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void isRequestAudioPolicyAsSinkSupported() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(
                SecurityException.class, () -> mFakeDevice.isRequestAudioPolicyAsSinkSupported());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mFakeDevice.isRequestAudioPolicyAsSinkSupported())
                .isEqualTo(BluetoothStatusCodes.FEATURE_NOT_CONFIGURED);
    }

    @Test
    public void setGetAudioPolicy() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        BluetoothSinkAudioPolicy demoAudioPolicy = new BluetoothSinkAudioPolicy.Builder().build();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy));
        assertThrows(SecurityException.class, () -> mFakeDevice.getRequestedAudioPolicyAsSink());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        assertThat(mFakeDevice.getRequestedAudioPolicyAsSink()).isNull();

        BluetoothSinkAudioPolicy newPolicy =
                new BluetoothSinkAudioPolicy.Builder(demoAudioPolicy)
                        .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setActiveDevicePolicyAfterConnection(
                                BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                        .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .build();

        assertThat(mFakeDevice.requestAudioPolicyAsSink(newPolicy))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        assertThat(mFakeDevice.getRequestedAudioPolicyAsSink()).isNull();

        assertThat(newPolicy.getCallEstablishPolicy())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_ALLOWED);
        assertThat(newPolicy.getActiveDevicePolicyAfterConnection())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED);
        assertThat(newPolicy.getInBandRingtonePolicy())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_ALLOWED);
    }

    private byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return null;
        }
        return pinBytes;
    }

    @Test
    public void getPackageNameOfBondingApplication() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        mContext.registerReceiver(mockReceiver, filter);

        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class, () -> mFakeDevice.getPackageNameOfBondingApplication());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class, () -> mFakeDevice.getPackageNameOfBondingApplication());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT);
        // Since no application actually start bonding with this device, this should return null
        assertThat(mFakeDevice.getPackageNameOfBondingApplication()).isNull();

        mFakeDevice.createBond();
        assertThat(mFakeDevice.getPackageNameOfBondingApplication())
                .isEqualTo(mContext.getPackageName());
        verifyIntentReceived(
                mockReceiver,
                Duration.ofSeconds(5),
                hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                hasExtra(BluetoothDevice.EXTRA_DEVICE, mFakeDevice),
                hasExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING));

        // Clean up create bond
        // Either cancel the bonding process or remove bond
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            mFakeDevice.cancelBondProcess();
            mFakeDevice.removeBond();
        }

        verifyIntentReceived(
                mockReceiver,
                Duration.ofSeconds(5),
                hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                hasExtra(BluetoothDevice.EXTRA_DEVICE, mFakeDevice),
                hasExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE));
    }

    @Test
    public void setActiveAudioDevicePolicy_getActiveAudioDevicePolicy() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:AA:AA";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        // This should throw a SecurityException because no BLUETOOTH_CONNECT permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(device.getActiveAudioDevicePolicy())
                .isEqualTo(BluetoothDevice.ACTIVE_AUDIO_DEVICE_POLICY_DEFAULT);
        assertThat(
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
    }

    @Test
    public void setMicrophonePreferredForCalls_isMicrophonePreferredForCalls() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Use alternate address to prevent another test from having unwanted consequences here
        mFakeDevice = mAdapter.getRemoteDevice("AB:11:22:AA:BB:CC");
        Permissions.enforceEachPermissions(
                () -> mFakeDevice.setMicrophonePreferredForCalls(false),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        Permissions.enforceEachPermissions(
                () -> mFakeDevice.isMicrophonePreferredForCalls(),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        // default value should be true
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mFakeDevice.isMicrophonePreferredForCalls()).isTrue();
            assertThat(mFakeDevice.setMicrophonePreferredForCalls(true))
                    .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        }
    }

    @Test
    public void getKeyMissingCount() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded, so key missing count should be -1.
        mFakeDevice = mAdapter.getRemoteDevice("AB:11:22:AA:BB:DD");
        assertThat(mFakeDevice.getKeyMissingCount()).isEqualTo(-1);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getKeyMissingCount());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        mFakeDevice.createBond();
        if (mFakeDevice.isConnected()) {
            assertThat(mFakeDevice.getKeyMissingCount()).isEqualTo(0);
        }
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            mFakeDevice.removeBond();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_PRIORITIZED_IN_EAR_ROUTING)
    @Test
    public void testSetOnHeadDetectionEnabled_permissionsAndEdgeCases() {
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        Permissions.enforceEachPermissions(
                () -> mFakeDevice.setOnHeadDetectionEnabled(true),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            // Test when Bluetooth is disabled
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(mFakeDevice.setOnHeadDetectionEnabled(true))
                    .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_PRIORITIZED_IN_EAR_ROUTING)
    @Test
    public void testSetOnHead_permissionsAndEdgeCases() {
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        Permissions.enforceEachPermissions(
                () -> mFakeDevice.setOnHead(true),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            // Test when Bluetooth is disabled
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(mFakeDevice.setOnHead(true))
                    .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
        }
    }

    private void verifyIntentReceived(
            BroadcastReceiver receiver, Duration timeout, Matcher<Intent>... matchers) {
        verify(receiver, timeout(timeout.toMillis()))
                .onReceive(any(), MockitoHamcrest.argThat(AllOf.allOf(matchers)));
    }

    @Test
    public void isConnected_getEncryptionStatus() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded, so key missing count should be -1.
        mFakeDevice = mAdapter.getRemoteDevice("AB:11:22:AA:BB:FF");
        assertThat(mFakeDevice.isConnected(BluetoothDevice.TRANSPORT_BREDR)).isEqualTo(false);
        assertThat(mFakeDevice.getEncryptionStatus(BluetoothDevice.TRANSPORT_BREDR)).isNull();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.isConnected(BluetoothDevice.TRANSPORT_BREDR));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.getEncryptionStatus(BluetoothDevice.TRANSPORT_BREDR));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        // Create a fake encryption status and verify the values, mocking the values is not possible
        // as the BluetoothDevice class is final.
        EncryptionStatus encryptionStatus =
                new EncryptionStatus(mFakeKeySize, BluetoothDevice.ENCRYPTION_ALGORITHM_AES);
        assertThat(encryptionStatus.getAlgorithm())
                .isEqualTo(BluetoothDevice.ENCRYPTION_ALGORITHM_AES);
        assertThat(encryptionStatus.getKeySize()).isEqualTo(mFakeKeySize);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_GET_BOND_STATUS)
    @Test
    public void getBondStatus() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded.
        mFakeDevice = mAdapter.getRemoteDevice("AB:11:22:AA:BB:DE");
        // For an unbonded device, this should return null.
        assertThat(mFakeDevice.getBondStatus(BluetoothDevice.TRANSPORT_BREDR)).isNull();
        assertThat(mFakeDevice.getBondStatus(BluetoothDevice.TRANSPORT_LE)).isNull();

        // TRANSPORT_AUTO is not a valid transport for getBondStatus.
        assertThrows(
                IllegalArgumentException.class,
                () -> mFakeDevice.getBondStatus(BluetoothDevice.TRANSPORT_AUTO));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.getBondStatus(BluetoothDevice.TRANSPORT_BREDR));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.getBondStatus(BluetoothDevice.TRANSPORT_LE));

        // Create a fake bond status and verify the getters.
        BondStatus bondStatus =
                new BondStatus(
                        BluetoothDevice.PAIRING_ALGORITHM_BREDR_SSP,
                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION);
        assertThat(bondStatus.getPairingVariant())
                .isEqualTo(BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION);
        assertThat(bondStatus.getPairingAlgorithm())
                .isEqualTo(BluetoothDevice.PAIRING_ALGORITHM_BREDR_SSP);
    }

    /*Testcases for BluetoothDevice#connectGatt(BluetoothGattConnectionSettings)*/
    @Test(expected = NullPointerException.class)
    @RequiresFlagsEnabled(Flags.FLAG_GATT_CONN_SETTINGS)
    public void illegalArgumentsToConnectGattWithNullConnectionSettings() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // No support for Gatt connection without Gatt Callback handler
        mFakeDevice.connectGatt(
                (BluetoothGattConnectionSettings) null,
                mContext.getMainExecutor(),
                new BluetoothGattCallback() {});
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GATT_CONN_SETTINGS)
    public void connectGattUsingConnectionSettingsTest() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        BluetoothGattConnectionSettings settings =
                new BluetoothGattConnectionSettings.Builder()
                        .setTransport(BluetoothDevice.TRANSPORT_LE)
                        .setAutoConnectEnabled(false)
                        .setOpportunisticEnabled(false)
                        .setAutomaticMtuEnabled(true)
                        .build();

        BluetoothGatt gatt =
                mFakeDevice.connectGatt(
                        settings, mContext.getMainExecutor(), new BluetoothGattCallback() {});

        assertThat(gatt).isNotNull();
    }
}
