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

import android.service.settings.preferences.SettingsPreferenceMetadata
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.UsesParameterizedTestWithArgumentGenerator

/**
 * Mark a [SettingsPreferenceMetadata] parameter as being parameterised with all available
 * preferences. Optionally, you can use filters to handle only preferences that meet specific
 * conditions.
 *
 * You must be using the [BedsteadJUnit4] test runner to use this annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@UsesParameterizedTestWithArgumentGenerator(UsesParameterizedTestWithArgumentGenerator.SETTINGS)
annotation class SettingsPreferenceMetadataParameter(
    /**
     * Package name of the application for which tests will be generated.
     */
    val packageName: String = SETTINGS_PACKAGE_NAME,

    /**
     * Generates test only for preferences with any of these write sensitivity values.
     */
    val writeSensitivity: IntArray = [],

    /**
     * Generates test only for preferences that meet all of the declared filters.
     */
    val otherFilters: IntArray = [],

    /**
     * Skip unsupported preferences. Caution! This parameter is very expensive!
     */
    // TODO(karzelek) cache it or make it available in SettingsPreferenceMetadata
    val skipUnsupportedPreferences: Boolean = false
) {
    companion object {
        const val PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY: Int = 0
        const val PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY: Int = 1
        const val PREFERENCE_FILTER_LAUNCH_INTENT_NOT_NULL: Int = 2
    }
}
