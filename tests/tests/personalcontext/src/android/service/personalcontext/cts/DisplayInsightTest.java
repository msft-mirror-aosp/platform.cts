/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:DisplayInsightTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class DisplayInsightTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.DisplayInsight#getDisplayDetails",
                "android.service.personalcontext.insight.DisplayInsight#equals",
                "android.service.personalcontext.insight.DisplayInsight#hashCode",
            })
    @Test
    public void testDisplayInsight_bundleUnbundle() {
        final InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .build();
        final DisplayInsight originalInsight = new DisplayInsight.Builder(displayDetails).build();

        final ContextInsight outputInsight = bundleUnbundle(originalInsight);

        assertThat(outputInsight).isInstanceOf(DisplayInsight.class);
        final DisplayInsight outputDisplayInsight = (DisplayInsight) outputInsight;

        assertThat(originalInsight.getDetails()).isEqualTo(outputDisplayInsight.getDetails());
        assertThat(outputDisplayInsight).isEqualTo(originalInsight);
        assertThat(outputDisplayInsight.hashCode()).isEqualTo(originalInsight.hashCode());
    }

    /** Bundles then unbundles the given {@link ContextInsight}. */
    public ContextInsight bundleUnbundle(ContextInsight insight) {
        return ContextInsight.createInsightFromBundle(insight.toBundle());
    }
}
