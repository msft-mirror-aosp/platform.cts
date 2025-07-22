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

import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.settings.preferences.SettingsPreferenceMetadata
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.AfterClass
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class SettingsParameterizedTestWithArgumentGeneratorTest {

    @Test
    @RequireFlagsEnabled(FLAG_SETTINGS_CATALYST)
    fun test(@SettingsPreferenceMetadataParameter argument: SettingsPreferenceMetadata) {
        numberOfTestRuns++
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        var numberOfTestRuns = 0

        @AfterClass
        @JvmStatic
        fun checkTheNumberOfTestRuns() {
            val flagValueProvider = DeviceFlagsValueProvider()
            if (flagValueProvider.getBoolean(FLAG_SETTINGS_CATALYST)) {
                Truth.assertThat(numberOfTestRuns).isEqualTo(
                    deviceState.getSettingsPreferenceRepository().allMetadata.size
                )
            }
        }
    }
}
