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

package android.media.audio.cts.audiopermissiontests.common

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.launch

/** Service which calls some AudioManager APIs . */
open class ControlService : MyForegroundService() {
    val mFocusReq: AudioFocusRequest =
        with(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)) {
                with(AudioAttributes.Builder()) {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    }
                    .let { setAudioAttributes(it.build()) }
                setOnAudioFocusChangeListener { focusChange ->
                    Log.i(TAG, "Focus change $focusChange")
                }
            }
            .build()

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mScope.launch {
            Log.i(TAG, "Receive onStartCommand action: ${intent.getAction()}")
            when (intent.getAction()) {
                PREFIX + ACTION_REQUEST_FOCUS -> {
                    val res =
                        getSystemService(AudioManager::class.java).requestAudioFocus(mFocusReq)
                    val action = ACTION_FOCUS_REQUESTED
                    Log.i(TAG, "Sending $action")
                    sendBroadcast(
                        Intent(PREFIX + action).apply {
                            setPackage(TARGET_PACKAGE)
                            putExtra(EXTRA_FOCUS_RESULT, res)
                        }
                    )
                }
                PREFIX + ACTION_START_BACKGROUND -> {} // nothing to do
                PREFIX + ACTION_START_FOREGROUND ->
                    intent.getIntExtra(EXTRA_CAP_OVERRIDE, getCapabilities()).let {
                        Log.i(TAG, "Going foreground with capabilities $it")
                        startForeground(1, buildNotification("playback"), it)
                    }
                PREFIX + ACTION_STOP_FOREGROUND -> stopForeground(STOP_FOREGROUND_REMOVE)
                PREFIX + ACTION_TEARDOWN -> {
                    getSystemService(AudioManager::class.java).abandonAudioFocusRequest(mFocusReq)
                    // Mark supervisor complete, completer will fire when all children complete.
                    mJob.complete()
                }
            }
        }
        return START_NOT_STICKY
    }
}
