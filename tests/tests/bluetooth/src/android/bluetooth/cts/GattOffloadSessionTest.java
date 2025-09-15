/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.GattOffloadSession;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class GattOffloadSessionTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Mock private GattOffloadSession mGattOffloadSession;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GATT_OFFLOAD_API)
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeGattOffloadSessionCoverage() {
        mGattOffloadSession.getSessionId();
        mGattOffloadSession.getGattService();
        mGattOffloadSession.getGattCharacteristics();
        mGattOffloadSession.getEndpointId();
        mGattOffloadSession.getHubId();
        mGattOffloadSession.close();
    }
}
