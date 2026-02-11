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
package android.app.appfunctions.cts

import android.Manifest
import android.app.appfunctions.AppFunctionActivityId
import android.app.appfunctions.AppFunctionActivityState
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionState
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.assertFunctionEnabledState
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunction
import android.app.appfunctions.testutils.ConcatStrings.Companion.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.ACTION_REGISTER_APP_FUNCTION
import android.app.appfunctions.testutils.DynamicRegistrationActivity2
import android.app.appsearch.GenericDocument
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionActivityMultiregistrationTest {
    // TODO(b/482000294): add external same activity multi-instance tests

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var manager: AppFunctionManager
    private lateinit var internalLaunchIntent: Intent

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@AppFunctionActivityMultiregistrationTest.manager = manager

        internalLaunchIntent = Intent(context, DynamicRegistrationActivity::class.java)
        // FLAG_ACTIVITY_NEW_TASK is required of start activity not from the activity context,
        // FLAG_ACTIVITY_MULTIPLE_TASK is required to start multiple instances of the same
        // activity.
        internalLaunchIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        assertFunctionEnabledState(
            AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
            ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
            manager,
            isEnabled = false
        )
        retryAssert {
            assertFunctionEnabledState(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = false
            )
        }
    }

    @After
    fun tearDown() {
        TestApis.activities().clearAllActivities()
    }

    @Test
    fun registerAndUnregister_enabledStateIsCorrect() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.registerAppFunction(
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            ) }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
            assertFunctionEnabledState(
                AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true
            )
            scenario.onActivity { activity -> activity.unregisterAppFunction() }
            assertFunctionEnabledState(
                AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = false
            )
        }
    }

    @Test
    fun register_inTheSeparateApp_enabledStateIsCorrect() = doBlocking {
        try {
            TestApis.activities().startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
                )
            )
            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true
                )
            }
        } finally {
            TestApis.activities().clearAllActivities()
        }
    }

    @Test
    fun register_fromTwoActivities_bothRegistrationsAreActive() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.registerAppFunction(
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            ) }
            assertFunctionEnabledState(
                AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true
            )
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(
                internalLaunchIntent
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity -> activity.registerAppFunction(
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
                )
                }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
                assertFunctionEnabledState(
                    AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true
                )

                scenario.onActivity { activity -> activity.unregisterAppFunction() }
                scenario.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                // Verify that the second activity registration is still valid
                assertFunctionEnabledState(
                    AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true
                )

                scenario2.onActivity { activity -> activity.unregisterAppFunction() }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                assertFunctionEnabledState(
                    AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = false
                )
            }
        }
    }

    @Test
    fun register_globalFunction_onlyOneRegistrationIsAllowed() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.registerAppFunction(
                CONCAT_STRINGS_FUNCTION_ID
            ) }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(
                internalLaunchIntent
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity -> activity.registerAppFunction(
                    CONCAT_STRINGS_FUNCTION_ID
                ) }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
            }
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_success() = doBlocking {
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )

        val activityIds = awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request = ExecuteAppFunctionRequest.Builder(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
        )
            .setActivityId(activityIds.first())
            .setParameters(parameters)
            .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNull()
        assertThat(
            response
                .getOrNull()!!
                .resultDocument
                .getPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
        )
            .isEqualTo("AB")
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_doesntProvideActivityId_fail() = doBlocking {
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )

        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request = ExecuteAppFunctionRequest.Builder(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
        )
            .setParameters(parameters)
            .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNotNull()
        assertThat(response.appFunctionException().errorCode).isEqualTo(
            AppFunctionException.ERROR_DISABLED
        )
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_wrongActivityIdProvided_fail() = doBlocking {
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )
        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        // Obtain ActivityId from this app
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
                )
            }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            val wrongActivityId = awaitRegisteredActivityIds(
                AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1
            ).single()

            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("prefix", "A")
                    .setPropertyString("suffix", "B")
                    .build()
            val request = ExecuteAppFunctionRequest.Builder(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
            )
                .setActivityId(wrongActivityId)
                .setParameters(parameters)
                .build()

            val response = manager.executeAppFunction(request)
            assertThat(response.exceptionOrNull()).isNotNull()
            assertThat(response.appFunctionException().errorCode).isEqualTo(
                AppFunctionException.ERROR_DISABLED
            )
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_wrongFunctionName_fail() = doBlocking {
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )

        val activityIds = awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request = ExecuteAppFunctionRequest.Builder(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_GET_URIS
        )
            .setActivityId(activityIds.first())
            .setParameters(parameters)
            .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNotNull()
        assertThat(response.appFunctionException().errorCode).isEqualTo(
            AppFunctionException.ERROR_DISABLED
        )
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_twoActivitiesRegistered_targetsCorrectActivity() = doBlocking {
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY_2,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )
        val activityId2 = awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        // Start second activity which registers the same function but different implementation
        TestApis.activities().startActivity(
            getExternalRegistrationIntent(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                REGISTRATION_ACTIVITY,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
            )
        )
        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 2
        )

        // Target execution request to the first activity
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request = ExecuteAppFunctionRequest.Builder(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
        )
            .setActivityId(activityId2.first())
            .setParameters(parameters)
            .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNull()
        assertThat(
            response
                .getOrNull()!!
                .resultDocument
                .getPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
        )
            .isEqualTo(DynamicRegistrationActivity2.Companion.CUSTOM_PREFIX + "AB")
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionActivityState_inTheSeparateApp_returnsRegisteredFunction() = doBlocking {
        TestApis.activities().startActivity(getExternalRegistrationIntent(
            DynamicSchemaHelperApp.PACKAGE_NAME,
            REGISTRATION_ACTIVITY,
            ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
        ))

        val activityIds = awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1
        )

        val activityStates = getAppFunctionActivityStates(setOf(activityIds.single()))
        assertThat(activityStates).hasSize(1)
        val activityState = activityStates.single()
        assertThat(activityState.activityId).isEqualTo(activityIds.single())
        assertThat(activityState.functionNames).hasSize(1)
        assertThat(activityState.functionNames.single()).isEqualTo(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
        )
    }

    private fun getExternalRegistrationIntent(
        packageName: String,
        activityName: String,
        functionId: String
    ): Intent {
        return Intent().apply {
            component = ComponentName(
                packageName,
                activityName
            )
            action = ACTION_REGISTER_APP_FUNCTION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(
                DynamicRegistrationActivity.EXTRA_FUNCTION_ID,
                functionId
            )
        }
    }

    private suspend fun awaitRegisteredActivityIds(
        functionName: AppFunctionName,
        numRegistrations: Int = 1
    ): Set<AppFunctionActivityId> {
        var activityIds: Set<AppFunctionActivityId>? = null
        retryAssert {
            val state = getAppFunctionState(functionName)
            assertThat(state).isNotNull()
            assertThat(state.single().isEnabled).isTrue()
            assertThat(state.single().activityIds).isNotNull()
            assertThat(state.single().activityIds).hasSize(numRegistrations)
            activityIds = state.single().activityIds!!
        }
        return activityIds!!
    }

    private suspend fun getAppFunctionState(
        functionName: AppFunctionName
    ): List<AppFunctionState> = suspendCancellableCoroutine { continuation ->
        manager.getAppFunctionStates(
            listOf(functionName),
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    private suspend fun getAppFunctionActivityStates(
        activityIds: Set<AppFunctionActivityId>
    ): List<AppFunctionActivityState> = suspendCancellableCoroutine { continuation ->
        manager.getAppFunctionActivityStates(
            activityIds,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    private fun <T> Result<T>.appFunctionException(): AppFunctionException =
        exceptionOrNull() as AppFunctionException

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    companion object {
        const val REGISTRATION_ACTIVITY =
            "android.app.appfunctions.testutils.DynamicRegistrationActivity"

        const val REGISTRATION_ACTIVITY_2 =
            "android.app.appfunctions.testutils.DynamicRegistrationActivity2"
    }
}
