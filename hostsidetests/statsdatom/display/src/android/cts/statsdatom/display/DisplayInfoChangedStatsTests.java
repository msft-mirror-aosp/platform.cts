/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.cts.statsdatom.display;

import static android.cts.statsdatom.display.DisplayTestUtils.DISPLAY_TEST_APK;
import static android.cts.statsdatom.display.DisplayTestUtils.DISPLAY_TEST_PKG;
import static android.cts.statsdatom.display.DisplayTestUtils.TEST_CLASS_DISPLAY_EVENT;
import static android.cts.statsdatom.display.DisplayTestUtils.TIMEOUT_MS;
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentAccelerometerRotationMode;
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentBrightnessLevel;
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentBrightnessMode;
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentUserRotationMode;
import static android.cts.statsdatom.lib.DeviceUtils.setAccelerometerRotationMode;
import static android.cts.statsdatom.lib.DeviceUtils.setAutoBrightnessMode;
import static android.cts.statsdatom.lib.DeviceUtils.setScreenBrightnessLevel;
import static android.cts.statsdatom.lib.DeviceUtils.setUserRotationMode;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.display.EventSource;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.PollingCheck;
import com.android.os.StatsLog;
import com.android.os.display.DisplayExtensionAtoms;
import com.android.os.display.DisplayInfoChanged;
import com.android.server.display.feature.flags.Flags;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DisplayInfoChangedStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.turnScreenOn(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), DISPLAY_TEST_APK, DISPLAY_TEST_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), DISPLAY_TEST_PKG);
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS)
    public void testDisplayEventBrightnessReported() throws Exception {
        // Only run if we have a valid ambient light sensor.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkValidLightSensor")) {
            return;
        }

        // Don't run if there is no app that has permission to access slider usage.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkBrightnessSliderPermission")) {
            return;
        }

        DeviceUtils.turnScreenOn(getDevice());
        // Upload config to collect DisplayInfoChanged event
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_INFO_CHANGED_FIELD_NUMBER);

        int brightnessLevelBeforeTest = getCurrentBrightnessLevel(getDevice());
        int brightnessModeBeforeTest = getCurrentBrightnessMode(getDevice());
        setAutoBrightnessMode(getDevice(), 0);
        PollingCheck.check(
                "Brightness mode did not change to manual.",
                TIMEOUT_MS,
                () -> getCurrentBrightnessMode(getDevice()) == 0);

        // Make sure we don't go out of the [0 - 255] range
        int newBrightness =
                (brightnessLevelBeforeTest < 100
                        ? brightnessLevelBeforeTest + 10
                        : brightnessLevelBeforeTest - 10);

        // Make change to brightness (trigger event)
        setScreenBrightnessLevel(getDevice(), newBrightness);
        PollingCheck.check(
                "Brightness level did not change.",
                TIMEOUT_MS,
                () -> getCurrentBrightnessLevel(getDevice()) == newBrightness);

        // Assert brightness event has not been recorded
        assertDisplayEvent(
                EnumSet.of(DisplayInfoGroupForTest.COLOR_AND_BRIGHTNESS), null, 0, false);

        // Reset brightness to initial level and mode
        setScreenBrightnessLevel(getDevice(), brightnessLevelBeforeTest);
        PollingCheck.check(
                "Brightness level did not turn return to previous values.",
                TIMEOUT_MS,
                () -> getCurrentBrightnessLevel(getDevice()) == brightnessLevelBeforeTest);
        setAutoBrightnessMode(getDevice(), brightnessModeBeforeTest);
        PollingCheck.check(
                "Brightness mode did not turn return to previous values.",
                TIMEOUT_MS,
                () -> getCurrentBrightnessMode(getDevice()) == brightnessModeBeforeTest);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS)
    public void testDisplayEventRotationReported() throws Exception {
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkLandscapeOrientationSupported")) {
            return;
        }

        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkPortraitOrientationSupported")) {
            return;
        }

        // Upload config to collect DisplayInfoChanged event
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_INFO_CHANGED_FIELD_NUMBER);

        int accelerometerRotationBeforeTest = getCurrentAccelerometerRotationMode(getDevice());

        // Prevent the physical orientation of the device from interfering.
        setAccelerometerRotationMode(getDevice(), /* mode= */ 0);
        PollingCheck.check(
                "Accelerometer rotation mode did not turn off.",
                TIMEOUT_MS,
                () -> getCurrentAccelerometerRotationMode(getDevice()) == 0);

        int userRotationBeforeTest = getCurrentUserRotationMode(getDevice());
        int rotationPortrait = 0; // Surface.ROTATION_0
        int rotationLandscape = 1; // Surface.ROTATION_90

        try (AutoCloseable ignored =
                DeviceUtils.withActivity(
                        getDevice(),
                        DeviceUtils.STATSD_ATOM_TEST_PKG,
                        "StatsdCtsForegroundActivity",
                        "action",
                        "action.show_application_overlay")) {
            int newRotation =
                    userRotationBeforeTest == rotationLandscape
                            ? rotationPortrait
                            : rotationLandscape;
            setUserRotationMode(getDevice(), newRotation);

            PollingCheck.check(
                    "Rotation did not change.",
                    TIMEOUT_MS,
                    () -> getCurrentUserRotationMode(getDevice()) == newRotation);
        }

        // Assert that at least one rotation event was logged.
        long eventPollingTimeoutMs = TIMEOUT_MS * 2;
        PollingCheck.check(
                "Display rotation event not logged within timeout.",
                eventPollingTimeoutMs,
                () -> {
                    try {
                        assertDisplayEvent(
                                EnumSet.of(
                                        DisplayInfoGroupForTest.DIMENSIONS_AND_SHAPES,
                                        DisplayInfoGroupForTest.ORIENTATION_AND_ROTATION),
                                EventSource.EVENT_SOURCE_WINDOW_MANAGER,
                                /* expectedEventCount= */ 1,
                                /* greaterThan= */ true);
                        return true; // Assertion passed, event found
                    } catch (AssertionError e) {
                        return false; // Assertion failed, event not yet found
                    }
                });

        // Restore the original rotation setting
        setUserRotationMode(getDevice(), userRotationBeforeTest);
        PollingCheck.check(
                "Failed to restore original rotation mode.",
                TIMEOUT_MS,
                () -> getCurrentUserRotationMode(getDevice()) == userRotationBeforeTest);

        setAccelerometerRotationMode(getDevice(), accelerometerRotationBeforeTest);
        PollingCheck.check(
                "Failed to restore original accelerometer mode.",
                TIMEOUT_MS,
                () ->
                        getCurrentAccelerometerRotationMode(getDevice())
                                == accelerometerRotationBeforeTest);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS,
        Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS
    })
    public void testDisplayEventStateReported() throws Exception {
        // Upload config to collect DisplayInfoChanged event
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DISPLAY_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_INFO_CHANGED_FIELD_NUMBER);

        // This test changes display state 2 times.
        runDeviceTests(DISPLAY_TEST_PKG, TEST_CLASS_DISPLAY_EVENT, "testDisplayStateChangedEvent");
        assertDisplayEvent(
                EnumSet.of(DisplayInfoGroupForTest.STATE),
                EventSource.EVENT_SOURCE_DISPLAY_MANAGER,
                2,
                true);
    }

    private void assertDisplayEvent(
            EnumSet<DisplayInfoGroupForTest> groups,
            EventSource eventSource,
            int expectedEventCount,
            boolean greaterThan)
            throws Exception {
        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        DisplayExtensionAtoms.registerAllExtensions(registry);

        final List<DisplayInfoChanged> events =
                ReportUtils.getEventMetricDataList(getDevice(), registry).stream()
                        .map(this::getDisplayInfoChanged)
                        .filter(x -> x.getNumDisplayInfoGroupsChanged() >= groups.size())
                        .filter(x -> checkDisplayInfoGroupFieldsAreCorrect(x, groups))
                        .filter(x -> eventSource == null || x.getEventSource().equals(eventSource))
                        .toList();

        if (greaterThan) {
            assertTrue(events.size() >= expectedEventCount);
        } else {
            assertTrue(events.size() <= expectedEventCount);
        }
    }

    private boolean checkDisplayInfoGroupFieldsAreCorrect(
            DisplayInfoChanged info, EnumSet<DisplayInfoGroupForTest> groups) {
        // We expect that all the groups in the enum set have changed
        // (therefore their fields should be 1).
        for (DisplayInfoGroupForTest group : groups) {
            // Get the actual value (of the event that happened) and compare it to the expected
            // value
            if (getActualDisplayValue(info, group) != 1) {
                return false;
            }
        }
        return true;
    }

    // Private helper method to get the value for a specific group
    private int getActualDisplayValue(DisplayInfoChanged info, DisplayInfoGroupForTest group) {
        return switch (group) {
            case BASIC_PROPERTIES -> info.getBasicPropertiesChanged();
            case ORIENTATION_AND_ROTATION -> info.getOrientationAndRotationChanged();
            case REFRESH_RATE_AND_MODE -> info.getRefreshRateAndModeChanged();
            case DIMENSIONS_AND_SHAPES -> info.getDimensionsAndShapesChanged();
            case COLOR_AND_BRIGHTNESS -> info.getColorAndBrightnessChanged();
            case STATE -> info.getStateChanged();
        };
    }

    private DisplayInfoChanged getDisplayInfoChanged(StatsLog.EventMetricData data) {
        return data.getAtom().getExtension(DisplayExtensionAtoms.displayInfoChanged);
    }

    /**
     * Enum representing different groups of display information that can change. This is a copy of
     * {@link android.view.DisplayInfo.DisplayInfoGroup} for testing purposes.
     */
    enum DisplayInfoGroupForTest {
        BASIC_PROPERTIES,
        DIMENSIONS_AND_SHAPES,
        ORIENTATION_AND_ROTATION,
        REFRESH_RATE_AND_MODE,
        COLOR_AND_BRIGHTNESS,
        STATE
    }
}
