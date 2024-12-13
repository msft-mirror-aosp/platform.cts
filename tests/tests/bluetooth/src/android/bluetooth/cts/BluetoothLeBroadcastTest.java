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
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastTest {
    private static final String TAG = BluetoothLeBroadcastTest.class.getSimpleName();

    private static final int BROADCAST_CALLBACK_TIMEOUT_MS = 500;
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final String TEST_BROADCAST_NAME = "TEST";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;
    private static final int TEST_AUDIO_QUALITY_STANDARD = 0x1 << 0;

    private static final int TEST_CODEC_ID = 42;
    private static final int TEST_CHANNEL_INDEX = 56;

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final long TEST_AUDIO_LOCATION_FRONT_RIGHT = 0x02;
    private static final int TEST_SAMPLE_RATE_16000 = 0x01 << 2;
    private static final int TEST_FRAME_DURATION_7500 = 0x01 << 0;
    private static final int TEST_OCTETS_PER_FRAME = 200;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";
    private static final int TEST_QUALITY = BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD;

    private static final int TEST_REASON = BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST;

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private Executor mExecutor;

    private BluetoothLeBroadcast mBluetoothLeBroadcast;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    private boolean mOnBroadcastStartedCalled = false;
    private boolean mOnBroadcastStartFailedCalled = false;
    private boolean mOnBroadcastStoppedCalled = false;
    private boolean mOnBroadcastStopFailedCalled = false;
    private boolean mOnPlaybackStartedCalled = false;
    private boolean mOnPlaybackStoppedCalled = false;
    private boolean mOnBroadcastUpdatedCalled = false;
    private boolean mOnBroadcastUpdateFailedCalled = false;
    private boolean mOnBroadcastMetadataChangedCalled = false;

    private BluetoothLeBroadcastMetadata mTestMetadata;
    private CountDownLatch mCallbackCountDownLatch;
    private @Captor ArgumentCaptor<Integer> mBroadcastId;

    @Mock BluetoothLeBroadcast.Callback mCallback;

    BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT)
                        .setSampleRate(TEST_SAMPLE_RATE_16000)
                        .setFrameDuration(TEST_FRAME_DURATION_7500)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .setLanguage(TEST_LANGUAGE)
                        .build();
        BluetoothLeBroadcastSubgroup.Builder builder =
                new BluetoothLeBroadcastSubgroup.Builder()
                        .setCodecId(TEST_CODEC_ID)
                        .setCodecSpecificConfig(codecMetadata)
                        .setContentMetadata(contentMetadata);

        BluetoothLeAudioCodecConfigMetadata channelCodecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_RIGHT)
                        .setSampleRate(TEST_SAMPLE_RATE_16000)
                        .setFrameDuration(TEST_FRAME_DURATION_7500)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();

        // builder expect at least one channel
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setSelected(true)
                        .setChannelIndex(TEST_CHANNEL_INDEX)
                        .setCodecMetadata(channelCodecMetadata)
                        .build();
        builder.addChannel(channel);
        return builder.build();
    }

    BluetoothLeBroadcastMetadata createBroadcastMetadata() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .build();

        BluetoothLeBroadcastMetadata.Builder builder =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS)
                        .setAudioConfigQuality(TEST_AUDIO_QUALITY_STANDARD)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata);
        // builder expect at least one subgroup
        builder.addSubgroup(createBroadcastSubgroup());
        return builder.build();
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        MockitoAnnotations.initMocks(this);
        mExecutor = mContext.getMainExecutor();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeBroadcast = null;

        Assume.assumeTrue(mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED);
        assertThat(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST)).isTrue();

        assertThat(
                        mAdapter.getProfileProxy(
                                mContext,
                                new ServiceListener(),
                                BluetoothProfile.LE_AUDIO_BROADCAST))
                .isTrue();
    }

    @After
    public void tearDown() {
        if (mBluetoothLeBroadcast != null) {
            mBluetoothLeBroadcast.close();
            mBluetoothLeBroadcast = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();
        assertThat(mIsProfileReady).isTrue();

        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST, mBluetoothLeBroadcast);
        assertThat(waitForProfileDisconnect()).isTrue();
        assertThat(mIsProfileReady).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(
                UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getConnectedDevices());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(
                UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getDevicesMatchingConnectionStates(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(
                UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getConnectionState(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        Executor executor = mContext.getMainExecutor();

        // Verify invalid parameters
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothLeBroadcast.registerCallback(null, mCallback));
        assertThrows(
                NullPointerException.class,
                () -> mBluetoothLeBroadcast.registerCallback(executor, null));
        assertThrows(
                NullPointerException.class, () -> mBluetoothLeBroadcast.unregisterCallback(null));

        // Verify valid parameters
        mBluetoothLeBroadcast.registerCallback(executor, mCallback);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        Executor executor = mContext.getMainExecutor();
        BluetoothLeBroadcast.Callback callback =
                new BluetoothLeBroadcast.Callback() {
                    @Override
                    public void onBroadcastStarted(int reason, int broadcastId) {}

                    @Override
                    public void onBroadcastStartFailed(int reason) {}

                    @Override
                    public void onBroadcastStopped(int reason, int broadcastId) {}

                    @Override
                    public void onBroadcastStopFailed(int reason) {}

                    @Override
                    public void onPlaybackStarted(int reason, int broadcastId) {}

                    @Override
                    public void onPlaybackStopped(int reason, int broadcastId) {}

                    @Override
                    public void onBroadcastUpdated(int reason, int broadcastId) {}

                    @Override
                    public void onBroadcastUpdateFailed(int reason, int broadcastId) {}

                    @Override
                    public void onBroadcastMetadataChanged(
                            int broadcastId, BluetoothLeBroadcastMetadata metadata) {}
                };

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.registerCallback(executor, callback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcast() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        byte[] broadcastCode = {1, 2, 3, 4, 5, 6};

        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () ->
                        mBluetoothLeBroadcast.startBroadcast(
                                contentMetadataBuilder.build(), broadcastCode));
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        mBluetoothLeBroadcast.startBroadcast(contentMetadataBuilder.build(), broadcastCode);
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT_MS))
                .onBroadcastStarted(
                        eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), mBroadcastId.capture());

        mBluetoothLeBroadcast.stopBroadcast(mBroadcastId.getValue());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT_MS))
                .onBroadcastStopped(eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), anyInt());

        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        byte[] broadcastCode = {1, 2, 3, 4, 5, 6};
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () ->
                        mBluetoothLeBroadcast.startBroadcast(
                                contentMetadataBuilder.build(), broadcastCode));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastGroup() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mBluetoothLeBroadcast.startBroadcast(broadcastSettingsBuilder.build()));
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        mBluetoothLeBroadcast.startBroadcast(broadcastSettingsBuilder.build());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT_MS))
                .onBroadcastStarted(
                        eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), mBroadcastId.capture());

        mBluetoothLeBroadcast.stopBroadcast(mBroadcastId.getValue());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT_MS))
                .onBroadcastStopped(eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), anyInt());
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastGroupWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.startBroadcast(broadcastSettingsBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcast() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mBluetoothLeBroadcast.updateBroadcast(1, contentMetadataBuilder.build()));
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        mBluetoothLeBroadcast.updateBroadcast(1, contentMetadataBuilder.build());
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.updateBroadcast(1, contentMetadataBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastGroup() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mBluetoothLeBroadcast.updateBroadcast(1, broadcastSettingsBuilder.build()));
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        mBluetoothLeBroadcast.updateBroadcast(1, broadcastSettingsBuilder.build());
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastGroupWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.updateBroadcast(1, broadcastSettingsBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void stopBroadcast() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        // Verifies that it throws exception when no callback is registered
        assertThrows(IllegalStateException.class, () -> mBluetoothLeBroadcast.stopBroadcast(1));
        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        mBluetoothLeBroadcast.stopBroadcast(1);

        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void stopBroadcastWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        mBluetoothLeBroadcast.registerCallback(mExecutor, mCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mBluetoothLeBroadcast.stopBroadcast(1));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mBluetoothLeBroadcast.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void isPlaying() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        assertThat(mBluetoothLeBroadcast.isPlaying(1)).isFalse();
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void isPlayingWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mBluetoothLeBroadcast.isPlaying(1));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllBroadcastMetadata() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        assertThat(mBluetoothLeBroadcast.getAllBroadcastMetadata()).isEmpty();
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllBroadcastMetadataWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mBluetoothLeBroadcast.getAllBroadcastMetadata());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumNumberOfBroadcasts() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        assertThat(mBluetoothLeBroadcast.getMaximumNumberOfBroadcasts()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumNumberOfBroadcastsWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.getMaximumNumberOfBroadcasts());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumStreamsPerBroadcast() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        assertThat(mBluetoothLeBroadcast.getMaximumStreamsPerBroadcast()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumStreamsPerBroadcastWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.getMaximumStreamsPerBroadcast());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumSubgroupsPerBroadcast() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        assertThat(mBluetoothLeBroadcast.getMaximumSubgroupsPerBroadcast()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumSubgroupsPerBroadcastWithoutPrivilegedPermission() {
        assertThat(waitForProfileConnect()).isTrue();
        assertThat(mBluetoothLeBroadcast).isNotNull();

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mBluetoothLeBroadcast.getMaximumSubgroupsPerBroadcast());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
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

    private final class ServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothLeBroadcast = (BluetoothLeBroadcast) proxy;
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

    static BluetoothLeBroadcastSubgroupSettings createBroadcastSubgroupSettings() {
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .setLanguage(TEST_LANGUAGE)
                        .build();
        BluetoothLeBroadcastSubgroupSettings.Builder builder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                        .setPreferredQuality(TEST_QUALITY)
                        .setContentMetadata(contentMetadata);
        return builder.build();
    }
}
