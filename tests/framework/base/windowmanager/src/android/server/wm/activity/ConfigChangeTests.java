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

package android.server.wm.activity;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.StateLogger.log;
import static android.server.wm.StateLogger.logAlways;
import static android.server.wm.StateLogger.logE;
import static android.server.wm.app.Components.FONT_SCALE_ACTIVITY;
import static android.server.wm.app.Components.FONT_SCALE_NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.FontScaleActivity.EXTRA_FONT_ACTIVITY_DPI;
import static android.server.wm.app.Components.FontScaleActivity.EXTRA_FONT_PIXEL_SIZE;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.RECREATE_ON_KEYBOARD_CHANGE_ACTIVITY;
import static android.server.wm.app.Components.RECREATE_ON_NAVIGATION_CHANGE_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.keyboardresources.Components.KEYBOARD_RESOURCES_ACTIVITY;
import static android.server.wm.navigationresources.Components.NAVIGATION_RESOURCES_ACTIVITY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CommandSession.ActivityCallback;
import android.server.wm.Condition;
import android.server.wm.CountSpec;
import android.server.wm.RotationSession;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.app.Components;
import android.view.InputDevice;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.input.ConfigurationItem;
import com.android.cts.input.UinputKeyboard;
import com.android.cts.input.UinputDevice;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.cts.input.UinputRegisterCommand;
import com.android.window.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:ConfigChangeTests
 */
@Presubmit
@RunWith(BedsteadJUnit4.class)
public class ConfigChangeTests extends ActivityManagerTestBase {

