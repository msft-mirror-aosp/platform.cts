/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.display.cts.staticdevicestatetests;

import static android.hardware.display.DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.devicestate.cts.util.DeviceStateManagerTestRule;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.server.display.feature.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for {@link DisplayManager} that should not be ran through all device states. Tests should
 * only be included here if you're sure it will cause issues being run through all device states.
 * This is usually due to the test itself modifying device state.
 *
 * <p>This is in its own file and test module as we need to disable the cts foldable testing for
 * this test as it cycles through device states in the test.
 *
 * <p>atest
 * CtsDisplayTestCasesNoFoldableStates:android.display.cts.staticdevicestatetests.DisplayManagerSingleDeviceStateTest
 */
public class DisplayManagerSingleDeviceStateTest {

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public DeviceStateManagerTestRule mDeviceStateManagerTestRule =
            new DeviceStateManagerTestRule();

    private TestActivity mActivity;

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_CATEGORY_BUILT_IN)
    public void testDisplayCategoryBuiltIn_allDisplays() throws Throwable {
        final DisplayManager displayManager =
                Objects.requireNonNull(mActivity.getSystemService(DisplayManager.class));

        final Set<String> uniqueDisplayIds = new HashSet<>();
        for (Display display : displayManager.getDisplays(DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)) {
            uniqueDisplayIds.add(display.getUniqueId());
        }

        mDeviceStateManagerTestRule.cycleThroughHardwareStates(
                () -> {
                    Display[] displays = displayManager.getDisplays();
                    for (Display display : displays) {
                        if (display.getType() == Display.TYPE_INTERNAL) {
                            assertTrue(
                                    "Built in display not in built in displays. Expected: "
                                            + display.getUniqueId()
                                            + ", Set: "
                                            + uniqueDisplayIds,
                                    uniqueDisplayIds.contains(display.getUniqueId()));
                        }
                    }
                });
    }

    public static class TestActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.setBackgroundColor(Color.RED);
            FrameLayout.LayoutParams layoutParams =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT);

            setContentView(frameLayout, layoutParams);
        }
    }
}
