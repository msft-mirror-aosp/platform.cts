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

package com.android.cts.stopandkillapp;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class StateManager {
    private static final String STATE_FILE_FORMAT = "%s-state.txt";
    private static final String TAG = "CtsStopAndKillTestApp";

    /**
     * Creates a state file in the public Documents directory.
     *
     * @param context The context of the calling application.
     */
    public static void createStateFile(Context context) {
        try {
            final String stateFileName = String.format(STATE_FILE_FORMAT, context.getPackageName());
            // Get the public Documents directory.
            File documentsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            // Ensure the directory exists.
            documentsDir.mkdirs();
            File stateFile = new File(documentsDir, stateFileName);
            if (stateFile.exists()) {
                Log.d(TAG, "State file already exists: " + stateFile);
            } else {
                Log.d(TAG, "Creating file at " + stateFile);
                if (stateFile.createNewFile()) {
                    Log.d(TAG, "State file created successfully: " + stateFile);
                } else {
                    Log.e(TAG, "Failed to create state file: " + stateFile);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create state file", e);
        }
    }
}