    private static final float EXPECTED_FONT_SIZE_SP = 10.0f;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @After
    public void tearDown() {
        Components.forceStopPackage();
    }

    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation90Relaunch() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());

        // Should relaunch on every rotation and receive no onConfigurationChanged()
        testRotation(TEST_ACTIVITY, 1, 1, 0);
    }

    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation90NoRelaunch() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());

        // Should receive onConfigurationChanged() on every rotation and no relaunch
        testRotation(NO_RELAUNCH_ACTIVITY, 1, 0, 1);
    }

    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation180_RegularActivity() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());
        assumeFalse("Skipping test: display cutout present, can't predict exact lifecycle",
                hasDisplayCutout());

        // Should receive nothing
        testRotation(TEST_ACTIVITY, 2, 0, 0);
    }

    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation180_NoRelaunchActivity() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());
        assumeFalse("Skipping test: display cutout present, can't predict exact lifecycle",
                hasDisplayCutout());

        // Should receive nothing
        testRotation(NO_RELAUNCH_ACTIVITY, 2, 0, 0);
    }

    /**
     * Test activity configuration changes for devices with cutout(s). Landscape and
     * reverse-landscape rotations should result in same screen space available for apps.
     */
    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation180RelaunchWithCutout() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());
        assumeTrue("Skipping test: no display cutout", hasDisplayCutout());

        testRotation180WithCutout(TEST_ACTIVITY, false /* canHandleConfigChange */);
    }

    @Test
    @RequireNotAutomotive(reason = "Automotive screens don't support rotation")
    public void testRotation180NoRelaunchWithCutout() {
        assumeTrue("Skipping test: no rotation support", supportsOrientationRequest());
        assumeTrue("Skipping test: no display cutout", hasDisplayCutout());

        testRotation180WithCutout(NO_RELAUNCH_ACTIVITY, true /* canHandleConfigChange */);
    }

    private void testRotation180WithCutout(ComponentName activityName,
            boolean canHandleConfigChange) {
        launchActivity(activityName);
        mWmState.computeState(activityName);

        final RotationSession rotationSession = createManagedRotationSession();
        final ActivityLifecycleCounts count1 = getLifecycleCountsForRotation(activityName,
                rotationSession, ROTATION_0 /* before */, ROTATION_180 /* after */,
                canHandleConfigChange);
        final int configChangeCount1 = count1.getCount(ActivityCallback.ON_CONFIGURATION_CHANGED);
        final int relaunchCount1 = count1.getCount(ActivityCallback.ON_CREATE);

        final ActivityLifecycleCounts count2 = getLifecycleCountsForRotation(activityName,
                rotationSession, ROTATION_90 /* before */, ROTATION_270 /* after */,
                canHandleConfigChange);
        final int configChangeCount2 = count2.getCount(ActivityCallback.ON_CONFIGURATION_CHANGED);
        final int relaunchCount2 = count2.getCount(ActivityCallback.ON_CREATE);

        final int configChange = configChangeCount1 + configChangeCount2;
        final int relaunch = relaunchCount1 + relaunchCount2;
        if (canHandleConfigChange) {
            assertWithMessage("There must be at most one 180 degree rotation that results in the"
                    + " same configuration.").that(configChange).isLessThan(2);
            assertEquals("There must be no relaunch during test", 0, relaunch);
            return;
        }

        // If the size change does not cross the threshold, the activity will receive
        // onConfigurationChanged instead of relaunching.
        assertWithMessage("There must be at most one 180 degree rotation that results in relaunch"
                + " or a configuration change.").that(relaunch + configChange).isLessThan(2);

        final boolean resize1 = configChangeCount1 + relaunchCount1 > 0;
        final boolean resize2 = configChangeCount2 + relaunchCount2 > 0;
        // There should at least one 180 rotation without resize.
        final boolean sameSize = !resize1 || !resize2;

        assertTrue("A device with cutout should have the same available screen space"
                + " in landscape and reverse-landscape", sameSize);
    }

    private void prepareRotation(ComponentName activityName, RotationSession session,
            int currentRotation, int initialRotation, boolean canHandleConfigChange) {
        final boolean is90DegreeDelta = Math.abs(currentRotation - initialRotation) % 2 != 0;
        if (is90DegreeDelta) {
            separateTestJournal();
        }
        session.set(initialRotation);
        if (is90DegreeDelta) {
            // Consume the changes of "before" rotation to make sure the activity is in a stable
            // state to apply "after" rotation.
            final ActivityCallback expectedCallback = canHandleConfigChange
                    ? ActivityCallback.ON_CONFIGURATION_CHANGED
                    : ActivityCallback.ON_CREATE;
            final CountSpec hasCallbacks = expectedCallback.hasCountGreaterThan(0);
            Condition.waitFor(
                    new ActivityLifecycleCounts(activityName)
                            .countWithRetry("activity rotated with 90 degree delta", hasCallbacks));
        }
    }

    private ActivityLifecycleCounts getLifecycleCountsForRotation(ComponentName activityName,
            RotationSession session, int before, int after, boolean canHandleConfigChange) {
        final int currentRotation = mWmState.getRotation();
        // The test verifies the events from "before" rotation to "after" rotation. So when
        // preparing "before" rotation, the changes should be consumed to avoid being mixed into
        // the result to verify.
        prepareRotation(activityName, session, currentRotation, before, canHandleConfigChange);
        separateTestJournal();
        session.set(after);
        mWmState.computeState(activityName);
        return new ActivityLifecycleCounts(activityName);
    }

    @Test
    public void testChangeFontScaleRelaunch() {
        // Should relaunch and receive no onConfigurationChanged()
        testChangeFontScale(FONT_SCALE_ACTIVITY, true /* relaunch */);
    }

    @Test
    public void testChangeFontScaleNoRelaunch() {
        // Should receive onConfigurationChanged() and no relaunch
        testChangeFontScale(FONT_SCALE_NO_RELAUNCH_ACTIVITY, false /* relaunch */);
    }

    private void testRotation(ComponentName activityName, int rotationStep, int numRelaunch,
            int numConfigChange) {
        launchActivity(activityName, WINDOWING_MODE_FULLSCREEN);
        mWmState.computeState(activityName);

        final int initialRotation = 4 - rotationStep;
        final RotationSession rotationSession = createManagedRotationSession();
        prepareRotation(activityName, rotationSession, mWmState.getRotation(), initialRotation,
                numConfigChange > 0);
        final int actualStackId =
                mWmState.getTaskByActivity(activityName).getRootTaskId();
        final int displayId = mWmState.getRootTask(actualStackId).mDisplayId;
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
            mWmState.computeState(activityName);
            // The configuration could be changed more than expected due to TaskBar recreation.
            new ActivityLifecycleCounts(activityName)
                    .assertCountWithRetry(
                            "relaunch or config changed",
                            ActivityCallback.ON_DESTROY.hasCountEquals(numRelaunch),
                            ActivityCallback.ON_CREATE.hasCountEquals(numRelaunch),
                            ActivityCallback.ON_CONFIGURATION_CHANGED.hasCountGreaterThanOrEquals(
                                    numConfigChange));
        }
    }

    private void testChangeFontScale(ComponentName activityName, boolean relaunch) {
        assumeRunNotOnVisibleBackgroundNonProfileUser(
                "Font scale cannot be modified by visible background users");
        final FontScaleSession fontScaleSession = createManagedFontScaleSession();
        fontScaleSession.set(1.0f);
        separateTestJournal();
        launchActivity(activityName);
        mWmState.computeState(activityName);

        final Bundle extras = TestJournalContainer.get(activityName).extras;
        if (!extras.containsKey(EXTRA_FONT_ACTIVITY_DPI)) {
            fail("No fontActivityDpi reported from activity " + activityName);
        }
        final int densityDpi = extras.getInt(EXTRA_FONT_ACTIVITY_DPI);

        final float fontScale = 0.85f;
        separateTestJournal();
        fontScaleSession.set(fontScale);
        mWmState.computeState(activityName);
        // The number of config changes could be greater than expected as there may have
        // other configuration change events triggered after font scale changed, such as
        // NavigationBar recreated.
        new ActivityLifecycleCounts(activityName)
                .assertCountWithRetry(
                        "relaunch or config changed",
                        ActivityCallback.ON_DESTROY.hasCountEquals(relaunch ? 1 : 0),
                        ActivityCallback.ON_CREATE.hasCountEquals(relaunch ? 1 : 0),
                        ActivityCallback.ON_RESUME.hasCountEquals(relaunch ? 1 : 0),
                        ActivityCallback.ON_CONFIGURATION_CHANGED.hasCountGreaterThanOrEquals(
                                relaunch ? 0 : 1));

        // Verify that the display metrics are updated, and therefore the text size is also
        // updated accordingly.
        waitForOrFail("reported fontPixelSize from " + activityName,
                () -> scaledPixelsToPixels(EXPECTED_FONT_SIZE_SP, fontScale, densityDpi)
                        == TestJournalContainer.get(activityName).extras.getInt(
                        EXTRA_FONT_PIXEL_SIZE));
    }

    // Calculate the scaled pixel size just like the device is supposed to.
    private static int scaledPixelsToPixels(float sp, float fontScale, int densityDpi) {
        final int DEFAULT_DENSITY = 160;
        float f = densityDpi * (1.0f / DEFAULT_DENSITY) * fontScale * sp;
        logAlways("scaledPixelsToPixels, f=" + f + ", densityDpi=" + densityDpi
                + ", fontScale=" + fontScale + ", sp=" + sp
                + ", Math.nextUp(f)=" + Math.nextUp(f));
        // Use the next up adjacent number to prevent precision loss of the float number.
        f = Math.nextUp(f);
        return (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
    }

    /**
     * Verifies if Activity receives {@link Activity#onConfigurationChanged(Configuration)} even if
     * the size change is small.
     */
    @Test
    public void testResizeWithoutCrossingSizeBucket() {
        assumeTrue(supportsSplitScreenMultiWindow());

        launchActivity(NO_RELAUNCH_ACTIVITY);

        waitAndAssertResumedActivity(NO_RELAUNCH_ACTIVITY, "Activity must be resumed");
        final int taskId = mWmState.getTaskByActivity(NO_RELAUNCH_ACTIVITY).getTaskId();

        separateTestJournal();
        mTaskOrganizer.putTaskInSplitPrimary(taskId);

        // It is expected a config change callback because the Activity goes to split mode.
        assertRelaunchOrConfigChanged(NO_RELAUNCH_ACTIVITY, 0 /* numRelaunch */,
                1 /* numConfigChange */);

        // Resize task a little and verify if the Activity still receive config changes.
        separateTestJournal();
        final Rect taskBounds = mTaskOrganizer.getPrimaryTaskBounds();
        taskBounds.set(taskBounds.left, taskBounds.top, taskBounds.right, taskBounds.bottom + 10);
        mTaskOrganizer.setRootPrimaryTaskBounds(taskBounds);

        mWmState.waitForValidState(NO_RELAUNCH_ACTIVITY);

        assertRelaunchOrConfigChanged(NO_RELAUNCH_ACTIVITY, 0 /* numRelaunch */,
                1 /* numConfigChange */);
    }

    /**
     * Verifies that an activity without "keyboard" or "keyboardHidden" defined in the
     * {@code android:recreateOnConfigChanges} attribute is not relaunched and receives the
     * {@link android.app.Activity#onConfigurationChanged} callback instead when a keyboard
     * configuration change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testKeyboardConfigChange_noRelaunch() {
        testKeyboardConfigChange(TEST_ACTIVITY, 0 /* numRelaunch */, 1 /* numConfigChange */);
    }

    /**
     * Verifies that an activity with "keyboard" or "keyboardHidden" explicitly defined in the
     * {@code android:recreateOnConfigChanges} attribute is relaunched and does not receive the
     * {@link android.app.Activity#onConfigurationChanged} callback when a keyboard configuration
     * change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testKeyboardConfigChange_relaunch() {
        testKeyboardConfigChange(RECREATE_ON_KEYBOARD_CHANGE_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
    }

    /**
     * Verifies that if the app provides resources for a specific keyboard configuration, the
     * activity is relaunched and does not receive the
     * {@link android.app.Activity#onConfigurationChanged} callback when a keyboard configuration
     * change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testKeyboardConfigChange_keyboardResources_relaunch() {
        testKeyboardConfigChange(KEYBOARD_RESOURCES_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
    }

    private void testKeyboardConfigChange(ComponentName activityName,  int numRelaunch,
            int numConfigChange) {
        // TODO: Disable any keyboard device and remove this.
        assumeTrue(hasNoKeyboardDevice());

        launchActivity(activityName);
        waitAndAssertResumedActivity(activityName, "Activity must be resumed");
        separateTestJournal();

        // Check the activity state when connect a new keyboard device.
        try (UinputKeyboard keyboardDevice = new UinputKeyboard(
                InstrumentationRegistry.getInstrumentation(),
                List.of("KEY_Q", "KEY_W"),
                0xabcd /* productId */)) {
            assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
            separateTestJournal();
        }

        // Check the activity state after automatically disconnecting the new keyboard device.
        assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
    }

    private boolean hasNoKeyboardDevice() {
        return hasNoInputDeviceMatching(device ->
                device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC);
    }

    private boolean hasNoInputDeviceMatching(Predicate<InputDevice> condition) {
        InputManager inputManager = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(InputManager.class);
        assertNotNull(inputManager);

        final int[] inputDeviceIds = inputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
            if (inputDevice != null && inputDevice.isEnabled() && !inputDevice.isVirtual()) {
                if (condition.test(inputDevice)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Verifies that an activity without "navigation" defined in the
     * {@code android:recreateOnConfigChanges} attribute is not relaunched and receives the
     * {@link android.app.Activity#onConfigurationChanged} callback instead when a navigation
     * configuration change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testNavigationConfigChange_noRelaunch() {
        testNavigationConfigChange(TEST_ACTIVITY, 0 /* numRelaunch */, 1 /* numConfigChange */);
    }

    /**
     * Verifies that an activity with "navigation" explicitly defined in the
     * {@code android:recreateOnConfigChanges} attribute is relaunched and does not receive the
     * {@link android.app.Activity#onConfigurationChanged} callback when a navigation configuration
     * change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testNavigationConfigChange_relaunch() {
        testNavigationConfigChange(RECREATE_ON_NAVIGATION_CHANGE_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
    }

    /**
     * Verifies that if the app provides resources for a specific navigation configuration, the
     * activity is relaunched and does not receive the
     * {@link android.app.Activity#onConfigurationChanged} callback when a navigation configuration
     * change occurs.
     */
    @Test
    @ApiTest(apis = {"android.R.attr#configChanges", "android.R.attr#recreateOnConfigChanges"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LESS_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    @EnableCompatChanges(ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)
    public void testNavigationConfigChange_navigationResources_relaunch() {
        testNavigationConfigChange(NAVIGATION_RESOURCES_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
    }

    private void testNavigationConfigChange(ComponentName activityName,  int numRelaunch,
            int numConfigChange) {
        assumeTrue(hasNoNavigationDevice());

        launchActivity(activityName);
        waitAndAssertResumedActivity(activityName, "Activity must be resumed");
        separateTestJournal();

        // Check the activity state when connect a new dpad device.
        try (UinputDevice dpadDevice = new UinputDevice(
                InstrumentationRegistry.getInstrumentation(),
                InputDevice.SOURCE_DPAD, createDeviceRegisterCommand(), null)) {
            assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
            separateTestJournal();
        }

        // Check the activity state after automatically disconnecting the new dpad device.
        assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
    }

    private boolean hasNoNavigationDevice() {
        return hasNoInputDeviceMatching(device ->
                device.getSources() == InputDevice.SOURCE_DPAD
                        || device.getSources() == InputDevice.SOURCE_TRACKBALL);
    }

    private UinputRegisterCommand createDeviceRegisterCommand() {
        List<ConfigurationItem> configurationItems = Arrays.asList(
                new ConfigurationItem(
                        "UI_SET_EVBIT",
                        List.of("EV_KEY")),
                new ConfigurationItem(
                        "UI_SET_KEYBIT",
                        List.of("KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_SELECT"))
        );

        return new UinputRegisterCommand(
                100 /* id */,
                "Virtual Dpad Device (Test)" /* name */,
                0x18d1 /* vid */,
                0xabcd /* pid */,
                "usb" /* bus */,
                "usb:1" /* port */,
                configurationItems,
                Map.of(),
                null /* ffEffectsMax */
        );
    }
}
