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
import android.app.appfunctions.AppFunctionState
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.installExistingPackageAsUser
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabledRemote
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.ConcatStrings.Companion.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.ACTION_REGISTER_APP_FUNCTION
import android.app.appfunctions.testutils.FunctionType
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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(BedsteadJUnit4::class)
class GetAppFunctionStatesTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val serviceTestRule = ServiceTestRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    private lateinit var internalLaunchIntent: Intent

    @Before
    fun setup() = doBlocking {
        setTestPageSize(2)

        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@GetAppFunctionStatesTest.manager = manager

        // Doing containsAtLeast instead of containsExactly here in case there are preloaded
        // apps having app functions.
        assertThat(getAllStaticMetadataPackages())
            .containsAtLeast(CtsApp.PACKAGE_NAME, DynamicSchemaHelperApp.PACKAGE_NAME)
        // required permission because runtime metadata is only visible to owner package
        runWithShellPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) {
            assertThat(getAllRuntimeMetadataPackages())
                .containsAtLeast(CtsApp.PACKAGE_NAME, DynamicSchemaHelperApp.PACKAGE_NAME)
        }

        internalLaunchIntent = Intent(context, DynamicRegistrationActivity::class.java)
        // FLAG_ACTIVITY_NEW_TASK is required of start activity not from the activity context,
        // FLAG_ACTIVITY_MULTIPLE_TASK is required to start multiple instances of the same
        // activity.
        internalLaunchIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
    }

    @After
    fun reset() {
        resetTestPageSize()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionState_functionDoesNotExist_returnsEmptyOrNull() = doBlocking {
        val fakeFunction = AppFunctionName("fake.package", "doesNotExist")

        val results = getAppFunctionStates(listOf(fakeFunction))

        assertThat(results).isEmpty()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun getAppFunctionStates_returnNothing_withoutPermission() = doBlocking {
        val request =
            listOf(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT,
            )

        val results = getAppFunctionStates(request).associateBy { it.functionName }

        assertThat(results).isEmpty()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun getSelfAppFunctionStates_returnCorrectState_withoutPermission() = doBlocking {
        val request = listOf(CtsApp.FunctionNames.ADD, CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT)

        val results = getAppFunctionStates(request).associateBy { it.functionName }

        assertThat(results).hasSize(2)
        assertThat(results[CtsApp.FunctionNames.ADD]!!.isEnabled).isTrue()
        assertThat(results[CtsApp.FunctionNames.ADD_DISABLED_BY_DEFAULT]!!.isEnabled).isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionStates_returnsCorrectDefaultState_withPermission() {
        getAppFunctionStates_returnsCorrectDefaultState_base()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    fun getAppFunctionStates_returnsCorrectDefaultState_withSystemPermission() {
        getAppFunctionStates_returnsCorrectDefaultState_base()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS)
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    fun getAppFunctionStates_returnsCorrectDefaultState_withDiscoverPermission() {
        getAppFunctionStates_returnsCorrectDefaultState_base()
    }

    fun getAppFunctionStates_returnsCorrectDefaultState_base() = doBlocking {
        val request =
            listOf(
                DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT,
                DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT,
            )

        val results = getAppFunctionStates(request).associateBy { it.functionName }

        assertThat(results).hasSize(2)
        assertThat(results[DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT]!!.isEnabled)
            .isTrue()
        assertThat(results[DynamicSchemaHelperApp.FunctionNames.DISABLED_BY_DEFAULT]!!.isEnabled)
            .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionStates_readAllFromOtherPackage() = doBlocking {
        val request = DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS.toList()

        val results = getAppFunctionStates(request).associateBy { it.functionName }

        assertThat(results.keys)
            .containsExactlyElementsIn(DynamicSchemaHelperApp.FunctionNames.ALL_FUNCTIONS)
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionStates_reflectsRuntimeChangesAfterDisabled() = doBlocking {
        val functionName = DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT
        try {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            val state = getAppFunctionStates(listOf(functionName)).single()

            assertThat(state.isEnabled).isFalse()
            assertThat(state.activityIds).isNull()
        } finally {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionStates_reflectsRuntimeChangesAfterDisabledThenEnabled() = doBlocking {
        val functionName = DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT
        try {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )

            val state = getAppFunctionStates(listOf(functionName)).single()

            assertThat(state.isEnabled).isTrue()
            assertThat(state.activityIds).isNull()
        } finally {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionState_dynamicRegistrationAfterRegister() = doBlocking {
        val registrationService = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        try {
            assertThat(
                    registrationService.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())
                )
                .isTrue()

            val results =
                getAppFunctionStates(
                    listOf(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
                )

            assertThat(results).hasSize(1)
            assertThat(results[0].isEnabled).isTrue()
            assertThat(results[0].activityIds).isNull()
        } finally {
            registrationService.safeUnregister(FunctionType.CONCAT_STRINGS.toString())
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionState_dynamicRegistrationAfterRegisterThenUnregister() = doBlocking {
        val registrationService = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        try {
            assertThat(
                    registrationService.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())
                )
                .isTrue()
            assertThat(
                    registrationService.unregisterAppFunction(
                        FunctionType.CONCAT_STRINGS.toString()
                    )
                )
                .isTrue()

            val results =
                getAppFunctionStates(
                    listOf(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS)
                )

            assertThat(results).hasSize(1)
            assertThat(results[0].isEnabled).isFalse()
            assertThat(results[0].activityIds).isNull()
        } finally {
            registrationService.safeUnregister(FunctionType.CONCAT_STRINGS.toString())
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionStates_registerButDisabled_shouldShowDisabled() = doBlocking {
        val registrationService = bindToRegistrationService(DynamicSchemaHelperApp.PACKAGE_NAME)
        val functionName = DynamicSchemaHelperApp.FunctionNames.DYNAMIC_CONCAT_STRINGS
        try {
            assertThat(
                    registrationService.registerAppFunction(FunctionType.CONCAT_STRINGS.toString())
                )
                .isTrue()
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )

            val state = getAppFunctionStates(listOf(functionName)).single()

            assertThat(state.isEnabled).isFalse()
            assertThat(state.activityIds).isNull()
        } finally {
            setAppFunctionEnabledRemote(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                functionName.functionId,
                AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
            )
            registrationService.safeUnregister(FunctionType.CONCAT_STRINGS.toString())
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    @EnsureDoesNotHavePermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    fun getAppFunctionStates_crossUserWithoutCrossUserPermission_fail() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
        installExistingPackageAsUser(DynamicSchemaHelperApp.PACKAGE_NAME, secondaryUser)
        retryAssert {
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
            val unused =
                getAppFunctionStates(
                    listOf(DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT)
                )
        } catch (e: Exception) {
            exception = e
        }

        assertThat(exception).isNotNull()
        assertThat(exception!!.cause).isInstanceOf(SecurityException::class.java)
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @EnsureHasAdditionalUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
    )
    fun getAppFunctionStates_crossUserWithCrossUserPermission_succeed() = doBlocking {
        val secondaryUser = sDeviceState.additionalUser()
        assumeTrue(
            "Test requires an additional user different from the primary user.",
            secondaryUser != TestApis.users().instrumented(),
        )
        installExistingPackageAsUser(CtsApp.PACKAGE_NAME, secondaryUser)
        installExistingPackageAsUser(DynamicSchemaHelperApp.PACKAGE_NAME, secondaryUser)
        retryAssert {
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
        manager =
            context
                .createContextAsUser(secondaryUser.userHandle(), 0)
                .getSystemService(AppFunctionManager::class.java)

        val states =
            getAppFunctionStates(listOf(DynamicSchemaHelperApp.FunctionNames.ENABLED_BY_DEFAULT))

        assertThat(states).hasSize(1)
        assertThat(states[0].isEnabled).isTrue()
        assertThat(states[0].activityIds).isNull()
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @EnsureHasNoDeviceOwner
    fun getAppFunctionStates_registerFromActivity_reportsActivityId() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.registerAppFunction(
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            ) }

            val state =
                getAppFunctionStates(
                    listOf(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)
                ).single()
            assertThat(state.isEnabled).isTrue()
            assertThat(state.activityIds).isNotNull()
            assertThat(state.activityIds).hasSize(1)

            scenario.onActivity { activity -> activity.unregisterAppFunction() }
            val stateUnregistered =
                getAppFunctionStates(
                    listOf(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)
                ).single()
            assertThat(stateUnregistered.isEnabled).isFalse()
            assertThat(stateUnregistered.activityIds).isNull()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @EnsureHasNoDeviceOwner
    fun getAppFunctionStates_registerFromTwoActivities_reportsBothActivityIds() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.registerAppFunction(
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            ) }

            val stateFirstRegistration =
                getAppFunctionStates(
                    listOf(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)
                ).single()
            assertThat(stateFirstRegistration.isEnabled).isTrue()
            assertThat(stateFirstRegistration.activityIds).isNotNull()
            assertThat(stateFirstRegistration.activityIds).hasSize(1)
            val firstActivityId = stateFirstRegistration.activityIds!!.first()

            scenario.onActivity { activity -> activity.unregisterAppFunction() }

            ActivityScenario.launch<DynamicRegistrationActivity>(
                internalLaunchIntent
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity -> activity.registerAppFunction(
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
                ) }

                val stateSecondRegistration =
                    getAppFunctionStates(
                        listOf(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)
                    ).single()
                assertThat(stateSecondRegistration.isEnabled).isTrue()
                assertThat(stateSecondRegistration.activityIds).isNotNull()
                assertThat(stateSecondRegistration.activityIds).hasSize(1)
                val secondActivityId = stateSecondRegistration.activityIds!!.first()
                assertThat(firstActivityId).isNotEqualTo(secondActivityId)

                scenario.onActivity { activity -> activity.registerAppFunction(
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
                )
                }
                val stateDoubleRegistration =
                    getAppFunctionStates(
                        listOf(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)
                    ).single()
                assertThat(stateDoubleRegistration.isEnabled).isTrue()
                assertThat(stateDoubleRegistration.activityIds).isNotNull()
                assertThat(stateDoubleRegistration.activityIds).hasSize(2)
                assertThat(stateDoubleRegistration.activityIds).containsExactly(
                    firstActivityId,
                    secondActivityId
                )
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionStates"])
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionState_registerFromSeparateAppActivity_enabledStateIsCorrect() = doBlocking {
        val intent = Intent().apply {
            component = ComponentName(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                "android.app.appfunctions.testutils.DynamicRegistrationActivity"
            )
            action = ACTION_REGISTER_APP_FUNCTION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(
                DynamicRegistrationActivity.EXTRA_FUNCTION_ID,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        }

        try {
            TestApis.activities().startActivity(intent)

            retryAssert {
                val state =
                    getAppFunctionStates(
                        listOf(
                            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
                        )
                    ).single()
                assertThat(state.isEnabled).isTrue()
                assertThat(state.activityIds).isNotNull()
                assertThat(state.activityIds).hasSize(1)
            }
        } finally {
            TestApis.activities().clearAllActivities()
        }
    }

    private suspend fun getAppFunctionStates(
        functionNames: List<AppFunctionName>
    ): List<AppFunctionState> = suspendCancellableCoroutine { continuation ->
        manager.getAppFunctionStates(
            functionNames,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
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

    private fun ITestAppFunctionRegistrationService.safeUnregister(functionType: String) {
        try {
            unregisterAppFunction(functionType)
        } catch (_: Exception) {}
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

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
    }
}
