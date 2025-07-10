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

package android.server.wm.backgroundactivity.appa

import android.server.wm.backgroundactivity.common.Components.CommonForegroundActivityExtras
import android.server.wm.backgroundactivity.common.Components.TEST_SERVICE
import android.server.wm.component.ComponentsProvider

/** Constants for background activity test components in appa. */
object Components : ComponentsProvider() {

    @JvmField val APP_A_PACKAGE_NAME = packageName

    @JvmField val APP_A_TEST_SERVICE = component(TEST_SERVICE.className)

    @JvmField val APP_A_BACKGROUND_ACTIVITY = component("BackgroundActivity")
    @JvmField val APP_A_SECOND_BACKGROUND_ACTIVITY = component("SecondBackgroundActivity")

    @JvmField val APP_A_FOREGROUND_ACTIVITY = component("ForegroundActivity")

    /** Action constants for [ForegroundActivity]. */
    object ForegroundActivityAction {
        const val ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX = "ACTION_LAUNCH_BACKGROUND_ACTIVITIES"
        const val ACTION_FINISH_ACTIVITY_SUFFIX = "ACTION_FINISH_ACTIVITY"

        @JvmField
        val APP_A_LAUNCH_BACKGROUND_ACTIVITIES =
            ACTION_LAUNCH_BACKGROUND_ACTIVITIES_SUFFIX.toFullAction()
        @JvmField val APP_A_FINISH_ACTIVITY = ACTION_FINISH_ACTIVITY_SUFFIX.toFullAction()
    }

    /** Extra key constants for [ForegroundActivity]. */
    object ForegroundActivityExtra {
        const val RELAUNCH_FOREGROUND_ACTIVITY_EXTRA = "RELAUNCH_FOREGROUND_ACTIVITY_EXTRA"
        const val DEFAULT_ACTIVITY_ID = -1
        const val ACTIVITY_ID = CommonForegroundActivityExtras.ACTIVITY_ID
        const val ALLOW_CROSS_UID = CommonForegroundActivityExtras.ALLOW_CROSS_UID
        const val LAUNCH_INTENTS = CommonForegroundActivityExtras.LAUNCH_INTENTS
        const val LAUNCH_PENDING_INTENTS = "LAUNCH_PENDING_INTENTS_EXTRA"
        const val LAUNCH_FOR_RESULT_AND_FINISH = "LAUNCH_FOR_RESULT_AND_FINISH"
        const val FINISH_FIRST = CommonForegroundActivityExtras.FINISH_FIRST
    }

    @JvmField val APP_A_FOREGROUND_EMBEDDING_ACTIVITY = component("ForegroundEmbeddingActivity")

    /** Action constants for [ForegroundEmbeddingActivity]. */
    object ForegroundEmbeddedActivityAction {
        const val ACTION_LAUNCH_EMBEDDED_ACTIVITY_SUFFIX = "ACTION_LAUNCH_EMBEDDED_ACTIVITY"
        const val ACTION_FINISH_ACTIVITY_SUFFIX = "ACTION_FINISH_ACTIVITY"

        @JvmField
        val APP_A_LAUNCH_EMBEDDED_ACTIVITY = ACTION_LAUNCH_EMBEDDED_ACTIVITY_SUFFIX.toFullAction()
        @JvmField val APP_A_FINISH_ACTIVITY = ACTION_FINISH_ACTIVITY_SUFFIX.toFullAction()
    }

    @JvmField val APP_A_START_PENDING_INTENT_ACTIVITY = component("StartPendingIntentActivity")

    /** Extra key constants for [StartPendingIntentActivity] */
    object StartPendingIntentActivityExtra {
        const val START_BUNDLE = "START_BUNDLE"
        const val PENDING_INTENT = "PENDING_INTENT_EXTRA"
    }

    @JvmField val APP_A_SIMPLE_BROADCAST_RECEIVER = component("SimpleBroadcastReceiver")
    @JvmField val APP_A_SIMPLE_ADMIN_RECEIVER = component("SimpleAdminReceiver")
    @JvmField val APP_A_ACTIVITY_START_SERVICE = component("ActivityStarterService")
    @JvmField val APP_A_PIP_ACTIVITY = component("PipActivity")
    @JvmField val APP_A_RELAUNCHING_ACTIVITY = component("RelaunchingActivity")

    @JvmField val APP_A_VIRTUAL_DISPLAY_ACTIVITY = component("VirtualDisplayActivity")

    /** Extra key constants for [VirtualDisplayActivity] */
    object VirtualDisplayActivityExtra {
        const val USE_PUBLIC_PRESENTATION = "USE_PUBLIC_PRESENTATION_EXTRA"
    }

    @JvmField val APP_A_WIDGET_CONFIG_TEST_ACTIVITY = component("WidgetConfigTestActivity")
    @JvmField val APP_A_WIDGET_PROVIDER = component("WidgetProvider")
    @JvmField val APP_A_START_NEXT_MATCHING_ACTIVITY = component("StartNextMatchingActivity")
    @JvmField val APP_A_BIND_SERVICE_ACTIVITY = component("BindServiceActivity")

    @JvmStatic
    fun buildFullActionName(appPackageName: String, action: String) = "$appPackageName.$action"

    private fun String.toFullAction() = buildFullActionName(packageName, this)
}
