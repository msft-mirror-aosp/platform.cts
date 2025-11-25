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
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class StateManager {
    private static final String STATE_FILE = "state.txt";

    /**
     * Creates a state file in the app's external files directory.
     *
     * @param context The context of the calling application.
     */
    public static void createStateFile(Context context) {
        try {
            File stateFile = new File(context.getExternalFilesDir(null), STATE_FILE);
            stateFile.createNewFile();
        } catch (IOException e) {
            Log.e("StateManager", "Failed to create state file", e);
        }
    }
}
