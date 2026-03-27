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

package android.media.audio.cts.audiopermissiontests

import android.Manifest.permission.MODIFY_AUDIO_ROUTING
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_IGNORED
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.ACTION_VOLUME_CHANGED
import android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import android.media.audio.cts.audiopermissiontests.common.*
import android.media.AudioManager.EXTRA_VOLUME_STREAM_TYPE
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioManager.STREAM_MUSIC
import android.platform.test.annotations.AppModeFull
import com.android.compatibility.common.util.ApiTest
import org.junit.Assume.assumeTrue
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation

import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil
import com.android.media.audio.Flags
import com.android.media.mediatestutils.TestUtils.getFutureForIntent

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "Test requires intents between multiple apps")
@RunWith(AndroidJUnit4::class)
class AudioControlForegroundTests {
    companion object {
        const val OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS =
            "android:system_exempt_from_power_restrictions"

        const val OPSTR_CONTROL_AUDIO = "android:control_audio"

        const val TAG = "AudioControlForegroundTests"
        // Keep in sync with test apps
        const val TEST_PACKAGE = "android.media.audio.cts.CtsPlaybackService"

        const val SERVICE_NAME = ".ControlService"

        const val FUTURE_WAIT_SECS = 10L
        const val FALSE_NEG_SECS = 3L

        const val TEST_APPOPS_CONSTS =
            "top_state_settle_time=0,fg_service_state_settle_time=0,bg_state_settle_time=0"

        @ClassRule
        @JvmField
        val sPermRule = SettingsRule(Settings.Global.APP_OPS_CONSTANTS, TEST_APPOPS_CONSTS)
    }

    @Rule @JvmField val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule
    @JvmField
    val mPermRule =
        AdoptShellPermissionsRule(getInstrumentation().getUiAutomation(), MODIFY_AUDIO_ROUTING)

    private val mInstrumentation = getInstrumentation()
    private val mContext = mInstrumentation.getContext()
    private val mAudioManager = mContext.getSystemService(AudioManager::class.java)!!
    private val mAppOpsManager = mContext.getSystemService(AppOpsManager::class.java)!!

    // Used in teardown
    private var mStartedActivity = false
    private var mStartedService = false

    private var mTestUid: Int = -1

    @Before
    fun setup() {
        sPermRule.checkSetup()
        mTestUid = mContext.getPackageManager().getPackageUid(TEST_PACKAGE, /* flags= */ 0)

        val pm = mContext.getPackageManager()
        val isAutomotive = pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val isTelevision = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val isFixedVolume = mContext.getResources().getBoolean(
            Resources.getSystem().getIdentifier("config_useFixedVolume", "bool", "android")
        )
        val isSingleVolume = mContext.getResources().getBoolean(
            Resources.getSystem().getIdentifier("config_single_volume", "bool", "android")
        )
        assumeTrue("Not supported on automotive", !isAutomotive)
        assumeTrue("Not supported on television", !isTelevision)
        assumeTrue("Not supported on fixed volume", !isFixedVolume)
        assumeTrue("Not supported on single volume", !isSingleVolume)
    }

