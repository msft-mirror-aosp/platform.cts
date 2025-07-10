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
 * Base class for a constants-holding object that provides component definitions.
 *
 * This is the standard contract for providing component constants within a test APK. This pattern
 * uses a singleton `object` named `Components` to provide a single, well-known entry point for
 * all component constants. By extending this class, your `object` automatically gains the
 * `.packageName` property and `.component()` function without boilerplate.
 *
 * ### Basic Usage
 *
 * Define a singleton `object` named `Components` in your test APK's main package. This object
 * should extend `ComponentsProvider`.
 *
 * ```kotlin
 * package android.server.wm.app27
 *
 * import android.server.wm.component.ComponentsProvider
 *
 * /** Constants for SDK 27 test components. */
 * object Components : ComponentsProvider() {
 *   @JvmField
 *   val SDK_27_HOME_ACTIVITY = component("HomeActivity")
 * }
 * ```
 *
 * ### Repackaging Components from Libraries
 *
 * A common pattern is to build test apps as libraries and package them in different test APKs
 * to test different target SDK behaviors. In such cases, you can repackage components from
 * library packages into the test APK's package using the `component(className)` function.
 *
 * **Library Components (android.server.wm.overlay):**
 * ```kotlin
 * package android.server.wm.overlay
 *
 * /** Constants for overlay test components. */
 * object Components : ComponentsProvider() {
 *     @JvmField val OVERLAY_ACTIVITY = component("OverlayActivity")
 * }
 * ```
 *
 * **Test APK Components (android.server.wm.third):**
 * ```kotlin
 * package android.server.wm.third
 *
 * import android.server.wm.component.ComponentsProvider
 * import android.server.wm.overlay.Components.OVERLAY_ACTIVITY
 *
 * /** Constants for the third device services test components. */
 * object Components : ComponentsProvider() {
 *     @JvmField val THIRD_OVERLAY_ACTIVITY = component(OVERLAY_ACTIVITY.className)
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
     * @throws IllegalArgumentException if the implementing class is not named exactly `Components`.
     */
    @JvmField
    val packageName: String = javaClass.run {
        require(simpleName == "Components") {
            "The implementing class must be an 'object' named 'Components', but was '$simpleName'."
        }
        packageName
    }

    /**
     * Builds a [ComponentName] for a class within this component's package.
     *
     * @param className The simple class name (e.g., "MyActivity") or a fully qualified class name
     *   (e.g., "com.example.app.MyActivity"). If a simple name is provided, it will be prepended
     *   with the APK's package name.
     * @return A [ComponentName] object for the specified class.
     * @throws IllegalArgumentException if the [className] starts with a '.'.
     */
    fun component(className: String): ComponentName {
        require(!className.startsWith(".")) {
            "Class name must not start with a '.'. " +
                    "For classes in sub-packages, provide the fully qualified name."
        }
        val fullClassName = if ('.' in className) className else "$packageName.$className"
        return ComponentName(packageName, fullClassName)
    }
}
