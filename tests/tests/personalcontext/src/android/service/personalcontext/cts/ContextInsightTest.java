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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:ContextInsightTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ContextInsightTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Tests bundling and unbundling fields on the base ContextInsight.
    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.ContextInsight#getInsightType",
                "android.service.personalcontext.insight.ContextInsight#getInsightId",
                "android.service.personalcontext.insight.ContextInsight#getOriginHints"
            })
    @Test
    public void testContextInsightBundleUnbundle() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final BundleHint hint = new BundleHint();
        hint.getDataBundle().putInt(dataKey, inputValue);

        final BundleInsight insight = new BundleInsight.Builder().addOriginHint(hint).build();
        ContextInsight outputInsight = bundleUnbundle(insight);

        assertThat(outputInsight).isInstanceOf(BundleInsight.class);
        assertThat(insight.getInsightId()).isEqualTo(outputInsight.getInsightId());

        assertThat(outputInsight.getOriginHints().size()).isEqualTo(1);

        final BundleHint outHint = (BundleHint) outputInsight.getOriginHints().get(0);
        assertThat(outHint.getDataBundle().getInt(dataKey)).isEqualTo(inputValue);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.BundleInsight#getDataBundle",
            })
    @Test
    public void testBundleInsightBundleUnbundle() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final Bundle data = new Bundle();
        data.putInt(dataKey, inputValue);

        final BundleInsight insight = new BundleInsight.Builder().setDataBundle(data).build();

        ContextInsight outputInsight = bundleUnbundle(insight);
        assertThat(outputInsight).isInstanceOf(BundleInsight.class);
        final int outputValue = ((BundleInsight) outputInsight).getDataBundle().getInt(dataKey);

        assertThat(outputValue).isEqualTo(inputValue);
    }

    /** Bundles then unbundles the given {@link ContextInsight}. */
    public ContextInsight bundleUnbundle(ContextInsight insight) {
        return ContextInsight.createInsightFromBundle(insight.toBundle());
    }
}
