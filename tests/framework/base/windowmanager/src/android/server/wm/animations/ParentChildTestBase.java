/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.wm.animations;

import static android.server.wm.StateLogger.log;
import static android.server.wm.animations.DialogFrameTestActivity.EXTRA_TEST_CASE;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Intent;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.WindowManagerState.WindowState;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

abstract class ParentChildTestBase<T extends Activity> extends ActivityManagerTestBase {

    interface ParentChildTest<U extends Activity> {
        void doTest(WindowState parent, WindowState child, ActivityScenario<U> scenario);
    }

    private ActivityScenario<T> startTestCase(String testCase, boolean isFullscreen)
            throws Exception {
        final Intent intent =
                new Intent().putExtra(EXTRA_TEST_CASE, testCase).setComponent(activityName());
        final ActivityOptions options = ActivityOptions.makeBasic();
        if (isFullscreen) {
            options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
        }
        final ActivityScenario<T> scenario = ActivityScenario.launch(intent, options.toBundle());
        scenario.moveToState(Lifecycle.State.RESUMED);
        return scenario;
    }

    private ActivityScenario<T> startTestCaseDocked(String testCase) throws Exception {
        final ActivityScenario<T> scenario = startTestCase(testCase, false /* isFullscreen */);
        mWmState.computeState(activityName());
        putActivityInPrimarySplit(activityName());
        return scenario;
    }

    abstract ComponentName activityName();

    abstract void doSingleTest(ParentChildTest<T> t, ActivityScenario<T> scenario) throws Exception;

    void doFullscreenTest(String testCase, ParentChildTest<T> t) throws Exception {
        log("Running test fullscreen");
        try (ActivityScenario<T> scenario = startTestCase(testCase, true /* isFullscreen */)) {
            doSingleTest(t, scenario);
        }
    }

    private void doDockedTest(String testCase, ParentChildTest<T> t) throws Exception {
        log("Running test docked");
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }
        try (ActivityScenario<T> scenario = startTestCaseDocked(testCase)) {
            doSingleTest(t, scenario);
        }

        mWmState.waitFor(wmState -> !wmState.containsActivity(activityName()),
                "activity must be removed");
        Condition.waitFor(
                "primary split to be empty", () -> mTaskOrganizer.getPrimarySplitTaskCount() == 0);
    }

    void doParentChildTest(String testCase, ParentChildTest<T> t) throws Exception {
        doFullscreenTest(testCase, t);
        doDockedTest(testCase, t);
    }
}
