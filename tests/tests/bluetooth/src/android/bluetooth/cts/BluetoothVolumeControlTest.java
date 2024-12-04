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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothVolumeControl;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
public class BluetoothVolumeControlTest {
    private static final String TAG = BluetoothVolumeControlTest.class.getSimpleName();

    @Mock private BluetoothVolumeControl.Callback mCallback;

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothVolumeControl mBluetoothVolumeControl;
    private boolean mIsVolumeControlSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private Executor mTestExecutor;
    private BluetoothDevice mTestDevice;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothVolumeControl = null;

        boolean isLeAudioSupportedInConfig = TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO);
        boolean isVolumeControlEnabledInConfig =
                TestUtils.isProfileEnabled(BluetoothProfile.VOLUME_CONTROL);
        if (isLeAudioSupportedInConfig) {
            /* If Le Audio is supported then Volume Control shall be supported */
            assertThat(isVolumeControlEnabledInConfig).isTrue();
        }

        if (isVolumeControlEnabledInConfig) {
            mIsVolumeControlSupported =
                    mAdapter.getProfileProxy(
                            mContext,
                            new BluetoothVolumeControlServiceListener(),
                            BluetoothProfile.VOLUME_CONTROL);
            assertThat(mIsVolumeControlSupported).isTrue();

            mTestExecutor = mContext.getMainExecutor();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth) {
            if (mBluetoothVolumeControl != null) {
                mBluetoothVolumeControl.close();
                mBluetoothVolumeControl = null;
                mIsProfileReady = false;
                mTestDevice = null;
                mTestExecutor = null;
            }
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(BluetoothProfile.VOLUME_CONTROL, mBluetoothVolumeControl);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothVolumeControl.getConnectedDevices();
        assertThat(mBluetoothVolumeControl.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothVolumeControl.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @Test
    public void registerUnregisterCallback() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        // Verify parameter
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothVolumeControl.registerCallback(null, mCallback));
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothVolumeControl.registerCallback(mTestExecutor, null));
        assertThrows(
                NullPointerException.class, () -> mBluetoothVolumeControl.unregisterCallback(null));

        // Test success register unregister
        mBluetoothVolumeControl.registerCallback(mTestExecutor, mCallback);
        mBluetoothVolumeControl.unregisterCallback(mCallback);

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothVolumeControl.registerCallback(mTestExecutor, mCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @Test
    public void setVolumeOffset() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        mBluetoothVolumeControl.setVolumeOffset(mTestDevice, 0);

        enforceConnectAndPrivileged(() -> mBluetoothVolumeControl.setVolumeOffset(mTestDevice, 0));
    }

    @Test
    public void setDeviceVolume() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        int testVolume = 43;

        mBluetoothVolumeControl.setDeviceVolume(mTestDevice, testVolume, true);
        mBluetoothVolumeControl.setDeviceVolume(mTestDevice, testVolume, false);

        // volume expect in range [0, 255]
        assertThrows(
                IllegalArgumentException.class,
                () -> mBluetoothVolumeControl.setDeviceVolume(mTestDevice, -1, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> mBluetoothVolumeControl.setDeviceVolume(mTestDevice, 256, true));

        enforceConnectAndPrivileged(
                () -> mBluetoothVolumeControl.setDeviceVolume(mTestDevice, testVolume, true));
    }

    @Test
    public void isVolumeOffsetAvailable() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        enforceConnectAndPrivileged(
                () -> mBluetoothVolumeControl.isVolumeOffsetAvailable(mTestDevice));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        assertThat(mBluetoothVolumeControl.isVolumeOffsetAvailable(mTestDevice)).isFalse();
    }

    @Test
    public void getNumberOfVolumeOffsetInstances() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        enforceConnectAndPrivileged(
                () -> mBluetoothVolumeControl.getNumberOfVolumeOffsetInstances(mTestDevice));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns 0 if bluetooth is not enabled
        assertEquals(0, mBluetoothVolumeControl.getNumberOfVolumeOffsetInstances(mTestDevice));
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(
                BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothVolumeControl.getConnectionState(null));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothVolumeControl.getConnectionState(testDevice));
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothVolumeControl.getConnectionPolicy(null));

        enforceConnectAndPrivileged(() -> mBluetoothVolumeControl.getConnectionPolicy(testDevice));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothVolumeControl.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertThat(
                        mBluetoothVolumeControl.setConnectionPolicy(
                                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN))
                .isFalse();
        assertThat(
                        mBluetoothVolumeControl.setConnectionPolicy(
                                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED))
                .isFalse();

        enforceConnectAndPrivileged(
                () ->
                        mBluetoothVolumeControl.setConnectionPolicy(
                                testDevice, BluetoothProfile.CONNECTION_POLICY_ALLOWED));
        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(
                        mBluetoothVolumeControl.setConnectionPolicy(
                                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN))
                .isFalse();
    }

    @Test
    public void getAudioInputControlServices() {
        assumeTrue(mHasBluetooth && mIsVolumeControlSupported);
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothVolumeControl);

        assertThrows(
                NullPointerException.class,
                () -> mBluetoothVolumeControl.getAudioInputControlServices(null));

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        Permissions.enforceEachPermissions(
                () -> mBluetoothVolumeControl.getAudioInputControlServices(testDevice),
                List.of(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mBluetoothVolumeControl.getAudioInputControlServices(testDevice))
                    .isNotNull();
        }
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

    private void enforceConnectAndPrivileged(ThrowingRunnable runnable) {
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        assertThrows(SecurityException.class, runnable);

        // Verify throws SecurityException without permission.BLUETOOTH_CONNECT
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);
        assertThrows(SecurityException.class, runnable);
    }

    private final class BluetoothVolumeControlServiceListener
            implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothVolumeControl = (BluetoothVolumeControl) proxy;
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
