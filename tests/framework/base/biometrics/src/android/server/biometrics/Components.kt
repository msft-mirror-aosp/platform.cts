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

package android.server.biometrics

import android.server.wm.component.ComponentsProvider
import android.server.wm.component.forceStopPackage

/** Constants for biometrics test components. */
object Components : ComponentsProvider() {
    @JvmField
    val CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY =
        component("Class2BiometricOrCredentialActivity")

    @JvmField
    val CLASS_2_BIOMETRIC_ACTIVITY =
        component("Class2BiometricActivity")

    @JvmField
    val CLASS_3_BIOMETRIC_ACTIVITY =
        component("Class3BiometricActivity")

    @JvmField
    val CONFIRM_DEVICE_CREDENTIAL_TEST_ACTIVITY = component(
        "ConfirmDeviceCredentialTestActivity"
    )

    @JvmStatic fun forceStopPackage() = (this as ComponentsProvider).forceStopPackage()
}
