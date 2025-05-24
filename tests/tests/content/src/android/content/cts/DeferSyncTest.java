/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.content.cts;

import static com.android.cts.content.Utils.ALWAYS_SYNCABLE_AUTHORITY;
import static com.android.cts.content.Utils.NOT_ALWAYS_SYNCABLE_AUTHORITY;
import static com.android.cts.content.Utils.SYNC_TIMEOUT_MILLIS;
import static com.android.cts.content.Utils.allowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.disallowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.hasDataConnection;
import static com.android.cts.content.Utils.requestSync;
import static com.android.cts.content.Utils.withAccount;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.cts.content.AlwaysSyncableSyncService;
import com.android.cts.content.FlakyTestRule;
import com.android.cts.content.NotAlwaysSyncableSyncService;
import com.android.cts.content.StubActivity;
import com.android.cts.content.Utils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Sync manage not supported")
public final class DeferSyncTest {
    private static final String THREAD_NAME = "DeferSyncTestBackgroundThread";
    @Rule public final TestRule mFlakyTestRule = new FlakyTestRule(3);

    @Rule
    public final ActivityScenarioRule<StubActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(StubActivity.class);

    @Before
    public void setUp() throws Exception {
        allowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @After
    public void tearDown() throws Exception {
        disallowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @Test
    public void noSyncsWhenDeferred() {
        assumeTrue(hasDataConnection());
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        SettableFuture<Boolean> hasFinishedFuture = SettableFuture.create();
        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AbstractThreadedSyncAdapter notAlwaysSyncableAdapter =
                                NotAlwaysSyncableSyncService.getInstance(activity).setNewDelegate();
                        AbstractThreadedSyncAdapter alwaysSyncableAdapter =
                                AlwaysSyncableSyncService.getInstance(activity).setNewDelegate();

                        when(alwaysSyncableAdapter.onUnsyncableAccount()).thenReturn(false);
                        when(notAlwaysSyncableAdapter.onUnsyncableAccount()).thenReturn(false);
                        handler.post(
                                () -> {
                                    try (Utils.ClosableAccount ignored = withAccount(activity)) {
                                        requestSync(NOT_ALWAYS_SYNCABLE_AUTHORITY);
                                        requestSync(ALWAYS_SYNCABLE_AUTHORITY);

                                        SystemClock.sleep(SYNC_TIMEOUT_MILLIS);

                                        verify(notAlwaysSyncableAdapter, atLeast(1))
                                                .onUnsyncableAccount();
                                        verify(notAlwaysSyncableAdapter, never())
                                                .onPerformSync(any(), any(), any(), any(), any());

                                        verify(alwaysSyncableAdapter, atLeast(1))
                                                .onUnsyncableAccount();
                                        verify(alwaysSyncableAdapter, never())
                                                .onPerformSync(any(), any(), any(), any(), any());
                                        hasFinishedFuture.set(true);
                                    } catch (Exception e) {
                                        hasFinishedFuture.set(false);
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        }
        try {
            hasFinishedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void deferSyncAndMakeSyncable() {
        assumeTrue(hasDataConnection());
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        SettableFuture<Boolean> hasFinishedFuture = SettableFuture.create();
        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AbstractThreadedSyncAdapter adapter =
                                NotAlwaysSyncableSyncService.getInstance(activity).setNewDelegate();
                        when(adapter.onUnsyncableAccount()).thenReturn(false);
                        handler.post(
                                () -> {
                                    try (Utils.ClosableAccount account = withAccount(activity)) {
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onUnsyncableAccount();

                                        // Enable the adapter by making the account/provider
                                        // syncable
                                        ContentResolver.setIsSyncable(
                                                account.account, NOT_ALWAYS_SYNCABLE_AUTHORITY, 1);
                                        requestSync(NOT_ALWAYS_SYNCABLE_AUTHORITY);

                                        ArgumentCaptor<Bundle> extrasCaptor =
                                                forClass(Bundle.class);
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onPerformSync(
                                                        any(),
                                                        extrasCaptor.capture(),
                                                        any(),
                                                        any(),
                                                        any());

                                        // As the adapter is made syncable, we should not get an
                                        // initialization sync
                                        assertThat(
                                                        extrasCaptor
                                                                .getValue()
                                                                .containsKey(
                                                                        ContentResolver
                                                                                .SYNC_EXTRAS_INITIALIZE))
                                                .isFalse();
                                        hasFinishedFuture.set(true);
                                    } catch (Exception e) {
                                        hasFinishedFuture.set(false);
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        }
        try {
            hasFinishedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void deferSyncAndReportIsReady() {
        assumeTrue(hasDataConnection());
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        SettableFuture<Boolean> hasFinishedFuture = SettableFuture.create();
        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AbstractThreadedSyncAdapter adapter =
                                NotAlwaysSyncableSyncService.getInstance(activity).setNewDelegate();
                        when(adapter.onUnsyncableAccount()).thenReturn(false);
                        handler.post(
                                () -> {
                                    try (Utils.ClosableAccount ignored = withAccount(activity)) {
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onUnsyncableAccount();

                                        // Enable the adapter by returning true from onNewAccount
                                        when(adapter.onUnsyncableAccount()).thenReturn(true);
                                        requestSync(NOT_ALWAYS_SYNCABLE_AUTHORITY);
                                        verify(adapter, atLeast(1)).onUnsyncableAccount();

                                        ArgumentCaptor<Bundle> extrasCaptor =
                                                forClass(Bundle.class);
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onPerformSync(
                                                        any(),
                                                        extrasCaptor.capture(),
                                                        any(),
                                                        any(),
                                                        any());

                                        // As the adapter is not syncable yet, we should get an
                                        // initialization sync
                                        assertThat(
                                                        extrasCaptor
                                                                .getValue()
                                                                .getBoolean(
                                                                        ContentResolver
                                                                                .SYNC_EXTRAS_INITIALIZE))
                                                .isTrue();
                                        hasFinishedFuture.set(true);
                                    } catch (Exception e) {
                                        hasFinishedFuture.set(false);
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        }
        try {
            hasFinishedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void deferSyncAndReportIsReadyAlwaysSyncable() {
        assumeTrue(hasDataConnection());
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        SettableFuture<Boolean> hasFinishedFuture = SettableFuture.create();
        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AbstractThreadedSyncAdapter adapter =
                                AlwaysSyncableSyncService.getInstance(activity).setNewDelegate();
                        when(adapter.onUnsyncableAccount()).thenReturn(false);
                        handler.post(
                                () -> {
                                    try (Utils.ClosableAccount ignored = withAccount(activity)) {
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onUnsyncableAccount();

                                        // Enable the adapter by returning true from onNewAccount
                                        when(adapter.onUnsyncableAccount()).thenReturn(true);
                                        requestSync(ALWAYS_SYNCABLE_AUTHORITY);
                                        verify(adapter, atLeast(1)).onUnsyncableAccount();

                                        ArgumentCaptor<Bundle> extrasCaptor =
                                                forClass(Bundle.class);
                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onPerformSync(
                                                        any(),
                                                        extrasCaptor.capture(),
                                                        any(),
                                                        any(),
                                                        any());

                                        // The adapter is always syncable, hence there is no init
                                        // sync
                                        assertThat(
                                                        extrasCaptor
                                                                .getValue()
                                                                .containsKey(
                                                                        ContentResolver
                                                                                .SYNC_EXTRAS_INITIALIZE))
                                                .isFalse();
                                        hasFinishedFuture.set(true);
                                    } catch (Exception e) {
                                        hasFinishedFuture.set(false);
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        }
        try {
            hasFinishedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            handlerThread.quitSafely();
        }
    }

    @Test
    public void onNewAccountForEachAccount() {
        assumeTrue(hasDataConnection());
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        SettableFuture<Boolean> hasFinishedFuture = SettableFuture.create();
        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AbstractThreadedSyncAdapter adapter =
                                NotAlwaysSyncableSyncService.getInstance(activity).setNewDelegate();
                        when(adapter.onUnsyncableAccount()).thenReturn(true, false);
                        handler.post(
                                () -> {
                                    try (Utils.ClosableAccount ignored = withAccount(activity)) {
                                        try (Utils.ClosableAccount ignored1 =
                                                withAccount(activity)) {
                                            verify(adapter, timeout(SYNC_TIMEOUT_MILLIS).atLeast(2))
                                                    .onUnsyncableAccount();

                                            // Exactly account should have gotten the init-sync. No
                                            // further syncs happen as
                                            // onNewAccount returns false again.
                                            ArgumentCaptor<Bundle> extrasCaptor =
                                                    forClass(Bundle.class);
                                            verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                    .onPerformSync(
                                                            any(),
                                                            extrasCaptor.capture(),
                                                            any(),
                                                            any(),
                                                            any());
                                            assertThat(
                                                            extrasCaptor
                                                                    .getValue()
                                                                    .getBoolean(
                                                                            ContentResolver
                                                                                    .SYNC_EXTRAS_INITIALIZE))
                                                    .isTrue();
                                            hasFinishedFuture.set(true);
                                        }
                                    } catch (Exception e) {
                                        hasFinishedFuture.set(false);
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        }
        try {
            hasFinishedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            handlerThread.quitSafely();
        }
    }
}
