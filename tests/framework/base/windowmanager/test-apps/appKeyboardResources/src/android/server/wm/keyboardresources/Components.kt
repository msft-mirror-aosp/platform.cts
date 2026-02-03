/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.server.wm.keyboardresources

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for keyboard resources test components. */
object Components : ComponentsProvider() {

    /** A test activity in a test app that has resources with the -keyboard qualifier. */
    @JvmField val KEYBOARD_RESOURCES_ACTIVITY = component("KeyboardResourcesActivity")

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}