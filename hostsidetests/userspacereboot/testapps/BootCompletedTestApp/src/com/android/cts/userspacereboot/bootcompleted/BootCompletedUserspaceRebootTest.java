/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.userspacereboot.bootcompleted;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Scanner;

@RunWith(JUnit4.class)
public class BootCompletedUserspaceRebootTest {

    private static final String TAG = "UserspaceRebootTest";

    private static final String FILE_NAME = "secret.txt";
    private static final String SECRET_MESSAGE = "wow, much secret";

    private static final String RECEIVED_BROADCASTS_FILE = "received_broadcasts.txt";

    private final Context mCeContext =
            getInstrumentation().getContext().createCredentialProtectedStorageContext();
    private final Context mDeContext =
            getInstrumentation().getContext().createDeviceProtectedStorageContext();

    @Test
    public void prepareFile() throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                mCeContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE))) {
            writer.write(SECRET_MESSAGE);
        }
    }

    @Test
    public void testVerifyCeStorageUnlocked() throws Exception {
        UserManager um = getInstrumentation().getContext().getSystemService(UserManager.class);
        assertThat(um.isUserUnlocked(0)).isTrue();
        try (Scanner scanner = new Scanner(mCeContext.openFileInput(FILE_NAME))) {
            final String content = scanner.nextLine();
            assertThat(content).isEqualTo(SECRET_MESSAGE);
        }
    }

    @Test
    public void testVerifyReceivedBootCompletedBroadcast() throws Exception {
        try (Scanner scanner = new Scanner(mDeContext.openFileInput(RECEIVED_BROADCASTS_FILE))) {
            final String intent = scanner.nextLine();
            assertThat(intent).isEqualTo(Intent.ACTION_BOOT_COMPLETED);
        }
    }

    public static class BootReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received! " + intent);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    context.createDeviceProtectedStorageContext().openFileOutput(
                            RECEIVED_BROADCASTS_FILE, Context.MODE_PRIVATE)))) {
                writer.println(intent.getAction());
            } catch (IOException e) {
                Log.w(TAG, "Failed to append to " + RECEIVED_BROADCASTS_FILE, e);
            }
        }
    }
}
