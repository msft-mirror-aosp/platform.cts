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

package android.voiceinteraction.testassistant

import android.app.Activity
import android.app.voiceinteraction.VoiceInteractionManager
import android.content.Intent
import android.os.Bundle

/** An activity that checks whether it can request user grant read screen context data or not. */
class GetReadScreenContextRequestStateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val voiceInteractionManager = getSystemService(VoiceInteractionManager::class.java)
        setResult(
            RESULT_OK,
            Intent().putExtra(
                EXTRA_REQUEST_STATE,
                voiceInteractionManager.getReadScreenContextRequestState()
            )
        )
        finish()
    }

    companion object {
        const val EXTRA_REQUEST_STATE =
            "android.voiceinteraction.testassistant.extra.REQUEST_STATE"
    }
}
