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

package android.server.wm.cts

import android.server.wm.component.ComponentsProvider
import android.server.wm.overlay.Components.EXIT_ANIMATION_ACTIVITY
import android.server.wm.overlay.Components.OVERLAY_ACTIVITY
import android.server.wm.overlay.Components.TOAST_ACTIVITY
import android.server.wm.overlay.Components.TRANSLUCENT_FLOATING_ACTIVITY
import android.server.wm.overlay.Components.UNTRUSTED_TOUCH_TEST_SERVICE

/** Constants for test components under the instrumentation test's package. */
object Components : ComponentsProvider() {

    @JvmField val SAME_UID_TRANSLUCENT_FLOATING_ACTIVITY =
        component(TRANSLUCENT_FLOATING_ACTIVITY.className)

    @JvmField val SAME_UID_UNTRUSTED_TOUCH_TEST_SERVICE =
        component(UNTRUSTED_TOUCH_TEST_SERVICE.className)

    @JvmField val SAME_UID_OVERLAY_ACTIVITY = component(OVERLAY_ACTIVITY.className)

    @JvmField val SAME_UID_TOAST_ACTIVITY = component(TOAST_ACTIVITY.className)

    @JvmField val SAME_UID_EXIT_ANIMATION_ACTIVITY = component(EXIT_ANIMATION_ACTIVITY.className)
}
