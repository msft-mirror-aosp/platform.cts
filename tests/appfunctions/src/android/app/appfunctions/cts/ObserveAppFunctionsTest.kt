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
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionObservation
import android.app.appfunctions.AppFunctionObserver
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.cts.AppFunctionActivityScopedRegistrationTest.Companion.REGISTRATION_ACTIVITY
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.UpdatableHelperApp
import android.app.appfunctions.testutils.AppFunctionUtils.assertAppFunctionPackageMetadataEquals
import android.app.appfunctions.testutils.AppFunctionUtils.assertFunctionEnabledState
import android.app.appfunctions.testutils.AppFunctionUtils.getAllAppFunctionPackages
import android.app.appfunctions.testutils.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.testutils.AppFunctionUtils.installPackage
import android.app.appfunctions.testutils.AppFunctionUtils.searchAppFunctions
import android.app.appfunctions.testutils.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.testutils.AppFunctionUtils.setAppFunctionEnabledRemote
import android.app.appfunctions.testutils.AppFunctionUtils.uninstallPackage
import android.app.appfunctions.testutils.AppFunctionUtils.uninstallPackageAsUser
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.freezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.CtsTestUtil.safeRetryAssert
import android.app.appfunctions.testutils.CtsTestUtil.safeUnfreezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.unfreezeProcess
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.ITestAppFunctionProxyManagerService
import android.app.appfunctions.testutils.ITestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionProxyManagerService
import android.app.appfunctions.testutils.TestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS,
    Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2,
)
class ObserveAppFunctionsTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(DynamicRegistrationActivity::class.java)

    @get:Rule val serviceTestRule = ServiceTestRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    private lateinit var activityAppFunctionManager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.CREATED)
        activityScenarioRule.scenario.onActivity { activity ->
            this@ObserveAppFunctionsTest.activityAppFunctionManager = activity.manager
        }

        uninstallPackage(UpdatableHelperApp.PACKAGE_NAME, context, checkIndexation = true)

        TestAppFunctionServiceLifecycleReceiver.reset()
        manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
    }

    @After
    fun cleanup() = doBlocking {
        uninstallPackage(UpdatableHelperApp.PACKAGE_NAME, context, checkIndexation = true)
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @EnsureDoesNotHavePermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
    )
    fun packageInstalled_withExecuteAndQueryAllPermissions_shouldSeeUpdate() = doBlocking {
        assertPackageInstallationNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
    )
    @RequireRootInstrumentation("To remove EXECUTE_APP_FUNCTIONS which is granted in manifest")
    fun packageInstalled_withDiscoverAndQueryAllPermissions_shouldSeeUpdate() = doBlocking {
        assertPackageInstallationNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation("To remove EXECUTE_APP_FUNCTIONS which is granted in manifest")
    fun packageInstalled_withExecuteSystemAndQueryAllPermissions_shouldSeeUpdate() = doBlocking {
        assertPackageInstallationNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation("To remove EXECUTE_APP_FUNCTIONS which is granted in manifest")
    fun packageInstalled_withoutAnyPermissions_shouldNotSeeUpdates() = doBlocking {
        assertPackageInstallationNotNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun packageInstalled_withExecutePermissionOnly_shouldNotSeeUpdate() = doBlocking {
        assertPackageInstallationNotNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun packageInstalled_withDiscoverPermissionOnly_shouldNotSeeUpdate() = doBlocking {
        assertPackageInstallationNotNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    @EnsureDoesNotHavePermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun packageInstalled_withExecuteSystemPermissionOnly_shouldNotSeeUpdate() = doBlocking {
        assertPackageInstallationNotNotified()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUpdated_topLevelDocumentsAdded_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.NO_TOP_LEVEL_DOCS,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )

            // Static metadata (top-level documents) are updated for
            // DynamicSchemaHelperApp.PACKAGE_NAME.
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)

                assertExactPackageMetadata(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.PackageMetadata.DYNAMIC_SCHEMA_PACKAGE_METADATA,
                )
            }
        } finally {
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUpdated_topLevelDocumentsRemoved_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )

            // Static metadata (top-level documents) are updated for
            // DynamicSchemaHelperApp.PACKAGE_NAME.
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.NO_TOP_LEVEL_DOCS,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)

                assertExactPackageMetadata(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.PackageMetadata.EMPTY_PACKAGE_METADATA,
                )
            }
        } finally {
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUpdated_functionAdded_callsOnAppFunctionsChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.ONE_FUNCTION_REMOVED,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )

            // Install app version with additional function.
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)

                assertPackageHasAtLeastFunctions(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS,
                )
            }
        } finally {
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUpdated_functionRemoved_callsOnAppFunctionsChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )

            // Install app version with one less function.
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.ONE_FUNCTION_REMOVED,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)

                assertPackageHasAtLeastFunctions(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.FunctionNames.APK_WITH_ONE_FUNCTION_REMOVED_FUNCTIONS,
                )
            }
        } finally {
            awaitApkReinstall(
                context,
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                observer,
            )
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUpdated_changedSchema_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            installPackage(
                UpdatableHelperApp.ApkPaths.NO_FUNCTIONS,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = false, // No functions to index
            )
            // Ensure that the test only finish when the debounced callback is
            // triggered. To avoid affecting other tests.
            safeRetryAssert {
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)
            }
            observer.clearHistory()

            // Static metadata is updated for UpdatableHelperApp.PACKAGE_NAME.
            installPackage(
                UpdatableHelperApp.ApkPaths.STATIC_ONLY_FUNCTIONS,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)

                assertPackageHasAtLeastFunctions(
                    UpdatableHelperApp.PACKAGE_NAME,
                    setOf(
                        UpdatableHelperApp.FunctionNames.PRINT_2,
                        UpdatableHelperApp.FunctionNames.PRINT_3,
                    ),
                )
            }
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageUninstalled_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

            uninstallPackage(UpdatableHelperApp.PACKAGE_NAME, context, checkIndexation = true)

            retryAssert(maxIntervals = 40) {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory).isEmpty()
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)

                assertPackageHasNoAppFunctions(UpdatableHelperApp.PACKAGE_NAME)
            }
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun changeEnabledState_withExecuteAndQueryAllPermission_shouldSeeAllUpdates() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun changeEnabledState_withDiscoverAndQueryAllPermission_shouldSeeAllUpdates() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun changeEnabledState_withExecuteSystemAndQueryAllPermission_shouldSeeAllUpdates() =
        doBlocking {
            val observer = TestClientObserver()
            var observation: AppFunctionObservation? = null
            try {
                observation = observeAppFunctions(observer)

                assertChangedEnabledStateNotified(
                    observer,
                    CtsApp.FunctionNames.ADD,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
                )
                assertChangedEnabledStateNotified(
                    observer,
                    DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
                )
            } finally {
                observation?.cancel()
                resetEnabledStates()
            }
        }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun changeEnabledState_withExecutePermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            assertSeesUpdateAfterVisibilityGranted(observer)
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun changeEnabledState_withDiscoverPermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            assertSeesUpdateAfterVisibilityGranted(observer)
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    @RequireRootInstrumentation(
        "Require to remove QUERY_ALL_PACKAGES permission that is granted by default when" +
            "using Bedstead"
    )
    @Ignore("b/491066437")
    fun changeEnabledState_withExecuteSystemPermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            assertSeesUpdateAfterVisibilityGranted(observer)
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @RequireRootInstrumentation("To remove EXECUTE_APP_FUNCTIONS which is granted in manifest")
    fun changeEnabledState_withoutAnyPermission_shouldOnlySeeSelfUpdate() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertChangedEnabledStateNotified(
                observer,
                CtsApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun registerDynamicFunction_callsOnAppFunctionsChangedIfEnabled() = doBlocking {
        var observation: AppFunctionObservation? = null
        val observer = TestClientObserver()
        observation = observeAppFunctions(observer)

        val registrationService = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        try {
            assertThat(registrationService.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)).isTrue()

            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
            }
        } finally {
            observation?.cancel()
            registrationService.safeUnregister(CONCAT_STRINGS_FUNCTION_ID)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun unregisterDynamicFunction_callsOnAppFunctionsChangedIfEnabled() = doBlocking {
        var observation: AppFunctionObservation? = null
        val registrationService = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        try {
            assertThat(registrationService.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)).isTrue()

            val observer = TestClientObserver()

            observation = observeAppFunctions(observer)

            assertThat(registrationService.unregisterAppFunction(CONCAT_STRINGS_FUNCTION_ID))
                .isTrue()
            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
            }
        } finally {
            observation?.cancel()
            registrationService.safeUnregister(CONCAT_STRINGS_FUNCTION_ID)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    fun packageInstalled_crossUser_failsWithoutPermission() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        try {
            installExistingPackageAsUser(
                CtsApp.PACKAGE_NAME,
                secondaryUser,
                context,
                checkIndexation = true,
            )
            runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
                manager =
                    context
                        .createContextAsUser(secondaryUser.userHandle(), 0)
                        .getSystemService(AppFunctionManager::class.java)
            }

            assertFailsWith<SecurityException>(
                "Expected observeAppFunctions to throw a security exception without permission"
            ) {
                observeAppFunctions(TestClientObserver())
            }
        } finally {
            uninstallPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
    )
    fun packageInstalled_crossUser_seesOnlyCurrentUserUpdates() = doBlocking {
        var observation: AppFunctionObservation? = null
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        installExistingPackageAsUser(
            CtsApp.PACKAGE_NAME,
            secondaryUser,
            context,
            checkIndexation = true,
        )
        uninstallPackageAsUser(UpdatableHelperApp.PACKAGE_NAME, secondaryUser)
        uninstallPackage(UpdatableHelperApp.PACKAGE_NAME, context, checkIndexation = true)
        retryAssert {
            assertThat(
                    context
                        .createContextAsUser(secondaryUser.userHandle(), 0)
                        .packageManager
                        .getInstalledPackages(0)
                        .map { it.packageName }
                )
                .doesNotContain(UpdatableHelperApp.PACKAGE_NAME)
            assertThat(context.packageManager.getInstalledPackages(0).map { it.packageName })
                .doesNotContain(UpdatableHelperApp.PACKAGE_NAME)
        }
        manager =
            context
                .createContextAsUser(secondaryUser.userHandle(), 0)
                .getSystemService(AppFunctionManager::class.java)

        val observer = TestClientObserver()
        try {
            observation = observeAppFunctions(observer)
            installExistingPackageAsUser(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                secondaryUser,
                context,
                checkIndexation = true,
            )
            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory).isEmpty()
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.PACKAGE_NAME)

                assertPackageHasAppFunctions(DynamicSchemaHelperApp.PACKAGE_NAME)
            }
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun packageInstalled_afterObservationCancelled_doesNotNotify() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

            observation.cancel()
            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            assertThat(observer.updatedPackagesHistory).isEmpty()
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun changeEnabledState_afterObservationCancelled_doesNotNotify() = doBlocking {
        var observation: AppFunctionObservation? = null
        try {
            val observer = TestClientObserver()

            observation = observeAppFunctions(observer)
            observation.cancel()

            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                    manager,
                    isEnabled = true,
                )
            }

            assertThat(observer.updatedPackagesHistory).isEmpty()
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()
        } finally {
            observation?.cancel()
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun freezeProcessWithDynamicAppFunctionRegistered_shouldCallOnAppFunctionsChanged() =
        doBlocking {
            val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
            var observation: AppFunctionObservation? = null
            val observer = TestClientObserver()

            try {
                service.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)
                observation = observeAppFunctions(observer)

                freezeProcess(
                    context,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.REGISTRATION_SERVICE_PROCESS_NAME,
                )

                retryAssert {
                    assertThat(observer.updatedPackagesHistory).isEmpty()
                    assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                    assertThat(observer.updatedFunctionStatesHistory.flatten())
                        .contains(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
                }
            } finally {
                observation?.cancel()
                safeUnfreezeProcess(
                    context,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.REGISTRATION_SERVICE_PROCESS_NAME,
                )
            }
        }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun unfreezeProcessWithDynamicAppFunctionRegistered_shouldCallOnAppFunctionsChanged() =
        doBlocking {
            val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
            var observation: AppFunctionObservation? = null
            val observer = TestClientObserver()

            try {
                service.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)
                freezeProcess(
                    context,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.REGISTRATION_SERVICE_PROCESS_NAME,
                )
                observation = observeAppFunctions(observer)

                unfreezeProcess(
                    context,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.REGISTRATION_SERVICE_PROCESS_NAME,
                )

                retryAssert {
                    assertThat(observer.updatedPackagesHistory).isEmpty()
                    assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                    assertThat(observer.updatedFunctionStatesHistory.flatten())
                        .contains(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
                }
            } finally {
                observation?.cancel()
                safeUnfreezeProcess(
                    context,
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.REGISTRATION_SERVICE_PROCESS_NAME,
                )
            }
        }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun killProcessWithDynamicAppFunctionRegistered_shouldCallOnAppFunctionsChanged() = doBlocking {
        val service = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        var observation: AppFunctionObservation? = null
        val observer = TestClientObserver()

        try {
            service.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)
            observation = observeAppFunctions(observer)

            ShellCommand.builder("am force-stop ${DynamicSchemaHelperApp.PACKAGE_NAME}").execute()

            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
            }
        } finally {
            observation?.cancel()
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun observeAppFunctions_shouldNotReceiveNotificationWhileFreeze() = doBlocking {
        val testObserver = TestClientObserver()
        var testObservation = observeAppFunctions(testObserver)
        val proxyManagerService = bindToProxyManagerService()
        proxyManagerService.startTestObserver()
        freezeProcess(context, CtsApp.PACKAGE_NAME, CtsApp.PROXY_MANAGER_PROCESS_NAME)

        try {
            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                    manager,
                    isEnabled = true,
                )
            }
            retryAssert {
                assertThat(testObserver.updatedPackagesHistory.flatten())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)
                assertThat(testObserver.updatedFunctionStatesHistory.flatten())
                    .contains(DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT)
            }
            testObserver.clearHistory()
            unfreezeProcess(context, CtsApp.PACKAGE_NAME, CtsApp.PROXY_MANAGER_PROCESS_NAME)
            // Since there is no signal available to know whether the unfreeze
            // notification has been dispatched, we need to wait for a period of
            // time before start asserting.
            delay(LONG_DELAY_MS)

            // Observer registered in frozen process should receive notification
            // that all packages contains AppFunctions have changed.
            val history = proxyManagerService.getTestObserverHistory()
            assertThat(history.changedPackageNameHistory).hasSize(1)
            assertThat(history.changedPackageNameHistory[0]!!)
                .containsExactlyElementsIn(getAllAppFunctionPackages(context))
            assertThat(history.changedFunctionNamesHistory).isEmpty()
            // Observer that was not frozen should not receive any notification.
            assertThat(testObserver.updatedPackagesHistory).isEmpty()
            assertThat(testObserver.updatedFunctionStatesHistory).isEmpty()
        } finally {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, testObserver)
            testObservation.cancel()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun onPackageDataCleared_initialRuntimeStateDefault_doesNotNotify() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        val targetPackage = DynamicSchemaHelperApp.PACKAGE_NAME
        val functionEnabledByDefault = DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT

        try {
            setAppFunctionEnabledRemote(
                targetPackage,
                functionEnabledByDefault.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )

            observation = observeAppFunctions(observer)
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()

            ShellCommand.builder(
                    "pm clear --user ${TestApis.users().current().id()} $targetPackage"
                )
                .execute()

            // Trigger an unrelated notification wait for it to be dispatched, to ensure preceding
            // notifications, if any, have already been dispatched.
            val sentinelFunction = CtsApp.FunctionNames.ADD
            manager.setAppFunctionEnabled(
                sentinelFunction.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            retryAssert {
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(sentinelFunction)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .doesNotContain(functionEnabledByDefault)
            }
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun onPackageDataCleared_initialRuntimeStateNotDefault_notifiesObserver() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        val targetPackage = DynamicSchemaHelperApp.PACKAGE_NAME
        val functionEnabledByDefault = DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT
        val functionDisabledByDefault = DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT

        try {
            setAppFunctionEnabledRemote(
                targetPackage,
                functionEnabledByDefault.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            setAppFunctionEnabledRemote(
                targetPackage,
                functionDisabledByDefault.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )

            observation = observeAppFunctions(observer)
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()

            ShellCommand.builder(
                    "pm clear --user ${TestApis.users().current().id()} $targetPackage"
                )
                .execute()

            retryAssert {
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .containsAtLeast(functionDisabledByDefault, functionEnabledByDefault)
            }
        } finally {
            observation?.cancel()
            resetEnabledStates()
        }
    }

    private fun observeAppFunctions(observer: TestClientObserver): AppFunctionObservation {
        return manager.observeAppFunctions(context.mainExecutor, observer)
    }

    private suspend fun assertPackageInstallationNotified() {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            retryAssert {
                assertThat(observer.updatedPackagesHistory).hasSize(1)
                assertThat(observer.updatedPackagesHistory.flatten())
                    .contains(UpdatableHelperApp.PACKAGE_NAME)

                assertPackageHasAtLeastFunctions(
                    UpdatableHelperApp.PACKAGE_NAME,
                    setOf(UpdatableHelperApp.FunctionNames.PRINT_1),
                )
            }
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    private suspend fun assertPackageInstallationNotNotified() {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)

            installPackage(
                UpdatableHelperApp.ApkPaths.BASE_APP,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            // TODO(b/478810311): Use a privileged observer from a different package to ensure that
            //  observer notifications have already been dispatch by the time we check for
            //  no-notification.
            repeat(5) {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                delay(1000)
            }
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    private suspend fun assertChangedEnabledStateNotified(
        observer: TestClientObserver,
        appFunctionName: AppFunctionName,
        @AppFunctionManager.EnabledState state: Int,
    ) {
        observer.clearHistory()

        if (appFunctionName.packageName == context.packageName) {
            manager.setAppFunctionEnabled(appFunctionName.functionIdentifier, state)
        } else {
            setAppFunctionEnabledRemote(
                appFunctionName.packageName,
                appFunctionName.functionIdentifier,
                state,
            )
        }

        retryAssert {
            assertFunctionEnabledState(
                appFunctionName.packageName,
                appFunctionName.functionIdentifier,
                manager,
                isEnabled = state == AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
        }

        retryAssert {
            assertThat(observer.updatedFunctionStatesHistory.flatten()).contains(appFunctionName)
        }
    }

    private suspend fun assertChangedEnabledStateNotNotified(
        observer: TestClientObserver,
        appFunctionName: AppFunctionName,
        @AppFunctionManager.EnabledState state: Int,
    ) {
        observer.clearHistory()

        if (appFunctionName.packageName == context.packageName) {
            manager.setAppFunctionEnabled(appFunctionName.functionIdentifier, state)
        } else {
            setAppFunctionEnabledRemote(
                appFunctionName.packageName,
                appFunctionName.functionIdentifier,
                state,
            )
        }

        retryAssert {
            assertFunctionEnabledState(
                appFunctionName.packageName,
                appFunctionName.functionIdentifier,
                manager,
                isEnabled = state == AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
        }

        // TODO(b/478810311): Use a privileged observer from a different package to ensure that
        //  observer notifications have already been dispatch by the time we check for
        //  no-notification.
        repeat(5) {
            assertThat(observer.updatedFunctionStatesHistory.flatten())
                .doesNotContain(appFunctionName)
            delay(1000)
        }
    }

    private suspend fun assertSeesUpdateAfterVisibilityGranted(observer: TestClientObserver) {
        val remoteFunction = DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT
        // Grant implicit visibility by starting activity.
        TestApis.context()
            .instrumentedContext()
            .startActivity(
                Intent().apply {
                    component =
                        ComponentName(DynamicSchemaHelperApp.PACKAGE_NAME, REGISTRATION_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

        observer.clearHistory()

        setAppFunctionEnabledRemote(
            remoteFunction.packageName,
            remoteFunction.functionIdentifier,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )

        retryAssert {
            assertFunctionEnabledState(
                remoteFunction.packageName,
                remoteFunction.functionIdentifier,
                manager,
                isEnabled = true,
            )
        }

        retryAssert {
            assertThat(observer.updatedFunctionStatesHistory.flatten()).contains(remoteFunction)
        }
    }

    private suspend fun resetEnabledStates() {
        manager.setAppFunctionEnabled(
            CtsApp.FunctionNames.ADD.functionIdentifier,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        setAppFunctionEnabledRemote(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT.functionIdentifier,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        setAppFunctionEnabledRemote(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT.functionIdentifier,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        TestApis.activities().clearAllActivities()
    }

    private suspend fun assertPackageHasNoAppFunctions(packageName: String) {
        val searchSpec = AppFunctionSearchSpec.Builder().setPackageNames(setOf(packageName)).build()
        val afMetadata = manager.searchAppFunctions(searchSpec)
        assertThat(afMetadata).isEmpty()
    }

    private suspend fun assertPackageHasAppFunctions(packageName: String) {
        val searchSpec = AppFunctionSearchSpec.Builder().setPackageNames(setOf(packageName)).build()
        val afMetadataByPackage =
            manager.searchAppFunctions(searchSpec).groupBy { it.name.packageName }
        assertThat(afMetadataByPackage.keys).contains(packageName)
    }

    private suspend fun assertPackageHasAtLeastFunctions(
        packageName: String,
        appFunctions: Set<AppFunctionName>,
    ) {
        val searchSpec = AppFunctionSearchSpec.Builder().setPackageNames(setOf(packageName)).build()
        val afMetadataByFunctionName = manager.searchAppFunctions(searchSpec).groupBy { it.name }
        assertThat(afMetadataByFunctionName.keys).containsAtLeastElementsIn(appFunctions)
    }

    private suspend fun assertExactPackageMetadata(
        packageName: String,
        expectedPackageMetadata: AppFunctionPackageMetadata,
    ) {
        val searchSpec = AppFunctionSearchSpec.Builder().setPackageNames(setOf(packageName)).build()
        val searchResults = manager.searchAppFunctions(searchSpec)
        assertAppFunctionPackageMetadataEquals(
            searchResults.first().packageMetadata,
            expectedPackageMetadata,
        )
    }

    private suspend fun awaitApkUninstall(
        context: Context,
        packageName: String,
        observer: TestClientObserver,
    ) {
        observer.clearHistory()
        uninstallPackage(packageName, context, checkIndexation = true)
        // Ensure that the test only finish when the debounced callback is
        // triggered. To avoid affecting other tests.
        safeRetryAssert {
            assertThat(observer.updatedPackagesHistory).hasSize(1)
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()
            assertThat(observer.updatedPackagesHistory.flatten()).contains(packageName)
        }
        observer.clearHistory()
    }

    private suspend fun awaitApkReinstall(
        context: Context,
        apkPath: String,
        packageName: String,
        observer: TestClientObserver,
    ) {
        observer.clearHistory()
        // Reinstall base app to restore state for subsequent tests
        installPackage(apkPath, packageName, context, checkIndexation = true)
        // Ensure that the test only finish when the debounced callback is
        // triggered. To avoid affecting other tests.
        safeRetryAssert {
            assertThat(observer.updatedPackagesHistory).hasSize(1)
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()
            assertThat(observer.updatedPackagesHistory.flatten()).contains(packageName)
        }
        observer.clearHistory()
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

    private fun bindToProxyManagerService(): ITestAppFunctionProxyManagerService {
        val serviceIntent = Intent(context, TestAppFunctionProxyManagerService::class.java)
        val binder: IBinder = serviceTestRule.bindService(serviceIntent)
        return ITestAppFunctionProxyManagerService.Stub.asInterface(binder)
    }

    private fun ITestAppFunctionRegistrationService.safeUnregister(functionType: String) {
        try {
            unregisterAppFunction(functionType)
        } catch (_: Exception) {}
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    class TestClientObserver : AppFunctionObserver {
        val updatedPackagesHistory: MutableList<List<String>> = mutableListOf()
        val updatedFunctionStatesHistory: MutableList<List<AppFunctionName>> = mutableListOf()

        fun clearHistory() {
            updatedPackagesHistory.clear()
            updatedFunctionStatesHistory.clear()
        }

        override fun onAppFunctionMetadataChanged(packageNames: Set<String>) {
            updatedPackagesHistory.add(packageNames.toList())
        }

        override fun onAppFunctionStatesChanged(appFunctions: Set<AppFunctionName>) {
            updatedFunctionStatesHistory.add(appFunctions.toList())
        }
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val LONG_DELAY_MS = 5000L
    }
}
