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

package android.sysui.cts

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.ShowPowerMenuCallback
import android.app.StatusBarManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.interactive.Step
import com.android.interactive.annotations.Interactive
import com.android.interactive.steps.sysui.IsPowerMenuVisible
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowPowerMenuInteractiveTest {

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()

        private fun performGlobalAction(action: Int) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(action)
        }
    }

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(android.app.Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    @Test
    @Interactive
    @EnsureHasPermission(Manifest.permission.SHOW_POWER_MENU)
    @ApiTest(apis = ["android.app.StatusBarManager#showPowerMenu"])
    fun showPowerMenu() {
        val latch = CountDownLatch(1)
        val statusBarManager = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(StatusBarManager::class.java)
        assumeTrue(statusBarManager != null)
        val callback = Callback(latch)
        statusBarManager.showPowerMenu(Runnable::run, callback)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(CallbackStatus.POWER_MENU_TRUE, callback.callbackStatus)
        assertTrue(Step.execute(IsPowerMenuVisible::class.java))
    }

    @RequiresFlagsEnabled(android.app.Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    @Test
    @Interactive
    @EnsureHasPermission(Manifest.permission.SHOW_POWER_MENU)
    @ApiTest(apis = ["android.app.StatusBarManager#showPowerMenu"])
    fun showPowerMenu_multipleCallsInShortTime() {
        val latch = CountDownLatch(2)
        val statusBarManager = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(StatusBarManager::class.java)
        assumeTrue(statusBarManager != null)
        val callback1 = Callback(latch)
        val callback2 = Callback(latch)
        statusBarManager.showPowerMenu(Runnable::run, callback1)
        statusBarManager.showPowerMenu(Runnable::run, callback2)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(CallbackStatus.POWER_MENU_TRUE, callback1.callbackStatus)
        assertEquals(CallbackStatus.POWER_MENU_TRUE, callback2.callbackStatus)
        assertTrue(Step.execute(IsPowerMenuVisible::class.java))
    }

    @RequiresFlagsEnabled(android.app.Flags.FLAG_STATUSBAR_API_SHOW_POWER_MENU)
    @Test
    @Interactive
    @EnsureHasPermission(Manifest.permission.SHOW_POWER_MENU)
    @ApiTest(apis = ["android.app.StatusBarManager#showPowerMenu"])
    fun showPowerMenu_alreadyShowing() {
        val statusBarManager = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(StatusBarManager::class.java)
        assumeTrue(statusBarManager != null)
        // Show power menu. We know that this should succeed.

        val latch1 = CountDownLatch(1)
        val callback1 = Callback(latch1)
        statusBarManager.showPowerMenu(Runnable::run, callback1)
        assertTrue(latch1.await(5L, TimeUnit.SECONDS))
        assertEquals(CallbackStatus.POWER_MENU_TRUE, callback1.callbackStatus)

        // Now try to show again
        val latch2 = CountDownLatch(1)
        val callback2 = Callback(latch2)

        statusBarManager.showPowerMenu(Runnable::run, callback2)
        assertTrue(latch2.await(5, TimeUnit.SECONDS))
        assertEquals(CallbackStatus.POWER_MENU_TRUE, callback2.callbackStatus)
        assertTrue(Step.execute(IsPowerMenuVisible::class.java))
    }

    private class Callback(private val latch: CountDownLatch) : ShowPowerMenuCallback {
        var callbackStatus: CallbackStatus = CallbackStatus.NOT_CALLED
            private set

        override fun onPowerMenuShown(showing: Boolean) {
            callbackStatus = if (showing) {
                CallbackStatus.POWER_MENU_TRUE
            } else {
                CallbackStatus.POWER_MENU_FALSE
            }
            latch.countDown()
        }

        override fun onError(error: Int) {
            callbackStatus = when (error) {
                ShowPowerMenuCallback.ERROR_TIMEOUT -> CallbackStatus.ERROR_TIMEOUT
                else -> CallbackStatus.ERROR_UNKNOWN
            }
            latch.countDown()
        }
    }

    private enum class CallbackStatus {
        NOT_CALLED,
        POWER_MENU_TRUE,
        POWER_MENU_FALSE,
        ERROR_TIMEOUT,
        ERROR_UNKNOWN,
    }

    @After
    fun goHome() {
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
