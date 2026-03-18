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

import static com.android.cts.pcc.common.StorageTestUtils.deleteIgnoreException;
import static com.android.cts.pcc.processes.AppDataSetupTest.TEST_CACHE_FILE;
import static com.android.cts.pcc.processes.AppDataSetupTest.TEST_DE_FILE;
import static com.android.cts.pcc.processes.AppDataSetupTest.TEST_FILE;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class AppDataDestroyTest {

    // This isn't exactly a test. We're forcing the app to delete the data it created in
    // AppDataSetupTest for the purpose of PccSandboxStorageStatsTest
    @Test
    public void cleanupAppData() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        deleteIgnoreException(new File(context.getFilesDir(), TEST_FILE));
        deleteIgnoreException(
                new File(
                        context.createDeviceProtectedStorageContext().getFilesDir(), TEST_DE_FILE));
        deleteIgnoreException(new File(context.getCacheDir(), TEST_CACHE_FILE));
    }
}
