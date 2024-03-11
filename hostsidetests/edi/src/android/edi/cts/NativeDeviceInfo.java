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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System native device info collector.
 */
public class NativeDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();
        CommandResult commandResult = device.executeShellV2Command("cat /proc/self/maps");
        if (!commandResult.getStderr().isEmpty()) {
            CLog.w("Warnings occurred when running cat:\n%s", commandResult.getStderr());
        }
        if (commandResult.getExitCode() == null) {
            throw new NullPointerException("cat command exit code is null");
        }
        if (commandResult.getExitCode() != 0) {
            throw new IllegalStateException(
                String.format("cat commaned returned %d: %s", commandResult.getExitCode(),
                              commandResult.getStderr()));
        }
        String stdout = commandResult.getStdout();
        if (stdout == null) {
            throw new NullPointerException("cat command resulted in no output");
        }

        String allocatorName;
        if (stdout.indexOf(":scudo:") != -1) {
            allocatorName = "scudo";
        } else {
            allocatorName = "jemalloc";
        }

        // Check for the bitness of the device. A device that supports
        // both 32 bits and 64 bits is assumed to be whatever the shell
        // user runs as.
        // On 32 bit devices, the format of the entries is:
        //   beace000-beaf0000 rw-p 00000000 00:00 0          [stack]
        // On 64 bit devices, the format of the entries is:
        //   7ffdc13000-7ffdc35000 rw-p 00000000 00:00 0      [stack]
        // This pattern looks for an entry that contains only 8 hex digits
        // in the first and second map address to indicate 32 bit devices.
        Pattern mapPattern = Pattern.compile("^[0-9a-f]{8,8}-[0-9a-f]{8,8} ");
        Matcher matcher = mapPattern.matcher(stdout);
        if (matcher.find()) {
            allocatorName += "32";
        } else {
            allocatorName += "64";
        }

        if (device.getBooleanProperty("ro.config.low_ram", false)) {
            allocatorName += "_lowmemory";
        }
        store.addResult("allocator", allocatorName);
    }
}
