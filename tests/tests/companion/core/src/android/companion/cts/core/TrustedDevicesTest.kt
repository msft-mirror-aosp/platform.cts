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

package android.companion.cts.core

import android.Manifest.permission.ACCESS_COMPANION_MESSAGE_PCC
import android.companion.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the APIs related to establishing trust relationships between devices.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:TrustedDevicesTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_TRUSTED_DEVICES)
class TrustedDevicesTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun test_getTrustedAssociations_requiresPermission() {
        assertFailsWith(SecurityException::class) {
            cdm.getTrustedAssociations()
        }

        withShellPermissionIdentity(ACCESS_COMPANION_MESSAGE_PCC) {
            cdm.getTrustedAssociations()
        }
    }
}
