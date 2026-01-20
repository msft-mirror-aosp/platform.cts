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
import android.app.appfunctions.AppFunction
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.isAppFunctionEnabled
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.testutils.ConcatStrings
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.assertReadAccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertReadInaccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertWriteAccessible
import android.app.appfunctions.testutils.CtsTestUtil.assertWriteInaccessible
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.DisabledByDefault
import android.app.appfunctions.testutils.DisabledByDefault.Companion.DISABLED_BY_DEFAULT_FUNCTION_ID
import android.app.appfunctions.testutils.FunctionType
import android.app.appfunctions.testutils.GetUris.Companion.GET_URIS_FUNCTION_ID
import android.app.appfunctions.testutils.ITestAppFunctionRegistrationService
import android.app.appfunctions.testutils.LongRunning
import android.app.appfunctions.testutils.LongRunning.Companion.LONG_RUNNING_FUNCTION_ID
import android.app.appfunctions.testutils.OutputInvalidArgumentException
import android.app.appfunctions.testutils.OutputInvalidArgumentException.Companion.OUTPUT_INVALID_ARGUMENT_EXCEPTION_FUNCTION_ID
import android.app.appfunctions.testutils.StopProcess.Companion.STOP_PROCESS_FUNCTION_ID
import android.app.appfunctions.testutils.TestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForCancelListenerSet
import android.app.appfunctions.testutils.ThrowInvalidArgumentException
import android.app.appfunctions.testutils.ThrowInvalidArgumentException.Companion.THROW_INVALID_ARGUMENT_FUNCTION_ID
import android.app.appfunctions.testutils.ThrowUnknownException
import android.app.appfunctions.testutils.ThrowUnknownException.Companion.THROW_UNKNOWN_EXCEPTION_FUNCTION_ID
import android.app.appsearch.GenericDocument
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.IBinder
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.ServiceTestRule
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.lang.IllegalArgumentException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionRegistrationTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val serviceTestRule = ServiceTestRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private lateinit var manager: AppFunctionManager

    private val registrations = mutableListOf<AppFunctionRegistration>()

    private val executionExecutor: Executor = Executors.newSingleThreadExecutor()

    private val testRegistrationExecutor: Executor = Executors.newSingleThreadExecutor()

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@AppFunctionRegistrationTest.manager = manager
        uninstallPackage(TEST_APP_A_PKG)

        setAppFunctionEnabled(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            CONCAT_STRINGS_FUNCTION_ID,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
    }

    @After
    fun tearDown() {
        for (registration in registrations) {
            registration.unregister()
        }
        registrations.clear()

        TestAppFunctionServiceLifecycleReceiver.reset()
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun register_once_success() {
        registerConcatStringsAppFunction()
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun register_disabledByDefaultFunction_success() {
        registerAppFunction(DISABLED_BY_DEFAULT_FUNCTION_ID, DisabledByDefault())
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_theSameIdTwice_fail() {
        registerConcatStringsAppFunction()
        assertFailsWith<IllegalStateException>() { registerConcatStringsAppFunction() }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_theSameIdTwiceDifferentProcesses_fail() {
        val service = bindToRegistrationService(CURRENT_PKG)

        service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())

        assertFailsWith<IllegalStateException>() { registerConcatStringsAppFunction() }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_serviceLevelFunction_reportsInvalidArgumentError() {
        assertFailsWith<IllegalArgumentException>() {
            registerAppFunction(CtsApp.FunctionNames.ADD.functionId, ConcatStrings())
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_unknownFunction_reportsInvalidArgumentError() {
        assertFailsWith<IllegalArgumentException>() {
            registerAppFunction("unknownFunctionId", ConcatStrings())
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_rightAfterInstalledPackage_success() = doBlocking {
        try {
            installPackage(
                TEST_APP_A_PATH_DYNAMIC_ONLY_FUNCTIONS,
                TEST_APP_A_PKG,
                checkIndexation = true,
            )
            val service = bindToRegistrationService(TEST_APP_A_PKG)
            retryAssert {
                assertThat(service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString()))
                    .isEqualTo(true)
            }
        } finally {
            uninstallPackage(TEST_APP_A_PKG)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_rightAfterIndexedFunctionsDuringUpdate_success() = doBlocking {
        try {
            installPackage(TEST_APP_A_PATH_NO_FUNCTIONS, TEST_APP_A_PKG, checkIndexation = false)
            installPackage(
                TEST_APP_A_PATH_DYNAMIC_ONLY_FUNCTIONS,
                TEST_APP_A_PKG,
                checkIndexation = true,
            )
            val service = bindToRegistrationService(TEST_APP_A_PKG)
            retryAssert {
                assertThat(service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString()))
                    .isEqualTo(true)
            }
        } finally {
            uninstallPackage(TEST_APP_A_PKG)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_rightAfterIndexedFunctionsDuringUpdate_fromStaticFunctions_success() = doBlocking {
        try {
            installPackage(
                TEST_APP_A_PATH_STATIC_ONLY_FUNCTIONS,
                TEST_APP_A_PKG,
                checkIndexation = true,
            )
            installPackage(
                TEST_APP_A_PATH_DYNAMIC_ONLY_FUNCTIONS,
                TEST_APP_A_PKG,
                checkIndexation = true,
            )
            val service = bindToRegistrationService(TEST_APP_A_PKG)
            retryAssert {
                assertThat(service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString()))
                    .isEqualTo(true)
            }
        } finally {
            uninstallPackage(TEST_APP_A_PKG)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationWithSameId() {
        val staleRegistration = registerConcatStringsAppFunction()
        staleRegistration.unregister()
        val activeRegistration = registerConcatStringsAppFunction()

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The second registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationWithSameIdAndFunction() {
        val function = ConcatStrings()
        val staleRegistration =
            registerAppFunction(function = function, functionId = CONCAT_STRINGS_FUNCTION_ID)
        staleRegistration.unregister()
        val activeRegistration =
            registerAppFunction(function = function, functionId = CONCAT_STRINGS_FUNCTION_ID)

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The second registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationInTheOtherProcess() {
        val staleRegistration = registerConcatStringsAppFunction()
        staleRegistration.unregister()
        val service = bindToRegistrationService(CURRENT_PKG)
        service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The service registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_unregisteredFunction_returnsError() = doBlocking {
        val request = createConcatStringsRequest(CURRENT_PKG)

        val response = executeAppFunctionAndWait(manager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_functionFromTheSamePackageDifferentProcess_success() = doBlocking {
        val service = bindToRegistrationService(CURRENT_PKG)
        service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())
        val request = createConcatStringsRequest(targetPackage = CURRENT_PKG)

        val response = executeAppFunctionAndWait(manager, request)

        assertConcatStringsResponseCorrect(response)
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_functionFromDifferentPackage_success() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request =
                createConcatStringsRequest(targetPackage = DynamicSchemaHelperApp.PACKAGE_NAME)

            val response = executeAppFunctionAndWait(manager, request)
            assertConcatStringsResponseCorrect(response)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_outputsInvalidArgumentException_propagatesToCaller() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.OUTPUT_INVALID_ARGUMENT_EXCEPTION.toString())
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        OUTPUT_INVALID_ARGUMENT_EXCEPTION_FUNCTION_ID,
                    )
                    .build()

            val response = executeAppFunctionAndWait(manager, request)
            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
            assertThat(response.appFunctionException().message)
                .startsWith(OutputInvalidArgumentException.INVALID_ARGUMENT_MESSAGE)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_throwsUnknownException_reportsAppError() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.THROW_UNKNOWN_EXCEPTION.toString())
        val request =
            ExecuteAppFunctionRequest.Builder(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    THROW_UNKNOWN_EXCEPTION_FUNCTION_ID,
                )
                .build()

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
            assertThat(response.appFunctionException().message)
                .startsWith(ThrowUnknownException.UNKNOWN_EXCEPTION_MESSAGE)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_throwsInvalidArgument_convertsToAppFunctionException() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.THROW_INVALID_ARGUMENT_EXCEPTION.toString())
        val request =
            ExecuteAppFunctionRequest.Builder(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    THROW_INVALID_ARGUMENT_FUNCTION_ID,
                )
                .build()

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
            assertThat(response.appFunctionException().message)
                .startsWith(ThrowInvalidArgumentException.INVALID_ARGUMENT_MESSAGE)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_sendCancellationSignal_cancelled() {
        val service = bindToRegistrationService(CURRENT_PKG)
        service.registerAppFunction(FunctionType.LONG_RUNNING.toString())
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, LONG_RUNNING_FUNCTION_ID).build()
        val cancellationSignal = CancellationSignal()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        val exceptionQueue = LinkedBlockingQueue<AppFunctionException>()
        val latch = CountDownLatch(1)

        manager.executeAppFunction(
            request,
            executionExecutor,
            cancellationSignal,
            createOutcomeReceiver(blockingQueue, exceptionQueue, latch),
        )

        // Wait until cancellation listener is set to be able to call cancel
        assertThat(waitForCancelListenerSet(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
        cancellationSignal.cancel()

        val callbackReceived = latch.await(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)
        assertThat(callbackReceived).isTrue()
        assertThat(blockingQueue).isEmpty()
        assertThat(exceptionQueue).hasSize(1)
        assertThat(exceptionQueue.first().errorCode).isEqualTo(AppFunctionException.ERROR_CANCELLED)
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun execute_disabledByDefaultFunction_throwsDisabledException() = doBlocking {
        val service = bindToRegistrationService(CURRENT_PKG)
        service.registerAppFunction(FunctionType.DISABLED_BY_DEFAULT.toString())
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, DISABLED_BY_DEFAULT_FUNCTION_ID).build()

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DISABLED)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun execute_afterEnablingDisabledByDefaultFunction_success() = doBlocking {
        val service = bindToRegistrationService(CURRENT_PKG)
        service.registerAppFunction(FunctionType.DISABLED_BY_DEFAULT.toString())
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, DISABLED_BY_DEFAULT_FUNCTION_ID).build()

        try {
            setAppFunctionEnabled(
                manager,
                DISABLED_BY_DEFAULT_FUNCTION_ID,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val response = executeAppFunctionAndWait(manager, request)

                assertThat(response.isSuccess).isTrue()
            }
        } finally {
            setAppFunctionEnabled(
                manager,
                DISABLED_BY_DEFAULT_FUNCTION_ID,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun execute_afterRegistrationOfEnabledFunction_success() = doBlocking {
        try {
            setAppFunctionEnabled(
                manager,
                DISABLED_BY_DEFAULT_FUNCTION_ID,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )

            val service = bindToRegistrationService(CURRENT_PKG)
            service.registerAppFunction(FunctionType.DISABLED_BY_DEFAULT.toString())
            val request =
                ExecuteAppFunctionRequest.Builder(CURRENT_PKG, DISABLED_BY_DEFAULT_FUNCTION_ID)
                    .build()
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val response = executeAppFunctionAndWait(manager, request)

                assertThat(response.isSuccess).isTrue()
            }
        } finally {
            setAppFunctionEnabled(
                manager,
                DISABLED_BY_DEFAULT_FUNCTION_ID,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun unregisterAndReregister_duringExecution_doesNotBlockOrInterrupt() {
        val executionStartedLatch = CountDownLatch(1)
        val executionFinishLatch = CountDownLatch(1)
        val emptyDocument = GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()

        val registration =
            registerAppFunction(LONG_RUNNING_FUNCTION_ID) { request, cancelSignal, outcomeReceiver
                ->
                executionStartedLatch.countDown()
                executionFinishLatch.await(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)
                outcomeReceiver.onResult(ExecuteAppFunctionResponse(emptyDocument))
            }
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, LONG_RUNNING_FUNCTION_ID).build()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        val exceptionQueue = LinkedBlockingQueue<AppFunctionException>()
        val onResultLatch = CountDownLatch(1)

        manager.executeAppFunction(
            request,
            executionExecutor,
            CancellationSignal(),
            createOutcomeReceiver(blockingQueue, exceptionQueue, onResultLatch),
        )

        // Wait for the function to start executing.
        assertWithMessage("Timed out waiting for execution to start")
            .that(executionStartedLatch.await(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS))
            .isTrue()

        // While the function is "running" (awaiting), unregister it and register a new one.
        registration.unregister()
        registerAppFunction(LONG_RUNNING_FUNCTION_ID, LongRunning(context))

        // Allow the original function to complete.
        executionFinishLatch.countDown()

        // Assert that the original function completed successfully.
        assertWithMessage("Timed out waiting for onResult callback")
            .that(onResultLatch.await(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
            .isTrue()
        assertThat(blockingQueue).hasSize(1)
        assertThat(exceptionQueue).isEmpty()
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun register_registrationProcessDied_functionIsDisabled() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request =
                createConcatStringsRequest(targetPackage = DynamicSchemaHelperApp.PACKAGE_NAME)
            serviceTestRule.unbindService()
            ShellCommand.builder("am force-stop $DynamicSchemaHelperApp.PACKAGE_NAME").execute()

            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_DISABLED)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @Throws(Exception::class)
    fun execute_toolProviderProcessStopped_reportsAppError() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.STOP_PROCESS.toString())

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        STOP_PROCESS_FUNCTION_ID,
                    )
                    .build()

            val response = executeAppFunctionAndWait(manager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    @RequiresFlagsEnabled(
        android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2
    )
    fun execute_getUrisFunction_hasAccessToReturnedUris() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        service.registerAppFunction(FunctionType.GET_URIS.toString())
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        GET_URIS_FUNCTION_ID,
                    )
                    .build()

            val response = executeAppFunctionAndWait(manager, request)
            assertThat(response.isSuccess).isTrue()
            assertThat(response.getOrNull()).isNotNull()
            val uris = response.getOrNull()!!.uriGrants
            assertThat(uris).hasSize(3)
            val readOnlyUri = uris[0].uri
            val writeOnlyUri = uris[1].uri
            val readWriteUri = uris[2].uri
            assertReadAccessible(contentResolver, readOnlyUri)
            assertReadAccessible(contentResolver, readWriteUri)
            assertReadInaccessible(contentResolver, writeOnlyUri)
            assertWriteAccessible(contentResolver, writeOnlyUri)
            assertWriteAccessible(contentResolver, readWriteUri)
            assertWriteInaccessible(contentResolver, readOnlyUri)
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun isAppFunctionEnabled_shouldReturnFalse_whenFunctionNotRegisteredAndDisabled() = doBlocking {
        setAppFunctionEnabled(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            CONCAT_STRINGS_FUNCTION_ID,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val result =
                isAppFunctionEnabled(
                    manager,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    CONCAT_STRINGS_FUNCTION_ID,
                )

            assertThat(result.exceptionOrNull()).isNull()
            assertThat(result.getOrThrow()).isFalse()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun isAppFunctionEnabled_shouldReturnFalse_whenFunctionNotRegisteredButEnabled() = doBlocking {
        setAppFunctionEnabled(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            CONCAT_STRINGS_FUNCTION_ID,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val result =
                isAppFunctionEnabled(
                    manager,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    CONCAT_STRINGS_FUNCTION_ID,
                )

            assertThat(result.exceptionOrNull()).isNull()
            assertThat(result.getOrThrow()).isFalse()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun isAppFunctionEnabled_shouldReturnFalse_whenFunctionRegisteredButDisabled() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        setAppFunctionEnabled(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            CONCAT_STRINGS_FUNCTION_ID,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )
        assertThat(service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())).isTrue()

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val result =
                isAppFunctionEnabled(
                    manager,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    CONCAT_STRINGS_FUNCTION_ID,
                )

            assertThat(result.exceptionOrNull()).isNull()
            assertThat(result.getOrThrow()).isFalse()
        }
    }

    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    fun isAppFunctionEnabled_shouldReturnTrue_whenFunctionRegisteredAndEnabled() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        setAppFunctionEnabled(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            CONCAT_STRINGS_FUNCTION_ID,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        assertThat(service.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())).isTrue()

        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val result =
                isAppFunctionEnabled(
                    manager,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    CONCAT_STRINGS_FUNCTION_ID,
                )

            assertThat(result.exceptionOrNull()).isNull()
            assertThat(result.getOrThrow()).isTrue()
        }
    }

    private fun registerAppFunction(
        functionId: String,
        function: AppFunction,
    ): AppFunctionRegistration {
        val registration =
            manager.registerAppFunction(functionId, testRegistrationExecutor, function)
        registrations.add(registration)
        return registration
    }

    private fun registerConcatStringsAppFunction(): AppFunctionRegistration {
        return registerAppFunction(CONCAT_STRINGS_FUNCTION_ID, ConcatStrings())
    }

    /** Creates an OutcomeReceiver for testing purposes. */
    private fun createOutcomeReceiver(
        resultQueue: LinkedBlockingQueue<ExecuteAppFunctionResponse>,
        exceptionQueue: LinkedBlockingQueue<AppFunctionException>,
        onResultLatch: CountDownLatch,
    ): OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
        return object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
            override fun onResult(result: ExecuteAppFunctionResponse) {
                resultQueue.add(result)
                onResultLatch.countDown()
            }

            override fun onError(error: AppFunctionException) {
                exceptionQueue.add(error)
                onResultLatch.countDown()
            }
        }
    }

    private fun <T> Result<T>.appFunctionException(): AppFunctionException =
        exceptionOrNull() as AppFunctionException

    /** Runs a suspend block in a blocking manner */
    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun bindToRegistrationService(
        packageName: String
    ): ITestAppFunctionRegistrationService {
        val serviceIntent =
            if (packageName == CURRENT_PKG) {
                Intent(context, TestAppFunctionRegistrationService::class.java)
            } else {
                Intent().apply {
                    component =
                        ComponentName(
                            packageName,
                            "android.app.appfunctions.testutils.TestAppFunctionRegistrationService",
                        )
                }
            }
        val binder: IBinder = serviceTestRule.bindService(serviceIntent)
        return ITestAppFunctionRegistrationService.Stub.asInterface(binder)
    }

    private fun setAppFunctionEnabled(
        packageName: String,
        functionId: String,
        @AppFunctionManager.EnabledState enabledState: Int,
    ) {
        val enabledStateString =
            when (enabledState) {
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT -> "default"
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED -> "enable"
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED -> "disable"
                else -> throw IllegalArgumentException("Unknown enable state: $enabledState")
            }

        assertThat(
                ShellCommand.builder("cmd app_function set-enabled")
                    .addOption("--package", packageName)
                    .addOption("--function", functionId)
                    .addOption("--state", enabledStateString)
                    .execute()
            )
            .isEqualTo("App function enabled state updated successfully.\n")
    }

    private fun createConcatStringsRequest(
        targetPackage: String = CURRENT_PKG
    ): ExecuteAppFunctionRequest {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        return ExecuteAppFunctionRequest.Builder(targetPackage, CONCAT_STRINGS_FUNCTION_ID)
            .setParameters(parameters)
            .build()
    }

    private fun assertConcatStringsResponseCorrect(
        response: Result<ExecuteAppFunctionResponse>,
        expectedOutput: String = "AB",
    ) {
        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(expectedOutput)
    }

    private suspend fun installPackage(
        path: String,
        packageName: String,
        checkIndexation: Boolean,
    ) {
        assertThat(
                SystemUtil.runShellCommand(java.lang.String.format("pm install -r -t -g %s", path))
            )
            .isEqualTo("Success\n")

        // Blocked until the AppFunctions are indexed too
        if (checkIndexation) {
            retryAssert { assertThat(getAllStaticMetadataPackages(context)).contains(packageName) }
            runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
                retryAssert { assertThat(getAllRuntimeMetadataPackages()).contains(packageName) }
            }
        }
    }

    private fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"
        const val SHORT_TIMEOUT_SECOND: Long = 2
        const val LONG_TIMEOUT_SECOND: Long = 20

        const val EXECUTE_APP_FUNCTIONS_PERMISSION = Manifest.permission.EXECUTE_APP_FUNCTIONS
        const val TEST_APP_A_PKG: String = "com.android.cts.appsearch.indexertestapp.a"
        const val TEST_APP_ROOT_FOLDER: String = "/data/local/tmp/cts/appfunctions/"

        const val TEST_APP_A_PATH_NO_FUNCTIONS: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk"

        const val TEST_APP_A_PATH_STATIC_ONLY_FUNCTIONS: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk"

        const val TEST_APP_A_PATH_DYNAMIC_ONLY_FUNCTIONS =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAAppLevelFunctionsReg.apk"
    }
}
