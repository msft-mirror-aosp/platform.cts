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
package com.android.bedstead.settings

import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceServiceClient
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent

/**
 * Simplifies usage of [SettingsPreferenceServiceClient] from Bedstead tests.
 */
class SettingsPreferenceServiceClientComponent : DeviceStateComponent {

    private val clients: MutableMap<String, BlockingSettingsPreferenceServiceClient> =
        mutableMapOf()

    internal fun getBlockingClient(packageName: String): BlockingSettingsPreferenceServiceClient {
        return clients[packageName] ?: BlockingSettingsPreferenceServiceClient(packageName).also {
            clients[packageName] = it
        }
    }

    internal fun allMetadata(packageName: String): List<SettingsPreferenceMetadata> {
        return getBlockingClient(packageName).allMetadata
    }

    override fun teardownShareableState() {
        clients.values.forEach {
            it.client.close()
        }
        clients.clear()
    }
}

/**
 * Get a BlockingSettingsPreferenceServiceClient for the [packageName]
 */
fun DeviceState.getBlockingSettingsPreferenceServiceClient(
    packageName: String = SETTINGS_PACKAGE_NAME
): BlockingSettingsPreferenceServiceClient {
    return getDependency(
        SettingsPreferenceServiceClientComponent::class.java
    ).getBlockingClient(packageName)
}

/**
 * Get a SettingsPreferenceServiceClient for the [packageName].
 */
fun DeviceState.settingsPreferenceServiceClient(
    packageName: String = SETTINGS_PACKAGE_NAME
): SettingsPreferenceServiceClient {
    return getBlockingSettingsPreferenceServiceClient(packageName).client
}

const val SETTINGS_PACKAGE_NAME = "com.android.settings"
