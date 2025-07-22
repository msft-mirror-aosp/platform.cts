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
import android.service.settings.preferences.SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION
import android.service.settings.preferences.SettingsPreferenceMetadata.NO_SENSITIVITY
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth.assertWithMessage
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MissingPermissionTest {

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @EnsureHasPermission(READ_SYSTEM_PREFERENCES)
    // TODO(b/433713114) there is a bug  with getting the values with missing permission
    fun getPreferenceValueWhenMissingPermissions_RequireAppPermissionResultCodeReturned(
        @SettingsPreferenceMetadataParameter(
            otherFilters = [PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY],
            skipUnsupportedPreferences = true
        )
        argument: SettingsPreferenceMetadata,
    ) {
        argument.readPermissions.forEach { missingPermission ->
            permissions()
                .withPermission(*argument.readPermissions.minus(missingPermission).toTypedArray())
                .withoutPermission(missingPermission).use {
                    val getValueResult = deviceState
                        .getSettingsPreferenceRepository()
                        .getValueResult(argument, grantRequiredPermissions = false)

                    assertWithMessage(
                        "resultCode: ${getValueResult.resultCode}, " +
                                "missingPermission: $missingPermission, " +
                                "readPermissions: ${argument.readPermissions}"
                    ).that(
                        getValueResult.resultCode
                    ).isEqualTo(GetValueResult.RESULT_REQUIRE_APP_PERMISSION)
                }
        }
    }

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @EnsureHasPermission(READ_SYSTEM_PREFERENCES, WRITE_SYSTEM_PREFERENCES)
    //TODO (b/433713902) it's impossible to know if all of the write permissions are required
    // or any of them
    fun setPreferenceValueWhenMissingPermissions_RequireAppPermissionResultCodeReturned(
        @SettingsPreferenceMetadataParameter(
            writeSensitivity = [NO_SENSITIVITY, EXPECT_POST_CONFIRMATION],
            otherFilters = [PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY],
            skipUnsupportedPreferences = true
        )
        argument: SettingsPreferenceMetadata,
    ) {
        val valueResult = deviceState.getSettingsPreferenceRepository().getValueResult(
            argument,
            grantRequiredPermissions = true
        )
        argument.writePermissions.forEach { missingPermission ->
            permissions()
                .withPermission(*argument.writePermissions.minus(missingPermission).toTypedArray())
                .withoutPermission(missingPermission).use {
                    val setValueResult = deviceState
                        .getSettingsPreferenceRepository()
                        .setValueResult(
                            argument,
                            valueResult.value!!,
                            grantRequiredPermissions = false
                        )

                    assertWithMessage(
                        "resultCode: ${setValueResult.resultCode}, " +
                                "missingPermission: $missingPermission, " +
                                "writePermissions: ${argument.writePermissions}"
                    ).that(
                        setValueResult.resultCode
                    ).isEqualTo(SetValueResult.RESULT_REQUIRE_APP_PERMISSION)
                }
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
