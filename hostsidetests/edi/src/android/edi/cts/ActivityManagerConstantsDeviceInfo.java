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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.IOException;

/*
 * When a device gets the adb command "dumpsys activity settings", the output
 * is of the form:
 *
 * CHECKSTYLE:OFF
 * <pre>

ACTIVITY MANAGER SETTINGS (dumpsys activity settings) activity_manager_constants:
  max_cached_processes=1024
  background_settle_time=60000
[...]
  ENABLE_WAIT_FOR_FINISH_ATTACH_APPLICATION=true
  follow_up_oomadj_update_wait_duration=1000
  proc_state_debug_uids={}
    uid-state-delay=0
    proc-state-delay=0
CachedAppOptimizer settings
  use_compaction=true
[...]

 * </pre>
 * CHECKSTYLE:ON
 *
 * where the first line is always the same.  Our constants always have at least
 * one leading space in front of them, and use an "=" between the name and the
 * value.  When we get to a line without an indentation, we're done with the
 * constants and can ignore everything else.
 */

/** Collector for constants from the frameworks ActivityManagerConstants. */
public class ActivityManagerConstantsDeviceInfo extends DeviceInfo {
    private static final String CONSTANTS_LIST_NAME = "constants";
    private static final String CONSTANT_NAME = "name";
    private static final String CONSTANT_VALUE = "value";

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        String output = getDumpsysOutput();
        String[] lines = output.split("\n");
        if (lines.length < 2) {
            CLog.w("Did not get lines as expected, got '" + output + "'");
            return;
        }
        int startIndex = 0;
        for (; startIndex < lines.length && !isStartLine(lines[startIndex]); startIndex++) {
            // No loop body
        }
        if (startIndex >= lines.length) {
            CLog.w("Unable to find first line of output in:\n" + output);
            return;
        }

        store.startArray(CONSTANTS_LIST_NAME);

        for (int i = startIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (isEndLine(line)) {
                CLog.d("Found end line '" + line + "'");
                // We stop processing lines here.
                break;
            }
            processLine(line.trim(), store);
        }

        store.endArray();
    }

    private String getDumpsysOutput() throws Exception {
        final String cmd = "dumpsys activity settings";
        CommandResult result = getDevice().executeShellV2Command(cmd);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Failed command " + cmd + "\n" + result.getStdout() + result.getStderr());
        }

        return result.getStdout();
    }

    private boolean isStartLine(String line) {
        // The "activity_manager_constants" part of the line comes from code,
        // we just focus on the hardcoded start of the line.
        // Note this output string has been the same since the initial checkin
        // of the code in 2017, go/ag/1829114.
        return line.startsWith("ACTIVITY MANAGER SETTINGS (dumpsys activity settings)");
    }

    private boolean isEndLine(String line) {
        // We specifically expect "CachedAppOptimizer settings" as our end line.
        // But we'll return for any line which isn't empty and doesn't start
        // with a space.
        return ((line.length() > 0) && (line.charAt(0) != ' '));
    }

    private void processLine(String line, HostInfoStore store) throws IOException {
        if (line.length() == 0) {
            // We do get an empty line in this output, so no need to log this.
            return;
        }
        String[] parts = line.split("=", 2);
        if (parts.length != 2) {
            CLog.i("Discarding invalid constant line '" + line + "'");
            return;
        }
        store.startGroup();
        store.addResult(CONSTANT_NAME, parts[0].trim());
        store.addResult(CONSTANT_VALUE, parts[1].trim());
        store.endGroup();
    }
}
