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

package android.server.wm.second

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage
import android.server.wm.overlay.Components.EXIT_ANIMATION_ACTIVITY
import android.server.wm.overlay.Components.OVERLAY_ACTIVITY
import android.server.wm.overlay.Components.TOAST_ACTIVITY
import android.server.wm.overlay.Components.TRAMPOLINE_ACTIVITY
import android.server.wm.overlay.Components.TRANSLUCENT_FLOATING_ACTIVITY
import android.server.wm.overlay.Components.UNTRUSTED_TOUCH_TEST_SERVICE

/** Constants for the second device services test components. */
object Components : ComponentsProvider() {

    @JvmField val EMBEDDING_ACTIVITY = component("EmbeddingActivity")

    object EmbeddingActivity {
        const val ACTION_EMBEDDING_TEST_ACTIVITY_START = "broadcast_test_activity_start"
        const val EXTRA_EMBEDDING_COMPONENT_NAME = "component_name"
        const val EXTRA_EMBEDDING_TARGET_DISPLAY = "target_display"
    }

    @JvmField val SECOND_ACTIVITY = component("SecondActivity")

    object SecondActivity {
        const val EXTRA_DISPLAY_ACCESS_CHECK = "display_access_check"
    }

    @JvmField val SECOND_NO_EMBEDDING_ACTIVITY = component("SecondActivityNoEmbedding")

    @JvmField
    val TEST_ACTIVITY_WITH_SAME_AFFINITY_DIFFERENT_UID =
        component("TestActivityWithSameAffinityDifferentUid")

    @JvmField val SECOND_LAUNCH_BROADCAST_RECEIVER = component("LaunchBroadcastReceiver")

    /** See AndroidManifest.xml. */
    @JvmField val SECOND_LAUNCH_BROADCAST_ACTION = "$packageName.LAUNCH_BROADCAST_ACTION"

    @JvmField val IMPLICIT_TARGET_SECOND_TEST_ACTION = "$packageName.TEST_ACTION"

    @JvmField val IMPLICIT_TARGET_SECOND_ACTIVITY = component("ImplicitTargetActivity")

    @JvmField val SECOND_TRANSLUCENT_FLOATING_ACTIVITY =
        component(TRANSLUCENT_FLOATING_ACTIVITY.className)

    @JvmField val SECOND_TRAMPOLINE_ACTIVITY = component(TRAMPOLINE_ACTIVITY.className)

    @JvmField val SECOND_UNTRUSTED_TOUCH_TEST_SERVICE =
        component(UNTRUSTED_TOUCH_TEST_SERVICE.className)

    @JvmField val SECOND_OVERLAY_ACTIVITY = component(OVERLAY_ACTIVITY.className)

    @JvmField val SECOND_TOAST_ACTIVITY = component(TOAST_ACTIVITY.className)

    @JvmField val SECOND_EXIT_ANIMATION_ACTIVITY = component(EXIT_ANIMATION_ACTIVITY.className)

    @JvmStatic fun getPackageName() = packageName

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
