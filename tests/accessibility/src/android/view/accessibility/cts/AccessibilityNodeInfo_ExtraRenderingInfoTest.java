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

package android.view.accessibility.cts;

import static com.google.common.truth.Truth.assertThat;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.graphics.Color;
import android.os.Parcel;
import android.util.TypedValue;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo;
import android.view.accessibility.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Class for testing {@link AccessibilityNodeInfo.ExtraRenderingInfo}. */
@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AccessibilityNodeInfo_ExtraRenderingInfoTest {

    /** Allowed tolerance for floating point equality comparisons. */
    public static final float FLOAT_TOLERANCE = 0.001f;

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLayoutSize",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testBuilder_defaults() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().build();

        assertThat(info.getLayoutSize()).isNull();
        assertThat(info.getTextSizeInPx()).isEqualTo(-1f);
        assertThat(info.getTextSizeUnit()).isEqualTo(-1);
        assertThat(info.getTextColor()).isEqualTo(0);
        assertThat(info.getHintTextColor()).isEqualTo(0);
        assertThat(info.getLinkTextColor()).isEqualTo(0);
        assertThat(info.getBackgroundColor()).isEqualTo(0);
        assertThat(info.getAlpha()).isEqualTo(-1f);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetLayoutSize() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().setLayoutSize(100, 200).build();

        assertThat(info.getLayoutSize().getWidth()).isEqualTo(100);
        assertThat(info.getLayoutSize().getHeight()).isEqualTo(200);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetTextSizeInPx() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().setTextSizeInPx(10f).build();

        assertThat(info.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(10f);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetTextSizeUnit() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().setTextSizeUnit(1).build();

        assertThat(info.getTextSizeUnit()).isEqualTo(1);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetTextColor() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().setTextColor(Color.RED).build();

        assertThat(info.getTextColor()).isEqualTo(Color.RED);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setHintTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetHintTextColor() {
        ExtraRenderingInfo info =
                new ExtraRenderingInfo.Builder().setHintTextColor(Color.BLUE).build();

        assertThat(info.getHintTextColor()).isEqualTo(Color.BLUE);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLinkTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetLinkTextColor() {
        ExtraRenderingInfo info =
                new ExtraRenderingInfo.Builder().setLinkTextColor(Color.GREEN).build();

        assertThat(info.getLinkTextColor()).isEqualTo(Color.GREEN);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setBackgroundColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetBackgroundColor() {
        ExtraRenderingInfo info =
                new ExtraRenderingInfo.Builder().setBackgroundColor(Color.BLUE).build();

        assertThat(info.getBackgroundColor()).isEqualTo(Color.BLUE);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setAlpha"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testSetAndGetAlpha() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().setAlpha(0.5f).build();

        assertThat(info.getAlpha()).isWithin(FLOAT_TOLERANCE).of(0.5f);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setAlpha"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyBuilder_copiesAllFields() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder()
                        .setTextSizeInPx(12.0f)
                        .setTextSizeUnit(2)
                        .setLayoutSize(100, 200)
                        .setTextColor(Color.GREEN)
                        .setHintTextColor(Color.BLUE)
                        .setLinkTextColor(Color.RED)
                        .setBackgroundColor(Color.YELLOW)
                        .setAlpha(0.5f)
                        .build();

        ExtraRenderingInfo copy = new ExtraRenderingInfo.Builder(original).build();

        assertThat(copy.getLayoutSize()).isEqualTo(original.getLayoutSize());
        assertThat(copy.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(original.getTextSizeInPx());
        assertThat(copy.getTextSizeUnit()).isEqualTo(original.getTextSizeUnit());
        assertThat(copy.getTextColor()).isEqualTo(original.getTextColor());
        assertThat(copy.getHintTextColor()).isEqualTo(original.getHintTextColor());
        assertThat(copy.getLinkTextColor()).isEqualTo(original.getLinkTextColor());
        assertThat(copy.getBackgroundColor()).isEqualTo(original.getBackgroundColor());
        assertThat(copy.getAlpha()).isWithin(FLOAT_TOLERANCE).of(original.getAlpha());
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndModifyTextSize() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setTextSizeInPx(12.0f).setTextSizeUnit(2).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original)
                        .setTextSizeInPx(15.0f)
                        .setTextSizeUnit(1)
                        .build();

        assertThat(copy.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(15.0f);
        assertThat(copy.getTextSizeUnit()).isEqualTo(1);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndModifyColors() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder()
                        .setTextColor(Color.RED)
                        .setHintTextColor(Color.BLUE)
                        .setLinkTextColor(Color.GREEN)
                        .setBackgroundColor(Color.YELLOW)
                        .setAlpha(0.5f)
                        .build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original)
                        .setTextColor(Color.GREEN)
                        .setHintTextColor(Color.RED)
                        .setLinkTextColor(Color.BLUE)
                        .setBackgroundColor(Color.RED)
                        .setAlpha(0.75f)
                        .build();

        assertThat(copy.getTextColor()).isEqualTo(Color.GREEN);
        assertThat(copy.getHintTextColor()).isEqualTo(Color.RED);
        assertThat(copy.getLinkTextColor()).isEqualTo(Color.BLUE);
        assertThat(copy.getBackgroundColor()).isEqualTo(Color.RED);
        assertThat(copy.getAlpha()).isWithin(FLOAT_TOLERANCE).of(0.75f);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearTextSizeUnit"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearTextSize() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setTextSizeInPx(12.0f).setTextSizeUnit(2).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original)
                        .clearTextSizeInPx()
                        .clearTextSizeUnit()
                        .build();

        assertThat(copy.getTextSizeInPx()).isEqualTo(-1f);
        assertThat(copy.getTextSizeUnit()).isEqualTo(-1);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearLayoutSize"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearLayoutSize() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setLayoutSize(100, 200).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original).clearLayoutSize().build();

        assertThat(copy.getLayoutSize()).isNull();
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearTextColor() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setTextColor(Color.RED).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original).clearTextColor().build();

        assertThat(copy.getTextColor()).isEqualTo(0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearHintTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearHintTextColor() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setHintTextColor(Color.BLUE).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original).clearHintTextColor().build();

        assertThat(copy.getHintTextColor()).isEqualTo(0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearLinkTextColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearLinkTextColor() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setLinkTextColor(Color.GREEN).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original).clearLinkTextColor().build();

        assertThat(copy.getLinkTextColor()).isEqualTo(0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearBackgroundColor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearBackgroundColor() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder().setBackgroundColor(Color.BLUE).build();

        ExtraRenderingInfo copy =
                new ExtraRenderingInfo.Builder(original).clearBackgroundColor().build();

        assertThat(copy.getBackgroundColor()).isEqualTo(0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#clearAlpha"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyAndClearAlpha() {
        ExtraRenderingInfo original = new ExtraRenderingInfo.Builder().setAlpha(0.5f).build();

        ExtraRenderingInfo copy = new ExtraRenderingInfo.Builder(original).clearAlpha().build();

        assertThat(copy.getAlpha()).isEqualTo(-1f);
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#createFromParcel",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#writeToParcel",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testMarshalAndUnmarshal_fullyPopulated() {
        ExtraRenderingInfo originalInfo =
                new ExtraRenderingInfo.Builder()
                        .setLayoutSize(100, 200)
                        .setTextSizeInPx(20f)
                        .setTextSizeUnit(TypedValue.COMPLEX_UNIT_SP)
                        .setTextColor(Color.RED)
                        .setHintTextColor(Color.BLUE)
                        .setLinkTextColor(Color.GREEN)
                        .setBackgroundColor(Color.YELLOW)
                        .setAlpha(0.5f)
                        .build();

        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ExtraRenderingInfo newInfo = ExtraRenderingInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(newInfo.getLayoutSize()).isEqualTo(originalInfo.getLayoutSize());
        assertThat(newInfo.getTextSizeInPx()).isEqualTo(originalInfo.getTextSizeInPx());
        assertThat(newInfo.getTextSizeUnit()).isEqualTo(originalInfo.getTextSizeUnit());
        assertThat(newInfo.getTextColor()).isEqualTo(originalInfo.getTextColor());
        assertThat(newInfo.getHintTextColor())
                .isEqualTo(originalInfo.getHintTextColor());
        assertThat(newInfo.getLinkTextColor())
                .isEqualTo(originalInfo.getLinkTextColor());
        assertThat(newInfo.getBackgroundColor()).isEqualTo(originalInfo.getBackgroundColor());
        assertThat(newInfo.getAlpha()).isEqualTo(originalInfo.getAlpha());
    }

    @Test
    @ApiTest(
            apis = {
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getHintTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getLinkTextColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getBackgroundColor",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#getAlpha",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#writeToParcel",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo#createFromParcel"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testMarshalAndUnmarshal_allFieldsDefault() {
        ExtraRenderingInfo originalInfo = new ExtraRenderingInfo.Builder().build();

        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ExtraRenderingInfo newInfo = ExtraRenderingInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(newInfo.getLayoutSize()).isNull();
        assertThat(newInfo.getTextSizeInPx()).isEqualTo(-1f);
        assertThat(newInfo.getTextSizeUnit()).isEqualTo(-1);
        assertThat(newInfo.getTextColor()).isEqualTo(0);
        assertThat(newInfo.getHintTextColor()).isEqualTo(0);
        assertThat(newInfo.getLinkTextColor()).isEqualTo(0);
        assertThat(newInfo.getBackgroundColor()).isEqualTo(0);
        assertThat(newInfo.getAlpha()).isEqualTo(-1f);
    }
}
