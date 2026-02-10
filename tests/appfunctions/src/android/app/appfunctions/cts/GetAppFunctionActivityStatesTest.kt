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
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionState
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.CtsApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(BedsteadJUnit4::class)
class GetAppFunctionActivityStatesTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@GetAppFunctionActivityStatesTest.manager = manager

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

    @Test
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAppFunctionActivityStates"])
    @EnsureHasNoDeviceOwner
    @EnsureDoesNotHavePermission(
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM,
        Manifest.permission.DISCOVER_APP_FUNCTIONS,
    )
    fun getSelfAppFunctionActivityStates_returnsCorrectFunctions_withoutPermission() = doBlocking {
        val scenario =
            ActivityScenario.launch<DynamicRegistrationActivity>(
                getOpenRegistrationActivityIntent()
            )
        try {
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                activity.registerAppFunction(
                    CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS.functionIdentifier
                )
            }
            val state =
                assertActivityFunctionState(
                    CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS,
                    isEnabled = true,
                    numActivities = 1,
                )
            val activityId = state.activityIds!!.single()

            val activityStates = getAppFunctionActivityStates(setOf(activityId))
            assertThat(activityStates).hasSize(1)
            assertThat(activityStates[0].activityId).isEqualTo(activityId)
            assertThat(activityStates[0].functionNames)
                .containsExactly(CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS)

            scenario.onActivity { activity -> activity.unregisterAppFunction() }
            assertActivityFunctionState(
                CtsApp.FunctionNames.ACTIVITY_SCOPE_CONCAT_STRINGS,
                isEnabled = false,
                numActivities = 0,
            )

            val activityStatesAfterUnregister = getAppFunctionActivityStates(setOf(activityId))
            assertThat(activityStatesAfterUnregister).isEmpty()
        } finally {
            scenario.close()
        }
    }

    private suspend fun assertActivityFunctionState(
        functionName: AppFunctionName,
        isEnabled: Boolean,
        numActivities: Int,
    ): AppFunctionState {
        var state: AppFunctionState? = null
        retryAssert {
            state = getAppFunctionStates(listOf(functionName)).single()
            assertThat(state.isEnabled).isEqualTo(isEnabled)
            if (numActivities == 0) {
                assertThat(state.activityIds).isNull()
            } else {
                assertThat(state.activityIds).isNotNull()
                assertThat(state.activityIds).hasSize(numActivities)
            }
        }
        return checkNotNull(state)
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

    private suspend fun getAppFunctionActivityStates(
        activityIds: Set<AppFunctionActivityId>
    ): List<AppFunctionActivityState> = suspendCancellableCoroutine { continuation ->
        manager.getAppFunctionActivityStates(
            activityIds,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    private fun getOpenRegistrationActivityIntent(): Intent {
        return Intent(context, DynamicRegistrationActivity::class.java).apply {
            // FLAG_ACTIVITY_NEW_TASK is required of start activity not from the activity context,
            // FLAG_ACTIVITY_MULTIPLE_TASK is required to start multiple instances of the same
            // activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()
    }
}
