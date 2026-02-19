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
import com.android.compatibility.common.util.PollingCheck
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
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

    private val edgeCoordinates =
        listOf(
            Pair(0, 0), // Top-left
            Pair(bounds.width() - 1, 0), // Top-right
            Pair(0, bounds.height() - 1), // Bottom-left
            Pair(bounds.width() - 1, bounds.height() - 1), // Bottom-right
            Pair(bounds.width() / 2, 0), // Top-middle
            Pair(bounds.width() / 2, bounds.height() - 1), // Bottom-middle
            Pair(0, bounds.height() / 2), // Left-middle
            Pair(bounds.width() - 1, bounds.height() / 2), // Right-middle
        )

    private val outOfBoundsCoordinates =
        listOf(
            // Positive out of bounds
            Pair(bounds.width(), 0),
            Pair(0, bounds.height()),
            Pair(bounds.width(), bounds.height()),
            Pair(bounds.width() + 100, bounds.height() + 100),
            // Negative coordinates
            Pair(-1, -1),
            Pair(-1, 0),
            Pair(0, -1),
            Pair(-100, -100),
        )

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

        val tap = testAppAgent.nextAction(Action.Tap::class.java)
        assertThat(tap).isNotNull()
        tap!!
        assertThat(tap.x).isEqualTo(x)
        assertThat(tap.y).isEqualTo(y)
    }

    @Test
    fun testLongPress() = launchTestApp().use { testAppAgent ->
        val x = bounds.width() / 2
        val y = bounds.height() / 2
        testAppAgent.longPress(x, y)

        val longPress = testAppAgent.nextAction(Action.LongPress::class.java)
        assertThat(longPress).isNotNull()
        longPress!!
        assertThat(longPress.x).isEqualTo(x)
        assertThat(longPress.y).isEqualTo(y)
    }

    @Test
    fun testLongPress_edge() = launchTestApp().use { testAppAgent ->
        // Test long pressing at the corners and edges of the screen.
        for ((x, y) in edgeCoordinates) {
            testAppAgent.longPress(x, y)

            val longPress = testAppAgent.nextAction(Action.LongPress::class.java)
            assertThat(longPress).isNotNull()
            longPress!!
            assertThat(longPress.x).isEqualTo(x)
            assertThat(longPress.y).isEqualTo(y)
        }
    }

    @Test
    fun testLongPress_outOfBounds() = launchTestApp().use { testAppAgent ->
        // Test long pressing outside the screen bounds.
        for ((x, y) in outOfBoundsCoordinates) {
            assertThrows(IllegalArgumentException::class.java) { testAppAgent.longPress(x, y) }
        }
    }

    @Test
    fun testSwipe() = launchTestApp().use { testAppAgent ->
        val x1 = bounds.width() / 2
        val y1 = bounds.height() / 2
        val x2 = bounds.width() / 4
        val y2 = bounds.height() / 4
        testAppAgent.swipe(x1, y1, x2, y2)

        val swipe = testAppAgent.nextAction(Action.Swipe::class.java)
        assertThat(swipe).isNotNull()
        swipe!!
        assertThat(swipe.x1).isEqualTo(x1)
        assertThat(swipe.y1).isEqualTo(y1)
        assertThat(swipe.x2).isEqualTo(x2)
        assertThat(swipe.y2).isEqualTo(y2)
    }

    @Test
    fun testSwipe_edge() = launchTestApp().use { testAppAgent ->
        val centerX = bounds.width() / 2
        val centerY = bounds.height() / 2

        // Test swiping from/to the corners and edges of the screen.
        val testCases =
            edgeCoordinates.flatMap { (x, y) ->
                listOf(
                    // From center to edge and back
                    arrayOf(centerX, centerY, x, y),
                    arrayOf(x, y, centerX, centerY)
                )
            } +
                listOf(
                    // Edge to edge
                    arrayOf(0, centerY, bounds.width() - 1, centerY), // Left to Right
                    arrayOf(bounds.width() - 1, centerY, 0, centerY), // Right to Left
                    arrayOf(centerX, 0, centerX, bounds.height() - 1), // Top to Bottom
                    arrayOf(centerX, bounds.height() - 1, centerX, 0) // Bottom to Top
                )

        for (coords in testCases) {
            val (x1, y1, x2, y2) = coords
            testAppAgent.swipe(x1, y1, x2, y2)

            val swipe = testAppAgent.nextAction(Action.Swipe::class.java)
            assertThat(swipe).isNotNull()
            swipe!!
            assertThat(swipe.x1).isEqualTo(x1)
            assertThat(swipe.y1).isEqualTo(y1)
            assertThat(swipe.x2).isEqualTo(x2)
            assertThat(swipe.y2).isEqualTo(y2)
        }
    }

    @Test
    fun testSwipe_outOfBounds() = launchTestApp().use { testAppAgent ->
        val centerX = bounds.width() / 2
        val centerY = bounds.height() / 2

        // Swipes that are not entirely within the display bounds should be rejected.
        val outOfBoundsTestCases =
            outOfBoundsCoordinates.flatMap { (x, y) ->
                listOf(
                    // From center to out-of-bounds and back
                    arrayOf(centerX, centerY, x, y),
                    arrayOf(x, y, centerX, centerY)
                )
            } +
                listOf(
                    // From one out-of-bounds point to another, intersecting the screen
                    arrayOf(
                        outOfBoundsCoordinates[5].first,
                        centerY,
                        outOfBoundsCoordinates[0].first,
                        centerY
                    ), // Horizontal
                    arrayOf(
                        centerX,
                        outOfBoundsCoordinates[6].second,
                        centerX,
                        outOfBoundsCoordinates[1].second
                    ) // Vertical
                )

        for (coords in outOfBoundsTestCases) {
            val (x1, y1, x2, y2) = coords
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.swipe(x1, y1, x2, y2)
            }
        }
    }

    @Test
    fun testPerformAction_GoBack() = launchTestApp().use { testAppAgent ->
        // 1 is the action code for GoBack.
        testAppAgent.performAction(1)

        val goBack = testAppAgent.nextAction(Action.GoBack::class.java)
        assertThat(goBack).isNotNull()
    }

    @Test
    fun testGetDisplaySize() = launchTestApp().use { testAppAgent ->
        val screenSize = testAppAgent.getDisplaySize()
        assertThat(screenSize.width).isEqualTo(bounds.width())
        assertThat(screenSize.height).isEqualTo(bounds.height())
    }

    @Test
    fun testTap_edge() = launchTestApp().use { testAppAgent ->
        // Test tapping at the corners and edges of the screen.
        for ((x, y) in edgeCoordinates) {
            testAppAgent.tap(x, y)

            val tap = testAppAgent.nextAction(Action.Tap::class.java)
            assertThat(tap).isNotNull()
            tap!!
            assertThat(tap.x).isEqualTo(x)
            assertThat(tap.y).isEqualTo(y)
        }
    }

    @Test
    fun testTap_outOfBounds() = launchTestApp().use { testAppAgent ->
        // Test tapping outside the screen bounds.
        for ((x, y) in outOfBoundsCoordinates) {
            assertThrows(IllegalArgumentException::class.java) { testAppAgent.tap(x, y) }
        }
    }

    @Test
    fun testGetScreenshot() = launchTestApp().use { testAppAgent ->
        val screenshot = testAppAgent.getScreenshot()
        assertThat(screenshot).isNotNull()
        screenshot!!.use {
            assertThat(it.width).isEqualTo(bounds.width())
            assertThat(it.height).isEqualTo(bounds.height())
        }
    }

    @Test
    fun testInsertText() = launchTestApp(TEST_APP_CLASS_NAME).use { testAppAgent ->
        // Insert text1 to text field 1.
        val text1 = "Hello World"
        val text2 = "Goodbye World"
        testAppAgent.requestFocus(Constants.TEXT_FIELD_1)
        testAppAgent.insertText(text1)
        var insertText1 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText1).isNotNull()
        insertText1!!
        assertThat(insertText1.textFieldId).isEqualTo(Constants.TEXT_FIELD_1)
        assertThat(insertText1.text).isEqualTo(text1)

        // Insert text2 to text field 1 again.
        testAppAgent.insertText(text2)
        insertText1 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText1).isNotNull()
        insertText1!!
        assertThat(insertText1.textFieldId).isEqualTo(Constants.TEXT_FIELD_1)
        assertThat(insertText1.text).isEqualTo(text2)

        // Insert text2 to text field 2.
        testAppAgent.requestFocus(Constants.TEXT_FIELD_2)
        testAppAgent.insertText(text2)
        val insertText2 = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
        assertThat(insertText2).isNotNull()
        insertText2!!
        assertThat(insertText2.textFieldId).isEqualTo(Constants.TEXT_FIELD_2)
        assertThat(insertText2.text).isEqualTo(text2)
    }

    @Test
    fun testInsertText_combinations() = launchTestApp(TEST_APP_CLASS_NAME).use { testAppAgent ->
        val textFieldId = Constants.TEXT_FIELD_1
        testAppAgent.requestFocus(textFieldId)

        fun assertText(expectedText: String, expectedUncommittedText: String) {
            val change = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
            assertWithMessage(
                "TextFieldValueChange action was not received within the timeout"
            )
                .that(change)
                .isNotNull()
            val nonNullChange = change!!
            assertThat(nonNullChange.textFieldId).isEqualTo(textFieldId)
            assertThat(nonNullChange.text).isEqualTo(expectedText)
            if (expectedUncommittedText.isEmpty()) {
                assertThat(nonNullChange.uncommittedText).isAnyOf(null, "")
            } else {
                assertThat(nonNullChange.uncommittedText).isEqualTo(expectedUncommittedText)
            }
        }
        // Initial state is empty.

        // Insert "text1", don't replace, commit.
        testAppAgent.insertText("text1", replaceExisting = false, commitText = true)
        assertText("text1", "")

        // Insert "text2" to append, don't replace, commit.
        testAppAgent.insertText(" text2", replaceExisting = false, commitText = true)
        assertText("text1 text2", "")

        // Insert " text3" as composing text, don't replace, don't commit.
        testAppAgent.insertText(
            " text3", replaceExisting = false, commitText = false, waitForStable = false)
        assertText("text1 text2 text3", "")

        // Insert " text4" to append to composing text, don't replace, don't commit.
        testAppAgent.insertText(
            " text4", replaceExisting = false, commitText = false, waitForStable = false)
        assertText("text1 text2 text3 text4", "")

        // Commit the composing text by inserting an empty string, then append.
        testAppAgent.insertText("", replaceExisting = false, commitText = true)
        assertText("text1 text2 text3 text4", "")
        testAppAgent.insertText(" text5", replaceExisting = false, commitText = true)
        assertText("text1 text2 text3 text4 text5", "")

        // Replace all text and commit.
        testAppAgent.insertText("replaced", replaceExisting = true, commitText = true)
        assertText("replaced", "")

        // Replace text with composing text, don't commit.
        testAppAgent.insertText(
            "new composing", replaceExisting = true, commitText = false, waitForStable = false)
        assertText("new composing", "")

        // Append to the new composing text, don't commit.
        testAppAgent.insertText(
            " more", replaceExisting = false, commitText = false, waitForStable = false)
        assertText("new composing more", "")

        // Commit the current composing text, then append.
        testAppAgent.insertText("", replaceExisting = false, commitText = true)
        assertText("new composing more", "")
        testAppAgent.insertText(" final", replaceExisting = false, commitText = true)
        assertText("new composing more final", "")

        // Replace with empty string and commit to clear the text field.
        testAppAgent.insertText("", replaceExisting = true, commitText = true)
        assertText("", "")
    }

    @Test
    fun testInsertText_noFocus_isNoOp() =
        launchTestApp(TEST_APP_CLASS_NAME).use { testAppAgent ->
            // Insert text without focusing on any text field.
            testAppAgent.insertText("should not appear", replaceExisting = false, commitText = true)
            testAppAgent.insertText("should not appear", replaceExisting = true, commitText = true)
            testAppAgent.insertText("should not appear", replaceExisting = false, commitText = false)

            // Expect no text field value change action to be received.
            val noAction = testAppAgent.nextAction(Action.TextFieldValueChange::class.java)
            assertThat(noAction).isNull()
        }

    @Test
    fun testLaunchMultipleTimes() {
        for (i in 1..5) {
            launchTestApp().use { testAppAgent ->
                assertThat(testAppAgent).isNotNull()
                // Perform a simple action to ensure session is usable.
                val displaySize = testAppAgent.getDisplaySize()
                assertThat(displaySize.width).isGreaterThan(0)
                assertThat(displaySize.height).isGreaterThan(0)
            }
        }
    }

    companion object {
        private const val TEST_APP_PACKAGE_NAME = "android.computercontrol.testapp"
        private const val TEST_APP_CLASS_NAME = "android.computercontrol.testapp.app.AppActivity"
    }
}