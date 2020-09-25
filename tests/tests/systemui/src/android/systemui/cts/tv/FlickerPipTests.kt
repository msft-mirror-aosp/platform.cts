/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.cts.tv

import android.graphics.Region
import android.platform.test.annotations.Postsubmit
import android.server.wm.UiDeviceUtils
import android.server.wm.annotation.Group2
import android.support.test.launcherhelper.TvLauncherStrategy
import android.support.test.uiautomator.UiDevice
import android.systemui.tv.cts.Components.KEYBOARD_ACTIVITY
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.windowName
import android.systemui.tv.cts.KeyboardActivity.ACTION_HIDE_KEYBOARD
import android.systemui.tv.cts.KeyboardActivity.ACTION_SHOW_KEYBOARD
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.ResourceNames.WINDOW_NAME_INPUT_METHOD
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.cts.mockime.ImeSettings
import com.android.cts.mockime.MockImeSessionRule
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.server.wm.flicker.dsl.runWithFlicker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests slightly advanced picture in picture (PiP) behaviors.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:FlickerPipTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class FlickerPipTests : PipTestBase() {

    @get:Rule
    val rule = MockImeSessionRule(
        context,
        instrumentation.uiAutomation,
        ImeSettings.Builder()
            .setInputViewHeight(windowManager.maximumWindowMetrics.bounds.height() / 3)
    )

    private val testRepetitions = 10

    private val tvLauncherStrategy = TvLauncherStrategy().apply {
        setUiDevice(UiDevice.getInstance(instrumentation))
    }

    /** Starts and stops a keyboard app and a pip app. Repeats [testRepetitions] times. */
    private val keyboardScenario: FlickerBuilder
        get() = FlickerBuilder(instrumentation, tvLauncherStrategy).apply {
            repeat { testRepetitions }
            // disable layer tracing
            withLayerTracing { null }
            setup {
                test {
                    UiDeviceUtils.pressHomeButton()
                    // launch our target pip app
                    launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
                    waitForEnterPip(PIP_ACTIVITY)
                    // open an app with an input field and a keyboard
                    launchActivity(KEYBOARD_ACTIVITY)
                    waitForFullscreen(KEYBOARD_ACTIVITY)
                    waitForKeyboardShown()
                }
            }
            teardown {
                test {
                    stopPackage(PIP_ACTIVITY)
                    stopPackage(KEYBOARD_ACTIVITY)
                }
            }
        }

    /** Ensure the pip window remains visible throughout any keyboard interactions. */
    @Test
    fun pipWindow_doesNotLeaveTheScreen_onKeyboardOpenClose() {
        val testTag = "pipWindow_doesNotLeaveTheScreen_onKeyboardOpenClose"
        runWithFlicker(keyboardScenario) {
            withTag { testTag }
            transitions {
                // open the soft keyboard
                launchActivity(KEYBOARD_ACTIVITY, ACTION_SHOW_KEYBOARD)
                waitForKeyboardShown()

                // then close it again
                launchActivity(KEYBOARD_ACTIVITY, ACTION_HIDE_KEYBOARD)
                waitForKeyboardHidden()
            }
            assertions {
                windowManagerTrace {
                    all("PiP window must remain inside visible bounds") {
                        coversAtMostRegion(
                            partialWindowTitle = PIP_ACTIVITY.windowName(),
                            region = Region(windowManager.maximumWindowMetrics.bounds)
                        )
                    }
                }
            }
        }
    }

    /** Ensure the pip window does not obscure the keyboard. */
    @Test
    fun pipWindow_doesNotObscure_keyboard() {
        val testTag = "pipWindow_doesNotObscure_keyboard"
        runWithFlicker(keyboardScenario) {
            withTag { testTag }
            transitions {
                // open the soft keyboard
                launchActivity(KEYBOARD_ACTIVITY, ACTION_SHOW_KEYBOARD)
                waitForKeyboardShown()
            }
            teardown {
                eachRun {
                    // close the keyboard
                    launchActivity(KEYBOARD_ACTIVITY, ACTION_HIDE_KEYBOARD)
                    waitForKeyboardHidden()
                }
            }
            assertions {
                windowManagerTrace {
                    end {
                        isAboveWindow(WINDOW_NAME_INPUT_METHOD, PIP_ACTIVITY.windowName())
                    }
                }
            }
        }
    }

    /** Wait for the soft keyboard window to be open or throw. */
    private fun waitForKeyboardShown() {
        waitForWMState("Keyboard must be shown") { state ->
            state.isWindowVisible(WINDOW_NAME_INPUT_METHOD)
        }
    }

    /** Wait for the soft keyboard window to be hidden or throw. */
    private fun waitForKeyboardHidden() {
        waitForWMState("Keyboard must be hidden") { state ->
            !state.isWindowVisible(WINDOW_NAME_INPUT_METHOD)
        }
    }
}
