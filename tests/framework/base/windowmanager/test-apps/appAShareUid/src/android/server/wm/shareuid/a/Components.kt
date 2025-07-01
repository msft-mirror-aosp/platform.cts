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

package android.server.wm.shareuid.a

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for test components A in the shared user ID affinity. */
object Components : ComponentsProvider() {
    /** Test activity with the shared user ID affinity. */
    @JvmField val TEST_ACTIVITY_WITH_SAME_AFFINITY = component("TestActivityWithSameAffinity")

    /** Another activity with the same shared user ID affinity in the same package. */
    @JvmField
    val TEST_ACTIVITY_WITH_SAME_AFFINITY_SAME_APP = component("TestActivityWithSameAffinitySameApp")

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
