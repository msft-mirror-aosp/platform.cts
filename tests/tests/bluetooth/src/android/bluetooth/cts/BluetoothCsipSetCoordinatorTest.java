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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCsipSetCoordinator;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
public class BluetoothCsipSetCoordinatorTest {
    private static final String TAG = BluetoothCsipSetCoordinatorTest.class.getSimpleName();

    @Mock private BluetoothCsipSetCoordinator.ClientLockCallback mCallback;

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect
    private static final int TEST_CALLBACK_TIMEOUT_MS = 500; // ms timeout for test callback

    private Context mContext;
    private BluetoothAdapter mAdapter;

    private BluetoothCsipSetCoordinator mBluetoothCsipSetCoordinator;
    private boolean mIsCsipSetCoordinatorSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private boolean mGroupLockCallbackCalled;
    private Condition mConditionTestCallback;
    private Executor mTestExecutor;
    private BluetoothDevice mTestDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothCsipSetCoordinator = null;

        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO));
        assertEquals(BluetoothStatusCodes.FEATURE_SUPPORTED, mAdapter.isLeAudioSupported());

        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR));
        assertThat(TestUtils.isProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR)).isTrue();

        Assume.assumeTrue(
                mAdapter.getProfileProxy(
                        mContext,
                        new BluetoothCsipServiceListener(),
                        BluetoothProfile.CSIP_SET_COORDINATOR));

        mGroupLockCallbackCalled = false;

        mTestExecutor = mContext.getMainExecutor();
    }

    @After
    public void tearDown() throws Exception {
        if (mBluetoothCsipSetCoordinator != null) {
            mBluetoothCsipSetCoordinator.close();
            mBluetoothCsipSetCoordinator = null;
            mIsProfileReady = false;
            mGroupLockCallbackCalled = false;
        }
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(
                BluetoothProfile.CSIP_SET_COORDINATOR, mBluetoothCsipSetCoordinator);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothCsipSetCoordinator.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothCsipSetCoordinator.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        int state = mBluetoothCsipSetCoordinator.getConnectionState(mTestDevice);
        assertEquals(BluetoothProfile.STATE_DISCONNECTED, state);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getGroupUuidMapByDevice() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothCsipSetCoordinator.getGroupUuidMapByDevice(mTestDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        assertThat(mBluetoothCsipSetCoordinator.getGroupUuidMapByDevice(mTestDevice)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void lockUnlockGroup() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        int groupId = 1;
        // Verify parameter
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothCsipSetCoordinator.lockGroup(groupId, null, mCallback));
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothCsipSetCoordinator.lockGroup(groupId, mTestExecutor, null));

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothCsipSetCoordinator.lockGroup(groupId, mTestExecutor, mCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Lock group
        boolean isLocked = false;
        int opStatus = BluetoothStatusCodes.ERROR_CSIP_INVALID_GROUP_ID;
        mBluetoothCsipSetCoordinator.lockGroup(groupId, mTestExecutor, mCallback);

        verify(mCallback, timeout(TEST_CALLBACK_TIMEOUT_MS))
                .onGroupLockSet(groupId, opStatus, isLocked);

        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        mBluetoothCsipSetCoordinator.unlockGroup(uuid);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllGroupIds() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothCsipSetCoordinator);

        TestUtils.dropPermissionAsShellUid();
        assertThrows(
                SecurityException.class,
                () -> mBluetoothCsipSetCoordinator.getAllGroupIds(BluetoothUuid.CAP));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mBluetoothCsipSetCoordinator.getAllGroupIds(null)).isEmpty();
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

    private final class BluetoothCsipServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothCsipSetCoordinator = (BluetoothCsipSetCoordinator) proxy;
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
