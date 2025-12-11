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
import android.companion.ActionRequest.REQUEST_NEARBY_ADVERTISING
import android.companion.ActionRequest.REQUEST_NEARBY_SCANNING
import android.companion.ActionRequest.REQUEST_TRANSPORT
import android.companion.ActionResult
import android.companion.ActionResult.RESULT_ACTIVATED
import android.companion.DevicePresenceEvent
import android.companion.Flags
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.SERVICE_NAME_A
import android.companion.cts.common.SERVICE_NAME_B
import android.companion.cts.common.getAssociationForPackage
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
 * Test CDM APIs for requesting actions to companion apps.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:RequestActionTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_SYNC)
class RequestActionTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    override fun setUp() {
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setRequestActionAllowList(listOf(SERVICE_NAME_A, SERVICE_NAME_B))
        }
    }

    override fun tearDown() {
        withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
            cdm.setRequestActionAllowList(null)
        }
    }

    @Test
    fun testRequestAction_deliversAllActions() {
        var associationIdA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        val associationIdArray = intArrayOf(associationIdA)

        val allActions = listOf(
            REQUEST_NEARBY_SCANNING,
            REQUEST_NEARBY_ADVERTISING,
            REQUEST_TRANSPORT
        )

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

        for (action in allActions) {
            // Reset service state for each action type
            PrimaryCompanionService.clearReceivedActions()
            val startRequest = ActionRequest.Builder(action, OP_ACTIVATE).build()
            val stopRequest = ActionRequest.Builder(action, OP_DEACTIVATE).build()
            // 1. First "start" request from service A. App should be notified.
            withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
                cdm.requestAction(startRequest, SERVICE_NAME_A, associationIdArray)
            }
            PrimaryCompanionService.waitToActionRequest(
                expectedAssociationId = associationIdA,
                expectedAction = action
            )

            val resultFromApp = ActionResult.Builder(
                action,
                RESULT_ACTIVATED
            ).build()
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
                cdm.notifyActionResult(associationIdA, resultFromApp)
            }

            // 2. Second "start" request from service B. App should NOT be notified.
            withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
                cdm.requestAction(startRequest, SERVICE_NAME_B, associationIdArray)
            }
            PrimaryCompanionService.assertNoActionRequest()

            // 3. First "stop" request from service A. App should NOT be notified.
            withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
                cdm.requestAction(stopRequest, SERVICE_NAME_A, associationIdArray)
            }
            PrimaryCompanionService.assertNoActionRequest()

            // 4. Second (and last) "stop" request from service B. App should be notified.
            withShellPermissionIdentity(USE_COMPANION_TRANSPORTS) {
                cdm.requestAction(stopRequest, SERVICE_NAME_B, associationIdArray)
            }
            PrimaryCompanionService.waitToActionRequest(
                expectedAssociationId = associationIdA,
                expectedAction = action
            )
        }
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
    fun testActionRequest_builder() {
        val requestA = ActionRequest.Builder(REQUEST_TRANSPORT, OP_ACTIVATE).build()
        assertEquals(actual = REQUEST_TRANSPORT, expected = requestA.action)
        assertEquals(actual = OP_ACTIVATE, expected = requestA.operation)
    }

    @Test
    fun testRequestAction_withoutPermission_throwsSecurityException() {
        targetApp.associate(MAC_ADDRESS_A)
        val association = withShellPermissionIdentity {
            getAssociationForPackage(userId, targetApp.packageName, MAC_ADDRESS_A, cdm)
        }

        assertFailsWith(SecurityException::class) {
            cdm.requestAction(
                ActionRequest.Builder(REQUEST_TRANSPORT, OP_ACTIVATE).build(),
                SERVICE_NAME_A,
                intArrayOf(association.id)
            )
        }
    }
}
