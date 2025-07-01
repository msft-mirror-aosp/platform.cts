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

package android.server.wm.dndtargetappsdk23

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for dnd target app sdk 23 test components. */
object Components : ComponentsProvider() {

    @JvmField val DROP_TARGET_SDK23 = component("DropTarget")

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
