/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.server.wm.component

import android.app.ActivityManager
import android.content.Context
import android.server.wm.NestedShellPermission
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Stops the test package associated with this [ComponentsProvider].
 *
 * @param context The [Context] used to access [ActivityManager] system services.
 *
 * ### Example Usage:
 * ```kotlin
 * import android.server.wm.app27.Components
 * import android.server.wm.component.forceStopPackage // Import the extension
 *
 * @After
 * fun tearDown() {
 *     Components.forceStopPackage()
 * }
 * ```
 *
 * ### Java Interoperability
 * To expose this extension to Java tests as a static method on your `Components` object, you can
 * add a forwarding method:
 * ```kotlin
 *   @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
 * ```
 */
fun ComponentsProvider.forceStopPackage(
    context: Context = InstrumentationRegistry.getInstrumentation().context
) {
    val activityManager = context.getSystemService(ActivityManager::class.java)!!
    NestedShellPermission.run { activityManager.forceStopPackage(packageName) }
}
