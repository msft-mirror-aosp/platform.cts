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

package com.android.cts.verifier.sysui

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.AppTask
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.android.cts.verifier.R

class PinnedSampleActivity : Activity() {

    private val activityManager by lazy { getSystemService(ActivityManager::class.java) }

    private lateinit var enterPinnedBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sysui_pinned_sample_activity)

        enterPinnedBtn = findViewById(R.id.button_enter_pinned)
        enterPinnedBtn.setOnClickListener {
            val task = activityManager.appTasks.first()
            task.requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED, mainExecutor) { code ->
                if (code == AppTask.WINDOWING_LAYER_REQUEST_REJECTED) {
                    Toast.makeText(
                            this,
                            R.string.pinned_layer_failed_enter_pinned,
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }
        }
    }
}
