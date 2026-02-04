/*
 * Copyright 2026 The Android Open Source Project
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

package android.backup.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataOutput
import android.content.Intent
import android.os.ParcelFileDescriptor

class BackupTestAgent : BackupAgent() {
    var instantiatedViaFactory = false

    override fun onCreate() {
        super.onCreate()
        if (instantiatedViaFactory) {
            val intent = Intent("android.backup.app.AGENT_INSTANTIATED")
            intent.setPackage("android.backup.cts")
            sendBroadcast(intent)
        }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        // No-op: test focuses on instantiation, not the data transfer.
    }

    override fun onRestore(
        data: android.app.backup.BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        // No-op
    }
}
