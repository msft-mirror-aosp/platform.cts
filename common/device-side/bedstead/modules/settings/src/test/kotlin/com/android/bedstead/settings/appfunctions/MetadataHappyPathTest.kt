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

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.types.OptionalBoolean
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MetadataHappyPathTest {

    @Test
    fun getWritableDeviceItemMetadata_changingValueOfThePreferenceIsSuccessful(
        @DeviceStateItemMetadataParameter(isWritable = OptionalBoolean.TRUE)
        itemMetadata: DeviceStateItemMetadata
    ) {
        val possibleValuesList = itemMetadata.possibleValuesList
        assertThat(possibleValuesList).isNotNull()
        assertThat(possibleValuesList!!.size).isGreaterThan(1)

        val firstGetResponse = client.getDeviceStateItem(itemMetadata.key)
        assertThat(firstGetResponse?.resultDocument).isNotNull()

        val deviceStateItem = firstGetResponse!!.resultDocument.asDeviceStateItemResult()
        val originalValue = deviceStateItem.jsonValue!!
        val newValue = possibleValuesList.find { it != originalValue }!!

        val setResponse = client.setDeviceStateItem(itemMetadata.key, newValue)
        assertThat(setResponse).isNotNull()

        val deviceStateItemResponse = setResponse?.resultDocument?.asSetDeviceStateItemResult()

        assertThat(deviceStateItemResponse!!.failureReason).isNull()
        assertThat(deviceStateItemResponse.isSuccessful).isEqualTo(true)

        val secondGetResponse = client.getDeviceStateItem(
            itemMetadata.key
        )!!.resultDocument.asDeviceStateItemResult()
        assertThat(secondGetResponse.jsonValue).isEqualTo(newValue)

        val secondSetResponse = client.setDeviceStateItem(itemMetadata.key, originalValue)
        assertThat(
            secondSetResponse?.resultDocument?.asSetDeviceStateItemResult()?.isSuccessful
        ).isTrue()
    }

    companion object {
        val client = AppFunctionsBlockingClient()
    }
}
