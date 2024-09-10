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

import com.android.bedstead.nene.utils.FailureDumper
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Registrar of dependencies for use by Bedstead modules.
 *
 * Use of this service locator allows for the single [DeviceState] entry point to
 * bedstead while allowing modularisation and loose coupling.
 */
class BedsteadServiceLocator : DeviceStateComponent {

    private val loadedModules = mutableListOf<Module>()
    private val dependenciesMap = mutableMapOf<KClass<*>, Any>()

    /**
     * Obtains the instance of the given [clazz]
     * if you have circular dependencies use [getValue], or lazy delegate
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T {
        val existingInstance = dependenciesMap[clazz]
        return if (existingInstance != null) {
            existingInstance as T
        } else {
            createDependency(clazz).also {
                dependenciesMap[clazz] = it
            }
        }
    }

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

    private fun <T : Any> createDependency(clazz: KClass<T>): T {
        loadedModules.forEach {
            val dependency = it.getDependency(clazz)
            if (dependency != null) {
                return dependency
            }
        }
        return createDependencyByReflection(clazz.java)
    }

    private fun <T : Any> createDependencyByReflection(clazz: Class<T>): T {
        try {
            return clazz.getDeclaredConstructor().newInstance()
        } catch (ignored: NoSuchMethodException) {
            try {
                return clazz
                        .getDeclaredConstructor(BedsteadServiceLocator::class.java)
                        .newInstance(this)
            } catch (ignored: NoSuchMethodException) {
                throw IllegalStateException(
                    "Could not find the dependency in the loaded modules, and this class doesn't " +
                            "have a constructor taking BedsteadServiceLocator as the only " +
                            "parameter or an empty constructor. Make sure to load a module " +
                            "supporting this dependency (for example in AnnotationExecutor) " +
                            "or provide the right constructor."
                )
            }
        }
    }

    /**
     * Load one or more Bedstead Service Locator modules
     * to make its objects available via [createDependency]
     */
    fun loadModules(vararg modules: Module) {
        modules.forEach { module ->
            if (!loadedModules.any { it.javaClass == module.javaClass }) {
                loadedModules.add(module)
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
    inline fun <reified T : Any> getAllDependenciesOfType(): List<T> {
        return getAllDependencies().filterIsInstance<T>()
    }

    /**
     * Get all loaded FailureDumpers
     */
    fun getAllFailureDumpers(): List<FailureDumper> {
        return getAllDependenciesOfType<FailureDumper>()
    }

    override fun teardownShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            it.teardownShareableState()
        }
    }

    override fun teardownNonShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            it.teardownNonShareableState()
        }
    }

    override fun prepareTestState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            it.prepareTestState()
        }
    }

    override fun releaseResources() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            it.releaseResources()
        }
    }

    /**
     * Module is a source of dependencies for Service Locator
     */
    interface Module {
        /**
         * Returns instance of the class specified in clazz parameter or null
         * if this class is not provided by this module
         */
        fun <T : Any> getDependency(clazz: KClass<T>): T?
    }
}
