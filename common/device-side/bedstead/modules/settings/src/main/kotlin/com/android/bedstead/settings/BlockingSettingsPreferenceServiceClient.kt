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

import android.Manifest.permission.READ_SYSTEM_PREFERENCES
import android.Manifest.permission.WRITE_SYSTEM_PREFERENCES
import android.os.OutcomeReceiver
import android.service.settings.preferences.GetValueRequest
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.MetadataRequest
import android.service.settings.preferences.SetValueRequest
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceServiceClient
import android.service.settings.preferences.SettingsPreferenceValue
import android.util.Log
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.utils.BlockingCallback.DefaultBlockingCallback
import java.util.concurrent.TimeUnit

/**
 * A blocking wrapper of [SettingsPreferenceServiceClient] (with a limited functionality).
 */
class BlockingSettingsPreferenceServiceClient(val packageName: String) {

    private val context by lazy { TestApis.context().instrumentedContext() }
    internal val client: SettingsPreferenceServiceClient by lazy { initClient() }

    /**
     * Cached result of [SettingsPreferenceServiceClient.getAllPreferenceMetadata].
     */
    val allMetadata: List<SettingsPreferenceMetadata> by lazy { initMetadata() }

    private fun initClient(): SettingsPreferenceServiceClient {
        val callback = DefaultBlockingCallback<SettingsPreferenceServiceClient>()
        permissions().withPermission(READ_SYSTEM_PREFERENCES).use {
            SettingsPreferenceServiceClient(
                context,
                packageName,
                context.mainExecutor,
                object : OutcomeReceiver<SettingsPreferenceServiceClient, Exception> {
                    override fun onResult(result: SettingsPreferenceServiceClient) {
                        Log.d(LOG_TAG, "SettingsPreferenceServiceClient initialized")
                        callback.triggerCallback(result)
                    }

                    override fun onError(error: Exception) {
                        throw IllegalStateException("Binding failed", error)
                    }
                }
            )
            return callback.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun initMetadata(): List<SettingsPreferenceMetadata> {
        val callback = DefaultBlockingCallback<List<SettingsPreferenceMetadata>>()
        permissions().withPermission(READ_SYSTEM_PREFERENCES).use {
            client.getAllPreferenceMetadata(
                MetadataRequest.Builder().build(),
                context.mainExecutor
            ) { result ->
                callback.triggerCallback(result.metadataList)
            }

            return callback.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    /**
     * A blocking wrapper of [SettingsPreferenceServiceClient.getPreferenceValue]
     */
    fun getValueResult(
        metadata: SettingsPreferenceMetadata,
        grantRequiredPermissions: Boolean = true
    ): GetValueResult {
        if (grantRequiredPermissions) {
            permissions().withPermission(
                *metadata.readPermissions.toTypedArray().plus(READ_SYSTEM_PREFERENCES)
            ).use {
                return getValueResultInternal(metadata)
            }
        } else {
            return getValueResultInternal(metadata)
        }
    }

    private fun getValueResultInternal(
        metadata: SettingsPreferenceMetadata
    ): GetValueResult {
        val callback = DefaultBlockingCallback<GetValueResult>()

        client.getPreferenceValue(
            GetValueRequest.Builder(metadata.screenKey, metadata.key).build(),
            context.mainExecutor
        ) { result ->
            callback.triggerCallback(result)
        }

        return callback.await(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * A blocking wrapper of [SettingsPreferenceServiceClient.setPreferenceValue]
     */
    fun setValueResult(
        metadata: SettingsPreferenceMetadata,
        settingsPreferenceValue: SettingsPreferenceValue,
        grantRequiredPermissions: Boolean = true
    ): SetValueResult {
        if (grantRequiredPermissions) {
            permissions().withPermission(
                *READ_AND_WRITE_SYSTEM_PREFERENCES_PERMISSIONS.plus(metadata.writePermissions)
            ).use {
                return setValueResultInternal(metadata, settingsPreferenceValue)
            }
        } else {
            return setValueResultInternal(metadata, settingsPreferenceValue)
        }
    }

    private fun setValueResultInternal(
        metadata: SettingsPreferenceMetadata,
        settingsPreferenceValue: SettingsPreferenceValue
    ): SetValueResult {
        val callback = DefaultBlockingCallback<SetValueResult>()

        client.setPreferenceValue(
            SetValueRequest.Builder(
                metadata.screenKey,
                metadata.key,
                settingsPreferenceValue
            ).build(),
            context.mainExecutor
        ) { result ->
            callback.triggerCallback(result)
        }

        return callback.await(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val LOG_TAG = "bedstead-settings"
        private const val SHORT_TIMEOUT_SECONDS = 1L
        private const val TIMEOUT_SECONDS = 10L
        private val READ_AND_WRITE_SYSTEM_PREFERENCES_PERMISSIONS = arrayOf(
            READ_SYSTEM_PREFERENCES,
            WRITE_SYSTEM_PREFERENCES
        )
    }
}
