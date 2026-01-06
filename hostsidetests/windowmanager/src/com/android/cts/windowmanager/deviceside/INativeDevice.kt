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

package com.android.cts.windowmanager.deviceside

import com.android.tradefed.device.DeviceUnresponsiveException
import com.android.tradefed.device.IManagedTestDevice
import com.android.tradefed.device.INativeDevice
import com.android.tradefed.log.LogUtil.CLog
import org.junit.Assert.assertEquals

const val DEVICE_NOT_AVAILABLE_TIMEOUT = 20000L

/**
 * A minimalistic version of [INativeDevice.reboot] to invoke the reboot sequence in system services
 * without the root access. It is critical to the proper shutdown sequence in system services.
 */
fun INativeDevice.frameworkReboot() {
    try {
        val rebootResult =
            executeAdbV2Command(*"shell svc power reboot".split(" ").toTypedArray())
        assertEquals(
            "Failed to framework reboot device: ${rebootResult.stderr}",
            "",
            rebootResult.stderr
        )
    } catch (e: DeviceUnresponsiveException) {
        // Tradefed throws IOException if an ADB shell command fails. This particular command is
        // written not to return until the system server process dies. Therefore it is possible that
        // the device still reboots when the command itself fails. When all retry attempts are
        // exhausted, Tradefed throws a DeviceUnresponsiveException. See b/472678103.
        CLog.w(
            "Reboot adb command failed, but the device may still reboot. If someone suspects any " +
                    "issues, please check logcat."
        )
        CLog.w(e)
    }
    if (this is IManagedTestDevice) {
        if (!monitor.waitForDeviceNotAvailable(DEVICE_NOT_AVAILABLE_TIMEOUT)) {
            CLog.w(
                "Didn't detect device %s becoming unavailable after framework reboot within " +
                    "%dms",
                serialNumber,
                DEVICE_NOT_AVAILABLE_TIMEOUT
            )
        }
    } else {
        CLog.w(
            "Device %s isn't a IManagedTestDevice. Can't wait until it's unavailable.",
            serialNumber
        )
    }
    connection.reconnect(serialNumber)
    waitForDeviceAvailable()
}
