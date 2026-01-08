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
import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.annotation.CallSuper
import android.companion.CompanionDeviceManager.MESSAGE_REQUEST_TRUSTED_DEVICE
import android.companion.Flags
import android.companion.cts.common.CdmMessage
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MessageDemuxer
import android.companion.cts.common.MessageFeeder
import android.companion.cts.common.waitFor
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @CallSuper
    override fun setUp() {
        super.setUp()
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(1) // Force raw transport
        }
    }

    @CallSuper
    override fun tearDown() {
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(0) // Reset transport type
        }
        super.tearDown()
    }

    @Test
    fun test_getTrustedAssociations_requiresPermission() {
        assertFailsWith(SecurityException::class) {
            cdm.getTrustedAssociations()
        }

        withShellPermissionIdentity(ACCESS_COMPANION_MESSAGE_PCC) {
            cdm.getTrustedAssociations()
        }
    }

    @Test
    fun test_trustedDeviceExchange_onTransportAttached() {
        // Create association and attach transport
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)

        // 1. Assert that local device sends out verification message
        val verificationMessage = output.getNextMessage(MESSAGE_REQUEST_TRUSTED_DEVICE)
        assertNotNull(verificationMessage)

        // 2. Simulate ACK response from the remote device
        input.feedMessage(verificationMessage!!.success())

        // 3. Simulate remote device sending out its own verification message.
        // Raw channels use symmetric role, so just mirror back the original message.
        input.feedMessage(verificationMessage)

        // 4. Assert that local device sends out an ACK
        val response = output.getNextMessage(CdmMessage.MESSAGE_RESPONSE_SUCCESS)
        assertNotNull(response)

        // Wait for the association to be updated.
        assertTrue(waitFor { cdm.myAssociations[0].isTrusted })
    }
}
