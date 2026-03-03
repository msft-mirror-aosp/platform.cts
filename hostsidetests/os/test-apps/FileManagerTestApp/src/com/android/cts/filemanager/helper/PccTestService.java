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

package com.android.cts.filemanager.helper;

import android.app.privatecompute.PccService;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PccTestService extends PccService {
    private static final String TAG = "PccTestService";

    @Override
    public void onReceiveData(Bundle data, String packageName) {
        String action = data.getString("action", "verify");
        Log.i(TAG, "onReceiveData: action=" + action);

        if ("prepare".equals(action)) {
            handlePrepare(data);
        } else {
            handleVerify(data);
        }
    }

    private void handlePrepare(Bundle data) {
        String fileName = data.getString("file");
        String content = data.getString("content");
        if (fileName == null || content == null) {
            Log.e(TAG, "Prepare failed: missing file or content");
            return;
        }

        File file = new File(getDataDir(), fileName);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
            Log.i(TAG, "Prepare successful: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Prepare failed", e);
        }
    }

    private void handleVerify(Bundle data) {
        String expectedFile = data.getString("expected_file");
        String expectedContent = data.getString("expected_content");
        Log.i(TAG, "handleVerify: expectedFile=" + expectedFile);

        boolean success = false;
        if (expectedFile != null) {
            File file = new File(getDataDir(), expectedFile);
            if (file.exists()) {
                Log.i(TAG, "File found: " + file.getAbsolutePath());

                boolean contentMatches = true;
                if (expectedContent != null) {
                    try {
                        byte[] actualBytes = java.nio.file.Files.readAllBytes(file.toPath());
                        String actualContent = new String(actualBytes);
                        contentMatches = actualContent.contains(expectedContent);
                        if (!contentMatches) {
                            Log.e(
                                    TAG,
                                    "Content mismatch. Expected to contain: "
                                            + expectedContent
                                            + ", Actual: "
                                            + actualContent);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to read file content", e);
                        contentMatches = false;
                    }
                }

                if (contentMatches) {
                    // Try to write to it to verify write access
                    try (FileOutputStream fos = new FileOutputStream(file, true)) {
                        fos.write("PCC was here".getBytes());
                        Log.i(TAG, "Write successful");
                        success = true;
                    } catch (IOException e) {
                        Log.e(TAG, "Write failed", e);
                    }
                }
            } else {
                Log.e(TAG, "File NOT found: " + file.getAbsolutePath());
            }
        }

        if (success) {
            // Create canary file only on success
            File canary = new File(getDataDir(), "verification_success.txt");
            try {
                canary.createNewFile();
                Log.i(TAG, "Canary created");
            } catch (IOException e) {
                Log.e(TAG, "Failed to create canary", e);
            }
        } else {
            Log.e(TAG, "Verification failed, skipping canary creation");
        }
    }
}
