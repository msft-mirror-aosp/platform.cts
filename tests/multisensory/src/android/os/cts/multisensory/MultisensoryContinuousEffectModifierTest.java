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

package android.os.cts.multisensory;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.multisensory.Flags;
import android.os.multisensory.MultisensoryContinuousEffectModifier;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** CTS tests for {@link MultisensoryContinuousEffectModifier}. */
@SmallTest
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_MULTISENSORY_FEEDBACK)
@RunWith(AndroidJUnit4.class)
public class MultisensoryContinuousEffectModifierTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testConstructor_withValidValues_succeeds() {
        MultisensoryContinuousEffectModifier modifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_INTENSITY);
        assertThat(modifier.getTargetParameter())
                .isEqualTo(MultisensoryContinuousEffectModifier.TARGET_PARAMETER_INTENSITY);

        modifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_SHARPNESS);
        assertThat(modifier.getTargetParameter())
                .isEqualTo(MultisensoryContinuousEffectModifier.TARGET_PARAMETER_SHARPNESS);
    }

    @Test
    public void testConstructor_withInvalidTargetParameter_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new MultisensoryContinuousEffectModifier(-1);
                });
    }

    @Test
    public void testSetModifierValue_withInvalidValue_throwsException() {
        MultisensoryContinuousEffectModifier intensityModifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_INTENSITY);
        assertThrows(
                IllegalArgumentException.class, () -> intensityModifier.setModifierValue(1.1f));
        assertThrows(
                IllegalArgumentException.class, () -> intensityModifier.setModifierValue(-1.1f));

        MultisensoryContinuousEffectModifier sharpnessModifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_SHARPNESS);
        assertThrows(
                IllegalArgumentException.class, () -> sharpnessModifier.setModifierValue(1.1f));
        assertThrows(
                IllegalArgumentException.class, () -> sharpnessModifier.setModifierValue(-1.1f));
    }

    @Test
    public void testSetRampDuration_withInvalidValue_throwsException() {
        MultisensoryContinuousEffectModifier modifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_INTENSITY);
        assertThrows(IllegalArgumentException.class, () -> modifier.setRampDuration(-1));
        assertThrows(IllegalArgumentException.class, () -> modifier.setRampDuration(0));
    }

    @Test
    public void testSetModifierValue_withValidValue_succeeds() {
        float expectedSharpness = 0.8f;
        MultisensoryContinuousEffectModifier modifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_SHARPNESS);
        modifier.setModifierValue(expectedSharpness);
        assertThat(modifier.getModifierValue()).isEqualTo(expectedSharpness);
    }

    @Test
    public void testSetRampDuration_withValidValue_succeeds() {
        long expectedRampDuration = 200;
        MultisensoryContinuousEffectModifier modifier =
                new MultisensoryContinuousEffectModifier(
                        MultisensoryContinuousEffectModifier.TARGET_PARAMETER_INTENSITY);
        modifier.setRampDuration(expectedRampDuration);
        assertThat(modifier.getRampDurationMillis()).isEqualTo(expectedRampDuration);
    }
}
