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
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.cts.AppFunctionManagerTest.ThrowRunnable
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionMetadataTest {
    @Rule
    fun grantExecuteAppFunctionsPermissionRule() =
        AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.EXECUTE_APP_FUNCTIONS,
        )

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

    @Before
    @After
    fun uninstallTestPackages() {
        uninstallPackage(TEST_APP_A_PKG)
        uninstallPackage(TEST_APP_B_PKG)
    }

    @Test
    fun installPackageWithAppFunction_runtimeMetadataExist() = runTest {
        installPackage(TEST_APP_A_V2_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
        }
    }

    @Test
    fun updatePackage_runtimeMetadataUpdated() = runTest {
        installPackage(TEST_APP_A_V2_PATH)
        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
        }

        installPackage(TEST_APP_A_V3_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(
                    AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print2"),
                    AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print3"),
                )
        }
    }

    @Test
    fun uninstallPackageWithAppFunctions_runtimeMetadataRemoved() = runTest {
        installPackage(TEST_APP_A_V2_PATH)
        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
        }

        uninstallPackage(TEST_APP_A_PKG)

        retryAssert { assertThat(queryAppFunctionInfos(TEST_APP_A_PKG)).isEmpty() }
    }

    @Test
    fun installTwoPackageWithAppFunctions_runtimeMetadataExist() = runTest {
        installPackage(TEST_APP_A_V2_PATH)
        installPackage(TEST_APP_B_V1_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }
    }

    private fun installPackage(path: String) {
        assertThat(
                SystemUtil.runShellCommand(
                    java.lang.String.format(
                        "pm install -r -i %s -t -g %s",
                        context.packageName,
                        path,
                    )
                )
            )
            .isEqualTo("Success\n")
    }

    private fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    private fun queryAppFunctionInfos(packageName: String): List<AppFunctionInfo> {
        val globalSearchSession: GlobalSearchSessionShim =
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                String.format("packageName:\"%s\"", packageName),
                SearchSpec.Builder()
                    .addFilterNamespaces(AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE)
                    .addFilterPackageNames("android")
                    .addFilterSchemas(AppFunctionRuntimeMetadata.RUNTIME_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults).map {
            AppFunctionInfo(
                it.getPropertyString(PROPERTY_PACKAGE_NAME)!!,
                it.getPropertyString(PROPERTY_FUNCTION_ID)!!,
            )
        }
    }

    data class AppFunctionInfo(val packageName: String, val functionId: String)

    private companion object {
        const val TEST_APP_ROOT_FOLDER: String = "/data/local/tmp/cts/appfunctions/"
        const val TEST_APP_A_V1_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk"
        const val TEST_APP_A_V2_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk"
        const val TEST_APP_A_V3_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk"
        const val TEST_APP_B_V1_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppBV1.apk"
        const val TEST_APP_A_PKG: String = "com.android.cts.appsearch.indexertestapp.a"
        const val TEST_APP_B_PKG: String = "com.android.cts.appsearch.indexertestapp.b"
        const val RETRY_CHECK_INTERVAL_MILLIS: Long = 500
        const val RETRY_MAX_INTERVALS: Long = 10
        const val PROPERTY_FUNCTION_ID: String = "functionId"
        const val PROPERTY_PACKAGE_NAME: String = "packageName"

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
                    // TODO(b/357551503): Figure out the correct rule to make runtest blocking
                    Thread.sleep(RETRY_CHECK_INTERVAL_MILLIS)
                }
            }
            throw lastError!!
        }
    }
}
