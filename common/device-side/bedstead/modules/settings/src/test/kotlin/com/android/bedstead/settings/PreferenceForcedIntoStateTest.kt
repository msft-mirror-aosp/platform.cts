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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.settings.preferences.SettingsPreferenceValue
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.android.settingslib.flags.Flags.FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PreferenceForcedIntoStateTest {

    @get:Rule
    val checkFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @PreferenceTest(
        screenKey = "accessibility_color_and_motion",
        preferenceKey = "dark_ui_mode",
        valueType = SettingsPreferenceValue.TYPE_BOOLEAN,
        valueToSet = "true"
    )
    @Test
    fun getPreferenceForcedIntoState_sensitivityNone() {
        deviceState.getCurrentlySetPreferenceValue(
            screenKey = "accessibility_color_and_motion",
            preferenceKey = "dark_ui_mode"
        )?.run {
            Truth.assertThat(this.type).isEqualTo(SettingsPreferenceValue.TYPE_BOOLEAN)
            Truth.assertThat(this.booleanValue).isEqualTo(true)
        } ?: Assert.fail(FAILED_TEST_MESSAGE)
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @PreferenceTest(
        screenKey = "bluetooth_switchbar_screen",
        preferenceKey = "use_bluetooth",
        valueType = SettingsPreferenceValue.TYPE_BOOLEAN,
        valueToSet = "true"
    )
    @Test
    fun getPreferenceForcedIntoState_sensitivityExpectPostConfirmation() {
        deviceState.getCurrentlySetPreferenceValue(
            screenKey = "bluetooth_switchbar_screen",
            preferenceKey = "use_bluetooth"
        )?.run {
            Truth.assertThat(this.type).isEqualTo(SettingsPreferenceValue.TYPE_BOOLEAN)
            Truth.assertThat(this.booleanValue).isEqualTo(true)
        } ?: Assert.fail(FAILED_TEST_MESSAGE)
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @PreferenceTest(
        screenKey = "network_provider_and_internet_screen",
        preferenceKey = "airplane_mode_on",
        valueType = SettingsPreferenceValue.TYPE_BOOLEAN,
        valueToSet = "true"
    )
    @Test
    fun getPreferenceForcedIntoState_sensitivityDeeplinkOnly() {
        deviceState.getCurrentlySetPreferenceValue(
            screenKey = "accessibility_color_and_motion",
            preferenceKey = "dark_ui_mode"
        )?.run {
            Truth.assertThat(this.type).isEqualTo(SettingsPreferenceValue.TYPE_BOOLEAN)
            Truth.assertThat(this.booleanValue).isEqualTo(true)
        } ?: Assert.fail(FAILED_TEST_MESSAGE)
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST, FLAG_WRITE_SYSTEM_PREFERENCE_PERMISSION_ENABLED)
    @PreferenceTest(
        screenKey = "accessibility_color_and_motion",
        preferenceKey = "daltonizer_preference",
        valueType = SettingsPreferenceValue.TYPE_BOOLEAN,
        valueToSet = "true"
    )
    @Test
    fun getPreferenceForcedIntoState_sensitivityNoDirectAccess() {
        deviceState.getCurrentlySetPreferenceValue(
            screenKey = "accessibility_color_and_motion",
            preferenceKey = "dark_ui_mode"
        )?.run {
            Truth.assertThat(this.type).isEqualTo(SettingsPreferenceValue.TYPE_BOOLEAN)
            Truth.assertThat(this.booleanValue).isEqualTo(true)
        } ?: Assert.fail(FAILED_TEST_MESSAGE)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private const val FAILED_TEST_MESSAGE = "No new value set during the test's preparation"
    }
}
