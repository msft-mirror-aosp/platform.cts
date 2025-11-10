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
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testBuilder_defaults() {
        ExtraRenderingInfo info = new ExtraRenderingInfo.Builder().build();

        assertThat(info.getLayoutSize()).isNull();
        assertThat(info.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(-1f);
        assertThat(info.getTextSizeUnit()).isEqualTo(-1);
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
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#build",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeInPx",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setTextSizeUnit",
                "android.view.accessibility.AccessibilityNodeInfo.ExtraRenderingInfo.Builder#setLayoutSize"
            })
    @RequiresFlagsEnabled(Flags.FLAG_A11Y_EXTRA_RENDERING_INFO_COLOR_ADDITIONS)
    public void testCopyBuilder_copiesAllFields() {
        ExtraRenderingInfo original =
                new ExtraRenderingInfo.Builder()
                        .setTextSizeInPx(12.0f)
                        .setTextSizeUnit(2)
                        .setLayoutSize(100, 200)
                        .build();

        ExtraRenderingInfo copy = new ExtraRenderingInfo.Builder(original).build();

        assertThat(copy.getLayoutSize()).isEqualTo(original.getLayoutSize());
        assertThat(copy.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(original.getTextSizeInPx());
        assertThat(copy.getTextSizeUnit()).isEqualTo(original.getTextSizeUnit());
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

        assertThat(copy.getTextSizeInPx()).isWithin(FLOAT_TOLERANCE).of(-1f);
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
}
