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

package com.android.cts.pcc.processes;

import static com.android.cts.pcc.common.StorageTestUtils.writeFile;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppDataSetupTest {
    public static final String TEST_FILE = "test_file.dat";
    public static final String TEST_CACHE_FILE = "test_cache_file.dat";
    public static final String TEST_DE_FILE = "test_de_file.dat";
    public static final long APP_DATA_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    // This isn't exactly a test. We're forcing the app to create some data in its directory for
    // PccSandboxStorageStatsTest
    @Test
    public void setupAppData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        writeFile(context.getFilesDir(), TEST_FILE, APP_DATA_FILE_SIZE);
        writeFile(
                context.createDeviceProtectedStorageContext().getFilesDir(),
                TEST_DE_FILE,
                APP_DATA_FILE_SIZE);
        writeFile(context.getCacheDir(), TEST_CACHE_FILE, APP_DATA_FILE_SIZE);
    }
}
