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
 *
 */

package com.android.cts.windowmanager.deviceside

import android.content.ComponentName
import android.os.Process
import android.server.wm.ShellCommandHelper
import android.server.wm.UiDeviceUtils
import android.server.wm.WakeUpAndUnlockRule
import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerStateHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.UiAutomatorUtils2
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistAcrossRebootsActivityTest {
    @get:Rule
    val mWakeUpAndUnlockRule = WakeUpAndUnlockRule()

    val mWmState = WindowManagerStateHelper()

    @Before
    fun setUp() {
        ShellCommandHelper.executeShellCommand(LAUNCH_ACTIVITY_COMMAND)
        mWmState.waitForActivityState(
            PERSIST_ACROSS_REBOOTS_COMPONENT,
            WindowManagerState.STATE_RESUMED
        )
    }

    @After
    fun tearDown() {
        UiDeviceUtils.pressHomeButton()
        mWmState.waitForActivityState(
            PERSIST_ACROSS_REBOOTS_COMPONENT,
            WindowManagerState.STATE_STOPPED
        )
    }

    @Test
    fun testShowDefaultValue_preReboot() {
        UiAutomatorUtils2.waitFindObject(By.text(DEFAULT_VALUE))
    }

    @Test
    fun testShowPersistedValue_postReboot() {
        UiAutomatorUtils2.waitFindObject(By.text(PERSISTED_VALUE))
    }

    companion object {
        private val PERSIST_ACROSS_REBOOTS_COMPONENT =
            ComponentName.createRelative(
                "com.android.cts.windowmanager.deviceside",
                ".PersistAcrossRebootsActivity"
            )
        private val LAUNCH_ACTIVITY_COMMAND =
            String.format(
                "am start -n %s --user %d",
                PERSIST_ACROSS_REBOOTS_COMPONENT.flattenToShortString(),
                Process.myUserHandle().identifier
            )

        private const val DEFAULT_VALUE = "default_value"
        private const val PERSISTED_VALUE = "persisted_value"
    }
}