    @After
    fun teardown() {
        if (mStartedActivity) stopActivity()
        if (mStartedService) stopService()
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#requestAudioFocus"])
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testRequestFocusInBackground_isDenied() {
        startService(false)
        val future = makeFuture(ACTION_FOCUS_REQUESTED)
        waitForOpState(OPSTR_CONTROL_AUDIO, MODE_IGNORED)
        sendCommand(ACTION_REQUEST_FOCUS)

        val intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertEquals(intent.getIntExtra(EXTRA_FOCUS_RESULT, -1), AUDIOFOCUS_REQUEST_FAILED)
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#requestAudioFocus"])
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testRequestFocusInTop_isAllowed() {
        startActivity()
        val future = makeFuture(ACTION_FOCUS_REQUESTED)
        sendCommand(ACTION_REQUEST_FOCUS)

        val intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertEquals(intent.getIntExtra(EXTRA_FOCUS_RESULT, -1), AUDIOFOCUS_REQUEST_GRANTED)
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#requestAudioFocus"])
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL)
    fun testRequestFocusInFgs_isAllowed() {
        startService(true)
        val future = makeFuture(ACTION_FOCUS_REQUESTED)
        sendCommand(ACTION_REQUEST_FOCUS)

        val intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        assertEquals(intent.getIntExtra(EXTRA_FOCUS_RESULT, -1), AUDIOFOCUS_REQUEST_GRANTED)
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#setStreamVolume"])
    @RequiresFlagsEnabled(Flags.FLAG_HARDENING_PARTIAL_VOLUME)
    fun testAdjustVolumeInBackground_isDenied() {
        startService(false)
        waitForOpState(OPSTR_CONTROL_AUDIO, MODE_IGNORED)

        val future = getFutureForIntent(mContext, AudioManager.ACTION_VOLUME_CHANGED) {
            it != null && it.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
        }

        val mediaVol = triggerVolumeChange()

        try {
            assertThrows(TimeoutException::class.java) {
                future.get(FALSE_NEG_SECS, TimeUnit.SECONDS)
            }
        } finally {
            if (future.isDone()) {
                val cleanupFuture = getFutureForIntent(mContext, ACTION_VOLUME_CHANGED) {
                    it != null && it.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
                }
                mAudioManager.setStreamVolume(STREAM_MUSIC, mediaVol, 0)
                cleanupFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
            } else {
                future.cancel(false);
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#setStreamVolume"])
    fun testAdjustVolumeInFgs_isAllowed() {
        startService(true)
        Thread.sleep(FALSE_NEG_SECS * 1000)

        val future = getFutureForIntent(mContext, ACTION_VOLUME_CHANGED) {
            it != null && it.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
        }

        val mediaVol = triggerVolumeChange()

        try {
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        } finally {
            if (future.isDone()) {
                val cleanupFuture = getFutureForIntent(mContext, ACTION_VOLUME_CHANGED) {
                    it != null && it.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
                }
                mAudioManager.setStreamVolume(STREAM_MUSIC, mediaVol, 0)
                cleanupFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
            } else {
                future.cancel(false);
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.media.AudioManager#setStreamVolume"])
    fun testAdjustVolumeInTop_isAllowed() {
        startActivity()

        val future = getFutureForIntent(mContext, ACTION_VOLUME_CHANGED) {
            it != null && it.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
        }

        val mediaVol = triggerVolumeChange()

        try {
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        } finally {
            if (future.isDone()) {
                val cleanupFuture = getFutureForIntent(mContext, ACTION_VOLUME_CHANGED) {
                    it != null && it.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == STREAM_MUSIC
                }
                mAudioManager.setStreamVolume(STREAM_MUSIC, mediaVol, 0)
                cleanupFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
            } else {
                future.cancel(false);
            }
        }
    }


    fun sendCommand(action: String, intentF: (Intent) -> Unit = {}) =
        mContext.startService(getIntentForAction(TEST_PACKAGE, action).also(intentF))

    fun startService(isForeground: Boolean) {
        // To get around background launch restrictions, the app has to start services, so
        // temporarily make it TOP
        val shouldLaunch = !mStartedActivity
        if (shouldLaunch) startActivity()

        mContext.startService(
            getIntentForAction(
                TEST_PACKAGE,
                if (isForeground) {
                    ACTION_START_FOREGROUND
                } else {
                    ACTION_START_BACKGROUND
                },
            )
        )

        // We have to wait until the app is actually running to mark it freezer ineligible
        unfreezePackage(TEST_PACKAGE)
        if (shouldLaunch) stopActivity()
        mStartedService = true
    }

    fun startActivity() {
        if (mStartedActivity) return

        val future = makeFuture(ACTION_ACTIVITY_STARTED)
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(TEST_PACKAGE, TEST_PACKAGE + ".SimpleActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        mInstrumentation.getTargetContext().startActivity(intent)
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        mStartedActivity = true
        unfreezePackage(TEST_PACKAGE)
    }

    fun stopActivity() {
        val future = makeFuture(ACTION_ACTIVITY_FINISHED)
        mContext.sendBroadcast(
            Intent(TEST_PACKAGE + ACTION_FINISH_ACTIVITY).setPackage(TEST_PACKAGE)
        )
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        mStartedActivity = false
    }

    fun stopService() {
        val future = makeFuture(ACTION_TEARDOWN_FINISHED)
        mContext.startService(getIntentForAction(TEST_PACKAGE, ACTION_TEARDOWN))
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
        // Just in case
        mInstrumentation
            .getTargetContext()
            .stopService(Intent().setClassName(TEST_PACKAGE, TEST_PACKAGE + SERVICE_NAME))
    }

    private fun makeFuture(action: String) = getFutureForIntent(mContext, TEST_PACKAGE + action)

    private fun waitForOpState(opstr: String, state: Int) {
        for (i in 0..20) { // 2s timeout
            val opState = mAppOpsManager.unsafeCheckOpNoThrow(opstr, mTestUid, TEST_PACKAGE)
            if (opState == state) return
            Thread.sleep(100)
        }
        // timed out - throw an exception to indicate failure
        assertTrue(false)
    }

    private fun getIntentForAction(packageName: String, action: String) =
        Intent()
            .setClassName(packageName, packageName + SERVICE_NAME)
            .setAction(packageName + action)

    private fun unfreezePackage(packageName: String) =
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky $packageName")

    private fun triggerVolumeChange(): Int {
        val mediaVol = mAudioManager.getStreamVolume(STREAM_MUSIC);
        val changeVol = if (mediaVol == mAudioManager.getStreamMaxVolume(STREAM_MUSIC)) {
            mediaVol - 1
        } else {
            mediaVol + 1
        }
        sendCommand(ACTION_SET_MEDIA_VOLUME) {
            it.putExtra(EXTRA_VOLUME_INDEX, changeVol)
        }
        return mediaVol
    }
}
