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

package com.android.compatibility.common.util;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Static methods used to validate preconditions in the media CTS suite to simplify failure
 * diagnosis.
 */
public final class Preconditions {
    private static final String TAG = "Preconditions";

    private static long getDirSize(File dir) {
        long size = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += (file.isDirectory() ? getDirSize(file) : file.length());
                }
            }
        }
        return size;
    }

    private static String getDiskUsage(String path) {
        String command = "df -k " + path;
        StringBuilder msg = new StringBuilder(command).append("\n");
        try {
            Process df = Runtime.getRuntime().exec(command);
            try (Scanner scanner = new Scanner(df.getInputStream())) {
                while (scanner.hasNextLine()) {
                    msg.append(scanner.nextLine()).append("\n");
                }
            }
        } catch (IOException e) {
            msg.append(e);
        }
        return msg.toString();
    }

    /**
     * While accessing resource file, if it is not present, media codec api sometimes sends
     * obfuscated message indicating the same. Have the test run this check before accessing
     * resource.
     */
    public static void assertTestFileExists(String pathName) {
        File testFile = new File(pathName);
        if (!testFile.exists()) {
            StringBuilder msg = new StringBuilder();
            msg.append(String.format("Test Setup Error, missing file: %s\n", pathName));
            File parentDir = testFile.getAbsoluteFile().getParentFile();
            // Check if assets dir exists, if it is present then get its size.
            // Also, get disk usage for available free space
            while (parentDir != null) {
                String parentPath = parentDir.getPath();
                if (parentDir.exists()) {
                    msg.append(String.format("Assets Dir: %s, size: %d bytes\n", parentPath,
                            getDirSize(parentDir)));
                    msg.append(getDiskUsage(parentPath));
                    break;
                } else {
                    msg.append(String.format("Assets Dir: %s, not found\n", parentPath));
                }
                parentDir = parentDir.getParentFile();
            }
            fail(msg.toString());
        }
    }

    private Preconditions() {}
}
