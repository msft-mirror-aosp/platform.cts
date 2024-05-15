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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

    private static final int WAIT_FOR_INTENT_TIMEOUT_MS = 10000; // ms to wait for intent callback
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect
    private static final String FAKE_REMOTE_ADDRESS = "42:11:22:AA:BB:CC";

    private static List<Integer> sValidConnectionStates =
            List.of(
                    BluetoothProfile.STATE_CONNECTING,
                    BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTING);

    private BluetoothHearingAid mService;
    private BroadcastReceiver mIntentReceiver;

    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private boolean mIsProfileReady;
    private AdvertisementServiceData mAdvertisementData;

    private List<BluetoothDevice> mIntentCallbackDeviceList;

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(sContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HEARING_AID));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mService = null;
        sBluetoothAdapter.getProfileProxy(
                sContext, new HearingAidsServiceListener(), BluetoothProfile.HEARING_AID);

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
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mService).isNotNull();
        assertThat(mIsProfileReady).isTrue();

        sBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEARING_AID, mService);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    /** Basic test case to make sure that Hearing Aid Profile Proxy can connect. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getProxyServiceConnect() {
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();
    }

    /** Basic test case to make sure that a fictional device is disconnected. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionState() {
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mService.setVolume(42));
    }

    /** Basic test case to make sure that a fictional device is unknown side. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceSide() {
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

        List<BluetoothDevice> deviceList;

        deviceList = mService.getConnectedDevices();
        Log.d(TAG, "getConnectedDevices(): size=" + deviceList.size());
        for (BluetoothDevice device : deviceList) {
            int connectionState = mService.getConnectionState(device);
            checkValidConnectionState(connectionState);
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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

        for (int connectionState : sValidConnectionStates) {
            List<BluetoothDevice> deviceList;

            deviceList = mService.getDevicesMatchingConnectionStates(new int[] {connectionState});
            assertThat(deviceList).isNotNull();
            Log.d(
                    TAG,
                    "getDevicesMatchingConnectionStates("
                            + connectionState
                            + "): size="
                            + deviceList.size());
            checkDeviceListAndStates(deviceList, connectionState);
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
        waitForProfileConnect();
        assertThat(mIsProfileReady).isTrue();
        assertThat(mService).isNotNull();

        // Find out how many Hearing Aid bonded devices
        List<BluetoothDevice> bondedDeviceList = new ArrayList();
        int numDevices = 0;
        for (int connectionState : sValidConnectionStates) {
            List<BluetoothDevice> deviceList;

            deviceList = mService.getDevicesMatchingConnectionStates(new int[] {connectionState});
            bondedDeviceList.addAll(deviceList);
            numDevices += deviceList.size();
        }

        if (numDevices <= 0) return;
        Log.d(TAG, "Number Hearing Aids devices bonded=" + numDevices);

        mIntentCallbackDeviceList = new ArrayList();

        // Set up the Connection State Changed receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        mIntentReceiver = new HearingAidIntentReceiver();
        sContext.registerReceiver(mIntentReceiver, filter);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        int sanityCount = WAIT_FOR_INTENT_TIMEOUT_MS;
        while ((numDevices != mIntentCallbackDeviceList.size()) && (sanityCount > 0)) {
            final int SLEEP_QUANTUM_MS = 100;
            sleep(SLEEP_QUANTUM_MS);
            sanityCount -= SLEEP_QUANTUM_MS;
        }

        // Tear down
        sContext.unregisterReceiver(mIntentReceiver);

        assertThat(bondedDeviceList).containsExactly(mIntentCallbackDeviceList);
    }

    private boolean waitForProfileConnect() {
        mProfileConnectionlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return mIsProfileReady;
    }

    private boolean waitForProfileDisconnect() {
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mProfileConnectionlock.lock();
        try {
            while (mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Disconnect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileDisconnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return !mIsProfileReady;
    }

    private final class HearingAidsServiceListener implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mService = (BluetoothHearingAid) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            mService = null;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }

    private class HearingAidIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                int previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                Log.d(
                        TAG,
                        "HearingAidIntentReceiver.onReceive: device="
                                + device
                                + ", state="
                                + state
                                + ", previousState="
                                + previousState);

                checkValidConnectionState(state);
                checkValidConnectionState(previousState);

                mIntentCallbackDeviceList.add(device);
            }
        }
    }

    private void checkDeviceListAndStates(List<BluetoothDevice> deviceList, int connectionState) {
        Log.d(
                TAG,
                "checkDeviceListAndStates(): size="
                        + deviceList.size()
                        + ", connectionState="
                        + connectionState);
        for (BluetoothDevice device : deviceList) {
            assertWithMessage("Mismatched connection state for device=" + device)
                    .that(mService.getConnectionState(device))
                    .isEqualTo(connectionState);
        }
    }

    private void checkValidConnectionState(int connectionState) {
        assertThat(connectionState).isIn(sValidConnectionStates);
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
        }
    }
}
