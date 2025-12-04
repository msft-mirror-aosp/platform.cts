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
import android.app.UiAutomation
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionSchemaMetadata
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.Companion.CURRENT_PKG
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.Companion.TEST_HELPER_DYNAMIC_SCHEMA_PKG
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.Companion.TEST_HELPER_PKG
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.FunctionMetadata
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.FunctionName
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.FunctionName.HELPER_PACKAGE_FUNCTIONS
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.cts.AppSearchUtils.sanitizeGenericDocument
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
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

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val uiAutomation: UiAutomation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private lateinit var mManager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        uninstallPackage(TEST_APP_A_PKG)

        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        mManager = manager

        // Doing containsAtLeast instead of containsExactly here in case there are preloaded
        // apps having app functions.
        assertThat(getAllStaticMetadataPackages())
            .containsAtLeast(CURRENT_PKG, TEST_HELPER_DYNAMIC_SCHEMA_PKG)
        // required permission because runtime metadata is only visible to owner package
        runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
            assertThat(getAllRuntimeMetadataPackages())
                .containsAtLeast(CURRENT_PKG, TEST_HELPER_DYNAMIC_SCHEMA_PKG)
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
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
                "using Bedstead"
    )
    fun searchAppFunctions_withoutAnyPermission_shouldOnlySeeSelfFunctions() = doBlocking {
        installPackage(TEST_APP_A_PATH)
        try {
            val searchSpec = AppFunctionSearchSpec.Builder().build()

            val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
                searchAppFunctions(searchSpec).associateBy { it.name }

            assertThat(resultAppFunctionsByName.keys)
                .containsExactly(
                    FunctionName.SAME_PACKAGE_THROW_EXCEPTION,
                    FunctionName.SAME_PACKAGE_UNCAUGHT_CLIENT_EXCEPTION,
                    FunctionName.SAME_PACKAGE_ADD_INVOKE_CALLBACK_TWICE,
                    FunctionName.SAME_PACKAGE_DYNAMIC_CONCAT_STRINGS,
                    FunctionName.SAME_PACKAGE_DYNAMIC_LONG_RUNNING,
                    FunctionName.SAME_PACKAGE_ADD_ASYNC,
                    FunctionName.SAME_PACKAGE_NOT_INVOKE_CALLBACK,
                    FunctionName.SAME_PACKAGE_RUN_FOREVER,
                    FunctionName.SAME_PACKAGE_ADD,
                    FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT,
                    FunctionName.SAME_PACKAGE_NO_OP,
                    FunctionName.SAME_PACKAGE_KILL,
                    FunctionName.SAME_PACKAGE_LONG_RUNNING_FUNCTION,
                    FunctionName.SAME_PACKAGE_NO_SCHEMA,
                )
        } finally {
            uninstallPackage(TEST_APP_A_PKG)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureHasPermission(Manifest.permission.QUERY_ALL_PACKAGES)
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
                "when using Bedstead"
    )
    fun searchAppFunctions_withoutExecuteAppFunctionPermission_shouldOnlySeeSelfFunctions() =
        doBlocking {
            installPackage(TEST_APP_A_PATH)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
                    searchAppFunctions(searchSpec).associateBy { it.name }

                assertThat(resultAppFunctionsByName.keys)
                    .containsExactly(
                        FunctionName.SAME_PACKAGE_THROW_EXCEPTION,
                        FunctionName.SAME_PACKAGE_UNCAUGHT_CLIENT_EXCEPTION,
                        FunctionName.SAME_PACKAGE_ADD_INVOKE_CALLBACK_TWICE,
                        FunctionName.SAME_PACKAGE_DYNAMIC_LONG_RUNNING,
                        FunctionName.SAME_PACKAGE_ADD_ASYNC,
                        FunctionName.SAME_PACKAGE_NOT_INVOKE_CALLBACK,
                        FunctionName.SAME_PACKAGE_DYNAMIC_CONCAT_STRINGS,
                        FunctionName.SAME_PACKAGE_RUN_FOREVER,
                        FunctionName.SAME_PACKAGE_ADD,
                        FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT,
                        FunctionName.SAME_PACKAGE_NO_OP,
                        FunctionName.SAME_PACKAGE_KILL,
                        FunctionName.SAME_PACKAGE_LONG_RUNNING_FUNCTION,
                        FunctionName.SAME_PACKAGE_NO_SCHEMA,
                    )
            } finally {
                uninstallPackage(TEST_APP_A_PKG)
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
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
                "when using Bedstead"
    )
    fun searchAppFunctions_noPermissions_shouldReturnEmpty_whenTargetOtherPackages() =
        doBlocking {
            installPackage(TEST_APP_A_PATH)
            try {
                val searchSpec =
                    AppFunctionSearchSpec.Builder()
                        .setPackageNames(listOf(TEST_HELPER_DYNAMIC_SCHEMA_PKG))
                        .build()

                val resultAppFunctions = searchAppFunctions(searchSpec)

                assertThat(resultAppFunctions).isEmpty()
            } finally {
                uninstallPackage(TEST_APP_A_PKG)
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
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default " +
                "when using Bedstead"
    )
    fun searchAppFunctions_noPermissions_shouldReturnEmpty_whenTargetOtherPackagesFunctions() =
        doBlocking {
            installPackage(TEST_APP_A_PATH)
            try {
                val searchSpec =
                    AppFunctionSearchSpec.Builder()
                        .setFunctionNames(listOf(FunctionName.ENABLED_BY_DEFAULT))
                        .build()

                val resultAppFunctions = searchAppFunctions(searchSpec)

                assertThat(resultAppFunctions).isEmpty()
            } finally {
                uninstallPackage(TEST_APP_A_PKG)
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
            installPackage(TEST_APP_A_PATH)
            try {
                val searchSpec = AppFunctionSearchSpec.Builder().build()

                val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
                val functionsGroupByPackage = results.associateBy { it.packageMetadata.packageName }

                assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
            } finally {
                uninstallPackage(TEST_APP_A_PKG)
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
        installPackage(TEST_APP_A_PATH)
        try {
            val searchSpec = AppFunctionSearchSpec.Builder().build()

            val results: List<AppFunctionMetadata> = searchAppFunctions(searchSpec)
            val functionsGroupByPackage = results.associateBy { it.packageMetadata.packageName }

            assertThat(functionsGroupByPackage.keys).containsAnyIn(getVisiblePackages())
        } finally {
            uninstallPackage(TEST_APP_A_PKG)
        }
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
                .setPackageNames(listOf(TEST_HELPER_DYNAMIC_SCHEMA_PKG))
                .setFunctionNames(listOf(FunctionName.ENABLED_BY_DEFAULT))
                .setSchemaCategory(schemaMetadata.category)
                .setSchemaName(schemaMetadata.name)
                .setMinSchemaVersion(schemaMetadata.version)
                .build()

        val resultList = searchAppFunctions(searchSpec)

        assertThat(resultList).hasSize(1)
        val resultMetadata = resultList[0]
        assertAppFunctionMetadataEquals(resultMetadata, FunctionMetadata.ENABLED_BY_DEFAULT)
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
                .setPackageNames(listOf(CURRENT_PKG, TEST_HELPER_DYNAMIC_SCHEMA_PKG))
                .setFunctionNames(
                    listOf(FunctionName.ENABLED_BY_DEFAULT, FunctionName.DISABLED_BY_DEFAULT)
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(FunctionName.ENABLED_BY_DEFAULT, FunctionName.DISABLED_BY_DEFAULT)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.DISABLED_BY_DEFAULT]!!,
            FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
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
                .setPackageNames(listOf(CURRENT_PKG))
                .setFunctionNames(
                    listOf(FunctionName.SAME_PACKAGE_ADD, FunctionName.ENABLED_BY_DEFAULT)
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys).containsExactly(FunctionName.SAME_PACKAGE_ADD)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.SAME_PACKAGE_ADD]!!,
            FunctionMetadata.SAME_PACKAGE_ENABLED_BY_DEFAULT,
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
                .setPackageNames(listOf(CURRENT_PKG))
                .setFunctionNames(
                    // Both functions belongs to a different package
                    listOf(FunctionName.ENABLED_BY_DEFAULT, FunctionName.DISABLED_BY_DEFAULT)
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
            .containsExactly(FunctionName.ENABLED_BY_DEFAULT, FunctionName.HIGH_SCHEMA_VERSION)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.HIGH_SCHEMA_VERSION]!!,
            FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
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
            .containsExactly(FunctionName.ENABLED_BY_DEFAULT, FunctionName.HIGH_SCHEMA_VERSION)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.HIGH_SCHEMA_VERSION]!!,
            FunctionMetadata.HIGH_SCHEMA_VERSION,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
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

        assertThat(resultAppFunctionsByName.keys).containsExactly(FunctionName.HIGH_SCHEMA_VERSION)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.HIGH_SCHEMA_VERSION]!!,
            FunctionMetadata.HIGH_SCHEMA_VERSION,
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
                .setPackageNames(listOf(TEST_HELPER_DYNAMIC_SCHEMA_PKG))
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactlyElementsIn(
                HELPER_PACKAGE_FUNCTIONS
            )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.DISABLED_BY_DEFAULT]!!,
            FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.HIGH_SCHEMA_VERSION]!!,
            FunctionMetadata.HIGH_SCHEMA_VERSION,
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
                    listOf(FunctionName.ENABLED_BY_DEFAULT, FunctionName.SAME_PACKAGE_ADD)
                )
                .build()

        val resultAppFunctionsByName: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(searchSpec).associateBy { it.name }

        assertThat(resultAppFunctionsByName.keys)
            .containsExactly(FunctionName.ENABLED_BY_DEFAULT, FunctionName.SAME_PACKAGE_ADD)
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.SAME_PACKAGE_ADD]!!,
            FunctionMetadata.SAME_PACKAGE_ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            resultAppFunctionsByName[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
        )
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#searchAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun searchAppFunctions_changeEnabledState_reflectsInSearchResult() = doBlocking {
        val searchSpec =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    listOf(
                        FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT,
                        FunctionName.SAME_PACKAGE_ADD,
                    )
                )
                .build()
        var resultsByFunctionName = searchAppFunctions(searchSpec).associateBy { it.name }
        assertThat(
                resultsByFunctionName[FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT]!!.isEnabled
            )
            .isFalse()
        assertThat(resultsByFunctionName[FunctionName.SAME_PACKAGE_ADD]!!.isEnabled).isTrue()

        try {
            setAppFunctionEnabled(
                mManager,
                FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            setAppFunctionEnabled(
                mManager,
                FunctionName.SAME_PACKAGE_ADD.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            resultsByFunctionName = searchAppFunctions(searchSpec).associateBy { it.name }
            assertThat(
                    resultsByFunctionName[FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT]!!
                        .isEnabled
                )
                .isTrue()
            assertThat(resultsByFunctionName[FunctionName.SAME_PACKAGE_ADD]!!.isEnabled).isFalse()
        } finally {
            // Reset back to default
            setAppFunctionEnabled(
                mManager,
                FunctionName.SAME_PACKAGE_ADD_DISABLED_BY_DEFAULT.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            setAppFunctionEnabled(
                mManager,
                FunctionName.SAME_PACKAGE_ADD.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
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
        installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
        installExistingPackageAsUser(TEST_HELPER_DYNAMIC_SCHEMA_PKG, secondaryUser)
        retryAssert {
            runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(TEST_HELPER_DYNAMIC_SCHEMA_PKG)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(TEST_HELPER_DYNAMIC_SCHEMA_PKG)
            }
        }
        runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
            mManager =
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
        installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
        installExistingPackageAsUser(TEST_HELPER_DYNAMIC_SCHEMA_PKG, secondaryUser)
        retryAssert {
            assertThat(
                    getAllStaticMetadataPackages(
                        context.createContextAsUser(secondaryUser.userHandle(), 0)
                    )
                )
                .contains(TEST_HELPER_DYNAMIC_SCHEMA_PKG)
            assertThat(
                    getAllRuntimeMetadataPackages(
                        context.createContextAsUser(secondaryUser.userHandle(), 0)
                    )
                )
                .contains(TEST_HELPER_DYNAMIC_SCHEMA_PKG)
        }
        mManager =
            context
                .createContextAsUser(secondaryUser.userHandle(), 0)
                .getSystemService(AppFunctionManager::class.java)

        val result: Map<AppFunctionName, AppFunctionMetadata> =
            searchAppFunctions(
                    AppFunctionSearchSpec.Builder()
                        .setPackageNames(
                            listOf(
                                TEST_HELPER_PKG, // Does not installed on secondary user
                                TEST_HELPER_DYNAMIC_SCHEMA_PKG,
                            )
                        )
                        .build()
                )
                .associateBy { it.name }

        assertThat(result.keys)
            .containsExactlyElementsIn(
                HELPER_PACKAGE_FUNCTIONS
            )
        assertAppFunctionMetadataEquals(
            result[FunctionName.ENABLED_BY_DEFAULT]!!,
            FunctionMetadata.ENABLED_BY_DEFAULT,
        )
        assertAppFunctionMetadataEquals(
            result[FunctionName.DISABLED_BY_DEFAULT]!!,
            FunctionMetadata.DISABLED_BY_DEFAULT_NO_SCHEMA,
        )
        assertAppFunctionMetadataEquals(
            result[FunctionName.HIGH_SCHEMA_VERSION]!!,
            FunctionMetadata.HIGH_SCHEMA_VERSION,
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
        retryAssert { getAllStaticMetadataPackages(context).contains(TEST_APP_A_PKG) }
        runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
            retryAssert { assertThat(getAllRuntimeMetadataPackages()).contains(TEST_APP_A_PKG) }
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
        mManager.searchAppFunctions(
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
        assertThat(actual.isEnabled).isEqualTo(expected.isEnabled)
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
        // TODO(b/446132791): Enable GD comparison once the top-level documents were included
        //        val clearedActualGd = sanitizeGenericDocument(actual.metadataDocument)
        //        val expectedGd = sanitizeGenericDocument(expected.metadataDocument)
        //        assertThat(clearedActualGd).isEqualTo(expectedGd)
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_APP_ROOT_FOLDER: String = "/data/local/tmp/cts/appfunctions/"
        const val TEST_APP_A_PATH: String =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk"
        const val TEST_APP_A_PKG: String = "com.android.cts.appsearch.indexertestapp.a"
    }
}
