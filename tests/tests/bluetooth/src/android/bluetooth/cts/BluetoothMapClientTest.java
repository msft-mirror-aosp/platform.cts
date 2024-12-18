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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothMapClientTest {
    private static final String TAG = BluetoothMapClientTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final String ACTION_MESSAGE_SENT_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest.MESSAGE_SENT_SUCCESSFULLY";
    private static final String ACTION_MESSAGE_DELIVERED_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest" + ".MESSAGE_DELIVERED_SUCCESSFULLY";

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothMapClient mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        assumeTrue(BluetoothProperties.isProfileMapClientEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.MAP_CLIENT))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.MAP_CLIENT), captor.capture());
        mService = (BluetoothMapClient) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.MAP_CLIENT));
    }

    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void getConnectionState() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionState(null)).isEqualTo(STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(new int[] {})).isEmpty();
    }

    @Test
    public void sendMessage() {
        Collection<Uri> contacts = new HashSet<Uri>();
        String message = "";
        PendingIntent sentIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_MESSAGE_SENT_SUCCESSFULLY),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deliveredIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_MESSAGE_DELIVERED_SUCCESSFULLY),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Verify returns false when invalid input device is given
        assertThat(mService.sendMessage(null, contacts, message, sentIntent, deliveredIntent))
                .isFalse();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.sendMessage(mDevice, contacts, message, sentIntent, deliveredIntent))
                .isFalse();
    }

    @Test
    public void setConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_UNKNOWN)).isFalse();
        assertThat(mService.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED)).isFalse();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isFalse();
    }
}
