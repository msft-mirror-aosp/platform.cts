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

package android.server.wm.propertycameracompatallowsimulaterequestedorientationoptin

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for camera compat allow simulate requested orientation opt-in test components. */
object Components : ComponentsProvider() {

    /** Activity in the app with application level component property. */
    @JvmField
    val CAMERA_COMPAT_ALLOW_SIMULATE_REQUESTED_ORIENTATION_OPT_IN_ACTIVITY =
        component("CameraCompatAllowSimulateRequestedOrientationOptInActivity")

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
