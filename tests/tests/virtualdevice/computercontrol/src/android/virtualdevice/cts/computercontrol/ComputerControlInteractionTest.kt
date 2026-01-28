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

package android.virtualdevice.cts.computercontrol

import android.computercontrol.testapp.common.Action
import android.computercontrol.testapp.common.Constants
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
class ComputerControlInteractionTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule
    @JvmField
    val adoptShellPermissionsRule: AdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            getInstrumentation().uiAutomation,
            "android.permission.ACCESS_COMPUTER_CONTROL",
            "android.permission.POST_NOTIFICATIONS",
        )

    @get:Rule val testName = TestName()

    private val bounds =
        getInstrumentation()
            .context
            .getSystemService(WindowManager::class.java)
            .currentWindowMetrics
            .bounds

    fun launchTestApp(className: String? = null): TestAppAgent {
        // TODO(b/463464798): Added failure test case to verify the behavior
        // when permission dialog is pending on the screen or permission is
        // refused from the settings.
        return TestAppAgentLauncher()
            .launch(
                "CtsComputerControlTest-${testName.methodName}",
                TEST_APP_PACKAGE_NAME,
                className,
            )
    }

    @Test
    fun testTap() = launchTestApp().use { testAppAgent ->
        val x = bounds.width() / 2
        val y = bounds.height() / 2
        testAppAgent.tap(x, y)
        Log.d(TAG, "Tapped at ($x, $y)")

        val tap = testAppAgent.nextAction(Action.Tap::class.java)
        assertThat(tap).isNotNull()
        tap!!
        Log.d(TAG, "Tap from TestApp: (${tap.x}, ${tap.y})")
        assertThat(tap.x).isEqualTo(x)
        assertThat(tap.y).isEqualTo(y)
    }

    @Test
    fun testLongPress() = launchTestApp().use { testAppAgent ->
        val x = bounds.width() / 2
        val y = bounds.height() / 2
        testAppAgent.longPress(x, y)
        Log.d(TAG, "Long pressed at ($x, $y)")

        val longPress = testAppAgent.nextAction(Action.LongPress::class.java)
        assertThat(longPress).isNotNull()
        longPress!!
        Log.d(TAG, "LongPress from TestApp: (${longPress.x}, ${longPress.y})")
        assertThat(longPress.x).isEqualTo(x)
        assertThat(longPress.y).isEqualTo(y)
    }

    @Test
    fun testSwipe() = launchTestApp().use { testAppAgent ->
        val x1 = bounds.width() / 2
        val y1 = bounds.height() / 2
        val x2 = bounds.width() / 4
        val y2 = bounds.height() / 4
        testAppAgent.swipe(x1, y1, x2, y2)
        Log.d(TAG, "Swiped from ($x1, $y1) to ($x2, $y2)")

        val swipe = testAppAgent.nextAction(Action.Swipe::class.java)
        assertThat(swipe).isNotNull()
        swipe!!
        Log.d(TAG, "Swipe from TestApp: (${swipe.x1}, ${swipe.y1}) to (${swipe.x2}, ${swipe.y2})")
        assertThat(swipe.x1).isEqualTo(x1)
        assertThat(swipe.y1).isEqualTo(y1)
        assertThat(swipe.x2).isEqualTo(x2)
        assertThat(swipe.y2).isEqualTo(y2)
    }

    @Test
    fun testPerformAction_GoBack() = launchTestApp().use { testAppAgent ->
        // 1 is the action code for GoBack.
        testAppAgent.performAction(1)
        Log.d(TAG, "Performed GoBack")

        val goBack = testAppAgent.nextAction(Action.GoBack::class.java)
        Log.d(TAG, "GoBack from TestApp")
        assertThat(goBack).isNotNull()
    }

    @Test
    fun testGetDisplaySize() = launchTestApp().use { testAppAgent ->
        val screenSize = testAppAgent.getDisplaySize()
        Log.d(TAG, "Screen size from agent: ${screenSize.width}x${screenSize.height}")
        Log.d(TAG, "Screen size from OS: ${bounds.width()}x${bounds.height()}")
        assertThat(screenSize.width).isEqualTo(bounds.width())
        assertThat(screenSize.height).isEqualTo(bounds.height())
    }

    @Test
    fun testTap_edge() = launchTestApp().use { testAppAgent ->
        val width = bounds.width()
        val height = bounds.height()

        // Test tapping at the corners and edges of the screen.
        val testCases =
            listOf(
                Pair(0, 0), // Top-left
                Pair(width - 1, 0), // Top-right
                Pair(0, height - 1), // Bottom-left
                Pair(width - 1, height - 1), // Bottom-right
                Pair(width / 2, 0), // Top-middle
                Pair(width / 2, height - 1), // Bottom-middle
                Pair(0, height / 2), // Left-middle
                Pair(width - 1, height / 2) // Right-middle
            )

        for ((x, y) in testCases) {
            testAppAgent.tap(x, y)
            Log.d(TAG, "Tapped at edge: ($x, $y)")

            val tap = testAppAgent.nextAction(Action.Tap::class.java)
            assertThat(tap).isNotNull()
            tap!!
            Log.d(TAG, "Tap from TestApp: (${tap.x}, ${tap.y})")
            assertThat(tap.x).isEqualTo(x)
            assertThat(tap.y).isEqualTo(y)
        }
    }

    @Test
    fun testGetScreenshot() = launchTestApp().use { testAppAgent ->
        val screenshot = testAppAgent.getScreenshot()
        assertThat(screenshot).isNotNull()
        Log.d(TAG, "Screenshot size: ${screenshot!!.width}x${screenshot.height}")
        assertThat(screenshot.width).isEqualTo(bounds.width())
        assertThat(screenshot.height).isEqualTo(bounds.height())
    }

    @Test
    fun testInsertText() = launchTestApp(TEST_APP_CLASS_NAME).use { testAppAgent ->
        // Insert text1 to text field 1.
        val text1 = "Hello World"
        val text2 = "Goodbye World"
        testAppAgent.requestFocus(Constants.TEXT_FIELD_1)
        testAppAgent.insertText(text1)
        Log.d(TAG, "Inserted text: $text1 in text field 1")
        var insertText1 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText1).isNotNull()
        insertText1!!
        Log.d(TAG, "InsertText from TestApp: ${insertText1.text} in text field 1")
        assertThat(insertText1.textFieldId).isEqualTo(Constants.TEXT_FIELD_1)
        assertThat(insertText1.text).isEqualTo(text1)

        // Insert text2 to text field 1 again.
        testAppAgent.insertText(text2)
        Log.d(TAG, "Inserted text: $text2 in text field 1")
        insertText1 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText1).isNotNull()
        insertText1!!
        Log.d(TAG, "InsertText from TestApp: ${insertText1.text} in text field 1")
        assertThat(insertText1.textFieldId).isEqualTo(Constants.TEXT_FIELD_1)
        assertThat(insertText1.text).isEqualTo(text2)

        // Insert text2 to text field 2.
        testAppAgent.requestFocus(Constants.TEXT_FIELD_2)
        testAppAgent.insertText(text2)
        Log.d(TAG, "Inserted text: $text2")
        val insertText2 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText2).isNotNull()
        insertText2!!
        Log.d(TAG, "InsertText from TestApp: ${insertText2.text}")
        assertThat(insertText2.textFieldId).isEqualTo(Constants.TEXT_FIELD_2)
        assertThat(insertText2.text).isEqualTo(text2)
    }

    companion object {
        private const val TAG = "ComputerControlTest"
        private const val TEST_APP_PACKAGE_NAME = "android.computercontrol.testapp"
        private const val TEST_APP_CLASS_NAME = "android.computercontrol.testapp.app.AppActivity"
    }
}
