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
import android.companion.ActionResult
import android.companion.Flags
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
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

    private var listener: BiConsumer<Int, ActionResult>? = null

    override fun setUp() {
        super.setUp()
        // Create two associations to test filtering.
        associationIdA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        associationIdB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        receivedResults.clear()
    }

    override fun tearDown() {
        // Clean up listeners to avoid interfering with other tests
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.removeOnActionResultListener(SERVICE_NAME_A)
            cdm.removeOnActionResultListener(SERVICE_NAME_B)
        }
        super.tearDown()
    }

    @Test
    fun testAddAndRemoveListener_receivesResult() {
        val requestA = ActionRequest.Builder(
            ActionRequest.REQUEST_NEARBY_SCANNING,
            ActionRequest.OP_ACTIVATE
        ).build()
        val resultToSend = ActionResult.Builder(requestA, ActionResult.RESULT_SUCCESS)
            .build()

        // 1. Add a listener.
        listener = BiConsumer { associationId, result ->
            receivedResults.offer(associationId to result)
        }

        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setOnActionResultListener(
                intArrayOf(associationIdA),
                SERVICE_NAME_A,
                executor,
                listener!!
            )
        }

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
    }

    @Test
    fun testActionRequestResultListener_multipleListeners() {
        val listenerA = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }
        val listenerB = BiConsumer<Int, ActionResult> { id, result ->
            receivedResults.offer(id to result)
        }

        // 1. Register listeners for both associations.
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

        // 2. Notify a result for association A.
        val requestA = ActionRequest.Builder(
            ActionRequest.REQUEST_NEARBY_SCANNING,
            ActionRequest.OP_ACTIVATE
        ).build()
        val resultForA = ActionResult.Builder(requestA, ActionResult.RESULT_SUCCESS)
            .build()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.notifyActionResult(associationIdA, resultForA)
        }

        // Verify both listeners received it.
        assertResultsReceived(2)
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
