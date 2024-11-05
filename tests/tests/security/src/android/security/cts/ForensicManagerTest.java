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

package android.security.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.forensic.ForensicManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AFL_API)
public class ForensicManagerTest {
    private Context mContext;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private ForensicManager mForensicManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws InterruptedException {
        mContext = mInstrumentation.getContext();
        mForensicManager = mContext.getSystemService(ForensicManager.class);
        assertNotNull(mForensicManager);
        reset();
    }

    @After
    public void teardown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    private void reset() throws InterruptedException {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_FORENSIC_STATE,
                Manifest.permission.MANAGE_FORENSIC_STATE);
        var commandLatch = new CountDownLatch(1);

        var executor = newSingleThreadExecutor();
        mForensicManager.disable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertTrue(commandLatch.await(1, SECONDS));

        var stateLatch = new CountDownLatch(1);
        mForensicManager.addStateCallback(executor,
                state -> {
                    if (stateLatch.getCount() > 0) {
                        stateLatch.countDown();
                        assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                    }
                });
        assertTrue(stateLatch.await(1, SECONDS));
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testAddStateCallback_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mForensicManager.addStateCallback(
                executor, state -> {}));
        executor.close();
    }

    @Test
    public void testRemoveStateCallback_NoPermission() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_FORENSIC_STATE);
        var executor = newSingleThreadExecutor();
        Consumer<Integer> scb = state -> {};
        mForensicManager.addStateCallback(executor, scb);

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mForensicManager.removeStateCallback(scb));
        executor.close();
    }

    @Test
    public void testEnable_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mForensicManager.enable(
                executor, new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        fail("onSuccess shall not be called");
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                }));
        executor.close();
    }

    @Test
    public void testDisable_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mForensicManager.disable(
                executor, new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        fail("onSuccess shall not be called");
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                }));
        executor.close();
    }

    @Test
    public void testRemoveStateCallback() throws InterruptedException {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_FORENSIC_STATE,
                Manifest.permission.MANAGE_FORENSIC_STATE);

        var executor = newSingleThreadExecutor();

        var scb0Latch0 = new CountDownLatch(1);
        var scb0Latch1 = new CountDownLatch(1);
        var scb0Latch2 = new CountDownLatch(1);
        AtomicInteger scb0Counter = new AtomicInteger();
        scb0Counter.set(0);
        Consumer<Integer> scb0 = state -> {
            if (scb0Counter.get() == 0) {
                assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                scb0Latch0.countDown();
                scb0Counter.getAndIncrement();
            } else if (scb0Counter.get() == 1) {
                assertEquals(ForensicManager.STATE_ENABLED, state.intValue());
                scb0Latch1.countDown();
                scb0Counter.getAndIncrement();
            } else if (scb0Counter.get() == 2) {
                assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                scb0Latch2.countDown();
                scb0Counter.getAndIncrement();
            } else {
                fail("state callback (scb0) can only be called three times!");
            }
        };

        var scb1Latch0 = new CountDownLatch(1);
        var scb1Latch1 = new CountDownLatch(1);
        AtomicInteger scb1Counter = new AtomicInteger();
        scb1Counter.set(0);
        Consumer<Integer> scb1 = state -> {
            if (scb1Counter.get() == 0) {
                assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                scb1Latch0.countDown();
                scb1Counter.getAndIncrement();
            } else if (scb1Counter.get() == 1) {
                assertEquals(ForensicManager.STATE_ENABLED, state.intValue());
                scb1Latch1.countDown();
                scb1Counter.getAndIncrement();
            } else {
                fail("state callback (scb1) can only be called twice!");
            }
        };

        mForensicManager.addStateCallback(executor, scb0);
        mForensicManager.addStateCallback(executor, scb1);

        var commandLatch0 = new CountDownLatch(1);
        mForensicManager.enable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(scb0Latch0.await(1, SECONDS)).isTrue();
        assertThat(scb1Latch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();
        assertThat(scb0Latch1.await(1, SECONDS)).isTrue();
        assertThat(scb1Latch1.await(1, SECONDS)).isTrue();

        mForensicManager.removeStateCallback(scb1);
        var commandLatch1 = new CountDownLatch(1);
        mForensicManager.disable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch1.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch1.await(1, SECONDS)).isTrue();
        assertThat(scb0Latch2.await(1, SECONDS)).isTrue();
        executor.close();
    }

    @Test
    public void testDisable_FromDisable() throws InterruptedException {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_FORENSIC_STATE,
                Manifest.permission.MANAGE_FORENSIC_STATE);

        var executor = newSingleThreadExecutor();

        var stateLatch0 = new CountDownLatch(1);
        AtomicInteger stateCounter = new AtomicInteger();
        stateCounter.set(0);
        mForensicManager.addStateCallback(executor,
                state -> {
                    if (stateCounter.get() == 0) {
                        assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                        stateLatch0.countDown();
                        stateCounter.getAndIncrement();
                    } else {
                        fail("state callback can be called only once!");
                    }
                });

        var commandLatch0 = new CountDownLatch(1);
        mForensicManager.disable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(stateLatch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();

        executor.close();
    }

    @Test
    public void testEnable_FromEnable() throws InterruptedException {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_FORENSIC_STATE,
                Manifest.permission.MANAGE_FORENSIC_STATE);

        var executor = newSingleThreadExecutor();

        var stateLatch0 = new CountDownLatch(1);
        var stateLatch1 = new CountDownLatch(1);
        AtomicInteger stateCounter = new AtomicInteger();
        stateCounter.set(0);
        mForensicManager.addStateCallback(executor,
                state -> {
                    if (stateCounter.get() == 0) {
                        assertEquals(ForensicManager.STATE_DISABLED, state.intValue());
                        stateLatch0.countDown();
                        stateCounter.getAndIncrement();
                    } else if (stateCounter.get() == 1) {
                        assertEquals(ForensicManager.STATE_ENABLED, state.intValue());
                        stateLatch1.countDown();
                        stateCounter.getAndIncrement();
                    } else {
                        fail("state callback can only be called twice!");
                    }
                });

        var commandLatch0 = new CountDownLatch(1);
        mForensicManager.enable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(stateLatch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();
        assertThat(stateLatch1.await(1, SECONDS)).isTrue();

        var commandLatch1 = new CountDownLatch(1);
        mForensicManager.enable(executor,
                new ForensicManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch1.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch1.await(1, SECONDS)).isTrue();
        executor.close();
    }
}
