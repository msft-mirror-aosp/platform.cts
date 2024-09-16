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

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @get:Rule
    val setTimeoutRule: DeviceConfigStateChangerRule =
        DeviceConfigStateChangerRule(
            context,
            "appfunctions",
            "execute_app_function_timeout_millis",
            "1000",
        )

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
            assertThat(getAllStaticMetadataPackages()).containsAtLeast(CTS_PACKAGE, TEST_HELPER_PKG)
        }
    }

    @Test
    fun checkManagerNotNull() {
        assertThat(mManager).isNotNull()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @Throws(Exception::class)
    fun executeAppFunction_failed_noSuchMethod() {
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "noSuchMethod").build()

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
            ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "add_invokeCallbackTwice")
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
    fun executeAppFunction_success() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()

        val request =
            ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "add").setParameters(parameters).build()

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
    fun executeAppFunction_otherExistingTargetPackage() = runTest {
        val request = ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "add").build()

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
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "throwException").build()

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
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "kill").build()

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
    fun executeAppFunction_timedOut() {
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "notInvokeCallback").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_TIMED_OUT)
        assertServiceDestroyed()
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
            ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "addAsync")
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
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.resultCode).isEqualTo(ExecuteAppFunctionResponse.RESULT_INTERNAL_ERROR)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_hasManagedProfileRunInPersonalProfile_success() {
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "noOp").build()

        val response = executeAppFunctionAndWait(request)

        assertThat(response.isSuccess).isTrue()
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_deviceOwner_fail() {
        val request = ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "noOp").build()

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
            ExecuteAppFunctionRequest.Builder(CTS_PACKAGE, "add").setParameters(parameters).build()

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

    @Throws(InterruptedException::class)
    private fun executeAppFunctionAndWait(
        request: ExecuteAppFunctionRequest
    ): ExecuteAppFunctionResponse {
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor) { e: ExecuteAppFunctionResponse
            ->
            blockingQueue.add(e)
        }
        return requireNotNull(blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
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

    /** Runnable that throws. */
    fun interface ThrowRunnable {
        @Throws(Throwable::class) suspend fun run()
    }

    companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
        const val CTS_PACKAGE: String = "android.app.appfunctions.cts"
        const val SHORT_TIMEOUT_SECOND: Long = 1
        const val LONG_TIMEOUT_SECOND: Long = 5
        private const val TEST_APP_ROOT_FOLDER = "/data/local/tmp/cts/appfunctions/"

        private const val RETRY_CHECK_INTERVAL_MILLIS: Long = 500
        private const val RETRY_MAX_INTERVALS: Long = 10
        private const val PROPERTY_PACKAGE_NAME = "packageName"
        private const val APP_FUNCTION_INDEXER_PACKAGE = "android"

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
