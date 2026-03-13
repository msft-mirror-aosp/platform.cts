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
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_X
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_EAST
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_SOUTH
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_ABS
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT
import com.android.cts.input.UinputGamepad
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.hardware.input.Flags.FLAG_CONTROLLER_REMAPPING
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS, FLAG_CONTROLLER_REMAPPING)
class ControllerButtonToAxisRemappingTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val rule = ActivityScenarioRule(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var inputManager: InputManager
    private lateinit var inputDevice: InputDevice
    private lateinit var remappingApi: ControllerRemappingApi

    @Before
    fun setUp() {
        // TODO(b/454344508): Consider making InputManagerService multi-user aware.
        assumeFalse(
            "InputManagerService only tracks the current user. " +
                "Settings changes for non-current users are not applied, causing tests " +
                "to fail for visible background users.",
            UserHelper().isVisibleBackgroundUser(),
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
        if (this::inputDevice.isInitialized) {
            remappingApi.clearAllControllerButtonRemappings(inputDevice.identifier)
            remappingApi.clearAllControllerButtonToAxisRemappings(inputDevice.identifier)
            remappingApi.clearAllControllerAxisRemappings(inputDevice.identifier)
        }
    }

    @Test
    fun mappedToNonExistingAxis_generatesMotionAndKeyEvents() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertNoEvents()

            gamepadDevice.injectKeyUp(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertNoEvents()
        }
    }

    @Test
    fun mappedToExistingAxis_generatesMotionAndKeyEvents() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
                MotionEvent.AXIS_X,
            )

            gamepadDevice.injectKeyDown(BTN_EAST)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_X, 1f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_EAST)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_X, 0f),
                )
            )

            remappingApi.removeControllerButtonToAxisRemappingAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
            )

            gamepadDevice.injectKeyDown(BTN_EAST)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )

            gamepadDevice.injectKeyUp(BTN_EAST)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )

            verifier.assertNoEvents()
        }
    }

    @Test
    fun buttonToBothButtonAndAxis_generatesMotionAndKeyEvents() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            remappingApi.remapControllerButtonAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
            )
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )
            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )
        }
    }

    @Test
    fun buttonToButtonToAxis_doesNotApplyTransitiveRemappings() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            remappingApi.remapControllerButtonAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
            )
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
                MotionEvent.AXIS_LTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertNoEvents()

            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertNoEvents()
        }
    }

    @Test
    fun buttonToAxisToAxis_doesNotApplyTransitiveRemappings() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )
            remappingApi.remapControllerAxisAndWait(
                inputDevice.identifier,
                MotionEvent.AXIS_LTRIGGER,
                MotionEvent.AXIS_HAT_Y,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_HAT_Y, 0f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_HAT_Y, 0f),
                )
            )
        }
    }

    @Test
    fun updateButtonToAxisRemapping_generatesCorrectEvents() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            // Remap Button A to LTRIGGER
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )

            // Now, update the remapping for the same button to a different axis
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_RTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)

            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )
        }
    }

    @Test
    fun multipleButtonsAndPhysicalAxis_generatesCorrectEvents() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)
            // Remap Button A to LTRIGGER and Button B to RTRIGGER
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
                MotionEvent.AXIS_RTRIGGER,
            )

            // Press Button A, Button B, and move the X axis
            gamepadDevice.injectKeyDown(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_X, 0f),
                )
            )

            gamepadDevice.injectKeyDown(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_X, 0f),
                )
            )

            gamepadDevice.injectEvents(EV_ABS, ABS_X, 89)
            gamepadDevice.injectEvents(EV_SYN, SYN_REPORT, 0)
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_X, 0.7f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_X, 0.7f),
                )
            )

            gamepadDevice.injectEvents(EV_ABS, ABS_X, 20)
            gamepadDevice.injectEvents(EV_SYN, SYN_REPORT, 0)
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_X, 0.157f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                    withAxisValue(MotionEvent.AXIS_X, 0.157f),
                )
            )
        }
    }

    @Test
    fun clearAllControllerButtonToAxisRemappings_clearsRemappings() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            remappingApi = ControllerRemappingApi(inputManager, gamepadDevice.deviceId)

            // Remap Button A to LTRIGGER and Button B to RTRIGGER
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_A,
                MotionEvent.AXIS_LTRIGGER,
            )
            remappingApi.remapControllerButtonToAxisAndWait(
                inputDevice.identifier,
                KeyEvent.KEYCODE_BUTTON_B,
                MotionEvent.AXIS_RTRIGGER,
            )

            gamepadDevice.injectKeyDown(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 1f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_LTRIGGER, 0f),
                )
            )

            gamepadDevice.injectKeyDown(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 1f),
                )
            )

            gamepadDevice.injectKeyUp(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withAxisValue(MotionEvent.AXIS_RTRIGGER, 0f),
                )
            )

            remappingApi.clearAllControllerButtonToAxisRemappingsAndWait(inputDevice.identifier)

            gamepadDevice.injectKeyDown(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertNoEvents()

            gamepadDevice.injectKeyUp(BTN_SOUTH)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_A), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertNoEvents()

            gamepadDevice.injectKeyDown(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_DOWN))
            )
            verifier.assertNoEvents()

            gamepadDevice.injectKeyUp(BTN_EAST)
            verifier.assertReceivedKey(
                allOf(withKeyCode(KeyEvent.KEYCODE_BUTTON_B), withKeyAction(KeyEvent.ACTION_UP))
            )
            verifier.assertNoEvents()
        }
    }
}
