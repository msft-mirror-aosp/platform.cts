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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHidHostTest {
    private static final String TAG = BluetoothHidHostTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothHidHost mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        assumeTrue(BluetoothProperties.isProfileHidHostEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HID_HOST))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HID_HOST), captor.capture());
        mService = (BluetoothHidHost) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HID_HOST));
    }

    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @Test
    public void getConnectionState() {
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(IllegalArgumentException.class, () -> mService.getConnectionState(null));
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void getConnectionPolicy() {
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(IllegalArgumentException.class, () -> mService.getConnectionPolicy(null));
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void setConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_UNKNOWN)).isFalse();
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.setConnectionPolicy(null, CONNECTION_POLICY_UNKNOWN));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP)
    @Test
    public void getPreferredTransportTest() {
        // Verify throws NullPointerException when null BluetoothDevice is used
        assertThrows(NullPointerException.class, () -> mService.getPreferredTransport(null));

        // Verify returns TRANSPORT_AUTO if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.getPreferredTransport(mDevice))
                .isEqualTo(BluetoothDevice.TRANSPORT_AUTO);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP)
    @Test
    public void setPreferredTransportTest() {
        // Verify that BLUETOOTH_PRIVILEGED permission is enforced
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class,
                () -> mService.setPreferredTransport(mDevice, BluetoothDevice.TRANSPORT_AUTO));

        // Verify that BLUETOOTH_CONNECT permission is enforced
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThrows(
                SecurityException.class,
                () -> mService.setPreferredTransport(mDevice, BluetoothDevice.TRANSPORT_AUTO));

        // Get required permissions
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Verify returns false when invalid input is given
        assertThat(mService.setPreferredTransport(mDevice, BluetoothDevice.TRANSPORT_AUTO))
                .isFalse();

        // Verify throws NullPointerException when null BluetoothDevice is used
        assertThrows(
                NullPointerException.class,
                () -> mService.setPreferredTransport(null, BluetoothDevice.TRANSPORT_AUTO));

        // Verify returns false if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.setPreferredTransport(mDevice, BluetoothDevice.TRANSPORT_AUTO))
                .isFalse();
    }
}
