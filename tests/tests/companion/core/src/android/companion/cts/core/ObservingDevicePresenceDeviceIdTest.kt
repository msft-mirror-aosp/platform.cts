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

import android.Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
import android.companion.DeviceId
import android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED
import android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED
import android.companion.Flags
import android.companion.ObservingDevicePresenceRequest
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.assertDevicePresenceEvent
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.companion.cts.common.createDeviceId
import android.os.SystemClock.sleep
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for observing device presence base on DeviceId.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:ObservingDevicePresenceDeviceIdTest
 **/

@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_VERIFICATION)
class ObservingDevicePresenceDeviceIdTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testObservingDevicePresence_invalid_deviceId() {
        val deviceId: DeviceId = createDeviceId(null, MAC_ADDRESS_A)
        // create a ObservingDevicePresenceRequest with no 128 bit id device id
        val request = ObservingDevicePresenceRequest.Builder().setDeviceId(deviceId).build()

        withShellPermissionIdentity {
            assertFailsWith(IllegalArgumentException::class) {
                cdm.startObservingDevicePresence(request)
            }
        }
    }

    @Test
    fun testObservingDevicePresence_device_with_require_permissions() {
        val deviceId: DeviceId = createDeviceId(null, MAC_ADDRESS_A)
        // create a ObservingDevicePresenceRequest with no 128 bit id device id
        val request = ObservingDevicePresenceRequest.Builder().setDeviceId(deviceId).build()

        // Try to call startObservingDevicePresence with no permissions.
        assertFailsWith(SecurityException::class) {
            cdm.startObservingDevicePresence(request)
        }

        // Try to call startObservingDevicePresence with only
        // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE permission.
        withShellPermissionIdentity(REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            assertFailsWith(SecurityException::class) {
                cdm.startObservingDevicePresence(request)
            }
        }
    }

    @Test
    fun testObservingDevicePresence_deviceId() {
        testApp.associate(MAC_ADDRESS_A)
        withShellPermissionIdentity {
            val id = cdm.allAssociations[0].id
            val deviceId: DeviceId = createDeviceId(null, MAC_ADDRESS_A)
            val newDeviceId = cdm.createAndSetDeviceId(id, deviceId)
            if (newDeviceId != null) {
                val request = ObservingDevicePresenceRequest.Builder()
                    .setDeviceId(newDeviceId).build()
                // After register the listener,
                // the package should be added into packagesToNotify list.
                cdm.startObservingDevicePresence(request)
                assertNotNull(
                    cdm.allAssociations[0].packagesToNotify,
                    "PackagesToNotify should not be null"
                )

                // Load the association from disk the,
                // packagesToNotify list should be still remaining.
                runShellCommand("cmd companiondevice refresh-cache")
                assertNotNull(
                    cdm.allAssociations[0].packagesToNotify,
                    "PackagesToNotify should not be null"
                )
                // After stop the listener,
                // the package should be removed from the packagesToNotify list.
                cdm.stopObservingDevicePresence(request)
                assertNull(
                    cdm.allAssociations[0].packagesToNotify,
                    "PackagesToNotify should be null"
                )
            }
        }
    }

    @Test
    fun test_devicePresence_deviceId_normalApp_observing_after() {
        // Create association for the test app(normal app)
        testApp.associate(MAC_ADDRESS_A)
        withShellPermissionIdentity {
            val associationId = cdm.allAssociations[0].id
            val deviceId: DeviceId = DeviceId.Builder().setMacAddress(MAC_ADDRESS_A).build()
            val newDeviceId = cdm.createAndSetDeviceId(associationId, deviceId)
            // testApp(normal app) start observing device presence.
            startObservingDevicePresenceById(userId, testApp.packageName, associationId)
            if (newDeviceId != null) {
                // targetApp(pretend it is a system app) start observing device presence.
                val request = ObservingDevicePresenceRequest.Builder()
                    .setDeviceId(newDeviceId).build()
                cdm.startObservingDevicePresence(request)

                // targetApp should receive the callback once simulate device connected.
                simulateDeviceEvent(associationId, EVENT_BT_CONNECTED)
                PrimaryCompanionService.waitAssociationToBtConnect(associationId)
                PrimaryCompanionService.getCurrentEvent()
                    ?.let { assertDevicePresenceEvent(EVENT_BT_CONNECTED, it) }

                // targetApp should receive the callback once simulate device disconnected.
                simulateDeviceEvent(associationId, EVENT_BT_DISCONNECTED)
                sleep(2000)
                PrimaryCompanionService.getCurrentEvent()
                    ?.let { assertDevicePresenceEvent(EVENT_BT_DISCONNECTED, it) }

                // testApp stop observing device presence.
                stopObservingDevicePresenceById(userId, testApp.packageName, associationId)
                // targetApp's service should unbind.
                assertValidCompanionDeviceServicesUnbind()
                // targetApp stop observing device presence.
                cdm.stopObservingDevicePresence(request)
            } else {
                error("The device id should not be null")
            }
        }
    }

    @Test
    fun test_devicePresence_deviceId_normalApp_observing_first() {
        // Create association for the test app
        testApp.associate(MAC_ADDRESS_A)
        withShellPermissionIdentity {
            val associationId = cdm.allAssociations[0].id
            val deviceId: DeviceId = DeviceId.Builder().setMacAddress(MAC_ADDRESS_A).build()
            val newDeviceId = cdm.createAndSetDeviceId(associationId, deviceId)

            // First, testApp(normal app) start observing device presence later.
            startObservingDevicePresenceById(userId, testApp.packageName, associationId)
            // Second, simulate device connected event.
            simulateDeviceEvent(associationId, EVENT_BT_CONNECTED)
            if (newDeviceId != null) {
                // Third, targetApp start observing device presence.
                val request = ObservingDevicePresenceRequest.Builder()
                    .setDeviceId(newDeviceId).build()
                cdm.startObservingDevicePresence(request)

                // Do not need to simulate to send the event,
                // the callback should trigger immediately.
                PrimaryCompanionService.waitAssociationToBtConnect(associationId)
                PrimaryCompanionService.getCurrentEvent()
                    ?.let { assertDevicePresenceEvent(EVENT_BT_CONNECTED, it) }

                // Forth, simulate disconnected event and targetApp
                // should receive the callback.
                simulateDeviceEvent(associationId, EVENT_BT_DISCONNECTED)
                PrimaryCompanionService.getCurrentEvent()
                    ?.let { assertDevicePresenceEvent(EVENT_BT_DISCONNECTED, it) }

                // testApp stop observing device presence.
                stopObservingDevicePresenceById(userId, testApp.packageName, associationId)
                // targetApp's service should unbind.
                assertValidCompanionDeviceServicesUnbind()
                // targetApp stop observing device presence.
                cdm.stopObservingDevicePresence(request)
            } else {
                error("The device id should not be null")
            }
        }
    }

    @Test
    fun test_devicePresenceDeviceId_noCallback_if_normalApp_notObserving() {
        // Create association for the test app(normal app)
        testApp.associate(MAC_ADDRESS_A)
        withShellPermissionIdentity {
            val associationId = cdm.allAssociations[0].id
            val deviceId: DeviceId = DeviceId.Builder().setMacAddress(MAC_ADDRESS_A).build()
            val newDeviceId = cdm.createAndSetDeviceId(associationId, deviceId)
            if (newDeviceId != null) {
                // targetApp start observing device presence.
                val request = ObservingDevicePresenceRequest.Builder()
                    .setDeviceId(newDeviceId).build()
                cdm.startObservingDevicePresence(request)
                // targetApp's service should remain unbind.
                assertValidCompanionDeviceServicesUnbind()
                cdm.stopObservingDevicePresence(request)
            } else {
                error("The device id should not be null")
            }
        }
    }
}
