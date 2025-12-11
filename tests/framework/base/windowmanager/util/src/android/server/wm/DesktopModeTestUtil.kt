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

package android.server.wm

import android.content.res.Resources
import android.os.SystemProperties

/**
 * A lightweight utility for tests to check desktop mode eligibility.
 *
 * This class mimics the logic of the internal `com.android.server.wm.DesktopModeHelper` to
 * determine if desktop mode can be entered, based on device resources and developer options.
 *
 * Unlike the internal helper, this class relies only on public APIs and SystemProperties, avoiding
 * direct dependencies on WindowManager internals or hidden feature flags. This makes it suitable
 * for use in CTS and end-to-end tests where such dependencies are undesirable.
 */
class DesktopModeTestUtil(private val resources: Resources) {

    /**
     * Returns `true` if desktop mode can be entered on the current device.
     *
     * This mimics the behavior of `DesktopModeHelper.canEnterDesktopMode(Context)`, checking both
     * native device support and developer option overrides using available resources and system
     * properties.
     */
    fun canEnterDesktopMode(): Boolean {
        // Native support is the primary check; if the device is built for desktop mode, allow it.
        if (isDesktopModeSupported()) return true

        // If not natively supported, check if it can be enabled via developer options.
        // Both the capability (resource) and the actual toggle (setting/prop) must be present.
        return isDevOptionSupported() && isDevOptionEnabled()
    }

    private fun isDesktopModeSupported(): Boolean =
        // Checks the device configuration resource that indicates native desktop mode support.
        getResourceBoolean("config_isDesktopModeSupported")

    private fun isDevOptionSupported(): Boolean =
        // Checks the device configuration resource that allows desktop mode to be forced enabled
        // via developer options.
        getResourceBoolean("config_isDesktopModeDevOptionSupported")

    private fun getResourceBoolean(flagName: String): Boolean {
        // Dynamically looks up resources because the symbols are internal (com.android.internal.R)
        // and not available in the SDK stubs used for CTS compilation.
        val resId =
            resources.getIdentifier(
                /* name = */ flagName,
                /* defType = */ "bool",
                /* defPackage = */ "android",
            )
        return resources.getBoolean(resId)
    }

    private fun isDevOptionEnabled(): Boolean =
        // Check the system property for "Desktop Experience" developer option.
        // The platform (DesktopExperienceFlags) relies solely on this property to determine
        // if the feature is enabled via developer options.
        SystemProperties.getBoolean("persist.wm.debug.desktop_experience_devopts", false)
}
