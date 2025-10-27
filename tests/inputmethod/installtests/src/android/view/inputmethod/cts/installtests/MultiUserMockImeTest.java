/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.view.inputmethod.cts.installtests;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.ApplicationExitInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.InstantAppInfo;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.CommonPackages;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingSupplier;
import com.android.cts.input.DebugInputRule;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImePackageNames;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public final class MultiUserMockImeTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    private static final long MOCKIME_CRASH_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    @Rule public DebugInputRule mDebugInputRule = new DebugInputRule();

    /** Tag for the first EditText. */
    private static final String FIRST_EDIT_TEXT_TAG = "first-EditText";
    /** Tag for the second EditText. */
    private static final String SECOND_EDIT_TEXT_TAG = "second-EditText";

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @After
    public void tearDown() {
        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());
    }

    /**
     * TODO(b/327704045): Unify the implementation with
     * {@link android.view.inputmethod.cts.util.EndToEndImeTestBase#getTestMarker(String)}
     */
    private String getTestMarker(@NonNull String tag) {
        return getClass().getName() + "/" + tag + "/" + SystemClock.elapsedRealtimeNanos();
    }

    @Test
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testProfileSwitching() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference workUser = workProfile(sDeviceState, currentUser);
        final int currentUserId = currentUser.id();
        final int workUserId = workUser.id();

        assertTrue(workUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();
        final boolean isInstant = isInstantApp(context, uiAutomation);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, workUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder().setTargetUser(workUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    expectEvent(stream1, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);
                }
            }
        }
    }

    /**
     * Verifies that having the IME visible on two apps from different profiles, and switching
     * between them, allows the IME visibility to be restored.
     */
    @Test
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testProfileSwitchingCanRestoreImeVisibility() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference workUser = workProfile(sDeviceState, currentUser);
        final int currentUserId = currentUser.id();
        final int workUserId = workUser.id();

        assertTrue(workUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();
        final boolean isInstant = isInstantApp(context, uiAutomation);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, workUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder()
                             .setTargetUser(workUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1,
                            MockTestActivityUtil.EXTRA_SOFT_INPUT_MODE,
                            Integer.toString(SOFT_INPUT_STATE_UNSPECIFIED)))) {
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2,
                                MockTestActivityUtil.EXTRA_SOFT_INPUT_MODE,
                                Integer.toString(SOFT_INPUT_STATE_UNSPECIFIED)))) {
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    expectEvent(stream1, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_FINISH, workUserId);
                    expectEvent(stream2, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);
                    expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);

                    expectImeVisible(TIMEOUT);
                }
            }
        }
    }



    /**
     * Verifies that having the IME visible in two apps in split screen, each from a different
     * user profile, hiding the IME in one, switching to the other, switching back to the first one
     * and requesting to show it does succeed.
     */
    @Test
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testHidingKeyboardInSplitScreenWithCrossProfileAppsCanShowKeyboardAgain()
            throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference workUser = workProfile(sDeviceState, currentUser);
        final int workUserId = workUser.id();

        assertTrue(workUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();
        final boolean isInstant = isInstantApp(context, uiAutomation);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, workUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder()
                             .setTargetUser(workUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch in the same process for the current user. We need the activity reference so
            // that we can launch it in split screen together with activity2 below.
            final TestActivity activity1 = new TestActivity.Starter().asNewTask()
                    .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                    .startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        // Place EditText at bottom to enable clicking it in vertical split screen.
                        layout.setGravity(Gravity.BOTTOM);

                        final EditText focusedEditText = new EditText(activity);
                        focusedEditText.setHint("focused editText");
                        focusedEditText.setPrivateImeOptions(marker1);
                        focusedEditText.requestFocus();
                        layout.addView(focusedEditText);
                        editTextRef.set(focusedEditText);
                        return layout;
                    }, TestActivity.class);
            final var editText = editTextRef.get();
            final var display = editText.getContext().getDisplay();

            expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

            activity1.runOnUiThread(() ->
                    editText.getWindowInsetsController().show(WindowInsets.Type.ime()));
            expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
            expectImeVisible(TIMEOUT);

            activity1.runOnUiThread(() ->
                    editText.getWindowInsetsController().hide(WindowInsets.Type.ime()));
            expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
            try (var touch = new UinputTouchScreen(instrumentation, display);
                    var activity2 = MockTestActivityUtil.launchSyncAsUser(activity1,
                            workUserId, isInstant, true /* splitScreen */,
                            Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2,
                                MockTestActivityUtil.EXTRA_SOFT_INPUT_MODE,
                                Integer.toString(SOFT_INPUT_STATE_UNSPECIFIED)), null)) {
                expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);

                mWmState.waitForAppTransitionIdleOnDisplay(display.getDisplayId());
                assertWithMessage("Second activity should be resumed after launch in split screen")
                        .that(mWmState.waitForActivityState(MockTestActivityUtil.TEST_ACTIVITY,
                                STATE_RESUMED))
                        .isTrue();

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                // Able to show successfully after hiding and switching profiles.
                touch.tapOnViewCenter(editText);
                TestUtils.waitOnMainUntil(() -> editText.hasFocus() && editText.hasWindowFocus(),
                        TIMEOUT, "EditText is focused after click");

                // TODO(b/280797309): The tap sends the IME show request before the
                //  input focus changes, so we simulate a "slower" tap that actually sent the
                //  request.
                // If the tap is too fast, we fail the IME show request as the window is not
                // focused. If the tap is too slow, we send the IME show request after the process
                // is created/bound, and it succeeds. For this particular race condition we must get
                // the requestedVisibleTypes set again on the app window , but before the
                // IME process is created/bound.
                activity1.runOnUiThread(() ->
                        editText.getWindowInsetsController().show(WindowInsets.Type.ime()));

                expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                // TODO(b/454882327): Remove this hide after fixing the issue with a
                //  RemoteInsetsControlTarget that still has the IME visible could lead to an
                //  unexpected show request later.
                activity1.runOnUiThread(() ->
                        editText.getWindowInsetsController().hide(WindowInsets.Type.ime()));
                expectEvent(stream1, eventMatcher("hideSoftInput"), TIMEOUT);
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    @Test
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    @DebugInputRule.DebugInput(bug = 385227171)
    public void testRemoveCurrentProfileCanStartInputOnOtherUser() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference profileUser = workProfile(sDeviceState, currentUser);
        final int currentUserId = currentUser.id();
        final int profileUserId = profileUser.id();

        assertTrue(profileUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();

        // DeviceState setup disabled keyguard to avoid test failures, but we need it enabled.
        // This also ensures we don't end the test with keyguard shown.
        TestApis.device().setKeyguardEnabled(true);

        // Copy required packages from the current user to the profile user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, profileUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), profileUserId,
                false /* instant */));

        try (var lockScreenSession = new LockScreenSession(instrumentation, mWmState);
                var session1 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                        new ImeSettings.Builder()
                                .setTargetUser(profileUser.userHandle())
                                .setSuppressDeleteSettings(true))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, false /* instant */,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        profileUserId, false /* instant */,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    expectEvent(stream1, event -> "onDestroy".equals(event.getEventName()),
                            TIMEOUT);

                    lockScreenSession.sleepDevice();

                    // Remove profile with screen off to maintain currentImeUser ID in
                    // InputMethodManagerService.
                    profileUser.remove();

                    final var exitInfo = PollingCheck.waitFor(MOCKIME_CRASH_TIMEOUT,
                            session2::findLatestMockImeSessionExitInfo, Objects::nonNull);
                    assertEquals("Expected MockImeSession to crash due to removed user",
                            ApplicationExitInfo.REASON_USER_STOPPED, exitInfo.getReason());

                    lockScreenSession.wakeUpDevice();
                    // Wait for lock screen to be visible and focused before unlocking.
                    mWmState.waitForNonActivityWindowFocused();
                    lockScreenSession.unlockDevice();

                    mWmState.waitForFocusedActivity(MockTestActivityUtil.TEST_ACTIVITY);

                    // Must be able to startInput on the previous user even when the currentImeUser
                    // was removed.
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);
                }
            }
        }
    }

    private static <T> T runWithShellPermissionIdentity(@NonNull UiAutomation uiAutomation,
            @NonNull ThrowingSupplier<T> supplier, String... permissions) {
        Object[] placeholder = new Object[1];
        SystemUtil.runWithShellPermissionIdentity(uiAutomation, () ->
                placeholder[0] = supplier.get(), permissions);
        return (T) placeholder[0];
    }

    private boolean isInstantApp(@NonNull Context context, @NonNull UiAutomation uiAutomation) {
        return runWithShellPermissionIdentity(uiAutomation, () -> {
            // as this test app itself is always running as a full app, we can check if the
            // CtsInputMethodStandaloneTestApp was installed as an instant app
            Optional<InstantAppInfo> instantAppInfo =
                    context.getPackageManager().getInstantApps().stream()
                            .filter(packageInfo -> TextUtils.equals(packageInfo.getPackageName(),
                                    MockTestActivityUtil.TEST_ACTIVITY.getPackageName()))
                            .findFirst();
            return instantAppInfo.isPresent()
                    && instantAppInfo.get().getApplicationInfo().isInstantApp();
        }, Manifest.permission.ACCESS_INSTANT_APPS);
    }
}
