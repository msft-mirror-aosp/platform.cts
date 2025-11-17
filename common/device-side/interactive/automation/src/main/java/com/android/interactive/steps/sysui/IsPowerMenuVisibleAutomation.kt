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

package com.android.interactive.steps.sysui

import android.platform.uiautomatorhelpers.DeviceHelpers.waitForNullableObj
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import com.android.interactive.Automation
import com.android.interactive.annotations.AutomationFor
import java.util.regex.Pattern

@AutomationFor("com.android.interactive.steps.sysui.IsPowerMenuVisible")
class IsPowerMenuVisibleAutomation : Automation<Boolean> {
    override fun automate(): Boolean {
        val found = waitForNullableObj(
            POWER_PANEL_SELECTOR,
        ) != null
        Log.d("InteractiveAutomation", "Power menu found? $found",)
        return found
    }

    private companion object {
        private const val SYSUI_PACKAGE = "com.android.systemui"

        @JvmStatic
        fun sysuiResSelector(resourceId: String): BySelector =
            By.pkg(SYSUI_PACKAGE).res(SYSUI_PACKAGE, resourceId)

        private val POWER_OFF_BUTTON_SELECTOR =
            By.text(Pattern.compile("Power off", Pattern.CASE_INSENSITIVE))

        val POWER_PANEL_SELECTOR = sysuiResSelector("global_actions_view")
            .hasDescendant(sysuiResSelector("list_flow"))
            .hasDescendant(POWER_OFF_BUTTON_SELECTOR)
    }
}
