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
import android.app.appfunctions.AppFunctionAttribution
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.UserHandle
import android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED
import android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.database.getStringOrNull
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(
    FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
)
class AppFunctionAccessHistoryProviderTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val setCancellationTimeoutRule: DeviceConfigStateChangerRule =
        DeviceConfigStateChangerRule(
            context,
            "appfunctions",
            "execute_app_function_cancellation_timeout_millis",
            "3000",
        )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = doBlocking { TestAppFunctionServiceLifecycleReceiver.reset() }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessHistoryContentUri"])
    @Test
    fun queryAppFunctionAccessHistory_withInvalidUriType_shouldFail() = doBlocking {
        runWithShellPermission(MANAGE_APP_FUNCTION_ACCESS) {
            assertFailsWith<IllegalArgumentException> {
                context.contentResolver.queryAllAccessHistory(
                    Uri.parse("content://com.android.appfunction.accesshistory")
                )
            }
            assertFailsWith<IllegalArgumentException> {
                context.contentResolver.queryAllAccessHistory(
                    Uri.parse("content://com.android.appfunction.accesshistory/user")
                )
            }
            assertFailsWith<IllegalArgumentException> {
                context.contentResolver.queryAllAccessHistory(
                    Uri.parse("content://com.android.appfunction.accesshistory/user/invalid")
                )
            }
            assertFailsWith<IllegalArgumentException> {
                context.contentResolver.queryAllAccessHistory(
                    Uri.parse("content://com.android.appfunction.accesshistory/user/10/path")
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessHistoryContentUri"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun executeAppFunction_shouldCreateAccessHistoryInCurrentUser() = doBlocking {
        runWithShellPermission(MANAGE_APP_FUNCTION_ACCESS) {
            clearAllAccessHistory(context)
            assertMetadataIndexed(context)

            executeAll(
                context,
                listOf(
                    ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp")
                        .setAttribution(
                            AppFunctionAttribution.Builder(
                                    AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY
                                )
                                .build()
                        )
                        .build(),
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "noOp")
                        .setAttribution(
                            AppFunctionAttribution.Builder(
                                    AppFunctionAttribution.INTERACTION_TYPE_USER_SCHEDULED
                                )
                                .build()
                        )
                        .build(),
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "noOp")
                        .setAttribution(
                            AppFunctionAttribution.Builder(
                                    AppFunctionAttribution.INTERACTION_TYPE_OTHER
                                )
                                .setCustomInteractionType("CUSTOM_TYPE")
                                .setThreadId("TestThreadId")
                                .setInteractionUri(Uri.parse("content://test.uri"))
                                .build()
                        )
                        .build(),
                ),
            )

            assertAccessHistoryContainsExactly(
                currentContext = context,
                targetContext = context,
                expected =
                    arrayOf(
                        AccessHistory(
                            agentPackageName = CURRENT_PKG,
                            targetPackageName = CURRENT_PKG,
                            interactionType = AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY,
                            customInteractionType = null,
                            interactionUri = null,
                            threadId = null,
                            accessTime = 0,
                            duration = 0,
                        ),
                        AccessHistory(
                            agentPackageName = CURRENT_PKG,
                            targetPackageName = TEST_HELPER_PKG,
                            interactionType =
                                AppFunctionAttribution.INTERACTION_TYPE_USER_SCHEDULED,
                            customInteractionType = null,
                            interactionUri = null,
                            threadId = null,
                            accessTime = 0,
                            duration = 0,
                        ),
                        AccessHistory(
                            agentPackageName = CURRENT_PKG,
                            targetPackageName = TEST_HELPER_PKG,
                            interactionType = AppFunctionAttribution.INTERACTION_TYPE_OTHER,
                            customInteractionType = "CUSTOM_TYPE",
                            interactionUri = "content://test.uri",
                            threadId = "TestThreadId",
                            accessTime = 0,
                            duration = 0,
                        ),
                    ),
            )
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessHistoryContentUri"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun queryAccessHistory_withoutReadPermission_shouldFail() = doBlocking {
        assertFailsWith<SecurityException> {
            context.contentResolver.queryAllAccessHistory(context.getAppFunctionAccessHistoryUri())
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessHistoryContentUri"])
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasAdditionalUser
    fun queryOtherUserAccessHistory_withoutCrossUserPermission_shouldFail() = doBlocking {
        runWithShellPermission(MANAGE_APP_FUNCTION_ACCESS) {
            var secondaryContext: Context? = null
            runWithShellPermission(INTERACT_ACROSS_USERS_FULL_PERMISSION) {
                val secondaryUser = sDeviceState.additionalUser()
                installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
                secondaryContext =
                    context.createContextAsUser(checkNotNull(secondaryUser).userHandle(), 0)
                assumeTrue(
                    "Test requires an additional user different from the primary user.",
                    secondaryUser != TestApis.users().instrumented(),
                )
                clearAllAccessHistory(checkNotNull(secondaryContext))
            }

            assertFailsWith<SecurityException> {
                context.contentResolver.queryAllAccessHistory(
                    checkNotNull(secondaryContext).getAppFunctionAccessHistoryUri()
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessHistoryContentUri"])
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasAdditionalUser
    fun queryOtherUserAccessHistory_withCrossUserPermission_shouldSucceed() = doBlocking {
        runWithShellPermission(MANAGE_APP_FUNCTION_ACCESS, INTERACT_ACROSS_USERS_FULL_PERMISSION) {
            val secondaryUser = sDeviceState.additionalUser()
            assumeTrue(
                "Test requires an additional user different from the primary user.",
                secondaryUser != TestApis.users().instrumented(),
            )
            installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
            installExistingPackageAsUser(TEST_HELPER_PKG, secondaryUser)
            val secondaryContext = context.createContextAsUser(secondaryUser.userHandle(), 0)
            clearAllAccessHistory(secondaryContext)
            assertMetadataIndexed(secondaryContext)
            executeAll(
                secondaryContext,
                listOf(
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "noOp")
                        .setAttribution(
                            AppFunctionAttribution.Builder(
                                    AppFunctionAttribution.INTERACTION_TYPE_OTHER
                                )
                                .setCustomInteractionType("CUSTOM_TYPE")
                                .setThreadId("TestThreadId")
                                .setInteractionUri(Uri.parse("content://test.uri"))
                                .build()
                        )
                        .build()
                ),
            )

            assertAccessHistoryContainsExactly(
                currentContext = context,
                targetContext = secondaryContext,
                expected =
                    arrayOf(
                        AccessHistory(
                            agentPackageName = CURRENT_PKG,
                            targetPackageName = TEST_HELPER_PKG,
                            interactionType = AppFunctionAttribution.INTERACTION_TYPE_OTHER,
                            customInteractionType = "CUSTOM_TYPE",
                            interactionUri = "content://test.uri",
                            threadId = "TestThreadId",
                            accessTime = 0,
                            duration = 0,
                        )
                    ),
            )
        }
    }

    private fun ContentResolver.queryAllAccessHistory(uri: Uri): List<AccessHistory>? {
        val cursor = query(uri, null, null, null) ?: return null
        val result = buildList {
            while (cursor.moveToNext()) {
                add(AccessHistory.read(cursor))
            }
        }
        cursor.close()
        return result
    }

    /** The test access history that ignores [accessTime] and [duration] when comparing. */
    internal class AccessHistory(
        val agentPackageName: String,
        val targetPackageName: String,
        val interactionType: Int,
        val customInteractionType: String?,
        val interactionUri: String?,
        val threadId: String?,
        val accessTime: Long,
        val duration: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AccessHistory

            // Ignoring accessTime and duration
            if (agentPackageName != other.agentPackageName) return false
            if (targetPackageName != other.targetPackageName) return false
            if (interactionType != other.interactionType) return false
            if (customInteractionType != other.customInteractionType) return false
            if (interactionUri != other.interactionUri) return false
            if (threadId != other.threadId) return false
            return true
        }

        override fun hashCode(): Int {
            // Ignoring accessTime and duration
            var result = agentPackageName.hashCode()
            result = 31 * result + targetPackageName.hashCode()
            result = 31 * result + interactionType
            result = 31 * result + (customInteractionType?.hashCode() ?: 0)
            result = 31 * result + (interactionUri?.hashCode() ?: 0)
            result = 31 * result + (threadId?.hashCode() ?: 0)
            return result
        }

        companion object {
            fun read(cursor: Cursor): AccessHistory {
                return AccessHistory(
                    agentPackageName =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                            )
                        ),
                    targetPackageName =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                            )
                        ),
                    interactionType =
                        cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_INTERACTION_TYPE
                            )
                        ),
                    customInteractionType =
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE
                            )
                        ),
                    interactionUri =
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_INTERACTION_URI
                            )
                        ),
                    threadId =
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_THREAD_ID
                            )
                        ),
                    accessTime =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_ACCESS_TIME
                            )
                        ),
                    duration =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                AppFunctionManager.AccessHistory.COLUMN_DURATION
                            )
                        ),
                )
            }
        }
    }

    private suspend fun clearAllAccessHistory(targetContext: Context) {
        val manager = targetContext.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        runWithShellPermission(MANAGE_APP_FUNCTION_ACCESS, INTERACT_ACROSS_USERS_FULL_PERMISSION) {
            manager.clearAccessHistory()
        }
    }

    private fun Context.getAppFunctionAccessHistoryUri(): Uri {
        val manager = getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        return manager.getAccessHistoryContentUri()
    }

    private suspend fun executeAll(
        targetContext: Context,
        requests: List<ExecuteAppFunctionRequest>,
    ) {
        val manager = targetContext.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)

        for (request in requests) {
            runWithAppFunctionAccess(
                agentPackage = targetContext.packageName,
                agentUser = targetContext.user,
                targetPackage = request.targetPackageName,
                targetUser = targetContext.user,
            ) {
                executeAppFunctionAndWait(manager, request)
            }
        }
    }

    private suspend fun assertMetadataIndexed(targetContext: Context) {
        retryAssert {
            assertThat(getAllStaticMetadataPackages(targetContext))
                .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG)
            assertThat(getAllRuntimeMetadataPackages(targetContext))
                .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG)
        }
    }

    private suspend fun runWithAppFunctionAccess(
        agentPackage: String,
        agentUser: UserHandle,
        targetPackage: String,
        targetUser: UserHandle,
        block: suspend () -> Unit,
    ) {
        try {
            ShellCommand.builder("cmd app_function grant-app-function-access")
                .addOption("--agent-package", agentPackage)
                .addOption("--agent-user", agentUser.identifier)
                .addOption("--target-package", targetPackage)
                .addOption("--target-user", targetUser.identifier)
                .execute()
            block.invoke()
        } finally {
            ShellCommand.builder("cmd app_function revoke-app-function-access")
                .addOption("--agent-package", agentPackage)
                .addOption("--agent-user", agentUser.identifier)
                .addOption("--target-package", targetPackage)
                .addOption("--target-user", targetUser.identifier)
                .execute()
        }
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")
    }

    private suspend fun assertAccessHistoryContainsExactly(
        currentContext: Context,
        targetContext: Context,
        expected: Array<AccessHistory>,
    ) {
        retryAssert {
            val accessHistories =
                currentContext.contentResolver.queryAllAccessHistory(
                    targetContext.getAppFunctionAccessHistoryUri()
                )
            assertThat(accessHistories).isNotNull()
            assertThat(accessHistories).containsExactly(*expected)
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"

        const val MANAGE_APP_FUNCTION_ACCESS = Manifest.permission.MANAGE_APP_FUNCTION_ACCESS
        const val INTERACT_ACROSS_USERS_FULL_PERMISSION =
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
    }
}
