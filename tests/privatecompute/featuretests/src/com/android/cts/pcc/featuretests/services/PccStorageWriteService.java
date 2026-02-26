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
import android.util.Log;

import java.io.File;

public class PccStorageWriteService extends PccService {
    private static final String TAG = "PccStorageWriteService";
    public static final String EXTRA_COMMAND = "command";
    public static final String COMMAND_WRITE_FILE = "write_file";
    public static final String COMMAND_WRITE_DE_FILE = "write_de_file";
    public static final String COMMAND_WRITE_CACHE_FILE = "write_cache_file";
    public static final String COMMAND_CLEANUP = "cleanup";
    public static final String EXTRA_FILE_SIZE_BYTES = "file_size_bytes";
    private static final String PCC_FILE = "test_pcc_file.dat";
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
