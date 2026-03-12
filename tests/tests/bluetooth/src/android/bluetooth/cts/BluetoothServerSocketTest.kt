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
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.content.pm.PackageManager.FEATURE_BLUETOOTH
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothServerSocketTest {

    private val adapter = BlockingBluetoothAdapter.adapter
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val uiAutomation = instrumentation.uiAutomation

    private lateinit var bluetoothServerSocket: BluetoothServerSocket
    private var hasBluetooth = false

    @Before
    fun setUp() {
        hasBluetooth = context.packageManager.hasSystemFeature(FEATURE_BLUETOOTH)
        assumeTrue(hasBluetooth)

        uiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT)
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
        bluetoothServerSocket = adapter.listenUsingL2capChannel()
    }

    @After
    fun tearDown() {
        if (!hasBluetooth) {
            return
        }
        uiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT)
        if (::bluetoothServerSocket.isInitialized) {
            bluetoothServerSocket.close()
        }
        uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun accept() {
        assertThrows(IOException::class.java) { bluetoothServerSocket.accept(SCAN_STOP_TIMEOUT) }
    }

    companion object {
        private const val SCAN_STOP_TIMEOUT = 1000
    }
}
