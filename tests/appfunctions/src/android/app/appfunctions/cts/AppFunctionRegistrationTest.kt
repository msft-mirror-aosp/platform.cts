/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.appfunctions.AppFunction
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.testutils.ITestAppFunctionRegistrationService
import android.app.appfunctions.testutils.TestAppFunctionConcatStrings
import android.app.appfunctions.testutils.TestAppFunctionConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.app.appfunctions.testutils.TestAppFunctionRegistrationService
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.ServiceTestRule
import com.android.bedstead.harrier.BedsteadJUnit4
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(android.app.appfunctions.flags.Flags.FLAG_ENABLE_CONTEXTUAL_APP_FUNCTIONS)
class AppFunctionRegistrationTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 2) val serviceTestRule = ServiceTestRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var manager: AppFunctionManager

    private val registrations = mutableListOf<AppFunctionRegistration>()

    @Before
    fun setup() {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@AppFunctionRegistrationTest.manager = manager
    }

    @After
    fun tearDown() {
        for (registration in registrations) {
            registration.unregister()
        }
        registrations.clear()
    }

    private fun registerConcatStringsAppFunction(
        function: AppFunction = TestAppFunctionConcatStrings()
    ) : AppFunctionRegistration {
        val registration = manager.registerAppFunction(
            CONCAT_STRINGS_FUNCTION_ID,
            context.mainExecutor,
            function
        )
        registrations.add(registration)
        return registration
    }

    @Test
    fun register_once_success() {
        registerConcatStringsAppFunction()
    }

    @Test
    @Throws(Exception::class)
    fun register_theSameIdTwice_fail() {
        registerConcatStringsAppFunction()
        assertFailsWith<IllegalStateException>() {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @Throws(Exception::class)
    fun register_theSameIdTwiceDifferentProcesses_fail() {
        val serviceIntent = Intent(context, TestAppFunctionRegistrationService::class.java)
        val binder: IBinder = serviceTestRule.bindService(serviceIntent)
        val service = ITestAppFunctionRegistrationService.Stub.asInterface(binder)

        service.registerAppFunction()

        assertFailsWith<IllegalStateException>() {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationWithSameId() {
        val staleRegistration = registerConcatStringsAppFunction()
        staleRegistration.unregister()
        val activeRegistration = registerConcatStringsAppFunction()

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to if appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The second registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationWithSameIdAndFunction() {
        val function = TestAppFunctionConcatStrings()
        val staleRegistration = registerConcatStringsAppFunction(function)
        staleRegistration.unregister()
        val activeRegistration = registerConcatStringsAppFunction(function)

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to if appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The second registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }

    @Test
    @Throws(Exception::class)
    fun unregister_callTwice_shouldNotAffectActiveRegistrationInTheOtherProcess() {
        val staleRegistration = registerConcatStringsAppFunction()
        staleRegistration.unregister()
        val serviceIntent = Intent(context, TestAppFunctionRegistrationService::class.java)
        val binder: IBinder = serviceTestRule.bindService(serviceIntent)
        val service = ITestAppFunctionRegistrationService.Stub.asInterface(binder)
        service.registerAppFunction()

        staleRegistration.unregister() // This call should be no-op

        // TODO(b/438413084): switch to if appfunction enabled check
        assertFailsWith<IllegalStateException>(
            "The service registration is expected to still be valid."
        ) {
            registerConcatStringsAppFunction()
        }
    }
}
