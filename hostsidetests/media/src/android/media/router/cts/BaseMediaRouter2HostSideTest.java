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
package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

public class BaseMediaRouter2HostSideTest extends BaseHostJUnit4Test {
    /** The maximum period of time to wait for a scan request to take effect, in milliseconds. */
    protected static final long WAIT_MS_SCAN_PROPAGATION = 3000;

    protected boolean forceStopAndWaitForRunningStatus(String packageName) throws Throwable {
        getDevice().executeShellCommand("am force-stop " + packageName);
        return waitForPackageRunningStatus(
                MEDIA_ROUTER_TEST_PACKAGE, /* isPackageExpectedToRun= */ false);
    }

    /**
     * Blocks execution until the package with the given name has the given running status.
     *
     * @param packageName The name of the package to check the running status for.
     * @param isPackageExpectedToRun True if the expected running status is "running", and false if
     *     the expected running status is "not running".
     */
    protected boolean waitForPackageRunningStatus(
            String packageName, boolean isPackageExpectedToRun) throws Throwable {
        long start = System.currentTimeMillis();
        while (isPackageRunning(packageName) != isPackageExpectedToRun) {
            if (System.currentTimeMillis() - start > WAIT_MS_SCAN_PROPAGATION) {
                return false;
            }
            Thread.sleep(/* millis= */ 200); // Wait a bit before we call adb again.
        }
        return true;
    }

    protected boolean isPackageRunning(String packageName) throws DeviceNotAvailableException {
        return !getDevice().executeShellCommand("pidof " + packageName).isEmpty();
    }
}
