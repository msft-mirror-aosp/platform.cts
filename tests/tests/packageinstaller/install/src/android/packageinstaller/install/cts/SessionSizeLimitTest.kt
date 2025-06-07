/*
 * Copyright 2025 The Android Open Source Project
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
package android.packageinstaller.install.cts

import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED
import android.net.Uri
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class SessionSizeLimitTest : PackageInstallerTestBase() {

    @Test
    fun setOriginatingUri_exceedLimit_fail() {
        val uriExceedLimit = Uri.parse("a".repeat(2049))
        val params = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        assertThrows(IllegalArgumentException::class.java) {
            params.setOriginatingUri(uriExceedLimit)
        }
        val sessionId = pi.createSession(params)
        assertThat(pi.getSessionInfo(sessionId)!!.originatingUri).isNull()
        pi.openSession(sessionId).abandon()
    }

    @Test
    fun setReferrerUri_exceedLimit_fail() {
        val uriExceedLimit = Uri.parse("a".repeat(2049))
        val params = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        assertThrows(IllegalArgumentException::class.java) {
            params.setReferrerUri(uriExceedLimit)
        }
        val sessionId = pi.createSession(params)
        assertThat(pi.getSessionInfo(sessionId)!!.referrerUri).isNull()
        pi.openSession(sessionId).abandon()
    }

    @Test
    fun setWhiteListedRestrictedPermissions() {
        val restrictedPermission1 = "android.permission.READ_EXTERNAL_STORAGE"
        val restrictedPermission2 = "android.permission.READ_SMS"
        val fakePermission = "a".repeat(2049)
        val (sessionId, session) = createSession(
            0,
            false,
            null,
            {params -> params.setWhitelistedRestrictedPermissions(
                setOf(restrictedPermission1, restrictedPermission2, fakePermission)
            )}
        )
        assertThat(
            pi.getSessionInfo(sessionId)!!.getWhitelistedRestrictedPermissions()
        ).isEqualTo(setOf(restrictedPermission1, restrictedPermission2))
        session.abandon()
    }

    @Test
    fun setPermissionState_exceedLimit_fail() {
        val permission = "a".repeat(125)
        val params = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        assertThrows(IllegalArgumentException::class.java) {
            for (i in 1..130) {
                params.setPermissionState(
                    String.format("%s%03d", permission, i),
                    PERMISSION_STATE_GRANTED
                )
            }
        }
        val p = TestApis.permissions().withPermission(
            "android.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS"
        )
        try {
            val sessionId = pi.createSession(params)
            assertThat(pi.getSessionInfo(sessionId)!!.getGrantedRuntimePermissions()!!.size)
                .isAtMost(128)
            pi.openSession(sessionId).abandon()
        } finally {
            p.close()
        }
    }
}
