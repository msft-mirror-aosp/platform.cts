/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothHearingAid.AdvertisementServiceData;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.EnableBluetoothRule;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Unit test cases for {@link BluetoothHearingAid}. */
@RunWith(AndroidJUnit4.class)
public class HearingAidProfileTest {
    private static final String TAG = HearingAidProfileTest.class.getSimpleName();

    @ClassRule public static final EnableBluetoothRule sEnableBluetooth = new EnableBluetoothRule();

    @Rule
    public final AdoptShellPermissionsRule mPermissionRule =
            new AdoptShellPermissionsRule(sUiAutomation, BLUETOOTH_CONNECT);

    private static final BluetoothAdapter sBluetoothAdapter = BlockingBluetoothAdapter.getAdapter();
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration WAIT_FOR_INTENT_TIMEOUT = Duration.ofSeconds(1);
    private static final String FAKE_REMOTE_ADDRESS = "42:11:22:AA:BB:CC";

    private static List<Integer> sValidConnectionStates =
            List.of(
                    BluetoothProfile.STATE_CONNECTING,
                    BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTING);

    private BluetoothHearingAid mService;

    private AdvertisementServiceData mAdvertisementData;

    @Mock BluetoothProfile.ServiceListener mServiceListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Assume.assumeTrue(TestUtils.isBleSupported(sContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HEARING_AID));

        assertThat(
                        sBluetoothAdapter.getProfileProxy(
                                sContext, mServiceListener, BluetoothProfile.HEARING_AID))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mServiceListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HEARING_AID), captor.capture());
        mService = (BluetoothHearingAid) captor.getValue();
        assertThat(mService).isNotNull();

        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b110); // CSIP supported, MODE_BINAURAL, SIDE_LEFT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        mAdvertisementData = AdvertisementServiceData.CREATOR.createFromParcel(parcel);
        assertThat(mAdvertisementData).isNotNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        sBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEARING_AID, mService);
        verify(mServiceListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HEARING_AID));
    }

    /** Basic test case to make sure that a fictional device is disconnected. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionState() {
        // Create a fake device
        BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertThat(device).isNotNull();

        // Fake device should be disconnected
        assertThat(mService.getConnectionState(device))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Basic test case to make sure that a fictional device throw a SecurityException when setting
     * volume.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void setVolume() {
        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mService.setVolume(42));
    }

    /** Basic test case to make sure that a fictional device is unknown side. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceSide() {
        // Create a fake device
        final BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertThat(device).isNotNull();

        // Fake device should be no value, unknown side
        assertThat(mService.getDeviceSide(device)).isEqualTo(BluetoothHearingAid.SIDE_UNKNOWN);
    }

    /** Basic test case to make sure that a fictional device is unknown mode. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceMode() {
        // Create a fake device
        final BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertThat(device).isNotNull();

        // Fake device should be no value, unknown mode
        assertThat(mService.getDeviceMode(device)).isEqualTo(BluetoothHearingAid.MODE_UNKNOWN);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data is
     * null.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementServiceData() {
        // Create a fake device
        final BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertThat(device).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED);

        // Fake device should have no service data
        assertThat(mService.getAdvertisementServiceData(device)).isNull();
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's mode
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceMode() {
        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;

        // Fake device should be MODE_BINAURAL
        assertThat(data.getDeviceMode()).isEqualTo(BluetoothHearingAid.MODE_BINAURAL);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's side
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceSide() {
        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;

        // Fake device should be SIDE_LEFT
        assertThat(data.getDeviceSide()).isEqualTo(BluetoothHearingAid.SIDE_LEFT);
    }

    /**
     * Basic test case to make sure that a fictional device's truncated HiSyncId is the expected
     * value.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getTruncatedHiSyncId() {
        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;
        assertThat(data).isNotNull();

        // Fake device should be supported
        assertThat(data.getTruncatedHiSyncId()).isEqualTo(1);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isCsipSupported() {
        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;
        assertThat(data).isNotNull();

        // Fake device should be supported
        assertThat(data.isCsipSupported()).isTrue();
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isLikelyPairOfBluetoothHearingAid() {
        // Create a fake advertisement data
        final AdvertisementServiceData data = mAdvertisementData;
        assertThat(data).isNotNull();

        // Create another fake advertisement data
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b111); // CSIP supported, MODE_BINAURAL, SIDE_RIGHT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        AdvertisementServiceData dataOtherSide =
                AdvertisementServiceData.CREATOR.createFromParcel(parcel);
        assertThat(dataOtherSide).isNotNull();

        // two devices should be a pair
        assertThat(data.isInPairWith(dataOtherSide)).isTrue();
    }

    /** Basic test case to get the list of connected Hearing Aid devices. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectedDevices() {
        List<BluetoothDevice> deviceList = mService.getConnectedDevices();
        Log.d(TAG, "getConnectedDevices(): size=" + deviceList.size());
        for (BluetoothDevice device : deviceList) {
            assertThat(mService.getConnectionState(device)).isIn(sValidConnectionStates);
        }
    }

    /**
     * Basic test case to get the list of matching Hearing Aid devices for each of the 4 connection
     * states.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDevicesMatchingConnectionStates() {
        for (int connectionState : sValidConnectionStates) {
            List<BluetoothDevice> deviceList =
                    mService.getDevicesMatchingConnectionStates(new int[] {connectionState});
            assertThat(deviceList).isNotNull();
            for (BluetoothDevice device : deviceList) {
                assertWithMessage("Mismatched connection state for device=" + device)
                        .that(mService.getConnectionState(device))
                        .isEqualTo(connectionState);
            }
        }
    }

    /**
     * Test case to make sure that if the connection changed intent is called, the parameters and
     * device are correct.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionStateChangedIntent() {
        // Find out how many Hearing Aid bonded devices
        List<BluetoothDevice> bondedDeviceList = new ArrayList();
        for (int connectionState : sValidConnectionStates) {
            List<BluetoothDevice> deviceList =
                    mService.getDevicesMatchingConnectionStates(new int[] {connectionState});
            bondedDeviceList.addAll(deviceList);
        }

        int numDevices = bondedDeviceList.size();
        if (numDevices <= 0) return;
        Log.d(TAG, "Number Hearing Aids devices bonded=" + numDevices);

        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        sContext.registerReceiver(
                mockReceiver,
                new IntentFilter(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED));
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        try {
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();

            verify(mockReceiver, timeout(WAIT_FOR_INTENT_TIMEOUT.toMillis()).times(numDevices))
                    .onReceive(any(), captor.capture());
        } finally {
            sContext.unregisterReceiver(mockReceiver);
        }

        for (Intent intent : captor.getAllValues()) {
            assertThat(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))
                    .isIn(sValidConnectionStates);
            assertThat(intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1))
                    .isIn(sValidConnectionStates);
            assertThat((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                    .isIn(bondedDeviceList);
        }
    }
}
