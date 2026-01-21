/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.lights.cts.tests;

import static android.graphics.Color.BLUE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.MAGENTA;
import static android.graphics.Color.RED;
import static android.graphics.Color.TRANSPARENT;
import static android.graphics.Color.YELLOW;
import static android.hardware.lights.LightsRequest.Builder;
import static android.os.SystemClock.sleep;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.hardware.lights.ColorSequence;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.hardware.lights.MultiLightEffect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.lights.feature.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class LightsManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int ON_TAN = 0xffd2b48c;
    private static final int ON_RED = 0xffff0000;
    private static final LightState STATE_TAN = new LightState(ON_TAN);
    private static final LightState STATE_RED = new LightState(ON_RED);
    private static final int HIGH_PRIORITY = Integer.MAX_VALUE;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private LightsManager mManager;
    private List<Light> mLights;
    private List<Light> mEffectLights = new ArrayList<Light>();
    private List<Light> mStaticLights = new ArrayList<Light>();

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.CONTROL_DEVICE_LIGHTS);

        mManager = mContext.getSystemService(LightsManager.class);
        mLights = mManager.getLights();
        for (Light light : mLights) {
            if (Flags.enableLightAnimations() && light.hasAnimationControl()) {
                mEffectLights.add(light);
            } else {
                mStaticLights.add(light);
            }
        }
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testControlLightsPermissionIsRequiredToUseLights() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        try {
            mManager.getLights();
            fail("Expected SecurityException to be thrown for getLights()");
        } catch (SecurityException expected) {
        }

        try (LightsManager.LightsSession session = mManager.openSession()) {
            fail("Expected SecurityException to be thrown for openSession()");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testControlSingleLight() {
        assumeTrue(mLights.size() >= 1);

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When the session requests to turn a single light on:
            session.requestLights(new Builder()
                    .addLight(mLights.get(0), STATE_RED)
                    .build());

            // Then the light should turn on.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(ON_RED);
        }
    }

    @Test
    public void testControlMultipleLights() {
        assumeTrue(mLights.size() >= 2);

        int[] initialColors = new int[mLights.size()];
        for (int i = 0; i < mLights.size(); i++) {
            initialColors[i] = mManager.getLightState(mLights.get(i)).getColor();
        }

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When the session requests to turn two of the lights on:
            session.requestLights(new Builder()
                    .addLight(mLights.get(0), new LightState(0xffaaaaff))
                    .addLight(mLights.get(1), new LightState(0xffbbbbff))
                    .build());

            // Then both should turn on.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(0xffaaaaff);
            assertThat(mManager.getLightState(mLights.get(1)).getColor()).isEqualTo(0xffbbbbff);

            // Any others should remain in their initial state.
            for (int i = 2; i < mLights.size(); i++) {
                assertThat(mManager.getLightState(mLights.get(i)).getColor()).isEqualTo(
                        initialColors[i]);
            }
        }
    }

    @Test
    public void testControlLights_onlyEffectiveForLifetimeOfClient() {
        assumeTrue(mLights.size() >= 1);

        int initialColor = mManager.getLightState(mLights.get(0)).getColor();

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When a session commits changes:
            session.requestLights(new Builder().addLight(mLights.get(0), STATE_TAN).build());
            // Then the light should turn on.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(ON_TAN);

            // When the session goes away:
            session.close();
            // Then the light should return to its initial state.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(initialColor);
        }
    }

    @Test
    public void testControlLights_firstCallerWinsContention() {
        assumeTrue(mLights.size() >= 1);

        int initialColor = mManager.getLightState(mLights.get(0)).getColor();

        try (LightsManager.LightsSession session1 = mManager.openSession(HIGH_PRIORITY);
                LightsManager.LightsSession session2 = mManager.openSession(HIGH_PRIORITY)) {

            // When session1 and session2 both request the same light:
            session1.requestLights(new Builder().addLight(mLights.get(0), STATE_TAN).build());
            session2.requestLights(new Builder().addLight(mLights.get(0), STATE_RED).build());
            // Then session1 should win because it was created first.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(ON_TAN);

            // When session1 goes away:
            session1.close();
            // Then session2 should have its request go into effect.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(ON_RED);

            // When session2 goes away:
            session2.close();
            // Then the light should return to its initial state because there are no more sessions.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(initialColor);
        }
    }

    @Test
    public void testClearLight() {
        assumeTrue(mLights.size() >= 1);

        int initialColor = mManager.getLightState(mLights.get(0)).getColor();

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When the session turns a light on:
            session.requestLights(new Builder().addLight(mLights.get(0), STATE_RED).build());
            // And then the session clears it again:
            session.requestLights(new Builder().clearLight(mLights.get(0)).build());
            // Then the light should return to its initial state.
            assertThat(mManager.getLightState(mLights.get(0)).getColor()).isEqualTo(initialColor);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testDynamicLightsHaveMinUpdatePeriod() {
        for (Light light : mEffectLights) {
            assertThat(light.getMinUpdatePeriodMillis()).isGreaterThan(0);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testColorSequenceValidation() {
        ColorSequence.Builder colorSequenceBuilder = new ColorSequence.Builder();

        // 0 delay is valid for the first control point as a start value.
        colorSequenceBuilder.addControlPoint(0, BLUE);
        // Can append another valid sequence.
        colorSequenceBuilder.addControlPoints(
                new ColorSequence.Builder()
                        .addControlPoint(100, GREEN)
                        .addControlPoint(200, RED)
                        .build());
        // Can append control points through arrays.
        colorSequenceBuilder.addControlPoints(new long[] {300, 400}, new int[] {YELLOW, MAGENTA});

        // 0 delay after the first control point is not a valid value.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    colorSequenceBuilder.addControlPoint(0, RED);
                });
        // A valid color sequence with starting point becomes invalid as a middle segment.
        ColorSequence middleSegments =
                new ColorSequence.Builder()
                        .addControlPoint(0, GREEN)
                        .addControlPoint(100, BLUE)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(middleSegments);
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(
                            new long[] {0, 100}, new int[] {YELLOW, MAGENTA});
                });

        // Bad inputs.
        assertThrows(
                NullPointerException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(null);
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(null, new int[] {YELLOW, MAGENTA});
                });

        assertThrows(
                NullPointerException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(new long[] {100, 100}, null);
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    colorSequenceBuilder.addControlPoints(
                            new long[] {100}, new int[] {YELLOW, MAGENTA});
                });

        ColorSequence colorSequence = colorSequenceBuilder.build();
        assertThat(colorSequence.getDelaysMillis())
                .asList()
                .containsExactly(0L, 100L, 200L, 300L, 400L)
                .inOrder();
        assertThat(colorSequence.getColors())
                .asList()
                .containsExactly(BLUE, GREEN, RED, YELLOW, MAGENTA)
                .inOrder();
        assertThat(colorSequence.getInterpolationMode())
                .isEqualTo(ColorSequence.INTERPOLATION_MODE_LINEAR);
        assertThat(colorSequence.getDurationMillis()).isEqualTo(1000);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testMultiLightEffectValidation() {
        assumeTrue("Skipped. Test requires animatable lights.", mEffectLights.size() >= 1);

        Light light = mEffectLights.getFirst();
        ColorSequence colorSequence =
                new ColorSequence.Builder()
                        .addControlPoint(0, BLUE)
                        .addControlPoint(500, GREEN)
                        .build();

        // Validate creating a well constructed effect.
        MultiLightEffect.Builder effectBuilder =
                new MultiLightEffect.Builder().addLightSequence(light, colorSequence);
        LightsRequest.Builder requestBuilder =
                new LightsRequest.Builder().setEffect(effectBuilder.build());

        Map<Integer, ColorSequence> sequenceMap = requestBuilder.build().getEffect().getSequences();
        assertThat(sequenceMap.keySet()).containsExactly(light.getId());
        assertThat(sequenceMap.get(light.getId()).getDelaysMillis())
                .asList()
                .containsExactly(0L, 500L)
                .inOrder();
        assertThat(sequenceMap.get(light.getId()).getColors())
                .asList()
                .containsExactly(BLUE, GREEN)
                .inOrder();

        // Bad sequence parameters:
        assertThrows(
                NullPointerException.class,
                () -> {
                    new MultiLightEffect.Builder().addLightSequence(null, colorSequence).build();
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    new MultiLightEffect.Builder().addLightSequence(light, null).build();
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new MultiLightEffect.Builder().build();
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testLightEffect_noInterpolation() {
        assumeTrue("Skipped. Test requires animatable lights.", mEffectLights.size() >= 1);

        Light light = mEffectLights.getFirst();
        ColorSequence colorSequence =
                new ColorSequence.Builder()
                        .addControlPoint(0, BLUE)
                        .addControlPoint(500, GREEN)
                        .setInterpolationMode(ColorSequence.INTERPOLATION_MODE_NONE)
                        .build();

        // Validate that hte
        MultiLightEffect.Builder effectBuilder =
                new MultiLightEffect.Builder().addLightSequence(light, colorSequence);
        Map<Integer, ColorSequence> sequenceMap = effectBuilder.build().getSequences();
        assertThat(sequenceMap.keySet()).containsExactly(light.getId());
        assertThat(sequenceMap.get(light.getId()).getInterpolationMode())
                .isEqualTo(ColorSequence.INTERPOLATION_MODE_NONE);

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When the session plays an effect on the light:
            session.requestLights(new Builder().setEffect(effectBuilder.build()).build());

            // The light is configured with a sequence.
            ColorSequence sequence = mManager.getLightSequence(light);

            // Service received the interpolation type and kept it.
            assertThat(sequence.getInterpolationMode())
                    .isEqualTo(ColorSequence.INTERPOLATION_MODE_NONE);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testRequestValidation_onlyEffectsOnRequest() {
        assumeTrue("Skipped. Test requires animatable lights.", mEffectLights.size() >= 1);

        Light light = mEffectLights.getFirst();
        LightsRequest.Builder requestBuilder = new LightsRequest.Builder();

        requestBuilder.setEffect(
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(100, BLUE).build())
                        .build());

        assertThrows(
                IllegalStateException.class,
                () -> {
                    requestBuilder.addLight(
                            light, new LightState.Builder().setColor(MAGENTA).build());
                });

        assertThrows(
                IllegalStateException.class,
                () -> {
                    requestBuilder.clearLight(light);
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testRequestValidation_onlyStatesOnRequest() {
        assumeTrue("Skipped. Test requires animatable lights.", mEffectLights.size() >= 1);

        Light light = mEffectLights.getFirst();
        LightsRequest.Builder requestBuilder = new LightsRequest.Builder();

        requestBuilder.addLight(light, new LightState.Builder().setColor(MAGENTA).build());

        assertThrows(
                IllegalStateException.class,
                () -> {
                    requestBuilder.setEffect(
                            new MultiLightEffect.Builder()
                                    .addLightSequence(
                                            light,
                                            new ColorSequence.Builder()
                                                    .addControlPoint(100, BLUE)
                                                    .build())
                                    .build());
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testRequestValidation_rejectEffectsOnStaticLights() {
        assumeTrue("Skipped. Test requires non-animatable lights.", mStaticLights.size() >= 1);

        Light light = mStaticLights.getFirst();
        LightsRequest.Builder requestBuilder = new LightsRequest.Builder();

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    requestBuilder.setEffect(
                            new MultiLightEffect.Builder()
                                    .addLightSequence(
                                            light,
                                            new ColorSequence.Builder()
                                                    .addControlPoint(100, BLUE)
                                                    .build())
                                    .build());
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect() {
        assumeTrue("Skipped. Test requires animatable lights.", mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(500, BLUE).build())
                        .build();

        try (LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY)) {
            // When the session plays an effect on the light:
            session.requestLights(new Builder().setEffect(effect).build());

            // The light is configured with a sequence.
            ColorSequence sequence = mManager.getLightSequence(light);

            // Validate the internal state of the service.
            assertThat(sequence.getColors().length).isEqualTo(1);
            assertThat(sequence.getColors()[0]).isEqualTo(BLUE);
            assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
            assertThat(sequence.getDelaysMillis()[0]).isEqualTo(500L);
            assertThat(sequence.getInterpolationMode())
                    .isEqualTo(ColorSequence.INTERPOLATION_MODE_LINEAR);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect_preemptWithState() {
        assumeTrue(mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(2000, BLUE).build())
                        .build();

        LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY);
        // When the session plays an effect on the light:
        session.requestLights(new Builder().setEffect(effect).build());

        // Validate the internal state of the light is the sequence.
        ColorSequence sequence = mManager.getLightSequence(light);
        LightState state = mManager.getLightState(light);
        assertThat(sequence.getColors().length).isEqualTo(1);
        assertThat(sequence.getColors()[0]).isEqualTo(BLUE);
        assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
        assertThat(sequence.getDelaysMillis()[0]).isEqualTo(2000L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // Let the light on for a few milliseconds and change to a state.
        sleep(100);
        session.requestLights(new LightsRequest.Builder().addLight(light, STATE_RED).build());

        // Validate that the state is red.
        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(state.getColor()).isEqualTo(RED);
        assertThat(sequence).isNull();

        session.close();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect_preemptWithEffect() {
        assumeTrue(mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(2000, BLUE).build())
                        .setPreemptive(true)
                        .build();
        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect2 =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(500, RED).build())
                        .setPreemptive(true)
                        .build();

        LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY);
        // When the session plays an effect on the light:
        session.requestLights(new Builder().setEffect(effect).build());

        // Validate the internal state of the light is the sequence.
        ColorSequence sequence = mManager.getLightSequence(light);
        LightState state = mManager.getLightState(light);
        assertThat(sequence.getColors().length).isEqualTo(1);
        assertThat(sequence.getColors()[0]).isEqualTo(BLUE);
        assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
        assertThat(sequence.getDelaysMillis()[0]).isEqualTo(2000L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // Let the light on for a few milliseconds and change to a different effect.
        sleep(100);
        session.requestLights(new Builder().setEffect(effect2).build());

        // Validate that the state is transparent.
        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence.getColors().length).isEqualTo(1);
        assertThat(sequence.getColors()[0]).isEqualTo(RED);
        assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
        assertThat(sequence.getDelaysMillis()[0]).isEqualTo(500L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        session.close();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect_transitionToContinuationEffect() {
        assumeTrue(mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(500, BLUE).build())
                        .setPreemptive(true)
                        .build();
        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect2 =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(300, RED).build())
                        .setPreemptive(false)
                        .build();

        LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY);
        // Play one effect and schedule a continuation effect.
        session.requestLights(new Builder().setEffect(effect).build());
        session.requestLights(new Builder().setEffect(effect2).build());

        // Small delay to guarantee the effect settles.
        sleep(100);

        // Validate the internal state of the light is the sequence of the first effect.
        ColorSequence sequence = mManager.getLightSequence(light);
        LightState state = mManager.getLightState(light);
        assertThat(sequence.getColors().length).isEqualTo(1);
        assertThat(sequence.getColors()[0]).isEqualTo(BLUE);
        assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
        assertThat(sequence.getDelaysMillis()[0]).isEqualTo(500L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // Sleep beyond the transition from effect -> effect2.
        sleep(500);

        // Validate that the state is transparent.
        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence.getColors().length).isEqualTo(1);
        assertThat(sequence.getColors()[0]).isEqualTo(RED);
        assertThat(sequence.getDelaysMillis().length).isEqualTo(1);
        assertThat(sequence.getDelaysMillis()[0]).isEqualTo(300L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        session.close();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect_finiteIterations() {
        assumeTrue(mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        // Create an effect wit two iterations.
        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder()
                                        .addControlPoint(200, BLUE)
                                        .addControlPoint(200, GREEN)
                                        .build())
                        .setPreemptive(true)
                        .setIterations(2)
                        .build();
        // Create a simple effect for a light that supports effects.
        MultiLightEffect effect2 =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(300, RED).build())
                        .setPreemptive(false)
                        .build();

        LightsManager.LightsSession session = mManager.openSession(HIGH_PRIORITY);
        // Play one effect and schedule a continuation effect.
        session.requestLights(new Builder().setEffect(effect).build());
        session.requestLights(new Builder().setEffect(effect2).build());

        // Wait for the first iteration to be complete.
        sleep(500);

        // Validate the internal state of the light is the sequence of the first effect.
        ColorSequence sequence = mManager.getLightSequence(light);
        LightState state = mManager.getLightState(light);
        assertThat(sequence.getColors()).asList().containsExactly(BLUE, GREEN).inOrder();
        assertThat(sequence.getDelaysMillis()).asList().containsExactly(200L, 200L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // Sleep beyond the transition from effect -> effect2.
        sleep(500);

        // Validate that the state is transparent.
        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence.getColors()).asList().containsExactly(RED);
        assertThat(sequence.getDelaysMillis()).asList().containsExactly(300L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        session.close();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIGHT_ANIMATIONS)
    @Test
    public void testEffect_hideEffectFromLowerPrioritySession() {
        assumeTrue(mEffectLights.size() >= 1);

        Light light = mEffectLights.get(0);

        MultiLightEffect effect =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(200, BLUE).build())
                        .build();
        MultiLightEffect effect2 =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(300, RED).build())
                        .build();
        MultiLightEffect effect3 =
                new MultiLightEffect.Builder()
                        .addLightSequence(
                                light,
                                new ColorSequence.Builder().addControlPoint(300, YELLOW).build())
                        .setPreemptive(false)
                        .build();

        LightsManager.LightsSession highPrioritySession = mManager.openSession(HIGH_PRIORITY);
        LightsManager.LightsSession lowPrioritySession = mManager.openSession(100);

        // Set up the scenario:
        //  - highPrioritySession: 1 effect.
        //  - lowPrioritySession: 2 effects (playing + continuation).
        highPrioritySession.requestLights(new Builder().setEffect(effect).build());
        lowPrioritySession.requestLights(new Builder().setEffect(effect2).build());
        lowPrioritySession.requestLights(new Builder().setEffect(effect3).build());

        // At t=100 the high priority session should be mid playback.
        sleep(100);

        ColorSequence sequence = mManager.getLightSequence(light);
        LightState state = mManager.getLightState(light);
        assertThat(sequence.getColors()).asList().containsExactly(BLUE).inOrder();
        assertThat(sequence.getDelaysMillis()).asList().containsExactly(200L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // The high priority session stops using the light. Low priority session should take over
        // and should be still in the first effect.
        highPrioritySession.requestLights(new LightsRequest.Builder().clearLight(light).build());

        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence.getColors()).asList().containsExactly(RED);
        assertThat(sequence.getDelaysMillis()).asList().containsExactly(300L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        // At t=200 the low priority session is still on effect2.
        sleep(100);

        // The high priority session requests a solid color, shadowing the low priority session
        // again.
        highPrioritySession.requestLights(
                new LightsRequest.Builder()
                        .addLight(light, new LightState.Builder().setColor(MAGENTA).build())
                        .build());

        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence).isNull();
        assertThat(state.getColor()).isEqualTo(MAGENTA);

        // At t=400 the high priority session is still in control.
        sleep(200);

        // High priority session goes away.
        highPrioritySession.close();
        state = mManager.getLightState(light);
        sequence = mManager.getLightSequence(light);
        assertThat(sequence.getColors()).asList().containsExactly(YELLOW);
        assertThat(sequence.getDelaysMillis()).asList().containsExactly(300L);
        assertThat(state.getColor()).isEqualTo(TRANSPARENT);

        lowPrioritySession.close();
    }
}
