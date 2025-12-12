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

import android.service.settings.preferences.SettingsPreferenceValue
import android.service.settings.preferences.SettingsPreferenceValue.Builder
import android.service.settings.preferences.SettingsPreferenceValue.TYPE_BOOLEAN
import android.service.settings.preferences.SettingsPreferenceValue.TYPE_DOUBLE
import android.service.settings.preferences.SettingsPreferenceValue.TYPE_INT
import android.service.settings.preferences.SettingsPreferenceValue.TYPE_LONG
import android.service.settings.preferences.SettingsPreferenceValue.TYPE_STRING

/**
 * Creates a copy with an arbitrary value that is always different than the original one.
 *
 * The use case is testing of preference saving - to make sure that the saved preference is
 * different than the original one.
 */
fun SettingsPreferenceValue.arbitraryValue(): SettingsPreferenceValue {
    val builder = Builder(type)
    return when (type) {
        TYPE_BOOLEAN -> builder.setBooleanValue(booleanValue.not()).build()
        TYPE_DOUBLE -> {
            return if (doubleValue == 2.0) {
                builder.setDoubleValue(1.0).build()
            } else {
                builder.setDoubleValue(2.0).build()
            }
        }

        TYPE_INT -> builder.setIntValue(intValue + 1).build()
        TYPE_LONG -> builder.setLongValue(longValue + 1L).build()
        TYPE_STRING -> builder.setStringValue(stringValue + RANDOM_STRING).build()
        else -> throw IllegalStateException("unsupported type: $type")
    }
}

/**
 * Compares [SettingsPreferenceValue] values considering its type.
 */
fun SettingsPreferenceValue.isEqualTo(second: SettingsPreferenceValue?): Boolean {
    if (type != second?.type) {
        return false
    }

    return when (type) {
        TYPE_BOOLEAN -> booleanValue == second.booleanValue
        TYPE_DOUBLE -> doubleValue == second.doubleValue
        TYPE_INT -> intValue == second.intValue
        TYPE_LONG -> longValue == second.longValue
        TYPE_STRING -> stringValue == second.stringValue
        else -> throw IllegalStateException("unsupported type: $type")
    }
}

private const val RANDOM_STRING = "randomString"
