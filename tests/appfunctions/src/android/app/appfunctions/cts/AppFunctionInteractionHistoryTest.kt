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
import android.app.AppInteractionAttribution
import android.app.AppInteractionContract
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.LegacySchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.clearInteractionAllowlist
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunction
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.setInteractionAllowlist
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.database.getStringOrNull
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_INTERACTION_API)
class AppFunctionInteractionHistoryTest {
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

    @Before
    fun setup() = doBlocking {
        TestAppFunctionServiceLifecycleReceiver.reset()
        if (Flags.enableAppFunctionPermissionV2()) {
            AppFunctionUtils.enableAllowlist()
            setInteractionAllowlist(CtsApp.PACKAGE_NAME, listOf(LegacySchemaHelperApp.PACKAGE_NAME))
        }
        installApkAsUser(TestApis.users().instrumented(), LegacySchemaHelperApp.APK_PATH)
    }

    @After
    fun teardown() = doBlocking {
        if (Flags.enableAppFunctionPermissionV2()) {
            AppFunctionUtils.disableAllowlist()
            clearInteractionAllowlist()
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun executeAppFunction_shouldCreateInteractionHistoryInCurrentUser() = doBlocking {
        runWithShellPermission(READ_APP_INTERACTION_PERMISSION) {
            val testStartTime = System.currentTimeMillis()
            assertMetadataIndexed(context)

            executeAll(
                context,
                listOf(
                    ExecuteAppFunctionRequest.Builder(CtsApp.PACKAGE_NAME, "noOp")
                        .setAttribution(
                            AppInteractionAttribution.Builder(
                                    AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY
                                )
                                .build()
                        )
                        .build(),
                    ExecuteAppFunctionRequest.Builder(LegacySchemaHelperApp.PACKAGE_NAME, "noOp")
                        .setAttribution(
                            AppInteractionAttribution.Builder(
                                    AppInteractionAttribution.INTERACTION_TYPE_USER_SCHEDULED
                                )
                                .build()
                        )
                        .build(),
                    ExecuteAppFunctionRequest.Builder(LegacySchemaHelperApp.PACKAGE_NAME, "noOp")
                        .setAttribution(
                            AppInteractionAttribution.Builder(
                                    AppInteractionAttribution.INTERACTION_TYPE_OTHER
                                )
                                .setCustomInteractionType("CUSTOM_TYPE")
                                .setInteractionUri(Uri.parse("content://test.uri"))
                                .build()
                        )
                        .build(),
                ),
            )

            assertInteractionHistoryContainsExactly(
                after = testStartTime,
                currentContext = context,
                targetContext = context,
                expected =
                    arrayOf(
                        InteractionHistory(
                            agentPackageName = CtsApp.PACKAGE_NAME,
                            targetPackageName = CtsApp.PACKAGE_NAME,
                            interactionType = AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY,
                            customInteractionType = null,
                            interactionUri = null,
                            accessTime = 0,
                        ),
                        InteractionHistory(
                            agentPackageName = CtsApp.PACKAGE_NAME,
                            targetPackageName = LegacySchemaHelperApp.PACKAGE_NAME,
                            interactionType =
                                AppInteractionAttribution.INTERACTION_TYPE_USER_SCHEDULED,
                            customInteractionType = null,
                            interactionUri = null,
                            accessTime = 0,
                        ),
                        InteractionHistory(
                            agentPackageName = CtsApp.PACKAGE_NAME,
                            targetPackageName = LegacySchemaHelperApp.PACKAGE_NAME,
                            interactionType = AppInteractionAttribution.INTERACTION_TYPE_OTHER,
                            customInteractionType = "CUSTOM_TYPE",
                            interactionUri = "content://test.uri",
                            accessTime = 0,
                        ),
                    ),
            )
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasAdditionalUser
    fun executeAppFunctionInDifferentUser_shouldCreateInteractionHistoryInTargetUser() =
        doBlocking {
            runWithShellPermission(
                READ_APP_INTERACTION_PERMISSION,
                INTERACT_ACROSS_USERS_FULL_PERMISSION,
            ) {
                val testStartTime = System.currentTimeMillis()
                val secondaryUser = sDeviceState.additionalUser()
                assumeTrue(
                    "Test requires an additional user different from the primary user.",
                    secondaryUser != TestApis.users().instrumented(),
                )
                installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
                installExistingPackageAsUser(LegacySchemaHelperApp.PACKAGE_NAME, secondaryUser)
                val secondaryContext = context.createContextAsUser(secondaryUser.userHandle(), 0)
                assertMetadataIndexed(secondaryContext)
                executeAll(
                    secondaryContext,
                    listOf(
                        ExecuteAppFunctionRequest.Builder(
                                LegacySchemaHelperApp.PACKAGE_NAME,
                                "noOp",
                            )
                            .setAttribution(
                                AppInteractionAttribution.Builder(
                                        AppInteractionAttribution.INTERACTION_TYPE_OTHER
                                    )
                                    .setCustomInteractionType("CUSTOM_TYPE")
                                    .setInteractionUri(Uri.parse("content://test.uri"))
                                    .build()
                            )
                            .build()
                    ),
                )

                // Assert that no interaction history is created in the current user's context.
                assertInteractionHistoryContainsExactly(
                    after = testStartTime,
                    currentContext = context,
                    targetContext = context,
                    expected = arrayOf(),
                )
                assertInteractionHistoryContainsExactly(
                    after = testStartTime,
                    currentContext = context,
                    targetContext = secondaryContext,
                    expected =
                        arrayOf(
                            InteractionHistory(
                                agentPackageName = CtsApp.PACKAGE_NAME,
                                targetPackageName = LegacySchemaHelperApp.PACKAGE_NAME,
                                interactionType = AppInteractionAttribution.INTERACTION_TYPE_OTHER,
                                customInteractionType = "CUSTOM_TYPE",
                                interactionUri = "content://test.uri",
                                accessTime = 0,
                            )
                        ),
                )
            }
        }

    @Test
    fun uninstallTargetPackage_shouldDeleteInteractionHistory() = doBlocking {
        runWithShellPermission(READ_APP_INTERACTION_PERMISSION) {
            installApkAsUser(TestApis.users().instrumented(), LegacySchemaHelperApp.APK_PATH)
            val testStartTime = System.currentTimeMillis()
            assertMetadataIndexed(context)
            executeAll(
                context,
                listOf(
                    ExecuteAppFunctionRequest.Builder(LegacySchemaHelperApp.PACKAGE_NAME, "noOp")
                        .setAttribution(
                            AppInteractionAttribution.Builder(
                                    AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY
                                )
                                .build()
                        )
                        .build()
                ),
            )
            assertInteractionHistoryContainsExactly(
                after = testStartTime,
                currentContext = context,
                targetContext = context,
                expected =
                    arrayOf(
                        InteractionHistory(
                            agentPackageName = CtsApp.PACKAGE_NAME,
                            targetPackageName = LegacySchemaHelperApp.PACKAGE_NAME,
                            interactionType = AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY,
                            customInteractionType = null,
                            interactionUri = null,
                            accessTime = 0,
                        )
                    ),
            )

            TestApis.packages()
                .find(LegacySchemaHelperApp.PACKAGE_NAME)
                .uninstall(TestApis.users().instrumented())

            retryAssert {
                val accessHistories =
                    context.contentResolver.queryAllInteractionHistoryAfter(
                        context.getAppInteractionHistoryUri(),
                        timestamp = testStartTime,
                    )
                val helperHistories =
                    accessHistories?.filter { it.targetPackageName == CtsApp.PACKAGE_NAME }
                assertThat(helperHistories).isEmpty()
            }
        }
    }

    private fun ContentResolver.queryAllInteractionHistoryAfter(
        uri: Uri,
        timestamp: Long,
    ): List<InteractionHistory>? {
        return query(
                uri,
                null,
                "${AppInteractionContract.COLUMN_ACCESS_TIME} >= ?",
                arrayOf(timestamp.toString()),
                null,
                null,
            )
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(InteractionHistory.read(cursor))
                    }
                }
            }
    }

    /** The test interaction history that ignores [accessTime] when comparing. */
    internal class InteractionHistory(
        val agentPackageName: String,
        val targetPackageName: String,
        val interactionType: Int,
        val customInteractionType: String?,
        val interactionUri: String?,
        val accessTime: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as InteractionHistory

            // Ignoring accessTime and duration
            if (agentPackageName != other.agentPackageName) return false
            if (targetPackageName != other.targetPackageName) return false
            if (interactionType != other.interactionType) return false
            if (customInteractionType != other.customInteractionType) return false
            if (interactionUri != other.interactionUri) return false
            return true
        }

        override fun hashCode(): Int {
            // Ignoring accessTime and duration
            var result = agentPackageName.hashCode()
            result = 31 * result + targetPackageName.hashCode()
            result = 31 * result + interactionType
            result = 31 * result + (customInteractionType?.hashCode() ?: 0)
            result = 31 * result + (interactionUri?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "InteractionHistory(" +
                "agentPackageName=$agentPackageName, " +
                "targetPackageName=$targetPackageName, " +
                "interactionType=$interactionType, " +
                "customInteractionType=$customInteractionType, " +
                "interactionUri=$interactionUri, " +
                "accessTime=$accessTime)"
        }

        companion object {
            fun read(cursor: Cursor): InteractionHistory {
                return InteractionHistory(
                    agentPackageName =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                            )
                        ),
                    targetPackageName =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                            )
                        ),
                    interactionType =
                        cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                AppInteractionContract.COLUMN_INTERACTION_TYPE
                            )
                        ),
                    customInteractionType =
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow(
                                AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE
                            )
                        ),
                    interactionUri =
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow(
                                AppInteractionContract.COLUMN_INTERACTION_URI
                            )
                        ),
                    accessTime =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_ACCESS_TIME)
                        ),
                )
            }
        }
    }

    private fun Context.getAppInteractionHistoryUri(): Uri {
        return AppInteractionContract.getInteractionHistoryUriAsUser(user)
    }

    private suspend fun executeAll(
        targetContext: Context,
        requests: List<ExecuteAppFunctionRequest>,
    ) {
        val manager = targetContext.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)

        for (request in requests) {
            manager.executeAppFunction(request)
        }
    }

    private suspend fun assertMetadataIndexed(targetContext: Context) {
        retryAssert {
            runWithShellPermission(EXECUTE_APP_FUNCTION_PERMISSION) {
                assertThat(getAllStaticMetadataPackages(targetContext))
                    .containsAtLeast(CtsApp.PACKAGE_NAME, LegacySchemaHelperApp.PACKAGE_NAME)
                assertThat(getAllRuntimeMetadataPackages(targetContext))
                    .containsAtLeast(CtsApp.PACKAGE_NAME, LegacySchemaHelperApp.PACKAGE_NAME)
            }
        }
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        TestApis.packages().find(packageName).installExisting(user)
    }

    private fun installApkAsUser(user: UserReference, apkPath: String) {
        TestApis.packages().install(user, File(apkPath))
    }

    private suspend fun assertInteractionHistoryContainsExactly(
        after: Long,
        currentContext: Context,
        targetContext: Context,
        expected: Array<InteractionHistory>,
    ) {
        retryAssert {
            val accessHistories =
                currentContext.contentResolver.queryAllInteractionHistoryAfter(
                    targetContext.getAppInteractionHistoryUri(),
                    timestamp = after,
                )
            assertThat(accessHistories).isNotNull()
            assertThat(accessHistories).containsExactly(*expected)
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val READ_APP_INTERACTION_PERMISSION = Manifest.permission.READ_APP_INTERACTION
        const val EXECUTE_APP_FUNCTION_PERMISSION = Manifest.permission.EXECUTE_APP_FUNCTIONS
        const val INTERACT_ACROSS_USERS_FULL_PERMISSION =
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
    }
}
