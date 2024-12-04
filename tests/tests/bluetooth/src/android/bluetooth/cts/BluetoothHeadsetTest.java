/*
 * Copyright 2022 The Android Open Source Project
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
import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHeadsetTest {
    private static final String TAG = BluetoothHeadsetTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothHeadset mBluetoothHeadset;
    private boolean mIsHeadsetSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHeadsetSupported = TestUtils.isProfileEnabled(BluetoothProfile.HEADSET);
        if (!mIsHeadsetSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHeadset = null;

        mAdapter.getProfileProxy(
                mContext, new BluetoothHeadsetServiceListener(), BluetoothProfile.HEADSET);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHeadsetSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothHeadset != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mBluetoothHeadset = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(
                        mBluetoothHeadset.getDevicesMatchingConnectionStates(
                                new int[] {STATE_CONNECTED}))
                .isEmpty();
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(mBluetoothHeadset.getConnectionState(testDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void isAudioConnected() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(mBluetoothHeadset.isAudioConnected(testDevice)).isFalse();
        assertThat(mBluetoothHeadset.isAudioConnected(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.isAudioConnected(testDevice)).isFalse();
    }

    @Test
    public void isNoiseReductionSupported() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(mBluetoothHeadset.isNoiseReductionSupported(testDevice)).isFalse();
        assertThat(mBluetoothHeadset.isNoiseReductionSupported(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.isNoiseReductionSupported(testDevice)).isFalse();
    }

    @Test
    public void isVoiceRecognitionSupported() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice)).isFalse();
        assertThat(mBluetoothHeadset.isVoiceRecognitionSupported(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice)).isFalse();
    }

    @Test
    public void sendVendorSpecificResultCode() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThrows(
                IllegalArgumentException.class,
                () -> mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, null, null));

        assertThat(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", "")).isFalse();
        assertThat(mBluetoothHeadset.sendVendorSpecificResultCode(null, "", "")).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", "")).isFalse();
    }

    @Test
    public void connect() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        // Verify returns false when invalid input is given
        assertThat(mBluetoothHeadset.connect(null)).isFalse();

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, MODIFY_PHONE_STATE);
        assertThat(mBluetoothHeadset.connect(testDevice)).isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.connect(testDevice)).isFalse();
    }

    @Test
    public void disconnect() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        // Verify returns false when invalid input is given
        assertThat(mBluetoothHeadset.disconnect(null)).isFalse();

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThat(mBluetoothHeadset.disconnect(testDevice)).isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.disconnect(testDevice)).isFalse();
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertThat(mBluetoothHeadset.getConnectionPolicy(null))
                .isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        // Verify returns CONNECTION_POLICY_FORBIDDEN if bluetooth is not enabled
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.getConnectionPolicy(testDevice))
                .isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertThat(mBluetoothHeadset.setConnectionPolicy(testDevice, CONNECTION_POLICY_UNKNOWN))
                .isFalse();
        assertThat(mBluetoothHeadset.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED))
                .isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.setConnectionPolicy(testDevice, CONNECTION_POLICY_FORBIDDEN))
                .isFalse();
    }

    @Test
    public void getAudioState() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThrows(NullPointerException.class, () -> mBluetoothHeadset.getAudioState(null));

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThat(mBluetoothHeadset.getAudioState(testDevice))
                .isEqualTo(BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
    }

    @Test
    public void connectAudio() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.connectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.connectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND);
    }

    @Test
    public void disconnectAudio() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.disconnectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.disconnectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND);
    }

    @Test
    public void startScoUsingVirtualVoiceCall() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(
                BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED, MODIFY_PHONE_STATE);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.startScoUsingVirtualVoiceCall()).isFalse();
    }

    @Test
    public void stopScoUsingVirtualVoiceCall() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(
                BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED, MODIFY_PHONE_STATE);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.stopScoUsingVirtualVoiceCall()).isFalse();
    }

    @Test
    public void isInbandRingingEnabled() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothHeadset.isInbandRingingEnabled()).isFalse();
    }

    @Test
    public void setGetAudioRouteAllowed() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothHeadset).isNotNull();

        assertThat(mBluetoothHeadset.setAudioRouteAllowed(true))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mBluetoothHeadset.getAudioRouteAllowed())
                .isEqualTo(BluetoothStatusCodes.ALLOWED);

        assertThat(mBluetoothHeadset.setAudioRouteAllowed(false))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mBluetoothHeadset.getAudioRouteAllowed())
                .isEqualTo(BluetoothStatusCodes.NOT_ALLOWED);
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

    private final class BluetoothHeadsetServiceListener
            implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHeadset = (BluetoothHeadset) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }
}
