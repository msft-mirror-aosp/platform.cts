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

import android.content.Intent
import java.net.URISyntaxException

/**
 * A human-readable representation of AppFunction's PerScreenDeviceStates.
 */
data class PerScreenDeviceStates(
    val key: String?,
    val intentUri: String?,
    val deviceStateItems: List<DeviceStateItem>,
    val description: String
) {

    /**
     * @return [Intent] object created from [intentUri].
     */
    @Throws(URISyntaxException::class)
    fun intent(): Intent? {
        return intentUri?.let {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }
    }

    override fun toString(): String = key ?: "description=$description"
}

/**
 * A human-readable representation of AppFunction's DeviceStateItem.
 */
data class DeviceStateItem(
    val key: String,
    val jsonValue: String?,
    val name: LocalizedString?,
    val purpose: String?
) {
    override fun toString() = key
}

/**
 * A human-readable representation of AppFunction's LocalizedString.
 */
data class LocalizedString(
    val english: String,
    val localized: String
)

/**
 * A human-readable representation of AppFunction's PerScreenMetadata.
 */
data class PerScreenMetadata(
    val key: String? = null,
    val description: String,
    val intentUri: String? = null,
    val deviceStateItemsMetadata: List<DeviceStateItemMetadata> = listOf()
)

/**
 * A human-readable representation of AppFunction's DeviceStateItemMetadata.
 */
data class DeviceStateItemMetadata(
    val key: String,
    val name: LocalizedString?,
    val possibleValues: String?,
    val purpose: String?,
    val writable: Boolean
) {

    /**
     * A list of all possible values for the item.
     */
    val possibleValuesList: List<String>? by lazy {
        possibleValues?.split(',')?.map {
            val index = it.indexOf(" (")
            if (index != -1) {
                it.substring(0, index).trim()
            } else {
                it
            }
        }
    }
}

/**
 * A human-readable representation of AppFunction's SetDeviceStateItemResponse.
 */
data class SetDeviceStateItemResponse(
    val currentValue: String,
    val failureReason: String?,
    val isSuccessful: Boolean
)
