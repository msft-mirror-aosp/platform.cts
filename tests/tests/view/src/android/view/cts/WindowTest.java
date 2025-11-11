/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.view.cts;

import static android.view.flags.Flags.FLAG_TOOLKIT_SET_FRAME_RATE_READ_ONLY;

import static com.android.graphics.hwui.flags.Flags.FLAG_LIMITED_HDR;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Window;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowTest {
    private ViewTestCtsActivity mActivity;
    private Window mWindow;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mWindow = mActivity.getWindow();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_TOOLKIT_SET_FRAME_RATE_READ_ONLY)
    public void testFrameRateBoostOnTouchEnabled() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertTrue(mWindow.getFrameRateBoostOnTouchEnabled());
            mWindow.setFrameRateBoostOnTouchEnabled(false);
            assertFalse(mWindow.getFrameRateBoostOnTouchEnabled());
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_TOOLKIT_SET_FRAME_RATE_READ_ONLY)
    public void testFrameRatePowerSavingsBalanced() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertTrue(mWindow.isFrameRatePowerSavingsBalanced());
            mWindow.setFrameRatePowerSavingsBalanced(false);
            assertFalse(mWindow.isFrameRatePowerSavingsBalanced());
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIMITED_HDR)
    public void testSetGetDesiredHdrHeadroom() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertEquals(mWindow.getDesiredHdrHeadroom(), 0.0f);
            mWindow.setDesiredHdrHeadroom(2.5f);
            mWindow.setColorMode(ActivityInfo.COLOR_MODE_HDR);
            assertEquals(mWindow.getDesiredHdrHeadroom(), 2.5f);
        });
    }
}
