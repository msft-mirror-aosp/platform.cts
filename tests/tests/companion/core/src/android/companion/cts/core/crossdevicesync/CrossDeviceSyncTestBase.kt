/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.companion.cts.core.crossdevicesync

import android.Manifest.permission.DELIVER_COMPANION_MESSAGES
import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.DevicePresenceEvent.Builder
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY
import android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.core.CoreTestBase
import android.util.Log
import com.android.crossdevicesync.lib.api.CrossDeviceSyncTestLib
import kotlin.time.Duration.Companion.seconds

open class CrossDeviceSyncTestBase : CoreTestBase() {
    var associationId: Int = -1
    lateinit var testLib: CrossDeviceSyncTestLib

    override fun setUp() {
        super.setUp()
        associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        testLib = CrossDeviceSyncTestLib.newInstance(context)
        withShellPermissionIdentity(
            REQUEST_COMPANION_SELF_MANAGED,
            MANAGE_COMPANION_DEVICES,
            DELIVER_COMPANION_MESSAGES
        ) {
            // Force raw transport
            cdm.overrideTransportType(1)
            Log.i(TAG, "setUp: notifying presence and nearby")
            cdm.notifyDevicePresence(
                associationId,
                Builder().setAssociationId(associationId)
                    .setEvent(EVENT_SELF_MANAGED_APPEARED)
                    .build()
            )
            cdm.notifyDevicePresence(
                associationId,
                Builder().setAssociationId(associationId)
                    .setEvent(EVENT_SELF_MANAGED_NEARBY)
                    .build()
            )
            Log.i(TAG, "setUp: attaching transport")
            cdm.attachSystemDataTransport(
                associationId,
                testLib.cdmPipe?.getInputStreamForCdmTransport()!!,
                testLib.cdmPipe?.getOutputStreamForCdmTransport()!!
            )
        }
    }

    override fun tearDown() {
        if (::testLib.isInitialized) {
            testLib.close()
        }
        withShellPermissionIdentity(
            REQUEST_COMPANION_SELF_MANAGED,
            MANAGE_COMPANION_DEVICES,
            DELIVER_COMPANION_MESSAGES
        ) {
            cdm.overrideTransportType(0)
            Log.i(TAG, "tearDown: clearing presence and nearby")
            cdm.notifyDevicePresence(
                associationId,
                Builder().setAssociationId(associationId)
                    .setEvent(EVENT_SELF_MANAGED_DISAPPEARED)
                    .build()
            )
            cdm.notifyDevicePresence(
                associationId,
                Builder().setAssociationId(associationId)
                    .setEvent(EVENT_SELF_MANAGED_NOT_NEARBY)
                    .build()
            )
            Log.i(TAG, "tearDown: detaching transport")
            cdm.detachSystemDataTransport(associationId)
        }
        PrimaryCompanionService.clearReceivedActions()
        // Wait long enough to ensure the service will be unbound
        PrimaryCompanionService.waitForUnbind(10.seconds)
        super.tearDown()
    }

    private companion object {
        const val TAG: String = "CrossDeviceSyncTestBase"
    }
}
