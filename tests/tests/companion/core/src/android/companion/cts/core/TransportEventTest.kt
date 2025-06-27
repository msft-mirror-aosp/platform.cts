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

import android.Manifest
import android.annotation.CallSuper
import android.companion.cts.common.InvocationContainer
import android.companion.cts.common.InvocationTracker
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that CDM transport events can be subscribed to.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:TransportEventTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class TransportEventTest : CoreTestBase() {

    companion object {
        private const val SUCCESSFUL_CONNECTION = 200
        private const val ERROR_UPDATE_REQUIRED = 427
        private const val ERROR_UNKNOWN = 500
    }

    @CallSuper
    override fun setUp() {
        super.setUp()

        // Force CDM to initialize secure transports.
        withShellPermissionIdentity(Manifest.permission.MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(2)
        }
    }

    @CallSuper
    override fun tearDown() {
        super.tearDown()

        // Force CDM to reset transport type.
        withShellPermissionIdentity(Manifest.permission.MANAGE_COMPANION_DEVICES) {
            cdm.overrideTransportType(0)
        }
    }

    @Test
    fun test_addOnTransportEventListener_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val listener = Consumer<Int> { _: Int -> }

        // Attempts to call addOnTransportEventListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.addOnTransportEventListener(SIMPLE_EXECUTOR, associationId, listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.addOnTransportEventListener(SIMPLE_EXECUTOR, associationId, listener)
        }

        // Attempts to call removeOnTransportEventListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.removeOnTransportEventListener(associationId, listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.removeOnTransportEventListener(associationId, listener)
        }
    }

    @Test
    fun test_transportEventListener_invokedOnError() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val listener = RecordingOnTransportEventListener()

        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.addOnTransportEventListener(SIMPLE_EXECUTOR, associationId, listener)
        }

        // Assert that an error event is recorded by the registered listener.
        listener.assertInvokedByActions {
            // Attach a transport that sends bad handshake messages.
            cdm.attachSystemDataTransport(
                associationId,
                ByteArrayInputStream("BAD_HANDSHAKE_MESSAGE".toByteArray(Charsets.UTF_8)),
                ByteArrayOutputStream(),
            )
        }
        assertEquals(ERROR_UNKNOWN, listener.invocations[0])
    }
}

class RecordingOnTransportEventListener private constructor(container: InvocationContainer<Int>) :
    Consumer<Int>, InvocationTracker<Int> by container {

    constructor() : this(InvocationContainer())

    override fun accept(event: Int) = recordInvocation(event)
}
