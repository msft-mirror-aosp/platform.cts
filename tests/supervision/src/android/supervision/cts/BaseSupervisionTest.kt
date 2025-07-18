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
import android.Manifest.permission.CREATE_USERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.SupervisionManager
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule

/** Base class for supervision CTS tests. */
@AppModeFull(reason = "The SupervisionManager API is not available in instant apps.")
open class BaseSupervisionTest {

    fun setSupervisionEnabled(enabled: Boolean) {
        callWithShellPermissionIdentity(BYPASS_ROLE_QUALIFICATION, QUERY_USERS) {
            supervisionManager.setSupervisionEnabled(enabled)
            assertThat(supervisionManager.isSupervisionEnabled()).isEqualTo(enabled)
        }
    }

    /**
     * Creates a new supervising user and executes the given [action].
     *
     * By default, supervision is enabled and a PIN is set for the supervising user.
     *
     * When the [action] completes, the supervising user will be removed and supervision will be
     * disabled.
     *
     * @param supervisionEnabled Whether supervision will be enabled when executing the [action].
     * @param hasPin Whether a PIN will be set for the supervising user.
     * @param action The block of code to execute.
     */
    fun withSupervisingUser(
        supervisionEnabled: Boolean = true,
        hasPin: Boolean = true,
        action: () -> Unit
    ) {
        setSupervisionEnabled(supervisionEnabled)

        val userHandle = userManager.createSupervisingUser()
        try {
            if (hasPin) {
                val pin = "1234"
                val result = runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    "locksettings set-pin --user ${userHandle.identifier} $pin",
                )
                assertThat(result).contains(pin)
            }
            action()
        } finally {
            userManager.removeSupervisingUser(userHandle)
            setSupervisionEnabled(false)
        }
    }

    companion object {
        @[JvmField ClassRule Rule]
        val deviceState = DeviceState()

        val context = TestApis.context().instrumentedContext()
        val supervisionManager = context.getSystemService(SupervisionManager::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
    }
}

private fun UserManager.createSupervisingUser(): UserHandle =
    callWithShellPermissionIdentity(CREATE_USERS) {
        val userInfo = createUser("Supervising", UserManager.USER_TYPE_PROFILE_SUPERVISING, 0)
        assertThat(userInfo).isNotNull()
        assertThat(userInfo?.userHandle).isNotNull()
        userInfo?.userHandle!!
    }

private fun UserManager.removeSupervisingUser(userHandle: UserHandle) {
    callWithShellPermissionIdentity(CREATE_USERS) {
        val removed = removeUser(userHandle)
        assertThat(removed).isTrue()
    }
}
