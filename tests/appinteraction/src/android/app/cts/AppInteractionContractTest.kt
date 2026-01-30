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

package android.app.cts

import android.Manifest
import android.app.AppInteractionContract
import android.app.UiAutomation
import android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_INTERACTION_API
import android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(FLAG_ENABLE_APP_INTERACTION_API)
class AppInteractionContractTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var uiAutomation: UiAutomation

    @Before
    fun setup() {
        uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getInteractionHistoryUriAsUser"])
    @EnsureDoesNotHavePermission(
        value = [Manifest.permission.READ_APP_INTERACTION, Manifest.permission.INTERACT_ACROSS_USERS_FULL]
    )
    @Test
    fun queryAppInteractionHistory_withoutReadPermission_shouldFail() = doBlocking {
        assertFailsWith<SecurityException> {
            context.contentResolver.queryAll(
                AppInteractionContract.getInteractionHistoryUriAsUser(context.user)
            )
        }
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getInteractionHistoryUriAsUser"])
    @EnsureHasPermission(Manifest.permission.READ_APP_INTERACTION)
    @Test
    fun queryAppInteractionHistory_withInvalidUriType_shouldFail() = doBlocking {
        assertFailsWith<IllegalArgumentException> {
            // Missing user suffix
            context.contentResolver.queryAll(
                Uri.parse("content://com.android.appinteraction.history")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            // Missing target user
            context.contentResolver.queryAll(
                Uri.parse("content://com.android.appinteraction.history/user")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            // Invalid user id
            context.contentResolver.queryAll(
                Uri.parse("content://com.android.appinteraction.history/user/invalid")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            // Invalid additional suffix
            context.contentResolver.queryAll(
                Uri.parse("content://com.android.appinteraction.history/user/10/path")
            )
        }
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getInteractionHistoryUriAsUser"])
    @EnsureHasPermission(Manifest.permission.READ_APP_INTERACTION)
    @EnsureDoesNotHavePermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    @EnsureHasNoDeviceOwner
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @Test
    fun queryAppInteractionHistory_crossUserWithoutPermission_shouldFail() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        assertFailsWith<SecurityException> {
            context.contentResolver.queryAll(
                Uri.parse(
                    "content://com.android.appinteraction.history/user/${secondaryUser.userHandle().identifier}"
                )
            )
        }
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getDeviceAssistancePackageNames"])
    @EnsureDoesNotHavePermission(
        value =
            [
                Manifest.permission.EXECUTE_APP_FUNCTIONS,
                Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
                Manifest.permission.READ_APP_INTERACTION,
            ]
    )
    @Test
    fun getDeviceAssistancePackageNames_withoutPermission_shouldFail() = doBlocking {
        assertFailsWith<SecurityException> {
            val unused = AppInteractionContract.getDeviceAssistancePackageNames(context)
        }
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getDeviceAssistancePackageNames"])
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @Test
    fun getDeviceAssistancePackageNames_withExecuteAppFunctionsPermission_shouldSucceed() {
        getDeviceAssistancePackageNames_withPermission_shouldSucceed_base()
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getDeviceAssistancePackageNames"])
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    @RequiresFlagsEnabled(FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    @Test
    fun getDeviceAssistancePackageNames_withExecuteAppFunctionsSystemPermission_shouldSucceed() {
        getDeviceAssistancePackageNames_withPermission_shouldSucceed_base()
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getDeviceAssistancePackageNames"])
    @EnsureHasPermission(Manifest.permission.READ_APP_INTERACTION)
    @Test
    fun getDeviceAssistancePackageNames_withReadAppInteractionPermission_shouldSucceed() {
        getDeviceAssistancePackageNames_withPermission_shouldSucceed_base()
    }

    fun getDeviceAssistancePackageNames_withPermission_shouldSucceed_base() {
        val unused = AppInteractionContract.getDeviceAssistancePackageNames(context)
    }

    @ApiTest(apis = ["android.app.AppInteractionContract#getDeviceAssistancePackageNames"])
    @EnsureHasPermission(Manifest.permission.READ_APP_INTERACTION)
    @Test
    fun getDeviceAssistancePackageNames_shouldAllBeSystemApps() {
        val packages: List<String> = AppInteractionContract.getDeviceAssistancePackageNames(context)

        assertThat(packages).isNotNull()
        for (pkg in packages) {
            assertThat(pkg).isNotEmpty()
            assertIsSystemApp(pkg)
        }
    }

    private fun assertIsSystemApp(packageName: String) {
        val pm = context.packageManager
        if (pm.isSystemApp(packageName)) {
            return
        }
        fail("$packageName is not a system app")
    }

    private fun PackageManager.isSystemApp(packageName: String): Boolean {
        try {
            val applicationInfo = getApplicationInfo(packageName, 0)
            return (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun ContentResolver.queryAll(uri: Uri) {
        query(uri, null, null, null)
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
    }
}
