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

import android.content.ComponentName

/**
 * An abstract class for a constants-holding object that provides an idiomatic Kotlin API.
 *
 * This is the recommended contract for new test components written in Kotlin. This pattern uses a
 * singleton `object` to provide a single, well-known entry point for all component constants within
 * a test APK.
 *
 * By extending this class, your `object` automatically gains the `.packageName` property and
 * `.component()` function without any boilerplate for Kotlin callers.
 *
 * ### Example Usage
 *
 * ```kotlin
 * package android.server.wm.app27
 *
 * import android.server.wm.component.ComponentProvider
 *
 * /** Constants for SDK 27 test components. */
 * object Components : ComponentProvider {
 *   @JvmField
 *   val SDK_27_HOME_ACTIVITY = component("HomeActivity")
 * }
 * ```
 *
 * @see forceStopPackage for an extension function that simplifies stopping the test package during
 *   test cleanup. Its documentation includes usage examples and tips for Java interoperability.
 */
abstract class ComponentsProvider {
    /**
     * The package name of the test APK, derived from the `Components` object that implements this
     * interface.
     *
     * @throws AssertionError if the implementing class is not named exactly `Components`.
     */
    @JvmField val packageName: String = ComponentsBase.getPackageName(this.javaClass)

    /**
     * Builds a [ComponentName] for a class within this component's package.
     *
     * @param className The simple class name (e.g., "MyActivity") or a fully qualified class name
     *   (e.g., "com.example.app.MyActivity"). If a simple name is provided, it will be prepended
     *   with the APK's package name.
     * @return A [ComponentName] object for the specified class.
     * @throws AssertionError if the [className] starts with a '.'.
     */
    fun component(className: String): ComponentName =
        ComponentsBase.component(this.javaClass, className)
}
