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

import static android.cts.statsdatom.display.DisplayTestUtils.getCurrentBrightnessLevel;
import static android.cts.statsdatom.display.DisplayTestUtils.getCurrentBrightnessMode;
import static android.cts.statsdatom.display.DisplayTestUtils.setAutoBrightnessMode;
import static android.cts.statsdatom.display.DisplayTestUtils.setScreenBrightnessLevel;

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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DisplayEventOccurredStatsTests extends BaseHostJUnit4Test {

    private static final String DISPLAY_TEST_PKG = "android.display.cts";
    private static final String TEST_CLASS_DISPLAY_EVENT = "android.display.cts.DisplayEventTest";
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                DisplayExtensionAtoms.DISPLAY_EVENT_CALLBACK_OCCURRED_FIELD_NUMBER);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS)
    public void testDisplayEventBrightnessReportedButNoListenerRegistered() throws Exception {
        int brightnessLevelBeforeTest = getCurrentBrightnessLevel(getDevice());
        int brightnessModeBeforeTest = getCurrentBrightnessMode(getDevice());
        setAutoBrightnessMode(getDevice(), 0);
        PollingCheck.check(
                "Brightness mode did not turn off.",
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
        assertDisplayEvent(EventType.TYPE_DISPLAY_BRIGHTNESS_CHANGED, 1, false);

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
        // This test changes display state 2 times.
        runDeviceTests(DISPLAY_TEST_PKG, TEST_CLASS_DISPLAY_EVENT, "testDisplayStateChangedEvent");
        assertDisplayEvent(EventType.TYPE_DISPLAY_STATE_CHANGED, 2, true);
    }

    private void assertDisplayEvent(
            EventType eventType, int expectedEventCount, boolean isListenerNotified)
            throws Exception {
        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        DisplayExtensionAtoms.registerAllExtensions(registry);

        int uid = DeviceUtils.getAppUid(getDevice(), DISPLAY_TEST_PKG);

        final List<DisplayEventCallbackOccurred> events =
                ReportUtils.getEventMetricDataList(getDevice(), registry).stream()
                        .map(this::getDisplayEventCallbackOccurred)
                        .filter(x -> x.getEventType().equals(eventType))
                        .peek(x -> assertEquals(isListenerNotified, x.getUidList().contains(uid)))
                        .toList();

        assertTrue(events.size() >= expectedEventCount);
    }

    private DisplayEventCallbackOccurred getDisplayEventCallbackOccurred(
            StatsLog.EventMetricData data) {
        return data.getAtom().getExtension(DisplayExtensionAtoms.displayEventCallbackOccurred);
    }
}
