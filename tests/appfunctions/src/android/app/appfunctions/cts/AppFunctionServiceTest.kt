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

package android.app.appfunctions.cts

import android.Manifest
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.runWithInteractionAllowlisted
import android.app.appfunctions.flags.Flags
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.flags.annotations.RequireFlagsDisabled
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class AppFunctionServiceTest {
    private lateinit var context: Context

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() {
        if (Flags.enableAppFunctionPermissionV2()) {
            AppFunctionUtils.enableAllowlist()
        }
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(AppFunctionManager::class.java)
    }

    @After
    fun teardown() {
        if (Flags.enableAppFunctionPermissionV2()) {
            AppFunctionUtils.disableAllowlist()
        }
    }

    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @RequireFlagsDisabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    @Test
    fun callAppFunctionService_hasAccessToCallingPackageInfo() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID).build()

        val response = executeAppFunctionAndWait(manager, request)

        assertThat(response.exceptionOrNull()).isNull()
        val result = response.getOrNull()
        assertThat(result!!.resultDocument.getPropertyString("callingPackage"))
            .isEqualTo(CURR_PACKAGE_NAME)
        assertThat(result.resultDocument.getPropertyBoolean("hasCallingPackageSigningInfo"))
            .isTrue()
    }

    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @RequireFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    @Test
    fun callAppFunctionService_doesNotHaveAccessToCallingPackage() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackageName = CURR_PACKAGE_NAME,
            appPackageNames = listOf(TEST_PACKAGE_NAME),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID).build()

            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.exceptionOrNull()).isNull()
            val result = response.getOrNull()
            assertThat(result!!.resultDocument.getPropertyString("callingPackage")).isEqualTo("")
            assertThat(result.resultDocument.getPropertyBoolean("hasCallingPackageSigningInfo"))
                .isFalse()
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val CURR_PACKAGE_NAME = "android.app.appfunctions.cts"
        const val TEST_PACKAGE_NAME = "android.app.appfunctions.cts.service.helper"
        const val TEST_FUNCTION_ID = "test"
    }
}
