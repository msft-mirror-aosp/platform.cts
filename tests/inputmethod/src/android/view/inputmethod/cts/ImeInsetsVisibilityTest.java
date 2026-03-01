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

package android.view.inputmethod.cts;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.isInputMethodPickerShown;

import static com.android.cts.mockime.ImeEventStreamTestUtils.DEFAULT_TIMEOUT;
import static com.android.cts.mockime.ImeEventStreamTestUtils.NOT_EXPECT_TIMEOUT;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.showSoftInputMatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.AppModeFull;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.FixedDeviceOrientationSession;
import android.view.inputmethod.cts.util.FixedDeviceOrientationSession.Orientation;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestActivity2;
import android.view.inputmethod.cts.util.TestUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

@MediumTest
public final class ImeInsetsVisibilityTest extends EndToEndImeTestBase {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final int NEW_KEYBOARD_HEIGHT = 300;

    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testImeVisibilityWhenImeFocusableChildPopup() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (var imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), DEFAULT_TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), DEFAULT_TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectEventWithKeyValue(
                    stream, "onWindowVisibilityChanged", "visible", View.VISIBLE, DEFAULT_TIMEOUT);
            PollingCheck.check(
                    "Ime insets should be visible",
                    DEFAULT_TIMEOUT.toMillis(),
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(DEFAULT_TIMEOUT);

            try (ChildWindowHolder childWindow = createChildTransparentApplicationWindowOnMain(
                    activity, 200 /* width */, 200 /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM,
                    WindowInsets.Type.ime() | WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars())) {
                // The window will be shown above (in y-axis) the IME.
                TestUtils.runOnMainSync(
                        () -> childWindow.getRootView().setVisibility(View.VISIBLE));
                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        DEFAULT_TIMEOUT,
                        "Ime insets should be visible");
                expectImeVisible(DEFAULT_TIMEOUT);
            }
        }
    }

    @Test
    public void testImeVisibilityWhenImeFocusableGravityBottomChildPopup() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), DEFAULT_TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), DEFAULT_TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            PollingCheck.check(
                    "Ime insets should be visible",
                    DEFAULT_TIMEOUT.toMillis(),
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(DEFAULT_TIMEOUT);

            stream.skipAll();
            try (ChildWindowHolder childWindow = createChildBottomPanelWindowOnMain(activity,
                    MATCH_PARENT /* width */, NEW_KEYBOARD_HEIGHT /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM)) {
                // The window will be shown above (in y-axis) the IME.
                TestUtils.runOnMainSync(() -> {
                    childWindow.getRootView().setBackgroundColor(Color.RED);
                    childWindow.getRootView().setVisibility(View.VISIBLE);
                });
                // IME should be on screen without reset.
                notExpectEvent(
                        stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);

                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        DEFAULT_TIMEOUT,
                        "Ime insets should be visible");
                expectImeVisible(DEFAULT_TIMEOUT);
            }
        }
    }

    @Test
    public void testImeVisibilityWhenImeFocusableChildPopupOverlaps() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), DEFAULT_TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), DEFAULT_TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            PollingCheck.check(
                    "Ime insets should be visible",
                    DEFAULT_TIMEOUT.toMillis(),
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(DEFAULT_TIMEOUT);

            stream.skipAll();
            try (ChildWindowHolder childWindow = createChildBottomPanelWindowOnMain(activity,
                    MATCH_PARENT /* width */, NEW_KEYBOARD_HEIGHT /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM | FLAG_LAYOUT_IN_SCREEN)) {
                // The window will be shown behind (in z-axis) the IME.
                TestUtils.runOnMainSync(() -> {
                    childWindow.getRootView().setBackgroundColor(Color.RED);
                    childWindow.getRootView().setVisibility(View.VISIBLE);
                });
                // IME should be on screen without reset.
                notExpectEvent(
                        stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);

                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        DEFAULT_TIMEOUT,
                        "Ime insets should be visible");
                expectImeVisible(DEFAULT_TIMEOUT);
            }
        }
    }

    @RequireNotAutomotive(reason = "IME show picker is disabled on automotive")
    @AppModeFull(reason = "Instant apps cannot rely on ACTION_CLOSE_SYSTEM_DIALOGS")
    @Test
    public void testEditTextPositionAndPersistWhenAboveImeWindowShown() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(true, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;
            final WindowInsets[] insetsFromActivity = new WindowInsets[1];
            Point curEditPos = getLocationOnScreenForView(editText);

            TestUtils.runOnMainSync(() -> activity.getWindow().getDecorView()
                    .setOnApplyWindowInsetsListener((v, insets) -> insetsFromActivity[0] = insets));

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), DEFAULT_TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), DEFAULT_TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectEventWithKeyValue(
                    stream, "onWindowVisibilityChanged", "visible", View.VISIBLE, DEFAULT_TIMEOUT);
            expectImeVisible(DEFAULT_TIMEOUT);

            Point lastEditTextPos = new Point(curEditPos);
            curEditPos = getLocationOnScreenForView(editText);
            // Watch doesn't support navigation bar and has limited screen size, so no transition
            // in EditText with respect to x and y coordinates
            Configuration config = mInstrumentation
                    .getContext()
                    .getResources()
                    .getConfiguration();
            boolean isSmallScreenLayout =
                    config.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_SMALL);

            if (isSmallScreenLayout) {
                assertTrue("Insets should visible",
                        isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime()));
            } else {
                assertTrue("Insets should visible and EditText position should be adjusted",
                        isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime())
                                && curEditPos.y < lastEditTextPos.y);
            }

            imm.showInputMethodPicker();
            TestUtils.waitOnMainUntil(
                    () -> isInputMethodPickerShown(imm) && editText.isLaidOut(),
                    DEFAULT_TIMEOUT,
                    "InputMethod picker should be shown");
            lastEditTextPos = new Point(curEditPos);
            curEditPos = getLocationOnScreenForView(editText);

            assertTrue("Insets visibility & EditText position should persist when "
                            + "the above IME window shown",
                    isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime())
                            && curEditPos.equals(lastEditTextPos));

            mInstrumentation.getContext().sendBroadcast(
                    new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
            TestUtils.waitOnMainUntil(
                    () -> !isInputMethodPickerShown(imm),
                    DEFAULT_TIMEOUT,
                    "InputMethod picker should be closed");
        }
    }

    /**
     * Test the IME window won't cover the editor when the app creates a panel window to receive the
     * IME insets.
     *
     * <p>Regression test for Bug 195765264 and Bug 152304051.
     */
    @Test
    public void testEditorWontCoveredByImeWhenInputWindowBehindPanel_fullscreenWindow()
            throws Exception {
        runEditorWontCoveredByImeWhenInputWindowBehindPanel(WINDOWING_MODE_FULLSCREEN);
    }

    /**
     * Test the IME window won't cover the editor when the app creates a panel window to receive the
     * IME insets.
     *
     * <p>Regression test for Bug 195765264 and Bug 152304051.
     */
    @Test
    public void testEditorWontCoveredByImeWhenInputWindowBehindPanel_freeformWindow()
            throws Exception {
        assumeTrue(isFreeformSupported());
        runEditorWontCoveredByImeWhenInputWindowBehindPanel(WINDOWING_MODE_FREEFORM);
    }

    private void runEditorWontCoveredByImeWhenInputWindowBehindPanel(int windowingMode)
            throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();

            // Launch a test activity with SOFT_INPUT_ADJUST_NOTHING to not resize by IME insets.
            final TestActivity testActivity =
                    new TestActivity.Starter()
                            .asNewTask()
                            .withWindowingMode(windowingMode)
                            .startSync(
                                    activity -> {
                                        final LinearLayout layout = new LinearLayout(activity);
                                        layout.setOrientation(LinearLayout.VERTICAL);
                                        layout.setGravity(Gravity.BOTTOM);
                                        final EditText editText = new EditText(activity);
                                        editText.setHint("focused editText");
                                        editText.setPrivateImeOptions(marker);
                                        // Initial editor visibility as GONE for testing IME
                                        // visibility controlled by panel.
                                        editText.setVisibility(View.GONE);
                                        editTextRef.set(editText);
                                        layout.addView(editText);
                                        activity.getWindow()
                                                .setSoftInputMode(
                                                        WindowManager.LayoutParams
                                                                .SOFT_INPUT_ADJUST_NOTHING);
                                        return layout;
                                    },
                                    TestActivity.class);
            final EditText editText = editTextRef.get();
            // Create a panel window to receive IME insets for adjusting editText position.
            final View panelView = TestUtils.getOnMainSync(() -> {
                final View panel = new View(testActivity);
                panel.setOnApplyWindowInsetsListener((v, insets) -> {
                    if (insets.isVisible(WindowInsets.Type.ime())) {
                        // Request editText focused when IME insets visible.
                        editText.setVisibility(View.VISIBLE);
                        editText.requestFocus();
                        LinearLayout.LayoutParams lp =
                                (LinearLayout.LayoutParams) editText.getLayoutParams();
                        lp.setMargins(0, 0, 0, editText.getRootView().getMeasuredHeight()
                                - panel.getMeasuredHeight());
                        editText.requestLayout();
                    } else {
                        // Clear editText focused when IME insets invisible.
                        editText.clearFocus();
                        editText.setVisibility(View.GONE);
                    }
                    return insets;
                });
                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        1, MATCH_PARENT,
                        0, 0, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                        PixelFormat.TRANSLUCENT);
                lp.setFitInsetsTypes(WindowInsets.Type.ime() | WindowInsets.Type.systemBars());
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                lp.token = testActivity.getWindow().getDecorView().getWindowToken();
                testActivity.getWindowManager().addView(panel, lp);
                return panel;
            });
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);
            // Show IME by using WindowInsets API.
            testActivity.getWindow().getInsetsController().show(WindowInsets.Type.ime());
            TestUtils.waitOnMainUntil(
                    () -> isInsetsVisible(panelView.getRootWindowInsets(), WindowInsets.Type.ime()),
                    DEFAULT_TIMEOUT,
                    "The panel should receive IME insets");
            TestUtils.waitOnMainUntil(
                    () -> editText.getVisibility() == View.VISIBLE && editText.hasFocus(),
                    DEFAULT_TIMEOUT,
                    "The editor should be shown and visible");
            expectEvent(stream, editorMatcher("onStartInput", marker), DEFAULT_TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
            expectImeVisible(DEFAULT_TIMEOUT);
        }
    }

    /**
     * Regression test for bug 381134667.
     *
     * <p>This verifies that in split screen, windows (that is stretching end-to-end) receive insets
     * change exactly once on IME show and hide animation.
     */
    @Test
    public void testImeInsetsChangesOnceInSplitScreenE2EApp() throws Exception {
        assumeTrue(TestUtils.supportsSplitScreenMultiWindow());

        class InsetsListener implements View.OnApplyWindowInsetsListener {
            private boolean mInsetsVisible = false;
            private boolean mImeInsetsHasSize = false;
            int mInsetsVisibilityToggleCount;
            int mInsetsSizeToggleCount;

            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                boolean wasVisible = mInsetsVisible;
                boolean isVisible = insets.isVisible(WindowInsets.Type.ime());
                if (wasVisible != isVisible) {
                    mInsetsVisibilityToggleCount++;
                }
                mInsetsVisible = isVisible;

                boolean hadSize = mImeInsetsHasSize;
                boolean hasSize = insets.getInsets(WindowInsets.Type.ime()).bottom > 0;
                if (hadSize != hasSize) {
                    mInsetsSizeToggleCount++;
                }
                mImeInsetsHasSize = hasSize;
                return v.onApplyWindowInsets(insets);
            }

            private void resetCount() {
                mInsetsVisibilityToggleCount = 0;
                mInsetsSizeToggleCount = 0;
            }

            private void assertChangeCount(String message, int expected) {
                assertEquals(message, expected, mInsetsVisibilityToggleCount);
                assertEquals(message, expected, mInsetsSizeToggleCount);
            }
        }
        InsetsListener primaryInsetsListener = new InsetsListener();
        InsetsListener secondaryInsetsListener = new InsetsListener();

        try (var orientationSession = new FixedDeviceOrientationSession(Orientation.LANDSCAPE);
                MockImeSession imeSession =
                        MockImeSession.create(
                                mInstrumentation.getContext(),
                                mInstrumentation.getUiAutomation(),
                                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            // Launch primary activity
            final AtomicReference<EditText> primaryEditTextRef = new AtomicReference<>();
            final TestActivity primaryActivity =
                    new TestActivity.Starter()
                            .asNewTask()
                            .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .fitsSystemWindows(false)
                            .startSync(
                                    activity -> {
                                        final LinearLayout layout = new LinearLayout(activity);
                                        layout.setOrientation(LinearLayout.VERTICAL);
                                        layout.setGravity(Gravity.CENTER);
                                        final EditText editText = new EditText(activity);
                                        primaryEditTextRef.set(editText);
                                        layout.addView(editText);
                                        editText.setHint("Primary EditText");
                                        editText.setPrivateImeOptions(marker);
                                        editText.getRootView()
                                                .setOnApplyWindowInsetsListener(
                                                        primaryInsetsListener);
                                        editText.getRootView().setFitsSystemWindows(false);
                                        return layout;
                                    },
                                    TestActivity.class);
            expectImeInvisible(DEFAULT_TIMEOUT);

            // Launch secondary activity in split screen
            final TestActivity secondaryActivity =
                    new TestActivity.Starter()
                            .asMultipleTask()
                            .withAdditionalFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                            .fitsSystemWindows(false)
                            .startSync(
                                    primaryActivity,
                                    activity -> {
                                        final LinearLayout layout = new LinearLayout(activity);
                                        layout.getRootView()
                                                .setOnApplyWindowInsetsListener(
                                                        secondaryInsetsListener);
                                        return layout;
                                    },
                                    TestActivity2.class);
            TestUtils.waitOnMainUntil(secondaryActivity::hasWindowFocus, DEFAULT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            final boolean isVerticallySplit = isVerticallySplit(primaryActivity, secondaryActivity);

            final EditText primaryEditText = primaryEditTextRef.get();
            final var display = primaryEditText.getContext().getDisplay();
            try (var touch = new UinputTouchScreen(mInstrumentation, display)) {
                primaryInsetsListener.resetCount();
                secondaryInsetsListener.resetCount();

                // Tap the editor on the primary task to focus the window and show the IME.
                touch.tapOnViewCenter(primaryEditText);
                TestUtils.waitOnMainUntil(primaryEditText::hasWindowFocus, DEFAULT_TIMEOUT);
                // Next tap to show the IME.
                touch.tapOnViewCenter(primaryEditText);

                expectEvent(stream, editorMatcher("onStartInputView", marker), DEFAULT_TIMEOUT);
                expectImeVisible(DEFAULT_TIMEOUT);

                mInstrumentation.waitForIdleSync();

                if (!isVerticallySplit) {
                    primaryInsetsListener.assertChangeCount(
                            "Insets should become visible once on primary activity", 1);
                }
                secondaryInsetsListener.assertChangeCount(
                        "Insets should become visible once on secondary activity", 1);

                // Resets the state.
                primaryInsetsListener.resetCount();
                secondaryInsetsListener.resetCount();

                // Tap the split secondary task to switch focus and hide IME.
                View secondaryDecorView = secondaryActivity.getWindow().getDecorView();
                Insets insets =
                        secondaryDecorView
                                .getRootWindowInsets()
                                .getInsets(
                                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                int availableHeight = secondaryDecorView.getHeight() - insets.bottom - insets.top;
                int[] xy = new int[2];
                secondaryDecorView.getLocationOnScreen(xy);
                xy[0] += secondaryDecorView.getWidth() / 2;
                xy[1] += insets.top + availableHeight / 2;
                touch.touchDown(xy[0], xy[1]).lift();
            }

            expectEvent(stream, hideSoftInputMatcher(), DEFAULT_TIMEOUT);
            expectEvent(stream, eventMatcher("onFinishInputView"), DEFAULT_TIMEOUT);
            expectEventWithKeyValue(
                    stream, "onWindowVisibilityChanged", "visible", View.GONE, DEFAULT_TIMEOUT);
            expectImeInvisible(DEFAULT_TIMEOUT);

            mInstrumentation.waitForIdleSync();

            if (!isVerticallySplit) {
                primaryInsetsListener.assertChangeCount(
                        "Insets should become invisible once on primary activity", 1);
            }
            secondaryInsetsListener.assertChangeCount(
                    "Insets should become invisible once on secondary activity", 1);
        }
    }

    private boolean isInsetsVisible(WindowInsets winInsets, int type) {
        if (winInsets == null) {
            return false;
        }
        return winInsets.isVisible(type);
    }

    private Point getLocationOnScreenForView(View view) {
        return TestUtils.getOnMainSync(() -> {
            final int[] tmpPos = new int[2];
            view.getLocationOnScreen(tmpPos);
            return new Point(tmpPos[0], tmpPos[1]);
        });
    }

    @NonNull
    private static Pair<EditText, TestActivity> launchTestActivity(boolean useDialogTheme,
            @NonNull String focusedMarker) {
        final var focusedEditTextRef = new AtomicReference<EditText>();
        final var testActivity = TestActivity.startSync(activity -> {
            final var layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.BOTTOM);
            if (useDialogTheme) {
                // Create a floating Dialog
                activity.setTheme(android.R.style.Theme_Material_Dialog);
                final var textView = new TextView(activity);
                textView.setText("I'm a TextView");
                textView.setHeight(activity.getWindowManager().getMaximumWindowMetrics()
                        .getBounds().height() / 3);
                layout.addView(textView);
            }

            final var focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), testActivity);
    }

    /**
     * A utility class to pack the root {@link View} and its clean-up operation that is compatible
     * with {@link AutoCloseable} protocol.
     */
    private static final class ChildWindowHolder implements AutoCloseable {
        @NonNull
        private final View mRootView;

        private ChildWindowHolder(@NonNull View rootView) {
            mRootView = rootView;
        }

        @NonNull
        @AnyThread
        View getRootView() {
            return mRootView;
        }

        @Override
        public void close() {
            TestUtils.runOnMainSync(() -> mRootView.getContext()
                    .getSystemService(WindowManager.class).removeView(mRootView));
        }
    }

    @NonNull
    private ChildWindowHolder createChildBottomPanelWindowOnMain(Activity activity, int width,
            int height, int windowFlags) {
        return TestUtils.getOnMainSync(() -> {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.token = null;
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            attrs.width = width;
            attrs.height = height;
            attrs.gravity = Gravity.BOTTOM;
            attrs.flags = windowFlags;
            final View childViewRoot = new View(activity);
            activity.getSystemService(WindowManager.class).addView(childViewRoot, attrs);
            return new ChildWindowHolder(childViewRoot);
        });
    }

    @NonNull
    private ChildWindowHolder createChildTransparentApplicationWindowOnMain(Activity activity,
            int width, int height, int windowFlags, int fitInsetsTypes) {
        return TestUtils.getOnMainSync(() -> {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.token = activity.getWindow().getAttributes().token;
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            attrs.width = width;
            attrs.height = height;
            attrs.format = PixelFormat.TRANSPARENT;
            attrs.gravity = Gravity.NO_GRAVITY;
            attrs.flags = windowFlags;
            attrs.setFitInsetsTypes(fitInsetsTypes);
            final View childViewRoot = new View(activity);
            activity.getSystemService(WindowManager.class).addView(childViewRoot, attrs);
            return new ChildWindowHolder(childViewRoot);
        });
    }

    @NonNull
    private static InputMethodManager getImmOrFail() {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        assertNotNull(imm);
        return imm;
    }

    private static boolean isVerticallySplit(Activity act1, Activity act2) {
        final Rect r1 = act1.getWindowManager().getCurrentWindowMetrics().getBounds();
        final Rect r2 = act2.getWindowManager().getCurrentWindowMetrics().getBounds();
        return r1.left == r2.left && r1.right == r2.right && r1.bottom <= r2.top;
    }
}
