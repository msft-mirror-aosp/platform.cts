/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm;

import static android.server.wm.backgroundactivity.appa.Components.APP_A_BACKGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_FOREGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_FOREGROUND_EMBEDDING_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundEmbeddedActivityAction.APP_A_LAUNCH_EMBEDDED_ACTIVITY;
import static android.server.wm.backgroundactivity.appb.Components.APP_B_FOREGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appb.Components.ForegroundActivityAction.APP_B_LAUNCH_BACKGROUND_ACTIVITIES;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;

import android.content.ComponentName;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;

import androidx.test.filters.FlakyTest;

import com.android.bedstead.harrier.DeviceState;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivitySecurityModelEmbeddingTest extends BackgroundActivityTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeActivityEmbeddingSupportedDevice();

        // Set up app a with A and B running embedded
        new ActivityStartVerifier()
                .setupTaskWithEmbeddingActivity(APP_A_FOREGROUND_EMBEDDING_ACTIVITY)
                .startFromEmbeddingActivity(APP_A_LAUNCH_EMBEDDED_ACTIVITY)
                .activity(APP_B_FOREGROUND_ACTIVITY)
                .executeAndAssertLaunch(/*succeeds*/ true)
                .thenAssertEmbeddingTaskStack(
                        new ComponentName[] {APP_B_FOREGROUND_ACTIVITY},
                        APP_A_FOREGROUND_EMBEDDING_ACTIVITY);
    }

    @Test
    @FlakyTest(bugId = 291212072)
    public void testEmbeddingLaunchesActivity_launchAllowed() {
        // Base State:
        // | A.FGE (A1) | B.FG (B1) |   --> left | right
        new ActivityStartVerifier()
                .startFromEmbeddingActivity(APP_A_LAUNCH_EMBEDDED_ACTIVITY)
                .activity(APP_A_FOREGROUND_ACTIVITY)
                // Test - A1 launches A.FG (A2) - succeeds
                // As B allows itself to be embedded by A, A is may close or replace B with another
                // activity (e.g, split-pane views)
                .executeAndAssertLaunch(/*succeeds*/ true)
                // Final State:
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(
                        new ComponentName[] {APP_A_FOREGROUND_ACTIVITY, APP_B_FOREGROUND_ACTIVITY},
                        APP_A_FOREGROUND_EMBEDDING_ACTIVITY);
    }

    @Test
    @FlakyTest(bugId = 291212072)
    @RequiresFlagsEnabled(Flags.FLAG_ASM_RESTRICTIONS_V2)
    public void testEmbeddedLaunchesActivity_launchAllowedOnlyOnTop() {
        // Base State:
        // | A.FGE (A1) | B.FG (B1) |   --> left | right
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_B_LAUNCH_BACKGROUND_ACTIVITIES)
                .activity(APP_A_FOREGROUND_ACTIVITY)
                // Test - B1 launches A.FG (A2) - succeeds
                .executeAndAssertLaunch(/*succeeds*/ true)
                // Final State:
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(
                        new ComponentName[] {APP_A_FOREGROUND_ACTIVITY, APP_B_FOREGROUND_ACTIVITY},
                        APP_A_FOREGROUND_EMBEDDING_ACTIVITY);

        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_B_LAUNCH_BACKGROUND_ACTIVITIES)
                .activity(APP_A_BACKGROUND_ACTIVITY)
                // Test - B1 launches A.BG (A3) - fails
                .executeAndAssertLaunch(/*succeeds*/ false)
                // Final State (no change):
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(
                        new ComponentName[] {APP_A_FOREGROUND_ACTIVITY, APP_B_FOREGROUND_ACTIVITY},
                        APP_A_FOREGROUND_EMBEDDING_ACTIVITY);
    }
}
