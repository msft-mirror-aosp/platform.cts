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

package android.server.wm.overlay

import android.content.ComponentName
import android.os.Bundle
import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for overlay test components. */
object Components : ComponentsProvider() {

    @JvmField val UNTRUSTED_TOUCH_TEST_SERVICE = component("UntrustedTouchTestService")
    object UntrustedTouchTestService {
        const val EXTRA_DISPLAY_ID = "display_id"
    }

    @JvmField val OVERLAY_ACTIVITY = component("OverlayActivity")
    object OverlayActivity {
        const val EXTRA_OPACITY = "opacity"
        const val EXTRA_TOUCHABLE = "touchable"
        const val EXTRA_TOKEN_RECEIVER = "token_receiver"
        const val EXTRA_TOKEN = "token"
    }

    @JvmField val EXIT_ANIMATION_ACTIVITY = component("ExitAnimationActivity")
    object ExitAnimationActivityReceiver {
        const val ACTION_FINISH =
            "android.server.wm.overlay.ExitAnimationActivityReceiver.ACTION_FINISH"
        const val EXTRA_ANIMATION = "animation"
        const val EXTRA_VALUE_ANIMATION_EMPTY = 0
        const val EXTRA_VALUE_ANIMATION_0_7 = 1
        const val EXTRA_VALUE_ANIMATION_0_9 = 2
        const val EXTRA_VALUE_LONG_ANIMATION_0_7 = 3
    }

    @JvmField val TOAST_ACTIVITY = component("ToastActivity")

    @JvmField val TRANSLUCENT_FLOATING_ACTIVITY = component("TranslucentFloatingActivity")
    object TranslucentFloatingActivity {
        const val ACTION_FINISH =
            "android.server.wm.overlay.TranslucentFloatingActivity.ACTION_FINISH"
        const val EXTRA_FADE_EXIT =
            "android.server.wm.overlay.TranslucentFloatingActivity.ACTION_FINISH_FADE_EXIT"
    }

    @JvmField val TRAMPOLINE_ACTIVITY = component("TrampolineActivity")
    object TrampolineActivity {
        const val COMPONENTS_EXTRA = "components_extra"

        @JvmStatic
        fun buildTrampolineExtra(vararg componentNames: ComponentName): Bundle = Bundle().apply {
            putParcelableArray(COMPONENTS_EXTRA, componentNames)
        }
    }

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
