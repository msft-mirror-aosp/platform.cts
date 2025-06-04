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
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageIoInterfaceDeviceInfo extends DeviceInfo {
    private static final String INTERFACE_FIELD = "storage_io_interface";
    private static final String CONTEXT_FIELD = "storage_io_script_context";

    private static final String SCRIPT_BASE_NAME = "check_for_ufs";
    private static final String SCRIPT_EXT = ".sh";
    private static final String DEVICE_DIR = "/tmp/";
    private static final String FULL_PATH = DEVICE_DIR + SCRIPT_BASE_NAME + SCRIPT_EXT;

    private static String getInterfaceStringFromExitCode(int exitCode) {
        // This needs to be in sync with system/core/storaged/tests/check_for_ufs.sh
        switch (exitCode) {
            case 0:
                return "UFS";
            case 1:
                return "eMMC";
            case 2:
                return "Setup Error";
            case 3:
                return "Internal Error";
        }
        return "Unexpect exit code " + exitCode;
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        pushResourceFileToDevice(SCRIPT_BASE_NAME, SCRIPT_EXT);

        try {
            chmodScript();
            storeScriptResult(store);
        } finally {
            // Best effort for cleanup here.  If this fails, there's not
            // much we can do.  The impact is minimal since this is in /tmp.
            getDevice().executeShellV2Command("rm " + FULL_PATH);
        }
    }

    private void chmodScript() throws Exception {
        final String cmd = "chmod 755 " + FULL_PATH;
        CommandResult result = getDevice().executeShellV2Command(cmd);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Failed command " + cmd + "\n" + result.getStdout() + result.getStderr());
        }
    }

    private void storeScriptResult(HostInfoStore store) throws Exception {
        CommandResult result = getDevice().executeShellV2Command(FULL_PATH);
        store.addResult(INTERFACE_FIELD, getInterfaceStringFromExitCode(result.getExitCode()));
        // We want everything after the ": ".  This format is encoded in
        // system/core/storaged/tests/check_for_ufs.sh
        String stdout = result.getStdout();
        int index = stdout.indexOf(":") + 2;
        String context;
        if (index > 2) {
            context = stdout.substring(index);
        } else {
            context = "Malformed output: " + stdout;
        }
        store.addResult(CONTEXT_FIELD, context.strip());
    }

    /** Push a resource onto device */
    private void pushResourceFileToDevice(String resourceName, String ext) throws Exception {
        String fullResourceName = resourceName + ext;
        File outputFile = File.createTempFile(resourceName, ext);
        try {
            // TODO(b/422488422): When the utility logic is fixed, we should replace
            // much of this with:
            //    ResourceUtil.extractResourceToFile("/" + fullResourceName, outputFile);
            try (InputStream in = getClass().getResourceAsStream("/" + fullResourceName);
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                if (in == null) {
                    throw new IllegalArgumentException("Resource not found: " + fullResourceName);
                }
                byte[] buf = new byte[8192];
                int chunkSize;
                while ((chunkSize = in.read(buf)) != -1) {
                    out.write(buf, 0, chunkSize);
                }
            }
            getDevice().pushFile(outputFile, DEVICE_DIR + fullResourceName);
        } finally {
            outputFile.delete();
        }
    }
}
