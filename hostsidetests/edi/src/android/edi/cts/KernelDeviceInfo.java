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

public class KernelDeviceInfo extends DeviceInfo {

    private static final String BUILD_SYSTEM = "build_system";
    private static final String MODULES = "modules";

    /**
     * This provides the device's current boot state, which represents the level of protection
     * provided to the user and to apps after the device finishes booting.
     */
    public enum KernelBuildSystem {
        KERNEL_BUILD_SYSTEM_UNSPECIFIED,
        KERNEL_BUILD_SYSTEM_UNKNOWN,
        KERNEL_BUILD_SYSTEM_KLEAF
    }

    void collectKernelBuildSystem(ITestDevice device, HostInfoStore store) throws Exception {
        CommandResult commandResult = device.executeShellV2Command("cat /proc/version");
        if (commandResult.getExitCode() != 0) {
            CLog.e("Impossible to run `cat /proc/version`");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        String output = commandResult.getStdout();
        if (output == null) {
            CLog.e("Empty output");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        output = output.trim();
        if (output.isEmpty()) {
            CLog.e("Empty output");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        if (output.contains("(kleaf@build-host)")) {
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_KLEAF.name());
        } else {
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNKNOWN.name());
        }
    }

    void collectKernelModules(ITestDevice device, HostInfoStore store) throws Exception {
        String[] modules;

        CommandResult commandResult = device.executeShellV2Command("lsmod");
        if (commandResult.getExitCode() != 0) {
            CLog.e("Impossible to run `lsmod`");
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
        /*
         * At this point, `output` is a space-separated table where the first column represents the
         * module name.
         */
        modules =
                output.lines()
                        .skip(1) // Skips the first line (the header)
                        .map(line -> line.split("\\s+")[0]) // Take the first row element
                        .sorted() // Sort the elements in natural (alphabetical) order
                        .toArray(String[]::new);

        store.startArray(MODULES);
        for (String m : modules) {
            store.startGroup();
            store.addResult("name", m);
            store.endGroup();
        }
        store.endArray();
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();

        collectKernelBuildSystem(device, store);
        collectKernelModules(device, store);
    }
}
