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

import com.android.bedstead.nene.utils.Assert.assertThrows
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
@RunWith(JUnit4::class)
class BedsteadServiceLocatorTest {

    @Test
    fun emptyDI_getDependencyThrowsException() {
        class ClassNotSupportedByBedsteadServiceLocatorReflection(parameter: String)
        val locator = BedsteadServiceLocator()

        assertThrows(IllegalStateException::class.java) {
            locator.get(ClassNotSupportedByBedsteadServiceLocatorReflection::class)
        }
        assertThrows(IllegalStateException::class.java) {
            locator.get<ClassNotSupportedByBedsteadServiceLocatorReflection>()
        }
        assertThrows(IllegalStateException::class.java) {
            locator.get(ClassNotSupportedByBedsteadServiceLocatorReflection::class.java)
        }
    }

    @Test
    fun callGetFewTimes_executeGetDependencyOnce_dependencyInstanceIsReused() {
        class ExampleClass

        val locator = BedsteadServiceLocator()
        var howManyTimesExecuted = 0
        locator.loadModules(object : BedsteadServiceLocator.Module {
            override fun <T : Any> getDependency(clazz: KClass<T>): T? {
                howManyTimesExecuted += 1
                return if (clazz == ExampleClass::class) {
                    ExampleClass() as T
                } else {
                    null
                }
            }
        })

        val result1 = locator.get(ExampleClass::class)
        val result2 = locator.get(ExampleClass::class)
        val result3 = locator.get(ExampleClass::class.java)
        val result4 = locator.get<ExampleClass>()

        assertThat(result1).isSameInstanceAs(result2)
        assertThat(result1).isSameInstanceAs(result3)
        assertThat(result1).isSameInstanceAs(result4)
        assertThat(howManyTimesExecuted).isEqualTo(1)
        assertThat(locator.getAllDependencies().size).isEqualTo(1)
    }

    @Test
    fun getClassWithoutTheModule_classesAreCreatedByReflection() {
        class ClassWithoutParameters
        class ClassWithLocatorParameter(locator: BedsteadServiceLocator)
        class ExampleClass(locator: BedsteadServiceLocator) {
            val classWithoutParameters: ClassWithoutParameters = locator.get()
            val classWithLocatorParameter: ClassWithLocatorParameter = locator.get()
        }

        val locator = BedsteadServiceLocator()
        val exampleClass: ExampleClass = locator.get()

        assertThat(exampleClass.classWithoutParameters).isNotNull()
        assertThat(exampleClass.classWithLocatorParameter).isNotNull()
        assertThat(locator.getAllDependencies().size).isEqualTo(3)
    }
}
