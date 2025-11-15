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
package android.app.modes.cts;

import static android.app.modes.ContextualMode.STATE_ACTIVE;
import static android.app.modes.ContextualMode.STATE_INACTIVE;
import static android.app.modes.ContextualMode.STATE_UNKNOWN;
import static android.app.modes.ContextualMode.TYPE_BEDTIME;
import static android.app.modes.ContextualMode.TYPE_DRIVING;
import static android.app.modes.ContextualMode.TYPE_IMMERSIVE;
import static android.app.modes.ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB;
import static android.app.modes.ContextualMode.TYPE_OTHER;
import static android.app.modes.ContextualMode.TYPE_SCHEDULE_CALENDAR;
import static android.app.modes.ContextualMode.TYPE_SCHEDULE_TIME;
import static android.app.modes.ContextualMode.TYPE_THEATER;
import static android.app.modes.ContextualMode.TYPE_TRANSIT;
import static android.app.modes.ContextualMode.TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest.permission;
import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager;
import android.app.modes.ContextualModeManager.ContextualModeListener;
import android.app.modes.ContextualModesMutation;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.Condition;
import android.service.notification.Flags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DND_SYNC)
public class ContextualModeManagerTest {
    private static final long LISTENER_TIMEOUT_SEC = 5;

    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private ContextualModeManager mContextualModeManager;
    private NotificationManager mNotificationManager;
    private boolean mModeSyncWasEnabled;
    private List<ContextualMode> mInitialModes;
    private List<String> mTestAutomaticZenRuleIds;
    private ContextualModeListener mModeListener;

    @Before
    public void setUp() {
        adoptShellPermissions();
        mContext = ApplicationProvider.getApplicationContext();
        mContextualModeManager = mContext.getSystemService(ContextualModeManager.class);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mTestAutomaticZenRuleIds = new ArrayList<>();

        // Remember the current mode sync state.
        if (mContextualModeManager.isModeSyncSupported()) {
            mModeSyncWasEnabled = mContextualModeManager.isModeSyncEnabled();
        }
        // Remember initial mode states.
        mInitialModes = mContextualModeManager.getModes();
        // Turn off all modes.
        ContextualModesMutation.Builder builder = new ContextualModesMutation.Builder();
        mInitialModes.forEach(
                m ->
                        builder.addUpdatedMode(
                                new ContextualMode.Builder(m).setState(STATE_INACTIVE).build()));
        mContextualModeManager.mutateModes(builder.build());
    }

    @After
    public void tearDown() {
        adoptShellPermissions();

        // Remove mode listener.
        if (mModeListener != null) {
            mContextualModeManager.unregisterModeListener(mModeListener);
        }
        // Restore the mode sync state.
        if (mContextualModeManager.isModeSyncSupported()) {
            mContextualModeManager.setModeSyncEnabled(mModeSyncWasEnabled);
        }
        // Remove test only zen rules.
        for (String id : mTestAutomaticZenRuleIds) {
            mNotificationManager.removeAutomaticZenRule(id);
        }
        // Restore initial mode states.
        ContextualModesMutation.Builder builder = new ContextualModesMutation.Builder();
        mInitialModes.forEach(builder::addUpdatedMode);
        mContextualModeManager.mutateModes(builder.build());

        // Restore permission.
        dropShellPermissions();
    }

    @Test
    public void testSetAndGetModeSyncEnabled() {
        assumeTrue(mContextualModeManager.isModeSyncSupported());

        // Enable mode sync.
        mContextualModeManager.setModeSyncEnabled(true);
        assertThat(mContextualModeManager.isModeSyncEnabled()).isTrue();

        // Disable mode sync.
        mContextualModeManager.setModeSyncEnabled(false);
        assertThat(mContextualModeManager.isModeSyncEnabled()).isFalse();

        // Re-enable mode sync.
        mContextualModeManager.setModeSyncEnabled(true);
        assertThat(mContextualModeManager.isModeSyncEnabled()).isTrue();
    }

    @Test
    public void testGetModeSyncEnabled_noPermission_succeed() {
        dropShellPermissions();

        mContextualModeManager.isModeSyncEnabled();
    }

