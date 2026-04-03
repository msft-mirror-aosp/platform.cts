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

package com.android.cts.pcc.datainit;

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.common.StorageTestUtils.writeFile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

public class DataInitReceiver extends BroadcastReceiver {
    private static final String TAG = "DataInitReceiver";
    public static final String TEST_FILE = "test_file.dat";
    public static final String TEST_CACHE_FILE = "test_cache_file.dat";
    public static final String TEST_DE_FILE = "test_de_file.dat";
    private static final long TEST_FILE_SIZE = 10 * 1024 * 1024;
    static final String INIT_DATA_ACTION = "com.android.cts.pcc.datainit.INIT_DATA";
    static final String CLEANUP_DATA_ACTION = "com.android.cts.pcc.datainit.CLEANUP_DATA";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (INIT_DATA_ACTION.equals(intent.getAction())) {
            writeFile(context.getFilesDir(), TEST_FILE, TEST_FILE_SIZE);
            writeFile(
                    context.createDeviceProtectedStorageContext().getFilesDir(),
                    TEST_DE_FILE,
                    TEST_FILE_SIZE);
            writeFile(context.getCacheDir(), TEST_CACHE_FILE, TEST_FILE_SIZE);
        } else if (CLEANUP_DATA_ACTION.equals(intent.getAction())) {
            deleteIgnoreException(new File(context.getFilesDir(), TEST_FILE));
            deleteIgnoreException(
                    new File(
                            context.createDeviceProtectedStorageContext().getFilesDir(),
                            TEST_DE_FILE));
            deleteIgnoreException(new File(context.getCacheDir(), TEST_CACHE_FILE));
        }
    }
}
