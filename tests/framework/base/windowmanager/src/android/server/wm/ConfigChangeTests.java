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

package android.server.wm;

import static android.server.wm.ActivityManagerState.STATE_RESUMED;
import static android.server.wm.StateLogger.log;
import static android.server.wm.StateLogger.logE;
import static android.server.wm.app.Components.FONT_SCALE_ACTIVITY;
import static android.server.wm.app.Components.FONT_SCALE_NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.FontScaleActivity.EXTRA_FONT_ACTIVITY_DPI;
import static android.server.wm.app.Components.FontScaleActivity.EXTRA_FONT_PIXEL_SIZE;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.EXTRA_CONFIG_ASSETS_SEQ;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.CommandSession.SizeInfo;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.settings.SettingsSession;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:ConfigChangeTests
 */
@Presubmit
public class ConfigChangeTests extends ActivityManagerTestBase {

    private static final float EXPECTED_FONT_SIZE_SP = 10.0f;

    @Test
    public void testRotation90Relaunch() throws Exception{
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        // Should relaunch on every rotation and receive no onConfigurationChanged()
        testRotation(TEST_ACTIVITY, 1, 1, 0);
    }

    @Test
    public void testRotation90NoRelaunch() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        // Should receive onConfigurationChanged() on every rotation and no relaunch
        testRotation(NO_RELAUNCH_ACTIVITY, 1, 0, 1);
    }

    // TODO(b/110382028): Fix relaunch testing and use an activity that doesn't handle config change
    @Test
    public void testRotation180Relaunch() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        // TODO(b/110533226): Fix test on devices with display cutout
        assumeFalse("Skipping test: display cutout present, can't predict exact lifecycle",
                hasDisplayCutout());

        // Should receive a relaunch
        testRotation(TEST_ACTIVITY, 2, 0, 0);
    }

    @Test
    public void testRotation180NoRelaunch() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        // TODO(b/110533226): Fix test on devices with display cutout
        assumeFalse("Skipping test: display cutout present, can't predict exact lifecycle",
                hasDisplayCutout());

        // Should receive nothing
        testRotation(NO_RELAUNCH_ACTIVITY, 2, 0, 0);
    }

    @Test
    public void testChangeFontScaleRelaunch() throws Exception {
        // Should relaunch and receive no onConfigurationChanged()
        testChangeFontScale(FONT_SCALE_ACTIVITY, true /* relaunch */);
    }

    @Test
    public void testChangeFontScaleNoRelaunch() throws Exception {
        // Should receive onConfigurationChanged() and no relaunch
        testChangeFontScale(FONT_SCALE_NO_RELAUNCH_ACTIVITY, false /* relaunch */);
    }

    /**
     * Test activity configuration changes for devices with cutout(s). Landscape and
     * reverse-landscape rotations should result in same screen space available for apps.
     */
    @Test
    public void testConfigChangeWhenRotatingWithCutout() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());
        assumeTrue("Skipping test: no display cutout", hasDisplayCutout());

        // Start an activity that handles config changes
        launchActivity(RESIZEABLE_ACTIVITY);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);
        final int displayId = mAmWmState.getAmState().getDisplayByActivity(RESIZEABLE_ACTIVITY);

        // 0 - 180 rotation or 270 - 90 rotation should have same screen space
        boolean configSame0_180 = false, configSame270_90 = false;
        final int[] cutoutRotations = { ROTATION_180, ROTATION_90, ROTATION_270 };

        // Rotate the activity and check that the orientation doesn't change at least once
        try (final RotationSession rotationSession = new RotationSession()) {
            rotationSession.set(ROTATION_0);

            final SizeInfo[] sizes = new SizeInfo[cutoutRotations.length];
            for (int i = 0; i < cutoutRotations.length; i++) {
                separateTestJournal();
                final int rotation = cutoutRotations[i];
                rotationSession.set(rotation);
                final int newDeviceRotation = getDeviceRotation(displayId);
                if (rotation != newDeviceRotation) {
                    log("This device doesn't support locked user "
                            + "rotation mode. Not continuing the rotation checks.");
                    continue;
                }

                // Record configuration changes on rotations between opposite orientations
                sizes[i] = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY);
                if (i == 0) {
                    configSame0_180 = sizes[i] == null;
                } else if (i == 2) {
                    configSame270_90 = sizes[i] == null;
                }
            }

            assertThat("A device with cutout should have same available screen space in "
                    + "landscape and reverse-landscape", configSame0_180 || configSame270_90);
        }
    }

    private void testRotation(ComponentName activityName, int rotationStep, int numRelaunch,
            int numConfigChange) throws Exception {
        launchActivity(activityName);

        mAmWmState.computeState(activityName);

        final int initialRotation = 4 - rotationStep;
        try (final RotationSession rotationSession = new RotationSession()) {
            rotationSession.set(initialRotation);
            mAmWmState.computeState(activityName);
            final int actualStackId =
                    mAmWmState.getAmState().getTaskByActivity(activityName).mStackId;
            final int displayId = mAmWmState.getAmState().getStackById(actualStackId).mDisplayId;
            final int newDeviceRotation = getDeviceRotation(displayId);
            if (newDeviceRotation == INVALID_DEVICE_ROTATION) {
                logE("Got an invalid device rotation value. "
                        + "Continuing the test despite of that, but it is likely to fail.");
            } else if (newDeviceRotation != initialRotation) {
                log("This device doesn't support user rotation "
                        + "mode. Not continuing the rotation checks.");
                return;
            }

            for (int rotation = 0; rotation < 4; rotation += rotationStep) {
                separateTestJournal();
                rotationSession.set(rotation);
                mAmWmState.computeState(activityName);
                assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
            }
        }
    }

    /** Helper class to save, set, and restore font_scale preferences. */
    private static class FontScaleSession extends SettingsSession<Float> {
        FontScaleSession() {
            super(Settings.System.getUriFor(Settings.System.FONT_SCALE),
                    Settings.System::getFloat,
                    Settings.System::putFloat);
        }
    }

    private void testChangeFontScale(
            ComponentName activityName, boolean relaunch) throws Exception {
        try (final FontScaleSession fontScaleSession = new FontScaleSession()) {
            fontScaleSession.set(1.0f);
            separateTestJournal();
            launchActivity(activityName);
            mAmWmState.computeState(activityName);

            final int densityDpi = getActivityDensityDpi(activityName);

            for (float fontScale = 0.85f; fontScale <= 1.3f; fontScale += 0.15f) {
                separateTestJournal();
                fontScaleSession.set(fontScale);
                mAmWmState.computeState(activityName);
                assertRelaunchOrConfigChanged(activityName, relaunch ? 1 : 0, relaunch ? 0 : 1);

                // Verify that the display metrics are updated, and therefore the text size is also
                // updated accordingly.
                assertExpectedFontPixelSize(activityName,
                        scaledPixelsToPixels(EXPECTED_FONT_SIZE_SP, fontScale, densityDpi));
            }
        }
    }

    /**
     * Test updating application info when app is running. An activity with matching package name
     * must be recreated and its asset sequence number must be incremented.
     */
    @Test
    public void testUpdateApplicationInfo() throws Exception {
        separateTestJournal();

        // Launch an activity that prints applied config.
        launchActivity(TEST_ACTIVITY);
        final int assetSeq = getAssetSeqNumber(TEST_ACTIVITY);

        separateTestJournal();
        // Update package info.
        updateApplicationInfo(Arrays.asList(TEST_ACTIVITY.getPackageName()));
        mAmWmState.waitForWithAmState((amState) -> {
            // Wait for activity to be resumed and asset seq number to be updated.
            try {
                return getAssetSeqNumber(TEST_ACTIVITY) == assetSeq + 1
                        && amState.hasActivityState(TEST_ACTIVITY, STATE_RESUMED);
            } catch (Exception e) {
                logE("Error waiting for valid state: " + e.getMessage());
                return false;
            }
        }, "Waiting asset sequence number to be updated and for activity to be resumed.");

        // Check if activity is relaunched and asset seq is updated.
        assertRelaunchOrConfigChanged(TEST_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
        final int newAssetSeq = getAssetSeqNumber(TEST_ACTIVITY);
        assertEquals("Asset sequence number must be incremented.", assetSeq + 1, newAssetSeq);
    }

    private static int getAssetSeqNumber(ComponentName activityName) {
        return TestJournalContainer.get(activityName).extras.getInt(EXTRA_CONFIG_ASSETS_SEQ);
    }

    // Calculate the scaled pixel size just like the device is supposed to.
    private static int scaledPixelsToPixels(float sp, float fontScale, int densityDpi) {
        final int DEFAULT_DENSITY = 160;
        float f = densityDpi * (1.0f / DEFAULT_DENSITY) * fontScale * sp;
        return (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
    }

    private static int getActivityDensityDpi(ComponentName activityName)
            throws Exception {
        final Bundle extras = TestJournalContainer.get(activityName).extras;
        if (!extras.containsKey(EXTRA_FONT_ACTIVITY_DPI)) {
            fail("No fontActivityDpi reported from activity " + activityName);
            return -1;
        }
        return extras.getInt(EXTRA_FONT_ACTIVITY_DPI);
    }

    private void assertExpectedFontPixelSize(ComponentName activityName, int fontPixelSize)
            throws Exception {
        final Bundle extras = TestJournalContainer.get(activityName).extras;
        if (!extras.containsKey(EXTRA_FONT_PIXEL_SIZE)) {
            fail("No fontPixelSize reported from activity " + activityName);
        }
        assertEquals("Expected font pixel size does not match", fontPixelSize,
                extras.getInt(EXTRA_FONT_PIXEL_SIZE));
    }

    private void updateApplicationInfo(List<String> packages) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.scheduleApplicationInfoChanged(packages,
                        android.os.Process.myUserHandle().getIdentifier())
        );
    }
}
