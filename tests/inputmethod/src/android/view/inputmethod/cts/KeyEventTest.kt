/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.inputmethod.cts

import android.Manifest
import android.app.Instrumentation
import android.hardware.input.InputSettings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.cts.util.EndToEndImeTestBase
import android.view.inputmethod.cts.util.TestActivity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.DeviceState
import com.android.compatibility.common.util.PollingCheck.waitFor
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingRunnable
import com.android.cts.input.EvdevInputEventCodes
import com.android.cts.input.UinputKeyboard
import com.android.cts.mockime.ImeEventStreamTestUtils.DEFAULT_TIMEOUT
import com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher
import com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent
import com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue
import com.android.cts.mockime.ImeSettings
import com.android.cts.mockime.ImeSettings.OnKeyDownUpBehavior
import com.android.cts.mockime.MockImeSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class KeyEventTest : EndToEndImeTestBase() {

    private lateinit var instrumentation: Instrumentation
    private lateinit var uinputKeyboard: UinputKeyboard
    private var originalRepeatKeysEnabled: Boolean = false

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        uinputKeyboard = UinputKeyboard(instrumentation)

        runWithShellPermissionIdentity(
            ThrowingRunnable {
                originalRepeatKeysEnabled =
                    InputSettings.isRepeatKeysEnabled(instrumentation.context)
                InputSettings.setRepeatKeysEnabled(instrumentation.context, false)
            },
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
        )
    }

    @After
    fun tearDown() {
        if (this::uinputKeyboard.isInitialized) {
            uinputKeyboard.close()
        }

        runWithShellPermissionIdentity(
            ThrowingRunnable {
                InputSettings.setRepeatKeysEnabled(
                    instrumentation.context,
                    originalRepeatKeysEnabled,
                )
            },
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
        )
    }

    @Test
    fun testKeyPress_imeDoesNotConsume_verifyCallbacks() {
        MockImeSession.create(
                instrumentation.context,
                instrumentation.uiAutomation,
                ImeSettings.Builder()
                    .setOnKeyDownBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.NOT_CONSUME)
                    .setOnKeyUpBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.NOT_CONSUME),
            )
            .use { imeSession ->
                val stream = imeSession.openEventStream()

                val events = CopyOnWriteArrayList<Pair<String, Int>>()

                TestActivity.startSync(getActivityInitializer(events))

                expectEvent(stream, eventMatcher("onStartInput"), DEFAULT_TIMEOUT)

                uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyDown",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyDown =
                    listOf("onKeyPreIme" to KeyEvent.KEYCODE_Q, "onKeyDown" to KeyEvent.KEYCODE_Q)
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyDown == events },
                    { "Key down events mismatch. Expected: ${expectedKeyDown}, actual: ${events}" },
                )

                uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyUp",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyUp =
                    listOf(
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                        "onKeyDown" to KeyEvent.KEYCODE_Q,
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                        "onKeyUp" to KeyEvent.KEYCODE_Q,
                    )
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyUp == events },
                    { "Key up events mismatch. Expected: ${expectedKeyUp}, actual: ${events}" },
                )
            }
    }

    @Test
    fun testKeyPress_imeConsumesOnKeyDown_verifyCallbacks() {
        MockImeSession.create(
                instrumentation.context,
                instrumentation.uiAutomation,
                ImeSettings.Builder()
                    .setOnKeyDownBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.CONSUME)
                    .setOnKeyUpBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.NOT_CONSUME),
            )
            .use { imeSession ->
                val stream = imeSession.openEventStream()

                val events = CopyOnWriteArrayList<Pair<String, Int>>()

                TestActivity.startSync(getActivityInitializer(events))

                expectEvent(stream, eventMatcher("onStartInput"), DEFAULT_TIMEOUT)

                uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyDown",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyDown = listOf("onKeyPreIme" to KeyEvent.KEYCODE_Q)
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyDown == events },
                    { "Key down events mismatch. Expected: ${expectedKeyDown}, actual: ${events}" },
                )

                uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyUp",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyUp =
                    listOf(
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                        "onKeyUp" to KeyEvent.KEYCODE_Q,
                    )
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyUp == events },
                    { "Key up events mismatch. Expected: ${expectedKeyUp}, actual: ${events}" },
                )
            }
    }

    @Test
    fun testKeyPress_imeConsumesOnKeyUp_verifyCallbacks() {
        MockImeSession.create(
                instrumentation.context,
                instrumentation.uiAutomation,
                ImeSettings.Builder()
                    .setOnKeyDownBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.NOT_CONSUME)
                    .setOnKeyUpBehavior(KeyEvent.KEYCODE_Q, OnKeyDownUpBehavior.CONSUME),
            )
            .use { imeSession ->
                val stream = imeSession.openEventStream()

                val events = CopyOnWriteArrayList<Pair<String, Int>>()

                TestActivity.startSync(getActivityInitializer(events))

                expectEvent(stream, eventMatcher("onStartInput"), DEFAULT_TIMEOUT)

                uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyDown",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyDown =
                    listOf("onKeyPreIme" to KeyEvent.KEYCODE_Q, "onKeyDown" to KeyEvent.KEYCODE_Q)
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyDown == events },
                    { "Key down events mismatch. Expected: ${expectedKeyDown}, actual: ${events}" },
                )

                uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q)

                expectEventWithKeyValue(
                    stream,
                    "onKeyUp",
                    "keyCode",
                    KeyEvent.KEYCODE_Q,
                    DEFAULT_TIMEOUT,
                )

                val expectedKeyUp =
                    listOf(
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                        "onKeyDown" to KeyEvent.KEYCODE_Q,
                        "onKeyPreIme" to KeyEvent.KEYCODE_Q,
                    )
                waitFor(
                    DEFAULT_TIMEOUT.toMillis(),
                    { expectedKeyUp == events },
                    { "Key up events mismatch. Expected: ${expectedKeyUp}, actual: ${events}" },
                )
            }
    }

    private fun getActivityInitializer(
        events: MutableList<Pair<String, Int>>
    ): Function<TestActivity, View> {
        return Function { activity ->
            val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            val editText =
                object : EditText(activity) {
                    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                        events.add("onKeyPreIme" to keyCode)
                        return super.onKeyPreIme(keyCode, event)
                    }

                    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                        events.add("onKeyDown" to keyCode)
                        return super.onKeyDown(keyCode, event)
                    }

                    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                        events.add("onKeyUp" to keyCode)
                        return super.onKeyUp(keyCode, event)
                    }
                }

            layout.addView(editText)
            editText.requestFocus()
            layout
        }
    }

    companion object {
        @JvmField @ClassRule @Rule val deviceState = DeviceState()
    }
}
