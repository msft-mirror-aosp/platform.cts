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
package com.android.bedstead.deviceadminapp

import android.content.Context
import com.android.bedstead.deviceadminapp.DeviceAdminApp.deviceAdminComponentName
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.users.UserType
import com.android.eventlib.EventLogs
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DeviceAdminAppTest {
    companion object {
        @JvmField val sContext: Context = context().instrumentedContext()

        @ClassRule @Rule @JvmField val sDeviceState: DeviceState = DeviceState()
    }

    // This test assumes that DeviceAdminApp is set as a dependency of the test.
    @Before
    fun setUp() {
        EventLogs.resetLogs()
    }

    // TODO(scottjonathan): Add annotations to ensure no accounts and no users.
    @Test
    @RequireRunOnSystemUser
    @Throws(Exception::class)
    fun setAsDeviceOwner_isEnabled() {
        devicePolicy()
            .setDeviceOwner(deviceOwnerComponent = deviceAdminComponentName(context = sContext))
            .use { deviceOwner ->
                val logs: EventLogs<DeviceAdminEnabledEvent?> =
                    DeviceAdminEnabledEvent.queryPackage(sContext.packageName)
                assertThat(logs.poll()).isNotNull()
            }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasNoWorkProfile
    @Ignore
    fun setAsProfileOwner_isEnabled() {
        users()
            .createUser()
            .parent(users().instrumented())
            .type(users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
            .createAndStart()
            .use { profile ->
                packages().find(sContext.packageName).installExisting(profile)
                devicePolicy()
                    .setProfileOwner(
                        user = profile,
                        profileOwnerComponent = deviceAdminComponentName(sContext),
                    )

                val logs: EventLogs<DeviceAdminEnabledEvent?> =
                    DeviceAdminEnabledEvent.queryPackage(sContext.packageName).onUser(profile)
                assertThat(logs.poll()).isNotNull()
            }
    }
}
