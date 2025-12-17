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

import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.Manifest.permission.USE_COMPANION_TRANSPORTS
import android.companion.ActionRequest
import android.companion.ActionRequest.OP_ACTIVATE
import android.companion.ActionRequest.OP_DEACTIVATE
import android.companion.ActionRequest.REQUEST_NEARBY_SCANNING
import android.companion.ActionResult
import android.companion.ActionResult.RESULT_ACTIVATED
import android.companion.ActionResult.RESULT_DEACTIVATED
import android.companion.ActionResult.RESULT_FAILED_TO_ACTIVATE
import android.companion.DevicePresenceEvent
import android.companion.Flags
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SERVICE_NAME_A
import android.companion.cts.common.SERVICE_NAME_B
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for ActionRequest results.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ActionResultTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_SYNC)
class ActionResultTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val receivedResults: BlockingQueue<Pair<Int, ActionResult>> = LinkedBlockingQueue()
    private val executor = Executors.newSingleThreadExecutor()

    private var associationIdA: Int = -1
    private var associationIdB: Int = -1

    override fun setUp() {
        super.setUp()

        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setRequestActionAllowList(listOf(SERVICE_NAME_A, SERVICE_NAME_B))
        }
        associationIdA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        associationIdB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        receivedResults.clear()
    }

    override fun tearDown() {
        // Clean up listeners to avoid interfering with other tests
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.removeOnActionResultListener(SERVICE_NAME_A)
            cdm.removeOnActionResultListener(SERVICE_NAME_B)
            cdm.setRequestActionAllowList(null)
        }

        super.tearDown()
    }

    @Test
    fun testAddAndRemoveListener_receivesResult() {
        val request = ActionRequest.Builder(REQUEST_NEARBY_SCANNING, OP_ACTIVATE).build()
        val resultToSend = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_ACTIVATED
        ).build()

        // 1. Add a listener.
        val listener = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_A,
                executor,
                listener
            )
        }

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                    null
                )
            )
        }

        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                request,
                SERVICE_NAME_A,
                intArrayOf(associationIdA)
            )
        }
        // Wait for the mock app to receive the request to ensure the state is PENDING.
        PrimaryCompanionService.waitToActionRequest(
            expectedAssociationId = associationIdA,
            expectedAction = REQUEST_NEARBY_SCANNING
        )

        // 2. Notify a result from the app.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultToSend)
        }

        // 3. Verify the listener received the result.
        val (receivedId, receivedResult) = assertResultReceived()
        assertEquals(associationIdA, receivedId)
        assertEquals(resultToSend, receivedResult)

        // 4. Remove the listener.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.removeOnActionResultListener(SERVICE_NAME_A)
        }

        // 5. Notify another result and verify it is NOT received.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultToSend)
        }
        assertNoResultReceived()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                    null
                )
            )
        }
    }

    @Test
    fun testMultipleServices_onPendingAction_receiveResultTogether() {
        val request = ActionRequest.Builder(REQUEST_NEARBY_SCANNING, OP_ACTIVATE).build()
        val resultFromApp = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_ACTIVATED
        ).build()

        val listenerA = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }
        val listenerB = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }

        // 1. Register both listeners.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_A,
                executor,
                listenerA
            )
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_B,
                executor,
                listenerB
            )
        }

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                    null
                )
            )
        }

        // 2. Service A requests the action. This should go to the app.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                request,
                SERVICE_NAME_A,
                intArrayOf(associationIdA)
            )
        }
        PrimaryCompanionService.waitToActionRequest(
            expectedAssociationId = associationIdA,
            expectedAction = REQUEST_NEARBY_SCANNING
        )

        // 3. WHILE PENDING, Service B requests the same action. This should NOT go to the app.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                request,
                SERVICE_NAME_B,
                intArrayOf(associationIdA)
            )
        }
        PrimaryCompanionService.assertNoActionRequest()

        // 4. Now, the app reports the result.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultFromApp)
        }

        // 5. VERIFY: Both listeners should have received the single result from the app.
        val results = assertResultsReceived(2)
        assertEquals(2, results.size)
        assertEquals(resultFromApp, results[0].second)
        assertEquals(resultFromApp, results[1].second)

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                    null
                )
            )
        }
    }

    @Test
    fun testOnActionResult_forConfirmedActiveAction_getsActivatedCallback() {
        val request = ActionRequest.Builder(REQUEST_NEARBY_SCANNING, OP_ACTIVATE).build()
        val resultFromApp = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_ACTIVATED
        ).build()

        val listenerA = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }
        val listenerB = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }

        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_A,
                executor,
                listenerA
            )
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_B,
                executor,
                listenerB
            )
        }

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                    null
                )
            )
        }

        // 1. Service A requests to start the action.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                request,
                SERVICE_NAME_A,
                intArrayOf(associationIdA)
            )
        }
        PrimaryCompanionService.waitToActionRequest(
            expectedAssociationId = associationIdA,
            expectedAction = REQUEST_NEARBY_SCANNING
        )

        // 2. The app must confirm the action is active. This moves the state from
        //    PENDING to ACTIVE.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultFromApp)
        }

        assertResultReceived()

        // 3. NOW, Service B requests the same, now-active action.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                request,
                SERVICE_NAME_B,
                intArrayOf(associationIdA)
            )
        }
        PrimaryCompanionService.assertNoActionRequest()

        // 4. VERIFY: Service B should receive an immediate "already activated" callback.
        val (receivedId, receivedResult) = assertResultReceived()
        assertEquals(associationIdA, receivedId)
        assertEquals(RESULT_ACTIVATED, receivedResult.resultCode)

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                    null
                )
            )
        }
    }

    @Test
    fun testOnActionDeactivated_sendAndForget_whenOthersAreActive() {
        val startRequest = ActionRequest.Builder(
            REQUEST_NEARBY_SCANNING,
            OP_ACTIVATE
        ).build()
        val stopRequest = ActionRequest.Builder(
            REQUEST_NEARBY_SCANNING,
            OP_DEACTIVATE
        ).build()
        val resultFromApp = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_ACTIVATED
        ).build()

        val listenerA = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }
        // Listener for B is just to register it as a requester.
        val listenerB = BiConsumer<Int, ActionResult> { _, _ -> }

        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_A,
                executor,
                listenerA
            )
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_B,
                executor,
                listenerB
            )
        }

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                    null
                )
            )
        }

        // 1. Both services start the action.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                startRequest,
                SERVICE_NAME_A,
                intArrayOf(associationIdA)
            )
            cdm.requestAction(
                startRequest,
                SERVICE_NAME_B,
                intArrayOf(associationIdA)
            )
        }

        // 2. Wait for the app to receive the first request and confirm it's ACTIVE.
        PrimaryCompanionService.waitToActionRequest(
            expectedAssociationId = associationIdA,
            expectedAction = REQUEST_NEARBY_SCANNING
        )
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultFromApp)
        }

        assertResultReceived()

        // 3. First "stop" request from service A. App should NOT be notified because Service B
        //    is still using the action.
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.requestAction(
                stopRequest,
                SERVICE_NAME_A,
                intArrayOf(associationIdA)
            )
        }
        PrimaryCompanionService.assertNoActionRequest()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyDevicePresence(
                associationIdA,
                DevicePresenceEvent(
                    associationIdA,
                    DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                    null
                )
            )
        }
    }

    @Test
    fun testActionResult_Builder() {
        val resultA = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_ACTIVATED
        ).build()

        assertEquals(expected = REQUEST_NEARBY_SCANNING, actual = resultA.action)
        assertEquals(expected = RESULT_ACTIVATED, actual = resultA.resultCode)

        val resultB = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_FAILED_TO_ACTIVATE
        ).build()

        assertEquals(expected = REQUEST_NEARBY_SCANNING, actual = resultB.action)
        assertEquals(expected = RESULT_FAILED_TO_ACTIVATE, actual = resultB.resultCode)

        val resultC = ActionResult.Builder(
            REQUEST_NEARBY_SCANNING,
            RESULT_DEACTIVATED
        ).build()

        assertEquals(expected = REQUEST_NEARBY_SCANNING, actual = resultC.action)
        assertEquals(expected = RESULT_DEACTIVATED, actual = resultC.resultCode)
    }

    private fun assertResultReceived(
        timeout: Long = 2,
        unit: TimeUnit = TimeUnit.SECONDS
    ): Pair<Int, ActionResult> {
        val result = receivedResults.poll(timeout, unit)
        assertNotNull(
            result,
            "Did not receive an ActionResult within ${timeout}s."
        )
        return result
    }

    private fun assertResultsReceived(
        expectedCount: Int,
        timeoutPerEvent: Long = 2,
        unit: TimeUnit = TimeUnit.SECONDS
    ): List<Pair<Int, ActionResult>> {
        val actualResults = mutableListOf<Pair<Int, ActionResult>>()
        repeat(expectedCount) {
            val result = receivedResults.poll(timeoutPerEvent, unit)
            assertNotNull(
                result,
                "Timed out waiting for result ${it + 1} of $expectedCount."
            )
            actualResults.add(result)
        }
        return actualResults
    }

    private fun assertNoResultReceived(wait: Long = 200, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        val result = receivedResults.poll(wait, unit)
        assertNull(result, "Should not have received an ActionRequestResult, but did: $result")
    }
}
