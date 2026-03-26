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

import android.service.settings.preferences.SettingsPreferenceMetadata
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.UsesParameterizedTestWithArgumentGenerator
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.settings.SETTINGS_PACKAGE_NAME
import com.android.bedstead.settings.SettingsParameterizedTestWithArgumentGenerator

/**
 * Mark a [DeviceStateItemMetadata] parameter as being parameterized with all available preferences.
 *
 * You must be using the [BedsteadJUnit4] test runner to use this annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@UsesParameterizedTestWithArgumentGenerator(
    SettingsParameterizedTestWithArgumentGenerator::class
)
annotation class DeviceStateItemMetadataParameter(
    /**
     * Package name of the application for which tests will be generated.
     */
    val packageName: String = SETTINGS_PACKAGE_NAME,

    /**
     * If the value is different from ANY, preferences will be filtered using
     * [SettingsPreferenceMetadata.isWritable] value.
     */
    val isWritable: OptionalBoolean = OptionalBoolean.ANY,
)
