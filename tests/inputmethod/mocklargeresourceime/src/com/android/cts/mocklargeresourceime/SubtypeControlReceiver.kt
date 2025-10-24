/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.cts.mocklargeresourceime

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

class SubtypeControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive: $intent")
        if (intent?.action != ACTION_SET_ADDITIONAL_SUBTYPES) {
            return
        }

        val imm = context.getSystemService(InputMethodManager::class.java)
        val imeId: String =
            ComponentName(
                context,
                MethodWithManyAdditionalSubtypes::class.java
            ).flattenToShortString()

        val additionalSubtypes: MutableList<InputMethodSubtype?> =
            intent.getParcelableArrayListExtra(
                EXTRA_ADDITIONAL_SUBTYPES,
                InputMethodSubtype::class.java,
            ) ?: ArrayList<InputMethodSubtype?>()

        imm!!.setAdditionalInputMethodSubtypes(imeId, additionalSubtypes.toTypedArray())
        Log.d(TAG, "Set ${additionalSubtypes.size} additional subtypes for $imeId")
    }

    companion object {
        const val ACTION_SET_ADDITIONAL_SUBTYPES =
            "com.android.cts.mocklargeresourceime.ACTION_SET_ADDITIONAL_SUBTYPES"
        const val EXTRA_ADDITIONAL_SUBTYPES =
            "com.android.cts.mocklargeresourceime.EXTRA_ADDITIONAL_SUBTYPES"

        private val TAG = SubtypeControlReceiver::class.java.simpleName
    }
}
