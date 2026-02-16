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

package android.cts.backup.delayedfullrestoreapp;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.DelayedRestoreRequest;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class DelayedFullBackupAgent extends BackupAgent {
    private static final String TAG = "DelayedFullBackupAgent";
    private static final String DEPENDENCY_PKG = "android.cts.backup.delayedrestoredependency";
    private static final String FILE_NAME = "backup_file";

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        fullBackupFile(new File(getFilesDir(), FILE_NAME), data);
    }

    @Override
    public void onRestoreFile(ParcelFileDescriptor data, long size,
            File destination, int type, long mode, long mtime) throws IOException {
        Log.i(TAG, "onRestoreFile called");
        super.onRestoreFile(data, size, destination, type, mode, mtime);
    }

    @Override
    public void onRestoreFinished() {
        Log.i(TAG, "onRestoreFinished called");
        // Schedule delayed restore for the dependency app
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(DEPENDENCY_PKG)
                .build();

        BackupManager backupManager = new BackupManager(this);
        boolean success = backupManager.scheduleDelayedRestore(request);
        Log.i(TAG, "Scheduled delayed full restore: " + success);
    }

    @Override
    public void onDelayedFullRestore(DelayedRestoreRequest request) {
        // Verify this callback is invoked for Full Backup apps
        Log.i(TAG, "onDelayedFullRestore called!");
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // No-op for full backup agent
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {
        // No-op for full backup agent
    }
}
