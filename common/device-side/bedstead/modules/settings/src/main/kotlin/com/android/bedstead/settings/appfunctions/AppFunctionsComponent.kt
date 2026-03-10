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
package com.android.bedstead.settings.appfunctions

import com.android.bedstead.harrier.FrameworkMethodWithParameter
import com.android.bedstead.nene.types.OptionalBoolean
import org.junit.runners.model.FrameworkMethod

/**
 * Contains logic to handle AppFunctions annotations.
 */
class AppFunctionsComponent {

    /**
     * Contains logic to handle [PerScreenDeviceStatesParameter].
     */
    internal fun handlePerScreenDeviceStatesParameter(
        annotation: PerScreenDeviceStatesParameter,
        frameworkMethod: FrameworkMethod
    ): List<FrameworkMethod> {
        return allPerScreenDeviceStates(
            annotation.functionIdentifiers,
            annotation.packageName
        ).map {
            FrameworkMethodWithParameter(frameworkMethod, it)
        }
    }

    /**
     * Contains logic to handle [DeviceStateItemsParameter].
     */
    internal fun handleDeviceStateItemsParameter(
        annotation: DeviceStateItemsParameter,
        frameworkMethod: FrameworkMethod
    ): List<FrameworkMethod> {
        return allPerScreenDeviceStates(
            annotation.functionIdentifiers,
            annotation.packageName
        ).flatMap { it.deviceStateItems }.map {
            FrameworkMethodWithParameter(frameworkMethod, it)
        }
    }

    private fun allPerScreenDeviceStates(
        functionIdentifiers: Array<String>,
        packageName: String
    ): List<PerScreenDeviceStates> {
        val client = AppFunctionsBlockingClient(packageName)
        return functionIdentifiers.flatMap {
            client.getDeviceState(functionIdentifier = it)
        }
    }

    internal fun handleDeviceStateItemMetadataParameter(
        annotation: DeviceStateItemMetadataParameter,
        frameworkMethod: FrameworkMethod
    ): List<FrameworkMethod> {
        val client = AppFunctionsBlockingClient(annotation.packageName)
        return client.getDeviceStateMetadata().flatMap {
            it.deviceStateItemsMetadata
        }.applyIsWritableFilter(annotation.isWritable).map {
            FrameworkMethodWithParameter(frameworkMethod, it, it.key)
        }
    }

    private fun List<DeviceStateItemMetadata>.applyIsWritableFilter(
        isWritable: OptionalBoolean
    ): List<DeviceStateItemMetadata> {
        return if (isWritable == OptionalBoolean.ANY) {
            this
        } else {
            filter {
                it.writable == isWritable.toBoolean()
            }
        }
    }
}
