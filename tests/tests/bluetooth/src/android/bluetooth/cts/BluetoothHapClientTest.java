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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.os.Build;

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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHapClientTest {
    private static final String TAG = BluetoothHapClientTest.class.getSimpleName();

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final BluetoothAdapter sAdapter = BlockingBluetoothAdapter.getAdapter();
    private static final BluetoothDevice sTestDevice =
            sAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

    private BluetoothHapClient mService;

    private boolean mOnPresetSelected = false;
    private boolean mOnPresetSelectionFailed = false;
    private boolean mOnPresetSelectionForGroupFailed = false;
    private boolean mOnPresetInfoChanged = false;
    private boolean mOnSetPresetNameFailed = false;
    private boolean mOnSetPresetNameForGroupFailed = false;

    private CountDownLatch mCallbackCountDownLatch;
    private List<BluetoothHapPresetInfo> mPresetInfoList = new ArrayList();

    private static final int TEST_REASON_CODE = BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST;
    private static final int TEST_PRESET_INDEX = 13;
    private static final int TEST_STATUS_CODE = BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX;
    private static final int TEST_HAP_GROUP_ID = 65;

    @Mock BluetoothProfile.ServiceListener mServiceListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(sContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HAP_CLIENT));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(
                        sAdapter.getProfileProxy(
                                sContext, mServiceListener, BluetoothProfile.HAP_CLIENT))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mServiceListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HAP_CLIENT), captor.capture());
        mService = (BluetoothHapClient) captor.getValue();
        assertThat(mService).isNotNull();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        sAdapter.closeProfileProxy(BluetoothProfile.HAP_CLIENT, mService);
        verify(mServiceListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HAP_CLIENT));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mService.getConnectedDevices();
        assertThat(connectedDevices.isEmpty()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mService.getDevicesMatchingConnectionStates(null);
        assertThat(connectedDevices.isEmpty()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThat(mService.getConnectionState(null))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getConnectionState(sTestDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Verify getHapGroup() return -1 if Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHapGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getHapGroup(sTestDevice)).isEqualTo(-1);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetIndex() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns 0 if bluetooth is not enabled
        assertThat(mService.getActivePresetIndex(sTestDevice)).isEqualTo(0);
    }

    /** Verify getActivePresetInfo() return null if Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getActivePresetInfo(sTestDevice)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.selectPreset(sTestDevice, 1);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.selectPresetForGroup(1, 1);
    }

    /** Verify switchToNextPreset() will not cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToNextPreset(sTestDevice);
    }

    /** Verify switchToNextPresetForGroup() will not cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToNextPresetForGroup(1);
    }

    /** Verify switchToPreviousPreset() doesn't cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToPreviousPreset(sTestDevice);
    }

    /**
     * Verify switchToPreviousPresetForGroup() doesn't cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToPreviousPresetForGroup(1);
    }

    /** Verify switchToNextPresetForGroup() doesn't cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getPresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns null if bluetooth is not enabled
        assertThat(mService.getPresetInfo(sTestDevice, 1)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllPresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothHapPresetInfo> presets = mService.getAllPresetInfo(sTestDevice);
        assertThat(presets.isEmpty()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetName() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.setPresetName(sTestDevice, 1, "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetNameForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.setPresetNameForGroup(1, 1, "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setGetConnectionPolicy() {
        assertThrows(NullPointerException.class, () -> mService.setConnectionPolicy(null, 0));
        assertThat(mService.getConnectionPolicy(null))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        assertThat(
                        mService.setConnectionPolicy(
                                sTestDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN))
                .isTrue();

        TestUtils.dropPermissionAsShellUid();
        assertThrows(
                SecurityException.class,
                () ->
                        mService.setConnectionPolicy(
                                sTestDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class, () -> mService.getConnectionPolicy(sTestDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        Executor executor = sContext.getMainExecutor();

        BluetoothHapClient.Callback callback =
                new BluetoothHapClient.Callback() {
                    @Override
                    public void onPresetSelected(
                            BluetoothDevice device, int presetIndex, int reasonCode) {}

                    @Override
                    public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {}

                    @Override
                    public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {}

                    @Override
                    public void onPresetInfoChanged(
                            BluetoothDevice device,
                            List<BluetoothHapPresetInfo> presetInfoList,
                            int statusCode) {}

                    @Override
                    public void onSetPresetNameFailed(BluetoothDevice device, int status) {}

                    @Override
                    public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {}
                };

        // Verify parameter
        assertThrows(NullPointerException.class, () -> mService.registerCallback(null, callback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(executor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Verify valid parameters
        mService.registerCallback(executor, callback);
        mService.unregisterCallback(callback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.registerCallback(executor, callback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        Executor executor = sContext.getMainExecutor();

        BluetoothHapClient.Callback callback =
                new BluetoothHapClient.Callback() {
                    @Override
                    public void onPresetSelected(
                            BluetoothDevice device, int presetIndex, int reasonCode) {}

                    @Override
                    public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {}

                    @Override
                    public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {}

                    @Override
                    public void onPresetInfoChanged(
                            BluetoothDevice device,
                            List<BluetoothHapPresetInfo> presetInfoList,
                            int statusCode) {}

                    @Override
                    public void onSetPresetNameFailed(BluetoothDevice device, int status) {}

                    @Override
                    public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {}
                };

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.registerCallback(executor, callback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void callbackCalls() {
        BluetoothHapClient.Callback callback =
                new BluetoothHapClient.Callback() {
                    @Override
                    public void onPresetSelected(
                            BluetoothDevice device, int presetIndex, int reasonCode) {
                        mOnPresetSelected = true;
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {
                        mOnPresetSelectionFailed = true;
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {
                        mOnPresetSelectionForGroupFailed = true;
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onPresetInfoChanged(
                            BluetoothDevice device,
                            List<BluetoothHapPresetInfo> presetInfoList,
                            int statusCode) {
                        mOnPresetInfoChanged = true;
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onSetPresetNameFailed(BluetoothDevice device, int status) {
                        mOnSetPresetNameFailed = true;
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {
                        mOnSetPresetNameForGroupFailed = true;
                        mCallbackCountDownLatch.countDown();
                    }
                };

        mCallbackCountDownLatch = new CountDownLatch(6);
        try {
            callback.onPresetSelected(sTestDevice, TEST_PRESET_INDEX, TEST_REASON_CODE);
            callback.onPresetSelectionFailed(sTestDevice, TEST_STATUS_CODE);
            callback.onPresetSelectionForGroupFailed(TEST_HAP_GROUP_ID, TEST_STATUS_CODE);
            callback.onPresetInfoChanged(sTestDevice, mPresetInfoList, TEST_STATUS_CODE);
            callback.onSetPresetNameFailed(sTestDevice, TEST_STATUS_CODE);
            callback.onSetPresetNameForGroupFailed(TEST_HAP_GROUP_ID, TEST_STATUS_CODE);

            // Wait for all the callback calls or 5 seconds to verify
            mCallbackCountDownLatch.await(5, TimeUnit.SECONDS);
            assertThat(mOnPresetSelected).isTrue();
            assertThat(mOnPresetSelectionFailed).isTrue();
            assertThat(mOnPresetSelectionForGroupFailed).isTrue();
            assertThat(mOnPresetInfoChanged).isTrue();
            assertThat(mOnSetPresetNameFailed).isTrue();
            assertThat(mOnSetPresetNameForGroupFailed).isTrue();
        } catch (InterruptedException e) {
            fail("Failed to register callback call: " + e.toString());
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHearingAidType() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns 0x00 if bluetooth is not enabled
        assertThat(mService.getHearingAidType(sTestDevice)).isEqualTo(0x00);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsSynchronizedPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsSynchronizedPresets(sTestDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsIndependentPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsIndependentPresets(sTestDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsDynamicPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsDynamicPresets(sTestDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsWritablePresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsWritablePresets(sTestDevice)).isFalse();
    }
}
