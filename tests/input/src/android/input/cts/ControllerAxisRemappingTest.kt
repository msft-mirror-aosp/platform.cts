/*
 * Copyright 2025 The Android Open Source Project
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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UserHelper
import com.android.compatibility.common.util.WindowUtil
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_THROTTLE
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_X
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_ABS
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT
import com.android.cts.input.UinputGamepad
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.hardware.input.Flags.FLAG_CONTROLLER_REMAPPING
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create a virtual game controller device and inject a 'hardware' motion event after remapping
 * axes. Ensure that the event axis values are correctly remapped.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS, FLAG_CONTROLLER_REMAPPING)
class ControllerAxisRemappingTest {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var inputManager: InputManager
    private lateinit var gamepadDevice: InputDevice

    @Before
    fun setUp() {
        // TODO(b/454344508): Consider making InputManagerService multi-user aware.
        assumeFalse(
            "InputManagerService only tracks the current user. " +
                    "Settings changes for non-current users are not applied, causing tests " +
                    "to fail for visible background users.",
            UserHelper().isVisibleBackgroundUser()
        )

        rule.scenario.onActivity {
            inputManager = it.getSystemService(InputManager::class.java)!!
            activity = it
            verifier = activity.verifier
        }
        WindowUtil.waitForFocus(activity)
    }

    @After
    fun tearDown() {
        if (this::gamepadDevice.isInitialized) {
            clearAllControllerAxisRemappings(gamepadDevice.identifier)
        }
    }

    @Test
    fun testControllerAxisRemapping_forCenteredAxis() {
        UinputGamepad(instrumentation).use { device ->
            gamepadDevice = inputManager.getInputDevice(device.deviceId)!!
            val listener = TestInputDeviceListener(device.deviceId)
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            remapControllerAxis(
                gamepadDevice.identifier,
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_Z
            )
            assertEquals(
                mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_Z),
                getControllerAxisRemappings(gamepadDevice.identifier)
            )
            // Wait for input device to change (i.e. axis remapping applied)
            assertTrue(
                "Timed out waiting for axis remapping to be applied",
                listener.waitForDeviceChanged(1000)
            )

            device.injectEvents(EV_ABS, ABS_X, 127)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_Z, 1f),
                )
            )

            device.injectEvents(EV_ABS, ABS_X, -127)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_Z, -1f),
                )
            )
        }
    }

    @Test
    fun testControllerAxisRemapping_forNonCenteredAxis() {
        UinputGamepad(instrumentation).use { device ->
            gamepadDevice = inputManager.getInputDevice(device.deviceId)!!
            val listener = TestInputDeviceListener(device.deviceId)
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            remapControllerAxis(
                gamepadDevice.identifier,
                MotionEvent.AXIS_THROTTLE,
                MotionEvent.AXIS_LTRIGGER
            )
            assertEquals(
                mapOf(MotionEvent.AXIS_THROTTLE to MotionEvent.AXIS_LTRIGGER),
                getControllerAxisRemappings(gamepadDevice.identifier)
            )
            // Wait for input device to change (i.e. axis remapping applied)
            assertTrue(
                "Timed out waiting for axis remapping to be applied",
                listener.waitForDeviceChanged(1000)
            )

            device.injectEvents(EV_ABS, ABS_THROTTLE, 255)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )

            device.injectEvents(EV_ABS, ABS_THROTTLE, 0)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )
        }
    }

    @Test
    fun testControllerAxisRemapping_fromCenteredAxis_toNonCenteredAxis() {
        UinputGamepad(instrumentation).use { device ->
            gamepadDevice = inputManager.getInputDevice(device.deviceId)!!
            val listener = TestInputDeviceListener(device.deviceId)
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            remapControllerAxis(
                gamepadDevice.identifier,
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_LTRIGGER
            )
            assertEquals(
                mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_LTRIGGER),
                getControllerAxisRemappings(gamepadDevice.identifier)
            )
            // Wait for input device to change (i.e. axis remapping applied)
            assertTrue(
                "Timed out waiting for axis remapping to be applied",
                listener.waitForDeviceChanged(1000)
            )

            device.injectEvents(EV_ABS, ABS_X, 127)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )

            device.injectEvents(EV_ABS, ABS_X, -127)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )
        }
    }

    @Test
    fun testControllerAxisRemapping_fromNonCentredAxis_toCenteredAxis() {
        UinputGamepad(instrumentation).use { device ->
            gamepadDevice = inputManager.getInputDevice(device.deviceId)!!
            val listener = TestInputDeviceListener(device.deviceId)
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            remapControllerAxis(
                gamepadDevice.identifier,
                MotionEvent.AXIS_THROTTLE,
                MotionEvent.AXIS_X
            )
            assertEquals(
                mapOf(MotionEvent.AXIS_THROTTLE to MotionEvent.AXIS_X),
                getControllerAxisRemappings(gamepadDevice.identifier)
            )
            // Wait for input device to change (i.e. axis remapping applied)
            assertTrue(
                "Timed out waiting for axis remapping to be applied",
                listener.waitForDeviceChanged(1000)
            )

            device.injectEvents(EV_ABS, ABS_THROTTLE, 255)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_X, 1f),
                )
            )

            device.injectEvents(EV_ABS, ABS_THROTTLE, 0)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_X, -1f),
                )
            )
        }
    }

    @Test
    fun testRemoveControllerAxisRemapping() {
        UinputGamepad(instrumentation).use { device ->
            gamepadDevice = inputManager.getInputDevice(device.deviceId)!!
            val listener = TestInputDeviceListener(device.deviceId)
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            remapControllerAxis(
                gamepadDevice.identifier,
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_Z
            )
            // Wait for input device to change (i.e. axis remapping applied)
            assertTrue(
                "Timed out waiting for axis remapping to be applied",
                listener.waitForDeviceChanged(1000)
            )

            listener.reset()
            // Remove remapping
            removeControllerAxisRemapping(gamepadDevice.identifier, MotionEvent.AXIS_X)
            assertTrue(getControllerAxisRemappings(gamepadDevice.identifier).isEmpty())
            assertTrue(
                "Timed out waiting for axis remapping to be removed",
                listener.waitForDeviceChanged(1000)
            )

            device.injectEvents(EV_ABS, ABS_X, 127)
            device.injectEvents(EV_SYN, SYN_REPORT, 0)

            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_X, 1f),
                )
            )
        }
    }

    private fun remapControllerAxis(
        identifier: InputDeviceIdentifier,
        fromAxis: Int,
        toAxis: Int
    ) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapControllerAxis(identifier, fromAxis, toAxis) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun removeControllerAxisRemapping(
        identifier: InputDeviceIdentifier,
        fromAxis: Int,
    ) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.removeControllerAxisRemapping(identifier, fromAxis) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun clearAllControllerAxisRemappings(identifier: InputDeviceIdentifier) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllControllerAxisRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun getControllerAxisRemappings(identifier: InputDeviceIdentifier): Map<Int, Int> {
        return SystemUtil.runWithShellPermissionIdentity(
            ThrowingSupplier { inputManager.getControllerAxisRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private class TestInputDeviceListener(val deviceId: Int) : InputManager.InputDeviceListener {
        private var latch = CountDownLatch(1)
        override fun onInputDeviceAdded(deviceId: Int) {}
        override fun onInputDeviceRemoved(deviceId: Int) {}
        override fun onInputDeviceChanged(deviceId: Int) {
            if (deviceId == this.deviceId) {
                latch.countDown()
            }
        }

        fun waitForDeviceChanged(timeoutMillis: Long): Boolean {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        }

        fun reset() {
            latch = CountDownLatch(1)
        }
    }
}
