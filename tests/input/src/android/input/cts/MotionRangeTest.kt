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

import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.UserHelper
import com.android.compatibility.common.util.WindowUtil
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.UinputGamepad
import com.android.hardware.input.Flags.FLAG_CONTROLLER_REMAPPING
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS, FLAG_CONTROLLER_REMAPPING)
class MotionRangeTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val rule = ActivityScenarioRule(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager
    private lateinit var inputDevice: InputDevice
    private lateinit var remappingApi: ControllerRemappingApi

    @Before
    fun setUp() {
        assumeFalse(
            "InputManagerService only tracks the current user. " +
                "Settings changes for non-current users are not applied, causing tests " +
                "to fail for visible background users.",
            UserHelper().isVisibleBackgroundUser(),
        )

        rule.scenario.onActivity {
            inputManager = it.getSystemService(InputManager::class.java)!!
            activity = it
        }
        WindowUtil.waitForFocus(activity)
    }

    @After
    fun tearDown() {
        if (this::inputDevice.isInitialized) {
            remappingApi.clearAllControllerButtonRemappings(inputDevice.identifier)
            remappingApi.clearAllControllerButtonToAxisRemappings(inputDevice.identifier)
            remappingApi.clearAllControllerAxisRemappings(inputDevice.identifier)
        }
    }

    @Test
    fun buttonToAxisRemapping_updatesMotionRanges() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_LTRIGGER))
            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_HAT_Y))
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_X))
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_THROTTLE))
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            // Non existing trigger axis.
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )
            // Non existing centered axis.
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
                MotionEvent.AXIS_HAT_Y,
            )
            // Existing trigger axis.
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_Y,
                MotionEvent.AXIS_THROTTLE,
            )
            // Existing centered axis.
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_X,
                MotionEvent.AXIS_X,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!

            val triggerRange = inputDevice.getMotionRange(MotionEvent.AXIS_LTRIGGER)
            assertNotNull("motionRange is null", triggerRange)
            triggerRange!!
            assertEquals(0f, triggerRange.min)
            assertEquals(1f, triggerRange.max)

            val hatYRange = inputDevice.getMotionRange(MotionEvent.AXIS_HAT_Y)
            assertNotNull("motionRange is null", hatYRange)
            hatYRange!!
            assertEquals(-1f, hatYRange.min)
            assertEquals(1f, hatYRange.max)

            val gasRange = inputDevice.getMotionRange(MotionEvent.AXIS_THROTTLE)
            assertNotNull("motionRange is null", gasRange)
            gasRange!!
            assertEquals(0f, gasRange.min)
            assertEquals(1f, gasRange.max)

            val xRange = inputDevice.getMotionRange(MotionEvent.AXIS_X)
            assertNotNull("motionRange is null", xRange)
            xRange!!
            assertEquals(-1f, xRange.min)
            assertEquals(1f, xRange.max)

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!

            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_LTRIGGER))

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_HAT_Y))

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_Y,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_THROTTLE))

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_X,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_X))
        }
    }

    @Test
    fun updateButtonToAxisRemapping_updatesMotionRanges() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            // Remap Button A to LTRIGGER
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_LTRIGGER))
            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_RTRIGGER))

            // Now, update the remapping for the same button to a different axis
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_RTRIGGER,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_LTRIGGER))
            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_RTRIGGER))
        }
    }

    @Test
    fun axisRemappedToUnknown_removedFromMotionRanges_reappearsWhenRemappedAgain() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)

            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_X))

            // Remap AXIS_X to AXIS_DISABLED
            remappingApi.remapControllerAxisAndWait(
                inputDevice.identifier,
                MotionEvent.AXIS_X,
                ControllerRemappingApi.AXIS_DISABLED,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!

            assertNull(inputDevice.getMotionRange(MotionEvent.AXIS_X))

            // Remap another axis to AXIS_X, which should make AXIS_X reappear in motion ranges
            remappingApi.remapControllerAxisAndWait(
                inputDevice.identifier,
                MotionEvent.AXIS_Y,
                MotionEvent.AXIS_X,
            )
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!

            assertNotNull(inputDevice.getMotionRange(MotionEvent.AXIS_X))
        }
    }
}
