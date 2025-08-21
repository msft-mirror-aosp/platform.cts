/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.input

import android.os.SystemClock
import android.util.Log
import com.android.compatibility.common.util.SystemUtil
import org.junit.AssumptionViolatedException
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A test rule that allows for additional debugging of the input pipeline to be enabled for tests.
 *
 * To enable additional debugging, add this test rule to the test class and annotate the desired
 * test methods with [DebugInput].
 *
 * Note: This will only work on debuggable builds (e.g. eng, userdebug).
 */
class DebugInputRule : TestWatcher() {

    companion object {
        private val TAG = "DebugInput"

        // The list of log tags to enable when additional debugging of the input pipeline is
        // required. These are a special set of log tags that can be dynamically toggled on
        // debuggable builds.
        private val debugInputTags = listOf(
                "InputReaderRawEvents",
                "InputDispatcherInboundEvent",
                "InputTransportPublisher",
                "InputTransportResampling",
                "InputManager",
                "InputManagerGlobal",
                "TouchpadInputMapperGestures",
        )

        @JvmStatic
        fun dumpInputStateToLogcat() {
            logShellCommand("dumpsys input")
        }

        /**
         * For tests checking the functionality of Sony devices like lights/battery, we also want
         * to print the contents of the corresponding sysfs node. This will confirm whether the
         * kernel has registered any corresponding sysfs nodes for the input device capability.
         * For example for lights/battery:
        @formatter:off
           $ /sys/devices/virtual/misc/uhid/0005:054C:05C4.0002/leds # ls
           0005:054C:05C4.0002:blue  0005:054C:05C4.0002:global  0005:054C:05C4.0002:green  0005:054C:05C4.0002:red
           $ /sys/devices/virtual/misc/uhid/0005:054C:0DF2.0001/power_supply # ls
           ps-controller-battery-14:3a:9a:cb:15:fa
        @formatter:on
         */
        @JvmStatic
        fun dumpSonySysfsNode(node: String) {
            try {
                val dumpsysOutput = SystemUtil.runShellCommandOrThrow("dumpsys input")
                val lines = dumpsysOutput.lines()
                // Find the first line that mentions a Sony device.
                val sonyDeviceStartIndex = lines.indexOfFirst { it.contains("Sony") }
                if (sonyDeviceStartIndex == -1) {
                    Log.i(TAG, "No Sony device found in 'dumpsys input' output.")
                    return
                }

                // Search for the path in the lines following the Sony device name.
                val pathLine = lines.drop(sonyDeviceStartIndex)
                        .find { it.trim().startsWith("SysfsDevicePath:") }
                if (pathLine == null) {
                    Log.i(TAG, "Could not find SysfsDevicePath.")
                    return
                }

                val path = pathLine.substringAfter("SysfsDevicePath:").trim()
                if (path.isNotEmpty()) {
                    logShellCommand("ls -l $path/$node")
                } else {
                    Log.i(TAG, "SysfsDevicePath is blank.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get and process 'dumpsys input' for Sony device: $e")
            }
        }

        private fun logShellCommand(command: String) {
            try {
                for (line in SystemUtil.runShellCommandOrThrow(command).split("\\n")) {
                    // Sleed to avoid logging too often - otherwise, some lines may be dropped
                    SystemClock.sleep(10)
                    Log.i(TAG, line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute '$command': $e")
            }
        }
    }

    /**
     * Annotation for a [org.junit.Test] that enables additional debugging in the input pipeline for
     * the duration of the test. The test class must use the [DebugInputRule] for annotation to be
     * functional.
     */
    annotation class DebugInput(val bug: Long)

    val initialValues = mutableMapOf<String, String>()

    override fun starting(description: Description?) {
        if (!shouldEnableInputDebugging(description!!)) return

        for (tag in debugInputTags) {
            initialValues[tag] =
                    SystemUtil.runShellCommandOrThrow("getprop log.tag.$tag")!!.trim()
            SystemUtil.runShellCommandOrThrow("setprop log.tag.$tag DEBUG")
        }
        SystemUtil.runShellCommandOrThrow(
            "wm logging enable-text WM_DEBUG_FOCUS_LIGHT WM_DEBUG_FOCUS"
        )
    }

    override fun failed(e: Throwable?, description: Description?) {
        dumpInputStateToLogcat()
    }

    override fun finished(description: Description?) {
        if (!shouldEnableInputDebugging(description!!)) return

        for (entry in initialValues) {
            val value = entry.value.ifBlank { "UNKNOWN" }
            SystemUtil.runShellCommandOrThrow("setprop log.tag.${entry.key} $value")
        }
        initialValues.clear()
        SystemUtil.runShellCommandOrThrow(
            "wm logging disable-text WM_DEBUG_FOCUS_LIGHT WM_DEBUG_FOCUS"
        )
    }

    override fun skipped(e: AssumptionViolatedException?, description: Description?) {
        finished(description)
    }
}

private fun shouldEnableInputDebugging(description: Description): Boolean {
    return description.isTest &&
            description.getAnnotation(DebugInputRule.DebugInput::class.java) != null
}
