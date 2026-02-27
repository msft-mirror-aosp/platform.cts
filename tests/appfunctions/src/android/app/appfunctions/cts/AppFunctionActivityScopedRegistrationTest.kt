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
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.assertFunctionEnabledState
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunction
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.ConcatStrings
import android.app.appfunctions.testutils.ConcatStrings.Companion.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.freezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.safeUnfreezeProcess
import android.app.appfunctions.testutils.CtsTestUtil.unfreezeProcess
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
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
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
class AppFunctionActivityScopedRegistrationTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var manager: AppFunctionManager
    private lateinit var internalLaunchIntent: Intent

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@AppFunctionActivityScopedRegistrationTest.manager = manager

        internalLaunchIntent = Intent(context, DynamicRegistrationActivity::class.java)
        // FLAG_ACTIVITY_NEW_TASK is required of start activity not from the activity context,
        // FLAG_ACTIVITY_MULTIPLE_TASK is required to start multiple instances of the same
        // activity.
        internalLaunchIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        assertFunctionEnabledState(
            CtsApp.PACKAGE_NAME,
            ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
            manager,
            isEnabled = false,
        )
        retryAssert {
            assertFunctionEnabledState(
                DynamicSchemaHelperApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = false,
            )
        }
    }

    @After
    fun tearDown() {
        TestApis.activities().clearAllActivities()
    }

    @Test
    fun registerAndUnregister_enabledStateIsCorrect() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
            }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
            assertFunctionEnabledState(
                CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true,
            )
            scenario.onActivity { activity -> activity.unregisterAppFunction() }
            assertFunctionEnabledState(
                CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = false,
            )
        }
    }

    @Test
    fun register_inTheSeparateApp_enabledStateIsCorrect() = doBlocking {
        try {
            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )
            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true,
                )
            }
        } finally {
            TestApis.activities().clearAllActivities()
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun freezeProcessHavingActivityFunctionRegistered_enabledStateShouldBeFalse() = doBlocking {
        try {
            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )

            freezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)

            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = false,
                )
            }
        } finally {
            TestApis.activities().clearAllActivities()
            safeUnfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun unfreezeProcessHavingActivityFunctionRegistered_enabledStateShouldBeTrue() = doBlocking {
        try {
            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )

            freezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = false,
                )
            }
            unfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)

            retryAssert {
                assertFunctionEnabledState(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true,
                )
            }
        } finally {
            TestApis.activities().clearAllActivities()
            safeUnfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    fun register_fromTwoActivities_bothRegistrationsAreActive() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
            }
            assertFunctionEnabledState(
                CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true,
            )
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use {
                scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity ->
                    activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
                }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
                assertFunctionEnabledState(
                    CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true,
                )

                scenario.onActivity { activity -> activity.unregisterAppFunction() }
                scenario.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                // Verify that the second activity registration is still valid
                assertFunctionEnabledState(
                    CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true,
                )

                scenario2.onActivity { activity -> activity.unregisterAppFunction() }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                assertFunctionEnabledState(
                    CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = false,
                )
            }
        }
    }

    @Test
    fun register_globalFunction_onlyOneRegistrationIsAllowed() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)
            }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use {
                scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity ->
                    activity.registerAppFunction(CONCAT_STRINGS_FUNCTION_ID)
                }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
            }
        }
    }

    @Test
    fun unregister_callTwice_doesntAffectActiveRegistration() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
            }
            assertFunctionEnabledState(
                CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true,
            )

            ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use {
                scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity ->
                    activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
                }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
                scenario2.onActivity { activity -> activity.unregisterAppFunction(numTimes = 2) }
                assertFunctionEnabledState(
                    CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true,
                )
            }
        }
    }

    @Test
    fun register_twoRegistrations_finishOneOfActivity_unregistersOnce() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            internalLaunchIntent
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.manager.registerAppFunction(
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                        activity.mainExecutor,
                        ConcatStrings()
                    )
             }
            val activityIdFinished = awaitRegisteredActivityIds(
                CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1
            ).first()

            ActivityScenario.launch<DynamicRegistrationActivity>(
                internalLaunchIntent
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity ->
                    activity.registerAppFunction(
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
                }

                val activityIdsBoth = awaitRegisteredActivityIds(
                    CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 2
                )

                scenario.onActivity { activity -> activity.finish() }

                val activityIdSecond = awaitRegisteredActivityIds(
                    CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 1
                ).first()

                assertThat(activityIdSecond).isNotEqualTo(activityIdFinished)
                assertThat(activityIdsBoth).containsExactly(activityIdFinished, activityIdSecond)
            }
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_globalDynamicFunction_withActivityId_fail() = doBlocking {
        withRegisteredActivityId(CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS) { activityId ->
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("prefix", "A")
                    .setPropertyString("suffix", "B")
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        AppFunctionName(CtsApp.PACKAGE_NAME, CONCAT_STRINGS_FUNCTION_ID)
                    )
                    .setActivityId(activityId)
                    .setParameters(parameters)
                    .build()

            val response = manager.executeAppFunction(request)
            assertThat(response.exceptionOrNull()).isNotNull()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_staticFunction_withActivityId_fail() = doBlocking {
        withRegisteredActivityId(CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS) { activityId ->
            val request =
                ExecuteAppFunctionRequest.Builder(CtsApp.FunctionNames.ADD_ASYNC)
                    .setActivityId(activityId)
                    .build()

            val response = manager.executeAppFunction(request)
            assertThat(response.exceptionOrNull()).isNotNull()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_success() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )

        val activityIds =
            awaitRegisteredActivityIds(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1,
            )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(
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
    fun executeActivityFunctionFromFrozenProcess_fail() = doBlocking {
        try {
            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )
            val activityIds =
                awaitRegisteredActivityIds(
                    DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 1,
                )
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("prefix", "A")
                    .setPropertyString("suffix", "B")
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
                    )
                    .setActivityId(activityIds.single())
                    .setParameters(parameters)
                    .build()

            freezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
            val response = manager.executeAppFunction(request)

            assertThat(response.exceptionOrNull()).isNotNull()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
        } finally {
            safeUnfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun executeActivityFunctionFromUnfrozenProcess_success() = doBlocking {
        try {
            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )
            val activityIds =
                awaitRegisteredActivityIds(
                    DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 1,
                )
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("prefix", "A")
                    .setPropertyString("suffix", "B")
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
                    )
                    .setActivityId(activityIds.first())
                    .setParameters(parameters)
                    .build()

            freezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
            unfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
            val response = manager.executeAppFunction(request)

            assertThat(response.exceptionOrNull()).isNull()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                )
                .isEqualTo("AB")
        } finally {
            safeUnfreezeProcess(context, DynamicSchemaHelperApp.PACKAGE_NAME)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_doesntProvideActivityId_fail() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )

        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1,
        )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
                )
                .setParameters(parameters)
                .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNotNull()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_wrongActivityIdProvided_fail() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )
        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 1,
        )

        // Obtain ActivityId from this app
        withRegisteredActivityId(CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS) { wrongActivityId ->
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("prefix", "A")
                    .setPropertyString("suffix", "B")
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS
                    )
                    .setActivityId(wrongActivityId)
                    .setParameters(parameters)
                    .build()

            val response = manager.executeAppFunction(request)
            assertThat(response.exceptionOrNull()).isNotNull()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
        }
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_activityFunctionInTheSeparateApp_wrongFunctionName_fail() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )

        val activityIds =
            awaitRegisteredActivityIds(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1,
            )

        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_GET_URIS)
                .setActivityId(activityIds.first())
                .setParameters(parameters)
                .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNotNull()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun execute_twoActivitiesRegistered_targetsCorrectActivity() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY_2,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )
        val activityId2 =
            awaitRegisteredActivityIds(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1,
            )

        // Start second activity which registers the same function but different implementation
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )
        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
            numRegistrations = 2,
        )

        // Target execution request to the first activity
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("prefix", "A")
                .setPropertyString("suffix", "B")
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(
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
    fun execute_twoInstancesOfSameActivityRegistered_targetsCorrectActivity() = doBlocking {
        startExternalReturnInstanceIdActivity("instance1")
        val activityIds1 =
            awaitRegisteredActivityIds(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_RETURN_INSTANCE_ID,
                numRegistrations = 1
            )

        startExternalReturnInstanceIdActivity("instance2")
        awaitRegisteredActivityIds(
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_RETURN_INSTANCE_ID,
            numRegistrations = 2
        )

        // Target execution request to the first activity
        val request =
            ExecuteAppFunctionRequest.Builder(
                    DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_RETURN_INSTANCE_ID
                )
                .setActivityId(activityIds1.single())
                .build()

        val response = manager.executeAppFunction(request)
        assertThat(response.exceptionOrNull()).isNull()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo("instance1")
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionActivityState_inTheSeparateApp_returnsRegisteredFunction() = doBlocking {
        TestApis.activities()
            .startActivity(
                getExternalRegistrationIntent(
                    DynamicSchemaHelperApp.PACKAGE_NAME,
                    REGISTRATION_ACTIVITY,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                )
            )

        val activityIds =
            awaitRegisteredActivityIds(
                DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                numRegistrations = 1,
            )

        val activityStates = getAppFunctionActivityStates(setOf(activityIds.single()))
        assertThat(activityStates).hasSize(1)
        val activityState = activityStates.single()
        assertThat(activityState.activityId).isEqualTo(activityIds.single())
        assertThat(activityState.functionNames).hasSize(1)
        assertThat(activityState.functionNames.single())
            .isEqualTo(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS)
    }

    @Test
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS)
    fun getAppFunctionActivityState_withExecutePermission_returnsAllRegisteredFunctions() =
        doBlocking {
            val (localActivityId, externalActivityId) = startLocalAndExternalActivities()

            val activityStates =
                getAppFunctionActivityStates(setOf(localActivityId, externalActivityId))

            assertActivityStates(
                activityStates,
                localActivityId,
                listOf(AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS),
            )
        }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    @EnsureHasPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM)
    fun getAppFunctionActivityState_withExecuteSystemPermission_returnsAllRegisteredFunctions() =
        doBlocking {
            val (localActivityId, externalActivityId) = startLocalAndExternalActivities()

            val activityStates =
                getAppFunctionActivityStates(setOf(localActivityId, externalActivityId))

            assertActivityStates(
                activityStates,
                localActivityId,
                listOf(AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS),
            )
            assertActivityStates(
                activityStates,
                externalActivityId,
                listOf(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS),
            )
        }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    @EnsureHasPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS)
    fun getAppFunctionActivityState_discoverPermission_returnsAllRegisteredFunctions() =
        doBlocking {
            val (localActivityId, externalActivityId) = startLocalAndExternalActivities()

            val activityStates =
                getAppFunctionActivityStates(setOf(localActivityId, externalActivityId))

            assertActivityStates(
                activityStates,
                localActivityId,
                listOf(AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS),
            )
            assertActivityStates(
                activityStates,
                externalActivityId,
                listOf(DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS),
            )
        }

    @Test
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun getAppFunctionActivityState_withNoPermission_returnsOnlyLocalRegisteredFunctions() =
        doBlocking {
            val (localActivityId, externalActivityId) = startLocalAndExternalActivities()
            val activityStates =
                getAppFunctionActivityStates(setOf(localActivityId, externalActivityId))

            assertActivityStates(
                activityStates,
                localActivityId,
                listOf(AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS),
            )
            assertActivityStatesDoesNotContainActivityId(activityStates, externalActivityId)
        }

    private fun assertActivityStates(
        activityStates: List<AppFunctionActivityState>,
        expectedActivityId: AppFunctionActivityId,
        expectedFunctionNames: List<AppFunctionName>,
    ) {
        val activityState = activityStates.single { it.activityId == expectedActivityId }

        assertThat(activityState.functionNames).containsExactlyElementsIn(expectedFunctionNames)
    }

    private fun assertActivityStatesDoesNotContainActivityId(
        activityStates: List<AppFunctionActivityState>,
        activityId: AppFunctionActivityId,
    ) {
        assertThat(activityStates.any { it.activityId == activityId }).isFalse()
    }

    private suspend fun startLocalAndExternalActivities(): RegisteredActivityIds {
        // Have to temporarily grant EXECUTE_APP_FUNCTIONS permission to be able to get the
        // external activity ids.
        TestApis.permissions().withPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS).use {
            val localScenario =
                ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent)
            localScenario.moveToState(Lifecycle.State.STARTED)
            localScenario.onActivity { activity ->
                activity.registerAppFunction(ACTIVITY_CONCAT_STRINGS_FUNCTION_ID)
            }

            val localActivityIds =
                awaitRegisteredActivityIds(
                    AppFunctionMetadataTestHelper.CtsApp.FunctionNames.ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 1,
                )

            TestApis.activities()
                .startActivity(
                    getExternalRegistrationIntent(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                        ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    )
                )

            val externalActivityIds =
                awaitRegisteredActivityIds(
                    DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_CONCAT_STRINGS,
                    numRegistrations = 1,
                )

            return RegisteredActivityIds(
                localActivityId = localActivityIds.first(),
                externalActivityId = externalActivityIds.first(),
            )
        }
    }

    private suspend fun withRegisteredActivityId(
        functionNameToRegister: AppFunctionName,
        block: suspend (activityId: AppFunctionActivityId) -> Unit,
    ) {
        ActivityScenario.launch<DynamicRegistrationActivity>(internalLaunchIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(functionNameToRegister.functionIdentifier)
            }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            val activityId =
                awaitRegisteredActivityIds(functionNameToRegister, numRegistrations = 1).single()
            block(activityId)
        }
    }

    private fun getExternalRegistrationIntent(
        packageName: String,
        activityName: String,
        functionId: String,
    ): Intent {
        return Intent().apply {
            component = ComponentName(packageName, activityName)
            action = ACTION_REGISTER_APP_FUNCTION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DynamicRegistrationActivity.EXTRA_FUNCTION_ID, functionId)
        }
    }

    private suspend fun awaitRegisteredActivityIds(
        functionName: AppFunctionName,
        numRegistrations: Int = 1,
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

    private suspend fun getAppFunctionState(functionName: AppFunctionName): List<AppFunctionState> =
        suspendCancellableCoroutine { continuation ->
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

    private fun startExternalReturnInstanceIdActivity(instanceId: String) {
        val functionId =
            DynamicSchemaHelperApp.FunctionNames.DYNAMIC_ACTIVITY_RETURN_INSTANCE_ID.functionIdentifier
        context.startActivity(
            Intent().apply {
                component =
                    ComponentName(
                        DynamicSchemaHelperApp.PACKAGE_NAME,
                        REGISTRATION_ACTIVITY,
                    )
                action = ACTION_REGISTER_APP_FUNCTION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(DynamicRegistrationActivity.EXTRA_FUNCTION_ID, functionId)
                putExtra(DynamicRegistrationActivity.EXTRA_INSTANCE_ID, instanceId)
            }
        )
    }

    private fun <T> Result<T>.appFunctionException(): AppFunctionException =
        exceptionOrNull() as AppFunctionException

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private data class RegisteredActivityIds(
        val localActivityId: AppFunctionActivityId,
        val externalActivityId: AppFunctionActivityId,
    )

    companion object {
        const val REGISTRATION_ACTIVITY =
            "android.app.appfunctions.testutils.DynamicRegistrationActivity"

        const val REGISTRATION_ACTIVITY_2 =
            "android.app.appfunctions.testutils.DynamicRegistrationActivity2"
    }
}
