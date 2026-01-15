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

package android.backup.app;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import java.io.IOException;

public class PccCheckingBackupAgent extends BackupAgent {
    private static final String TAG = "PccCheckingBackupAgent";

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Process.isPrivateComputeCoreUid(Process.myUid())) {
            String msg = "Backup Agent NOT running in PCC process! UID: " + Process.myUid();
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }

    @Override
    public void onBackup(
            ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState)
            throws IOException {
        String key = "pcc_test_key";
        byte[] payload = "pcc_test_value".getBytes();

        data.writeEntityHeader(key, payload.length);
        data.writeEntityData(payload, payload.length);
    }

    @Override
    public void onRestore(
            android.app.backup.BackupDataInput data,
            int appVersionCode,
            ParcelFileDescriptor newState)
            throws IOException {
        // No-op
    }
}
