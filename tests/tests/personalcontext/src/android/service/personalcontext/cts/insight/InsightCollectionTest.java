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

package android.service.personalcontext.cts.insight;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightCollection;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** Build/Install/Run: atest CtsPersonalContextTestCases:InsightCollectionTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightCollectionTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static PendingIntent createFakePendingIntent() {
        return PendingIntent.getBroadcast(
                InstrumentationRegistry.getTargetContext(),
                0,
                new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.InsightCollection.Builder#build",
                "android.service.personalcontext.insight.InsightCollection.Builder#addInsight",
                "android.service.personalcontext.insight.InsightCollection#getInsights",
                "android.service.personalcontext.insight.InsightCollection#iterator",
                "android.service.personalcontext.insight.InsightCollection#equals",
                "android.service.personalcontext.insight.InsightCollection#hashCode",
            })
    @Test
    public void testInsightCollection_bundleUnbundle_sameInsightType() {
        final InsightActionDetails actionDetails1 =
                new InsightActionDetails.Builder()
                        .setPendingIntent(createFakePendingIntent())
                        .build();
        final InsightDisplayDetails displayDetails1 =
                new InsightDisplayDetails.Builder("title1")
                        .setContentDescription("content description 1")
                        .build();
        final ActionableInsight actionableInsight1 =
                new ActionableInsight.Builder(actionDetails1, displayDetails1).build();

        final InsightActionDetails actionDetails2 =
                new InsightActionDetails.Builder()
                        .setPendingIntent(createFakePendingIntent())
                        .build();
        final InsightDisplayDetails displayDetails2 =
                new InsightDisplayDetails.Builder("title2")
                        .setContentDescription("content description 2")
                        .build();
        final ActionableInsight actionableInsight2 =
                new ActionableInsight.Builder(actionDetails2, displayDetails2).build();

        final InsightCollection originalInsight =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight1)
                        .addInsight(actionableInsight2)
                        .build();

        final ContextInsight outputInsight = bundleUnbundle(originalInsight);

        assertThat(outputInsight).isInstanceOf(InsightCollection.class);
        final InsightCollection outputCollection = (InsightCollection) outputInsight;

        assertThat(outputCollection.getInsights())
                .containsExactly(actionableInsight1, actionableInsight2)
                .inOrder();
        assertThat(outputCollection).isEqualTo(originalInsight);
        assertThat(outputCollection.hashCode()).isEqualTo(originalInsight.hashCode());
    }

    @Test
    public void testInsightCollection_bundleUnbundle_differentInsightTypes() {
        final InsightActionDetails actionDetails =
                new InsightActionDetails.Builder()
                        .setPendingIntent(createFakePendingIntent())
                        .build();
        final InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .build();

        final ActionableInsight actionableInsight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();
        final DisplayInsight displayInsight = new DisplayInsight.Builder(displayDetails).build();

        final InsightCollection originalInsight =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        final ContextInsight outputInsight = bundleUnbundle(originalInsight);

        assertThat(outputInsight).isInstanceOf(InsightCollection.class);
        final InsightCollection outputCollection = (InsightCollection) outputInsight;

        assertThat(outputCollection.getInsights())
                .containsExactly(actionableInsight, displayInsight)
                .inOrder();
        assertThat(outputCollection).isEqualTo(originalInsight);
    }

    @Test
    public void testIterator() {
        final ActionableInsight actionableInsight =
                new ActionableInsight.Builder(
                                new InsightActionDetails.Builder()
                                        .setPendingIntent(createFakePendingIntent())
                                        .build(),
                                new InsightDisplayDetails.Builder("title").build())
                        .build();
        final DisplayInsight displayInsight =
                new DisplayInsight.Builder(new InsightDisplayDetails.Builder("title").build())
                        .build();

        final InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        List<ContextInsight> insightsFromIterator = new ArrayList<>();
        for (ContextInsight insight : collection) {
            insightsFromIterator.add(insight);
        }

        assertThat(insightsFromIterator).containsExactly(actionableInsight, displayInsight);
    }

    /** Bundles then unbundles the given {@link ContextInsight}. */
    private ContextInsight bundleUnbundle(ContextInsight insight) {
        return ContextInsight.createInsightFromBundle(insight.toBundle());
    }
}
