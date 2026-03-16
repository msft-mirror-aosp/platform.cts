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

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_PRIVILEGED
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED
import android.bluetooth.BluetoothStatusCodes.ERROR_TIMEOUT
import android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED
import android.bluetooth.le.DistanceMeasurementResult
import android.bluetooth.le.DistanceMeasurementSession
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class DistanceMeasurementSessionTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val adapter = BlockingBluetoothAdapter.adapter

    private val testCallback =
        object : DistanceMeasurementSession.Callback {
            override fun onStarted(session: DistanceMeasurementSession) {}

            override fun onStartFail(reason: Int) {}

            override fun onStopped(session: DistanceMeasurementSession, reason: Int) {}

            override fun onResult(device: BluetoothDevice, result: DistanceMeasurementResult) {}
        }

    @Before
    fun setUp() {
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU))
        Assume.assumeTrue(TestUtils.isBleSupported(context))
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)
        Assume.assumeTrue(adapter.isDistanceMeasurementSupported == FEATURE_SUPPORTED)
    }

    @After
    fun tearDown() {
        TestUtils.dropPermissionAsShellUid()
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @Test
    fun callbackMethods() {
        testCallback.onStarted(mock<DistanceMeasurementSession>())
        testCallback.onStartFail(ERROR_REMOTE_OPERATION_NOT_SUPPORTED)
        testCallback.onStopped(mock<DistanceMeasurementSession>(), ERROR_TIMEOUT)
        testCallback.onResult(mock<BluetoothDevice>(), mock<DistanceMeasurementResult>())
    }
}
