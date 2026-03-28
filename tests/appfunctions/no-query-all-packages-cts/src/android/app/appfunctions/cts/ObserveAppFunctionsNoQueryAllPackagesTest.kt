/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.app.appfunctions.cts.noqueryallpackages

import android.Manifest
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionObservation
import android.app.appfunctions.AppFunctionObserver
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.CtsNoQueryAllApp
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.LegacySchemaHelperApp
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.UpdatableHelperApp
import android.app.appfunctions.testutils.AppFunctionUtils.assertFunctionEnabledState
import android.app.appfunctions.testutils.AppFunctionUtils.installPackage
import android.app.appfunctions.testutils.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.testutils.AppFunctionUtils.setAppFunctionEnabledRemote
import android.app.appfunctions.testutils.AppFunctionUtils.uninstallPackage
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.safeRetryAssert
import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
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
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
class ObserveAppFunctionsNoQueryAllPackagesTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)

        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            awaitApkUninstall(context, LegacySchemaHelperApp.PACKAGE_NAME, observer)
            awaitApkUninstall(context, DynamicSchemaHelperApp.PACKAGE_NAME, observer)
        } finally {
            observation?.cancel()
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
    )
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
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
    )
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
    )
    fun packageInstalled_withExecuteSystemPermissionOnly_shouldNotSeeUpdate() = doBlocking {
        assertPackageInstallationNotNotified()
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
    )
    fun changeEnabledState_withExecutePermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        assertEnabledStateChangeNotifiedForVisiblePackagesOnly()
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
    )
    fun changeEnabledState_withDiscoverPermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        assertEnabledStateChangeNotifiedForVisiblePackagesOnly()
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
    )
    fun changeEnabledState_withExecuteSystemPermissionOnly_shouldSeeVisibleUpdate() = doBlocking {
        assertEnabledStateChangeNotifiedForVisiblePackagesOnly()
    }

    private fun observeAppFunctions(observer: TestClientObserver): AppFunctionObservation {
        return manager.observeAppFunctions(context.mainExecutor, observer)
    }

    private suspend fun assertEnabledStateChangeNotifiedForVisiblePackagesOnly() = doBlocking {
        val observer = TestClientObserver()
        var observation: AppFunctionObservation? = null
        try {
            observation = observeAppFunctions(observer)
            installPackage(
                LegacySchemaHelperApp.APK_PATH,
                LegacySchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )
            installPackage(
                DynamicSchemaHelperApp.ApkPaths.BASE_APP,
                DynamicSchemaHelperApp.PACKAGE_NAME,
                context,
                checkIndexation = true,
            )

            assertChangedEnabledStateNotNotified(
                observer,
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            assertChangedEnabledStateNotified(
                observer,
                CtsNoQueryAllApp.FunctionNames.ADD,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            // Visible by <queries> tag
            assertChangedEnabledStateNotified(
                observer,
                LegacySchemaHelperApp.FunctionNames.ADD_DISABLED_BY_DEFAULT,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
        } finally {
            awaitApkUninstall(context, LegacySchemaHelperApp.PACKAGE_NAME, observer)
            awaitApkUninstall(context, DynamicSchemaHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
            manager.setAppFunctionEnabled(
                CtsNoQueryAllApp.FunctionNames.ADD.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    private suspend fun assertPackageInstallationNotNotified() {
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

            awaitDebounce(observer)
            assertThat(observer.updatedPackagesHistory).isEmpty()
        } finally {
            awaitApkUninstall(context, UpdatableHelperApp.PACKAGE_NAME, observer)
            observation?.cancel()
        }
    }

    private suspend fun assertChangedEnabledStateNotified(
        observer: TestClientObserver,
        appFunctionName: AppFunctionName,
        state: Int,
    ) {
        observer.clearHistory()

        setAppFunctionEnabledRemote(
            appFunctionName.packageName,
            appFunctionName.functionIdentifier,
            state,
        )

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
        state: Int,
    ) {
        observer.clearHistory()

        setAppFunctionEnabledRemote(
            appFunctionName.packageName,
            appFunctionName.functionIdentifier,
            state,
        )

        retryAssert {
            assertFunctionEnabledState(
                appFunctionName.packageName,
                appFunctionName.functionIdentifier,
                manager,
                isEnabled = state == AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
        }

        awaitDebounce(observer)
        assertThat(observer.updatedFunctionStatesHistory.flatten()).doesNotContain(appFunctionName)
    }

    // Triggers an unrelated notification and waits for it to be dispatched, to ensure preceding
    // notifications, if any, have already been dispatched.
    private suspend fun awaitDebounce(observer: TestClientObserver) {
        val sentinelFunction = CtsNoQueryAllApp.FunctionNames.ADD
        try {
            setAppFunctionEnabledRemote(
                sentinelFunction.packageName,
                sentinelFunction.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            retryAssert {
                assertThat(observer.updatedFunctionStatesHistory.flatten())
                    .contains(sentinelFunction)
            }
        } finally {
            setAppFunctionEnabledRemote(
                sentinelFunction.packageName,
                sentinelFunction.functionIdentifier,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
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
            assertThat(observer.updatedPackagesHistory.flatten()).contains(packageName)
        }
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

        private fun uninstallPackage(packageName: String) {
            SystemUtil.runShellCommand("pm uninstall $packageName")
        }
    }
}
