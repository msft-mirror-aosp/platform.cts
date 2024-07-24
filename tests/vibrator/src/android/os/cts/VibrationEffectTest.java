/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.os.cts;

import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;
import static android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.VibrationEffect;
import android.os.VibrationEffect.Composition.UnreachableAfterRepeatingIndefinitelyException;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.RampSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VibrationEffectTest {
    private static final long TEST_TIMING = 100;
    private static final int TEST_AMPLITUDE = 100;
    private static final float TEST_FLOAT_AMPLITUDE = TEST_AMPLITUDE / 255f;
    private static final float TEST_TOLERANCE = 1e-5f;

    private static final long[] TEST_TIMINGS = new long[]{100, 100, 200};
    private static final int[] TEST_AMPLITUDES =
            new int[]{255, 0, 102};
    private static final float[] TEST_FLOAT_AMPLITUDES =
            new float[]{1f, 0f, 0.4f};

    private static final VibrationEffect TEST_ONE_SHOT =
            VibrationEffect.createOneShot(TEST_TIMING, TEST_AMPLITUDE);
    private static final VibrationEffect TEST_WAVEFORM =
            VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, -1);
    private static final VibrationEffect TEST_WAVEFORM_NO_AMPLITUDES =
            VibrationEffect.createWaveform(TEST_TIMINGS, -1);
    private static final VibrationEffect TEST_WAVEFORM_BUILT =
            VibrationEffect.startWaveform(targetAmplitude(0.5f))
                    .addSustain(Duration.ofMillis(10))
                    .addTransition(Duration.ZERO, targetAmplitude(0.8f), targetFrequency(100f))
                    .addSustain(Duration.ofMillis(10))
                    .addTransition(Duration.ofMillis(100), targetAmplitude(1))
                    .addTransition(Duration.ofMillis(200),
                            targetAmplitude(0.2f), targetFrequency(200f))
                    .build();
    private static final VibrationEffect TEST_PREBAKED =
            VibrationEffect.get(VibrationEffect.EFFECT_CLICK, true);
    private static final VibrationEffect TEST_COMPOSED =
            VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, /* delay= */ 10)
                    .addEffect(TEST_ONE_SHOT)
                    .addOffDuration(Duration.ofMillis(10))
                    .addEffect(TEST_WAVEFORM)
                    .addOffDuration(Duration.ofSeconds(1))
                    .addEffect(TEST_WAVEFORM_BUILT)
                    .compose();

    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    getRequiredPrivilegedPermissions());


    @Test
    public void testCreateOneShot() {
        VibrationEffect e = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
        assertThat(e.getDuration()).isEqualTo(100);
        assertAmplitude(VibrationEffect.DEFAULT_AMPLITUDE, e, 0);

        e = VibrationEffect.createOneShot(1, 1);
        assertThat(e.getDuration()).isEqualTo(1);
        assertAmplitude(1 / 255f, e, 0);

        e = VibrationEffect.createOneShot(1000, 255);
        assertThat(e.getDuration()).isEqualTo(1000);
        assertAmplitude(1f, e, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateOneShotFailsBadTiming() {
        VibrationEffect.createOneShot(0, TEST_AMPLITUDE);
    }

    @Test
    public void testCreateOneShotFailsBadAmplitude() {
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createOneShot(TEST_TIMING, -2));

        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createOneShot(TEST_TIMING, 0));

        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createOneShot(TEST_TIMING, 256));
    }

    @Test
    public void testOneShotEquals() {
        VibrationEffect otherEffect = VibrationEffect.createOneShot(TEST_TIMING, TEST_AMPLITUDE);
        assertThat(otherEffect).isEqualTo(TEST_ONE_SHOT);
        assertThat(otherEffect.hashCode()).isEqualTo(TEST_ONE_SHOT.hashCode());
    }

    @Test
    public void testOneShotNotEqualsAmplitude() {
        VibrationEffect otherEffect =
                VibrationEffect.createOneShot(TEST_TIMING, TEST_AMPLITUDE - 1);
        assertThat(otherEffect).isNotEqualTo(TEST_ONE_SHOT);
    }

    @Test
    public void testOneShotNotEqualsTiming() {
        VibrationEffect otherEffect =
                VibrationEffect.createOneShot(TEST_TIMING - 1, TEST_AMPLITUDE);
        assertThat(otherEffect).isNotEqualTo(TEST_ONE_SHOT);
    }

    @Test
    public void testOneShotEqualsWithDefaultAmplitude() {
        VibrationEffect effect =
                VibrationEffect.createOneShot(TEST_TIMING, VibrationEffect.DEFAULT_AMPLITUDE);
        VibrationEffect otherEffect =
                VibrationEffect.createOneShot(TEST_TIMING, VibrationEffect.DEFAULT_AMPLITUDE);
        assertThat(otherEffect).isEqualTo(effect);
        assertThat(otherEffect.hashCode()).isEqualTo(effect.hashCode());
    }

    @Test
    public void testCreatePrebaked() {
        int[] ids = { VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_DOUBLE_CLICK,
                VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_THUD,
                VibrationEffect.EFFECT_POP, VibrationEffect.EFFECT_HEAVY_CLICK,
                VibrationEffect.EFFECT_TEXTURE_TICK };
        boolean[] fallbacks = { false, true };
        for (int id : ids) {
            for (boolean fallback : fallbacks) {
                VibrationEffect effect = VibrationEffect.get(id, fallback);
                assertThat(effect.getDuration()).isEqualTo(-1);
                assertPrebakedEffectId(id, effect, 0);
                assertShouldFallback(fallback, effect, 0);
            }
        }
    }

    @Test
    public void testPrebakedEquals() {
        VibrationEffect otherEffect = VibrationEffect.get(VibrationEffect.EFFECT_CLICK, true);
        assertThat(otherEffect).isEqualTo(TEST_PREBAKED);
        assertThat(otherEffect.hashCode()).isEqualTo(TEST_PREBAKED.hashCode());
    }

    @Test
    public void testCreatePredefined() {
        VibrationEffect expectedEffect = VibrationEffect.get(
                VibrationEffect.EFFECT_DOUBLE_CLICK, true);
        VibrationEffect predefinedEffect = VibrationEffect.createPredefined(
                VibrationEffect.EFFECT_DOUBLE_CLICK);
        assertThat(predefinedEffect).isEqualTo(expectedEffect);
        assertThat(predefinedEffect.hashCode()).isEqualTo(expectedEffect.hashCode());
    }

    @Test
    public void testCreateWaveform() {
        VibrationEffect effect = VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, -1);
        assertArrayEquals(TEST_TIMINGS, getTimings(effect));
        assertThat(getRepeatIndex(effect)).isEqualTo(-1);
        assertThat(effect.getDuration()).isEqualTo(400);
        for (int i = 0; i < TEST_TIMINGS.length; i++) {
            assertAmplitude(TEST_FLOAT_AMPLITUDES[i], effect, i);
        }

        effect = VibrationEffect.createWaveform(new long[] { 10 },
                new int[] { VibrationEffect.DEFAULT_AMPLITUDE }, -1);
        assertAmplitude(VibrationEffect.DEFAULT_AMPLITUDE, effect, /* index= */ 0);

        effect = VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, 0);
        assertThat(getRepeatIndex(effect)).isEqualTo(0);

        effect = VibrationEffect.createWaveform(
                TEST_TIMINGS, TEST_AMPLITUDES, TEST_AMPLITUDES.length - 1);
        assertThat(getRepeatIndex(effect)).isEqualTo(TEST_AMPLITUDES.length - 1);
    }

    @Test
    public void testCreateWaveformFailsDifferentArraySize() {
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(
                        Arrays.copyOfRange(TEST_TIMINGS, 0, TEST_TIMINGS.length - 1),
                        TEST_AMPLITUDES, -1));

        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS,
                        Arrays.copyOfRange(TEST_AMPLITUDES, 0, TEST_AMPLITUDES.length - 1), -1));
    }

    @Test
    public void testCreateWaveformFailsRepeatIndexOutOfBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, -2));

        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES,
                        TEST_AMPLITUDES.length));
    }

    @Test
    public void testCreateWaveformFailsBadTimingValues() {
        final long[] badTimings = Arrays.copyOf(TEST_TIMINGS, TEST_TIMINGS.length);
        badTimings[1] = -1;
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(badTimings,TEST_AMPLITUDES, -1));

        final long[] emptyTimings = new long[TEST_TIMINGS.length];
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(emptyTimings, TEST_AMPLITUDES, -1));
    }

    @Test
    public void testCreateWaveformFailsBadAmplitudeValues() {
        final int[] negativeAmplitudes = new int[TEST_TIMINGS.length];
        negativeAmplitudes[1] = -2;
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, negativeAmplitudes, -1));

        final int[] highAmplitudes = new int[TEST_TIMINGS.length];
        highAmplitudes[1] = 256;
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, highAmplitudes, -1));
    }

    @Test
    public void testCreateWaveformWithNoAmplitudes() {
        VibrationEffect effect = VibrationEffect.createWaveform(TEST_TIMINGS, -1);
        assertArrayEquals(TEST_TIMINGS, getTimings(effect));
        assertThat(getRepeatIndex(effect)).isEqualTo(-1);
        for (int i = 0; i < TEST_TIMINGS.length; i++) {
            assertAmplitude(i % 2 == 0 ? 0 : VibrationEffect.DEFAULT_AMPLITUDE, effect, i);
        }

        effect = VibrationEffect.createWaveform(TEST_TIMINGS, 0);
        assertThat(getRepeatIndex(effect)).isEqualTo(0);

        effect = VibrationEffect.createWaveform(TEST_TIMINGS, TEST_TIMINGS.length - 1);
        assertThat(getRepeatIndex(effect)).isEqualTo(TEST_TIMINGS.length - 1);
    }

    @Test
    public void testCreateWaveformWithNoAmplitudesFailsRepeatIndexOutOfBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, -2));

        assertThrows(IllegalArgumentException.class,
                () -> VibrationEffect.createWaveform(TEST_TIMINGS, TEST_TIMINGS.length));
    }

    @Test
    public void testWaveformEquals() {
        VibrationEffect effect = VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, -1);
        VibrationEffect otherEffect =
                VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, -1);
        assertThat(otherEffect).isEqualTo(effect);
        assertThat(otherEffect.hashCode()).isEqualTo(effect.hashCode());
    }

    @Test
    public void testWaveformNotEqualsDifferentRepeatIndex() {
        VibrationEffect otherEffect =
                VibrationEffect.createWaveform(TEST_TIMINGS, TEST_AMPLITUDES, 0);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM);
    }

    @Test
    public void testWaveformNotEqualsDifferentTimingArrayValue() {
        long[] newTimings = Arrays.copyOf(TEST_TIMINGS, TEST_TIMINGS.length);
        newTimings[0] = 200;
        VibrationEffect otherEffect =
                VibrationEffect.createWaveform(newTimings, TEST_AMPLITUDES, -1);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM);
    }

    @Test
    public void testWaveformNotEqualsDifferentAmplitudeArrayValue() {
        int[] newAmplitudes = Arrays.copyOf(TEST_AMPLITUDES, TEST_AMPLITUDES.length);
        newAmplitudes[0] = 1;
        VibrationEffect otherEffect =
                VibrationEffect.createWaveform(TEST_TIMINGS, newAmplitudes, -1);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM);
    }

    @Test
    public void testWaveformNotEqualsDifferentArrayLength() {
        long[] newTimings = Arrays.copyOfRange(TEST_TIMINGS, 0, TEST_TIMINGS.length - 1);
        int[] newAmplitudes = Arrays.copyOfRange(TEST_AMPLITUDES, 0, TEST_AMPLITUDES.length -1);
        VibrationEffect otherEffect =
                VibrationEffect.createWaveform(newTimings, newAmplitudes, -1);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM);
    }

    @Test
    public void testWaveformWithNoAmplitudesEquals() {
        VibrationEffect otherEffect = VibrationEffect.createWaveform(TEST_TIMINGS, -1);
        assertThat(otherEffect).isEqualTo(TEST_WAVEFORM_NO_AMPLITUDES);
        assertThat(otherEffect.hashCode()).isEqualTo(TEST_WAVEFORM_NO_AMPLITUDES.hashCode());
    }

    @Test
    public void testWaveformWithNoAmplitudesNotEqualsDifferentRepeatIndex() {
        VibrationEffect otherEffect = VibrationEffect.createWaveform(TEST_TIMINGS, 0);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM_NO_AMPLITUDES);
    }

    @Test
    public void testWaveformWithNoAmplitudesNotEqualsDifferentArrayLength() {
        long[] newTimings = Arrays.copyOfRange(TEST_TIMINGS, 0, TEST_TIMINGS.length - 1);
        VibrationEffect otherEffect = VibrationEffect.createWaveform(newTimings, -1);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM_NO_AMPLITUDES);
    }

    @Test
    public void testWaveformWithNoAmplitudesNotEqualsDifferentTimingValue() {
        long[] newTimings = Arrays.copyOf(TEST_TIMINGS, TEST_TIMINGS.length);
        newTimings[0] = 1;
        VibrationEffect otherEffect = VibrationEffect.createWaveform(newTimings, -1);
        assertThat(otherEffect).isNotEqualTo(TEST_WAVEFORM_NO_AMPLITUDES);
    }

    @Test
    public void testParcelingOneShot() {
        Parcel p = Parcel.obtain();
        TEST_ONE_SHOT.writeToParcel(p, 0);
        p.setDataPosition(0);
        VibrationEffect parceledEffect = VibrationEffect.CREATOR.createFromParcel(p);
        assertThat(parceledEffect).isEqualTo(TEST_ONE_SHOT);
    }

    @Test
    public void testParcelingWaveForm() {
        Parcel p = Parcel.obtain();
        TEST_WAVEFORM.writeToParcel(p, 0);
        p.setDataPosition(0);
        VibrationEffect parceledEffect = VibrationEffect.CREATOR.createFromParcel(p);
        assertThat(parceledEffect).isEqualTo(TEST_WAVEFORM);
    }

    @Test
    public void testParcelingPrebaked() {
        Parcel p = Parcel.obtain();
        TEST_PREBAKED.writeToParcel(p, 0);
        p.setDataPosition(0);
        VibrationEffect parceledEffect = VibrationEffect.CREATOR.createFromParcel(p);
        assertThat(parceledEffect).isEqualTo(TEST_PREBAKED);
    }

    @Test
    public void testParcelingComposed() {
        Parcel p = Parcel.obtain();
        TEST_COMPOSED.writeToParcel(p, 0);
        p.setDataPosition(0);
        VibrationEffect parceledEffect = VibrationEffect.CREATOR.createFromParcel(p);
        assertThat(parceledEffect).isEqualTo(TEST_COMPOSED);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testParcelingVendorEffect() {
        VibrationEffect vendorEffect = VibrationEffect.createVendorEffect(createTestVendorData());
        Parcel p = Parcel.obtain();
        vendorEffect.writeToParcel(p, 0);
        p.setDataPosition(0);
        VibrationEffect parceledEffect = VibrationEffect.CREATOR.createFromParcel(p);
        assertThat(parceledEffect).isEqualTo(vendorEffect);
    }

    @Test
    public void testDescribeContents() {
        TEST_ONE_SHOT.describeContents();
        TEST_WAVEFORM.describeContents();
        TEST_PREBAKED.describeContents();
        TEST_COMPOSED.describeContents();
        if (Flags.vendorVibrationEffects()) {
            VibrationEffect.createVendorEffect(createTestVendorData()).describeContents();
        }
    }

    @Test
    public void testStartComposition() {
        VibrationEffect.Composition first = VibrationEffect.startComposition();
        VibrationEffect.Composition other = VibrationEffect.startComposition();
        assertThat(first).isNotEqualTo(other);
    }

    @Test
    public void testComposed() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .addEffect(TEST_ONE_SHOT)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 10)
                .addEffect(VibrationEffect.get(VibrationEffect.EFFECT_THUD))
                .addEffect(TEST_WAVEFORM)
                .compose();

        assertThat(effect.getDuration()).isEqualTo(-1);
        assertArrayEquals(new long[]{
                -1 /* tick */, TEST_TIMING /* oneshot */, -1 /* click */, -1 /* thud */,
                100, 100, 200 /* waveform */
        }, getTimings(effect));
        assertPrimitiveId(VibrationEffect.Composition.PRIMITIVE_TICK, effect, 0);
        assertAmplitude(TEST_FLOAT_AMPLITUDE, effect, 1);
        assertPrimitiveId(VibrationEffect.Composition.PRIMITIVE_CLICK, effect, 2);
        assertPrebakedEffectId(VibrationEffect.EFFECT_THUD, effect, 3);
        assertAmplitude(TEST_FLOAT_AMPLITUDES[0], effect, 4);
        assertAmplitude(TEST_FLOAT_AMPLITUDES[1], effect, 5);
        assertAmplitude(TEST_FLOAT_AMPLITUDES[2], effect, 6);
    }

    @Test
    public void testComposedEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .addOffDuration(Duration.ofMillis(10))
                .addEffect(TEST_ONE_SHOT)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 10)
                .addEffect(TEST_WAVEFORM)
                .compose();

        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0)
                .addOffDuration(Duration.ofMillis(10))
                .addEffect(TEST_ONE_SHOT)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 10)
                .addEffect(TEST_WAVEFORM)
                .compose();
        assertThat(effect).isEqualTo(otherEffect);
        assertThat(effect.hashCode()).isEqualTo(otherEffect.hashCode());
    }

    @Test
    public void testComposedRepeatingAreEqualsAreEquals() {
        VibrationEffect repeatingWaveform = VibrationEffect.createWaveform(
                new long[] { 10, 20, 30}, new int[] { 50, 100, 150 }, /* repeatIndex= */ 0);
        VibrationEffect nonRepeatingWaveform = VibrationEffect.createWaveform(
                new long[] { 10, 20, 30}, new int[] { 50, 100, 150 }, /* repeatIndex= */ -1);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addEffect(repeatingWaveform)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .repeatEffectIndefinitely(nonRepeatingWaveform)
                .compose();
        assertThat(effect).isEqualTo(otherEffect);
        assertThat(effect.hashCode()).isEqualTo(otherEffect.hashCode());
    }

    @Test
    public void testComposedDifferentPrimitivesNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentScaleNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentDelayNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 10)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 100)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentOrderNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentNumberOfPrimitivesNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentWaveformsNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addEffect(TEST_ONE_SHOT)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addEffect(TEST_WAVEFORM)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDifferentWaveformDelayNotEquals() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addOffDuration(Duration.ofMillis(10))
                .addEffect(TEST_ONE_SHOT)
                .compose();
        VibrationEffect otherEffect = VibrationEffect.startComposition()
                .addOffDuration(Duration.ofSeconds(10))
                .addEffect(TEST_ONE_SHOT)
                .compose();
        assertThat(effect).isNotEqualTo(otherEffect);
    }

    @Test
    public void testComposedDuration() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 1000)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .addEffect(TEST_ONE_SHOT)
                .compose();
        assertThat(effect.getDuration()).isEqualTo(-1);

        effect = VibrationEffect.startComposition()
                .addEffect(TEST_ONE_SHOT)
                .compose();
        assertThat(effect.getDuration()).isEqualTo(TEST_ONE_SHOT.getDuration());

        effect = VibrationEffect.startComposition().addOffDuration(Duration.ofSeconds(2)).compose();
        assertThat(effect.getDuration()).isEqualTo(2_000);

        effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                .addEffect(VibrationEffect.createWaveform(new long[]{10, 10}, /* repeat= */ 0))
                .compose();
        assertThat(effect.getDuration()).isEqualTo(Long.MAX_VALUE);

        effect = VibrationEffect.startComposition()
                .repeatEffectIndefinitely(TEST_ONE_SHOT)
                .compose();
        assertThat(effect.getDuration()).isEqualTo(Long.MAX_VALUE);
    }

    @Test(expected = IllegalStateException.class)
    public void testComposeEmptyCompositionIsInvalid() {
        VibrationEffect.startComposition().compose();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComposeRepeatEffectWithRepeatingEffectIsInvalid() {
        VibrationEffect.startComposition()
                .repeatEffectIndefinitely(
                        VibrationEffect.createWaveform(new long[] { 10 }, new int[] { 255 }, 0))
                .compose();
    }

    @Test(expected = UnreachableAfterRepeatingIndefinitelyException.class)
    public void testComposeAddOffDurationAfterRepeatingEffectIsInvalid() {
        VibrationEffect.startComposition()
                .repeatEffectIndefinitely(TEST_ONE_SHOT)
                .addOffDuration(Duration.ofMillis(20));
    }

    @Test(expected = UnreachableAfterRepeatingIndefinitelyException.class)
    public void testComposeAddRepeatingEffectAfterRepeatingEffectIsInvalid() {
        VibrationEffect.startComposition()
                .repeatEffectIndefinitely(TEST_ONE_SHOT)
                .repeatEffectIndefinitely(TEST_ONE_SHOT);
    }

    @Test(expected = UnreachableAfterRepeatingIndefinitelyException.class)
    public void testComposeAddEffectAfterRepeatingEffectIsInvalid() {
        VibrationEffect.startComposition()
                .addEffect(
                        VibrationEffect.createWaveform(new long[] { 10 }, new int[] { 255 }, 0))
                .addEffect(TEST_PREBAKED);
    }

    @Test(expected = UnreachableAfterRepeatingIndefinitelyException.class)
    public void testComposeAddPrimitiveAfterRepeatingEffectIsInvalid() {
        VibrationEffect.startComposition()
                .repeatEffectIndefinitely(TEST_ONE_SHOT)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK);
    }

    @Test
    public void testStartWaveform() {
        VibrationEffect.WaveformBuilder first = VibrationEffect.startWaveform();
        VibrationEffect.WaveformBuilder other = VibrationEffect.startWaveform();
        assertThat(first).isNotEqualTo(other);

        VibrationEffect effect = VibrationEffect.startWaveform(targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ZERO, targetAmplitude(0.8f), targetFrequency(100f))
                .addSustain(Duration.ofMillis(20))
                .addTransition(Duration.ofMillis(100), targetAmplitude(1))
                .addTransition(Duration.ofMillis(200), targetAmplitude(0.2f), targetFrequency(200f))
                .build();

        assertArrayEquals(new long[]{10, 20, 100, 200}, getTimings(effect));
        assertStepSegment(effect, 0);
        assertAmplitude(0.5f, effect, 0);
        assertFrequency(0f, effect, 0);

        assertStepSegment(effect, 1);
        assertAmplitude(0.8f, effect, 1);
        assertFrequency(100f, effect, 1);

        assertRampSegment(effect, 2);
        assertAmplitude(1f, effect, 2);
        assertFrequency(100f, effect, 2);

        assertRampSegment(effect, 3);
        assertAmplitude(0.2f, effect, 3);
        assertFrequency(200f, effect, 3);
    }

    @Test
    public void testStartWaveformEquals() {
        VibrationEffect other = VibrationEffect.startWaveform()
                .addTransition(Duration.ZERO, targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ZERO, targetAmplitude(0.8f), targetFrequency(100f))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ofMillis(100), targetAmplitude(1))
                .addTransition(Duration.ofMillis(200),
                        targetAmplitude(0.2f), targetFrequency(200f))
                .build();

        assertThat(other).isEqualTo(TEST_WAVEFORM_BUILT);
        assertThat(other.hashCode()).isEqualTo(TEST_WAVEFORM_BUILT.hashCode());

        VibrationEffect.WaveformBuilder builder =
                VibrationEffect.startWaveform(targetAmplitude(TEST_FLOAT_AMPLITUDE))
                .addSustain(Duration.ofMillis(TEST_TIMING));
        assertThat(builder.build()).isEqualTo(TEST_ONE_SHOT);
        assertThat(builder.build().hashCode()).isEqualTo(TEST_ONE_SHOT.hashCode());

        builder = VibrationEffect.startWaveform();
        for (int i = 0; i < TEST_TIMINGS.length; i++) {
            builder.addTransition(Duration.ZERO, targetAmplitude(TEST_FLOAT_AMPLITUDES[i]));
            builder.addSustain(Duration.ofMillis(TEST_TIMINGS[i]));
        }
        assertThat(builder.build()).isEqualTo(TEST_WAVEFORM);
        assertThat(builder.build().hashCode()).isEqualTo(TEST_WAVEFORM.hashCode());
    }

    @Test
    public void testStartWaveformEqualsSustainCreatedViaTransitions() {
        VibrationEffect effect = VibrationEffect.startWaveform()
                .addTransition(Duration.ZERO, targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .build();
        VibrationEffect other = VibrationEffect.startWaveform(targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .build();
        assertThat(effect).isEqualTo(other);

        effect = VibrationEffect.startWaveform(targetAmplitude(1f), targetFrequency(100f))
                .addTransition(Duration.ofMillis(10), targetAmplitude(1f), targetFrequency(100f))
                .build();
        other = VibrationEffect.startWaveform(targetAmplitude(1f), targetFrequency(100f))
                .addSustain(Duration.ofMillis(10))
                .build();
        assertThat(effect).isEqualTo(other);
    }

    @Test
    public void testStartWaveformNotEqualsDifferentNumberOfSteps() {
        VibrationEffect other = VibrationEffect.startWaveform(targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ofMillis(100), targetAmplitude(1))
                .build();
        assertThat(other).isNotEqualTo(TEST_WAVEFORM_BUILT);
    }

    @Test
    public void testStartWaveformNotEqualsDifferentTypesOfStep() {
        VibrationEffect first = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.5f))
                .build();
        VibrationEffect second = VibrationEffect.startWaveform(targetAmplitude(0.5f))
                .addSustain(Duration.ofMillis(10))
                .build();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    public void testStartWaveformNotEqualsDifferentAmplitudes() {
        VibrationEffect first = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.5f))
                .build();
        VibrationEffect second = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.8f))
                .build();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    public void testStartWaveformNotEqualsDifferentFrequency() {
        VibrationEffect first = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.5f), targetFrequency(100f))
                .build();
        VibrationEffect second = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.5f), targetFrequency(50f))
                .build();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    public void testStartWaveformNotEqualsDifferentDuration() {
        VibrationEffect first = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(10), targetAmplitude(0.5f), targetFrequency(50f))
                .build();
        VibrationEffect second = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(100), targetAmplitude(0.5f), targetFrequency(50f))
                .build();
        assertThat(first).isNotEqualTo(second);
    }

    @Test(expected = IllegalStateException.class)
    public void testStartWaveformEmptyBuilderIsInvalid() {
        VibrationEffect.startWaveform().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartWaveformAddZeroDurationSustainIsInvalid() {
        VibrationEffect.startWaveform().addSustain(Duration.ofNanos(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartWaveformTransitionWithSameParameterTwiceIsInvalid() {
        VibrationEffect.startWaveform().addTransition(Duration.ofSeconds(1),
                targetAmplitude(0.8f), targetAmplitude(1f));
    }

    @Test
    public void testStartWaveformZeroAmplitudeSustainIsSameAsOffPeriodOnlyComposition() {
        assertThat(VibrationEffect.startWaveform().addSustain(Duration.ofMillis(1_000)).build())
                .isEqualTo(
                        VibrationEffect.startComposition()
                                .addOffDuration(Duration.ofSeconds(1))
                                .compose());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testCreateVendorEffect() {
        PersistableBundle vendorData = createTestVendorData();
        VibrationEffect.VendorEffect effect =
                (VibrationEffect.VendorEffect) VibrationEffect.createVendorEffect(vendorData);
        assertThat(effect.getDuration()).isEqualTo(-1);
        assertThat(effect.getVendorData().size()).isEqualTo(vendorData.size());
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testCreateVendorEffectEmptyBundleFails() {
        VibrationEffect.createVendorEffect(new PersistableBundle());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testVendorEffectEquals() {
        VibrationEffect effect = VibrationEffect.createVendorEffect(createTestVendorData());
        VibrationEffect otherEffect = VibrationEffect.createVendorEffect(createTestVendorData());
        assertThat(otherEffect).isEqualTo(effect);
        assertThat(otherEffect.hashCode()).isEqualTo(effect.hashCode());
    }

    @SuppressWarnings("ReturnValueIgnored")
    @Test
    public void testToString() {
        TEST_ONE_SHOT.toString();
        TEST_WAVEFORM.toString();
        TEST_WAVEFORM_BUILT.toString();
        TEST_PREBAKED.toString();
        TEST_COMPOSED.toString();
        if (Flags.vendorVibrationEffects()) {
            VibrationEffect.createVendorEffect(createTestVendorData()).toString();
        }
    }

    private long[] getTimings(VibrationEffect effect) {
        if (effect instanceof VibrationEffect.Composed composed) {
            return composed.getSegments().stream()
                    .mapToLong(VibrationEffectSegment::getDuration)
                    .toArray();
        }
        return null;
    }

    private int getRepeatIndex(VibrationEffect effect) {
        if (effect instanceof VibrationEffect.Composed composed) {
            return composed.getRepeatIndex();
        }
        return -1;
    }

    private void assertStepSegment(VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        assertThat(composed.getSegments().get(index)).isInstanceOf(StepSegment.class);
    }

    private void assertRampSegment(VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        assertThat(composed.getSegments().get(index)).isInstanceOf(RampSegment.class);
    }

    private void assertAmplitude(float expected, VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        VibrationEffectSegment segment = composed.getSegments().get(index);
        if (segment instanceof StepSegment) {
            assertThat(((StepSegment) composed.getSegments().get(index)).getAmplitude())
                    .isWithin(TEST_TOLERANCE)
                    .of(expected);
        } else if (segment instanceof RampSegment) {
            assertThat(((RampSegment) composed.getSegments().get(index)).getEndAmplitude())
                    .isWithin(TEST_TOLERANCE)
                    .of(expected);
        } else {
            fail("Expected a step or ramp segment at index " + index + " of " + effect);
        }
    }

    private void assertFrequency(float expected, VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        VibrationEffectSegment segment = composed.getSegments().get(index);
        if (segment instanceof StepSegment) {
            assertThat(((StepSegment) composed.getSegments().get(index)).getFrequencyHz())
                    .isWithin(TEST_TOLERANCE)
                    .of(expected);
        } else if (segment instanceof RampSegment) {
            assertThat(((RampSegment) composed.getSegments().get(index)).getEndFrequencyHz())
                    .isWithin(TEST_TOLERANCE)
                    .of(expected);
        } else {
            fail("Expected a step or ramp segment at index " + index + " of " + effect);
        }
    }

    private void assertPrebakedEffectId(int expected, VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        assertThat(composed.getSegments().get(index)).isInstanceOf(PrebakedSegment.class);
        assertThat(((PrebakedSegment) composed.getSegments().get(index)).getEffectId())
                .isEqualTo(expected);
    }

    private void assertShouldFallback(boolean expected, VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        assertThat(composed.getSegments().get(index)).isInstanceOf(PrebakedSegment.class);
        assertThat(((PrebakedSegment) composed.getSegments().get(index)).shouldFallback())
                .isEqualTo(expected);
    }

    private void assertPrimitiveId(int expected, VibrationEffect effect, int index) {
        assertThat(effect).isInstanceOf(VibrationEffect.Composed.class);
        VibrationEffect.Composed composed = (VibrationEffect.Composed) effect;
        assertThat(index).isLessThan(composed.getSegments().size());
        assertThat(composed.getSegments().get(index)).isInstanceOf(PrimitiveSegment.class);
        assertThat(((PrimitiveSegment) composed.getSegments().get(index)).getPrimitiveId())
                .isEqualTo(expected);
    }

    private static PersistableBundle createTestVendorData() {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        vendorData.putDouble("scale", 0.5);
        vendorData.putBoolean("loop", false);
        vendorData.putLongArray("amplitudes", new long[] { 0, 255, 128 });
        vendorData.putString("label", "vibration");
        return vendorData;
    }

    private static String[] getRequiredPrivilegedPermissions() {
        if (Flags.vendorVibrationEffects()) {
            return new String[]{
                    android.Manifest.permission.VIBRATE_VENDOR_EFFECTS,
            };
        }
        return null;
    }
}
