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
package android.contextualsearch.caller

import android.app.Activity
import android.app.contextualsearch.ContextualSearchConfig
import android.app.contextualsearch.ContextualSearchManager
import android.os.Bundle
import com.android.compatibility.common.util.BroadcastMessenger

/**
 * This activity is used to test Contextual Search Manager Service interactions with activities
 * from a separate app.
 */
class CallerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(ContextualSearchManager::class.java)
        try {
            val config = intent.getParcelableExtra(
                EXTRA_CONTEXTUAL_SEARCH_CONFIG,
                ContextualSearchConfig::class.java
            )
            manager.startContextualSearch(this, config)
        } catch (e: SecurityException) {
            BroadcastMessenger.send(
                this,
                ContextualSearchMessage.TAG,
                ContextualSearchMessage(ContextualSearchMessage.RESULT_EXCEPTION)
            )
        } catch (e: Exception) {
            throw RuntimeException("Caught unexpected exception", e)
        }
        finish()
    }

    companion object {
        private val TAG: String = "CallerActivity"
        const val EXTRA_CONTEXTUAL_SEARCH_CONFIG =
            "android.contextualsearch.caller.EXTRA_CONTEXTUAL_SEARCH_CONFIG"
    }
}