    @Test
    public void testModeSyncEnabledListener() {
        assumeTrue(mContextualModeManager.isModeSyncSupported());

        CallbackWaiter<Boolean> waiter = new CallbackWaiter<>();
        mContextualModeManager.registerModeSyncEnabledListener(directExecutor(), waiter);

        // Flip state.
        boolean enabled = !mModeSyncWasEnabled;
        mContextualModeManager.setModeSyncEnabled(enabled);
        assertThat(waiter.waitFor(enabled, LISTENER_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();

        // Flip state again.
        enabled = !enabled;
        mContextualModeManager.setModeSyncEnabled(enabled);
        assertThat(waiter.waitFor(enabled, LISTENER_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();

        // Unregister.
        mContextualModeManager.unregisterModeSyncEnabledListener(waiter);

        // Flip again.
        enabled = !enabled;
        mContextualModeManager.setModeSyncEnabled(enabled);
        assertThat(waiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void testModeSyncEnabledListener_noPermission_succeed() {
        dropShellPermissions();

        Consumer<Boolean> listener = enabled -> {};
        mContextualModeManager.registerModeSyncEnabledListener(directExecutor(), listener);
        mContextualModeManager.unregisterModeSyncEnabledListener(listener);
    }

    @Test
    public void testSetAndGetManualDnd() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Manual DND must exist if a user has any mode.
        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        // Flip manual DND.
        ContextualMode flippedDnd = flipState(manualDnd);
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder().addUpdatedMode(flippedDnd).build());

        // Verify that mode changed.
        assertThat(findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB))
                .isEqualTo(flippedDnd);
        assertThat(hasInterruptionFilter()).isEqualTo(isActive(flippedDnd));

        // Flip manual DND back.
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder().addUpdatedMode(manualDnd).build());

        // Verify that mode changed.
        assertThat(findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB))
                .isEqualTo(manualDnd);
        assertThat(hasInterruptionFilter()).isEqualTo(isActive(manualDnd));
    }

    @Test
    public void testSetAndGetAutomaticZenMode() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Exclude TYPE_BEDTIME/TYPE_MANAGED since they can't be added by tests.
        List<Integer> typesToTest =
                new ArrayList<>(
                        List.of(
                                TYPE_DRIVING,
                                TYPE_IMMERSIVE,
                                TYPE_SCHEDULE_CALENDAR,
                                TYPE_SCHEDULE_TIME,
                                TYPE_THEATER,
                                TYPE_UNKNOWN,
                                TYPE_OTHER));
        if (android.app.Flags.modesUiTransit()) {
            typesToTest.add(TYPE_TRANSIT);
        }
        for (int type : typesToTest) {
            ContextualMode mode = addTestAutomaticZenRule(type);

            // Flip mode state.
            ContextualMode flippedMode = flipState(mode);
            mContextualModeManager.mutateModes(
                    new ContextualModesMutation.Builder().addUpdatedMode(flippedMode).build());

            // Verify that mode changed.
            assertThat(findModeById(mContextualModeManager.getModes(), mode.getId()))
                    .isEqualTo(flippedMode);
            assertThat(isAutomaticZenRuleActive(mode.getId())).isEqualTo(isActive(flippedMode));

            // Flip mode back.
            mContextualModeManager.mutateModes(
                    new ContextualModesMutation.Builder().addUpdatedMode(mode).build());

            // Verify that mode changed.
            assertThat(findModeById(mContextualModeManager.getModes(), mode.getId()))
                    .isEqualTo(mode);
            assertThat(isAutomaticZenRuleActive(mode.getId())).isEqualTo(isActive(mode));
        }
    }

    @Test
    public void testMutateMode_batchUpdate() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Add 2 automatic zen rules.
        ContextualMode mode1 = addTestAutomaticZenRule(TYPE_OTHER);
        ContextualMode mode2 = addTestAutomaticZenRule(TYPE_OTHER);

