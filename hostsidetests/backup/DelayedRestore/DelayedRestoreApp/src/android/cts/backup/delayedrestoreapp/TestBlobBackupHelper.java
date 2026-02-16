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

package android.cts.backup.delayedrestoreapp;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.BackupManager;
import android.app.backup.DelayedRestoreRequest;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class TestBlobBackupHelper implements BackupHelper {
    private static final String TAG = "TestBlobBackupHelper";
    private static final String KEY1 = "backup_file";
    private static final String KEY2 = "delayed_backup_file";
    private static final String DEPENDENCY_PKG = "android.cts.backup.delayedrestoredependency";
    private final Context mContext;

    public TestBlobBackupHelper(Context context) {
        mContext = context;
    }

    @Override
    public void performBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) {
        Log.i(TAG, "performBackup called");
        backupFile(KEY1, data);
        backupFile(KEY2, data);
    }

    private void backupFile(String key, BackupDataOutput data) {
        try {
            byte[] payload = getFileContent(key);
            if (payload != null) {
                data.writeEntityHeader(key, payload.length);
                data.writeEntityData(payload, payload.length);
            }
        } catch (Exception e) {
             Log.e(TAG, "Failed to backup key: " + key, e);
        }
    }

    private byte[] getFileContent(String fileName) throws IOException {
        File file = new File(mContext.getFilesDir(), fileName);
        if (!file.exists()) {
            Log.w(TAG, "File not found: " + fileName);
            return new byte[0];
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            return buffer;
        }
    }

    @Override
    public void restoreEntity(BackupDataInputStream data) {
        String key = data.getKey();
        Log.i(TAG, "restoreEntity called for key: " + key);
        try {
            byte[] payload = new byte[data.size()];
            data.read(payload);

            switch (key) {
                case KEY1 -> writeRestoredPayload(payload, KEY1);
                case KEY2 -> {
                    // Schedule the delayed restore for the dependency app
                    DelayedRestoreRequest request =
                            new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                                    .setPackageName(DEPENDENCY_PKG)
                                    .build();
                    BackupManager backupManager = new BackupManager(mContext);
                    boolean success = backupManager.scheduleDelayedRestore(request);
                    Log.i(TAG, "Scheduled delayed restore: " + success);
                }
                default -> Log.w(TAG, "Unknown key: " + key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore entity: " + key, e);
        }
    }

    @Override
    public void delayedRestoreEntity(DelayedRestoreRequest request, BackupDataInputStream data) {
        String key = data.getKey();
        Log.i(TAG, "delayedRestoreEntity called for key: " + key);
        try {
            byte[] payload = new byte[data.size()];
            data.read(payload);

            if (key.equals(KEY2)) {
                writeRestoredPayload(payload, KEY2);
            }
        } catch (Exception e) {
             Log.e(TAG, "Failed to delayed restore entity: " + key, e);
        }
    }

    @Override
    public void writeNewStateDescription(ParcelFileDescriptor newState) {
        // Not doing any state maintenance so all backups are non-incremental.
    }

    private void writeRestoredPayload(byte[] payload, String fileName) {
        try {
            File file = new File(mContext.getFilesDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(payload);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write restored payload", e);
        }
    }
}
