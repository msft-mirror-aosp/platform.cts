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
 * preferences.
 *
 * You must be using the [BedsteadJUnit4] test runner to use this annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@UsesParameterizedTestWithArgumentGenerator(UsesParameterizedTestWithArgumentGenerator.SETTINGS)
annotation class SettingsPreferenceMetadataParameter
