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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UserHelper
import com.android.compatibility.common.util.WindowUtil
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_EAST
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_MODE
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_NORTH
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_SELECT
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_SOUTH
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_START
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_THUMBL
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_THUMBR
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TL
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TL2
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TR
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TR2
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_WEST
import com.android.cts.input.UinputGamepad
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.hardware.input.Flags.FLAG_CONTROLLER_REMAPPING
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual game controller devices and inject a 'hardware' key event after remapping keys.
 * Ensure that the event keys are correctly remapped.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS, FLAG_CONTROLLER_REMAPPING)
class ControllerButtonRemappingTest {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        // TODO(b/454344508): Consider making InputManagerService multi-user aware.
        assumeFalse(
            "InputManagerService only tracks the current user. " +
                    "Settings changes for non-current users are not applied, causing tests " +
                    "to fail for visible background users.",
            UserHelper().isVisibleBackgroundUser()
        )

        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
            verifier = activity.verifier
        }
        inputManager.resetLockedModifierState()
        WindowUtil.waitForFocus(activity)
    }

    @Test
    fun testControllerRemapping() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            val inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            for (testData in keyRemappingData) {
                // Add remapping
                remapControllerButton(
                    inputDevice.identifier,
                    testData.fromKey,
                    testData.toKey
                )
                // Wait for remapping: getKeycodeForKeyLocation checks if the remapping is applied
                // at KL level
                PollingCheck.waitFor {
                    testData.toKey == inputDevice.getKeyCodeForKeyLocation(testData.fromKey)
                }
                verifyKeyPress(gamepadDevice, testData.fromScanCode, testData.toKey)

                // Get remapping
                val remapping = getControllerButtonRemappings(inputDevice.identifier)
                assertEquals(remapping, mapOf(testData.fromKey to testData.toKey))

                // Remove remapping
                removeControllerButtonRemapping(
                    inputDevice.identifier,
                    testData.fromKey
                )
                PollingCheck.waitFor {
                    // Assuming there is no default KL mapping defined otherwise removing the custom
                    // remapping will fallback to default KL remapping for the device, which can be
                    // different from the fromKeyCode (i.e. mapping in generic.kl)
                    testData.fromKey == inputDevice.getKeyCodeForKeyLocation(testData.fromKey)
                }
                verifyKeyPress(gamepadDevice, testData.fromScanCode, testData.fromKey)
            }
        }
    }

    @Test
    fun testClearAllControllerButtonRemappings() {
        UinputGamepad(instrumentation).use { gamepadDevice ->
            val inputDevice = inputManager.getInputDevice(gamepadDevice.deviceId)!!
            for (testData in keyRemappingData) {
                // Add remapping
                remapControllerButton(
                    inputDevice.identifier,
                    testData.fromKey,
                    testData.toKey
                )
            }
            for (testData in keyRemappingData) {
                // Wait for remapping at KL level
                PollingCheck.waitFor {
                    testData.toKey == inputDevice.getKeyCodeForKeyLocation(testData.fromKey)
                }
            }

            // Clear all remapping
            clearAllControllerButtonRemappings(inputDevice.identifier)

            for (testData in keyRemappingData) {
                PollingCheck.waitFor {
                    // Assuming there is no default KL mapping defined otherwise removing the custom
                    // remapping will fallback to default KL remapping for the device, which can be
                    // different from the fromKeyCode (i.e. mapping in generic.kl)
                    testData.fromKey == inputDevice.getKeyCodeForKeyLocation(testData.fromKey)
                }
                verifyKeyPress(gamepadDevice, testData.fromScanCode, testData.fromKey)
            }
        }
    }

    private fun verifyKeyPress(gamepadDevice: UinputGamepad, scanCode: Int, expectedKeyCode: Int) {
        gamepadDevice.injectKeyDown(scanCode)
        verifier.assertReceivedKey(
            allOf(
                withKeyCode(expectedKeyCode),
                withKeyAction(KeyEvent.ACTION_DOWN),
            )
        )

        gamepadDevice.injectKeyUp(scanCode)
        verifier.assertReceivedKey(
            allOf(
                withKeyCode(expectedKeyCode),
                withKeyAction(KeyEvent.ACTION_UP),
            )
        )
    }

    private fun remapControllerButton(
        identifier: InputDeviceIdentifier,
        fromButton: Int,
        toKeyCode: Int
    ) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapControllerButton(identifier, fromButton, toKeyCode) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun removeControllerButtonRemapping(
        identifier: InputDeviceIdentifier,
        fromButton: Int
    ) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.removeControllerButtonRemapping(identifier, fromButton) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun clearAllControllerButtonRemappings(identifier: InputDeviceIdentifier) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllControllerButtonRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    private fun getControllerButtonRemappings(identifier: InputDeviceIdentifier): Map<Int, Int> {
        return SystemUtil.runWithShellPermissionIdentity(
            ThrowingSupplier { inputManager.getControllerButtonRemappings(identifier) },
            "android.permission.CONTROLLER_REMAPPING"
        )
    }

    data class TestData(
        val fromKey: Int,
        val toKey: Int,
        val fromScanCode: Int,
        val toScanCode: Int
    )

    companion object {
        val keyRemappingData =
            listOf(
                TestData(
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_B,
                    BTN_SOUTH,
                    BTN_EAST
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_A,
                    BTN_EAST,
                    BTN_SOUTH
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_X,
                    KeyEvent.KEYCODE_BUTTON_Y,
                    BTN_NORTH,
                    BTN_WEST
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_Y,
                    KeyEvent.KEYCODE_BUTTON_X,
                    BTN_WEST,
                    BTN_NORTH
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_L1,
                    KeyEvent.KEYCODE_BUTTON_R1,
                    BTN_TL,
                    BTN_TR
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_R1,
                    KeyEvent.KEYCODE_BUTTON_L1,
                    BTN_TR,
                    BTN_TL
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_L2,
                    KeyEvent.KEYCODE_BUTTON_R2,
                    BTN_TL2,
                    BTN_TR2
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_R2,
                    KeyEvent.KEYCODE_BUTTON_L2,
                    BTN_TR2,
                    BTN_TL2
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_THUMBL,
                    KeyEvent.KEYCODE_BUTTON_THUMBR,
                    BTN_THUMBL,
                    BTN_THUMBR
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_THUMBR,
                    KeyEvent.KEYCODE_BUTTON_THUMBL,
                    BTN_THUMBR,
                    BTN_THUMBL
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_START,
                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    BTN_START,
                    BTN_SELECT
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_BUTTON_MODE,
                    BTN_SELECT,
                    BTN_MODE
                ),
                TestData(
                    KeyEvent.KEYCODE_BUTTON_MODE,
                    KeyEvent.KEYCODE_BUTTON_START,
                    BTN_MODE,
                    BTN_START
                ),
            )
    }
}
