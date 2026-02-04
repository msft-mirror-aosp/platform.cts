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

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();

        mAttentionManager = mContext.getSystemService(AttentionManager.class);
        assumeTrue("AttentionService is not available on this device", mAttentionManager != null);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterAndUnregisterListener() {
        final InteractionListener listener = (interactionInfo) -> {};
        final Executor executor = Executors.newSingleThreadExecutor();

        // Register the listener
        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_ALL, DEFAULT_DEBOUNCE_TIME, executor, listener);

        // Unregister the listener
        mAttentionManager.unregisterInteractionListener();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testUnregisterNonUnregisteredListener() {
        // Calling Unregister without registering a listener should throw an exception.
        assertThrows(
                RuntimeException.class, () -> mAttentionManager.unregisterInteractionListener());
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithInvalidDebounce() {
        final InteractionListener listener = (interactionInfo) -> {};
        final Executor executor = Executors.newSingleThreadExecutor();

        // Registering a listener with debounce time less than 500ms should throw an exception.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAttentionManager.registerInteractionListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                Duration.ofMillis(499),
                                executor,
                                listener));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithNullExecutor() {
        final InteractionListener listener = (interactionInfo) -> {};
        assertThrows(
                NullPointerException.class,
                () ->
                        mAttentionManager.registerInteractionListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                Duration.ofMillis(500),
                                null,
                                listener));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testRegisterListenerWithNullListener() {
        final Executor executor = Executors.newSingleThreadExecutor();
        assertThrows(
                NullPointerException.class,
                () ->
                        mAttentionManager.registerInteractionListener(
                                AttentionManager.INTERACTION_TYPE_ALL,
                                Duration.ofMillis(500),
                                executor,
                                null));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testKeyInteraction() throws InterruptedException {
        final UinputKeyboard uinputKeyboard =
                new UinputKeyboard(
                        mInstrumentation, java.util.Collections.singletonList("KEY_Q"), 1);

        final BlockingInteractionListener listener = new BlockingInteractionListener();
        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_KEY,
                DEFAULT_DEBOUNCE_TIME,
                Executors.newSingleThreadExecutor(),
                listener);

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
            mAttentionManager.unregisterInteractionListener();
            uinputKeyboard.close();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testTouchInteraction() throws InterruptedException {
        final Display display;
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        UinputTouchScreen uinputTouchScreen = new UinputTouchScreen(mInstrumentation, display);
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();

        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_GESTURE,
                DEFAULT_DEBOUNCE_TIME,
                executor,
                listener);

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
            mAttentionManager.unregisterInteractionListener();
            uinputTouchScreen.close();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testHoverInteraction() throws InterruptedException {
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();
        final UinputMouse uinputMouse = new UinputMouse(mInstrumentation);

        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_HOVER, DEFAULT_DEBOUNCE_TIME, executor, listener);

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
            mAttentionManager.unregisterInteractionListener();
            uinputMouse.close();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testInteractionReportsLatestTime() throws InterruptedException {
        final UinputKeyboard uinputKeyboard =
                new UinputKeyboard(
                        mInstrumentation, java.util.Collections.singletonList("KEY_Q"), 1);
        final UinputMouse uinputMouse = new UinputMouse(mInstrumentation);
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();

        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_ALL, DEFAULT_DEBOUNCE_TIME, executor, listener);

        try {
            // Inject a key event
            uinputKeyboard.injectKeyDown(EvdevInputEventCodes.KEY_Q);
            uinputKeyboard.injectKeyUp(EvdevInputEventCodes.KEY_Q);

            final long firstEventInjectionUptimeMillis = SystemClock.uptimeMillis();

            // Inject a hover-hover event
            uinputMouse.move(10, 10);
            uinputMouse.sync();

            // Expect a KEY and HOVER interaction
            InteractionInfo info = listener.waitForInteraction();
            assertNotNull("Expected interaction", info);
            assertEquals(
                    "Expected key and gesture interaction type",
                    AttentionManager.INTERACTION_TYPE_KEY | AttentionManager.INTERACTION_TYPE_HOVER,
                    info.getInteractionTypes());
            assertTrue(
                    "Interaction time should be greater than the first event injection time",
                    info.getInteractionTimeMillis() > firstEventInjectionUptimeMillis);

            // Expect a NONE interaction to indicate end activity
            info = listener.waitForInteraction();
            assertNotNull("Expected none interaction", info);
            assertEquals(
                    "Expected none interaction type",
                    AttentionManager.INTERACTION_TYPE_NONE,
                    info.getInteractionTypes());
        } finally {
            mAttentionManager.unregisterInteractionListener();
            uinputMouse.close();
            uinputKeyboard.close();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    @Test
    public void testDebounce() throws InterruptedException {
        final BlockingInteractionListener listener = new BlockingInteractionListener();
        final Executor executor = Executors.newSingleThreadExecutor();
        final UinputKeyboard uinputKeyboard =
                new UinputKeyboard(
                        mInstrumentation, java.util.Collections.singletonList("KEY_Q"), 1);
        mAttentionManager.registerInteractionListener(
                AttentionManager.INTERACTION_TYPE_KEY, DEFAULT_DEBOUNCE_TIME, executor, listener);

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
            mAttentionManager.unregisterInteractionListener();
            uinputKeyboard.close();
        }
    }
}
