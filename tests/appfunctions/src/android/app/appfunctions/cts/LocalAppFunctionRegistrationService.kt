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

import android.app.Service
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.testutils.ConcatStrings
import android.app.appfunctions.testutils.ConcatStrings.Companion.CONCAT_STRINGS_FUNCTION_ID
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors


/**
 * A service that registers AppFunctions locally in the same process as the
 * test. This service is intended to be used in tests where the test needs to
 * register AppFunctions locally in the same process as the test.
 */
class LocalAppFunctionRegistrationService : Service() {
    private val binder = LocalBinder()
    private lateinit var manager: AppFunctionManager
    private val registrationExecutor = Executors.newSingleThreadExecutor()

    inner class LocalBinder : Binder() {
        fun getService(): LocalAppFunctionRegistrationService = this@LocalAppFunctionRegistrationService
    }

    companion object {
        var registration: AppFunctionRegistration? = null
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(AppFunctionManager::class.java)!!
    }

    override fun onBind(intent: Intent): IBinder {
        return binder as IBinder
    }

    fun registerAppFunction(functionId: String): Boolean {
        registration = manager.registerAppFunction(
            functionId,
            registrationExecutor,
            ConcatStrings()
        )
        return true
    }

    override fun onDestroy() {
        // Deliberately not calling unregister to make this an "unsafe" service for testing.
        super.onDestroy()
    }
}
