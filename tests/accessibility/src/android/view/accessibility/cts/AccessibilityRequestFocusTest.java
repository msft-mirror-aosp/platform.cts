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

package android.view.accessibility.cts;

import static android.view.accessibility.Flags.FLAG_A11Y_SEQUENTIAL_FOCUS_STARTING_POINT;

import static com.google.common.truth.Truth.assertThat;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link AccessibilityRequestFocusTest} is set up to exercise cases where input focus interacts
 * with accessibility focus.
 */
@RunWith(Parameterized.class)
public class AccessibilityRequestFocusTest {
    private String mAccessibilityFocusId;
    private View mAccessibilityFocus;
    private int mKeyCode;
    int mMetaState;
    private String mFocusId;
    private View mFocus;

    @Rule public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public ActivityScenarioRule<AccessibilityRequestFocusActivity> activityRule =
            new ActivityScenarioRule<>(AccessibilityRequestFocusActivity.class);

    public AccessibilityRequestFocusTest(
            String accessibilityFocusId, String keyCode, int metaState, String focusId) {
        mAccessibilityFocusId = accessibilityFocusId;
        mKeyCode = KeyEvent.keyCodeFromString(keyCode);
        mMetaState = metaState;
        mFocusId = focusId;
    }

    @Parameterized.Parameters(name = "{0}_{1}_{2}_{3}")
    public static Collection<Object[]> getParameters() {
        // Each expectation lists the accessibility focus, the key pressed, and the resulting input
        // focus restored.
        Object[][] expectations = {
            {"topLeftButton", "KEYCODE_DPAD_UP", 0, "bottomRightButton"},
            {"topLeftButton", "KEYCODE_DPAD_DOWN", 0, "bottomLeftButton"},
            {"topLeftButton", "KEYCODE_DPAD_LEFT", 0, "bottomRightButton"},
            {"topLeftButton", "KEYCODE_DPAD_RIGHT", 0, "bottomLeftButton"},
            {"topLeftButton", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "bottomRightButton"},
            {"topLeftButton", "KEYCODE_TAB", 0, "bottomLeftButton"},
            {"bottomLeftButton", "KEYCODE_DPAD_UP", 0, "topLeftButton"},
            {"bottomLeftButton", "KEYCODE_DPAD_DOWN", 0, "topRightButton"},
            {"bottomLeftButton", "KEYCODE_DPAD_LEFT", 0, "topLeftButton"},
            {"bottomLeftButton", "KEYCODE_DPAD_RIGHT", 0, "topRightButton"},
            {"bottomLeftButton", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "topLeftButton"},
            {"bottomLeftButton", "KEYCODE_TAB", 0, "topRightButton"},
            {"topRightButton", "KEYCODE_DPAD_UP", 0, "bottomLeftButton"},
            {"topRightButton", "KEYCODE_DPAD_DOWN", 0, "bottomRightButton"},
            {"topRightButton", "KEYCODE_DPAD_LEFT", 0, "bottomLeftButton"},
            {"topRightButton", "KEYCODE_DPAD_RIGHT", 0, "bottomRightButton"},
            {"topRightButton", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "bottomLeftButton"},
            {"topRightButton", "KEYCODE_TAB", 0, "bottomRightButton"},
            {"bottomRightButton", "KEYCODE_DPAD_UP", 0, "topRightButton"},
            {"bottomRightButton", "KEYCODE_DPAD_DOWN", 0, "topLeftButton"},
            {"bottomRightButton", "KEYCODE_DPAD_LEFT", 0, "topRightButton"},
            {"bottomRightButton", "KEYCODE_DPAD_RIGHT", 0, "topLeftButton"},
            {"bottomRightButton", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "topRightButton"},
            {"bottomRightButton", "KEYCODE_TAB", 0, "topLeftButton"},
            {"firstTextView", "KEYCODE_DPAD_UP", 0, ""},
            {"firstTextView", "KEYCODE_DPAD_DOWN", 0, "topLeftButton"},
            {"firstTextView", "KEYCODE_DPAD_LEFT", 0, ""},
            {"firstTextView", "KEYCODE_DPAD_RIGHT", 0, "topRightButton"},
            {"firstTextView", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "bottomRightButton"},
            {"firstTextView", "KEYCODE_TAB", 0, "topLeftButton"},
            {"leftTextView", "KEYCODE_DPAD_UP", 0, "topLeftButton"},
            {"leftTextView", "KEYCODE_DPAD_DOWN", 0, "bottomLeftButton"},
            {"leftTextView", "KEYCODE_DPAD_LEFT", 0, ""},
            {"leftTextView", "KEYCODE_DPAD_RIGHT", 0, "bottomRightButton"},
            {"leftTextView", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "bottomRightButton"},
            {"leftTextView", "KEYCODE_TAB", 0, "topLeftButton"},
            {"lastTextView", "KEYCODE_DPAD_UP", 0, "bottomRightButton"},
            {"lastTextView", "KEYCODE_DPAD_DOWN", 0, ""},
            {"lastTextView", "KEYCODE_DPAD_LEFT", 0, "bottomLeftButton"},
            {"lastTextView", "KEYCODE_DPAD_RIGHT", 0, ""},
            {"lastTextView", "KEYCODE_TAB", KeyEvent.META_SHIFT_ON, "bottomRightButton"},
            {"lastTextView", "KEYCODE_TAB", 0, "topLeftButton"},
        };

        return Arrays.asList(expectations);
    }

