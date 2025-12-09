/*
 * Copyright 2024 The Android Open Source Project
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Service which plays some audio. */
open class PlaybackService : MyForegroundService() {

    private val mPlaybackChannel = Channel<Boolean>(Channel.UNLIMITED);

    private var mDidStart = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mScope.launch {
            Log.i(TAG, "Receive onStartCommand action: ${intent.getAction()}")
            when (intent.getAction()) {
                PREFIX + ACTION_START_PLAYBACK -> {
                    if (!mDidStart) {
                        launch(CoroutineName("Playback") + Dispatchers.IO) {
                            playback()
                        }
                        mDidStart = true;
                    }
                    mPlaybackChannel.send(true)
                }
                PREFIX + ACTION_STOP_PLAYBACK ->
                    mPlaybackChannel.send(false)
                PREFIX + ACTION_FINISH_PLAYBACK ->
                    mPlaybackChannel.close()
                PREFIX + ACTION_START_BACKGROUND -> {} // nothing to do
                PREFIX + ACTION_START_FOREGROUND ->
                    intent.getIntExtra(EXTRA_CAP_OVERRIDE, getCapabilities()).let {
                        Log.i(TAG, "Going foreground with capabilities $it")
                        startForeground(1, buildNotification("playback"), it)
                    }
                PREFIX + ACTION_STOP_FOREGROUND -> stopForeground(STOP_FOREGROUND_REMOVE)
                PREFIX + ACTION_TEARDOWN -> {
                    // Finish coroutine
                    mPlaybackChannel.close()
                    // Mark supervisor complete, completer will fire when all children complete.
                    mJob.complete()
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Plays some audio, controlled by mPlaybackChannel.
     */
    suspend fun playback() {
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val sampleRate = 48000
        val format = AudioFormat.ENCODING_PCM_16BIT
        val audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .build()

        var isPlaying = false
        val data = ShortArray(audioTrack.getBufferSizeInFrames() * 2)

        try {
            while (coroutineContext.isActive) {
                val newIsPlaying = computeNextState(mPlaybackChannel, isPlaying) ?: break
                if (!isPlaying && newIsPlaying) {
                    audioTrack.play()
                    mScope.launch { respond(ACTION_PLAYBACK_STARTED) }
                } else if (isPlaying && !newIsPlaying) {
                    audioTrack.stop()
                    mScope.launch { respond(ACTION_PLAYBACK_STOPPED) }
                }
                isPlaying = newIsPlaying
                if (isPlaying) {
                    for (i in data.indices) {
                        data[i] = Random.nextInt(-2000, 2000).toShort()
                    }
                    audioTrack.write(data, 0, data.size).also {
                        if (it < 0) throw IllegalStateException(
                            "AudioTrack write invalid result: $it")
                    }
                }
            }
        } finally {
            if (isPlaying) {
                audioTrack.stop()
                mScope.launch { respond(ACTION_PLAYBACK_STOPPED) }
            }
            audioTrack.release()
            mScope.launch { respond(ACTION_PLAYBACK_FINISHED) }
        }
    }
}
