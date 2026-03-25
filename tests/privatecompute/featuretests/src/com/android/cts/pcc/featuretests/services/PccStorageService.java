/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.cts.pcc.featuretests.services;

import static com.android.cts.pcc.common.StorageTestUtils.writeFile;

import android.app.privatecompute.PccService;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PccStorageService extends PccService {
    private static final String TAG = "PccStorageService";
    public static final String EXTRA_COMMAND = "command";
    public static final String COMMAND_WRITE_FILE = "write_file";
    public static final String COMMAND_WRITE_DE_FILE = "write_de_file";
    public static final String COMMAND_WRITE_CACHE_FILE = "write_cache_file";
    public static final String COMMAND_CLEANUP = "cleanup";
    public static final String COMMAND_READ_FD = "read_fd";
    public static final String COMMAND_CAN_OPEN_FILE_BY_PATH = "can_open_file_by_path";

    public static final String EXTRA_FILE_SIZE_BYTES = "file_size_bytes";
    public static final String EXTRA_PFD = "pfd";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_RESULT_RECEIVER = "result_receiver";

    public static final String PCC_FILE = "test_pcc_file.dat";
    private static final String PCC_CACHE_FILE = "test_pcc_cache_file.dat";
    private static final String PCC_DE_FILE = "test_pcc_de_file.dat";

    @Override
    public void onReceiveData(Bundle data, String packageName) {
        String command = data.getString(EXTRA_COMMAND);
        long size = data.getLong(EXTRA_FILE_SIZE_BYTES);

        if (COMMAND_WRITE_FILE.equals(command)) {
            writeFile(getFilesDir(), PCC_FILE, size);
        } else if (COMMAND_WRITE_DE_FILE.equals(command)) {
            writeFile(createDeviceProtectedStorageContext().getFilesDir(), PCC_DE_FILE, size);
        } else if (COMMAND_WRITE_CACHE_FILE.equals(command)) {
            writeFile(getCacheDir(), PCC_CACHE_FILE, size);
        } else if (COMMAND_CLEANUP.equals(command)) {
            cleanup();
        } else if (COMMAND_READ_FD.equals(command)) {
            ParcelFileDescriptor pfd = data.getParcelable(EXTRA_PFD, ParcelFileDescriptor.class);
            if (pfd != null) {
                try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                    int val = fis.read();
                    if (val == -1) {
                        Log.e(TAG, "Failed to read FD: EOF");
                        System.exit(1); // CRASH! Test expects to be able to read this.
                    }
                    Log.i(TAG, "Successfully read file via FD");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read fd", e);
                    System.exit(1); // CRASH! Test expects to be able to read this.
                }
            } else {
                System.exit(1); // CRASH! FD was null
            }

        } else if (COMMAND_CAN_OPEN_FILE_BY_PATH.equals(command)) {
            String path = data.getString(EXTRA_FILE_PATH);
            if (path != null) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    fis.read();
                    // VULNERABILITY! We successfully read an untrusted app file!
                    Log.e(TAG, "VULNERABILITY: Successfully opened file by path: " + path);
                    System.exit(1);
                } catch (IOException | SecurityException e) {
                    // EXPECTED BEHAVIOR! SELinux blocked the read.
                    Log.i(TAG, "Successfully blocked from opening file by path: " + path, e);
                }
            }
        }
    }

    private void cleanup() {
        try {
            File file = new File(getFilesDir(), PCC_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_FILE, e);
        }
        try {
            File file = new File(createDeviceProtectedStorageContext().getFilesDir(), PCC_DE_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_DE_FILE, e);
        }
        try {
            File file = new File(getCacheDir(), PCC_CACHE_FILE);
            file.delete();
            Log.i(TAG, "Successfully deleted " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete " + PCC_CACHE_FILE, e);
        }
    }
}
