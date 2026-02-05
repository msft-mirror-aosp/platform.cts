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
import android.app.appfunctions.testutils.DynamicRegistrationActivity
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.EXTRA_FUNCTION_ID
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    // TODO(b/478873466): add cross app test
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@AppFunctionActivityMultiregistrationTest.manager = manager

        verifyActivityFunctionState(isEnabled = false)
    }

    @Test
    fun register_andUnregister_enabledStateIsCorrect() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            getLaunchIntent()
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.handleIntent(getRegistrationIntent()) }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
            verifyActivityFunctionState(isEnabled = true)
            scenario.onActivity { activity -> activity.handleIntent(getUnregistrationIntent()) }
            verifyActivityFunctionState(isEnabled = false)
        }
    }

    @Test
    fun register_fromTwoActivities_bothRegistrationsAreActive() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            getLaunchIntent()
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.handleIntent(getRegistrationIntent()) }
            verifyActivityFunctionState(isEnabled = true)
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(
                getLaunchIntent()
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity -> activity.handleIntent(getRegistrationIntent()) }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }
                verifyActivityFunctionState(isEnabled = true)

                scenario.onActivity { activity -> activity.handleIntent(getUnregistrationIntent()) }
                scenario.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                // Verify that the second activity registration is still valid
                verifyActivityFunctionState(isEnabled = true)

                scenario2.onActivity {
                    activity -> activity.handleIntent(
                    getUnregistrationIntent()
                    )
                }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
                verifyActivityFunctionState(isEnabled = false)
            }
        }
    }

    @Test
    fun register_globalFunction_onlyOneRegistrationIsAllowed() = doBlocking {
        ActivityScenario.launch<DynamicRegistrationActivity>(
            getLaunchIntent()
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity -> activity.handleIntent(getRegistrationIntent(
                CONCAT_STRINGS_FUNCTION_ID
            )) }
            scenario.onActivity { activity -> assertThat(activity.isRegistered).isTrue() }

            ActivityScenario.launch<DynamicRegistrationActivity>(
                getLaunchIntent()
            ).use { scenario2 ->
                scenario2.moveToState(Lifecycle.State.STARTED)
                scenario2.onActivity { activity -> activity.handleIntent(
                    getRegistrationIntent(CONCAT_STRINGS_FUNCTION_ID)
                ) }
                scenario2.onActivity { activity -> assertThat(activity.isRegistered).isFalse() }
            }
        }
    }

    suspend fun verifyActivityFunctionState(isEnabled: Boolean) {
        assertFunctionState(
            AppFunctionMetadataTestHelper.CtsApp.PACKAGE_NAME,
            ACTIVITY_CONCAT_STRINGS_FUNCTION_ID,
            manager,
            isEnabled = isEnabled
        )
    }

    fun getLaunchIntent(): Intent {
        val intent = Intent(context, DynamicRegistrationActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return intent
    }

    fun getRegistrationIntent(functionId: String = ACTIVITY_CONCAT_STRINGS_FUNCTION_ID): Intent {
        val intent = Intent(DynamicRegistrationActivity.ACTION_REGISTER_APP_FUNCTION)
        intent.putExtra(EXTRA_FUNCTION_ID, functionId)
        return intent
    }

    fun getUnregistrationIntent(): Intent {
        val intent = Intent(DynamicRegistrationActivity.ACTION_UNREGISTER_APP_FUNCTION)
        return intent
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
}
