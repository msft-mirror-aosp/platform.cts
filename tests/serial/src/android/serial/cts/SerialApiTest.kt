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
package android.serial.cts

import android.content.Context
import android.hardware.serial.SerialManager
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortListener
import android.hardware.serial.SerialPortResponse
import android.os.OutcomeReceiver
import android.os.Process
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A simple compatibility test for Serial API.
 *
 * Note: most of the tests below test nothing if the Android device doesn't support PTY ports.
 *
 * atest CtsSerialTestCases
 */
@RunWith(AndroidJUnit4::class)
class SerialApiTest {
    @get:Rule val createCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()!!

    private var mContext: Context? = null
    private var mSerialManager: SerialManager? = null

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        mSerialManager = mContext!!.getSystemService(SerialManager::class.java)

        // Clear previous user access choices
        SystemUtil.runShellCommand("cmd serial clear-user-access")

        // Clear any dialogs, e.g. "Allow SerialApiTest to access USB serial port?"
        val uiDevice = UiAutomatorUtils2.getUiDevice()
        uiDevice.pressHome()
        uiDevice.pressBack()
    }

    @After
    fun tearDown() {
        setExposePty(false)
    }

    @ApiTest(apis = ["android.hardware.serial.SerialManager#getPorts"])
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_getPorts_isEmpty() {
        val ports = mSerialManager!!.getPorts()

        assertThat(ports).isEmpty()
    }

    @ApiTest(apis = ["android.hardware.serial.SerialManager#getPorts"])
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_getPorts_hasPtyPorts() {
        setExposePty(true)
        val ports = mSerialManager!!.getPorts()

        for (port in ports) {
            assertThat(port.name == "ptmx" || port.name.startsWith("pts/")).isTrue()
        }
    }

    @ApiTest(
        apis = [
            "android.hardware.serial.SerialManager#getPorts",
            "android.hardware.serial.SerialManager#registerSerialPortListener",
            "android.hardware.serial.SerialPort#requestOpen",
        ]
    )
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_registerSerialPortListener() {
        setExposePty(true)
        val ports = mSerialManager!!.getPorts()
        val ptyPort = ports.find { it.name == "ptmx" } ?: return
        val flags = SerialPort.OPEN_FLAG_READ_WRITE
        val exclusive = true
        val outcomeReceiver: OutcomeReceiver<SerialPortResponse, Exception> = mock()
        val listener: SerialPortListener = mock()
        val latch = CountDownLatch(2) // Wait for onSerialPortConnected and onResult
        doAnswer { latch.countDown() }.whenever(listener).onSerialPortConnected(any())
        doAnswer { latch.countDown() }.whenever(outcomeReceiver).onResult(any())
        runWithShellPermissionIdentity {
            mSerialManager!!.grantSerialPortAccess("ptmx", Process.myUid(), false, null)
        }

        // Opening PTY port /dev/ptmx causes creation of a new PTY device under /dev/pts
        // which should trigger the listener.
        // See https://man7.org/linux/man-pages/man4/ptmx.4.html
        mSerialManager!!.registerSerialPortListener(directExecutor(), listener)
        try {
            ptyPort.requestOpen(flags, exclusive, directExecutor(), outcomeReceiver)

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
            val serialPort = argumentCaptor<SerialPort>()
            verify(listener).onSerialPortConnected(serialPort.capture())
            with(serialPort.firstValue) {
                assertThat(name).startsWith("pts/")
                assertThat(vendorId).isEqualTo(SerialPort.INVALID_ID)
                assertThat(productId).isEqualTo(SerialPort.INVALID_ID)
            }
            verify(listener, never()).onSerialPortDisconnected(any())
        } finally {
            // cleanup: close the /dev/ptmx port
            val serialPortResponse = argumentCaptor<SerialPortResponse>()
            verify(outcomeReceiver).onResult(serialPortResponse.capture())
            serialPortResponse.firstValue?.fileDescriptor?.close()
        }
    }

    @ApiTest(
        apis = [
            "android.hardware.serial.SerialManager#getPorts",
            "android.hardware.serial.SerialManager#registerSerialPortListener",
            "android.hardware.serial.SerialManager#unregisterSerialPortListener",
            "android.hardware.serial.SerialPort#requestOpen",
        ]
    )
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_unregisterSerialPortListener() {
        setExposePty(true)
        val ports = mSerialManager!!.getPorts()
        val ptyPort = ports.find { it.name == "ptmx" } ?: return
        val flags = SerialPort.OPEN_FLAG_READ_WRITE
        val exclusive = true
        val outcomeReceiver: OutcomeReceiver<SerialPortResponse, Exception> = mock()
        val listener: SerialPortListener = mock()
        val latch = CountDownLatch(1) // Wait for onResult
        doAnswer { latch.countDown() }.whenever(outcomeReceiver).onResult(any())
        runWithShellPermissionIdentity {
            mSerialManager!!.grantSerialPortAccess("ptmx", Process.myUid(), false, null)
        }

        // Same logic as in test_registerSerialPortListener(), but now we test that
        // unregisterSerialPortListener() really unregisters the listener.
        mSerialManager!!.registerSerialPortListener(directExecutor(), listener)
        mSerialManager!!.unregisterSerialPortListener(listener)
        try {
            ptyPort.requestOpen(flags, exclusive, directExecutor(), outcomeReceiver)

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
            verify(listener, never()).onSerialPortConnected(any())
            verify(listener, never()).onSerialPortDisconnected(any())
        } finally {
            // cleanup: close the /dev/ptmx port
            val serialPortResponse = argumentCaptor<SerialPortResponse>()
            verify(outcomeReceiver).onResult(serialPortResponse.capture())
            serialPortResponse.firstValue?.fileDescriptor?.close()
        }
    }

    @ApiTest(
        apis = [
            "android.hardware.serial.SerialManager#getPorts",
            "android.hardware.serial.SerialPort#requestOpen",
        ]
    )
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_requestOpen_accessGranted() {
        setExposePty(true)
        val ports = mSerialManager!!.getPorts()
        val ptyPort = ports.find { it.name == "ptmx" } ?: return
        val flags = SerialPort.OPEN_FLAG_READ_WRITE
        val exclusive = true
        val outcomeReceiver: OutcomeReceiver<SerialPortResponse, Exception> = mock()
        var serialPortResponse: SerialPortResponse? = null
        val latch = CountDownLatch(1) // Wait for onResult
        doAnswer { invocation ->
            serialPortResponse = invocation.getArgument(0)
            latch.countDown()
        }.whenever(outcomeReceiver).onResult(any())
        runWithShellPermissionIdentity {
            mSerialManager!!.grantSerialPortAccess("ptmx", Process.myUid(), false, null)
        }

        try {
            ptyPort.requestOpen(flags, exclusive, directExecutor(), outcomeReceiver)

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
            with(serialPortResponse!!) {
                assertThat(port!!.name).isEqualTo("ptmx")
                assertThat(fileDescriptor!!.getFileDescriptor()!!.valid()).isTrue()
            }
            verify(outcomeReceiver, never()).onError(any())
        } finally {
            // cleanup: close the /dev/ptmx port
            serialPortResponse?.fileDescriptor?.close()
        }
    }

    @ApiTest(
        apis = [
            "android.hardware.serial.SerialManager#getPorts",
            "android.hardware.serial.SerialPort#requestOpen",
        ]
    )
    @Test
    @RequiresFlagsEnabled(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
    fun test_requestOpen_accessDenied() {
        setExposePty(true)
        val ports = mSerialManager!!.getPorts()
        val ptyPort = ports.find { it.name == "ptmx" } ?: return
        val flags = SerialPort.OPEN_FLAG_READ_WRITE
        val exclusive = true
        val outcomeReceiver: OutcomeReceiver<SerialPortResponse, Exception> = mock()
        val latch = CountDownLatch(1) // Wait for onError
        doAnswer { latch.countDown() }.whenever(outcomeReceiver).onError(any())

        ptyPort.requestOpen(flags, exclusive, directExecutor(), outcomeReceiver)
        // A permission request dialog is shown when trying to open a port.
        // We deny the access request by clicking the Back button.
        UiAutomatorUtils2.getUiDevice().pressBack()

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        verify(outcomeReceiver, never()).onResult(any())
        val exception = argumentCaptor<Exception>()
        verify(outcomeReceiver).onError(exception.capture())
        assertThat(exception.firstValue).isInstanceOf(SecurityException::class.java)
    }

    private fun setExposePty(b: Boolean) {
        SystemUtil.runShellCommand("cmd serial ${if (b) "expose-pty" else "hide-pty"}")
    }
}
