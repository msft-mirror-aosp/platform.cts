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

package android.view.cts.input

import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_Q
import com.android.cts.input.InputDeviceAssociationByDescriptor
import com.android.cts.input.UinputKeyboard
import com.android.cts.input.VirtualDisplayActivityScenario
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.google.common.truth.Truth.assertThat
import kotlin.use
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * Test {@link android.view.InputDevice} association with a display.
 */
@RunWith(AndroidJUnit4::class)
class InputDeviceDisplayAssociationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val inputManager =
        instrumentation.context.getSystemService(InputManager::class.java)

    private val displayManager =
        instrumentation.context.getSystemService(DisplayManager::class.java)

    @get:Rule
    val testName = TestName()
    @get:Rule
    val activityScenarioA = VirtualDisplayActivityScenario.Rule<CaptureEventActivity>(testName)
    @get:Rule
    val activityScenarioB = VirtualDisplayActivityScenario.Rule<CaptureEventActivity>(testName)

    @Test
    fun testDisplayAssociation_noDisplayToDefaultDisplayAndBack() {
        UinputKeyboard(instrumentation).use { keyboard ->
            assertAssociatedDisplayId(keyboard, INVALID_DISPLAY)

            displayAssociation(keyboard, DEFAULT_DISPLAY).use {
                assertAssociatedDisplayId(keyboard, DEFAULT_DISPLAY)
            }

            assertAssociatedDisplayId(keyboard, INVALID_DISPLAY)
        }
    }

    @Test
    fun testDisplayAssociation_activityReceivesEvents() {
        UinputKeyboard(instrumentation).use { keyboard ->

            displayAssociation(keyboard, activityScenarioA.displayId).use {
                assertAssociatedDisplayId(keyboard, activityScenarioA.displayId)

                keyboard.injectKeyDown(KEY_Q)
                keyboard.injectKeyUp(KEY_Q)

                activityScenarioA.activity.verifier.assertReceivedKey(
                    Matchers.allOf(
                        withKeyCode(KeyEvent.KEYCODE_Q),
                        withKeyAction(KeyEvent.ACTION_DOWN)
                    )
                )
                activityScenarioA.activity.verifier.assertReceivedKey(
                    Matchers.allOf(
                        withKeyCode(KeyEvent.KEYCODE_Q),
                        withKeyAction(KeyEvent.ACTION_UP)
                    )
                )
                activityScenarioB.activity.verifier.assertNoEvents()
            }

            displayAssociation(keyboard, activityScenarioB.displayId).use {
                assertAssociatedDisplayId(keyboard, activityScenarioB.displayId)

                keyboard.injectKeyDown(KEY_Q)
                keyboard.injectKeyUp(KEY_Q)

                activityScenarioA.activity.verifier.assertNoEvents()
                activityScenarioB.activity.verifier.assertReceivedKey(
                    Matchers.allOf(
                        withKeyCode(KeyEvent.KEYCODE_Q),
                        withKeyAction(KeyEvent.ACTION_DOWN)
                    )
                )
                activityScenarioB.activity.verifier.assertReceivedKey(
                    Matchers.allOf(
                        withKeyCode(KeyEvent.KEYCODE_Q),
                        withKeyAction(KeyEvent.ACTION_UP)
                    )
                )
            }
        }
    }

    private fun displayAssociation(keyboard: UinputKeyboard, displayId: Int) =
        InputDeviceAssociationByDescriptor.Associator(instrumentation)
            .associate(keyboard.deviceId, displayManager.getDisplay(displayId))

    private fun assertAssociatedDisplayId(keyboard: UinputKeyboard, displayId: Int) {
        val inputDevice: InputDevice = inputManager.getInputDevice(keyboard.deviceId)!!
        assertThat(inputDevice.associatedDisplayId).isEqualTo(displayId)
    }
}
