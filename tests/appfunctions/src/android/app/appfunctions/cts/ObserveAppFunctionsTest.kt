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
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.UpdatableHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.assertAppFunctionPackageMetadataEquals
import android.app.appfunctions.cts.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.cts.AppFunctionUtils.installPackage
import android.app.appfunctions.cts.AppFunctionUtils.searchAppFunctions
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.cts.AppFunctionUtils.uninstallPackage
import android.app.appfunctions.cts.AppFunctionUtils.uninstallPackageAsUser
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.freezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.CtsTestUtil.safeRetryAssert
import android.app.appfunctions.testutils.CtsTestUtil.safeUnfreezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.unfreezeProcess
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.ITestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.lang.UnsupportedOperationException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// TODO(b/478810311):
//  1. Add granular package visibility tests for each of the 3 relevant permissions.
//  2. Move duplicate test logic into base test methods.
//  3. Add tests for EXECUTE_APP_FUNCTIONS_SYSTEM and DISCOVER_APP_FUNCTIONS.
@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS,
    Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2,
)
class ObserveAppFunctionsTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val serviceTestRule = ServiceTestRule()

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(DynamicRegistrationActivity::class.java)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

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
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @Ignore("b/478851326 - Enable after checking permissions in the observer.")
    fun packageInstalled_noExecuteOrReadPermission_doNotSeeUpdates() = doBlocking {
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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
        Manifest.permission.QUERY_ALL_PACKAGES,
    )
    fun packageInstalled_withAllPermissions_seesAllUpdates() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)

            assertThat(observer.updatedPackagesHistory).isEmpty()

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

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun packageUpdated_topLevelDocumentsAdded_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.NO_TOP_LEVEL_DOCS,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun packageUpdated_topLevelDocumentsRemoved_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun packageUpdated_functionAdded_callsOnAppFunctionsChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            uninstallPackage(DynamicSchemaHelperApp.PACKAGE_NAME, context, checkIndexation = true)
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.ONE_FUNCTION_REMOVED,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun packageUpdated_functionRemoved_callsOnAppFunctionsChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    @Ignore("b/479123842 - Enable after fixing redundant onAppFunctionsChanged callbacks")
    fun packageUpdated_changedSchema_callsOnPackageChanged() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            installPackage(
                UpdatableHelperApp.ApkPaths.NO_FUNCTIONS,
                UpdatableHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = false, // No functions to index
            )
            observation = observeAppFunctions(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()

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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
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
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun changeStaticFunctionEnabledState_callsOnAppFunctionsChanged() = doBlocking {
        var observation: AppFunctionObservation? = null
        try {
            val observer = TestClientObserver()
            // TODO(b/478810311): test with non-root package

            observation = observeAppFunctions(observer)

            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            retryAssert {
                assertThat(
                        isAppFunctionEnabled(
                            CtsApp.PACKAGE_NAME,
                            CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                        )
                    )
                    .isTrue()
                assertThat(
                        isAppFunctionEnabled(
                            CtsApp.PACKAGE_NAME,
                            CtsApp.FunctionNames.ADD.functionIdentifier,
                        )
                    )
                    .isFalse()
            }

            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .containsAtLeastElementsIn(
                        setOf(
                            CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                            CtsApp.FunctionNames.ADD,
                        )
                    )
            }
        } finally {
            observation?.cancel()
            // Reset back to default
            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun registerDynamicFunction_callsOnAppFunctionsChangedIfEnabled() = doBlocking {
        var observation: AppFunctionObservation? = null
        val observer = TestClientObserver()
        // TODO(b/478810311): test with non-root package
        observation = observeAppFunctions(observer)

        var registration: AppFunctionRegistration? = null
        try {
            registration =
                activityAppFunctionManager.registerAppFunction(
                    CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS.functionIdentifier,
                    MoreExecutors.directExecutor(),
                ) { _, _, _ ->
                    throw UnsupportedOperationException("Stub!")
                }

            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
            }
        } finally {
            observation?.cancel()
            registration?.unregister()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#observeAppFunctions"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun unregisterDynamicFunction_callsOnAppFunctionsChangedIfEnabled() = doBlocking {
        var observation: AppFunctionObservation? = null
        var registration: AppFunctionRegistration? = null
        try {
            registration =
                activityAppFunctionManager.registerAppFunction(
                    // TODO(b/478810311): test with non-root package
                    CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS.functionIdentifier,
                    MoreExecutors.directExecutor(),
                ) { _, _, _ ->
                    throw UnsupportedOperationException("Stub!")
                }

            val observer = TestClientObserver()

            observation = observeAppFunctions(observer)

            registration.unregister()
            retryAssert {
                assertThat(observer.updatedPackagesHistory).isEmpty()
                assertThat(observer.updatedFunctionStatesHistory).hasSize(1)
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(CtsApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
            }
        } finally {
            observation?.cancel()
            registration?.unregister()
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
    )
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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
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
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun changeStaticFunctionEnabledState_afterObservationCancelled_doesNotNotify() = doBlocking {
        var observation: AppFunctionObservation? = null
        try {
            val observer = TestClientObserver()
            // TODO(b/478810311): test with non-root package

            observation = observeAppFunctions(observer)
            observation.cancel()

            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            retryAssert {
                assertThat(
                        isAppFunctionEnabled(
                            CtsApp.PACKAGE_NAME,
                            CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
                        )
                    )
                    .isTrue()
            }

            assertThat(observer.updatedPackagesHistory).isEmpty()
            assertThat(observer.updatedFunctionStatesHistory).isEmpty()
        } finally {
            observation?.cancel()
            manager.setAppFunctionEnabled(
                CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT.functionIdentifier,
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
    fun unfreezerocessWithDynamicAppFunctionRegistered_shouldCallOnAppFunctionsChanged() =
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

    private fun observeAppFunctions(observer: TestClientObserver): AppFunctionObservation {
        return manager.observeAppFunctions(context.mainExecutor, observer)
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

    private suspend fun isAppFunctionEnabled(
        targetPackage: String,
        functionIdentifier: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        manager.isAppFunctionEnabled(
            functionIdentifier,
            targetPackage,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
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
    }

    private fun bindToRegistrationService(
        packageName: String
    ): ITestAppFunctionRegistrationService {
        val serviceIntent =
            Intent().apply {
                component =
                    ComponentName(
                        packageName,
                        "android.app.appfunctions.testutils.TestAppFunctionRegistrationService",
                    )
            }
        val binder: IBinder = serviceTestRule.bindService(serviceIntent)
        return ITestAppFunctionRegistrationService.Stub.asInterface(binder)
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    class TestClientObserver : AppFunctionObserver {
        val updatedPackagesHistory: MutableSet<Set<String>> = mutableSetOf()
        val updatedFunctionStatesHistory: MutableSet<Set<AppFunctionName>> = mutableSetOf()

        fun clearHistory() {
            updatedPackagesHistory.clear()
            updatedFunctionStatesHistory.clear()
        }

        override fun onAppFunctionMetadataChanged(packageNames: Set<String>) {
            updatedPackagesHistory.add(packageNames)
        }

        override fun onAppFunctionStatesChanged(appFunctions: Set<AppFunctionName>) {
            updatedFunctionStatesHistory.add(appFunctions)
        }
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
    }
}