        // Batch flip state.
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder()
                        .addUpdatedMode(flipState(mode1))
                        .addUpdatedMode(flipState(mode2))
                        .build());

        // Verify state changed.
        List<ContextualMode> modes = mContextualModeManager.getModes();
        assertThat(findModeById(modes, mode1.getId())).isEqualTo(flipState(mode1));
        assertThat(findModeById(modes, mode2.getId())).isEqualTo(flipState(mode2));
        assertThat(isAutomaticZenRuleActive(mode1.getId())).isEqualTo(isActive(flipState(mode1)));
        assertThat(isAutomaticZenRuleActive(mode2.getId())).isEqualTo(isActive(flipState(mode2)));
    }

    @Test
    public void testMutateMode_updateNonExistingMode_fail() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mContextualModeManager.mutateModes(
                                new ContextualModesMutation.Builder()
                                        .addUpdatedMode(
                                                new ContextualMode.Builder("test_id")
                                                        .setType(TYPE_OTHER)
                                                        .setState(STATE_INACTIVE)
                                                        .build())
                                        .build()));
    }

    @Test
    public void testMutateMode_updateModeToUnknown_fail() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mContextualModeManager.mutateModes(
                                new ContextualModesMutation.Builder()
                                        .addUpdatedMode(
                                                new ContextualMode.Builder(manualDnd)
                                                        .setState(STATE_UNKNOWN)
                                                        .build())
                                        .build()));
    }

    @Test
    public void testMutateMode_duplicateModeId_fail() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mContextualModeManager.mutateModes(
                                new ContextualModesMutation.Builder()
                                        .addUpdatedMode(manualDnd)
                                        .addUpdatedMode(manualDnd)
                                        .build()));
    }

    @Test
    public void testMutateMode_wrongModeType_fail() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mContextualModeManager.mutateModes(
                                new ContextualModesMutation.Builder()
                                        .addUpdatedMode(
                                                new ContextualMode.Builder(manualDnd)
                                                        .setType(TYPE_BEDTIME)
                                                        .build())
                                        .build()));
    }

    @Test
    public void testMutateMode_noPermission_fail() {
        dropShellPermissions();

        assertThrows(
                SecurityException.class,
                () ->
                        mContextualModeManager.mutateModes(
                                new ContextualModesMutation.Builder().build()));
    }

    @Test
    public void testModeListener_stateChange() {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        CallbackWaiter<List<ContextualMode>> modeChangeWaiter = new CallbackWaiter<>();
        mModeListener = newTestModeListener(modeChangeWaiter, null);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);
        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        // Mode change callback triggered by state change.
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder().addUpdatedMode(flipState(manualDnd)).build());

        assertThat(
                        modeChangeWaiter.waitFor(
                                List.of(flipState(manualDnd)),
                                LISTENER_TIMEOUT_SEC,
                                TimeUnit.SECONDS))
                .isTrue();
        // Verify that we don't receive more callbacks afterwards.
        assertThat(modeChangeWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void testModeListener_newMode() throws Exception {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        CallbackWaiter<List<ContextualMode>> modeChangeWaiter = new CallbackWaiter<>();
        mModeListener = newTestModeListener(modeChangeWaiter, null);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);

        // Mode change callback triggered by adding new mode.
        ContextualMode mode = addTestAutomaticZenRule(TYPE_OTHER);

        assertThat(modeChangeWaiter.waitFor(List.of(mode), LISTENER_TIMEOUT_SEC, TimeUnit.SECONDS))
                .isTrue();
        // Verify that we don't receive more callbacks afterwards.
        assertThat(modeChangeWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void testModeListener_batchUpdate() throws Exception {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Add two new mode added.
        ContextualMode mode1 = addTestAutomaticZenRule(TYPE_OTHER);
        ContextualMode mode2 = addTestAutomaticZenRule(TYPE_OTHER);
        // Register listener.
        CallbackWaiter<List<ContextualMode>> modeChangeWaiter = new CallbackWaiter<>();
        mModeListener = newTestModeListener(modeChangeWaiter, null);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);

        // Batch update triggers callback at once.
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder()
                        .addUpdatedMode(flipState(mode1))
                        .addUpdatedMode(flipState(mode2))
                        .build());

        assertThat(
                        modeChangeWaiter.waitFor(
                                modes ->
                                        modes.containsAll(
                                                List.of(flipState(mode1), flipState(mode2))),
                                LISTENER_TIMEOUT_SEC,
                                TimeUnit.SECONDS))
                .isTrue();
        // Verify that we don't receive more callbacks afterwards.
        assertThat(modeChangeWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void testModeListener_modeRemoved() throws Exception {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Add a mode.
        ContextualMode mode = addTestAutomaticZenRule(TYPE_OTHER);
        // Register callback.
        CallbackWaiter<List<ContextualMode>> modeChangeWaiter = new CallbackWaiter<>();
        CallbackWaiter<String> modeRemovedWaiter = new CallbackWaiter<>();
        mModeListener = newTestModeListener(modeChangeWaiter, modeRemovedWaiter);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);

        // Mode deletion triggers callback.
        mNotificationManager.removeAutomaticZenRule(mode.getId());

        assertThat(modeRemovedWaiter.waitFor(mode.getId(), LISTENER_TIMEOUT_SEC, TimeUnit.SECONDS))
                .isTrue();
        // Verify that we don't receive more delete callbacks afterwards.
        assertThat(modeRemovedWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
        // Verify that we didn't receive any change callbacks.
        assertThat(modeChangeWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void unregisterModeListener_noLongReceivesCallback() throws Exception {
        // Skip test if the current user has no modes.
        assumeUserHasMode();

        // Add callback.
        CallbackWaiter<List<ContextualMode>> modeChangeWaiter = new CallbackWaiter<>();
        mModeListener = newTestModeListener(modeChangeWaiter, null);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);
        // Find DND.
        ContextualMode manualDnd =
                findModeByType(mContextualModeManager.getModes(), TYPE_MANUAL_DO_NOT_DISTURB);
        assertThat(manualDnd).isNotNull();

        // Unregister callback and trigger a new change.
        mContextualModeManager.unregisterModeListener(mModeListener);
        mContextualModeManager.mutateModes(
                new ContextualModesMutation.Builder().addUpdatedMode(flipState(manualDnd)).build());

        // Verify no change is received.
        assertThat(modeChangeWaiter.getNext(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void registerModeListener_noPermission_fail() {
        dropShellPermissions();

        mModeListener = newTestModeListener(null, null);
        assertThrows(
                SecurityException.class,
                () -> mContextualModeManager.registerModeListener(directExecutor(), mModeListener));
    }

    @Test
    public void unregisterModeListener_noPermission_fail() {
        mModeListener = newTestModeListener(null, null);
        mContextualModeManager.registerModeListener(directExecutor(), mModeListener);

        dropShellPermissions();
        assertThrows(
                SecurityException.class,
                () -> mContextualModeManager.unregisterModeListener(mModeListener));
    }

    private void adoptShellPermissions() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        permission.WRITE_SECURE_SETTINGS,
                        permission.MANAGE_CONTEXTUAL_MODES,
                        permission.MANAGE_NOTIFICATIONS);
    }

    private void dropShellPermissions() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private ContextualModeListener newTestModeListener(
            @Nullable CallbackWaiter<List<ContextualMode>> changeCallbackWaiter,
            @Nullable CallbackWaiter<String> removedCallbackWaiter) {
        return new ContextualModeListener() {
            @Override
            public void onModesChanged(@NonNull List<ContextualMode> modes) {
                if (changeCallbackWaiter != null) {
                    changeCallbackWaiter.accept(modes);
                }
            }

            @Override
            public void onModeRemoved(@NonNull String modeId) {
                if (removedCallbackWaiter != null) {
                    removedCallbackWaiter.accept(modeId);
                }
            }
        };
    }

    private void assumeUserHasMode() {
        assumeTrue(!mContextualModeManager.getModes().isEmpty());
    }

    @Nullable
    private static ContextualMode findModeByType(List<ContextualMode> modes, int type) {
        return findMode(modes, m -> m.getType() == type);
    }

    @Nullable
    private static ContextualMode findModeById(List<ContextualMode> modes, String id) {
        return findMode(modes, m -> m.getId().equals(id));
    }

    @Nullable
    private static ContextualMode findMode(
            List<ContextualMode> modes, Predicate<ContextualMode> predicate) {
        for (ContextualMode mode : modes) {
            if (predicate.test(mode)) {
                return mode;
            }
        }
        return null;
    }

    private boolean isAutomaticZenRuleActive(String id) {
        return mNotificationManager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE;
    }

    private ContextualMode addTestAutomaticZenRule(int type) {
        AutomaticZenRule rule =
                new AutomaticZenRule.Builder("test-rule", Uri.EMPTY)
                        .setConfigurationActivity(new ComponentName(mContext, TestActivity.class))
                        .setManualInvocationAllowed(true)
                        .setType(type)
                        .build();
        String id = mNotificationManager.addAutomaticZenRule(rule);
        mTestAutomaticZenRuleIds.add(id);
        ContextualMode mode = findModeById(mContextualModeManager.getModes(), id);
        assertThat(mode).isNotNull();
        return mode;
    }

    private boolean hasInterruptionFilter() {
        return mNotificationManager.getCurrentInterruptionFilter()
                != NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    private static boolean isActive(int state) {
        return state == STATE_ACTIVE;
    }

    private static boolean isActive(ContextualMode mode) {
        return isActive(mode.getState());
    }

    private static int flipState(int state) {
        return isActive(state) ? STATE_INACTIVE : STATE_ACTIVE;
    }

    private static ContextualMode flipState(ContextualMode mode) {
        return new ContextualMode.Builder(mode).setState(flipState(mode.getState())).build();
    }

    private static class CallbackWaiter<T> implements Consumer<T> {
        private final LinkedBlockingQueue<T> mQueue = new LinkedBlockingQueue<>();

        @Override
        public void accept(T t) {
            mQueue.add(t);
        }

        @Nullable
        T getNext(long timeout, TimeUnit unit) {
            try {
                return mQueue.poll(timeout, unit);
            } catch (InterruptedException e) {
                return null;
            }
        }

        boolean waitFor(Predicate<T> filter, long timeout, TimeUnit unit) {
            long timeoutMs = unit.toMillis(timeout);
            long start = SystemClock.elapsedRealtime();
            while (true) {
                T next = getNext(timeoutMs, TimeUnit.MILLISECONDS);
                if (next == null) {
                    // Timeout.
                    return false;
                }
                if (filter.test(next)) {
                    return true;
                }
                timeoutMs -= (SystemClock.elapsedRealtime() - start);
                if (timeoutMs <= 0) {
                    // Timeout.
                    return false;
                }
            }
        }

        boolean waitFor(T expected, long timeout, TimeUnit unit) {
            return waitFor(t -> Objects.equals(t, expected), timeout, unit);
        }
    }
}
