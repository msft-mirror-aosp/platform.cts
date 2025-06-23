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

package android.companion.cts.uiautomation

import android.companion.AssociationRequest
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.MAC_ADDRESS_A
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test APIs for applying transport flags to an association.
 *
 * Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:TransportFlagsTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class TransportFlagsTest : UiAutomationTestBase(null, null) {
    @Test
    fun test_deviceProfileWearableSensing_callerMustBeSystem() {
        assertFailsWith(SecurityException::class) {
            createSelfManagedAssociation(
                DEVICE_DISPLAY_NAME_A,
                AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING
            )
        }
    }

    @Test
    fun test_flagSet_extendPatchDiff() {
        // Creating an association with WEARABLE_SENSING should set the transport flag
        targetApp.associateSelfManaged(
            MAC_ADDRESS_A,
            AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING
        )
        val association = cdm.myAssociations[0]
        assertEquals(1, association.transportFlags)
    }
}
