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
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForOperationCancellation
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.content.Context
import android.os.CancellationSignal
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
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
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
    fun setup() = doBlocking {
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
    fun resetEnabledStatus() = doBlocking {
        setAppFunctionEnabled(mManager, "add", AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
        setAppFunctionEnabled(
            mManager,
            "add_disabledByDefault",
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun checkManagerNotNull() {
        assertThat(mManager).isNotNull()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_failed_noSuchMethod() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noSuchMethod").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_INVALID_ARGUMENT)
        assertThat(response.errorMessage).isEqualTo("Function does not exist")
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_platformAppFunctionService_success() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_otherNonExistingTargetPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("other.package", "add").build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_otherExistingTargetPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "someMethod").build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_throwsException() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "throwException").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_onRemoteProcessKilled() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "kill").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode)
            .isEqualTo(ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR)
        // The process that the service was just crashed. Validate the service is not created again.
        TestAppFunctionServiceLifecycleReceiver.reset()
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_success_async() = doBlocking {
        val parameters =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "addAsync")
                .setParameters(parameters)
                .build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_emptyPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("", "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    fun executeAppFunction_runInManagedProfile_fail() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledByDefault_fail() = doBlocking {
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add_disabledByDefault").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledInRuntime_fail() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").build()
        setAppFunctionEnabled(mManager, "add", AppFunctionManager.APP_FUNCTION_STATE_DISABLED)

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_hasManagedProfileRunInPersonalProfile_success() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_deviceOwner_fail() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_largeTransactionSuccess() = doBlocking {
        val largeByteArray = ByteArray(1024 * 1024 + 100)
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .setPropertyBytes("unused", largeByteArray)
                .build()

        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_restrictCallersWithExecuteAppFunctionsFalse_success() =
        doBlocking {
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

                val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_functionMetadataNotFound_failsWithInvalidArgument() =
        doBlocking {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val request =
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "random_function").build()

                val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionTrustedPermission_restrictCallersWithExecuteAppFunctionsTrue_success() =
        doBlocking {
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

                val response = executeAppFunctionAndWait(mManager, request)

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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_restrictCallersWithExecuteAppFunctionsTrue_resultDenied() =
        doBlocking {
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

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_DENIED)
                assertThat(response.errorMessage)
                    .endsWith("does not have permission to execute the appfunction")
                assertServiceWasNotCreated()
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun isAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        assertThat(isAppFunctionEnabled(CURRENT_PKG, "add")).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionIdentifier = "add_disabledByDefault"))
            .isFalse()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionNotExist() = doBlocking {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(CURRENT_PKG, functionIdentifier = "notExist")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_noPermission() = doBlocking {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add_disabledByDefault")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_hasExecuteAppFunctionPermission() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            assertThat(isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add")).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_hasExecuteAppFunctionTrustedPermission() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_TRUSTED_PERMISSION) {
            assertThat(isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add")).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        val functionUnderTest = "add"
        // Check if the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Disable the function
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Reset the enabled bit
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        val functionUnderTest = "add_disabledByDefault"
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Enable the function
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Reset the enabled bit
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionNotExist() = doBlocking {
        val functionUnderTest = "notExist"

        assertFailsWith<IllegalArgumentException>("does not exist") {
            setAppFunctionEnabled(
                mManager,
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
        }
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
    }
}

private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
