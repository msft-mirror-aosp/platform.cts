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

import android.app.appfunctions.AppFunctionService
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.UsesParameterizedTestWithArgumentGenerator
import com.android.bedstead.settings.SETTINGS_PACKAGE_NAME
import com.android.bedstead.settings.SettingsParameterizedTestWithArgumentGenerator

/**
 * Mark a [DeviceStateItem] parameter as being parameterised with all available preferences.
 *
 * You must be using the [BedsteadJUnit4] test runner to use this annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@UsesParameterizedTestWithArgumentGenerator(
    SettingsParameterizedTestWithArgumentGenerator::class
)
annotation class DeviceStateItemsParameter(
    /**
     * Package name of the application for which tests will be generated.
     */
    val packageName: String = SETTINGS_PACKAGE_NAME,

    /**
     * A list of functionIdentifiers - they're used by the [AppFunctionService] from the
     * target app to uniquely identify the function to be invoked.
     */
    val functionIdentifiers: Array<String> = [
        "getUncategorizedDeviceState",
        "getStorageDeviceState",
        "getBatteryDeviceState",
        "getMobileDataUsageDeviceState",
//        "getPermissionsDeviceState",
        "getNotificationsDeviceState",
//        "getWellbeingDeviceState",
        "getAppsDeviceState"
    ],
)
