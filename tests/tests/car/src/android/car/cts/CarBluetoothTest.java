/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Contains the tests to prove compliance with android automotive specific bluetooth requirements.
 */
@RequireRunNotOnVisibleBackgroundNonProfileUser(
        reason =
                "No Bluetooth support on visible background users currently, so skipping tests for"
                        + " secondary_user_on_secondary_display.")
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get Bluetooth related permissions")
public final class CarBluetoothTest extends AbstractCarTestCase {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock BluetoothProfile.ServiceListener mServiceListener;

    private static final String TAG = CarBluetoothTest.class.getSimpleName();

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final BluetoothAdapter mBluetoothAdapter = BlockingBluetoothAdapter.getAdapter();
    // Configurable timeout for waiting for profile proxies to connect
    private static final int PROXY_CONNECTIONS_TIMEOUT_MS = 1000; // ms

    @BeforeClass
    public static void setUpOnce() throws Exception {
        // Make sure Bluetooth is enabled before the test
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @Before
    public void setUp() {
        Log.d(
                TAG,
                "Setting up Automotive Bluetooth test. Device is "
                        + (FeatureUtil.isAutomotive() ? "" : "not ")
                        + "automotive");
    }

    // [A-0-2] : Android Automotive devices must support Hands Free Profile (HFP) [Phone calling]
    @Test
    @CddTest(requirements = {"7.4.3/A-0-2"})
    public void verifySupportHfpClient() {
        assertThat(
                        mBluetoothAdapter.getProfileProxy(
                                mContext, mServiceListener, BluetoothProfile.HEADSET_CLIENT))
                .isTrue();

        verify(mServiceListener, timeout(PROXY_CONNECTIONS_TIMEOUT_MS))
                .onServiceConnected(eq(BluetoothProfile.HEADSET_CLIENT), notNull());
        verify(mServiceListener, never()).onServiceDisconnected(anyInt());
    }

    // [A-0-2] : Android Automotive devices must support Audio Distribution Profile (A2DP) [Media
    // playback]
    @Test
    @CddTest(requirements = {"7.4.3/A-0-2"})
    public void verifySupportA2dpSink() {
        assertThat(
                        mBluetoothAdapter.getProfileProxy(
                                mContext, mServiceListener, BluetoothProfile.A2DP_SINK))
                .isTrue();

        verify(mServiceListener, timeout(PROXY_CONNECTIONS_TIMEOUT_MS))
                .onServiceConnected(eq(BluetoothProfile.A2DP_SINK), notNull());
        verify(mServiceListener, never()).onServiceDisconnected(anyInt());
    }

    // [A-0-2] : Android Automotive devices must support Phone Book Access Profile (PBAP) [Contact
    // sharing/receiving]
    @Test
    @CddTest(requirements = {"7.4.3/A-0-2"})
    public void verifySupportPbapClient() {
        assertThat(
                        mBluetoothAdapter.getProfileProxy(
                                mContext, mServiceListener, BluetoothProfile.PBAP_CLIENT))
                .isTrue();

        verify(mServiceListener, timeout(PROXY_CONNECTIONS_TIMEOUT_MS))
                .onServiceConnected(eq(BluetoothProfile.PBAP_CLIENT), notNull());
        verify(mServiceListener, never()).onServiceDisconnected(anyInt());
    }

    // [A-0-2] : Android Automotive devices must support Audio/Video Remote Control Profile (AVRCP)
    // [Media playback control]
    @Test
    @CddTest(requirements = {"7.4.3/A-0-2"})
    public void verifySupportAvrcpController() {
        // There is no API exposing the BluetoothProfile.AVRCP_CONTROLLER. We can only validate the
        // configuration was correctly set
        assertThat(BluetoothProperties.isProfileAvrcpControllerEnabled().orElse(false)).isTrue();
    }
}
