/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.harrier

import com.android.bedstead.multiuser.UsersComponent
import kotlin.reflect.KClass

class MainLocatorModule(private val deviceState: DeviceState) : BedsteadServiceLocator.Module {
    override fun <T : Any> getDependency(clazz: KClass<T>): T? {
        val dependency: Any? = when (clazz) {
            UsersComponent::class -> UsersComponent(deviceState)
            DeviceState::class -> deviceState
            else -> null
        }
        @Suppress("UNCHECKED_CAST") // the compiler can't check generic types
        return dependency as T?
    }
}
