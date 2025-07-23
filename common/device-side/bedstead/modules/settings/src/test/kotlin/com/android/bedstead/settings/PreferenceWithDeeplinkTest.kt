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

import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.util.Log
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.nene.TestApis
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PreferenceWithDeeplinkTest {

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getPreference_withSensitivityDeeplinkOnly_checkLaunchIntent_shouldBeNotNull_andLaunchActivity(
        @SettingsPreferenceMetadataParameter(writeSensitivity = [SettingsPreferenceMetadata.DEEPLINK_ONLY])
        argument: SettingsPreferenceMetadata) {

        argument.launchIntent?.let { deeplink ->
            checkDeeplinkBehaviour(deeplink)
        } ?: AnnotationExecutorUtil.failOrSkip(
            "Deeplink doesn't exist for preference: $argument, but it should, as it is a " +
                    "preference with DEEPLINK_ONLY sensitivity.",
            FailureMode.FAIL
        )
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getPreference_withSensitivityNoDirectAccess_checkLaunchIntent_shouldBeNull(
        @SettingsPreferenceMetadataParameter(writeSensitivity = [SettingsPreferenceMetadata.NO_DIRECT_ACCESS])
        argument: SettingsPreferenceMetadata) {

        Truth.assertThat(argument.launchIntent).isNull()
    }

    @RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
    @Test
    fun getPreference_withLowOrNoSensitivity_andWithExistingLaunchIntent_shouldLaunchActivity(
        @SettingsPreferenceMetadataParameter(
            writeSensitivity = [SettingsPreferenceMetadata.NO_SENSITIVITY, SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION],
            otherFilters = [SettingsPreferenceMetadataParameter.PREFERENCE_FILTER_LAUNCH_INTENT_NOT_NULL]
        ) argument: SettingsPreferenceMetadata) {

        checkDeeplinkBehaviour(argument.launchIntent!!)
    }

    private fun checkDeeplinkBehaviour(deeplink: Intent) {
        deeplink.component?.let {
            TestApis.activities().run {
                clearAllActivities()
                val pastForegroundActivity = foregroundActivity()

                try {
                    startActivity(deeplink)
                    val currentForegroundActivity = foregroundActivity()
                    Truth.assertThat(currentForegroundActivity).isNotNull()
                    Truth.assertThat(currentForegroundActivity).isNotEqualTo(pastForegroundActivity)
                } catch (ex: SecurityException) {
                    Log.d(TAG, "App tried to launch an intent, but couldn't due to missing " +
                            "permission(s). Which is enough for this test. Got: ${ex.cause}, ${ex.message}")
                }
            }
        } ?: AnnotationExecutorUtil.failOrSkip(
            "No component present on the device to handle such an intent",
            FailureMode.SKIP
        )
    }

    companion object {
        private const val TAG = "PreferenceWithDeeplinkTest"
    }

}