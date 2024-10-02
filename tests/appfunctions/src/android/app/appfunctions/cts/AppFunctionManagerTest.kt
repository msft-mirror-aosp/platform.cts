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
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionManager.EnabledState
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.SidecarUtil
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForOperationCancellation
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.nene.TestApis.permissions
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.google.android.appfunctions.sidecar.AppFunctionManager as SidecarAppFunctionManager
import com.google.android.appfunctions.sidecar.ExecuteAppFunctionRequest as SidecarExecuteAppFunctionRequest
import com.google.android.appfunctions.sidecar.ExecuteAppFunctionResponse as SidecarExecuteAppFunctionResponse
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionManagerTest {
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

    private lateinit var mManager: AppFunctionManager

    @Before
    fun setup() = runTest {
        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        mManager = manager
        retryAssert {
            // Doing containsAtLeast instead of containsExactly here in case there app preloaded
            // apps having app functions.
            assertThat(getAllStaticMetadataPackages())
                .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG, TEST_SIDECAR_HELPER_PKG)
            // required permission because runtime metadata is only visible to owner package
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                assertThat(getAllRuntimeMetadataPackages())
                    .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG, TEST_SIDECAR_HELPER_PKG)
            }
        }
    }

    @Before
    @After
    fun resetEnabledStatus() = runTest {
        setAppFunctionEnabled("add", AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
        setAppFunctionEnabled(
            "add_disabledByDefault",
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
    }

    @Test
    fun checkManagerNotNull() {
        assertThat(mManager).isNotNull()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @Throws(Exception::class)
    fun executeAppFunction_failed_noSuchMethod() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noSuchMethod").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_INVALID_ARGUMENT)
        assertThat(response.errorMessage).isEqualTo("Function does not exist")
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_onlyInvokeCallbackOnce() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add_invokeCallbackTwice")
                .setParameters(parameters)
                .build()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()

        mManager.executeAppFunction(request, context.mainExecutor) { e: ExecuteAppFunctionResponse
            ->
            blockingQueue.add(e)
        }

        val response = requireNotNull(blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
        assertThat(response.isSuccess).isTrue()
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

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_platformAppFunctionService_success() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response.resultDocument.getPropertyLong(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(
        apis = ["com.google.android.appfunctions.sidecar.AppFunctionManager#executeAppFunction"]
    )
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_sidecarManager_platformAppFunctionService_success() = runTest {
        suspendWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
            // Only run test if sidecar library is available.
            SidecarUtil.assumeSidecarAvailable()
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                SidecarExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response =
                suspendCancellableCoroutine<SidecarExecuteAppFunctionResponse> { continuation ->
                    SidecarAppFunctionManager(context)
                        .executeAppFunction(
                            request,
                            context.mainExecutor,
                            { response -> continuation.resume(response) },
                        )
                }

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response.resultDocument.getPropertyLong(
                        ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                    )
                )
                .isEqualTo(3)
            assertServiceDestroyed()
        }
    }

    @ApiTest(
        apis = ["com.google.android.appfunctions.sidecar.AppFunctionManager#executeAppFunction"]
    )
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_sidecarManager_sidecarAppFunctionService_success() = runTest {
        suspendWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
            // Only run test if sidecar library is available.
            SidecarUtil.assumeSidecarAvailable()
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                SidecarExecuteAppFunctionRequest.Builder(TEST_SIDECAR_HELPER_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response =
                suspendCancellableCoroutine<SidecarExecuteAppFunctionResponse> { continuation ->
                    SidecarAppFunctionManager(context)
                        .executeAppFunction(
                            request,
                            context.mainExecutor,
                            { response -> continuation.resume(response) },
                        )
                }

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response.resultDocument.getPropertyLong(
                        ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                    )
                )
                .isEqualTo(3)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_sidecarAppFunctionService_success() = runTest {
        suspendWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
            // Only run test if sidecar library is available.
            SidecarUtil.assumeSidecarAvailable()
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(TEST_SIDECAR_HELPER_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = executeAppFunctionAndWait(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response.resultDocument.getPropertyLong(
                        ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                    )
                )
                .isEqualTo(3)
        }
    }

    @ApiTest(
        apis = ["com.google.android.appfunctions.sidecar.AppFunctionManager#isAppFunctionEnabled"]
    )
    @Test
    fun isAppFunctionEnabled_sidecar() = runTest {
        SidecarUtil.assumeSidecarAvailable()

        assertThat(sidecarIsAppFunctionEnabled(CURRENT_PKG, "add")).isTrue()
    }

    @ApiTest(
        apis = ["com.google.android.appfunctions.sidecar.AppFunctionManager#setAppFUnctionEnabled"]
    )
    @Test
    fun setAppFunctionEnabled_sidecar() = runTest {
        SidecarUtil.assumeSidecarAvailable()

        val functionUnderTest = "add"
        assertThat(sidecarIsAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        sidecarSetAppFunctionEnabled(
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )

        assertThat(sidecarIsAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_otherNonExistingTargetPackage() {
        val request = ExecuteAppFunctionRequest.Builder("other.package", "add").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        // Apps without the permission can only invoke functions from themselves.
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DENIED)
        assertThat(response.errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_otherExistingTargetPackage() {
        val request = ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "someMethod").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DENIED)
        // The error message from this and executeAppFunction_otherNonExistingOtherPackage must be
        // kept in sync. This verifies that a caller cannot tell whether a package is installed or
        // not by comparing the error messages.
        assertThat(response.errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_throwsException() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "throwException").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_onRemoteProcessKilled() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "kill").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR)
        // The process that the service was just crashed. Validate the service is not created again.
        TestAppFunctionServiceLifecycleReceiver.reset()
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_success_async() {
        val parameters =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "addAsync")
                .setParameters(parameters)
                .build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response.resultDocument.getPropertyLong(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_emptyPackage() {
        val request = ExecuteAppFunctionRequest.Builder("", "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_INVALID_ARGUMENT)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @RequireRunOnWorkProfile
    @EnsureHasNoDeviceOwner
    @Postsubmit(reason = "new test")
    @Throws(Exception::class)
    fun executeAppFunction_runInManagedProfile_fail() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledByDefault_fail() {
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add_disabledByDefault").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledInRuntime_fail() = runTest {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").build()
        setAppFunctionEnabled("add", AppFunctionManager.APP_FUNCTION_STATE_DISABLED)

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_hasManagedProfileRunInPersonalProfile_success() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isTrue()
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_deviceOwner_fail() {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_largeTransactionSuccess() {
        val largeByteArray = ByteArray(1024 * 1024 + 100)
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .setPropertyBytes("unused", largeByteArray)
                .build()

        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response.resultDocument.getPropertyLong(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_restrictCallersWithExecuteAppFunctionsFalse_success() =
        runTest {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val parameters: GenericDocument =
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyLong("a", 1)
                        .setPropertyLong("b", 2)
                        .build()
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            TEST_HELPER_PKG,
                            "addWithRestrictCallersWithExecuteAppFunctionsFalse",
                        )
                        .setParameters(parameters)
                        .build()

                val response = executeAppFunctionAndWait(request)

                assertThat(response.errorMessage).isNull()
                assertThat(response.isSuccess).isTrue()
                assertThat(
                        response.resultDocument.getPropertyLong(
                            ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                        )
                    )
                    .isEqualTo(3)
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_functionMetadataNotFound_failsWithInvalidArgument() =
        runTest {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val request =
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "random_function").build()

                val response = executeAppFunctionAndWait(request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.resultCode)
                    .isEqualTo(ExecuteAppFunctionResponse.RESULT_INVALID_ARGUMENT)
                assertThat(response.errorMessage)
                    .contains(
                        "Document (android\$apps-db/app_functions," +
                            " android.app.appfunctions.cts.helper/random_function) not found"
                    )
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionTrustedPermission_restrictCallersWithExecuteAppFunctionsTrue_success() =
        runTest {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
                val parameters: GenericDocument =
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyLong("a", 1)
                        .setPropertyLong("b", 2)
                        .build()
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            TEST_HELPER_PKG,
                            "addWithRestrictCallersWithExecuteAppFunctionsTrue",
                        )
                        .setParameters(parameters)
                        .build()

                val response = executeAppFunctionAndWait(request)

                assertThat(response.errorMessage).isNull()
                assertThat(response.isSuccess).isTrue()
                assertThat(
                        response.resultDocument.getPropertyLong(
                            ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                        )
                    )
                    .isEqualTo(3)
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_restrictCallersWithExecuteAppFunctionsTrue_resultDenied() =
        runTest {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val parameters: GenericDocument =
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyLong("a", 1)
                        .setPropertyLong("b", 2)
                        .build()
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            TEST_HELPER_PKG,
                            "addWithRestrictCallersWithExecuteAppFunctionsTrue",
                        )
                        .setParameters(parameters)
                        .build()

                val response = executeAppFunctionAndWait(request)

                assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DENIED)
                assertThat(response.errorMessage)
                    .endsWith("does not have permission to execute the appfunction")
                assertServiceWasNotCreated()
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_cancellationSignal_cancelled_unbind() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "longRunningFunction")
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

    @Throws(InterruptedException::class)
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_cancellationSignal_cancellationTimedOut_unbind() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "notInvokeCallback")
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

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    fun isAppFunctionEnabled_functionDefaultEnabled() = runTest {
        assertThat(isAppFunctionEnabled(CURRENT_PKG, "add")).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionDefaultDisabled() = runTest {
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionIdentifier = "add_disabledByDefault"))
            .isFalse()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionNotExist() = runTest {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(CURRENT_PKG, functionIdentifier = "notExist")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_noPermission() = runTest {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add_disabledByDefault")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_hasExecuteAppFunctionPermission() = runTest {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            assertThat(isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add")).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_hasExecuteAppFunctionTrustedPermission() = runTest {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
            assertThat(isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add")).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultEnabled() = runTest {
        val functionUnderTest = "add"
        // Check if the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Disable the function
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_DISABLED)
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Reset the enabled bit
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_ENABLED)
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultDisabled() = runTest {
        val functionUnderTest = "add_disabledByDefault"
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Enable the function
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_ENABLED)
        // Confirm that the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Reset the enabled bit
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_ENABLED)
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionNotExist() = runTest {
        val functionUnderTest = "notExist"

        assertFailsWith<IllegalArgumentException>("does not exist") {
            setAppFunctionEnabled(functionUnderTest, AppFunctionManager.APP_FUNCTION_STATE_DISABLED)
        }
    }

    private fun executeAppFunctionAndWait(
        request: ExecuteAppFunctionRequest
    ): ExecuteAppFunctionResponse {
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor, CancellationSignal()) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }
        return requireNotNull(blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
    }

    private fun assertCancelListenerTriggered() {
        assertThat(waitForOperationCancellation(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
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

    private suspend fun sidecarIsAppFunctionEnabled(
        targetPackage: String,
        functionIdentifier: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        SidecarAppFunctionManager(context)
            .isAppFunctionEnabled(
                functionIdentifier,
                targetPackage,
                context.mainExecutor,
                continuation.asOutcomeReceiver(),
            )
    }

    private suspend fun sidecarSetAppFunctionEnabled(
        functionIdentifier: String,
        @EnabledState state: Int,
    ): Unit = suspendCancellableCoroutine { continuation ->
        SidecarAppFunctionManager(context)
            .setAppFunctionEnabled(
                functionIdentifier,
                state,
                context.mainExecutor,
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

    private suspend fun setAppFunctionEnabled(
        functionIdentifier: String,
        @EnabledState state: Int,
    ): Unit = suspendCancellableCoroutine { continuation ->
        mManager.setAppFunctionEnabled(
            functionIdentifier,
            state,
            context.mainExecutor,
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

    /** Verifies that the service is unbound by asserting the service was destroyed. */
    @Throws(InterruptedException::class)
    private fun assertServiceDestroyed() {
        assertThat(waitForServiceOnDestroy(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
    }

    /** Verifies that the service has never been created. */
    @Throws(InterruptedException::class)
    private fun assertServiceWasNotCreated() {
        assertThat(waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isFalse()
    }

    private fun getAllStaticMetadataPackages() =
        searchStaticMetadata().map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    private fun getAllRuntimeMetadataPackages() =
        searchRuntimeMetadata().map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    private fun searchStaticMetadata(): List<GenericDocument> {
        val globalSearchSession: GlobalSearchSessionShim =
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()

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

    private fun searchRuntimeMetadata(): List<GenericDocument> {
        val globalSearchSession: GlobalSearchSessionShim =
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()

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

    fun interface ThrowRunnable {
        @Throws(Throwable::class) suspend fun run()
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_SIDECAR_HELPER_PKG: String = "android.app.appfunctions.cts.helper.sidecar"
        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"
        const val SHORT_TIMEOUT_SECOND: Long = 1
        const val LONG_TIMEOUT_SECOND: Long = 5
        const val EXECUTE_APP_FUNCTIONS_PERMISSION = Manifest.permission.EXECUTE_APP_FUNCTIONS
        const val EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION =
            Manifest.permission.EXECUTE_APP_FUNCTIONS_TRUSTED
        const val RETRY_CHECK_INTERVAL_MILLIS: Long = 500
        const val RETRY_MAX_INTERVALS: Long = 10
        const val PROPERTY_PACKAGE_NAME = "packageName"
        const val APP_FUNCTION_INDEXER_PACKAGE = "android"

        suspend fun runWithShellPermission(vararg permissions: String, block: suspend () -> Unit) {
            permissions().withPermission(*permissions).use { block() }
        }

        suspend fun suspendWithShellPermission(
            vararg permissions: String,
            block: suspend () -> Unit,
        ) {
            permissions().withPermission(*permissions).use { block() }
        }

        /** Retries an assertion with a delay between attempts. */
        @Throws(Throwable::class)
        suspend fun retryAssert(runnable: ThrowRunnable) {
            var lastError: Throwable? = null

            for (attempt in 0 until RETRY_MAX_INTERVALS) {
                try {
                    runnable.run()
                    return
                } catch (e: Throwable) {
                    lastError = e
                    delay(RETRY_CHECK_INTERVAL_MILLIS)
                }
            }
            throw lastError!!
        }
    }
}
