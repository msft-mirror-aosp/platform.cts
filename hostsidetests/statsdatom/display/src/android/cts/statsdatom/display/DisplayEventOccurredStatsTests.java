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
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentBrightnessLevel;
import static android.cts.statsdatom.lib.DeviceUtils.getCurrentBrightnessMode;
import static android.cts.statsdatom.lib.DeviceUtils.setAutoBrightnessMode;
import static android.cts.statsdatom.lib.DeviceUtils.setScreenBrightnessLevel;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.display.EventType;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.PollingCheck;
import com.android.os.StatsLog;
import com.android.os.display.DisplayEventCallbackOccurred;
import com.android.os.display.DisplayExtensionAtoms;
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

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DisplayEventOccurredStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

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
    public void testDisplayEventBrightnessReportedButNoListenerRegistered() throws Exception {
        // Only run if we have a valid ambient light sensor.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkValidLightSensor")) {
            return;
        }

        // Don't run if there is no app that has permission to access slider usage.
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkBrightnessSliderPermission")) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_EVENT_CALLBACK_OCCURRED_FIELD_NUMBER);

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

        // Assert brightness event has been recorded
        long eventPollingTimeoutMs = TIMEOUT_MS * 2;
        PollingCheck.check(
                "Display rotation event not logged within timeout.",
                eventPollingTimeoutMs,
                () -> {
                    try {
                        assertDisplayEvent(EventType.TYPE_DISPLAY_BRIGHTNESS_CHANGED, 1, 0);
                        return true; // Assertion passed, event found
                    } catch (AssertionError e) {
                        return false; // Assertion failed, event not yet found
                    }
                });

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
    public void testDisplayEventStateReported() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DISPLAY_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_EVENT_CALLBACK_OCCURRED_FIELD_NUMBER);
        // This test changes display state 2 times.
        runDeviceTests(DISPLAY_TEST_PKG, TEST_CLASS_DISPLAY_EVENT, "testDisplayStateChangedEvent");
        assertDisplayEvent(EventType.TYPE_DISPLAY_STATE_CHANGED, 2, 1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS)
    public void testDisplayRefreshRateReported() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DISPLAY_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_EVENT_CALLBACK_OCCURRED_FIELD_NUMBER);

        runDeviceTests(
                DISPLAY_TEST_PKG, TEST_CLASS_DISPLAY_EVENT, "testDisplayRefreshRateChangedEvent");
        assertDisplayEvent(EventType.TYPE_DISPLAY_REFRESH_RATE_CHANGED, 1, 1);
    }

    private void assertDisplayEvent(
            EventType eventType, int expectedEventCount, int expectedClientCount) throws Exception {
        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        DisplayExtensionAtoms.registerAllExtensions(registry);

        final List<DisplayEventCallbackOccurred> events =
                ReportUtils.getEventMetricDataList(getDevice(), registry).stream()
                        .map(this::getDisplayEventCallbackOccurred)
                        .filter(x -> x.getEventType().equals(eventType))
                        .peek(x -> assertTrue(x.getClientCount() >= expectedClientCount))
                        .peek(x -> assertEquals(0, x.getUidCount())) // UID list is not being logged
                        .toList();

        assertTrue(events.size() >= expectedEventCount);
    }

    private DisplayEventCallbackOccurred getDisplayEventCallbackOccurred(
            StatsLog.EventMetricData data) {
        return data.getAtom().getExtension(DisplayExtensionAtoms.displayEventCallbackOccurred);
    }
}
