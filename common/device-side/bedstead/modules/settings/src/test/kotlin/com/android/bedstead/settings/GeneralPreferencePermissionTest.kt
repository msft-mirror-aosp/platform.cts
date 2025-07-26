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
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceValue
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class GeneralPreferencePermissionTest {

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @EnsureDoesNotHavePermission(READ_SYSTEM_PREFERENCES)
    @Ignore("it's blocked by b/432173079")
    fun getPreferenceValueWhenNoPermissions_RequireAppPermissionResultCodeReturned(
        @SettingsPreferenceMetadataParameter argument: SettingsPreferenceMetadata,
    ) {
        val getValueResult = deviceState
            .getBlockingSettingsPreferenceServiceClient()
            .getValueResult(argument, grantRequiredPermissions = false)

        assertThat(getValueResult.resultCode).isEqualTo(
            GetValueResult.RESULT_REQUIRE_APP_PERMISSION
        )
    }

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @EnsureDoesNotHavePermission(WRITE_SYSTEM_PREFERENCES)
    @EnsureHasPermission(READ_SYSTEM_PREFERENCES)
    @Ignore("it's blocked by b/432173079")
    fun setPreferenceValueWhenNoPermissions_RequireAppPermissionResultCodeReturned(
        @SettingsPreferenceMetadataParameter argument: SettingsPreferenceMetadata,
    ) {
        val setValueResult = deviceState
            .getBlockingSettingsPreferenceServiceClient()
            .setValueResult(
                argument,
                SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING).build(),
                grantRequiredPermissions = false
            )

        assertThat(setValueResult.resultCode).isEqualTo(
            SetValueResult.RESULT_REQUIRE_APP_PERMISSION
        )
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
