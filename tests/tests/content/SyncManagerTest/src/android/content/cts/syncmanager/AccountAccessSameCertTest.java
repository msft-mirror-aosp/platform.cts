/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.content.cts.syncmanager;

import static com.android.cts.content.Utils.ALWAYS_SYNCABLE_AUTHORITY;
import static com.android.cts.content.Utils.SYNC_TIMEOUT_MILLIS;
import static com.android.cts.content.Utils.allowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.disallowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.hasDataConnection;
import static com.android.cts.content.Utils.requestSync;
import static com.android.cts.content.Utils.withAccount;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.AbstractThreadedSyncAdapter;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.cts.content.AlwaysSyncableSyncService;
import com.android.cts.content.FlakyTestRule;
import com.android.cts.content.StubActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests whether a sync adapter can access accounts. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Sync manager not supported")
@AppModeNonSdkSandbox(reason = "Sync adapter not supported since sandboxes cannot declare services")
public final class AccountAccessSameCertTest {
    private static final String THREAD_NAME = "AccountAccessSameCertTestBackgroundThread";
    @Rule public final TestRule mFlakyTestTRule = new FlakyTestRule(3);

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
    public void testAccountAccess_sameCertAsAuthenticatorCanSeeAccount() {
        assumeTrue(hasDataConnection());

        try (ActivityScenario<StubActivity> scenario =
                ActivityScenario.launch(StubActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
                        handlerThread.start();
                        Handler handler = new Handler(handlerThread.getLooper());
                        handler.post(
                                () -> {
                                    try (AutoCloseable ignored = withAccount(activity)) {
                                        AbstractThreadedSyncAdapter adapter =
                                                AlwaysSyncableSyncService.getInstance(activity)
                                                        .setNewDelegate();

                                        requestSync(ALWAYS_SYNCABLE_AUTHORITY);

                                        verify(adapter, timeout(SYNC_TIMEOUT_MILLIS))
                                                .onPerformSync(any(), any(), any(), any(), any());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                        // Wait for the handlerThread to finish
                        try {
                            handlerThread.quitSafely();
                            handlerThread.join(SYNC_TIMEOUT_MILLIS);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
