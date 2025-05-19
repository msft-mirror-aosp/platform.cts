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

import android.companion.DeviceId
import android.companion.Flags
import android.companion.ObservingDevicePresenceRequest
import android.companion.cts.common.ASSOCIATION_ID
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.UUID_A
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM API for ObservingDevicePresenceRequest.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ObservingDevicePresentRequestTest
 **/
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_VERIFICATION)
class ObservingDevicePresentRequestTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun test_ObservingDevicePresenceRequest_invalid_builder() {
        val requestA = ObservingDevicePresenceRequest.Builder().setUuid(UUID_A).build()
        assertEquals(expected = UUID_A, actual = requestA.uuid)

        val requestB = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(ASSOCIATION_ID).build()
        val deviceId: DeviceId = DeviceId.Builder().setMacAddress(MAC_ADDRESS_A).build()

        assertEquals(expected = ASSOCIATION_ID, actual = requestB.associationId)

        // Verify that uuid and association id can not be both set.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                .setAssociationId(ASSOCIATION_ID).setUuid(UUID_A).build()
        }

        // Verify that uuid and device id can not be both set.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                .setDeviceId(deviceId).setUuid(UUID_A).build()
        }

        // Verify that uuid, device id and association id can not be set together.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                .setDeviceId(deviceId).setUuid(UUID_A).setAssociationId(ASSOCIATION_ID).build()
        }

        // Verify that need association id and device id can not both set.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                .setDeviceId(deviceId).setAssociationId(ASSOCIATION_ID).build()
        }

        // Verify that can not set device id, uuid and association id all together.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder()
                .setDeviceId(deviceId).setUuid(UUID_A).setAssociationId(ASSOCIATION_ID).build()
        }

        // Verify that building an ObservingDevicePresenceRequest without
        // setting any IDs throws an IllegalStateException.
        assertFailsWith(IllegalStateException::class) {
            ObservingDevicePresenceRequest.Builder().build()
        }
    }

    @Test
    fun test_ObservingDevicePresenceRequest_valid_builder() {
        val requestA = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(ASSOCIATION_ID).build()
        assertEquals(expected = ASSOCIATION_ID, actual = requestA.associationId)

        val deviceId: DeviceId = DeviceId.Builder().setMacAddress(MAC_ADDRESS_A).build()
        val requestB = ObservingDevicePresenceRequest.Builder().setDeviceId(deviceId).build()
        assertEquals(expected = requestB.deviceId, actual = deviceId)

        val requestC = ObservingDevicePresenceRequest.Builder().setUuid(UUID_A).build()
        assertEquals(expected = requestC.uuid, actual = UUID_A)
    }
}
