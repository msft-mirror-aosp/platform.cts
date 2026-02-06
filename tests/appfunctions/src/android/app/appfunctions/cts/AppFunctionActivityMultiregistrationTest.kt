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

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.cts.AppFunctionUtils.assertFunctionState
import android.app.appfunctions.testutils.ConcatStrings.Companion.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.ACTION_REGISTER_APP_FUNCTION
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionActivityMultiregistrationTest {
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

        assertFunctionState(
            AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
            ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
            manager,
            isEnabled = false
        )
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
            assertFunctionState(
                AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = true
            )
            scenario.onActivity { activity -> activity.unregisterAppFunction() }
            assertFunctionState(
                AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                manager,
                isEnabled = false
            )
        }
    }

    @Test
    fun register_inTheSeparateApp_enabledStateIsCorrect() = doBlocking {
        val intent = Intent().apply {
            component = ComponentName(
                AppFunctionMetadataTestHelper.DynamicSchemaHelperApp.PACKAGE_NAME,
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
                assertFunctionState(
                    AppFunctionMetadataTestHelper.DynamicSchemaHelperApp.PACKAGE_NAME,
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
            assertFunctionState(
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
                assertFunctionState(
                    AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true
                )

                scenario.onActivity { activity -> activity.unregisterAppFunction() }
                scenario.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                // Verify that the second activity registration is still valid
                assertFunctionState(
                    AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
                    ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
                    manager,
                    isEnabled = true
                )

                scenario2.onActivity { activity -> activity.unregisterAppFunction() }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                assertFunctionState(
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

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
}
