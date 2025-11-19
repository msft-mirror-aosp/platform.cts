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

package android.host.systemui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.ProcessInfo;
import com.android.tradefed.util.RunUtil;

import java.util.HashSet;
import java.util.Set;

public class ClockPluginTest extends DeviceTestCase {
    private static final String SYSUI_NAME = "com.android.systemui";
    private static final String SET_CLOCK_CMD =
            "settings put secure lock_screen_custom_clock_face '''{\"clockId\":\"%s\"}'''";
    private static final String SET_PROP_CMD = "setprop debug.sysui.plugins %b";
    private static final String RESTART_SYSUI_CMD = "am crash " + SYSUI_NAME;
    private static final String PID_SYSUI_CMD = "pidof " + SYSUI_NAME;
    private static final String DEFAULT_CLOCK_ID = "DEFAULT";
    private static final int SETUP_ATTEMPTS = 10;
    private static final int SETUP_WAIT_TIME = 500;
    private static final int TEST_WAIT_TIME = 3000;

    private Set<Integer> mSysUIPids;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setClock(DEFAULT_CLOCK_ID);
        setPluginDebugMode(true);

        for (int i = 0; i < SETUP_ATTEMPTS; i++) {
            RunUtil.getDefault().sleep(SETUP_WAIT_TIME);
            mSysUIPids = getSysuiPids();
            if (mSysUIPids != null) return;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        setPluginDebugMode(false);
        setClock(DEFAULT_CLOCK_ID);
        RunUtil.getDefault().sleep(SETUP_WAIT_TIME);
        mSysUIPids = null;
    }

    private Set<Integer> getSysuiPids() throws Exception {
        ProcessInfo sysUI = getDevice().getProcessByName(SYSUI_NAME);
        if (sysUI != null) return Set.of(sysUI.getPid());

        try {
            String output = getDevice().executeShellCommand(PID_SYSUI_CMD).trim();
            if (output.isEmpty()) return null;

            String[] pids = output.split(" ");
            Set<Integer> result = new HashSet<Integer>(pids.length);
            for (int i = 0; i < pids.length; i++) {
                result.add(Integer.parseInt(output));
            }
            return result;
        } catch (NumberFormatException e) {
            CLog.e(e);
            return null;
        }
    }

    private void setPluginDebugMode(boolean isEnabled) throws DeviceNotAvailableException {
        getDevice().executeShellCommand(String.format(SET_PROP_CMD, isEnabled));
        getDevice().executeShellCommand(RESTART_SYSUI_CMD);
    }

    private void setClock(String clockId) throws DeviceNotAvailableException {
        getDevice().executeShellCommand(String.format(SET_CLOCK_CMD, clockId));
    }

    private void assertSysuiUnchanged() throws Exception {
        Set<Integer> sysUIPids = getSysuiPids();
        assertThat(sysUIPids).isNotNull();
        assertThat(sysUIPids).isEqualTo(mSysUIPids);
    }

    private void testLoadClockPlugin(String clockId) throws Exception {
        // Devices without system ui can skip this test
        if (mSysUIPids == null) return;
        assertSysuiUnchanged();

        setClock(clockId);
        RunUtil.getDefault().sleep(TEST_WAIT_TIME);
        assertSysuiUnchanged();
    }

    public void testBigNum() throws Exception {
        testLoadClockPlugin("ANALOG_CLOCK_BIGNUM");
    }

    public void testCalligraphy() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_CALLIGRAPHY");
    }

    public void testGrowth() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_GROWTH");
    }

    public void testInflate() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_INFLATE");
    }

    public void testMetro() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_METRO");
    }

    public void testNumberOverlap() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_NUMBEROVERLAP");
    }

    public void testWeather() throws Exception {
        testLoadClockPlugin("DIGITAL_CLOCK_WEATHER");
    }
}
