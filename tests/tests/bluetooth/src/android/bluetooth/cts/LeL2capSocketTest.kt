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
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.bluetooth.test_utils.Permissions
import android.bluetooth.test_utils.TestUtils.isBleSupported
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class LeL2capSocketTest {

    private val adapter = BlockingBluetoothAdapter.adapter

    @Before
    fun setUp() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU))
        assumeTrue(isBleSupported(InstrumentationRegistry.getInstrumentation().context))

        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @Test
    fun openInsecureLeL2capServerSocket() {
        assertThrows(SecurityException::class.java) { adapter.listenUsingInsecureL2capChannel() }
        val serverSocket =
            Permissions.withPermissions(BLUETOOTH_CONNECT).use {
                adapter.listenUsingInsecureL2capChannel()
            }
        assertThat(serverSocket).isNotNull()
        serverSocket.close()
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @Test
    fun openSecureLeL2capServerSocket() {
        assertThrows(SecurityException::class.java) { adapter.listenUsingL2capChannel() }
        val serverSocket =
            Permissions.withPermissions(BLUETOOTH_CONNECT).use { adapter.listenUsingL2capChannel() }
        assertThat(serverSocket).isNotNull()
        serverSocket.close()
    }
}
