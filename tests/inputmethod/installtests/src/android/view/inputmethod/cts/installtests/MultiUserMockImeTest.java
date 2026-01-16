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
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.additionalUser;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectNoImeCrash;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.app.ApplicationExitInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.InstantAppInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.BuildUtils;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodSubtype;
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
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
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
import org.junit.Before;
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
@RunWith(BedsteadJUnit4.class)
public final class MultiUserMockImeTest {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15)
            * BuildUtils.HW_TIMEOUT_MULTIPLIER;

    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final long MOCKIME_CRASH_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    @Rule public DebugInputRule mDebugInputRule = new DebugInputRule();

    /** Tag for the first EditText. */
    private static final String FIRST_EDIT_TEXT_TAG = "first-EditText";
    /** Tag for the second EditText. */
    private static final String SECOND_EDIT_TEXT_TAG = "second-EditText";

    private static final InputMethodSubtype TEST_SUBTYPE1 =
            new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeId(0x01234567).build();

    private static final InputMethodSubtype TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeId(0x12345678).build();

    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule(mFlagsValueProvider);

    /**
     * Whether the IME process should only start if an EditText is focused. This also disables the
     * warm user profile switching.
     */
    private boolean mIsPreventImeStartup;

    private boolean mIsConcurrentMultiUserMode;

    @Before
    public void setUp() {
        mIsPreventImeStartup = isPreventImeStartup();
        mIsConcurrentMultiUserMode = isConcurrentMultiUserMode();
    }

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

    /**
     * Verifies the IME lifecycle when switching user profiles, and that the IME can be shown
     * for each profile.
     */
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
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                        expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                        expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                    }

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);
                }
            }
        }
    }

    /**
     * Verifies the IME lifecycle when switching across full users, and that the IME can be shown
     * for each user.
     */
    // TODO(b/457983966): remove after fixing activity launcher post user switch on automotive
    @RequireNotAutomotive(reason = "Automotive launches activities with delay after user switch")
    @Test
    @EnsureHasAdditionalUser
    public void testFullUserSwitching() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int additionalUserId = additionalUser.id();

        assertTrue(additionalUser.isRunning());

        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final var context = instrumentation.getContext();
        final var uiAutomation = instrumentation.getUiAutomation();
        final boolean isInstant = isInstantApp(context, uiAutomation);

        // Copy required packages from the current user to the additional user. Note that currently
        // bedstead does not support install-existing with "--instant" option so here we directly
        // use shell commands.

        // For MockIme, always install as full (non-instant) app.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, additionalUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the additional
        // user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), additionalUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder().setTargetUser(additionalUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                additionalUser.switchTo();

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        additionalUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    mWmState.waitAndAssertFocusedActivity(
                            "Test activity should be focused for new user",
                            MockTestActivityUtil.TEST_ACTIVITY);
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    if (mIsConcurrentMultiUserMode) {
                        notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                    }

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, additionalUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);
                }
            }
        } finally {
            // Prevent test isolation bugs by switching back to the initial user as this is what the
            // other tests expect.
            currentUser.switchTo(6 /* timeoutInMinutes */);
        }
    }

    /**
     * Verifies the IME lifecycle when switching user profiles and then switching across full users,
     * and that the IME can be shown for each.
     */
    // TODO(b/457983966): remove after fixing activity launcher post user switch on automotive
    @RequireNotAutomotive(reason = "Automotive launches activities with delay after user switch")
    @Test
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    @EnsureHasAdditionalUser
    public void testProfileSwitchingThenFullUserSwitching() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference workUser = workProfile(sDeviceState, currentUser);
        final UserReference additionalUser = additionalUser(sDeviceState);
        final int currentUserId = currentUser.id();
        final int workUserId = workUser.id();
        final int additionalUserId = additionalUser.id();

        assertTrue(workUser.isRunning());
        assertTrue(additionalUser.isRunning());

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
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockImePackageNames.MockIme1, additionalUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), additionalUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder().setTargetUser(workUser.userHandle()));
                var session3 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder().setTargetUser(additionalUser.userHandle()))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();
            var stream3 = session3.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);

            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                        expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                        expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                    }

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    additionalUser.switchTo();

                    final String marker3 = getTestMarker("additionalUser-editText");
                    try (var activity3 = MockTestActivityUtil.launchAsUser(
                            additionalUserId, isInstant,
                            Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker3))) {
                        mWmState.waitAndAssertFocusedActivity(
                                "Test activity should be focused for new user",
                                MockTestActivityUtil.TEST_ACTIVITY);
                        expectEvent(stream3, eventMatcher("onCreate"), TIMEOUT);
                        expectEvent(stream3, eventMatcher("bindInput"), TIMEOUT);
                        expectEvent(stream3, editorMatcher("onStartInput", marker3), TIMEOUT);
                        expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);
                        if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                                && !mIsPreventImeStartup) {
                            // Without the flag, this was already destroyed during the profile
                            // switch. With the flag, this should be destroyed during the full user
                            // switch.
                            expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                        }

                        MockTestActivityUtil.sendBroadcastAction(
                                MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, additionalUserId);
                        expectEvent(stream3, eventMatcher("showSoftInput"), TIMEOUT);
                        expectImeVisible(TIMEOUT);
                    }
                }
            }
        } finally {
            // Prevent test isolation bugs by switching back to the initial user as this is what the
            // other tests expect.
            currentUser.switchTo();
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
                            Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
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
                                Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                        expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                        expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                    }

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_FINISH, workUserId);
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream2, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                        expectEvent(stream2, hideSoftInputMatcher(), TIMEOUT);
                        expectEvent(stream2, eventMatcher("unbindInput"), TIMEOUT);
                        notExpectEvent(stream1, eventMatcher("onCreate"), NOT_EXPECT_TIMEOUT);
                    } else {
                        expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);
                        expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                    }
                    expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);
                    expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);
                }
            }
        }
    }

    /**
     * Verifies that having the IME visible on two different profiles, switching to the second
     * profile, freezing the IME of the first profile and switching back to it allows that IME to
     * start and be shown.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testFrozenImeFromDifferentProfileCanBeShownAfterSwitching() throws Exception {
        assumeFalse("PreventImeStartup will kill the IME process, skipping the test",
                mIsPreventImeStartup);

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
                            Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                notExpectEvent(stream1, eventMatcher("showSoftInput"), NOT_EXPECT_TIMEOUT);
                expectImeInvisible(TIMEOUT);

                final int currentUserImePid =
                        expectCommand(stream1, session1.callGetPid(), TIMEOUT)
                                .getReturnIntegerValue();
                assertWithMessage("Current user IME process should be found")
                        .that(currentUserImePid)
                        .isNotEqualTo(-1);

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        workUserId, isInstant,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2,
                                MockTestActivityUtil.EXTRA_SOFT_INPUT_MODE,
                                Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    SystemUtil.runWithShellPermissionIdentity(() -> {
                        runShellCommandOrThrow("am freeze " + currentUserImePid);
                        PollingCheck.waitFor(TIMEOUT, () ->
                                        runShellCommand("am isfrozen " + currentUserImePid)
                                                .contains("true"),
                                "Current user IME process should be frozen");
                    });
                    notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_FINISH, workUserId);
                    notExpectEvent(stream2, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream2, hideSoftInputMatcher(), TIMEOUT);
                    expectEvent(stream2, eventMatcher("unbindInput"), TIMEOUT);

                    SystemUtil.runWithShellPermissionIdentity(() -> PollingCheck.waitFor(TIMEOUT,
                            () -> runShellCommand("am isfrozen " + currentUserImePid)
                                    .contains("false"),
                            "Current user IME process should be unfrozen"));

                    notExpectEvent(stream1, eventMatcher("onCreate"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
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

            expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
            expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
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
                                Integer.toString(SOFT_INPUT_STATE_HIDDEN)), null)) {
                mWmState.waitForAppTransitionIdleOnDisplay(display.getDisplayId());
                assertWithMessage("Second activity should be resumed after launch in split screen")
                        .that(mWmState.waitForActivityState(MockTestActivityUtil.TEST_ACTIVITY,
                                STATE_RESUMED))
                        .isTrue();

                expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                        && !mIsPreventImeStartup) {
                    notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);
                } else {
                    expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                }

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

                if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                        && !mIsPreventImeStartup) {
                    notExpectEvent(stream2, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream2, hideSoftInputMatcher(), TIMEOUT);
                    expectEvent(stream2, eventMatcher("unbindInput"), TIMEOUT);
                    notExpectEvent(stream1, eventMatcher("onCreate"), NOT_EXPECT_TIMEOUT);
                } else {
                    expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);
                    expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                }
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
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
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);

                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, currentUserId);
                expectEvent(stream1, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);

                final String marker2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                try (var activity2 = MockTestActivityUtil.launchAsUser(
                        profileUserId, false /* instant */,
                        Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker2))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                        expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                        expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onDestroy"), TIMEOUT);
                    }

                    lockScreenSession.sleepDevice();

                    // Remove profile with screen off to maintain currentImeUser ID in
                    // InputMethodManagerService.
                    profileUser.remove();

                    final var exitInfo = PollingCheck.waitFor(MOCKIME_CRASH_TIMEOUT,
                            session2::findLatestMockImeSessionExitInfo, Objects::nonNull);
                    assertNotNull("Expected MockImeSession to crash after user removal", exitInfo);
                    assertEquals("Expected MockImeSession to crash due to user removal",
                            ApplicationExitInfo.REASON_USER_STOPPED, exitInfo.getReason());

                    lockScreenSession.unlock();

                    mWmState.waitForFocusedActivity(MockTestActivityUtil.TEST_ACTIVITY);

                    // Must be able to startInput on the previous user even when the currentImeUser
                    // was removed.
                    if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)
                            && !mIsPreventImeStartup) {
                        notExpectEvent(stream1, eventMatcher("onCreate"), NOT_EXPECT_TIMEOUT);
                    } else {
                        expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                    }
                    expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream1, editorMatcher("onStartInput", marker1), TIMEOUT);
                }
            }
        }
    }

    /**
     * This verifies that privileged operation calls from IMEs that are not currently active (i.e.
     * IMEs of a different user profile than the current one) won't have any effect.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testPrivilegedOperationsOnInactiveImesHaveNoEffect() throws Exception {
        assumeFalse("PreventImeStartup will kill the IME process, skipping the test",
                mIsPreventImeStartup);

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
                new ImeSettings.Builder()
                        .setAdditionalSubtypes(
                                TEST_SUBTYPE1, TEST_SUBTYPE2));
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
                            Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
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
                                Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                    expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    // MockIme1 cannot call any privileged operations while not active.
                    expectCommand(
                            stream1,
                            session1.callSwitchInputMethod(session1.getImeId(), TEST_SUBTYPE2),
                            TIMEOUT);
                    notExpectEvent(
                            stream1,
                            eventMatcher("onCurrentInputMethodSubtypeChanged"),
                            NOT_EXPECT_TIMEOUT);
                }
            }
        }
    }

    /**
     * This verifies that force stopping the IME of a different user (profile) than the current one
     * won't impact the current user/IME.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testImeForceStoppedWhileInactiveHasNoEffect() throws Exception {
        assumeFalse("PreventImeStartup will kill the IME process, skipping the test",
                mIsPreventImeStartup);

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
                MockImePackageNames.MockIme2, workUserId, false /* instant */));
        // For the test app, propagate isInstant option from the current user to the work user.
        runShellCommandOrThrow(ShellCommandUtils.installExisting(
                MockTestActivityUtil.TEST_ACTIVITY.getPackageName(), workUserId, isInstant));

        try (var session1 = MockImeSession.create(context, uiAutomation,
                new ImeSettings.Builder());
                var session2 = MockImeSession.create(context, uiAutomation,
                     new ImeSettings.Builder()
                             .setTargetUser(workUser.userHandle())
                             .setMockImePackageName(MockImePackageNames.MockIme2))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();

            final String marker1 = getTestMarker(FIRST_EDIT_TEXT_TAG);
            try (var activity1 = MockTestActivityUtil.launchAsUser(
                    currentUserId, isInstant,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker1,
                            MockTestActivityUtil.EXTRA_SOFT_INPUT_MODE,
                            Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
                expectEvent(stream1, eventMatcher("bindInput"), TIMEOUT);
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
                                Integer.toString(SOFT_INPUT_STATE_HIDDEN)))) {
                    expectEvent(stream2, eventMatcher("onCreate"), TIMEOUT);
                    expectEvent(stream2, eventMatcher("bindInput"), TIMEOUT);
                    expectEvent(stream2, editorMatcher("onStartInput", marker2), TIMEOUT);
                    notExpectEvent(stream1, eventMatcher("onDestroy"), NOT_EXPECT_TIMEOUT);
                    expectEvent(stream1, hideSoftInputMatcher(), TIMEOUT);
                    expectEvent(stream1, eventMatcher("unbindInput"), TIMEOUT);

                    MockTestActivityUtil.sendBroadcastAction(
                            MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, workUserId);
                    expectEvent(stream2, eventMatcher("showSoftInput"), TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    // force stopping MockIme1 shouldn't impact MockIme2
                    runShellCommandOrThrow("am force-stop " + session1.getMockImePackageName()
                            + " --user " + currentUserId);

                    PollingCheck.waitFor(
                            MOCKIME_CRASH_TIMEOUT,
                            () -> session1.findLatestMockImeSessionExitInfo() != null);
                    final var exitInfo = session1.findLatestMockImeSessionExitInfo();
                    assertWithMessage("Expected MockImeSession1 to crash due to killed application")
                            .that(exitInfo.getReason())
                            .isEqualTo(ApplicationExitInfo.REASON_USER_REQUESTED);
                    assertThat(session1.retrieveExitReasonIfMockImeCrashed()).isNotNull();

                    expectNoImeCrash(session2, NOT_EXPECT_TIMEOUT);

                    notExpectEvent(stream2, hideSoftInputMatcher(), NOT_EXPECT_TIMEOUT);
                    notExpectEvent(stream2, eventMatcher("unbindInput"), NOT_EXPECT_TIMEOUT);
                    expectImeVisible(TIMEOUT);
                }
            }
        }
    }


    /**
     * TODO(b/327704045): Unify the implementation with
     * {@link android.view.inputmethod.cts.util.EndToEndImeTestBase#isPreventImeStartup()}
     */
    private static boolean isPreventImeStartup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try {
            return context.getResources().getBoolean(
                    android.R.bool.config_preventImeStartupUnlessTextEditor);
        } catch (Resources.NotFoundException e) {
            // Assume this is not enabled.
            return false;
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

    /**
     * Returns {@code true} if the concurrent multi-user mode is enabled.
     *
     * <p>Currently not compatible with profiles (e.g. work profile).</p>
     *
     * @return {@code true} if the concurrent multi-user mode is enabled.
     */
    static boolean isConcurrentMultiUserMode() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && TestApis.users().isVisibleBackgroundUsersSupported()
                && context.getResources().getBoolean(android.R.bool.config_perDisplayFocusEnabled);
    }
}
