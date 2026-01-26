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

package com.android.cts.pcc.common;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Util class holding common methods used across storage tests */
public class StorageTestUtils {
    private static final String TAG = "StorageTestUtils";

    /** Delete file and ignore any exceptions thrown */
    public static void deleteIgnoreException(File file) {
        try {
            boolean ignored = file.delete();
        } catch (SecurityException e) {
            // Ignore exception
        }
    }

    /** Delete the given file */
    public static void deleteThrowException(File file) throws SecurityException {
        boolean ignored = file.delete();
    }

    /** Writes a file of the given size to the given directory */
    public static void writeFile(File dir, String fileName, long size) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = (byte) (i & 0xFF);
            }
            long written = 0;
            while (written < size) {
                int toWrite = (int) Math.min(buffer.length, size - written);
                fos.write(buffer, 0, toWrite);
                written += toWrite;
            }
            Log.i(TAG, "Successfully wrote " + written + " bytes to " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write file", e);
        }
    }
}
