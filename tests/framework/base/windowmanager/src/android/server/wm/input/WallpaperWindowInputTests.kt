/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.server.wm.input

import android.app.WallpaperManager
import android.content.pm.PackageManager
import android.os.SystemClock
import android.platform.test.annotations.Presubmit
import android.server.wm.ActivityManagerTestBase
import android.server.wm.CliIntentExtra
import android.server.wm.TestJournalProvider
import android.server.wm.WindowManagerState
import android.server.wm.annotation.Group2
import android.server.wm.app.Components
import android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import com.android.compatibility.common.util.PollingCheck
import junit.framework.AssertionFailedError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceInput:WallpaperWindowInputTests
 */
@Presubmit
@Group2
class WallpaperWindowInputTests : ActivityManagerTestBase() {
    private var lastMotionEvent: MotionEvent? = null

    @Before
    fun setup() {
        val wallpaperManager = WallpaperManager.getInstance(mContext)
        assumeTrue("Device does not support wallpapers", wallpaperManager.isWallpaperSupported)
        assumeTrue(
            "Device does not support live wallpapers",
            mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)
        )
    }

    @Test
    fun testShowWallpaper_withTouchEnabled() {
        val wallpaperSession = createManagedChangeWallpaperSession()
        wallpaperSession.setWallpaperComponent(Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE)

        launchActivity(
            Components.WALLPAPER_TARGET_ACTIVITY,
            CliIntentExtra.extraBool(
                Components.WallpaperTargetActivity.EXTRA_ENABLE_WALLPAPER_TOUCH,
                true
            )
        )
        mWmState.waitAndAssertWindowShown(WindowManager.LayoutParams.TYPE_WALLPAPER, true)
        TestJournalProvider.TestJournalContainer.start()
        val task = mWmState.getTaskByActivity(Components.WALLPAPER_TARGET_ACTIVITY)
        val motionEvent = getDownEventForTaskCenter(task)
        mInstrumentation.uiAutomation.injectInputEvent(motionEvent, true, true)

        PollingCheck.waitFor(
            2000,
            { this.updateLastMotionEventFromTestJournal() },
            "Waiting for wallpaper to receive the touch events"
        )

        assertNotNull(lastMotionEvent)
        assertMotionEvent(lastMotionEvent, motionEvent)
    }

    @Test
    fun testShowWallpaper_withWallpaperTouchDisabled() {
        val wallpaperSession = createManagedChangeWallpaperSession()
        wallpaperSession.setWallpaperComponent(Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE)

        launchActivity(
            Components.WALLPAPER_TARGET_ACTIVITY,
            CliIntentExtra.extraBool(
                Components.WallpaperTargetActivity.EXTRA_ENABLE_WALLPAPER_TOUCH,
                false
            )
        )
        mWmState.waitAndAssertWindowShown(WindowManager.LayoutParams.TYPE_WALLPAPER, true)

        TestJournalProvider.TestJournalContainer.start()
        val task = mWmState.getTaskByActivity(Components.WALLPAPER_TARGET_ACTIVITY)
        val motionEvent = getDownEventForTaskCenter(task)
        mInstrumentation.uiAutomation.injectInputEvent(motionEvent, true, true)

        val failMsg = "Waiting for wallpaper to receive the touch events"
        val exception: Throwable = assertThrows(
            AssertionFailedError::class.java
        ) {
            PollingCheck.waitFor(
                2000,
                { this.updateLastMotionEventFromTestJournal() },
                "Waiting for wallpaper to receive the touch events"
            )
        }
        assertEquals(failMsg, exception.message)
    }

    private fun updateLastMotionEventFromTestJournal(): Boolean {
        val journal =
            TestJournalProvider.TestJournalContainer.get(TestInteractiveLiveWallpaperKeys.COMPONENT)
        TestJournalProvider.TestJournalContainer.withThreadSafeAccess {
            lastMotionEvent =
                if (journal.extras.containsKey(
                        TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT)
                ) {
                    journal.extras.getParcelable(
                    TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT,
                    MotionEvent::class.java
                )
                } else {
                    null
                }
        }
        return lastMotionEvent != null
    }

    companion object {
        private const val TAG = "WallpaperWindowInputTests"

        private fun getDownEventForTaskCenter(task: WindowManagerState.Task): MotionEvent {
            // Get anchor coordinates on the screen
            val bounds = task.bounds
            val xOnScreen = bounds.width() / 2
            val yOnScreen = bounds.height() / 2
            val downTime = SystemClock.uptimeMillis()

            val eventDown = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                xOnScreen.toFloat(),
                yOnScreen.toFloat(),
                1
            )
            eventDown.source = InputDevice.SOURCE_TOUCHSCREEN

            return eventDown
        }

        private fun assertMotionEvent(event: MotionEvent?, expectedEvent: MotionEvent) {
            assertEquals("$TAG (action)", event!!.action, expectedEvent.action)
            assertEquals("$TAG (source)", event.source, expectedEvent.source)
            assertEquals("$TAG (down time)", event.downTime, expectedEvent.downTime)
            assertEquals("$TAG (event time)", event.eventTime, expectedEvent.eventTime)
            assertEquals("$TAG (position x)", event.x, expectedEvent.x, 0.01f)
            assertEquals("$TAG (position y)", event.y, expectedEvent.y, 0.01f)
        }
    }
}
