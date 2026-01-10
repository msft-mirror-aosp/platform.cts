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
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.AppFunctionSchemaMetadata
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appsearch.GenericDocument
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.compatibility.common.util.AdoptShellPermissionsRule
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

@RunWith(BedsteadJUnit4::class)
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

    @get:Rule
    val setAgentAllowlistRule: DeviceConfigStateChangerRule =
        DeviceConfigStateChangerRule(
            context,
            "machine_learning",
            "allowlisted_app_functions_agents",
            context.packageName,
        )

    @Before
    fun assumeValidAgent() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)

        if (checkAppFunctionAccessEnabled()) {
            runWithShellPermission(Manifest.permission.MANAGE_APP_FUNCTION_ACCESS) {
                assumeTrue(manager.validAgents.contains(context.packageName))
            }
        }
    }

    @Before
    @After
    fun uninstallTestPackages() {
        uninstallPackage(TEST_APP_A_PKG)
        uninstallPackage(TEST_APP_B_PKG)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun createAppFunctionMetadata() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        val packageName = TEST_APP_A_PKG
        val functionId = "com.example.utils#print1"
        val packageMetadata =
            AppFunctionPackageMetadata.create(
                TEST_APP_A_PKG,
                listOf(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyString("exampleProperty", "exampleValue")
                        .build()
                ),
            )

        retryAssert {
            val afStaticMetadataGd = queryAppFunctionStaticMetadata(packageName, functionId)
            val appFunctionMetadata =
                AppFunctionMetadata.Builder(afStaticMetadataGd, packageMetadata)
                    .setEnabled(false)
                    .build()

            assertThat(appFunctionMetadata.name).isEqualTo(AppFunctionName(packageName, functionId))
            assertThat(appFunctionMetadata.schemaMetadata)
                .isEqualTo(AppFunctionSchemaMetadata("utils", "print", 1L))
            assertThat(appFunctionMetadata.isEnabled).isFalse()
            assertThat(appFunctionMetadata.metadataDocument).isEqualTo(afStaticMetadataGd)
            assertThat(appFunctionMetadata.packageMetadata).isEqualTo(packageMetadata)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun parcelAndUnparcelMetadata_allFieldsSet() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        val packageName = TEST_APP_A_PKG
        val functionId = "com.example.utils#print1"
        val packageMetadata =
            AppFunctionPackageMetadata.create(
                TEST_APP_A_PKG,
                listOf(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyString("exampleProperty", "exampleValue")
                        .build()
                ),
            )

        retryAssert {
            val afStaticMetadataGd = queryAppFunctionStaticMetadata(packageName, functionId)
            val originalMetadata =
                AppFunctionMetadata.Builder(afStaticMetadataGd, packageMetadata)
                    .setEnabled(true)
                    .build()

            val restoredMetadata = parcelAndUnparcelAppFunctionMetadata(originalMetadata)

            assertThat(restoredMetadata.name).isEqualTo(originalMetadata.name)
            assertThat(restoredMetadata.schemaMetadata).isEqualTo(originalMetadata.schemaMetadata)
            assertThat(restoredMetadata.isEnabled).isEqualTo(originalMetadata.isEnabled)
            assertThat(restoredMetadata.metadataDocument)
                .isEqualTo(originalMetadata.metadataDocument)
            assertThat(restoredMetadata.packageMetadata).isEqualTo(originalMetadata.packageMetadata)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun parcelAndUnparcelMetadata_nullSchema() = doBlocking {
        val packageName = "android.app.appfunctions.cts"
        val functionId = "noSchema"
        val packageMetadata = AppFunctionPackageMetadata.create(packageName, listOf())

        retryAssert {
            val afStaticMetadataGd = queryAppFunctionStaticMetadata(packageName, functionId)
            val originalMetadata =
                AppFunctionMetadata.Builder(afStaticMetadataGd, packageMetadata)
                    .setEnabled(true)
                    .build()
            val restoredMetadata = parcelAndUnparcelAppFunctionMetadata(originalMetadata)

            assertThat(restoredMetadata.name).isEqualTo(originalMetadata.name)
            assertThat(restoredMetadata.schemaMetadata).isNull()
            assertThat(restoredMetadata.isEnabled).isEqualTo(originalMetadata.isEnabled)
            assertThat(restoredMetadata.metadataDocument)
                .isEqualTo(originalMetadata.metadataDocument)
            assertThat(restoredMetadata.packageMetadata).isEqualTo(originalMetadata.packageMetadata)
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun installPackageWithAppFunction_runtimeMetadataExist() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun updatePackage_runtimeMetadataUpdated() = doBlocking {
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
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun uninstallPackageWithAppFunctions_runtimeMetadataRemoved() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
        }

        uninstallPackage(TEST_APP_A_PKG)

        retryAssert { assertThat(queryAppFunctionInfos(TEST_APP_A_PKG)).isEmpty() }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun installTwoPackageWithAppFunctions_runtimeMetadataExist() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        installPackage(TEST_APP_B_V1_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun twoPackagesInstalled_updateOneOfThem_runtimeMetadataUpdated() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        installPackage(TEST_APP_B_V1_PATH)
        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }

        installPackage(TEST_APP_A_V3_PATH)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(
                    AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print2"),
                    AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print3"),
                )
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun twoPackagesInstalled_uninstallOneOfThem_runtimeMetadataUpdated() = doBlocking {
        installPackage(TEST_APP_A_V2_PATH)
        installPackage(TEST_APP_B_V1_PATH)
        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_A_PKG, "com.example.utils#print1"))
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }

        uninstallPackage(TEST_APP_A_PKG)

        retryAssert {
            assertThat(queryAppFunctionInfos(TEST_APP_A_PKG)).isEmpty()
            assertThat(queryAppFunctionInfos(TEST_APP_B_PKG))
                .containsExactly(AppFunctionInfo(TEST_APP_B_PKG, "com.example.utils#print5"))
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun installPackageWithAppFunction_notValidAgent_runtimeMetadataNotVisible() = doBlocking {
        clearAgentAllowlist()

        installPackage(TEST_APP_A_V2_PATH)

        retryAssert { assertThat(queryAppFunctionInfos(TEST_APP_A_PKG).isEmpty()) }
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

    private fun clearAgentAllowlist() {
        SystemUtil.runShellCommand(
            "device_config delete machine_learning allowlisted_app_functions_agents"
        )
    }

    private fun queryAppFunctionStaticMetadata(
        packageName: String,
        functionId: String,
    ): GenericDocument {
        val globalSearchSession: GlobalSearchSessionShim =
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                String.format(
                    "%s:\"%s\"  %s:\"%s\"",
                    PROPERTY_PACKAGE_NAME,
                    packageName,
                    PROPERTY_FUNCTION_ID,
                    functionId,
                ),
                SearchSpec.Builder()
                    .addFilterNamespaces(
                        AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
                    )
                    .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults).single()
    }

    private fun queryAppFunctionRuntimeMetadata(packageName: String): List<GenericDocument> {
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
        return collectAllSearchResults(searchResults)
    }

    private fun checkAppFunctionAccessEnabled(): Boolean {
        return android.permission.flags.Flags.appFunctionAccessApiEnabled() &&
            android.permission.flags.Flags.appFunctionAccessServiceEnabled()
    }

    private fun queryAppFunctionInfos(packageName: String): List<AppFunctionInfo> {
        return queryAppFunctionRuntimeMetadata(packageName).map {
            AppFunctionInfo(
                it.getPropertyString(PROPERTY_PACKAGE_NAME)!!,
                it.getPropertyString(PROPERTY_FUNCTION_ID)!!,
            )
        }
    }

    data class AppFunctionInfo(val packageName: String, val functionId: String)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_APP_ROOT_FOLDER: String = "/data/local/tmp/cts/appfunctions/"
        const val TEST_APP_A_V2_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk"
        const val TEST_APP_A_V3_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk"
        const val TEST_APP_B_V1_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppBV1.apk"
        const val TEST_APP_A_PKG: String = "com.android.cts.appsearch.indexertestapp.a"
        const val TEST_APP_B_PKG: String = "com.android.cts.appsearch.indexertestapp.b"
        const val PROPERTY_FUNCTION_ID: String = "functionId"
        const val PROPERTY_PACKAGE_NAME: String = "packageName"
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun parcelAndUnparcelAppFunctionMetadata(
        original: AppFunctionMetadata
    ): AppFunctionMetadata {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionMetadata.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
