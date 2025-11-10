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

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.annotation.CallSuper
import android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC
import android.companion.CompanionDeviceManager.MESSAGE_REQUEST_METADATA_UPDATE
import android.companion.Flags
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MessageDemuxer
import android.companion.cts.common.MessageFeeder
import android.companion.cts.common.sleepFor
import android.os.PersistableBundle
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_SYNC)
class MetadataSyncTest : CoreTestBase()  {
    @get:Rule
    val checkFlagsRule: CheckFlagsRule? = DeviceFlagsValueProvider.createCheckFlagsRule()

    @CallSuper
    override fun setUp() {
        super.setUp()
        targetApp.clearLocalMetadata()
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(1) // Force raw transport
        }
    }

    @CallSuper
    override fun tearDown() {
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(0) // Reset transport type
        }
        targetApp.clearLocalMetadata()
        super.tearDown()
    }

    @Test
    fun test_metadata_isSentOnTransportAttached() {
        // Create association and set local metadata without live connections
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        targetApp.setLocalMetadata(FEATURE_CROSS_DEVICE_SYNC, "version", "1")

        // Attach transport and assert current metadata is sent
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)
        output.getNextMessage(MESSAGE_REQUEST_METADATA_UPDATE)?.also {
            val metadata = PersistableBundle.readFromStream(ByteArrayInputStream(it.payload))
            assertNotNull("Metadata must not be null", metadata)

            val featureMetadata = metadata?.getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC)
            assertNotNull("Feature-specific metadata not found", featureMetadata)

            val modeSyncVersion = featureMetadata?.getInt("version")
            assertEquals("Unexpected metadata entry", 1, modeSyncVersion)
        } ?: {
            fail("Local metadata was not broadcasted after transport attachment")
        }
    }

    @Test
    fun test_metadata_isSentOnLocalMetadataUpdate() {
        // Create association
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Attach transport and wait for empty metadata to be sent
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)
        output.getNextMessage(MESSAGE_REQUEST_METADATA_UPDATE)

        // Set local metadata and assert populated metadata is sent
        targetApp.setLocalMetadata(FEATURE_CROSS_DEVICE_SYNC, "version", "1")
        output.getNextMessage(MESSAGE_REQUEST_METADATA_UPDATE)?.also {
            val metadata = PersistableBundle.readFromStream(ByteArrayInputStream(it.payload))
            assertNotNull("Metadata must not be null", metadata)

            val featureMetadata = metadata?.getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC)
            assertNotNull("Feature-specific metadata not found", featureMetadata)

            val modeSyncVersion = featureMetadata?.getInt("version")
            assertEquals("Unexpected metadata entry", 1, modeSyncVersion)
        } ?: {
            fail("Local metadata was not broadcasted after update")
        }
    }

    @Test
    fun test_metadata_isUpdatedOnReceive() {
        // Create association and attach transport
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)

        // Simulate metadata update reception message from the remote device
        // and wait for ACK
        val metadata = ByteArrayOutputStream()
        PersistableBundle().apply {
            putPersistableBundle(FEATURE_CROSS_DEVICE_SYNC, PersistableBundle().apply {
                putString("lorem", "ipsum")
            })
            writeToStream(metadata)
        }
        input.feedMessage(MESSAGE_REQUEST_METADATA_UPDATE, metadata.toByteArray())
        output.getNextMessage(MESSAGE_RESPONSE_SUCCESS) ?: {
            fail("Metadata update was not acknowledged")
        }

        // Assert remote metadata is updated
        cdm.myAssociations[0].getMetadata(FEATURE_CROSS_DEVICE_SYNC)?.also {
            assertEquals("ipsum", it.getString("lorem"))
        } ?: {
            fail("Remote metadata was not updated")
        }
    }

    @Test
    fun test_timestamp_isUpdatedOnSend() {
        // Create association and set local metadata without live connections
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        targetApp.setLocalMetadata(FEATURE_CROSS_DEVICE_SYNC, "version", "1")

        // Attach transport and simulate ACK response
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)
        output.getNextMessage(MESSAGE_REQUEST_METADATA_UPDATE)?.also {
            input.feedMessage(MESSAGE_RESPONSE_SUCCESS, byteArrayOf())
            sleepFor(1.seconds) // Wait for the timestamp to be updated
        }

        // Assert local metadata distribution timestamp is updated
        assertNotEquals("Outbound metadata timestamp was not updated",
                0L, cdm.myAssociations[0].metadataSentTimestamp)
    }

    @Test
    fun test_timestamp_isUpdatedOnReceive() {
        // Create association and attach transport
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val input = MessageFeeder()
        val output = MessageDemuxer {}
        cdm.attachSystemDataTransport(associationId, input, output)

        // Simulate metadata update reception message from the remote device
        val metadata = ByteArrayOutputStream()
        PersistableBundle().apply {
            putPersistableBundle(FEATURE_CROSS_DEVICE_SYNC, PersistableBundle())
            writeToStream(metadata)
        }
        input.feedMessage(MESSAGE_REQUEST_METADATA_UPDATE, metadata.toByteArray())
        output.getNextMessage(MESSAGE_RESPONSE_SUCCESS)

        // Assert remote metadata timestamp is updated
        assertNotEquals("Inbound metadata timestamp was not updated",
                0L, cdm.myAssociations[0].metadataTimestamp)
    }

    companion object {
        const val MESSAGE_RESPONSE_SUCCESS = 0x33838567 // CDM ACK response
    }
}