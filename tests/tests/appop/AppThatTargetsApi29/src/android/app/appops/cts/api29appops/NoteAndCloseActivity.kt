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

package android.app.appops.cts.api29appops

import android.app.Activity
import android.app.AppOpsManager
import android.os.Bundle
import android.os.Process

class NoteAndCloseActivity : Activity() {
    val intentExtra = "extra_op"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(AppOpsManager::class.java)!!.noteOpNoThrow(
            intent.getStringExtra(intentExtra)!!,
            Process.myUid(),
            packageName,
            "attribution tag",
            null
        )
        finish()
    }
}
