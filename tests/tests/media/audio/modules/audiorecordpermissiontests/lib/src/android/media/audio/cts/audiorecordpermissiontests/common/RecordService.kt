/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.AttributionSource
import android.content.AttributionSource.myAttributionSource
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
import android.os.IBinder
import android.permission.PermissionManager
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Service which can records and sends response intents when recording moves between silenced and
 * unsilenced state.
 */
open class RecordService : Service() {
    val TAG = getAppName() + "RecordService"
    val PREFIX = "android.media.audio.cts." + getAppName()

    val mIsRecording = AtomicBoolean(false)
    val mExecutor = Executors.newFixedThreadPool(2)

    lateinit var mPermissionManager: PermissionManager
    val mAttributionSource: AtomicReference<AttributionSource> = AtomicReference();

    var mFuture : Future<Any>? = null

    override fun onCreate() {
        mPermissionManager = getSystemService(PermissionManager::class.java)
        mAttributionSource.set(mPermissionManager.registerAttributionSource(myAttributionSource()))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Receive onStartCommand action: ${intent.getAction()}")
        when (intent.getAction()) {
            PREFIX + ACTION_START_RECORD -> {
                if (mIsRecording.compareAndSet(false, true)) {
                    intent.getExtras()
                        ?.getBinder(EXTRA_ATTRIBUTION)
                        ?.let(IAttrProvider.Stub::asInterface)
                        ?.let { getAttribution(it) }
                        ?.let { next ->
                            mAttributionSource.get().let { old ->
                                    mPermissionManager.registerAttributionSource(
                                        AttributionSource.Builder(old.getUid())
                                                .setPackageName(old.getPackageName())
                                                .setNext(next)
                                                .build())
                            }
                        }
                        ?.let { mAttributionSource.set(it) }
                    mFuture = mExecutor.submit(::record, Object())
                }
            }
            PREFIX + ACTION_START_FOREGROUND -> {
                Log.i(TAG, "Going foreground with capabilities " + getCapabilities())
                startForeground(1, buildNotification(), getCapabilities())
            }
            PREFIX + ACTION_STOP_FOREGROUND -> stopForeground(STOP_FOREGROUND_REMOVE)
            PREFIX + ACTION_TEARDOWN -> teardown()
            PREFIX + ACTION_REQUEST_ATTRIBUTION ->
                sendBroadcast(
                    Intent(PREFIX + ACTION_SEND_ATTRIBUTION).apply {
                        setPackage(TARGET_PACKAGE)
                        putExtras(Bundle().apply {
                            putBinder(EXTRA_ATTRIBUTION, object: IAttrProvider.Stub() {
                                override fun inject(x: IAttrConsumer) = x.provideAttribution(
                                        mAttributionSource.get())
                        })
                        })
                    }
                )
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        teardown()
        mExecutor.shutdown()
    }

    // Binding cannot be used since that affects the proc state
    override fun onBind(intent: Intent) : IBinder? = null

    override fun getAttributionSource(): AttributionSource = mAttributionSource.get()

    /** For subclasses to return the package name for receiving intents. */
    open fun getAppName(): String = "Base"

    /** For subclasses to return the capabilities to start the service with. */
    open fun getCapabilities(): Int = 0

    /**
     * If recording, stop recording, send response intent, and stop the service
     */
    private fun teardown() {
        if (mIsRecording.compareAndSet(true, false)) {
            mFuture!!.get()
            mFuture = null
        }
        Log.i(TAG, "FINISH_TEARDOWN")
        sendBroadcast(Intent(PREFIX + ACTION_TEARDOWN_FINISHED).setPackage(TARGET_PACKAGE))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Continuously record while {@link mIsRecording} is true. Returns when false. Send intents as
     * stream moves in and out of being silenced.
     */
    private fun record() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val sampleRate = 32000
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, format)
        val audioRecord =
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setContext(this)
                .build()

        audioRecord.startRecording()
        var isSilenced = true
        var isStarted = false
        val data = ShortArray(bufferSizeInBytes / 2)

        val WARMUP = 800 // 25ms
        var warmupFrames = 0;
        while (warmupFrames < WARMUP) {
            warmupFrames += audioRecord.read(data, 0, data.size).also {
                if (it < 0) throw IllegalStateException("AudioRecord read invalid $it")
            }
        }

        while (mIsRecording.get()) {
            val result = audioRecord.read(data, 0, data.size)
            if (result < 0) {
                throw IllegalStateException("AudioRecord read invalid result: $result")
            } else if (result == 0) {
                continue
            }
            val newIsSilenced = data.take(result).map {abs(it.toInt())}.sum() == 0
            if (!isStarted || isSilenced != newIsSilenced) {
                (if (newIsSilenced) ACTION_BEGAN_RECEIVE_SILENCE else ACTION_BEGAN_RECEIVE_AUDIO)
                        .let {
                    mExecutor.execute {
                        Log.i(TAG, it)
                        sendBroadcast(Intent(PREFIX + it).setPackage(TARGET_PACKAGE))
                    }
                }
            }
            isSilenced = newIsSilenced
            isStarted = true
        }
        audioRecord.stop()
        audioRecord.release()
    }

    private fun getAttribution(prov: IAttrProvider) : AttributionSource {
        val res = CompletableFuture<AttributionSource>()
        prov.inject(object : IAttrConsumer.Stub() {
            override fun provideAttribution(attr: AttributionSource) = res.complete(attr).let {}
        })
        return res.get().also {
            Log.i(TAG, "Received attr source ${it}")
        }
    }

    /**
     * Create a notification which is required to start a foreground service
     */
    private fun buildNotification() : Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel("all", "All Notifications", NotificationManager.IMPORTANCE_NONE))

        return Notification.Builder(this, "all")
            .setContentTitle("Recording audio")
            .setContentText("recording...")
            .setSmallIcon(R.drawable.ic_fg)
            .build()
    }
}
