/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.attentionservice.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.attention.AttentionManager;
import android.attention.InteractionInfo;
import android.attention.InteractionListener;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.input.EvdevInputEventCodes;
import com.android.cts.input.UinputKeyboard;
import com.android.cts.input.UinputMouse;
import com.android.cts.input.UinputTouchDevice.Pointer;
import com.android.cts.input.UinputTouchScreen;
import com.android.input.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
@RunWith(AndroidJUnit4.class)
public class AttentionManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Duration TEST_TIMEOUT = Duration.ofMillis(10000);
    private static final Duration DEFAULT_DEBOUNCE_TIME = Duration.ofMillis(500);

    private AttentionManager mAttentionManager;
    private Context mContext;
    private Instrumentation mInstrumentation;

    private static class BlockingInteractionListener implements InteractionListener {
        private final LinkedBlockingQueue<InteractionInfo> mInteractions =
                new LinkedBlockingQueue<>();

        @Override
        public void onInteraction(@NonNull InteractionInfo interactionInfo) {
            mInteractions.offer(interactionInfo);
        }

        private InteractionInfo waitForInteraction() throws InterruptedException {
            return mInteractions.poll(
                    AttentionManagerTest.TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void registerListener(
            int interactionTypes,
            Executor executor,
            InteractionListener listener,
            Duration debounceTime) {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mAttentionManager.registerInteractionListener(
                interactionTypes, debounceTime, executor, listener);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    private void unregisterListener() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mAttentionManager.unregisterInteractionListener();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mAttentionManager = mContext.getSystemService(AttentionManager.class);
        assumeTrue("AttentionService is not available on this device", mAttentionManager != null);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
                "android.attention.AttentionManager#unregisterInteractionListener",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterAndUnregisterListener() {
        final InteractionListener listener = (interactionInfo) -> {};
        final Executor executor = Executors.newSingleThreadExecutor();

        registerListener(
                AttentionManager.INTERACTION_TYPE_ALL, executor, listener, DEFAULT_DEBOUNCE_TIME);
        unregisterListener();
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#unregisterInteractionListener",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testUnregisterNonUnregisteredListener() {
        // Calling Unregister without registering a listener should throw an exception.
        assertThrows(RuntimeException.class, this::unregisterListener);
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithInvalidDebounce() {
        final InteractionListener listener = (interactionInfo) -> {};
        final Executor executor = Executors.newSingleThreadExecutor();

        // Registering a listener with debounce time less than 500ms should throw an exception.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        registerListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                executor,
                                listener,
                                Duration.ofMillis(499)));
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithNullExecutor() {
        final InteractionListener listener = (interactionInfo) -> {};
        assertThrows(
                NullPointerException.class,
                () ->
                        registerListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                null,
                                listener,
                                DEFAULT_DEBOUNCE_TIME));
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithNullListener() {
        final Executor executor = Executors.newSingleThreadExecutor();
        assertThrows(
                NullPointerException.class,
                () ->
                        registerListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                executor,
                                null,
                                DEFAULT_DEBOUNCE_TIME));
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
                "android.attention.AttentionManager#unregisterInteractionListener",
                "android.attention.InteractionInfo#getInteractionTimeMillis",
                "android.attention.InteractionInfo#getInteractionTypes",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testKeyInteraction() throws InterruptedException {
        final UinputKeyboard uinputKeyboard =
                new UinputKeyboard(
                        mInstrumentation, java.util.Collections.singletonList("KEY_Q"), 1);

        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();
        registerListener(
                AttentionManager.INTERACTION_TYPE_KEY, executor, listener, DEFAULT_DEBOUNCE_TIME);

        try {
            // Inject a key event
            final long inputEventInjectionUptimeMillis = SystemClock.uptimeMillis();
            uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q);
            uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q);

            // Expect a KEY interaction
            InteractionInfo info = listener.waitForInteraction();
            assertNotNull("Expected key interaction", info);
            assertEquals(
                    "Expected key interaction type",
                    AttentionManager.INTERACTION_TYPE_KEY,
                    info.getInteractionTypes());
            assertTrue(
                    "Interaction time should be greater than or equal to input event injection "
                            + "time",
                    info.getInteractionTimeMillis() >= inputEventInjectionUptimeMillis);

            // Expect a NONE interaction to indicate end activity
            info = listener.waitForInteraction();
            assertNotNull("Expected none interaction", info);
            assertEquals(
                    "Expected none interaction type",
                    AttentionManager.INTERACTION_TYPE_NONE,
                    info.getInteractionTypes());
        } finally {
            unregisterListener();
            uinputKeyboard.close();
        }
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
                "android.attention.AttentionManager#unregisterInteractionListener",
                "android.attention.InteractionInfo#getInteractionTimeMillis",
                "android.attention.InteractionInfo#getInteractionTypes",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testTouchInteraction() throws InterruptedException {
        final Display display;
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        UinputTouchScreen uinputTouchScreen = new UinputTouchScreen(mInstrumentation, display);
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();

        registerListener(
                AttentionManager.INTERACTION_TYPE_GESTURE,
                executor,
                listener,
                DEFAULT_DEBOUNCE_TIME);

        try {
            // Inject a tap event
            final long inputEventInjectionUptimeMillis = SystemClock.uptimeMillis();
            int x = display.getWidth() / 2;
            int y = display.getHeight() / 2;
            Pointer pointer = uinputTouchScreen.touchDown(x, y);
            pointer.lift();

            // Expect a GESTURE interaction
            InteractionInfo info = listener.waitForInteraction();
            assertNotNull("Expected gesture interaction", info);
            assertEquals(
                    "Expected gesture interaction type",
                    AttentionManager.INTERACTION_TYPE_GESTURE,
                    info.getInteractionTypes());
            assertTrue(
                    "Interaction time should be greater than or equal to input event injection "
                            + "time",
                    info.getInteractionTimeMillis() >= inputEventInjectionUptimeMillis);

            // Expect a NONE interaction to indicate end activity
            info = listener.waitForInteraction();
            assertNotNull("Expected none interaction", info);
            assertEquals(
                    "Expected none interaction type",
                    AttentionManager.INTERACTION_TYPE_NONE,
                    info.getInteractionTypes());
        } finally {
            unregisterListener();
            uinputTouchScreen.close();
        }
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
                "android.attention.AttentionManager#unregisterInteractionListener",
                "android.attention.InteractionInfo#getInteractionTimeMillis",
                "android.attention.InteractionInfo#getInteractionTypes",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testHoverInteraction() throws InterruptedException {
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();
        final UinputMouse uinputMouse = new UinputMouse(mInstrumentation);

        registerListener(
                AttentionManager.INTERACTION_TYPE_HOVER, executor, listener, DEFAULT_DEBOUNCE_TIME);

        try {
            // Inject a hover event (mouse move)
            final long inputEventInjectionUptimeMillis = SystemClock.uptimeMillis();
            uinputMouse.move(10, 10);
            uinputMouse.sync();

            // Expect a HOVER interaction
            InteractionInfo info = listener.waitForInteraction();
            assertNotNull("Expected hover interaction", info);
            assertEquals(
                    "Expected hover interaction type",
                    AttentionManager.INTERACTION_TYPE_HOVER,
                    info.getInteractionTypes());
            assertTrue(
                    "Interaction time should be greater than or equal to input event injection "
                            + "time",
                    info.getInteractionTimeMillis() >= inputEventInjectionUptimeMillis);

            // Expect a NONE interaction to indicate end activity
            info = listener.waitForInteraction();
            assertNotNull("Expected none interaction", info);
            assertEquals(
                    "Expected none interaction type",
                    AttentionManager.INTERACTION_TYPE_NONE,
                    info.getInteractionTypes());
        } finally {
            unregisterListener();
            uinputMouse.close();
        }
    }

    @ApiTest(
            apis = {
                "android.attention.AttentionManager#registerInteractionListener",
                "android.attention.AttentionManager#unregisterInteractionListener",
                "android.attention.InteractionInfo#getInteractionTimeMillis",
                "android.attention.InteractionInfo#getInteractionTypes",
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testDebounce() throws InterruptedException {
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();
        final UinputKeyboard uinputKeyboard =
                new UinputKeyboard(
                        mInstrumentation, java.util.Collections.singletonList("KEY_Q"), 1);
        registerListener(
                AttentionManager.INTERACTION_TYPE_KEY, executor, listener, DEFAULT_DEBOUNCE_TIME);

        try {
            // Inject a key event
            uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q);
            uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q);
            // Inject another key event immediately
            uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q);
            uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q);

            // Expect a single KEY interaction notification
            InteractionInfo info = listener.waitForInteraction();
            assertNotNull("Expected key interaction", info);
            assertEquals(
                    "Expected key interaction type",
                    AttentionManager.INTERACTION_TYPE_KEY,
                    info.getInteractionTypes());

            // Expect a NONE interaction to indicate end activity
            info = listener.waitForInteraction();
            assertNotNull("Expected none interaction", info);
            assertEquals(
                    "Expected none interaction type",
                    AttentionManager.INTERACTION_TYPE_NONE,
                    info.getInteractionTypes());
        } finally {
            unregisterListener();
            uinputKeyboard.close();
        }
    }
}
