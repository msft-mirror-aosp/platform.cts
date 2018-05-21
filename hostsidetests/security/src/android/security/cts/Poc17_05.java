/**
 * Copyright (C) 2017 The Android Open Source Project
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc17_05 extends SecurityTestCase {
    /**
     * b/33863909
     */
    @SecurityTest
    public void testPocCve_2016_10288() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/flashLED/strobe")) {
            AdbUtils.runPocNoOutput("CVE-2016-10288", getDevice(), 60);
        }
     }

    /**
     * b/34112914
     */
    @SecurityTest
    public void testPocCve_2017_0465() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/adsprpc-smd")) {
            AdbUtils.runPocNoOutput("CVE-2017-0465", getDevice(), 60);
        }
    }

    /**
     * b/33899710
     */
    @SecurityTest
    public void testPocCve_2016_10289() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/qcrypto/stats-1")) {
            AdbUtils.runPocNoOutput("CVE-2016-10289", getDevice(), 60);
        }
     }

    /**
     *  b/33898330
     */
    @SecurityTest
    public void testPocCve_2016_10290() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/sys/kernel/debug/rmt_storage/info")) {
          AdbUtils.runPocNoOutput("CVE-2016-10290", getDevice(), 60);
        }
    }

    /**
     *  b/34327795
     */
    @SecurityTest
    public void testPocCVE_2017_0624() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/proc/debugdriver/driverdump")) {
            AdbUtils.runPoc("CVE-2017-0624", getDevice(), 60);
        }
    }

    /**
     *  b/32094986
     */
    @SecurityTest
    public void testPocCVE_2016_10283() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-10283", getDevice(), 60);
        // CTS begins the next test before device finishes rebooting,
        // sleep to allow time for device to reboot.
        Thread.sleep(60000);
    }
}
