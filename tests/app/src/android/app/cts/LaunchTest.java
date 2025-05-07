/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.stubs.LocalActivity;
import android.app.stubs.TestedActivity;
import android.app.stubs.shared.ClearTop;
import android.app.stubs.shared.LaunchpadActivity;
import android.app.stubs.shared.LaunchpadHelper;
import android.app.stubs.shared.LocalScreen;
import android.app.stubs.shared.TestedScreen;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LaunchTest {
    private LaunchpadHelper mLaunchpadHelper;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLaunchpadHelper = new LaunchpadHelper(mContext);
    }

    @Test
    public void testClearTopWhilResumed() {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, ClearTop.class));
        mLaunchpadHelper.editIntent().putExtra(ClearTop.WAIT_CLEAR_TASK, true);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }

    @Test
    public void testClearTopInCreate() throws Exception {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, ClearTop.class));
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }

    @Test
    public void testForwardResult() {
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.FORWARD_RESULT);
    }

    @Test
    public void testLocalScreen() {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, LocalScreen.class));
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }

    @Test
    public void testColdScreen() {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, TestedScreen.class));
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }

    @Test
    public void testLocalActivity() {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, LocalActivity.class));
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }

    @Test
    public void testColdActivity() {
        mLaunchpadHelper
                .editIntent()
                .putExtra("component", new ComponentName(mContext, TestedActivity.class));
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LAUNCH);
    }
}
