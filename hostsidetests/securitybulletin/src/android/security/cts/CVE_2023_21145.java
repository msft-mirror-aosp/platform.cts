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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;
import static com.android.sts.common.DumpsysUtils.getParsedDumpsys;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.ProcessUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21145 extends NonRootSecurityTestCase {
    private final int mNoPidFound = -1; /* Default pid */

    @AsbSecurityTest(cveBugId = 265293293)
    @Test
    public void testPocCVE_2023_21145() {
        try {
            // Check if DUT supports PIP mode
            ITestDevice device = getDevice();
            assume().withMessage("Device does not support picture-in-picture mode")
                    .that(device.hasFeature("android.software.picture_in_picture"))
                    .isTrue();

            // Install poc and start PipActivity to invoke the vulnerability
            installPackage("CVE-2023-21145.apk");
            String pocPkg = "android.security.cts.CVE_2023_21145";
            device.executeShellCommand("am start-activity " + pocPkg + "/.PipActivity");

            // Wait for the PoC to start
            final int initialPid = waitAndGetPid(device, mNoPidFound /* initial pid */);
            assume().withMessage("PoC process did not start")
                    .that(initialPid)
                    .isNotEqualTo(mNoPidFound);

            // Wait for the PoC to be killed or restart
            final int latestPid = waitAndGetPid(device, initialPid);
            assume().withMessage("PoC process did not die")
                    .that(latestPid)
                    .isNotEqualTo(initialPid);

            // Vulnerability occurs when main window is null, thus interaction with
            // the pip window is not allowed.
            // Otherwise, interaction with pip window is possible.
            if (checkIfPipWindowCanBeShifted(device)) {
                return;
            }

            // Without fix, the process restarts with new pid
            assertWithMessage("Device is vulnerable to b/265293293 !!")
                    .that(waitAndGetPid(device, initialPid))
                    .isEqualTo(mNoPidFound);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private int waitAndGetPid(ITestDevice device, int initialPid) throws Exception {
        final long timeout = 10_000L;
        final String processName = "android.security.cts.CVE_2023_21145:pipActivity";

        // Check if pid has changed
        int currentPid = mNoPidFound;
        long startTime = System.currentTimeMillis();
        while ((currentPid == mNoPidFound || currentPid == initialPid) // Check if pid has changed
                && System.currentTimeMillis() - startTime <= timeout) {
            Optional<Integer> pid = ProcessUtil.pidOf(device, processName);
            currentPid = pid.isPresent() ? pid.get() : mNoPidFound;
            RunUtil.getDefault().sleep(200); // Sleep for 200 ms before checking pid again
        }
        return currentPid;
    }

    private boolean checkIfPipWindowCanBeShifted(ITestDevice device) throws Exception {
        // Fetch initial bounds of the pip window.
        final CompletableFuture<Map<String, Integer>> visibleBounds = new CompletableFuture<>();
        if (!poll(
                () -> {
                    try {
                        Map<String, Integer> boundsMap = fetchPipWindowBounds(device);
                        if (boundsMap != null) {
                            visibleBounds.complete(boundsMap);
                            return true;
                        }
                    } catch (Exception expected) {
                        // Ignore unexpected exceptions
                    }
                    return false;
                })) {
            return false;
        }
        final Map<String, Integer> initialBounds = visibleBounds.getNow(null);
        if (initialBounds == null) {
            return false;
        }

        // Try to shift the pip window to bottom-left.
        final Optional<Map<String, Integer>> pipWindowLocation =
                shiftPipWindow(device, initialBounds, 0, initialBounds.get("verticalMid"));
        if (pipWindowLocation.isEmpty()) {
            return false;
        }

        // Try to shift the pip window to top-left.
        final Map<String, Integer> currentBounds = pipWindowLocation.get();
        if (shiftPipWindow(device, currentBounds, currentBounds.get("horizontalMid"), 0)
                .isEmpty()) {
            return false;
        }
        return true;
    }

    private Optional<Map<String, Integer>> shiftPipWindow(
            ITestDevice device, Map<String, Integer> initialBounds, int x, int y) throws Exception {
        final boolean windowShifted =
                poll(
                        () -> {
                            try {
                                // Swipe pip window to [x, y] location
                                runAndCheck(
                                        device,
                                        String.format(
                                                "input swipe %s %s %d %d 100",
                                                initialBounds.get("horizontalMid"),
                                                initialBounds.get("verticalMid"),
                                                x,
                                                y));

                                // Fetch the updated bounds of the pip window.
                                // If window is interactable, the bounds value would change.
                                final Map<String, Integer> bounds = fetchPipWindowBounds(device);
                                return !bounds.get("horizontalMid")
                                                .equals(initialBounds.get("horizontalMid"))
                                        || !bounds.get("verticalMid")
                                                .equals(initialBounds.get("verticalMid"));
                            } catch (Exception expected) {
                                // Ignore unexpected exceptions
                            }
                            return false;
                        });
        return Optional.ofNullable(windowShifted ? fetchPipWindowBounds(device) : null);
    }

    private Map<String, Integer> fetchPipWindowBounds(ITestDevice device) throws Exception {
        // Fetch bounds of the pip window.
        // Expected dumpsys output:
        // Window #10 Window{...android.security.cts.CVE_2023_21145...mAppBounds=Rect(a, b - c, d)
        final Map<String, Integer> bounds = new HashMap<>();
        final Matcher matcher =
                getParsedDumpsys(
                        device,
                        "window windows",
                        "Splash\\s+Screen\\s+android.security.cts.CVE_2023_21145.*?"
                                + "mAppBounds=Rect\\("
                                + "(?<left>\\d+),\\s+"
                                + "(?<top>\\d+)\\s+-\\s+"
                                + "(?<right>\\d+),\\s+"
                                + "(?<bottom>\\d+)\\"
                                + ").*?Window",
                        Pattern.CASE_INSENSITIVE);
        if (matcher.find()) {
            bounds.put(
                    "horizontalMid",
                    Integer.parseInt(matcher.group("left"))
                            + ((Integer.parseInt(matcher.group("right"))
                                            - Integer.parseInt(matcher.group("left")))
                                    / 2));
            bounds.put(
                    "verticalMid",
                    Integer.parseInt(matcher.group("top"))
                            + ((Integer.parseInt(matcher.group("bottom"))
                                            - Integer.parseInt(matcher.group("top")))
                                    / 2));
            return bounds;
        }
        return null;
    }
}
