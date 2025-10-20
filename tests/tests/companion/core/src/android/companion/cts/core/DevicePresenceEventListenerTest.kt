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

import android.Manifest.permission.USE_COMPANION_TRANSPORTS
import android.companion.DevicePresenceEvent
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY
import android.companion.Flags
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.SERVICE_NAME_A
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for DevicePresenceEventListener.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DevicePresenceEventListenerTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_SYNC)
class DevicePresenceEventListenerTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val receivedEvents: BlockingQueue<DevicePresenceEvent> = LinkedBlockingQueue()
    private val executor = Executors.newSingleThreadExecutor()

    private var associationIdA: Int? = null
    private var associationIdB: Int? = null

    private var listener: Consumer<DevicePresenceEvent>? = null

    private data class ExpectedEvent(val associationId: Int, val eventType: Int)

    override fun setUp() {
        super.setUp()
        // Create two associations to test filtering.
        associationIdA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        associationIdB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)
        receivedEvents.clear()
    }

    override fun tearDown() {
        // Clean up listener if it's still registered.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.removeOnDevicePresenceEventListener(SERVICE_NAME_A)
        }

        // Clean up associations.
        associationIdA?.let { cdm.disassociate(it) }
        associationIdB?.let { cdm.disassociate(it) }
        super.tearDown()
    }

    @Test
    fun testAddAndRemoveListener_receivesEventsForAllAssociations() {
        val idA = requireNotNull(associationIdA)
        val idB = requireNotNull(associationIdB)

        // Add a listener for ALL associations.
        listener = Consumer { event -> receivedEvents.offer(event) }
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnDevicePresenceEventListener(
                intArrayOf(idA, idB),
                SERVICE_NAME_A,
                executor,
                listener!!
            )
        }

        // 1: Notify for the first association and verify it's received.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)

        assertEventsReceived(
            ExpectedEvent(associationId = idA, eventType = EVENT_SELF_MANAGED_NEARBY)
        )

        // 2: Notify for the second association and verify it's also received.
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NEARBY)
        assertEventsReceived(
            ExpectedEvent(associationId = idB, eventType = EVENT_SELF_MANAGED_NEARBY)
        )

        // 3: Remove the listener and verify no more events are received.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.removeOnDevicePresenceEventListener(SERVICE_NAME_A)
        }

        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)
        assertNoEventReceived()
        // Unbind the service.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NOT_NEARBY)
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NOT_NEARBY)

        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun testAddListener_filtersByAssociationId() {
        val idA = requireNotNull(associationIdA)
        val idB = requireNotNull(associationIdB)

        listener = Consumer { event -> receivedEvents.offer(event) }
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnDevicePresenceEventListener(
                intArrayOf(idA),
                SERVICE_NAME_A,
                executor,
                listener!!
            )
        }

        // Notify for the second (un-monitored) association.
        // Verify the event is NOT received.
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NEARBY)
        assertNoEventReceived()

        // 2: Notify for the first (monitored) association.
        // Verify the event is received.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)
        assertEventsReceived(
            ExpectedEvent(associationId = idA, eventType = EVENT_SELF_MANAGED_NEARBY)
        )
        // Unbind the service.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NOT_NEARBY)
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NOT_NEARBY)
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun testAddListener_notifyPresenceDevices() {
        val idA = requireNotNull(associationIdA)
        val idB = requireNotNull(associationIdB)

        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NEARBY)

        listener = Consumer { event -> receivedEvents.offer(event) }
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnDevicePresenceEventListener(
                intArrayOf(idA, idB),
                SERVICE_NAME_A,
                executor,
                listener!!
            )
        }
        assertEventsReceived(
            ExpectedEvent(associationId = idA, eventType = EVENT_SELF_MANAGED_NEARBY),
            ExpectedEvent(associationId = idB, eventType = EVENT_SELF_MANAGED_NEARBY)
        )

        // Unbind the service.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NOT_NEARBY)
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NOT_NEARBY)
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun testSetListener_overwritesExistingListener() {
        val idA = requireNotNull(associationIdA)
        val idB = requireNotNull(associationIdB)

        listener = Consumer { event -> receivedEvents.offer(event) }

        // 1. Register a listener for the first association only.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnDevicePresenceEventListener(
                intArrayOf(idA),
                SERVICE_NAME_A,
                executor,
                listener!!
            )
        }

        // 2. Verify an event for the monitored association is received.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)
        assertEventsReceived(
            ExpectedEvent(associationId = idA, eventType = EVENT_SELF_MANAGED_NEARBY)
        )

        // 3. Overwrite the listener, now filtering for the second association.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnDevicePresenceEventListener(
                intArrayOf(idB),
                SERVICE_NAME_A, // Same service name.
                executor,
                listener!!
            )
        }

        // 4. Verify an event for the first association is now IGNORED.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NEARBY)
        assertNoEventReceived()

        // 5. Verify an event for the second association is now RECEIVED.
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NEARBY)
        assertEventsReceived(
            ExpectedEvent(associationId = idB, eventType = EVENT_SELF_MANAGED_NEARBY)
        )

        // Unbind the service.
        notifyPresence(cdm, idA, EVENT_SELF_MANAGED_NOT_NEARBY)
        notifyPresence(cdm, idB, EVENT_SELF_MANAGED_NOT_NEARBY)
        assertValidCompanionDeviceServicesUnbind()
    }

    private fun assertEventsReceived(
        vararg expected: ExpectedEvent,
        timeoutPerEvent: Long = 2,
        unit: TimeUnit = TimeUnit.SECONDS
    ) {
        val expectedSet = expected.toSet()
        val actualEvents = mutableSetOf<ExpectedEvent>()

        // Poll the queue for the expected number of events.
        repeat(expected.size) { index ->
            val receivedEvent = receivedEvents.poll(timeoutPerEvent, unit)
            Assert.assertNotNull(
                "Timed out waiting for event ${index + 1} of ${expected.size}. " +
                        "Expected=[$expectedSet], Received so far=[$actualEvents]",
                receivedEvent
            )
            actualEvents.add(ExpectedEvent(
                receivedEvent.associationId,
                receivedEvent.event
            ))
        }

        // Assert that the set of received events matches the set of expected events.
        Assert.assertEquals(expectedSet, actualEvents)
    }

    private fun assertNoEventReceived() {
        val receivedEvent = receivedEvents.poll(2, TimeUnit.SECONDS)
        Assert.assertNull(
            "Should not have received an event, but did: $receivedEvent",
            receivedEvent
        )
    }
}
