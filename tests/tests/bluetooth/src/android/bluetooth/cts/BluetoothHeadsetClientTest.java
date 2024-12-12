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

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHeadsetClientTest {
    private static final String TAG = BluetoothHeadsetClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothHeadsetClient mBluetoothHeadsetClient;
    private boolean mIsHeadsetClientSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHeadsetClientSupported = BluetoothProperties.isProfileHfpHfEnabled().orElse(false);
        if (!mIsHeadsetClientSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHeadsetClient = null;

        mAdapter.getProfileProxy(
                mContext,
                new BluetoothHeadsetClientServiceListener(),
                BluetoothProfile.HEADSET_CLIENT);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothHeadsetClient != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT, mBluetoothHeadsetClient);
            mBluetoothHeadsetClient = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT, mBluetoothHeadsetClient);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothHeadsetClient.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothHeadsetClient.getDevicesMatchingConnectionStates(new int[] {}))
                .isEmpty();
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(STATE_DISCONNECTED, mBluetoothHeadsetClient.getConnectionState(null));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(STATE_DISCONNECTED, mBluetoothHeadsetClient.getConnectionState(testDevice));
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(
                CONNECTION_POLICY_FORBIDDEN, mBluetoothHeadsetClient.getConnectionPolicy(null));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHeadsetClient.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertThat(
                        mBluetoothHeadsetClient.setConnectionPolicy(
                                testDevice, CONNECTION_POLICY_UNKNOWN))
                .isFalse();
        assertThat(mBluetoothHeadsetClient.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED))
                .isFalse();

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(
                        mBluetoothHeadsetClient.setConnectionPolicy(
                                testDevice, CONNECTION_POLICY_FORBIDDEN))
                .isFalse();
    }

    @Test
    public void getNetworkServiceState() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertThat(mBluetoothHeadsetClient.getNetworkServiceState(testDevice)).isNull();
        assertThat(mBluetoothHeadsetClient.getNetworkServiceState(null)).isNull();
    }

    @Test
    @Ignore("b/373918345")
    public void createNetworkServiceStateFromParcel() {
        assumeTrue(mHasBluetooth && mIsHeadsetClientSupported);
        String testDeviceAddr = "00:11:22:AA:BB:CC";
        BluetoothDevice testDevice = mAdapter.getRemoteDevice(testDeviceAddr);

        Parcel p = Parcel.obtain();
        testDevice.writeToParcel(p, 0);
        p.writeInt(0); // Service Available
        p.writeString(""); // Operator Name
        p.writeInt(0); // General Signal Strength
        p.writeInt(0); // Roaming
        p.setDataPosition(0);

        BluetoothHeadsetClient.NetworkServiceState state =
                BluetoothHeadsetClient.NetworkServiceState.CREATOR.createFromParcel(p);

        assertEquals(testDevice, state.getDevice());
        assertEquals(false, state.isServiceAvailable());
        assertEquals("", state.getNetworkOperatorName());
        assertEquals(0, state.getSignalStrength());
        assertEquals(false, state.isRoaming());
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

    private final class BluetoothHeadsetClientServiceListener
            implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
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
