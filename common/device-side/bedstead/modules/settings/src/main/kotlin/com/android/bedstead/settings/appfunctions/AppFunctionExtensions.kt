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

import android.app.appsearch.GenericDocument

/**
 * Extracts [DeviceStateItem] from device state item result.
 */
fun GenericDocument.asDeviceStateItemResult(): DeviceStateItem {
    return getPropertyDocumentArray(
        "androidAppfunctionsReturnValue.deviceStateItem"
    )!!.first().asDeviceStateItem()
}

/**
 * Extracts [SetDeviceStateItemResponse] from set device state item result.
 */
fun GenericDocument.asSetDeviceStateItemResult(): SetDeviceStateItemResponse {
    return getPropertyDocumentArray(
        "androidAppfunctionsReturnValue"
    )!!.first().asSetDeviceStateItemResponse()
}

internal fun GenericDocument.asDeviceStateMetadataResult(): List<PerScreenMetadata> =
    getPropertyDocumentArray(
        "androidAppfunctionsReturnValue.perScreenMetadata"
    )!!.map {
        it.asPerScreenMetadata()
    }

internal fun GenericDocument.asDeviceStateResult(): List<PerScreenDeviceStates> =
    getPropertyDocumentArray(
        "androidAppfunctionsReturnValue.perScreenDeviceStates"
    )!!.map {
        it.asPerScreenDeviceStates()
    }

private fun GenericDocument.asPerScreenDeviceStates(): PerScreenDeviceStates {
    val description = getPropertyString("description")!!
    val closingBracketIndex = description.indexOf(']')
    return PerScreenDeviceStates(
        key = description.takeIf {
            it.startsWith(KEY_PREFIX)
        }?.substring(KEY_PREFIX.length, closingBracketIndex),
        intentUri = getPropertyString("intentUri"),
        deviceStateItems = getPropertyDocumentArray("deviceStateItems")?.map {
            it.asDeviceStateItem()
        } ?: emptyList(),
        description = description.substring(closingBracketIndex + 1)
    )
}

private fun GenericDocument.asDeviceStateItem() = DeviceStateItem(
    key = getPropertyString("key")!!,
    jsonValue = getPropertyString("jsonValue"),
    name = getPropertyDocument("name")?.asLocalizedString(),
    purpose = getPropertyString("purpose")
)

private fun GenericDocument.asLocalizedString() = LocalizedString(
    english = getPropertyString("english")!!,
    localized = getPropertyString("localized")!!,
)

private fun GenericDocument.asPerScreenMetadata() = PerScreenMetadata(
    description = getPropertyString("description")!!,
    intentUri = getPropertyString("intentUri"),
    deviceStateItemsMetadata = getPropertyDocumentArray("deviceStateItemsMetadata")?.map {
        it.asDeviceStateItemMetadata()
    } ?: listOf()
)

private fun GenericDocument.asDeviceStateItemMetadata() = DeviceStateItemMetadata(
    key = getPropertyString("key")!!,
    name = getPropertyDocument("name")?.asLocalizedString(),
    possibleValues = getPropertyString("possibleValues"),
    purpose = getPropertyString("purpose"),
    writable = getPropertyBoolean("writable")
)

private fun GenericDocument.asSetDeviceStateItemResponse() = SetDeviceStateItemResponse(
    currentValue = getPropertyString("currentValue")!!,
    failureReason = getPropertyString("failureReason"),
    isSuccessful = getPropertyBoolean("isSuccessful")
)

private const val KEY_PREFIX = "[key="
