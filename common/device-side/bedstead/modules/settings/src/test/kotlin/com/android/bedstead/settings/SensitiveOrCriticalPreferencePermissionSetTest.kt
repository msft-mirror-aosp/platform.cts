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

import android.service.settings.preferences.SetValueResult.RESULT_DISALLOW
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceMetadata.DEEPLINK_ONLY
import android.service.settings.preferences.SettingsPreferenceMetadata.NO_DIRECT_ACCESS
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class SensitiveOrCriticalPreferencePermissionSetTest {

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    fun setPreferenceValueWhenSensitiveOrCriticalPermissionsGranted_ResultDisallowReturned(
        @SettingsPreferenceMetadataParameter(
            writeSensitivity = [DEEPLINK_ONLY, NO_DIRECT_ACCESS],
            skipUnsupportedPreferences = true
        )
        argument: SettingsPreferenceMetadata,
    ) {
        val valueResult = deviceState.getSettingsPreferenceRepository().getValueResult(
            argument,
            grantRequiredPermissions = true
        )

        val setValueResult =
            deviceState.getSettingsPreferenceRepository().setValueResult(
                argument,
                valueResult.value!!,
                grantRequiredPermissions = true
            )

        assertThat(setValueResult.resultCode).isEqualTo(RESULT_DISALLOW)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
