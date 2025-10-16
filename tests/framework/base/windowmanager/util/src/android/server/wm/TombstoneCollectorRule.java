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

package android.server.wm;

import android.os.Bundle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A TestRule that finds any new tombstone files generated during a test run and reports them as
 * metrics for FilePullerLogCollector to pull.
 *
 * <p>NOTE: This requires root access on the device to read `/data/tombstones/`.
 */
public class TombstoneCollectorRule implements TestRule {
    private static final String TAG = "TombstoneCollector";
    private static final String TOMBSTONE_DIR = "/data/tombstones/";
    private static final String METRIC_KEY_PREFIX = "tombstone_file_";

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Set<String> filesBefore = getTombstoneFiles();
                try {
                    base.evaluate(); // This executes the actual @Test method
                } finally {
                    Set<String> filesAfter = getTombstoneFiles();
                    // Find the difference to identify newly created files
                    filesAfter.removeAll(filesBefore);
                    if (!filesAfter.isEmpty()) {
                        Log.d(TAG, "Found " + filesAfter.size() + " new tombstone(s).");
                        reportNewTombstones(filesAfter);
                    }
                }
            }
        };
    }

    /** Executes a shell command to list files in the tombstone directory. */
    private Set<String> getTombstoneFiles() {
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            // 'ls -p' appends '/' to directories, helping filter them out if any
            String commandResult = device.executeShellCommand("ls -p " + TOMBSTONE_DIR);

            return Arrays.stream(commandResult.split("\\s+")) // Split by whitespace
                    .filter(name -> !name.isEmpty() && !name.endsWith("/"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            Log.e(TAG, "Failed to list tombstone directory. Does the device have root?", e);
            return new HashSet<>();
        }
    }

    /** Reports the full path of each new tombstone as an instrumentation status metric. */
    private void reportNewTombstones(Set<String> newFiles) {
        int i = 0;
        for (String fileName : newFiles) {
            String key = METRIC_KEY_PREFIX + i;
            String absolutePath = TOMBSTONE_DIR + fileName;

            Bundle results = new Bundle();
            results.putString(key, absolutePath);

            InstrumentationRegistry.getInstrumentation().sendStatus(0, results);
            Log.i(TAG, "Reporting metric: key='" + key + "', value='" + absolutePath + "'");
            i++;
        }
    }
}
