/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.bedstead.enterprise

import android.util.Log
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceAdmin
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.nene.devicepolicy.DevicePolicyController
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDeviceAdmin
import com.android.bedstead.testapp.TestAppQueryBuilder

/**
 * Manages device admins for device state tests.
 *
 * @param locator provides access to other dependencies.
 */
class DeviceAdminComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val deviceState: DeviceState by locator
    private val enterpriseComponent: EnterpriseComponent by locator

    /**
     * Maps of device admins added/removed by bedstead.
     *
     *
     * Here, key is a unique identifier for each device admin, defaults to
     * `EnsureHasDeviceAdmin.DEFAULT_KEY`, and can be changed with the
     * `EnsureHasDeviceAdmin` annotation.
     *
     */
    private val mAddedDeviceAdmins: MutableMap<String, DevicePolicyController> = HashMap()
    private val mRemovedDeviceAdmins: MutableMap<String, DevicePolicyController> = HashMap()

    fun ensureHasNoTestDeviceAdmin(userType: UserType) {
        val user: UserReference = deviceState.resolveUserTypeToUser(userType)
        val remoteDeviceAdmins = RemoteDeviceAdmin.fetchAllRemoteDeviceAdmins(user)
        for (remoteDeviceAdmin in remoteDeviceAdmins) {
            remoteDeviceAdmin.devicePolicyController().remove()

            if (!mAddedDeviceAdmins.remove(remoteDeviceAdmin.key(),
                            remoteDeviceAdmin.devicePolicyController())) {
                mRemovedDeviceAdmins[remoteDeviceAdmin.key()] =
                        remoteDeviceAdmin.devicePolicyController()
            }
        }
    }

    fun ensureHasDeviceAdmin(key: String, userType: UserType, isPrimary: Boolean,
                                     deviceAdminQuery: TestAppQueryBuilder) {
        if (isPrimary && enterpriseComponent.primaryPolicyManager != null
                && !deviceAdminQuery.matches(enterpriseComponent.primaryPolicyManager?.testApp())) {
            throw IllegalStateException(
                    "Only one DeviceAdmin can be marked as primary per test (current primary is " +
                            enterpriseComponent.primaryPolicyManager + ")");
        }
        val currentRemoteDeviceAdmin = RemoteDeviceAdmin.fetchRemoteDeviceAdmin(deviceAdminQuery)
        val deviceAdmin: DevicePolicyController
        if (currentRemoteDeviceAdmin == null) {
            val user: UserReference = deviceState.resolveUserTypeToUser(userType)
            deviceAdmin = RemoteDeviceAdmin.setAsDeviceAdmin(user, deviceAdminQuery)
                    .devicePolicyController()
            if (!mRemovedDeviceAdmins.remove(key, deviceAdmin)) {
                mAddedDeviceAdmins.put(key, deviceAdmin)
            }
        } else {
            deviceAdmin = currentRemoteDeviceAdmin.devicePolicyController()
        }

        if (isPrimary) {
            enterpriseComponent.primaryPolicyManager =
                    RemoteDeviceAdmin.forDevicePolicyController(deviceAdmin);
        }
    }

    /**
     * See [DeviceState.deviceAdmin].
     */
    fun deviceAdmin(): RemoteDeviceAdmin? {
        return deviceAdmin(EnsureHasDeviceAdmin.DEFAULT_KEY)
    }

    /**
     * See [DeviceState.deviceAdmin(key)].
     */
    fun deviceAdmin(key: String?): RemoteDeviceAdmin? {
        if (mAddedDeviceAdmins.containsKey(key)) {
            return RemoteDeviceAdmin.forDevicePolicyController(mAddedDeviceAdmins.get(key))
        }
        throw IllegalStateException("No Harrier-managed device admin exists for the given key. " +
                "This method should only be used when the @EnsureHasDeviceAdmin annotation was " +
                "used.")
    }

    override fun teardownShareableState() {
        // Removing the DeviceAdmin instance for every user it was installed for.
        for (deviceAdmin in mAddedDeviceAdmins.values) {
            try {
                deviceAdmin.remove()
            } catch (e: NeneException) {
                Log.e(LOG_TAG, "Could not remove test device admin", e)
                if (deviceAdmin.isActive) {
                    throw NeneException("Could not remove test device admin", e)
                }
            }
        }
        mAddedDeviceAdmins.clear()

        // Readding a DeviceAdmin for every user it was removed for.
        for (deviceAdmin in mRemovedDeviceAdmins.values) {
            RemoteDeviceAdmin.setAsDeviceAdmin(deviceAdmin.user())
        }
        mRemovedDeviceAdmins.clear()
    }

    private companion object {
        const val LOG_TAG = "DeviceAdminComponent"
    }

}