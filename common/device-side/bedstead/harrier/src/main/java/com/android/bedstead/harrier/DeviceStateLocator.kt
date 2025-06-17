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
package com.android.bedstead.harrier

import android.util.Log
import com.android.bedstead.nene.utils.FailureDumper

/**
 * [BedsteadServiceLocator] for [DeviceState]
 */
class DeviceStateLocator : BedsteadServiceLocator(), DeviceStateComponent {

    override fun <T : Any> onDependencyCreated(dependency: T) {
        if (dependency is DeviceStateComponent) {
            Log.v(LOG_TAG, "prepareTestState (after creation): " + dependency.javaClass)
            dependency.prepareTestState()
        }
    }

    override fun teardownShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "teardownShareableState: " + it.javaClass)
            try {
                it.teardownShareableState()
            } catch (exception: Exception) {
                Log.e(
                    LOG_TAG,
                    "an exception occurred while executing " +
                            "teardownShareableState for ${it.javaClass}",
                    exception
                )
            }
        }
    }

    override fun teardownNonShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "teardownNonShareableState: " + it.javaClass)
            try {
                it.teardownNonShareableState()
            } catch (exception: Exception) {
                Log.e(
                    LOG_TAG,
                    "an exception occurred while executing " +
                            "teardownNonShareableState for ${it.javaClass}",
                    exception
                )
            }
        }
    }

    override fun prepareTestState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "prepareTestState: " + it.javaClass)
            it.prepareTestState()
        }
    }

    /**
     * Get all loaded FailureDumpers
     */
    fun getAllFailureDumpers(): List<FailureDumper> {
        return getAllDependenciesOfType<FailureDumper>()
    }

    /**
     * Get all loaded TestRuleExecutors
     */
    fun getAllTestRuleExecutors(): List<TestRuleExecutor> {
        return getAllDependenciesOfType<TestRuleExecutor>()
    }

    companion object {
        private const val LOG_TAG = "DeviceStateLocator"
    }
}
