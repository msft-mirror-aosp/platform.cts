/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.getPrimaryStackTopActivity;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * launch scenarios on secondary display.
 * <p>
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:MultiDisplayActivityEmbeddingLaunchTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class MultiDisplayActivityEmbeddingLaunchTests extends ActivityEmbeddingTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsMultiDisplay());
    }

    /**
     * Tests splitting activities with the same primary activity.
     */
    @Test
    public void testSplitWithPrimaryActivity() throws InterruptedException {
        // Create new virtual display.
        final WindowManagerState.DisplayContent mDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class, null /* activityId */, mDisplay.mId);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch multiple activities from the primary activity and verify that they all
        // successfully split with the primary activity.
        final List<Activity> secondaryActivities = new ArrayList<>();
        final List<List<SplitInfo>> splitInfosList = new ArrayList<>();
        final int numActivitiesToLaunch = 4;
        for (int activityLaunchIndex = 0; activityLaunchIndex < numActivitiesToLaunch;
                activityLaunchIndex++) {
            final Activity secondaryActivity = startActivityAndVerifySplitAttributes(
                    primaryActivity, TestActivityWithId.class, splitPairRule,
                    Integer.toString(activityLaunchIndex) /* secondActivityId */,
                    mSplitInfoConsumer);

            // Verify that the secondary container has all the secondary activities
            secondaryActivities.add(secondaryActivity);
            final List<SplitInfo> lastReportedSplitInfoList =
                    mSplitInfoConsumer.getLastReportedValue();
            splitInfosList.add(lastReportedSplitInfoList);
            assertEquals(1, lastReportedSplitInfoList.size());
            final SplitInfo splitInfo = lastReportedSplitInfoList.get(0);
            assertEquals(primaryActivity, getPrimaryStackTopActivity(splitInfo));
            assertEquals(secondaryActivities, splitInfo.getSecondaryActivityStack()
                    .getActivities());
        }

        // Iteratively finish each secondary activity and verify that the primary activity is split
        // with the next highest secondary activity.
        for (int i = secondaryActivities.size() - 1; i >= 1; i--) {
            final Activity currentSecondaryActivity = secondaryActivities.get(i);
            currentSecondaryActivity.finish();
            // A split info callback will occur because the split states have changed
            final List<SplitInfo> newSplitInfos = mSplitInfoConsumer.waitAndGet();
            // Verify the new split
            final Activity newSecondaryActivity = secondaryActivities.get(i - 1);
            assertValidSplit(primaryActivity, newSecondaryActivity, splitPairRule);
            assertEquals(splitInfosList.get(i - 1), newSplitInfos);
        }
    }
}
