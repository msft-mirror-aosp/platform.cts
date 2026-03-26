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

package android.media.audio.cts.audiopermissiontests

import android.media.audio.cts.audiopermissiontests.common.*

import android.Manifest.permission.MODIFY_AUDIO_ROUTING
import android.content.Intent
import android.media.AudioManager
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Process
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.util.Log

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil
import com.android.media.audio.Flags
import com.android.media.mediatestutils.TestUtils.getFutureForIntent

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture

import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore;
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException;

// Note, these tests can't run concurrently with other recording tests
@AppModeFull(reason = "Test requires intents between multiple apps")
@RunWith(AndroidJUnit4::class)
class AudioPlaybackForegroundTests {
    companion object {
        const val TAG = "AudioPlaybackForegroundTests"
        // Keep in sync with test apps
        const val TEST_PACKAGE = "android.media.audio.cts.CtsPlaybackService"

        const val SERVICE_NAME = ".PlaybackService"

        const val FUTURE_WAIT_SECS = 10L
        const val FALSE_NEG_SECS = 3L

        const val TEST_APPOPS_CONSTS =
                "top_state_settle_time=0,fg_service_state_settle_time=0,bg_state_settle_time=0"

        const val MUTED_BY_OP_CONTROL_AUDIO = 1 shl 7

        @ClassRule @JvmField
        val sPermRule = SettingsRule(Settings.Global.APP_OPS_CONSTANTS, TEST_APPOPS_CONSTS)
    }

    @Rule @JvmField
    val mPermRule = AdoptShellPermissionsRule(
            getInstrumentation().getUiAutomation(), MODIFY_AUDIO_ROUTING)

    @Rule @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private val mInstrumentation = getInstrumentation()
    private val mContext = mInstrumentation.getContext()
    private val mAudioManager = mContext.getSystemService(AudioManager::class.java)!!
    // Used in teardown
    private val mServiceStartedPackages = HashSet<String>()
    private val mActivityStartedPackages = HashSet<String>()
    private val mFutures = mutableListOf<ListenableFuture<*>>()


    private var mTestUid: Int = -1

    @Before
    fun setup() {
        sPermRule.checkSetup()
        mTestUid = mContext.getPackageManager().getPackageUid(TEST_PACKAGE, /* flags= */ 0)
    }

