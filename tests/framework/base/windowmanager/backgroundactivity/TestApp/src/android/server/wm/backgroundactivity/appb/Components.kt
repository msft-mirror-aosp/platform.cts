/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.server.wm.backgroundactivity.appb

import android.server.wm.backgroundactivity.appa.Components.APP_A_BACKGROUND_ACTIVITY
import android.server.wm.backgroundactivity.appa.Components.APP_A_FOREGROUND_ACTIVITY
import android.server.wm.backgroundactivity.appa.Components.APP_A_START_PENDING_INTENT_ACTIVITY
import android.server.wm.backgroundactivity.appa.Components.APP_A_TEST_SERVICE
import android.server.wm.backgroundactivity.appa.Components.ForegroundActivityAction.ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX
import android.server.wm.backgroundactivity.appa.Components.buildFullActionName
import android.server.wm.component.ComponentsProvider

/** Constants for background activity test components in appb. */
object Components : ComponentsProvider() {

    @JvmField val APP_B_PACKAGE_NAME = packageName

    @JvmField val APP_B_TEST_SERVICE = component(APP_A_TEST_SERVICE.className)

    @JvmField val APP_B_BACKGROUND_ACTIVITY = component(APP_A_BACKGROUND_ACTIVITY.className)

    @JvmField val APP_B_FOREGROUND_ACTIVITY = component(APP_A_FOREGROUND_ACTIVITY.className)

    object ForegroundActivityAction {
        @JvmField
        val APP_B_LAUNCH_BACKGROUND_ACTIVITIES =
            ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX.toFullAction()
    }

    @JvmField
    val APP_B_START_PENDING_INTENT_ACTIVITY =
        component(APP_A_START_PENDING_INTENT_ACTIVITY.className)

    @JvmField val APP_B_SIMPLE_BROADCAST_RECEIVER = component("SimpleBroadcastReceiver")

    private fun String.toFullAction() = buildFullActionName(packageName, this)
}
