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

package android.supervision.cts

import android.app.UiAutomation
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation

/**
 * When [UiAutomation.dropShellPermissionIdentity] is called, all previously adopted permissions
 * are cleared. This will also clear any previous permissions adopted with the [EnsureHasPermission]
 * annotation. When calling this method, [permissions] are adopted while executing [block].
 * Afterward, it restores the previously adopted permissions.
 */
fun <T> callWithShellPermissionIdentity(vararg permissions: String, block: () -> T): T {
    val uiAutomation = getInstrumentation().getUiAutomation()
    val adoptedPermissions = uiAutomation.getAdoptedShellPermissions()
    try {
        uiAutomation.adoptPermissions(adoptedPermissions + permissions.toSet())
        return block()
    } finally {
        uiAutomation.restorePermissions(adoptedPermissions)
    }
}

private fun UiAutomation.adoptPermissions(permissionsToAdopt: Set<String>) {
    if (permissionsToAdopt.isEmpty()) {
        adoptShellPermissionIdentity()
    } else {
        adoptShellPermissionIdentity(*permissionsToAdopt.toTypedArray())
    }
}

private fun UiAutomation.restorePermissions(adoptedPermissions: Set<String>) {
    if (adoptedPermissions.isEmpty()) {
        dropShellPermissionIdentity()
    } else {
        adoptPermissions(adoptedPermissions)
    }
}