    @Before
    public void setUp() throws Throwable {
        enableTouchAccessibility();
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);

        activityRule
                .getScenario()
                .onActivity(
                        a -> {
                            String packageName = a.getPackageName();
                            mAccessibilityFocus =
                                    (View)
                                            a.findViewById(
                                                    a.getResources()
                                                            .getIdentifier(
                                                                    mAccessibilityFocusId,
                                                                    "id",
                                                                    packageName));
                            mFocus =
                                    (View)
                                            a.findViewById(
                                                    a.getResources()
                                                            .getIdentifier(
                                                                    mFocusId, "id", packageName));

                            // Tests should always supply an accessibility focus.
                            assertThat(mAccessibilityFocus).isNotNull();

                            // Tests can supply an empty focus.
                            if (!mFocusId.isEmpty()) {
                                assertThat(mFocus).isNotNull();
                            }

                            // Place a11y focus.
                            mAccessibilityFocus.performAccessibilityAction(
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                            assertThat(mAccessibilityFocus.isAccessibilityFocused()).isTrue();

                            // Clear the focus to ensure tests have no input focus to
                            // test focus restore.
                            if (a.getCurrentFocus() != null) {
                                a.getCurrentFocus().clearFocus();
                            }
                        });

        // Wait for no focus.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    @LargeTest
    @RequiresFlagsEnabled(FLAG_A11Y_SEQUENTIAL_FOCUS_STARTING_POINT)
    public void test() throws Throwable {
        // Focus moves directionally [up/down, left/right, tab/shift-tab] from top left, bottom
        // left, top right, bottom right.

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        KeyEvent eventDown =
                new KeyEvent(
                        /* downTime= */ 0,
                        /* eventTime= */ 0,
                        KeyEvent.ACTION_DOWN,
                        mKeyCode,
                        /* repeat= */ 0,
                        mMetaState);
        instrumentation.sendKeySync(eventDown);
        KeyEvent eventUp =
                new KeyEvent(
                        /* downTime= */ 0,
                        /* eventTime= */ 0,
                        KeyEvent.ACTION_UP,
                        mKeyCode,
                        /* repeat= */ 0,
                        mMetaState);
        instrumentation.sendKeySync(eventUp);
        instrumentation.waitForIdleSync();

        activityRule
                .getScenario()
                .onActivity(
                        a -> {
                            // Focus restores to the expected focus supplied by the test params.
                            assertThat(a.getCurrentFocus()).isEqualTo(mFocus);
                            assertThat(mAccessibilityFocus.isAccessibilityFocused()).isTrue();
                        });
    }

    private void enableTouchAccessibility() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo customServiceInfo = new AccessibilityServiceInfo();
        customServiceInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        customServiceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        customServiceInfo.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        uiAutomation.setServiceInfo(customServiceInfo);

        AccessibilityManager manager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(AccessibilityManager.class);
        PollingCheck.waitFor(
                10000,
                () -> {
                    return manager.isTouchExplorationEnabled();
                });
    }
}
