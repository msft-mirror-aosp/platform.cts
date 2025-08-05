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

import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION
import android.service.settings.preferences.SettingsPreferenceMetadata.NO_SENSITIVITY
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class NotEmptyPermissionTest {

    @Test
    fun getValueResultWhenReadPermissionsNotEmpty_ResultOKReturned(
        @SettingsPreferenceMetadataParameter(
            otherFilters = [PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY],
            skipUnsupportedPreferences = true
        )
        argument: SettingsPreferenceMetadata
    ) {
        val getValueResult = deviceState.getSettingsPreferenceRepository().getValueResult(
            metadata = argument,
            grantRequiredPermissions = true
        )

        assertThat(getValueResult.resultCode).isEqualTo(GetValueResult.RESULT_OK)
    }

    @Test
    fun setValueResultWhenWritePermissionsNotEmpty_ResultOKReturned(
        @SettingsPreferenceMetadataParameter(
            writeSensitivity = [NO_SENSITIVITY, EXPECT_POST_CONFIRMATION],
            otherFilters = [PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY],
            skipUnsupportedPreferences = true
        )
        argument: SettingsPreferenceMetadata
    ) {
        val getValueResult = deviceState
            .getSettingsPreferenceRepository()
            .getValueResult(argument, grantRequiredPermissions = true)

        val setValueResult = deviceState
            .getSettingsPreferenceRepository()
            .setValueResult(
                argument,
                getValueResult.value!!.arbitraryValue(),
                grantRequiredPermissions = true
            )

        assertThat(setValueResult.resultCode).isEqualTo(SetValueResult.RESULT_OK)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
