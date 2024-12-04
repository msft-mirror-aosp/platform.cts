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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAudioTest {
    @Rule public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private BluetoothLeAudio.Callback mCallback;

    private static final String TAG = BluetoothLeAudioTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private Context mContext;
    private BluetoothAdapter mAdapter;

    private BluetoothLeAudio mBluetoothLeAudio;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private Executor mTestExecutor;
    private BluetoothDevice mTestDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeAudio = null;

        Assume.assumeTrue(mAdapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);

        mAdapter.getProfileProxy(
                mContext, new BluetoothLeAudioServiceListener(), BluetoothProfile.LE_AUDIO);

        mTestExecutor = mContext.getMainExecutor();
    }

    @After
    public void tearDown() throws Exception {
        if (mBluetoothLeAudio != null) {
            mBluetoothLeAudio.close();
            mBluetoothLeAudio = null;
            mIsProfileReady = false;
        }
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void closeProfileProxy() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, mBluetoothLeAudio);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedDevices() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothLeAudio.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mBluetoothLeAudio.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectionState() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(STATE_DISCONNECTED, mBluetoothLeAudio.getConnectionState(null));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(STATE_DISCONNECTED, mBluetoothLeAudio.getConnectionState(mTestDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_LEAUDIO_MONO_LOCATION_ERRATA_API)
    public void getAudioLocation_Old() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothLeAudio.AUDIO_LOCATION_INVALID,
                mBluetoothLeAudio.getAudioLocation(mTestDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_MONO_LOCATION_ERRATA_API)
    public void getAudioLocation() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothLeAudio.AUDIO_LOCATION_UNKNOWN,
                mBluetoothLeAudio.getAudioLocation(mTestDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isInbandRingtoneEnabled() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                false,
                mBluetoothLeAudio.isInbandRingtoneEnabled(BluetoothLeAudio.GROUP_ID_INVALID));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setgetConnectionPolicy() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        assertThat(mBluetoothLeAudio.setConnectionPolicy(null, 0)).isFalse();
        assertEquals(CONNECTION_POLICY_FORBIDDEN, mBluetoothLeAudio.getConnectionPolicy(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeAudio.registerCallback(mTestExecutor, mCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerUnregisterCallback() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        // Verify parameter
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothLeAudio.registerCallback(null, mCallback));
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothLeAudio.registerCallback(mTestExecutor, null));
        assertThrows(NullPointerException.class, () -> mBluetoothLeAudio.unregisterCallback(null));

        // Test success register unregister
        mBluetoothLeAudio.registerCallback(mTestExecutor, mCallback);
        mBluetoothLeAudio.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedGroupLeadDevice() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();

        int groupId = 1;

        // Verify returns null for unknown group id
        assertEquals(null, mBluetoothLeAudio.getConnectedGroupLeadDevice(groupId));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setVolume() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        mBluetoothLeAudio.setVolume(42);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getCodecStatus() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        assertNull(mBluetoothLeAudio.getCodecStatus(0));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setCodecConfigPreference() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        BluetoothLeAudioCodecConfig codecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                        .setCodecPriority(0)
                        .build();

        assertThrows(
                NullPointerException.class,
                () -> {
                    mBluetoothLeAudio.setCodecConfigPreference(0, null, null);
                });

        mBluetoothLeAudio.setCodecConfigPreference(0, codecConfig, codecConfig);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1"})
    @Test
    public void getGroupId() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        try {
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
            mBluetoothLeAudio.getGroupId(device);
        } finally {
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_BROADCAST_API_MANAGE_PRIMARY_GROUP)
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void broadcastToUnicastFallbackGroup() {
        assertThat(waitForProfileConnect()).isTrue();
        assertNotNull(mBluetoothLeAudio);

        int groupId = 1;

        Permissions.enforceEachPermissions(
                () -> {
                    mBluetoothLeAudio.setBroadcastToUnicastFallbackGroup(groupId);
                    return null;
                },
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        Permissions.enforceEachPermissions(
                () -> mBluetoothLeAudio.getBroadcastToUnicastFallbackGroup(),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            mBluetoothLeAudio.setBroadcastToUnicastFallbackGroup(groupId);

            /* There is no such group - verify if it's not updated */
            assertThat(mBluetoothLeAudio.getBroadcastToUnicastFallbackGroup())
                    .isEqualTo(BluetoothLeAudio.GROUP_ID_INVALID);
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

    private final class BluetoothLeAudioServiceListener
            implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothLeAudio = (BluetoothLeAudio) proxy;
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
