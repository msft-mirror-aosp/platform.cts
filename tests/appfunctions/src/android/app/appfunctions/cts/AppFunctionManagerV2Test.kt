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
import android.app.AppInteractionAttribution
import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_DISABLED
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.LegacySchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.TestAllowlistPackage
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunction
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.cts.AppFunctionUtils.runWithInteractionAllowlisted
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabledRemote
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.assertReadAccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertReadInaccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertWriteAccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertWriteInaccessible
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForOperationCancellation
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.policies.AppFunctionsPolicy
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
class AppFunctionManagerV2Test {
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

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private lateinit var mManager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        AppFunctionUtils.enableAllowlist()

        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        mManager = manager
        retryAssert {
            // Doing containsAtLeast instead of containsExactly here in case there app preloaded
            // apps having app functions.
            assertThat(getAllStaticMetadataPackages())
                .containsAtLeast(CtsApp.PACKAGE_NAME, LegacySchemaHelperApp.PACKAGE_NAME)
            // required permission because runtime metadata is only visible to owner package
            runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
                assertThat(getAllRuntimeMetadataPackages())
                    .containsAtLeast(CtsApp.PACKAGE_NAME, LegacySchemaHelperApp.PACKAGE_NAME)
            }
        }
    }

    @After fun tearDown() = doBlocking { AppFunctionUtils.disableAllowlist() }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun checkManagerNotNull() {
        assertThat(mManager).isNotNull()
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_failed_uncaughtClientException() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.UNCAUGHT_CLIENT_EXCEPTION
                            .functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
            assertThat(response.appFunctionException().errorMessage)
                .isEqualTo("Function does not exist")
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_onlyInvokeCallbackOnce() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_INVOKE_CALLBACK_TWICE
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()
            val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()

            mManager.executeAppFunction(request, context.mainExecutor, CancellationSignal()) {
                e: ExecuteAppFunctionResponse ->
                blockingQueue.add(e)
            }

            val response = requireNotNull(blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
            assertThat(
                    response.resultDocument.getPropertyLong(
                        ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                    )
                )
                .isEqualTo(3)

            // Each callback can only be invoked once.
            assertThat(blockingQueue.poll(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isNull()
            assertServiceDestroyed()
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasAdditionalUser
    @EnsureHasPermission(
        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
    )
    fun executeAppFunction_crossUser_fail_nonParam() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val secondaryUser = sDeviceState.additionalUser()
            assumeTrue(
                "Test requires an additional user different from the primary user.",
                secondaryUser != TestApis.users().instrumented(),
            )
            installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
            installExistingPackageAsUser(LegacySchemaHelperApp.PACKAGE_NAME, secondaryUser)
            retryAssert {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(LegacySchemaHelperApp.PACKAGE_NAME)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(LegacySchemaHelperApp.PACKAGE_NAME)
            }
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            mManager =
                context
                    .createContextAsUser(secondaryUser.userHandle(), 0)
                    .getSystemService(AppFunctionManager::class.java)
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DENIED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherNonExistingTargetPackage_withoutPermission() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("other.package", "add").build()

        val response = mManager.executeAppFunction(request)

        assertThat(response.isSuccess).isFalse()
        // Apps without the permission can only invoke functions from themselves.
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DENIED)
        assertThat(response.appFunctionException().errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherNonExistingTargetPackage_withPermission() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(TestAllowlistPackage("other.package")),
        ) {
            val request = ExecuteAppFunctionRequest.Builder("other.package", "add").build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
            assertServiceWasNotCreated()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherExistingTargetPackage_withoutPermissionAndAllowlist() = doBlocking {
        val request =
            ExecuteAppFunctionRequest.Builder(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.NO_OP.functionIdentifier,
                )
                .build()

        val response = mManager.executeAppFunction(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DENIED)
        // The error message from this and executeAppFunction_otherNonExistingOtherPackage must
        // be kept in sync. This verifies that a caller cannot tell whether a package is
        // installed or not by comparing the error messages.
        assertThat(response.appFunctionException().errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherExistingTargetPackage_withPermissionButWithoutAllowlist() =
        doBlocking {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.NO_OP.functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DENIED)
            // The error message from this and executeAppFunction_otherNonExistingOtherPackage must
            // be kept in sync. This verifies that a caller cannot tell whether a package is
            // installed or not by comparing the error messages.
            assertThat(response.appFunctionException().errorMessage)
                .endsWith("does not have permission to execute the appfunction")
            assertServiceWasNotCreated()
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherExistingTargetPackage_withoutPermissionButWithAllowlist() =
        doBlocking {
            runWithInteractionAllowlisted(
                agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
                appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
            ) {
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            LegacySchemaHelperApp.PACKAGE_NAME,
                            LegacySchemaHelperApp.FunctionNames.NO_OP.functionIdentifier,
                        )
                        .build()

                val response = mManager.executeAppFunction(request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_DENIED)
                // The error message from this and executeAppFunction_otherNonExistingOtherPackage
                // must
                // be kept in sync. This verifies that a caller cannot tell whether a package is
                // installed or not by comparing the error messages.
                assertThat(response.appFunctionException().errorMessage)
                    .endsWith("does not have permission to execute the appfunction")
                assertServiceWasNotCreated()
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_otherExistingTargetPackage_withPermissionAndAllowlist() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.NO_OP.functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    fun executeAppFunction_otherExistingTargetPackage_withSystemPermissionAndAllowlist() =
        doBlocking {
            runWithInteractionAllowlisted(
                agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
                appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
            ) {
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            LegacySchemaHelperApp.PACKAGE_NAME,
                            LegacySchemaHelperApp.FunctionNames.NO_OP.functionIdentifier,
                        )
                        .build()

                val response = mManager.executeAppFunction(request)

                assertThat(response.exceptionOrNull()).isNull()
                assertThat(response.isSuccess).isTrue()
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_throwsException() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.THROW_EXCEPTION.functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_onRemoteProcessKilled() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.KILL.functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
            // The process that the service was just crashed. Validate the service is not created
            // again.
            TestAppFunctionServiceLifecycleReceiver.reset()
            assertServiceWasNotCreated()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_success_async() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ASYNC.functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                )
                .isEqualTo(3)
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_emptyPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("", "noOp").build()

        val response = mManager.executeAppFunction(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @RequireRunOnWorkProfile
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_runInWorkProfile_fail() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DENIED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @RequireRunOnPrivateProfile
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_runInPrivateProfile_fail() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DENIED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasWorkProfile
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
    )
    fun executeAppFunction_crossUser_targetWorkProfile_fail() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val workProfileUser = sDeviceState.workProfile()
            assumeTrue(
                "Work profile user must be different from the primary user.",
                workProfileUser != TestApis.users().instrumented(),
            )
            installExistingPackageAsUser(CtsApp.PACKAGE_NAME, workProfileUser)
            installExistingPackageAsUser(LegacySchemaHelperApp.PACKAGE_NAME, workProfileUser)
            retryAssert {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(workProfileUser.userHandle(), 0)
                        )
                    )
                    .contains(LegacySchemaHelperApp.PACKAGE_NAME)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(workProfileUser.userHandle(), 0)
                        )
                    )
                    .contains(LegacySchemaHelperApp.PACKAGE_NAME)
            }
            mManager =
                context
                    .createContextAsUser(workProfileUser.userHandle(), 0)
                    .getSystemService(AppFunctionManager::class.java)
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DENIED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_disabledByDefault_fail() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DISABLED)
            assertServiceWasNotCreated()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_disabledInRuntime_fail() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            try {
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            LegacySchemaHelperApp.PACKAGE_NAME,
                            LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                                .functionIdentifier,
                        )
                        .build()
                setAppFunctionEnabledRemote(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT.functionIdentifier,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
                )

                val response = mManager.executeAppFunction(request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_DISABLED)
                assertServiceWasNotCreated()
            } finally {
                setAppFunctionEnabledRemote(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT.functionIdentifier,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @PolicyAppliesTest(policy = [AppFunctionsPolicy::class])
    fun executeAppFunction_deviceOwnerRestricted_fail() = doBlocking {
        val remoteDpm = sDeviceState.dpc().devicePolicyManager()
        val originalPolicy = remoteDpm.getAppFunctionsPolicy()
        try {
            remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED)
            assertThat(remoteDpm.getAppFunctionsPolicy()).isEqualTo(APP_FUNCTIONS_DISABLED)
            runWithInteractionAllowlisted(
                agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
                appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
            ) {
                val parameters: GenericDocument =
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyLong("a", 1)
                        .setPropertyLong("b", 2)
                        .build()
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            LegacySchemaHelperApp.PACKAGE_NAME,
                            LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                                .functionIdentifier,
                        )
                        .setParameters(parameters)
                        .build()

                val response = mManager.executeAppFunction(request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED)
                assertServiceWasNotCreated()
            }
        } finally {
            remoteDpm.setAppFunctionsPolicy(originalPolicy)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_deviceOwnerUnrestricted_success() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                )
                .isEqualTo(3)
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_largeTransactionSuccess() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val largeByteArray = ByteArray(1024 * 1024 + 100)
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .setPropertyBytes("unused", largeByteArray)
                    .build()

            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT
                            .functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.exceptionOrNull()).isNull()
            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                )
                .isEqualTo(3)
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_withExecuteAppFunctionPermission_functionMetadataNotFound_failsWithInvalidArgument() =
        doBlocking {
            runWithInteractionAllowlisted(
                agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
                appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
            ) {
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            LegacySchemaHelperApp.PACKAGE_NAME,
                            "random_function",
                        )
                        .build()

                val response = mManager.executeAppFunction(request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
                assertThat(response.appFunctionException().errorMessage)
                    .contains("App function not found.")
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_cancellationSignal_cancelled_unbind() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.LONG_RUNNING_FUNCTION.functionIdentifier,
                )
                .setParameters(parameters)
                .build()
        val cancellationSignal = CancellationSignal()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor, cancellationSignal) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }

        cancellationSignal.cancel()

        assertCancelListenerTriggered()
        assertServiceDestroyed()
        assertThat(blockingQueue).isEmpty()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_cancellationSignal_cancellationTimedOut_unbind() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.NOT_INVOKE_CALLBACK.functionIdentifier,
                )
                .setParameters(parameters)
                .build()
        val cancellationSignal = CancellationSignal()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor, cancellationSignal) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }

        cancellationSignal.cancel()

        assertCancelListenerTriggered()
        assertServiceDestroyed()
        assertThat(blockingQueue).isEmpty()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_largeBytes_success() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val fiveMb = 1024 * 1024 * 5
            val largeByteArray = ByteArray(fiveMb)
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyBytes("bytes", largeByteArray)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.ECHO_BYTES.functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(response.getOrNull()!!.resultDocument.getPropertyBytes("bytes"))
                .isEqualTo(largeByteArray)
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeSelfAppFunctionWithoutPermission_processStateIsNotBfgs() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.RUN_FOREVER.functionIdentifier,
                )
                .setParameters(parameters)
                .build()

        val cancellationSignal = CancellationSignal()
        try {
            mManager.executeAppFunction(request, Runnable::run, cancellationSignal) {}
            waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)
            assertProcessState(isBfgs = false)
        } finally {
            cancellationSignal.cancel()
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunctionWithPermission_processStateIsBfgs() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.RUN_FOREVER.functionIdentifier,
                )
                .setParameters(parameters)
                .build()

        val cancellationSignal = CancellationSignal()
        try {
            mManager.executeAppFunction(request, Runnable::run, cancellationSignal) {}
            waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)
            assertProcessState(isBfgs = true)
        } finally {
            cancellationSignal.cancel()
            assertServiceDestroyed()
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun onPackageDataCleared_enabledByDefault_disabledInRuntime_restoredToDefault() = doBlocking {
        val functionIdUnderTest = "add"
        ShellCommand.builder("cmd app_function set-enabled")
            .addOption("--package", LegacySchemaHelperApp.PACKAGE_NAME)
            .addOption("--function", functionIdUnderTest)
            .addOption("--state", "disable")
            .addOption("--user", TestApis.users().current().id())
            .execute()
        assertThat(isAppFunctionEnabled(LegacySchemaHelperApp.PACKAGE_NAME, functionIdUnderTest))
            .isFalse()

        ShellCommand.builder(
                "pm clear --user ${TestApis.users().current().id()}" +
                    " ${LegacySchemaHelperApp.PACKAGE_NAME}"
            )
            .execute()

        retryAssert {
            assertThat(
                    isAppFunctionEnabled(LegacySchemaHelperApp.PACKAGE_NAME, functionIdUnderTest)
                )
                .isTrue()
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun onPackageDataCleared_disabledByDefault_enabledInRuntime_restoredToDefault() = doBlocking {
        val functionIdUnderTest = "add_disabledByDefault"
        ShellCommand.builder("cmd app_function set-enabled")
            .addOption("--package", LegacySchemaHelperApp.PACKAGE_NAME)
            .addOption("--function", functionIdUnderTest)
            .addOption("--state", "enable")
            .addOption("--user", TestApis.users().current().id())
            .execute()
        assertThat(isAppFunctionEnabled(LegacySchemaHelperApp.PACKAGE_NAME, functionIdUnderTest))
            .isTrue()

        ShellCommand.builder(
                "pm clear --user ${TestApis.users().current().id()}" +
                    " ${LegacySchemaHelperApp.PACKAGE_NAME}"
            )
            .execute()

        retryAssert {
            assertThat(
                    isAppFunctionEnabled(LegacySchemaHelperApp.PACKAGE_NAME, functionIdUnderTest)
                )
                .isFalse()
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeAppFunction_withPermissionAndAccess_getUris() = doBlocking {
        runWithInteractionAllowlisted(
            agentPackage = CtsApp.TEST_ALLOWLIST_PACKAGE,
            appPackages = listOf(LegacySchemaHelperApp.TEST_ALLOWLIST_PACKAGE),
        ) {
            val readOnlyUri =
                Uri.parse(
                    "content://android.app.appfunctions.cts.helper.provider/read_only_test_file.txt"
                )
            val writeOnlyUri =
                Uri.parse(
                    "content://android.app.appfunctions.cts.helper.provider/write_only_test_file.txt"
                )
            val readWriteUri =
                Uri.parse(
                    "content://android.app.appfunctions.cts.helper.provider/read_write_test_file.txt"
                )
            val request =
                ExecuteAppFunctionRequest.Builder(
                        LegacySchemaHelperApp.PACKAGE_NAME,
                        LegacySchemaHelperApp.FunctionNames.GET_URIS.functionIdentifier,
                    )
                    .build()

            val response = mManager.executeAppFunction(request)

            assertThat(response.getOrThrow()).isNotNull()
            assertReadAccessible(contentResolver, readOnlyUri)
            assertReadAccessible(contentResolver, readWriteUri)
            assertReadInaccessible(contentResolver, writeOnlyUri)
            assertWriteAccessible(contentResolver, writeOnlyUri)
            assertWriteAccessible(contentResolver, readWriteUri)
            assertWriteInaccessible(contentResolver, readOnlyUri)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun isSelfAppFunctionEnabled_withoutPermission() = doBlocking {
        assertThat(
                isAppFunctionEnabled(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.ADD.functionIdentifier,
                )
            )
            .isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun isAppFunctionEnabled_unableToSeeOtherPackage_withoutPermission() {
        assertFailsWith<IllegalArgumentException>("function not found") {
            doBlocking {
                isAppFunctionEnabled(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT.functionIdentifier,
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun isAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        assertThat(
                isAppFunctionEnabled(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT.functionIdentifier,
                )
            )
            .isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun isAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        assertThat(
                isAppFunctionEnabled(
                    LegacySchemaHelperApp.PACKAGE_NAME,
                    LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                )
            )
            .isFalse()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun isAppFunctionEnabled_functionNotExist() {
        assertFailsWith<IllegalArgumentException>("function not found") {
            doBlocking { isAppFunctionEnabled(functionIdentifier = "notExist") }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun setAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        val functionUnderTest = "add"
        try {
            // Check if the function is enabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isTrue()
            // Disable the function
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            // Confirm that the function is disabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isFalse()
            // Reset the enabled bit
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            // Confirm that the function is now enabled (default)
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isTrue()

            // Manually set the enabled bit to true
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            // Confirm that the function is still enabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isTrue()
        } finally {
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun setAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        val functionUnderTest = "add_disabledByDefault"
        try {
            // Confirm that the function is disabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isFalse()
            // Enable the function
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            // Confirm that the function is enabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isTrue()
            // Reset the enabled bit
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            // Confirm that the function is now enabled (default)
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isFalse()

            // Manually set the enabled bit to true
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            // Confirm that the function is still enabled
            assertThat(isAppFunctionEnabled(CtsApp.PACKAGE_NAME, functionUnderTest)).isTrue()
        } finally {
            mManager.setAppFunctionEnabled(
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun setAppFunctionEnabled_functionNotExist() {
        val functionUnderTest = "notExist"

        assertFailsWith<IllegalArgumentException>("does not exist") {
            doBlocking {
                mManager.setAppFunctionEnabled(
                    functionUnderTest,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_INTERACTION_API)
    fun executeAppFunction_withAttribution_attributionNotPropagated() = doBlocking {
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    CtsApp.PACKAGE_NAME,
                    CtsApp.FunctionNames.CHECK_ATTRIBUTION.functionIdentifier,
                )
                .setAttribution(attribution)
                .build()

        val response = mManager.executeAppFunction(request)

        assertThat(response.exceptionOrNull()).isNull()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyBoolean(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isFalse()
        assertServiceDestroyed()
    }

    /** Runs a suspend block in a blocking manner */
    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun assertCancelListenerTriggered() {
        assertThat(waitForOperationCancellation(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
    }

    private suspend fun isAppFunctionEnabled(functionIdentifier: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            mManager.isAppFunctionEnabled(
                functionIdentifier,
                context.mainExecutor,
                continuation.asOutcomeReceiver(),
            )
        }

    private suspend fun isAppFunctionEnabled(
        targetPackage: String,
        functionIdentifier: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        mManager.isAppFunctionEnabled(
            functionIdentifier,
            targetPackage,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    /** Verifies that the service is unbound by asserting the service was destroyed. */
    private fun assertServiceDestroyed() {
        assertThat(waitForServiceOnDestroy(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
    }

    /** Verifies that the service has never been created. */
    private fun assertServiceWasNotCreated() {
        assertThat(waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isFalse()
    }

    /**
     * Asserts the state of the process running AppFunctionService is 'bfgs', i.e. bound foreground
     * service, or not.
     */
    private fun assertProcessState(isBfgs: Boolean) {
        val output = SystemUtil.runShellCommand("dumpsys activity lru")
        for (line in output.lines()) {
            if (
                line.contains("android.app.appfunctions.cts:appfunctions/u${UserHandle.myUserId()}")
            ) {
                if (isBfgs) {
                    assertThat(line).contains("BFGS")
                } else {
                    assertThat(line).doesNotContain("BFGS")
                }
                return
            }
        }
        fail(
            "Cannot find android.app.appfunctions.cts:appfunctions/u${UserHandle.myUserId()}" +
                " from dumpsys activity lru"
        )
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
        const val SHORT_TIMEOUT_SECOND: Long = 1
        const val LONG_TIMEOUT_SECOND: Long = 20
    }
}

private fun <T> Result<T>.appFunctionException(): AppFunctionException =
    exceptionOrNull() as AppFunctionException
