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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothProfile;
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
import java.util.concurrent.Executor;

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
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThat(mService.getConnectionState(null)).isEqualTo(STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getConnectionState(sTestDevice)).isEqualTo(STATE_DISCONNECTED);
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
        assertThat(mService.getAllPresetInfo(sTestDevice)).isEmpty();
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
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        assertThat(mService.setConnectionPolicy(sTestDevice, CONNECTION_POLICY_FORBIDDEN)).isTrue();

        TestUtils.dropPermissionAsShellUid();
        assertThrows(
                SecurityException.class,
                () -> mService.setConnectionPolicy(sTestDevice, CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class, () -> mService.getConnectionPolicy(sTestDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        Executor executor = sContext.getMainExecutor();

        BluetoothHapClient.Callback mockCallback = mock(BluetoothHapClient.Callback.class);

        // Verify parameter
        assertThrows(
                NullPointerException.class, () -> mService.registerCallback(null, mockCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(executor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Verify valid parameters
        mService.registerCallback(executor, mockCallback);
        mService.unregisterCallback(mockCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.registerCallback(executor, mockCallback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        BluetoothHapClient.Callback mockCallback = mock(BluetoothHapClient.Callback.class);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.registerCallback(sContext.getMainExecutor(), mockCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
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
