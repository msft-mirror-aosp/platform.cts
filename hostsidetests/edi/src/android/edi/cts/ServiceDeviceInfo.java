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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;

public class ServiceDeviceInfo extends DeviceInfo {

    private static final String PACKAGE = "services";
    private static final String NAME = "name";
    private static final String INTERFACE_DESCRIPTOR = "interface_descriptor";

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();

        CommandResult commandResult = device.executeShellV2Command("service list");
        if (commandResult.getExitCode() != 0) {
            CLog.e("The 'service' command exited with error");
            return;
        }

        String output = commandResult.getStdout();
        if (output == null) {
            CLog.e("Empty output");
            return;
        }

        output = output.trim();
        if (output.isEmpty()) {
            CLog.e("Empty output");
            return;
        }

        store.startArray(PACKAGE);

        // Iterate over every line of the output, each line corresponding to a
        // service found in the device.
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith("Found")) {
                // The first line begins returns the number of services found and can
                // be skipped. It's the only line string with "Found".
                continue;
            }

            // Each line has the format:
            // "<int:line number> <str:service name>: [<interface descriptor>]
            String[] entries = line.split("\\s+");
            if (entries.length != 3) {
                continue;
            }

            // Remove the last character of the string as it corresponds to a colon.
            if (entries[1].length() == 0) {
                continue;
            }
            String name = entries[1].substring(0, entries[1].length() - 1);

            // Remove the first and last characters of the string as they correspond
            // to square brackets.
            String interface_descriptor = "";
            if (entries.length > 2 && entries[2].length() > 2) {
                interface_descriptor = entries[2].substring(1, entries[2].length() - 1);
            }

            store.startGroup();
            store.addResult(NAME, name);
            store.addResult(INTERFACE_DESCRIPTOR, interface_descriptor);
            store.endGroup();
        }

        store.endArray();
    }
}
