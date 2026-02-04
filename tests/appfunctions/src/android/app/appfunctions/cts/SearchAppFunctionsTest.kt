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
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionMetadata.SCOPE_ACTIVITY
import android.app.appfunctions.AppFunctionMetadata.SCOPE_GLOBAL
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionSchemaMetadata
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.LegacySchemaHelperApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.UpdatableHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.cts.AppFunctionUtils.uninstallPackageAsUser
import android.app.appfunctions.cts.AppSearchUtils.sanitizeGenericDocument
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.ITestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(BedsteadJUnit4::class)
class SearchAppFunctionsTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val serviceTestRule = ServiceTestRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        setTestPageSize(2)

        uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)

        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@SearchAppFunctionsTest.manager = manager

        // Doing containsAtLeast instead of containsExactly here in case there are preloaded
        // apps having app functions.
        assertThat(getAllStaticMetadataPackages())
            .containsAtLeast(CtsApp.PACKAGE_NAME, DynamicSchemaHelperApp.PACKAGE_NAME)
        // required permission because runtime metadata is only visible to owner package
        runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
            assertThat(getAllRuntimeMetadataPackages())
                .containsAtLeast(CtsApp.PACKAGE_NAME, DynamicSchemaHelperApp.PACKAGE_NAME)
        }
    }

    @After
    fun reset() {
        resetTestPageSize()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    fun searchAppFunctions_withoutAnyPermission_shouldOnlySeeSelfFunctions() = doBlocking {
        installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
        try {
            val searchSpec = AppFunctionSearchSpec.Builder().build()

            val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
                searchAppFunctions(searchSpec).associateBy { it.name }

            assertThat(resultAppFunctionsByName.keys)
                .containsExactly(
                    CtsApp.FunctionNames.THROW_EXCEPTION,
                    CtsApp.FunctionNames.UNCAUGHT_CLIENT_EXCEPTION,
                    CtsApp.FunctionNames.ADD_INVOKE_CALLBACK_TWICE,
                    CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS,
                    CtsApp.FunctionNames.DYNAMIC_LONG_RUNNING,
                    CtsApp.FunctionNames.ADD_ASYNC,
                    CtsApp.FunctionNames.NOT_INVOKE_CALLBACK,
                    CtsApp.FunctionNames.RUN_FOREVER,
                    CtsApp.FunctionNames.ADD,
                    CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                    CtsApp.FunctionNames.NO_OP,
                    CtsApp.FunctionNames.KILL,
                    CtsApp.FunctionNames.LONG_RUNNING_FUNCTION,
                    CtsApp.FunctionNames.NO_SCHEMA,
                    CtsApp.FunctionNames.CONTEXT,
                )
        } finally {
            uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @EnsureHasPermission(Manifest.permission.QUERY_ALL_PACKAGES)
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
            "when using Bedstead"
    )
    fun searchAppFunctions_withoutExecuteAppFunctionPermission_shouldOnlySeeSelfFunctions() =
        doBlocking {
            installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
                    searchAppFunctions(searchSpec).associateBy { it.name }

                assertThat(resultAppFunctionsByName.keys)
                    .containsExactly(
                        CtsApp.FunctionNames.THROW_EXCEPTION,
                        CtsApp.FunctionNames.UNCAUGHT_CLIENT_EXCEPTION,
                        CtsApp.FunctionNames.ADD_INVOKE_CALLBACK_TWICE,
                        CtsApp.FunctionNames.DYNAMIC_LONG_RUNNING,
                        CtsApp.FunctionNames.ADD_ASYNC,
                        CtsApp.FunctionNames.NOT_INVOKE_CALLBACK,
                        CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS,
                        CtsApp.FunctionNames.RUN_FOREVER,
                        CtsApp.FunctionNames.ADD,
                        CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                        CtsApp.FunctionNames.NO_OP,
                        CtsApp.FunctionNames.KILL,
                        CtsApp.FunctionNames.LONG_RUNNING_FUNCTION,
                        CtsApp.FunctionNames.NO_SCHEMA,
                        CtsApp.FunctionNames.CONTEXT,
                    )
            } finally {
                uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
            "when using Bedstead"
    )
    fun searchAppFunctions_noPermissions_shouldReturnEmpty_whenTargetOtherPackages() = doBlocking {
        installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
        try {
            val searchSpec =
                AppFunctionSearchSpec.Builder()
                    .setPackageNames(listOf(DynamicSchemaHelperApp.PACKAGE_NAME))
                    .build()

            val resultAppFunctions = searchAppFunctions(searchSpec)

            assertThat(resultAppFunctions).isEmpty()
        } finally {
            uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
            "when using Bedstead"
    )
    fun searchAppFunctions_noPermissions_shouldReturnEmpty_whenTargetOtherPackagesFunctions() =
        doBlocking {
            installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
            try {
                val searchSpec =
                    AppFunctionSearchSpec.Builder()
                        .setFunctionNames(
                            listOf(DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT)
                        )
                        .build()

                val resultAppFunctions = searchAppFunctions(searchSpec)

                assertThat(resultAppFunctions).isEmpty()
            } finally {
                uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(Manifest.permission.QUERY_ALL_PACKAGES)
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
            "when using Bedstead"
    )
    fun searchAppFunctions_searchAllWithExecuteAppFunctionPermission_shouldSeeAllVisiblePackages() =
        doBlocking {
            installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
                val functionsGroupByPackage = results.associateBy { it.packageMetadata.packageName }

                assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
            } finally {
                uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun searchAppFunctions_searchAllWithAllPermission_shouldSeeAllPackages() = doBlocking {
        installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
        try {
            val searchSpec = AppFunctionSearchSpec.Builder().build()

            val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
            val functionsGroupByPackage = results.groupBy { it.packageMetadata.packageName }

            assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
            assertThat(
                    functionsGroupByPackage[LegacySchemaHelperApp.PACKAGE_NAME]!!.map { it.name }
                )
                .containsExactlyElementsIn(LegacySchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
            assertThat(
                    functionsGroupByPackage[DynamicSchemaHelperApp.PACKAGE_NAME]!!.map { it.name }
                )
                .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
        } finally {
            uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun searchAppFunctions_searchByScopeWithSingleScope_seeFilteredResult() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setScopes(listOf(AppFunctionMetadata.SCOPE_GLOBAL))
                .build()

        val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
        val functionsGroupByPackage = results.groupBy { it.packageMetadata.packageName }

        assertThat(functionsGroupByPackage[DynamicSchemaHelperApp.PACKAGE_NAME]!!.map { it.name })
            .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_GLOBAL_FUNCTIONS)
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun searchAppFunctions_searchByScopeWithTwoScopes_seeFilteredResult() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setScopes(
                    listOf(AppFunctionMetadata.SCOPE_GLOBAL, AppFunctionMetadata.SCOPE_ACTIVITY)
                )
                .build()

        val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
        val functionsGroupByPackage = results.groupBy { it.packageMetadata.packageName }

        // TODO: Update this to be ALL_FUNCTIONS once the indexer change is in.
        assertThat(functionsGroupByPackage[DynamicSchemaHelperApp.PACKAGE_NAME]!!.map { it.name })
            .containsExactlyElementsIn(
                listOf(
                    DynamicSchemaHelperApp.FunctionNames.GLOBAL_SCOPE,
                    DynamicSchemaHelperApp.FunctionNames.ACTIVITY_SCOPE
                )
            )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun searchAppFunctions_hasQueryAllAndReadAppFunctionMetadataPermission_shouldSeeAllPackages() =
        doBlocking {
            installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
                val functionsGroupByPackage = results.groupBy { it.packageMetadata.packageName }

                assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
                assertThat(
                        functionsGroupByPackage[LegacySchemaHelperApp.PACKAGE_NAME]!!.map {
                            it.name
                        }
                    )
                    .containsExactlyElementsIn(LegacySchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
                assertThat(
                        functionsGroupByPackage[DynamicSchemaHelperApp.PACKAGE_NAME]!!.map {
                            it.name
                        }
                    )
                    .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
            } finally {
                uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun searchAppFunctions_hasQueryAllAndExecuteSystemPermission_shouldSeeAllPackages() =
        doBlocking {
            installPackage(UpdatableHelperApp.ApkPaths.BASE_APP)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
                val functionsGroupByPackage = results.groupBy { it.packageMetadata.packageName }

                assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
                assertThat(
                        functionsGroupByPackage[LegacySchemaHelperApp.PACKAGE_NAME]!!.map {
                            it.name
                        }
                    )
                    .containsExactlyElementsIn(LegacySchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
                assertThat(
                        functionsGroupByPackage[DynamicSchemaHelperApp.PACKAGE_NAME]!!.map {
                            it.name
                        }
                    )
                    .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
            } finally {
                uninstallPackage(UpdatableHelperApp.PACKAGE_NAME)
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
    )
    fun searchAppFunctionsFromLegacySchema_shouldSucceedWithPermission() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(LegacySchemaHelperApp.PACKAGE_NAME))
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(
                LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT,
                LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                LegacySchemaHelperApp.FunctionNames.NO_OP,
                LegacySchemaHelperApp.FunctionNames.RESTRICT_CALLER_FALSE,
                LegacySchemaHelperApp.FunctionNames.RESTRICT_CALLER_TRUE,
                LegacySchemaHelperApp.FunctionNames.GET_URIS,
                LegacySchemaHelperApp.FunctionNames.ECHO_BYTES,
            )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
    )
    fun searchAppFunctionsFromLegacySchema_shouldReturnCorrectMetadata() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(LegacySchemaHelperApp.PACKAGE_NAME))
                .setFunctionNames(
                    listOf(
                        LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT,
                        LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                    )
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[LegacySchemaHelperApp.FunctionNames.ADD_ENABLED_BY_DEFAULT]!!,
            LegacySchemaHelperApp.FunctionMetadata.ADD_ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT]!!,
            LegacySchemaHelperApp.FunctionMetadata.ADD_DISABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_withCustomProperties_setsAllProperties() = doBlocking {
        val schemaMetadata = AppFunctionSchemaMetadata("myUtils", "testSchema", 1)
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(DynamicSchemaHelperApp.PACKAGE_NAME))
                .setFunctionNames(listOf(DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT))
                .setSchemaCategory(schemaMetadata.category)
                .setSchemaName(schemaMetadata.name)
                .setMinSchemaVersion(schemaMetadata.version)
                .build()

        val resultList = searchAppFunctions(searchSpec)

        assertThat(resultList).hasSize(1)
        val resultMetadata = resultList[0]
        assertAppFunctionMetadataEquals(
            resultMetadata,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_functionNamesStricterThanPackages_noSchema_succeeds() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(CtsApp.PACKAGE_NAME, DynamicSchemaHelperApp.PACKAGE_NAME))
                .setFunctionNames(
                    listOf(
                        DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                        DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT,
                    )
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT,
            )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_packagesStricterThanFunctionNames_noSchema_succeeds() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(CtsApp.PACKAGE_NAME))
                .setFunctionNames(
                    listOf(
                        CtsApp.FunctionNames.ADD,
                        DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                    )
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys).containsExactly(CtsApp.FunctionNames.ADD)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[CtsApp.FunctionNames.ADD]!!,
            CtsApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_disjointFunctionNamesAndPackageNames_noResult() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(CtsApp.PACKAGE_NAME))
                .setFunctionNames(
                    // Both functions belongs to a different package
                    listOf(
                        DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                        DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT,
                    )
                )
                .build()

        val result = searchAppFunctions(searchSpec)

        assertThat(result).isEmpty()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_schemaNameOnly_succeeds() = doBlocking {
        val searchSpec = AppFunctionSearchSpec.Builder().setSchemaName("testSchema").build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION,
            )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION]!!,
            DynamicSchemaHelperApp.FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_schemaCategoryOnly_succeeds() = doBlocking {
        val searchSpec = AppFunctionSearchSpec.Builder().setSchemaCategory("myUtils").build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION,
            )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION]!!,
            DynamicSchemaHelperApp.FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_highMinSchemaVersion_filtersLowerVersions() = doBlocking {
        val searchSpec = AppFunctionSearchSpec.Builder().setMinSchemaVersion(5).build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION]!!,
            DynamicSchemaHelperApp.FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_packageNamesSpecified_noFunctionNames_succeeds() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf(DynamicSchemaHelperApp.PACKAGE_NAME))
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION]!!,
            DynamicSchemaHelperApp.FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_functionNamesSpecified_noPackageNames_success() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    listOf(
                        DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                        CtsApp.FunctionNames.ADD,
                    )
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                CtsApp.FunctionNames.ADD,
            )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[CtsApp.FunctionNames.ADD]!!,
            CtsApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_functionNotExist_returnsEmptyList() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("fake.package"))
                .setFunctionNames(listOf(AppFunctionName("fake.package", "notExist")))
                .build()

        assertThat(searchAppFunctions(searchSpec)).isEmpty()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    fun searchAppFunctions_crossUser_shouldFailWithoutPermission() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
        installExistingPackageAsUser(DynamicSchemaHelperApp.PACKAGE_NAME, secondaryUser)
        retryAssert(maxIntervals = 20) {
            runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)
            }
        }
        runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
            manager =
                context
                    .createContextAsUser(secondaryUser.userHandle(), 0)
                    .getSystemService(AppFunctionManager::class.java)
        }

        var exception: Exception? = null
        try {
            searchAppFunctions(AppFunctionSearchSpec.Builder().build())
        } catch (e: RuntimeException) {
            exception = e
        }

        assertThat(exception).isNotNull()
        assertThat(exception!!.cause).isInstanceOf(SecurityException::class.java)
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
    )
    fun searchAppFunctions_crossUser_shouldOnlySeePackageInThatUser() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        uninstallPackageAsUser(LegacySchemaHelperApp.PACKAGE_NAME, secondaryUser)
        installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
        installExistingPackageAsUser(DynamicSchemaHelperApp.PACKAGE_NAME, secondaryUser)
        retryAssert {
            assertThat(
                    context
                        .createContextAsUser(secondaryUser.userHandle(), 0)
                        .packageManager
                        .getInstalledPackages(0)
                        .map { it.packageName }
                )
                .doesNotContain(LegacySchemaHelperApp.PACKAGE_NAME)
            assertThat(
                    getAllStaticMetadataPackages(
                        context.createContextAsUser(secondaryUser.userHandle(), 0)
                    )
                )
                .contains(DynamicSchemaHelperApp.PACKAGE_NAME)
            assertThat(
                    getAllRuntimeMetadataPackages(
                        context.createContextAsUser(secondaryUser.userHandle(), 0)
                    )
                )
                .contains(DynamicSchemaHelperApp.PACKAGE_NAME)
        }
        manager =
            context
                .createContextAsUser(secondaryUser.userHandle(), 0)
                .getSystemService(AppFunctionManager::class.java)

        val result: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(
                    AppFunctionSearchSpec.Builder()
                        .setPackageNames(
                            listOf(
                                LegacySchemaHelperApp
                                    .PACKAGE_NAME, // Not installed on secondary user
                                DynamicSchemaHelperApp.PACKAGE_NAME,
                            )
                        )
                        .build()
                )
                .associateBy { it.name }

        assertThat(result.keys)
            .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
        assertAppFunctionMetadataEquals(
            result[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            result[DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT]!!,
            DynamicSchemaHelperApp.FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            result[DynamicSchemaHelperApp.FunctionNames.HIGH_SCHEMA_VERSION]!!,
            DynamicSchemaHelperApp.FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun testSearchSpecBuilder_emptyPackageNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setPackageNames(emptyList())
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun testSearchSpecBuilder_emptyFunctionNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setFunctionNames(emptyList())
        }
    }

    private suspend fun installPackage(path: String) {
        assertThat(
                SystemUtil.runShellCommand(java.lang.String.format("pm install -r -t -g %s", path))
            )
            .isEqualTo("Success\n")

        // Blocked until the AppFunctions are indexed too
        retryAssert {
            getAllStaticMetadataPackages(context).contains(UpdatableHelperApp.PACKAGE_NAME)
        }
        runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
            retryAssert {
                assertThat(getAllRuntimeMetadataPackages())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)
            }
        }
    }

    private fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    private fun getVisiblePackages(): List<String> {
        return context.packageManager.getInstalledPackages(0).map { it.packageName }
    }

    private suspend fun searchAppFunctions(
        searchSpec: AppFunctionSearchSpec
    ): List<AppFunctionMetadata> = suspendCancellableCoroutine { continuation ->
        manager.searchAppFunctions(
            searchSpec,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun assertAppFunctionMetadataEquals(
        actual: AppFunctionMetadata,
        expected: AppFunctionMetadata,
    ) {
        assertThat(actual.name).isEqualTo(expected.name)
        assertThat(actual.schemaMetadata).isEqualTo(expected.schemaMetadata)
        val clearedActualGd = sanitizeGenericDocument(actual.metadataDocument)
        val expectedGd = sanitizeGenericDocument(expected.metadataDocument)
        assertThat(clearedActualGd).isEqualTo(expectedGd)
        assertAppFunctionPackageMetadataEquals(actual.packageMetadata, expected.packageMetadata)
    }

    private fun assertAppFunctionPackageMetadataEquals(
        actual: AppFunctionPackageMetadata,
        expected: AppFunctionPackageMetadata,
    ) {
        assertThat(actual.packageName).isEqualTo(expected.packageName)
        val clearedActualGd = sanitizeGenericDocument(actual.metadataDocument)
        val expectedGd = sanitizeGenericDocument(expected.metadataDocument)
        assertThat(clearedActualGd).isEqualTo(expectedGd)
    }

    private fun setTestPageSize(pageSize: Int) {
        assertThat(
                ShellCommand.builder("cmd app_function set-test-page-size")
                    .addOption("--page-size", pageSize.toString())
                    .execute()
            )
            .isEqualTo("Set test page size to $pageSize\n")
    }

    private fun resetTestPageSize() {
        ShellCommand.builder("cmd app_function reset-test-page-size").execute()
    }

    private fun bindToRegistrationService(
        packageName: String
    ): ITestAppFunctionRegistrationService {
        val serviceIntent =
            if (packageName == CtsApp.PACKAGE_NAME) {
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

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
    }
}
