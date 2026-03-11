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
package android.app.appfunctions.testutils

import android.app.Service
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionObserver
import android.app.appfunctions.AppFunctionObservation
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class TestAppFunctionProxyManagerService : Service() {
    private lateinit var manager: AppFunctionManager
    private lateinit var executor: Executor
    private var observation: AppFunctionObservation? = null
    private val changedPackageNamesHistory = mutableListOf<Set<String>>()
    private val changedFunctionNamesHistory = mutableListOf<Set<AppFunctionName>>()

    private val binder =
        object : ITestAppFunctionProxyManagerService.Stub() {
            override fun startTestObserver() {
                observation = manager.observeAppFunctions(
                  executor,
                  object : AppFunctionObserver {
                    override fun onAppFunctionMetadataChanged(
                        changedPackageNames: Set<String>
                    ) {
                      changedPackageNamesHistory.add(changedPackageNames)
                    }

                    override fun onAppFunctionStatesChanged(
                        changedFunctionNames: Set<AppFunctionName>
                    ) {
                      changedFunctionNamesHistory.add(changedFunctionNames)
                    }
                  }
                )
            }

            override fun getTestObserverHistory(): TestObserverHistory {
                return TestObserverHistory(
                    changedPackageNamesHistory,
                    changedFunctionNamesHistory
                )
            }
        }

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadExecutor()
        manager = getSystemService(AppFunctionManager::class.java)
    }

    override fun onDestroy() {
        observation?.cancel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}