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

package android.server.wm.profileable

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for profileable app test components. */
object Components : ComponentsProvider() {

    @JvmField val PROFILEABLE_APP_ACTIVITY = component("ProfileableAppActivity")

    object ProfileableAppActivity {
        /** @see android.os.ShellCommand#openFileForSystem */
        const val OUTPUT_DIR = "/data/local/tmp/AmProfileTest/"
        const val OUTPUT_NAME = "profile.trace"
        const val OUTPUT_FILE_PATH = OUTPUT_DIR + OUTPUT_NAME
        const val COMMAND_WAIT_FOR_PROFILE_OUTPUT = "wait_for_profile_output"
    }

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
