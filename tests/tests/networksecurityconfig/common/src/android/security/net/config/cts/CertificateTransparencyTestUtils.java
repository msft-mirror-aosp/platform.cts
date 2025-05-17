/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.security.net.config.cts;

import android.os.ConfigUpdate;

import com.android.compatibility.common.util.ShellUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Helper methods/constants for Certificate Transparency CTS & e2e tests. */
final class CertificateTransparencyTestUtils {

    static final String SCT_PROVIDED_DOMAIN = "https://android.com";
    static final String SCT_PROVIDED_DOMAIN_2 = "https://sha256.badssl.com";
    static final String NO_SCT_PROVIDED_DOMAIN = "https://no-sct.badssl.com/";
    static final int HTTP_OK_RESPONSE_CODE = 200;

    // Path copied from com.android.server.net.ct.Config
    // Note: we do this to avoid a dependency on the service, which may result in
    // testing the code in CTS instead of the device itself
    static final String CT_PARENT_DIRECTORY_PATH = "/data/misc/keychain/";
    static final String CT_DIRECTORY_NAME = "ct";
    static final String CT_ROOT_DIRECTORY_PATH = CT_PARENT_DIRECTORY_PATH + CT_DIRECTORY_NAME;

    /**
     * Returns whether the log list is ready to be used or not. There are many potential pitfalls
     * here. The function tries to enumerate all the subdirectories starting with "v". For all these
     * directories, if a "current" directory does not exist, fail (the list is currently being
     * copied). Also if no subdirectories are found at all, fail.
     *
     * <p>Assuming that no other process is removing the log list, the only gap remaining is when
     * one version has been created (e.g., "v1") and the newest version directory ("v2") has not
     * been created yet.
     *
     * <p>TODO(b/378427150): replace with Conscrypt API once implemented
     */
    static boolean isLogListFilePresent() {
        boolean found = false;
        Path root = Paths.get(CT_ROOT_DIRECTORY_PATH);
        try {
            for (Path version : Files.newDirectoryStream(root, "v*")) {
                if (Files.isDirectory(version)) {
                    found = true;
                    boolean foundCurrent = false;
                    for (Path dir : Files.newDirectoryStream(version)) {
                        if (dir.getFileName().toString().equals("current")) {
                            foundCurrent = true;
                            break;
                        }
                    }
                    if (!foundCurrent) {
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            // NoSuchFileException is a subclass of IOException, which is why we do not
            // specify it here in the catch statement.
            return false;
        }
        return found;
    }

    static void downloadLogList() {
        ShellUtils.runShellCommand("am broadcast -a " + ConfigUpdate.ACTION_UPDATE_CT_LOGS);
    }

    static void deleteLogList() {
        ShellUtils.runShellCommand("rm -r " + CT_ROOT_DIRECTORY_PATH);
    }

    /** Private constructor to prevent instantiation as this is effectively a static utils class. */
    private CertificateTransparencyTestUtils() {}
}
