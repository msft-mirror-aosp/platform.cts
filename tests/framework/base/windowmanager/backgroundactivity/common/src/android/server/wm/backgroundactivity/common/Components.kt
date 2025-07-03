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

package android.server.wm.backgroundactivity.common

import android.server.wm.component.ComponentsProvider
import androidx.annotation.IntDef

/** Constant-holding class common to AppA, AppB and tests. */
object Components : ComponentsProvider() {

    const val EVENT_NOTIFIER_EXTRA = "EVENT_NOTIFIER_EXTRA"

    @JvmField val TEST_SERVICE = component("TestService")

    object CommonForegroundActivityExtras {
        const val ACTIVITY_ID = "ACTIVITY_ID_EXTRA"
        const val ALLOW_CROSS_UID = "ALLOW_CROSS_UID_EXTRA"
        const val LAUNCH_INTENTS = "LAUNCH_INTENTS_EXTRA"
        const val FINISH_FIRST = "FINISH_FIRST_EXTRA"
    }

    @IntDef(
        Event.BROADCAST_RECEIVED,
        Event.APP_A_START_BACKGROUND_ACTIVITY_BROADCAST_RECEIVED,
        Event.APP_A_START_WIDGET_CONFIG_ACTIVITY,
        Event.APP_A_LAUNCHER_MOVING_TO_BACKGROUND_ACTIVITY,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Event {
        companion object {
            const val BROADCAST_RECEIVED: Int = 0
            const val APP_A_START_BACKGROUND_ACTIVITY_BROADCAST_RECEIVED: Int = 2
            const val APP_A_START_WIDGET_CONFIG_ACTIVITY: Int = 3
            const val APP_A_LAUNCHER_MOVING_TO_BACKGROUND_ACTIVITY: Int = 4
        }
    }
}
