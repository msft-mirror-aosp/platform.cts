/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.cts.statsdatom.appcompatstate;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.LetterboxPositionChanged;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test is for making sure that App Compat state changes log the desired atoms.
 *
 * <p>Build/Install/Run:
 * atest CtsStatsdAtomHostTestCases:ReachabilityStateStatsTests
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class ReachabilityStateStatsTests extends DeviceTestCase implements IBuildReceiver {

    private static final String WM_GET_LETTERBOX_STYLE =
            "wm get-letterbox-style";
    private static final String WM_SET_IGNORE_ORIENTATION_REQUEST =
            "wm set-ignore-orientation-request ";
    private static final String WM_GET_IGNORE_ORIENTATION_REQUEST =
            "wm get-ignore-orientation-request";
    private static final String DUMPSYS_ACTIVITY_ACTIVITIES = "dumpsys activity activities";
    private static final Pattern IGNORE_ORIENTATION_REQUEST_PATTERN =
            Pattern.compile("ignoreOrientationRequest (true|false) for displayId=\\d+");
    private static final Pattern HORIZONTAL_REACHABILITY_PATTERN =
            Pattern.compile("Is horizontal reachability enabled: (true|false)");
    private static final Pattern VERTICAL_REACHABILITY_PATTERN =
            Pattern.compile("Is vertical reachability enabled: (true|false)");
    private static final String TEST_ACTIVITY = "StatsdCtsForegroundActivity";

    // Regex to find the ActivityRecord and extract windowing mode
    private static final Pattern WINDOWING_MODE_PATTERN =
            Pattern.compile(
                    "\\* Hist\\s+#\\d+:\\s+ActivityRecord\\{[^\\}]*?\\s+"
                            + "\\b"
                            + DeviceUtils.STATSD_ATOM_TEST_PKG.replace(".", "\\.")
                            + "/\\."
                            + TEST_ACTIVITY
                            + "\\b[^\\}]*?\\s+"
                            + ".*?mWindowingMode=(\\S+)",
                    Pattern.DOTALL);
    private static final String WINDOWING_MODE_FULLSCREEN = "fullscreen";
    private static final String KEY_ACTION = "action";
    private static final String ACTION_LONG_SLEEP_WHILE_TOP = "action.long_sleep_top";

    private IBuildInfo mCtsBuild;
    private boolean mInitialIgnoreOrientationRequest;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        final Matcher matcher = IGNORE_ORIENTATION_REQUEST_PATTERN.matcher(
                getDevice().executeShellCommand(WM_GET_IGNORE_ORIENTATION_REQUEST));
        assertTrue("get-ignore-orientation-request should match pattern",
                matcher.find());
        mInitialIgnoreOrientationRequest = Boolean.parseBoolean(matcher.group(1));

        getDevice().executeShellCommand(WM_SET_IGNORE_ORIENTATION_REQUEST + "true");
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.LETTERBOX_POSITION_CHANGED_FIELD_NUMBER, /*uidInAttributionChain=*/
                false);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().executeShellCommand(
                WM_SET_IGNORE_ORIENTATION_REQUEST + mInitialIgnoreOrientationRequest);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testHorizontalReachability() throws Exception {
        if (isReachabilityDisabled(HORIZONTAL_REACHABILITY_PATTERN)
                || hasUnsupportedWindowingMode()) {
            return;
        }
        // Run an local test (AppCompatTests#testHorizontalReachability) to
        // generate device interactions that cause LetterboxPositionChanged atoms to be logged.
        final String testClass = ".appcompat.AppCompatTests";
        final String testMethod = "testHorizontalReachability";

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<LetterboxPositionChanged.PositionChange> realPositionChanges = new ArrayList<>();
        for (StatsLog.EventMetricData d : data) {
            realPositionChanges.add(d.getAtom().getLetterboxPositionChanged().getPositionChange());
        }

        assertThat(realPositionChanges)
                .containsAnyIn(
                        Arrays.asList(
                                LetterboxPositionChanged.PositionChange.CENTER_TO_RIGHT,
                                LetterboxPositionChanged.PositionChange.RIGHT_TO_CENTER,
                                LetterboxPositionChanged.PositionChange.CENTER_TO_LEFT,
                                LetterboxPositionChanged.PositionChange.LEFT_TO_CENTER));
    }

    public void testVerticalReachability() throws Exception {
        if (isReachabilityDisabled(VERTICAL_REACHABILITY_PATTERN)
                || hasUnsupportedWindowingMode()) {
            return;
        }
        // Run an local test (AppCompatTests#testVerticalReachability) to
        // generate device interactions that cause LetterboxPositionChanged atoms to be logged.
        final String testClass = ".appcompat.AppCompatTests";
        final String testMethod = "testVerticalReachability";

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<LetterboxPositionChanged.PositionChange> realPositionChanges = new ArrayList<>();
        for (StatsLog.EventMetricData d : data) {
            realPositionChanges.add(d.getAtom().getLetterboxPositionChanged().getPositionChange());
        }

        assertThat(realPositionChanges)
                .containsAnyIn(
                        Arrays.asList(
                                LetterboxPositionChanged.PositionChange.CENTER_TO_TOP,
                                LetterboxPositionChanged.PositionChange.TOP_TO_CENTER,
                                LetterboxPositionChanged.PositionChange.CENTER_TO_BOTTOM,
                                LetterboxPositionChanged.PositionChange.BOTTOM_TO_CENTER));
    }

    private boolean isReachabilityDisabled(Pattern pattern) throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand(WM_GET_LETTERBOX_STYLE);
        final Matcher matcher = pattern.matcher(output);
        assertTrue(matcher.find());
        return !Boolean.parseBoolean(matcher.group(1));
    }

    private boolean hasUnsupportedWindowingMode() throws Exception {
        // Reachability is disabled when the system doesn't use full screen windowing, so check if
        // the test activity will be launched in a fullscreen window
        try (AutoCloseable a =
                DeviceUtils.withActivity(
                        getDevice(),
                        DeviceUtils.STATSD_ATOM_TEST_PKG,
                        TEST_ACTIVITY,
                        /* actionKey= */ KEY_ACTION,
                        /* actionValue= */ ACTION_LONG_SLEEP_WHILE_TOP)) {
            // Wait for the activity to come up
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

            String output = getDevice().executeShellCommand(DUMPSYS_ACTIVITY_ACTIVITIES);
            Matcher matcher = WINDOWING_MODE_PATTERN.matcher(output);

            while (matcher.find()) {
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (!matcher.group(i).equals(WINDOWING_MODE_FULLSCREEN)) {
                        a.close();
                        return true;
                    }
                }
            }
            a.close();
            return false;
        }
    }
}
