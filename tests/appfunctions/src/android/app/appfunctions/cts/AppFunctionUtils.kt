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
package android.app.appfunctions.cts

import android.Manifest
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionManager.EnabledState
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionRegistrationTest.Companion.EXECUTE_APP_FUNCTIONS_PERMISSION
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appsearch.GenericDocument
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object AppFunctionUtils {

    suspend fun assertFunctionState(
        packageName: String,
        functionId: String,
        manager: AppFunctionManager,
        isEnabled: Boolean,
    ) {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val result = isAppFunctionEnabled(manager, packageName, functionId)

            assertThat(result.exceptionOrNull()).isNull()
            if (isEnabled) {
                assertThat(result.getOrThrow()).isTrue()
            } else {
                assertThat(result.getOrThrow()).isFalse()
            }
        }
    }

    /** Checks if target AppFunction is enable or not. */
    suspend fun isAppFunctionEnabled(
        manager: AppFunctionManager,
        packageName: String,
        functionIdentifier: String,
    ): Result<Boolean> {
        return suspendCancellableCoroutine { cont ->
            manager.isAppFunctionEnabled(
                functionIdentifier,
                packageName,
                Runnable::run,
                object : OutcomeReceiver<Boolean, Exception> {
                    override fun onResult(isEnabled: Boolean) {
                        cont.resume(Result.success(isEnabled))
                    }

                    override fun onError(error: Exception) {
                        cont.resume(Result.failure(error))
                    }
                },
            )
        }
    }

    /** Executes an app function and waits for the response. */
    suspend fun executeAppFunctionAndWait(
        manager: AppFunctionManager,
        request: ExecuteAppFunctionRequest,
    ): Result<ExecuteAppFunctionResponse> {
        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            manager.executeAppFunction(
                request,
                Runnable::run,
                cancellationSignal,
                object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
                    override fun onResult(result: ExecuteAppFunctionResponse) {
                        continuation.resume(Result.success(result))
                    }

                    override fun onError(e: AppFunctionException) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
        }
    }

    /** Sets the enabled state of an app function. */
    suspend fun setAppFunctionEnabled(
        manager: AppFunctionManager,
        functionIdentifier: String,
        @EnabledState state: Int,
    ): Unit = suspendCancellableCoroutine { continuation ->
        manager.setAppFunctionEnabled(
            functionIdentifier,
            state,
            Runnable::run,
            object : OutcomeReceiver<Void, Exception> {
                override fun onResult(result: Void?) {
                    continuation.resume(Unit)
                }

                override fun onError(error: Exception) {
                    continuation.resumeWithException(error)
                }
            },
        )
    }

    /** Sets [functionId] from [targetPackage] to [state]. */
    fun setAppFunctionEnabledRemote(
        targetPackage: String,
        functionId: String,
        @EnabledState state: Int,
    ) {
        val enableStateString =
            when (state) {
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT -> "default"
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED -> "enable"
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED -> "disable"
                else -> throw IllegalArgumentException("Unknown state of $state")
            }

        assertThat(
                ShellCommand.builder("cmd app_function set-enabled")
                    .addOption("--package", targetPackage)
                    .addOption("--function", functionId)
                    .addOption("--state", enableStateString)
                    .execute()
            )
            .isEqualTo("App function enabled state updated successfully.\n")
    }

    /** Install package as the context's user. */
    suspend fun installPackage(
        apkPath: String,
        packageName: String,
        userContext: Context,
        checkIndexation: Boolean,
    ) {
        assertThat(
                SystemUtil.runShellCommand(
                    "pm install -r -t -g --user ${userContext.userId} $apkPath"
                )
            )
            .isEqualTo("Success\n")

        if (checkIndexation) {
            retryAssert {
                assertThat(getAllStaticMetadataPackages(userContext)).contains(packageName)
            }
            runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
                retryAssert { assertThat(getAllRuntimeMetadataPackages()).contains(packageName) }
            }
        }
    }

    /** Install an existing package to the user. */
    suspend fun installExistingPackageAsUser(
        packageName: String,
        user: UserReference,
        context: Context? = null,
        checkIndexation: Boolean = false,
    ) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")

        if (checkIndexation) {
            retryAssert {
                runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
                    assertThat(
                            getAllStaticMetadataPackages(
                                context?.createContextAsUser(user.userHandle(), 0)
                            )
                        )
                        .contains(packageName)
                    assertThat(
                            getAllRuntimeMetadataPackages(
                                context?.createContextAsUser(user.userHandle(), 0)
                            )
                        )
                        .contains(packageName)
                }
            }
        }
    }

    suspend fun uninstallPackage(
        packageName: String,
        userContext: Context,
        checkIndexation: Boolean = false,
    ) {
        SystemUtil.runShellCommand("pm uninstall --user ${userContext.userId} $packageName")

        if (checkIndexation) {
            // Blocked until the AppFunctions are removed
            retryAssert {
                assertThat(getAllStaticMetadataPackages(userContext)).doesNotContain(packageName)
            }
            runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
                retryAssert {
                    assertThat(getAllRuntimeMetadataPackages()).doesNotContain(packageName)
                }
            }
        }
    }

    fun uninstallPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        SystemUtil.runShellCommand("pm uninstall --user $userId $packageName")
    }

    /** Gets all the static metadata packages. */
    fun getAllStaticMetadataPackages(context: Context? = null) =
        searchStaticMetadata(context).map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    /** Gets all the runtime metadata packages. */
    fun getAllRuntimeMetadataPackages(context: Context? = null) =
        searchRuntimeMetadata(context).map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    /** Enable allowlist. */
    fun enableAllowlist() {
        assertThat(ShellCommand.builder("cmd app_function enable-allowlist").execute())
            .isEqualTo("Enable allowlist\n")
    }

    /** Disable allowlist. */
    fun disableAllowlist() {
        assertThat(ShellCommand.builder("cmd app_function disable-allowlist").execute())
            .isEqualTo("Disable allowlist\n")
    }

    /** Sets interaction allowlist. */
    fun setInteractionAllowlist(agentPackageName: String, appPackageNames: List<String>) {
        assertThat(
                ShellCommand.builder("cmd app_function set-test-allowlist-entry")
                    .addOption("--agent-package", agentPackageName)
                    .addOption("--app-packages", appPackageNames.joinToString(separator = ","))
                    .execute()
            )
            .isEqualTo("Set test allowlist entry\n")
    }

    /** Clear interaction allowlist. */
    fun clearInteractionAllowlist() {
        ShellCommand.builder("cmd app_function clear-test-allowlist").execute()
    }

    /**
     * Runs [runnable] with interaction between [agentPackageName] and [appPackageNames]
     * allowlisted.
     */
    suspend fun runWithInteractionAllowlisted(
        agentPackageName: String,
        appPackageNames: List<String>,
        runnable: suspend () -> Unit,
    ) {
        setInteractionAllowlist(agentPackageName, appPackageNames)
        try {
            runnable.invoke()
        } finally {
            clearInteractionAllowlist()
        }
    }

    private fun searchStaticMetadata(context: Context? = null): List<GenericDocument> {
        val globalSearchSession = getGlobalSearchSession(context)

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                "",
                SearchSpec.Builder()
                    .addFilterNamespaces(APP_FUNCTION_STATIC_NAMESPACE)
                    .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                    .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults)
    }

    private fun searchRuntimeMetadata(context: Context? = null): List<GenericDocument> {
        val globalSearchSession = getGlobalSearchSession(context)

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                "",
                SearchSpec.Builder()
                    .addFilterNamespaces(AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE)
                    .addFilterSchemas(AppFunctionRuntimeMetadata.RUNTIME_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults)
    }

    private fun getGlobalSearchSession(context: Context? = null): GlobalSearchSessionShim {
        return if (context == null) {
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()
        } else {
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(context).get()
        }
    }

    private const val PROPERTY_PACKAGE_NAME = "packageName"
    private const val APP_FUNCTION_INDEXER_PACKAGE = "android"
}
