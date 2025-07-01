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

import android.Manifest
import android.os.OutcomeReceiver
import android.service.settings.preferences.MetadataRequest
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceServiceClient
import android.util.Log
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.nene.TestApis
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Simplifies usage of [SettingsPreferenceServiceClient] from Bedstead tests.
 */
class SettingsPreferenceServiceClientComponent : DeviceStateComponent {

    private val context by lazy { TestApis.context().instrumentedContext() }
    private var clientInitialized = false
    internal val client: SettingsPreferenceServiceClient by lazy {
        initClient()
    }

    internal val allMetadata: List<SettingsPreferenceMetadata> by lazy {
        initMetadata()
    }

    private fun initClient(): SettingsPreferenceServiceClient {
        val connectionLatch = CountDownLatch(1)
        var client: SettingsPreferenceServiceClient? = null
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            SettingsPreferenceServiceClient(
                context,
                "com.android.settings",
                context.mainExecutor,
                object : OutcomeReceiver<SettingsPreferenceServiceClient, Exception> {
                    override fun onResult(result: SettingsPreferenceServiceClient) {
                        client = result
                        clientInitialized = true
                        Log.d("bedstead-settings", "SettingsPreferenceServiceClient initialized")
                        connectionLatch.countDown()
                    }

                    override fun onError(error: Exception) {
                        throw IllegalStateException("Binding failed")
                    }
                }
            )
            if (!connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Binding timeout")
            }

            return client ?: throw IllegalStateException("client shouldn't be null")
        }
    }

    private fun initMetadata(): List<SettingsPreferenceMetadata> {
        var metadataList: List<SettingsPreferenceMetadata>? = null
        val connectionLatch = CountDownLatch(1)
        TestApis.permissions().withPermission(Manifest.permission.READ_SYSTEM_PREFERENCES).use {
            client.getAllPreferenceMetadata(
                MetadataRequest.Builder().build(),
                context.mainExecutor
            ) { result ->
                result.resultCode
                metadataList = result.metadataList
                connectionLatch.countDown()
            }

            if (!connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Binding timeout")
            }
        }
        return metadataList ?: throw IllegalStateException("metadataList shouldn't be null")
    }

    override fun teardownShareableState() {
        if (clientInitialized) {
            client.close()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L
    }
}

fun DeviceState.settingsPreferenceServiceClient(): SettingsPreferenceServiceClient {
    return getDependency(SettingsPreferenceServiceClientComponent::class.java).client
}

fun DeviceState.settingsPreferenceAllMetadata(): List<SettingsPreferenceMetadata> {
    return getDependency(SettingsPreferenceServiceClientComponent::class.java).allMetadata
}
