/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.ime;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.server.wm.InputMethodVisibilityVerifier.expectImeVisible;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
import static android.view.WindowManager.DISPLAY_IME_POLICY_HIDE;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.DisplayContent;
import android.server.wm.WindowManagerState.WindowState;
import android.server.wm.intent.Activities;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceIme:MultiDisplayImeTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplayImeTests extends MultiDisplayTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
    }

    // TODO(b/383228193): Remove this method once fallbackDisplayForSecondaryUserOnSecondaryDisplay
    //     flag is promoted.
    @Override
    protected int getMainDisplayId() {
        if (!android.view.inputmethod.Flags.fallbackDisplayForSecondaryUserOnSecondaryDisplay()) {
            return DEFAULT_DISPLAY;
        }
        return super.getMainDisplayId();
    }

    @Test
    @FlakyTest(bugId = 419800528)
    public void testImeWindowCanSwitchToDifferentDisplays() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivity> activitySession1 =
                     createManagedTestActivitySession();
             TestActivitySession<ImeTestActivity2> activitySession2 =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                     .setSimulateDisplay(true)) {

            // Create a virtual display and launch an activity on it.
            final DisplayContent dc = displaySession.createDisplay();

            final ImeEventStream stream = imeSession.openEventStream();

            activitySession1.launchTestActivityOnDisplaySync(ImeTestActivity.class, dc.mId);
            final var activity1 = activitySession1.getActivity();
            assertWithMessage("Activity1 window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity1.getWindow()))
                    .isTrue();

            expectEvent(stream, editorMatcher("onStartInput",
                    activity1.getEditText().getPrivateImeOptions()), TIMEOUT);

            // Make the activity to show soft input.
            showSoftInputAndAssertImeShownOnDisplay(dc.mId, activitySession1, stream);

            // Assert the configuration of the IME window is the same as the configuration of the
            // virtual display.
            assertImeWindowAndDisplayConfiguration(mWmState.getImeWindowState(), dc);

            // Launch another activity on the main display of the user. When the test runs on the
            // current user, the display will be the default display.
            final int defDisplayId = getMainDisplayId();
            activitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class, defDisplayId);
            final var activity2 = activitySession2.getActivity();
            // The overlay for the virtual display is on the main display and on top of Activity2.
            assertWithMessage("Activity2 window is 1st from top, below Overlay")
                    .that(
                            CtsWindowInfoUtils.waitForNthWindowFromTop(
                                    Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                                    () -> activity2.getWindow().getDecorView().getWindowToken(),
                                    1 /* expectedOrder */))
                    .isTrue();
            expectEvent(stream, editorMatcher("onStartInput",
                    activity2.getEditText().getPrivateImeOptions()), TIMEOUT);

            // Make the activity to show soft input.
            showSoftInputAndAssertImeShownOnDisplay(defDisplayId, activitySession2, stream);

            // Assert the configuration of the IME window is the same as the configuration of the
            // main display of the user.
            assertImeWindowAndDisplayConfiguration(
                    mWmState.getImeWindowState(), mWmState.getDisplay(defDisplayId));
        }
    }

    /**
     * This checks that calling showSoftInput on the incorrect display, requiring the fallback IMM,
     * will not drop the statsToken tracking the show request.
     */
    @Test
    @Ignore("b/418178877")
    public void testFallbackImmMaintainsParameters() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivity> activitySession =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setSimulateDisplay(true)) {

            final DisplayContent dc = displaySession.createDisplay();

            activitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class, dc.mId);
            final var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();
            final var stream = imeSession.openEventStream();

            expectEvent(stream, editorMatcher("onStartInput",
                    activity.getEditText().getPrivateImeOptions()), TIMEOUT);

            activitySession.runOnMainSyncAndWait(activity::showSoftInput);

            expectImeVisible(TIMEOUT);
            PollingCheck.waitFor(() -> !imeSession.hasPendingImeVisibilityRequests(),
                    "No pending requests should remain after the IME is visible");
        }
    }

    @Test
    @FlakyTest(bugId = 419800528)
    public void testImeApiForBug118341760() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivityWithBrokenContextWrapper> activitySession =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setSimulateDisplay(true)) {

            // Create a virtual display and launch an activity on it.
            final DisplayContent dc = displaySession.createDisplay();
            activitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivityWithBrokenContextWrapper.class, dc.mId);
            final var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();
            final ImeEventStream stream = imeSession.openEventStream();
            final String privateImeOption = activity.getEditText().getPrivateImeOptions();
            expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInput", event.getEventName())) {
                    return false;
                }
                final var editorInfo = event.getArguments().getParcelable("editorInfo",
                        EditorInfo.class);
                assertNotNull(editorInfo);
                return TextUtils.equals(editorInfo.packageName, mContext.getPackageName())
                        && TextUtils.equals(editorInfo.privateImeOptions, privateImeOption);
            }, TIMEOUT);

            activitySession.runOnMainSyncAndWait(() -> {
                final InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
                assertNotNull(imm);
                assertTrue("InputMethodManager.isActive() should work",
                        imm.isActive(activity.getEditText()));
            });
        }
    }

    @Test
    public void testImeWindowCanSwitchWhenTopFocusedDisplayChange() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());

        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivity> activitySession1 =
                     createManagedTestActivitySession();
             TestActivitySession<ImeTestActivity2> activitySession2 =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setSimulateDisplay(true)
                     .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)) {

            // Create a virtual display and launch an activity on virtual & default display.
            final DisplayContent dc = displaySession.createDisplay();
            final int defDisplayId = getMainDisplayId();
            try (var touch1 =
                            new UinputTouchScreen(mInstrumentation, mDm.getDisplay(defDisplayId));
                    var touch2 = new UinputTouchScreen(mInstrumentation, mDm.getDisplay(dc.mId))) {

                activitySession1.launchTestActivityOnDisplaySync(
                        ImeTestActivity.class, defDisplayId);
                final var activity1 = activitySession1.getActivity();
                // The overlay for the virtual display is on the main display and on top
                // of Activity1.
                assertWithMessage("Activity2 window is 1st from top, below Overlay")
                        .that(
                                CtsWindowInfoUtils.waitForNthWindowFromTop(
                                        Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                                        () -> activity1.getWindow().getDecorView().getWindowToken(),
                                        1 /* expectedOrder */))
                        .isTrue();
                activitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class, dc.mId);
                final var activity2 = activitySession2.getActivity();
                assertWithMessage("Activity2 window is on top")
                        .that(CtsWindowInfoUtils.waitForWindowOnTop(activity2.getWindow()))
                        .isTrue();

                final ImeEventStream stream = imeSession.openEventStream();

                touch1.tapOnViewCenter(activity1.getWindow().getDecorView());
                mWmState.waitForWithAmState(
                        state -> state.getFocusedDisplayId() == defDisplayId,
                        "Default display should be focused");
                mWmState.waitAndAssertFocusedActivity(
                        "Test activity 1 should be focused", activity1.getComponentName());
                expectEvent(
                        stream,
                        editorMatcher(
                                "onStartInput", activity1.getEditText().getPrivateImeOptions()),
                        TIMEOUT);
                showSoftInputAndAssertImeShownOnDisplay(defDisplayId, activitySession1, stream);

                // Tap virtual display as top focused display & request focus on EditText to show
                // soft input.
                touchAndCancelOnDisplayCenter(dc, touch2);
                mWmState.waitForWithAmState(
                        state -> state.getFocusedDisplayId() == dc.mId,
                        "Virtual display should be focused");
                expectEvent(
                        stream,
                        editorMatcher(
                                "onStartInput", activity2.getEditText().getPrivateImeOptions()),
                        TIMEOUT);
                showSoftInputAndAssertImeShownOnDisplay(dc.mId, activitySession2, stream);

                touch1.tapOnViewCenter(activity1.getWindow().getDecorView());
                mWmState.waitForWithAmState(
                        state -> state.getFocusedDisplayId() == defDisplayId,
                        "Default display should be focused");
                expectEvent(
                        stream,
                        editorMatcher(
                                "onStartInput", activity1.getEditText().getPrivateImeOptions()),
                        TIMEOUT);
                showSoftInputAndAssertImeShownOnDisplay(defDisplayId, activitySession1, stream);
            }
        }
    }

    /**
     * Test that the IME can be shown in a different display (actually the default display) than
     * the display on which the target IME application is shown.  Then test several basic operations
     * in {@link InputConnection}.
     */
    @Test
    public void testCrossDisplayBasicImeOperations() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
                TestActivitySession<ImeTestActivity> activitySession =
                        createManagedTestActivitySession();
                var displaySession =
                        createManagedVirtualDisplaySession()
                                .setDisplayImePolicy(DISPLAY_IME_POLICY_FALLBACK_DISPLAY)
                                .setPublicDisplay(true)
                                .setSupportsTouch(true)) {

            // Create a virtual display by app and assume the display should not show IME window.
            final DisplayContent dc = displaySession.createDisplay();

            // Launch IME test activity in virtual display.
            activitySession.launchTestActivityOnDisplay(ImeTestActivity.class, dc.mId);
            final var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();

            final ImeEventStream stream = imeSession.openEventStream();

            // Expect onStartInput would be executed when user tapping on the
            // non-system created display intentionally.
            tapAndAssertEditorFocusedOnImeActivity(activity, dc.mId);
            expectEvent(stream, editorMatcher("onStartInput",
                    activity.getEditText().getPrivateImeOptions()), TIMEOUT);

            // Verify the activity to show soft input on the default display.
            showSoftInputAndAssertImeShownOnDisplay(getMainDisplayId(), activitySession, stream);

            // Commit text & make sure the input texts should be delivered to focused EditText on
            // virtual display.
            final EditText editText = activity.getEditText();
            final String commitText = "test commit";
            expectCommand(stream, imeSession.callCommitText(commitText, 1), TIMEOUT);
            activitySession.runOnMainAndAssertWithTimeout(
                    () -> TextUtils.equals(commitText, editText.getText()), TIMEOUT,
                    "The input text should be delivered");

            // Since the IME and the IME target app are running in different displays,
            // InputConnection#requestCursorUpdates() is not supported and it should return false.
            // See InputMethodServiceTest#testOnUpdateCursorAnchorInfo() for the normal scenario.
            final ImeCommand callCursorUpdates = imeSession.callRequestCursorUpdates(
                    InputConnection.CURSOR_UPDATE_IMMEDIATE);
            assertFalse(expectCommand(stream, callCursorUpdates, TIMEOUT).getReturnBooleanValue());
        }
    }

    /**
     * Test that the IME can be hidden with the {@link WindowManager#DISPLAY_IME_POLICY_HIDE} flag.
     */
    @Test
    public void testDisplayPolicyImeHideImeOperation() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivity> activitySession =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setDisplayImePolicy(DISPLAY_IME_POLICY_HIDE)
                     .setSimulateDisplay(true)) {

            // Create a virtual display and launch an activity on virtual display.
            final DisplayContent dc = displaySession.createDisplay();

            // Launch IME test activity and initial the editor focus on virtual display.
            activitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class, dc.mId);
            final var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();

            // Verify the activity is launched on the secondary display.
            assertThat(mWmState.hasActivityInDisplay(dc.mId, activity.getComponentName()))
                    .isTrue();

            // Verify invoking showSoftInput will be ignored when the display has the HIDE policy.
            final ImeEventStream stream = imeSession.openEventStream();
            activitySession.runOnMainSyncAndWait(activity::showSoftInput);
            notExpectEvent(stream, eventMatcher("showSoftInput"), NOT_EXPECT_TIMEOUT);
        }
    }

    /**
     * A regression test for Bug 273630528.
     *
     * <p>Test that the IME on the editor activity with embedded in virtual display will be hidden
     * after pressing the back key.
     */
    @Test
    @FlakyTest(bugId = 419800528)
    public void testHideImeWhenImeTargetOnEmbeddedVirtualDisplay() throws Exception {
        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<ImeTestActivity> activitySession =
                     createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setPublicDisplay(true)
                     .setSupportsTouch(true)) {

            // Setup a virtual display embedded on an activity.
            final DisplayContent dc = displaySession.createDisplay();

            // Launch a test activity on that virtual display and show IME by tapping the editor.
            activitySession.launchTestActivityOnDisplay(ImeTestActivity.class, dc.mId);
            final var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();

            tapAndAssertEditorFocusedOnImeActivity(activity, dc.mId);
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = activity.getEditText().getPrivateImeOptions();
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Expect soft-keyboard becomes visible after requesting show IME.
            showSoftInputAndAssertImeShownOnDisplay(getMainDisplayId(), activitySession, stream);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Pressing back key, expect soft-keyboard will become invisible.
            pressBackButton();
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testImeWindowCanShownWhenActivityMovedToDisplay() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());

        // Launch a regular activity on default display at the test beginning to prevent the test
        // may mis-touch the launcher icon that breaks the test expectation.
        try (TestActivitySession<Activities.RegularActivity> regularActivitySession =
                     createManagedTestActivitySession()) {
            final int defDisplayId = getMainDisplayId();
            regularActivitySession.launchTestActivityOnDisplaySync(
                    Activities.RegularActivity.class, defDisplayId);
            final var regularActivity = regularActivitySession.getActivity();
            assertWithMessage("Regular activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(regularActivity.getWindow()))
                    .isTrue();

            try (var imeSession = createManagedMockImeSession(this);
                 TestActivitySession<ImeTestActivity> activitySession =
                         createManagedTestActivitySession();
                 var displaySession = createManagedVirtualDisplaySession()
                         .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                         .setSimulateDisplay(true)) {

                // Create a virtual display and launch an activity on virtual display.
                final DisplayContent dc = displaySession.createDisplay();

                // Launch IME test activity and initial the editor focus on virtual display.
                activitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class, dc.mId);
                final var activity = activitySession.getActivity();
                assertWithMessage("Activity window is on top")
                        .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                        .isTrue();

                // Verify the activity is launched to the secondary display.
                final ComponentName componentName = activity.getComponentName();
                assertWithMessage("Test activity should be in virtual display")
                        .that(mWmState.hasActivityInDisplay(dc.mId, componentName))
                        .isTrue();

                try (var touch1 =
                                new UinputTouchScreen(
                                        mInstrumentation, mDm.getDisplay(defDisplayId));
                        var touch2 =
                                new UinputTouchScreen(mInstrumentation, mDm.getDisplay(dc.mId))) {
                    final DisplayContent defDc = mWmState.getDisplay(defDisplayId);
                    touchAndCancelOnDisplayCenter(defDc, touch1);
                    mWmState.waitForWithAmState(
                            state -> state.getFocusedDisplayId() == defDisplayId,
                            "Default display should be focused");
                    mWmState.waitForAppTransitionIdleOnDisplay(defDisplayId);
                    mWmState.assertValidity();

                    // Reparent ImeTestActivity from virtual display to default display.
                    getLaunchActivityBuilder()
                            .setUseInstrumentation()
                            .setTargetActivity(componentName)
                            .setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .allowMultipleInstances(false)
                            .setDisplayId(defDisplayId)
                            .execute();
                    waitAndAssertResumedAndFocusedActivityOnDisplay(
                            componentName,
                            defDisplayId,
                            "Test activity should be top-resumed on displayId: " + defDisplayId);

                    // Activity is no longer on the secondary display
                    assertWithMessage("Test activity is no longer on virtual display")
                            .that(mWmState.hasActivityInDisplay(dc.mId, componentName))
                            .isFalse();

                    final ImeEventStream stream = imeSession.openEventStream();
                    touch2.tapOnViewCenter(activity.getWindow().getDecorView());
                    mWmState.waitForWithAmState(
                            state -> state.getFocusedDisplayId() == defDisplayId,
                            "Default display should be focused");
                    expectEvent(
                            stream,
                            editorMatcher(
                                    "onStartInput", activity.getEditText().getPrivateImeOptions()),
                            TIMEOUT);

                    // Verify the activity shows soft input on the default display.
                    showSoftInputAndAssertImeShownOnDisplay(defDisplayId, activitySession, stream);
                }
            }
        }
    }

    @Test
    @FlakyTest(bugId = 419800528)
    public void testNoConfigurationChangedWhenSwitchBetweenTwoIdenticalDisplays() throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());

        try (var displaySession = createManagedVirtualDisplaySession()
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .setOwnContentOnly(true)
                .setSimulateDisplay(true)
                .setResizeDisplay(false);
             TestActivitySession<ImeTestActivity2> activitySession2 =
                     createManagedTestActivitySession()) {
            // Create two displays with the same display metrics
            final var newDcs = displaySession.createDisplays(2);
            final DisplayContent dc1 = newDcs.get(0);
            final DisplayContent dc2 = newDcs.get(1);

            // Skip if the test environment somehow didn't create 2 displays with identical size.
            assumeTrue(
                    "Skip the test if the size of the created displays aren't identical",
                    dc1.getDisplayRect().equals(dc2.getDisplayRect()));

            activitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class, dc2.mId);
            final var activity2 = activitySession2.getActivity();
            assertWithMessage("Activity2 window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity2.getWindow()))
                    .isTrue();

            try (var touch1 = new UinputTouchScreen(mInstrumentation, mDm.getDisplay(dc1.mId));
                    var touch2 = new UinputTouchScreen(mInstrumentation, mDm.getDisplay(dc2.mId))) {

                // Make firstDisplay the top focus display.
                touchAndCancelOnDisplayCenter(dc1, touch1);
                mWmState.waitForWithAmState(
                        state -> state.getFocusedDisplayId() == dc1.mId,
                        "First display should be focused");

                try (var imeSession = createManagedMockImeSession(this);
                        TestActivitySession<ImeTestActivity> activitySession1 =
                                createManagedTestActivitySession()) {
                    ImeEventStream stream = imeSession.openEventStream();
                    // Filter out onConfigurationChanged events in case that IME is moved from the
                    // default display to the firstDisplay.
                    ImeEventStream configChangeVerifyStream =
                            clearOnConfigurationChangedFromStream(stream);

                    activitySession1.launchTestActivityOnDisplaySync(
                            ImeTestActivity.class, dc1.mId);
                    final var activity1 = activitySession1.getActivity();
                    assertWithMessage("Activity1 window is on top")
                            .that(CtsWindowInfoUtils.waitForWindowOnTop(activity1.getWindow()))
                            .isTrue();

                    // Wait until IME is ready for the IME client to call showSoftInput().
                    expectEvent(stream, editorMatcher("onStartInput",
                            activity1.getEditText().getPrivateImeOptions()), TIMEOUT);

                    int imeDisplayId =
                            expectCommand(stream, imeSession.callGetDisplayId(), TIMEOUT)
                                    .getReturnIntegerValue();
                    assertThat(imeDisplayId).isEqualTo(dc1.mId);

                    activitySession1.runOnMainSyncAndWait(activity1::showSoftInput);
                    waitOrderedImeEventsThenAssertImeShown(
                            stream, dc1.mId, event -> "showSoftInput".equals(event.getEventName()));
                    try {
                        // Launch IME must not lead to screen size changes.
                        waitAndAssertImeNoScreenSizeChanged(configChangeVerifyStream);

                        final Rect currentBoundsOnFirstDisplay =
                                expectCommand(
                                                stream,
                                                imeSession.callGetCurrentWindowMetricsBounds(),
                                                TIMEOUT)
                                        .getReturnParcelableValue();

                        // Clear onConfigurationChanged events before IME moves to the secondary
                        // display to prevent flaky because IME may receive configuration updates
                        // which we don't care about. An example is CONFIG_KEYBOARD_HIDDEN.
                        configChangeVerifyStream = clearOnConfigurationChangedFromStream(stream);

                        // Tap secondDisplay to change it to the top focused display.
                        touchAndCancelOnDisplayCenter(dc2, touch2);
                        mWmState.waitForWithAmState(
                                state -> state.getFocusedDisplayId() == dc2.mId,
                                "Second display should be focused");

                        // Move ImeTestActivity from firstDisplay to secondDisplay.
                        getLaunchActivityBuilder()
                                .setUseInstrumentation()
                                .setTargetActivity(activity1.getComponentName())
                                .setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .allowMultipleInstances(false)
                                .setDisplayId(dc2.mId)
                                .execute();

                        // Make sure ImeTestActivity is move from the firstDisplay to the
                        // secondDisplay
                        waitAndAssertResumedAndFocusedActivityOnDisplay(
                                activity1.getComponentName(),
                                dc2.mId,
                                "Test activity should be top-resumed on displayId: " + dc2.mId);
                        assertThat(
                                        mWmState.hasActivityInDisplay(
                                                dc1.mId, activity1.getComponentName()))
                                .isFalse();
                        assertWithMessage("Activity1 window is on top")
                                .that(CtsWindowInfoUtils.waitForWindowOnTop(activity1.getWindow()))
                                .isTrue();
                        // Wait until IME is ready for the IME client to call showSoftInput().
                        expectEvent(
                                stream,
                                editorMatcher(
                                        "onStartInput",
                                        activity1.getEditText().getPrivateImeOptions()),
                                TIMEOUT);
                        imeDisplayId =
                                expectCommand(stream, imeSession.callGetDisplayId(), TIMEOUT)
                                        .getReturnIntegerValue();
                        assertThat(imeDisplayId).isEqualTo(dc2.mId);

                        // Moving IME to the display with the same display metrics must not lead to
                        // screen size changes.
                        waitAndAssertImeNoScreenSizeChanged(configChangeVerifyStream);

                        final Rect currentBoundsOnSecondDisplay =
                                expectCommand(
                                                stream,
                                                imeSession.callGetCurrentWindowMetricsBounds(),
                                                TIMEOUT)
                                        .getReturnParcelableValue();

                        assertWithMessage(
                                        "The current WindowMetrics bounds of IME should not change")
                                .that(currentBoundsOnFirstDisplay)
                                .isEqualTo(currentBoundsOnSecondDisplay);
                    } catch (AssertionError e) {
                        mWmState.computeState();
                        final Rect displayRect1 = mWmState.getDisplay(dc1.mId).getDisplayRect();
                        final Rect displayRect2 = mWmState.getDisplay(dc2.mId).getDisplayRect();
                        assumeTrue(
                                "Skip test since the size of displays changed unexpectedly",
                                displayRect1.equals(displayRect2));
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Verifies if the IME matches the associated display ID when IME is moved with relaunching
     * {@link Activity}.
     */
    @ApiTest(apis = {"android.view.inputmethod.InputMethodService#getDisplay"})
    @Test
    public void testDisplayIdUpdateWhenImeMove_RelaunchActivity() throws Exception {
        testDisplayIdUpdateWhenImeMove(true /* verifyRelaunch */);
    }

    /**
     * Verifies if the IME matches the associated display ID when IME is moved with no-relaunching
     * {@link Activity}.
     */
    @ApiTest(apis = {"android.view.inputmethod.InputMethodService#getDisplay"})
    @Test
    public void testDisplayIdUpdateWhenImeMove_NoRelaunchActivity() throws Exception {
        testDisplayIdUpdateWhenImeMove(false /* verifyRelaunch */);
    }

    /**
     * Verifies if the IME matches the associated display ID when IME is moved with {@link
     * Activity}.
     *
     * @param verifyRelaunch whether the verified activity is relaunched with display switch, or
     *                       receives {@link Activity#onConfigurationChanged(Configuration)},
     *                       otherwise.
     */
    private <T extends ImeTestActivity> void testDisplayIdUpdateWhenImeMove(boolean verifyRelaunch)
            throws Exception {
        // If config_perDisplayFocusEnabled, the focus will not move even if touching on
        // the Activity in the different display.
        assumeFalse(perDisplayFocusEnabled());

        final Class<T> activityClass = (verifyRelaunch)
                ? (Class<T>) ImeTestActivity2.class
                : (Class<T>) ImeTestActivity.class;

        try (var imeSession = createManagedMockImeSession(this);
             TestActivitySession<T> activitySession = createManagedTestActivitySession();
             var displaySession = createManagedVirtualDisplaySession()
                     .setSimulateDisplay(true)
                     .setShowSystemDecorations(true)
                     .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)) {

            final int defDisplayId = getMainDisplayId();
            activitySession.launchTestActivityOnDisplaySync(activityClass, defDisplayId);
            var activity = activitySession.getActivity();
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();

            final ImeEventStream stream = imeSession.openEventStream();

            assertImeMatchesDisplayId(activitySession, imeSession, stream, defDisplayId);

            final DisplayContent dc = displaySession.createDisplay();

            // Move ImeTestActivity from main display to new display
            if (verifyRelaunch) {
                activitySession.launchTestActivityOnDisplaySync(activityClass, dc.mId);
                activity = activitySession.getActivity();
            } else {
                getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(activity.getComponentName())
                        .setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .allowMultipleInstances(false)
                        .setDisplayId(dc.mId)
                        .execute();
            }
            assertWithMessage("Activity window is on top")
                    .that(CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow()))
                    .isTrue();

            // Make sure ImeTestActivity is move from the firstDisplay to the secondDisplay
            waitAndAssertResumedAndFocusedActivityOnDisplay(
                    activity.getComponentName(),
                    dc.mId,
                    "Test activity should be top-resumed on displayId: " + dc.mId);

            // Launch activity on the secondary display and make IME show.
            assertImeMatchesDisplayId(activitySession, imeSession, stream, dc.mId);
        }
    }

    private void assertImeMatchesDisplayId(
            @NonNull TestActivitySession<? extends ImeTestActivity> activitySession,
            @NonNull MockImeSession imeSession, @NonNull ImeEventStream stream,
            int targetDisplayId) throws Exception {
        // Wait until IME is ready for the IME client to call showSoftInput().
        expectEvent(stream, editorMatcher("onStartInput",
                        activitySession.getActivity().getEditText().getPrivateImeOptions()),
                TIMEOUT);

        final int imeDisplayId = expectCommand(stream, imeSession.callGetDisplayId(), TIMEOUT)
                .getReturnIntegerValue();
        assertThat(imeDisplayId).isEqualTo(targetDisplayId);
    }

    public static class ImeTestActivity extends Activity {

        private EditText mEditText;

        @Override
        protected void onCreate(@Nullable Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(this);
            // Set private IME option for editorMatcher to identify which TextView received
            // onStartInput event.
            resetPrivateImeOptionsIdentifier();
            final var layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            // SOFT_INPUT_STATE_UNSPECIFIED may produced unexpected behavior for CTS. To make tests
            // deterministic, using SOFT_INPUT_STATE_UNCHANGED instead.
            setUnchangedSoftInputState();
            setContentView(layout);
        }

        void showSoftInput() {
            final InputMethodManager imm = getSystemService(InputMethodManager.class);
            assertNotNull(imm);
            imm.showSoftInput(mEditText, 0);
        }

        void resetPrivateImeOptionsIdentifier() {
            mEditText.setPrivateImeOptions(
                    getClass().getName() + "/" + SystemClock.elapsedRealtimeNanos());
        }

        EditText getEditText() {
            return mEditText;
        }

        private void setUnchangedSoftInputState() {
            final Window window = getWindow();
            final int currentSoftInputMode = window.getAttributes().softInputMode;
            final int newSoftInputMode =
                    (currentSoftInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                            | SOFT_INPUT_STATE_UNCHANGED;
            window.setSoftInputMode(newSoftInputMode);
        }
    }

    public static class ImeTestActivity2 extends ImeTestActivity {
    }

    public static final class ImeTestActivityWithBrokenContextWrapper extends Activity {

        private EditText mEditText;

        /**
         * Emulates the behavior of certain {@link ContextWrapper} subclasses we found in the wild.
         *
         * <p> Certain {@link ContextWrapper} subclass in the wild delegate method calls to
         * ApplicationContext except for {@link #getSystemService(String)}.</p>
         **/
        private static final class Bug118341760ContextWrapper extends ContextWrapper {

            @NonNull
            private final Context mOriginalContext;

            Bug118341760ContextWrapper(@NonNull Context base) {
                super(base.getApplicationContext());
                mOriginalContext = base;
            }

            /**
             * Emulates the behavior of {@link ContextWrapper#getSystemService(String)} of certain
             * {@link ContextWrapper} subclasses we found in the wild.
             *
             * @param name The name of the desired service.
             * @return The service or {@code null} if the name does not exist.
             */
            @Override
            public Object getSystemService(@NonNull String name) {
                return mOriginalContext.getSystemService(name);
            }
        }

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(new Bug118341760ContextWrapper(this));
            // Use SystemClock.elapsedRealtimeNanos()) as a unique ID of this edit text.
            mEditText.setPrivateImeOptions(Long.toString(SystemClock.elapsedRealtimeNanos()));
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            setContentView(layout);
        }

        EditText getEditText() {
            return mEditText;
        }
    }

    private void assertImeWindowAndDisplayConfiguration(@NonNull WindowState imeWinState,
            @NonNull DisplayContent display) {
        // The IME window should inherit the configuration from the IME DisplayArea.
        final WindowManagerState.DisplayArea imeContainerDisplayArea = display.getImeContainer();
        final Configuration configurationForIme = imeWinState.getMergedOverrideConfiguration();
        final Configuration configurationForImeContainer =
                imeContainerDisplayArea.getMergedOverrideConfiguration();
        final int displayDensityDpiForIme = configurationForIme.densityDpi;
        final int displayDensityDpiForImeContainer = configurationForImeContainer.densityDpi;
        final Rect displayBoundsForIme = configurationForIme.windowConfiguration.getBounds();
        final Rect displayBoundsForImeContainer =
                configurationForImeContainer.windowConfiguration.getBounds();

        assertEquals(
                "Display density should be the same",
                displayDensityDpiForImeContainer,
                displayDensityDpiForIme);
        assertEquals(
                "Display bounds should be the same",
                displayBoundsForImeContainer,
                displayBoundsForIme);
    }

    private void tapAndAssertEditorFocusedOnImeActivity(
            @NonNull ImeTestActivity activity, int displayId) {
        final var componentName = activity.getComponentName();
        waitAndAssertActivityStateOnDisplay(
                componentName,
                STATE_RESUMED,
                displayId,
                "Test activity should be resumed on displayId: " + displayId);
        try (var touch = new UinputTouchScreen(mInstrumentation, activity.getDisplay())) {
            touch.tapOnViewCenter(activity.getEditText());
            mWmState.waitForWithAmState(
                    state -> state.getFocusedDisplayId() == displayId,
                    "Focused displayId should be: " + displayId);
            mWmState.computeState(componentName);
            mWmState.assertFocusedAppOnDisplay(
                    "Test activity should be focused on display", componentName, displayId);
        }
    }

    /**
     * Touches and then cancels the input event on the center of the given display. Used to set the
     * display as focused, without accidentally launching any apps / pressing any buttons on the
     * display.
     *
     * @param dc the display content of the display to touch and cancel.
     * @param touch the touch screen device to use for the touch.
     */
    static void touchAndCancelOnDisplayCenter(
            @NonNull DisplayContent dc, @NonNull UinputTouchScreen touch) {
        final var bounds = dc.getBounds();
        final int x = bounds.left + bounds.width() / 2;
        final int y = bounds.top + bounds.height() / 2;
        final var pointer = touch.touchDown(x, y);
        pointer.close();
    }

    private void showSoftInputAndAssertImeShownOnDisplay(int displayId,
            @NonNull TestActivitySession<? extends ImeTestActivity> activitySession,
            @NonNull ImeEventStream stream) throws Exception {
        final var activity = activitySession.getActivity();
        activitySession.runOnMainSyncAndWait(activity::showSoftInput);
        expectEvent(stream, editorMatcher("onStartInputView",
                activity.getEditText().getPrivateImeOptions()), TIMEOUT);
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }
}
