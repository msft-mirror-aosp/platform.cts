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
 *
 */

package com.android.cts.windowmanager.deviceside

import android.app.Activity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.TextView

class PersistAcrossRebootsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate(Bundle)")
        super.onCreate(savedInstanceState)
        showContent(null)
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        Log.i(TAG, "onCreate(Bundle, PersistableBundle)")
        super.onCreate(savedInstanceState, persistentState)
        showContent(persistentState)
    }

    private fun showContent(persistentState: PersistableBundle?) {
        setContentView(R.layout.persist_across_reboots_layout)
        val view = findViewById<TextView>(R.id.text_view)
        view.text = persistentState?.getString(EXTRA_KEY) ?: DEFAULT_VALUE
        Log.i(TAG, "showContent(${view.text})")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.i(TAG, "onSaveInstanceState(Bundle)")
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_KEY, SAVED_VALUE)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        Log.i(TAG, "onSaveInstanceState(Bundle, PersistableBundle)")
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putString(EXTRA_KEY, SAVED_VALUE)
        outPersistentState.putString(EXTRA_KEY, PERSISTED_VALUE)
    }

    companion object {
        private const val TAG = "PersistAcrossRebootsActivity"
        private const val EXTRA_KEY = "extra_key"

        private const val DEFAULT_VALUE = "default_value"
        private const val SAVED_VALUE = "saved_value"
        private const val PERSISTED_VALUE = "persisted_value"
    }
}
