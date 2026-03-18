/*
 * Copyright 2026 The Android Open Source Project
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

package android.input.cts

import android.hardware.input.InputDeviceIdentifier
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import com.android.compatibility.common.util.SystemUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ControllerRemappingApi(val inputManager: InputManager, val deviceId: Int) {
    companion object {
        /** See {@link MotionEvent#AXIS_DISABLED} */
        const val AXIS_DISABLED = -1
    }

    private val listener = TestInputDeviceListener(deviceId)

    init {
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
    }

    fun remapControllerButtonAndWait(
        identifier: InputDeviceIdentifier,
        fromButton: Int,
        toButton: Int,
    ) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapControllerButton(identifier, fromButton, toButton) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun remapControllerButtonToAxisAndWait(
        identifier: InputDeviceIdentifier,
        fromButton: Int,
        toAxis: Int,
    ) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapControllerButtonToAxis(identifier, fromButton, toAxis) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun remapControllerAxisAndWait(identifier: InputDeviceIdentifier, fromAxis: Int, toAxis: Int) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapControllerAxis(identifier, fromAxis, toAxis) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun removeControllerButtonRemappingAndWait(identifier: InputDeviceIdentifier, fromButton: Int) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.removeControllerButtonRemapping(identifier, fromButton) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun removeControllerButtonToAxisRemappingAndWait(
        identifier: InputDeviceIdentifier,
        fromButton: Int,
    ) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.removeControllerButtonToAxisRemapping(identifier, fromButton) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun removeControllerAxisRemappingAndWait(identifier: InputDeviceIdentifier, fromAxis: Int) {
        listener.reset()
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.removeControllerAxisRemapping(identifier, fromAxis) },
            "android.permission.CONTROLLER_REMAPPING",
        )
        listener.waitForDeviceChanged()
    }

    fun clearAllControllerButtonRemappings(identifier: InputDeviceIdentifier) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllControllerButtonRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING",
        )
    }

    fun clearAllControllerButtonToAxisRemappings(identifier: InputDeviceIdentifier) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllControllerButtonToAxisRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING",
        )
    }

    fun clearAllControllerButtonToAxisRemappingsAndWait(identifier: InputDeviceIdentifier) {
        listener.reset()
        clearAllControllerButtonToAxisRemappings(identifier)
        listener.waitForDeviceChanged()
    }

    fun clearAllControllerAxisRemappings(identifier: InputDeviceIdentifier) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllControllerAxisRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING",
        )
    }
}

internal class TestInputDeviceListener(val deviceId: Int) : InputManager.InputDeviceListener {
    private var latch = CountDownLatch(1)

    override fun onInputDeviceAdded(deviceId: Int) {}

    override fun onInputDeviceRemoved(deviceId: Int) {}

    override fun onInputDeviceChanged(deviceId: Int) {
        if (deviceId == this.deviceId) {
            latch.countDown()
        }
    }

    fun waitForDeviceChanged() {
        if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
            throw TimeoutException("Timed out waiting for device to change")
        }
    }

    fun reset() {
        latch = CountDownLatch(1)
    }
}
