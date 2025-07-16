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

package android.supervision.cts

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.OBSERVE_ROLE_HOLDERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.flags.Flags
import android.platform.test.annotations.AppModeFull
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.supervision.withSystemSupervisionRoleHeld
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "The SupervisionManager API is not available in instant apps.")
@RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
class SupervisionAppServiceTest : BaseSupervisionTest() {

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionAppService#onSupervisionEnabled",
                "android.app.supervision.SupervisionAppService#onSupervisionDisabled",
            ]
    )
    @EnsureHasPermission(
        BYPASS_ROLE_QUALIFICATION,
        MANAGE_ROLE_HOLDERS,
        QUERY_USERS,
        OBSERVE_ROLE_HOLDERS
    )
    @EnsureHasNoAdditionalUser
    fun testSupervisionAppService_withSystemSupervisionRoleHeld() {
        /*
        This test makes use of the internal workings of various system services. The
        `AppBindingService` listens for role changes to rebind to the services.

        `withSystemSupervisionRoleHeld` registers a listener and does not proceed with executing the
        supplied `action` until its listener has been called. Binding to the service in the test
        should happen after the binding in `AppBindingService`. Additionally, it provides a reliable
        signal to start a timeout for `ServiceReporter.wasMethodCalled`, increasing reliability.
         */
        withSystemSupervisionRoleHeld {
            bindSupervisionAppService { reporter ->
                setSupervisionEnabled(true)
                assertThat(reporter.wasOnSupervisionEnabledCalled()).isTrue()

                setSupervisionEnabled(false)
                assertThat(reporter.wasOnSupervisionDisabledCalled()).isTrue()
            }
        }
    }
}