    @After
    fun teardown() {
        // Clean up any left-over activities, services
        for (pack in mActivityStartedPackages.toSet()) {
            Log.i(TAG, "Stopping  leftover activity: " + pack)
            stopActivity(pack)
        }
        for (pack in mServiceStartedPackages.toSet()) {
            Log.i(TAG, "Stopping  leftover service : " + pack)
            stopService(pack)
        }
        for (future in mFutures.toSet()) {
            future.cancel(false)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testShellInstrumentedPlayback_isNotMuted() {
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

        val future = getPlayerFutureForPred{ x ->
            x.getClientUid() == Process.myUid() &&
            x.isMuted() &&
            x.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO == MUTED_BY_OP_CONTROL_AUDIO
        }

        val playbackJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                audioTrack.play()
                while (coroutineContext.isActive) {
                    for (i in data.indices) {
                        data[i] = Random.nextInt(-2000, 2000).toShort()
                    }
                    audioTrack.write(data, 0, data.size)
                }
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }

        try {
            assertThrows(TimeoutException::class.java) {
                future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
            }
        } finally {
            playbackJob.cancel()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testStartPlaybackInBackground_isMuted() {
        startService(TEST_PACKAGE, false)
        Thread.sleep(1000)
        val future = getPlayerFuture(true)

        startServicePlayback(TEST_PACKAGE)

        val apc = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertTrue(apc.isMuted() &&
            (apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO) == MUTED_BY_OP_CONTROL_AUDIO)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testStartPlaybackTop_isNotMuted() {
        startActivity(TEST_PACKAGE)
        Thread.sleep(1000)
        val future = getPlayerFuture(true)

        startServicePlayback(TEST_PACKAGE)

        assertThrows(TimeoutException::class.java) {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
        }

        val apc = mAudioManager.getActivePlaybackConfigurations().find {
            x -> x.getClientUid() == mTestUid
        }!!
        assertEquals(0, apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testStartPlaybackFgs_isNotMuted() {
        startService(TEST_PACKAGE, true)
        Thread.sleep(1000)
        val future = getPlayerFuture(true)

        startServicePlayback(TEST_PACKAGE)

        assertThrows(TimeoutException::class.java) {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
        }

        val apc = mAudioManager.getActivePlaybackConfigurations().find {
            x -> x.getClientUid() == mTestUid
        }!!
        assertEquals(0, apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testMovingFromTopToBackground_isMuted() {
        startActivity(TEST_PACKAGE)
        val future = getPlayerFuture(true)
        startServicePlayback(TEST_PACKAGE)
        // we should start with un-muted playback
        assertThrows(TimeoutException::class.java) {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
        }

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE)

        // Expect that the stream is muted. Future completes when APC change which mutes is received
        val apc = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertTrue(apc.isMuted() &&
            (apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO) == MUTED_BY_OP_CONTROL_AUDIO)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testMovingFromTopToFgs_isNotMuted() {
        startActivity(TEST_PACKAGE)
        val future = getPlayerFuture(true)
        startServicePlayback(TEST_PACKAGE)
        // We should start with un-muted playback
        assertThrows(TimeoutException::class.java) {
            future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
        }

        startService(TEST_PACKAGE, true)
        // Move out of TOP to FGS
        stopActivity(TEST_PACKAGE)

        // We should continue un-muted playback
        assertThrows(TimeoutException::class.java, { future.get(FALSE_NEG_SECS, TimeUnit.SECONDS) })

        val apc = mAudioManager.getActivePlaybackConfigurations().find {
            x -> x.getClientUid() == mTestUid }!!
        assertEquals(0, apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    @Ignore
    fun testMovingFromBackgroundToFgs_isUnMuted() {
        startService(TEST_PACKAGE, false)
        val setupFuture = getPlayerFuture(true)
        startServicePlayback(TEST_PACKAGE)
        // we should start in the muted state
        val setupApc = setupFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertTrue(setupApc.isMuted() &&
            (setupApc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO) == MUTED_BY_OP_CONTROL_AUDIO)

        val future = getPlayerFuture(false)

        // Move from bg to fgs
        startService(TEST_PACKAGE, true)

        // This future completes when we get an un-muted player event
        val apc = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertEquals(0, apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO)
    }

    fun startService(packageName: String, isFg: Boolean) {
        // To get around background launch restrictions, the app has to start services, so
        // temporarily make it TOP
        val shouldLaunch = !mActivityStartedPackages.contains(packageName)
        if (shouldLaunch) startActivity(packageName)

        mContext.startService(getIntentForAction(packageName,
                if (isFg) {ACTION_START_FOREGROUND} else {ACTION_START_BACKGROUND}))

        mServiceStartedPackages.add(packageName)
        if (shouldLaunch) stopActivity(packageName)
        // We have to wait until the app is actually running to mark it freezer ineligible
        unfreezePackage(packageName)
    }

    fun startActivity(packageName: String) {
        if (mActivityStartedPackages.contains(packageName)) {
            return;
        }
        val future = makeFuture(packageName, ACTION_ACTIVITY_STARTED)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(packageName, packageName + ".SimpleActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        mInstrumentation.getTargetContext().startActivity(intent)
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        mActivityStartedPackages.add(packageName)
        unfreezePackage(packageName)
    }

    fun stopActivity(packageName: String) {
        val future = makeFuture(packageName, ACTION_ACTIVITY_FINISHED)
        mContext.sendBroadcast(
                Intent(packageName + ACTION_FINISH_ACTIVITY).setPackage(packageName))
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        mActivityStartedPackages.remove(packageName)
    }

    fun startServicePlayback(packageName: String) {
        val intent = getIntentForAction(packageName, ACTION_START_PLAYBACK)
        val future = makeFuture(packageName, ACTION_PLAYBACK_STARTED)

        mContext.startService(intent)
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)

        mServiceStartedPackages.add(packageName)
        // We have to wait until the app is actually running to mark it freezer ineligible
        unfreezePackage(packageName)
    }

    fun stopService(packageName: String) {
        val future = makeFuture(packageName, ACTION_TEARDOWN_FINISHED)
        mContext.startService(getIntentForAction(packageName, ACTION_TEARDOWN))
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        mServiceStartedPackages.remove(packageName)
        // Just in case
        mInstrumentation
                .getTargetContext()
                .stopService(Intent().setClassName(packageName, packageName + SERVICE_NAME))
    }

    private fun getIntentForAction(packageName: String, action: String) = Intent()
                .setClassName(packageName, packageName + SERVICE_NAME)
                .setAction(packageName + action)

    private fun makeFuture(packageName: String, action: String) =
            getFutureForIntent(mContext, packageName + action)

    private fun unfreezePackage(packageName: String) =
            SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky $packageName")

    private fun getPlayerFuture(expectedMuteState: Boolean) = getPlayerFutureForPred { apc ->
        apc.getClientUid() == mTestUid && when (expectedMuteState) {
            true -> apc.isMuted() &&
                    (apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO) == MUTED_BY_OP_CONTROL_AUDIO
            false -> (apc.getMutedBy() and MUTED_BY_OP_CONTROL_AUDIO) == 0
        }
    }

    private fun getPlayerFutureForPred(pred: (AudioPlaybackConfiguration) -> Boolean) :
            ListenableFuture<AudioPlaybackConfiguration> {
        val future: SettableFuture<AudioPlaybackConfiguration> = SettableFuture.create()
        mAudioManager.registerAudioPlaybackCallback(object : AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs:
                            MutableList<AudioPlaybackConfiguration>) {
                        configs.find { x ->
                            pred(x)
                        }?.let {
                            future.set(it)
                        }
                    }
                }.also {
                    // direct executor fine, AudioManager is re-entrant
                    future.addListener({ mAudioManager.unregisterAudioPlaybackCallback(it) },
                                       MoreExecutors.directExecutor())
                },
                null)
        mFutures.add(future)
        return future
    }
}
