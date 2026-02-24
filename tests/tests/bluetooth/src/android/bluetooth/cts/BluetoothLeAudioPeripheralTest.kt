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
package android.bluetooth.cts

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_PRIVILEGED
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothLeAudioPeripheral
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.bluetooth.test_utils.Permissions
import android.content.Context
import android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bluetooth.flags.Flags
import com.android.modules.utils.build.SdkLevel
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class BluetoothLeAudioPeripheralTest {
    @get:Rule val mFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mListener: BluetoothProfile.ServiceListener

    private val mAdapter: BluetoothAdapter =
        android.bluetooth.test_utils.BlockingBluetoothAdapter.adapter
    private val mDevice: BluetoothDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC")
    private val mContext: Context = InstrumentationRegistry.getInstrumentation().context
    private val mExecutor: Executor = mContext.mainExecutor

    private lateinit var mService: BluetoothLeAudioPeripheral

    companion object {
        private val PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500)
    }

    @Before
    fun setUp() {
        assumeTrue(SdkLevel.isAtLeastT())
        assumeTrue(mContext.packageManager.hasSystemFeature(FEATURE_BLUETOOTH_LE))
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()

        // Make sure we don't run on products having no TMAP roles defined
        Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED).use {
            assumeTrue(BluetoothProfile.LE_AUDIO_PERIPHERAL in mAdapter.getSupportedProfiles())
        }

        assertThat(
                mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.LE_AUDIO_PERIPHERAL)
            )
            .isTrue()
        val captor = ArgumentCaptor.forClass(BluetoothProfile::class.java)
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
            .onServiceConnected(eq(BluetoothProfile.LE_AUDIO_PERIPHERAL), captor.capture())
        mService = captor.value as BluetoothLeAudioPeripheral
        assertThat(mService).isNotNull()
    }

    @After
    fun tearDown() {
        Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED).use {
            if (BluetoothProfile.LE_AUDIO_PERIPHERAL in mAdapter.getSupportedProfiles()) {
                mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_PERIPHERAL, mService)
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun closeProfileProxy() {
        mService.close()
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
            .onServiceDisconnected(eq(BluetoothProfile.LE_AUDIO_PERIPHERAL))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun getConnectedDevices() {
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.connectedDevices
        }

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue()

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.connectedDevices).isEmpty()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun getDevicesMatchingConnectionStates() {
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.getDevicesMatchingConnectionStates(intArrayOf(STATE_CONNECTED))
        }

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue()

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun getConnectionState() {
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.getConnectionState(mDevice)
        }

        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionState(null)).isEqualTo(BluetoothProfile.STATE_DISCONNECTED)

        // Verify returns false if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue()
        assertThat(mService.getConnectionState(mDevice))
            .isEqualTo(BluetoothProfile.STATE_DISCONNECTED)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun registerUnregisterCallback() {
        val callback = mock<BluetoothLeAudioPeripheral.Callback>()
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.registerCallback(mExecutor, callback)
        }
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.unregisterCallback(callback)
        }

        Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED).use {
            // Verify parameter
            assertThrows(NullPointerException::class.java) {
                mService.registerCallback(null as Executor, callback)
            }
            assertThrows(NullPointerException::class.java) {
                mService.registerCallback(mExecutor, null as BluetoothLeAudioPeripheral.Callback)
            }
            assertThrows(NullPointerException::class.java) {
                mService.unregisterCallback(null as BluetoothLeAudioPeripheral.Callback)
            }

            // Test success register unregister
            mService.registerCallback(mExecutor, callback)
            mService.unregisterCallback(callback)
        }
    }

    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    @Suppress("DirectInvocationOnMock")
    fun fakeCallbackCoverage() {
        val callback = mock<BluetoothLeAudioPeripheral.Callback>()
        callback.onStreamTypesChanged(mDevice, 0)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_PERIPHERAL_FEATURE)
    fun setAndGetEnabledStreamTypes() {
        val testEnabledStreamTypes =
            BluetoothLeAudioPeripheral.STREAM_TYPE_MEDIA or
                BluetoothLeAudioPeripheral.STREAM_TYPE_CALL

        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.setStreamTypesEnabled(mDevice, testEnabledStreamTypes, true)
        }
        Permissions.enforce(listOf(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT)) {
            mService.getEnabledStreamTypes(mDevice)
        }

        Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED).use {
            // setStreamTypesEnabled should do nothing if CTS doesn't run with a compatible remote
            // device
            mService.setStreamTypesEnabled(mDevice, testEnabledStreamTypes, true)
            // getEnabledStreamTypes should return STREAM_TYPE_NONE if CTS doesn't run with a
            // compatible remote device
            assertThat(mService.getEnabledStreamTypes(mDevice))
                .isEqualTo(BluetoothLeAudioPeripheral.STREAM_TYPE_NONE)
        }
    }
}
