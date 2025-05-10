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

import android.app.stubs.LaunchpadTabActivity;
import android.app.stubs.shared.LaunchpadActivity;
import android.app.stubs.shared.LaunchpadHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LifecycleTest {
    private Intent mTopIntent;
    private Intent mTabIntent;
    private LaunchpadHelper mLaunchpadHelper;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLaunchpadHelper = new LaunchpadHelper(context);
        mTopIntent = mLaunchpadHelper.editIntent();
        mTabIntent = new Intent(context, LaunchpadTabActivity.class);
        mTabIntent.putExtra("tab", new ComponentName(context, LaunchpadActivity.class));
    }

    @Test
    public void testTabDialog() {
        mLaunchpadHelper.setIntent(mTabIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_DIALOG);
    }

    @Test
    public void testDialog() {
        mLaunchpadHelper.setIntent(mTopIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_DIALOG);
    }

    @Test
    public void testTabScreen() {
        mLaunchpadHelper.setIntent(mTabIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_SCREEN);
    }

    @Test
    public void testScreen() {
        mLaunchpadHelper.setIntent(mTopIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_SCREEN);
    }

    @Test
    public void testTabBasic() {
        mLaunchpadHelper.setIntent(mTabIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_BASIC);
    }

    @Test
    public void testBasic() {
        mLaunchpadHelper.setIntent(mTopIntent);
        mLaunchpadHelper.runLaunchpad(LaunchpadActivity.LIFECYCLE_BASIC);
    }
}
