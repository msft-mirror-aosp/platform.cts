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

package android.server.wm.optoutsandboxingviewboundsapis

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for view sandbox opt-out test components. */
object Components : ComponentsProvider() {
    const val ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED = "TEST_VIEW_SANDBOX_OPT_OUT_PASSED"
    const val TEST_VIEW_SANDBOX_OPT_OUT_TIMEOUT_MS = 5000L

    @JvmField
    val TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY = component("TestCompatViewSandboxOptOutActivity")

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
