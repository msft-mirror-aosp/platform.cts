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

package android.server.wm.jetpack.second

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for Jetpack second app test components. */
object Components : ComponentsProvider() {

    @JvmField val SECOND_ACTIVITY = component("SecondActivity")

    @JvmField val PORTRAIT_ACTIVITY = component("PortraitActivity")

    @JvmField
    val SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS = component("SecondActivityUnknownEmbeddingCerts")

    @JvmField
    val SECOND_UNTRUSTED_EMBEDDING_ACTIVITY = component("SecondActivityAllowsUntrustedEmbedding")

    @JvmField
    val SECOND_UNTRUSTED_EMBEDDING_ACTIVITY_STATE_SHARE =
        component("SecondActivityAllowsUntrustedEmbeddingStateShare")

    const val EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY = "launch_non_embeddable"

    const val ACTION_ENTER_PIP = "enter_pip"

    const val ACTION_EXIT_PIP = "exit_pip"

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
