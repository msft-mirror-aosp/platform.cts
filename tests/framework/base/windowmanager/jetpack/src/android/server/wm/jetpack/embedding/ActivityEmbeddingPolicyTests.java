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

package android.server.wm.jetpack.embedding;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.server.wm.jetpack.second.Components.PORTRAIT_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.res.Configuration;
import android.os.SystemProperties;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.NestedShellPermission;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerState;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests security
 * policies that should be applied by the system.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingPolicyTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingPolicyTests extends ActivityManagerTestBase {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeActivityEmbeddingSupportedDevice();
    }

    @After
    public void tearDown() {
        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.second"));
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.signed"));
    }

    /**
     * Verifies that the orientation request from the Activity is ignored if app uses
     * ActivityEmbedding on a large screen device that supports AE.
     */
    @ApiTest(apis = {
            "R.attr#screenOrientation",
            "android.view.WindowManager#PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED"
    })
    @Test
    public void testIgnoreOrientationRequestForActivityEmbeddingSplits() {
        // Skip the test on devices without WM extensions.
        assumeTrue(SystemProperties.getBoolean("persist.wm.extensions.enabled", false));

        // Skip the test if this is not a large screen device
        assumeTrue(getDisplayConfiguration().smallestScreenWidthDp
                >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP);

        // Rotate the device to landscape
        final RotationSession rotationSession = createManagedRotationSession();
        final int[] rotations = { ROTATION_0, ROTATION_90 };
        for (final int rotation : rotations) {
            if (getDisplayConfiguration().orientation == ORIENTATION_LANDSCAPE) {
                break;
            }
            rotationSession.set(rotation);
        }
        assumeTrue(getDisplayConfiguration().orientation == ORIENTATION_LANDSCAPE);

        // Launch a fixed-portrait activity
        startActivityOnDisplay(Display.DEFAULT_DISPLAY, PORTRAIT_ACTIVITY);

        // The display should be remained in landscape.
        assertEquals("The display should be remained in landscape", ORIENTATION_LANDSCAPE,
                getDisplayConfiguration().orientation);
    }

    private Configuration getDisplayConfiguration() {
        mWmState.computeState();
        WindowManagerState.DisplayContent display = mWmState.getDisplay(DEFAULT_DISPLAY);
        return display.getFullConfiguration();
    }
}
