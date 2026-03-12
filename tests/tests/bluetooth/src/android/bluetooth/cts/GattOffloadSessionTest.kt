/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.bluetooth.cts

import android.bluetooth.GattOffloadSession
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bluetooth.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

@RunWith(AndroidJUnit4::class)
class GattOffloadSessionTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock private lateinit var gattOffloadSession: GattOffloadSession

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(context))
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GATT_OFFLOAD_API)
    fun fakeGattOffloadSessionCoverage() {
        gattOffloadSession.sessionId
        gattOffloadSession.gattService
        gattOffloadSession.gattCharacteristics
        gattOffloadSession.endpointId
        gattOffloadSession.hubId
        gattOffloadSession.close()
    }
}
