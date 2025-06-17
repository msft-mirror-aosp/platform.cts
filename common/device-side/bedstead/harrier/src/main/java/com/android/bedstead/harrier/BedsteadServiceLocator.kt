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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Registrar of dependencies for use by Bedstead modules.
 *
 * Use of this service locator allows for the single entry point to
 * bedstead while allowing modularisation and loose coupling.
 */
open class BedsteadServiceLocator {

    private val dependenciesMap = mutableMapOf<KClass<*>, Any>()

    /**
     * Obtains the instance of the given [clazz]
     * if you have circular dependencies use [getValue]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T {
        val existingInstance = dependenciesMap[clazz]
        return if (existingInstance != null) {
            existingInstance as T
        } else {
            createDependencyByReflection(clazz.java).also {
                dependenciesMap[clazz] = it
                onDependencyCreated(it)
            }
        }
    }

    /**
     * Executed after the [dependency] is created by reflection.
     */
    protected open fun <T : Any> onDependencyCreated(dependency: T) {}

    /**
     * See [BedsteadServiceLocator.get]
     */
    inline fun <reified T : Any> get(): T = get(T::class)

    /**
     * Obtains the instance of the given type when needed by delegated properties
     * example: val instance: Type by locator
     */
    inline operator fun <reified T : Any> getValue(thisRef: Any, property: KProperty<*>): T {
        return get<T>()
    }

    /**
     * See [BedsteadServiceLocator.get]
     */
    fun <T : Any> get(clazz: Class<T>): T = get(clazz.kotlin)

    /**
     * Obtains the instance of the given [className]
     * @param className – the fully qualified name of the desired class.
     */
    @Suppress("UNCHECKED_CAST")
    @CanIgnoreReturnValue
    fun <T : Any> get(className: String): T {
        try {
            return (get(Class.forName(className))) as T
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "Could not find dependency: $className. " +
                        "Make sure it is on the classpath and the appropriate module is loaded"
            )
        }
    }

    /**
     * Obtains the instance of the given [className] or null if it's not available
     * @param className – the fully qualified name of the desired class.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(className: String): T? {
        return try {
            (get(Class.forName(className))) as T
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private fun <T : Any> createDependencyByReflection(clazz: Class<T>): T {
        return try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (ignored: NoSuchMethodException) {
            try {
                clazz
                    .getDeclaredConstructor(BedsteadServiceLocator::class.java)
                    .newInstance(this)
            } catch (ignored: NoSuchMethodException) {
                throw IllegalStateException(
                    "$clazz doesn't have a constructor taking BedsteadServiceLocator as the only " +
                            "parameter or an empty constructor. " +
                            "Kotlin classes with init blocks can't be created by reflection. " +
                            "Provide the right constructor."
                )
            }
        }
    }

    /**
     * Get all loaded dependencies
     */
    fun getAllDependencies(): Collection<Any> {
        return dependenciesMap.values
    }

    /**
     * Get all loaded dependencies of type T
     */
    protected inline fun <reified T : Any> getAllDependenciesOfType(): List<T> {
        return getAllDependencies().filterIsInstance<T>()
    }

    /**
     * remove all dependencies in order to free some memory
     */
    fun clearDependencies() {
        dependenciesMap.clear()
    }
}
