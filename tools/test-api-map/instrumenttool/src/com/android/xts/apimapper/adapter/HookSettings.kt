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

package com.android.xts.apimapper.adapter

import com.android.xts.apimapper.asm.ClassNodes
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

private const val TEST_CLASS_ACCESS_MASK = Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE or
        Opcodes.ACC_PROTECTED or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
private val JUNIT3_TEST_CLASS_NAMES = setOf(
    "junit/framework/TestCase",
    "android/test/AndroidTestCase",
    "android/test/InstrumentationTestCase"
)
private val JUNIT4_ANNOTATION_PREFIXED = arrayOf("org.junit")

private const val API_MAPPER_CLASS_PREFIX = "com/android/xts/apimapper/"

// Assume only classes (android.* or com.android.*) or jetpack libs will call android APIs.
// TODO(slotus): Use more general rule.
private val API_CALLER_CLASS_PREFIXES = arrayOf(
    "android/",
    "com/android/",
    "com/google/",
    "com/google/android/",
    "androidx/",
    "com/androidx/",
)

// Ignore classes that could introduce a lot of meaningless logs.
private val MEANINGLESS_API_CALLER_CLASS_PREFIXES = arrayOf(
    "androidx/test/",
    "androidx/tracing/Trace",
    "com/android/tradefed",
)

// Potential Android API classes.
private val API_CLASS_PREFIXES = arrayOf(
    "android/",
    "com/android/",
    "dalvik/",
    "libcore/",
)

// Android API classes that are meaningless to log.
private val MEANINGLESS_API_CLASS_PREFIXES = arrayOf(
    "org/junit/",
    "junit/",
    "org/mockito/",
    "kotlin/",
    "kotlinx/",
    "android/test/AndroidTestCase",
    "android/test/InstrumentationTestCase"
)

/** Decide whether the given class should be injected. */
fun shouldProcessClass(className: String): Boolean {
    return className.mayAndroidApiCallerClass()
}

/** Decide whether the class is a test class. */
fun ClassNode.isTestClass(classNodes: ClassNodes): Boolean {
    if (this.access and TEST_CLASS_ACCESS_MASK != 0) {
        return false
    }
    return isJunit3Test(classNodes) ||
            hasJunit4Annotation(classNodes)
}

/** Decide whether the class is a Junit3 test class. */
fun ClassNode.isJunit3Test(classNodes: ClassNodes): Boolean {
    var currentClassNode = classNodes.findClass(this.name)
    while (currentClassNode != null) {
        val superName = currentClassNode.superName
        if (superName in JUNIT3_TEST_CLASS_NAMES) {
            return true
        }
        currentClassNode = classNodes.findClass(superName)
    }
    return false
}

/** An interface used to decide whether a method call should be logged. */
interface HookSettings {
    fun shouldInjectHook(
        classNodes: ClassNodes,
        callerClass: String,
        opcode: Int,
        apiClass: String,
        apiMethod: String,
        apiDesc: String
    ): Boolean
}

class AndroidApiInjectionSettings : HookSettings {

    override fun shouldInjectHook(
        classNodes: ClassNodes,
        callerClass: String,
        opcode: Int,
        apiClass: String,
        apiMethod: String,
        apiDesc: String,
    ): Boolean {
        if (classNodes.hasClass(apiClass)) {
            return false
        }
        if (!callerClass.mayAndroidApiCallerClass()) {
            return false
        }
        // Never hook array methods.
        if (apiClass.startsWith("[")) {
            return false
        }
        // Don't call methods from Object.
        // This is because handling it correctly would be painful when calling into, e.g. clone(),
        // onto an array class.
        if (apiClass == "java/lang/Object") {
            return false
        }
        if (apiClass.isMeaninglessAndroidApiClass()) {
            return false
        }
        return apiClass.mayAndroidApiClass()
    }
}

private fun String.startsWithAny(prefixes: Array<String>): Boolean {
    prefixes.forEach {
        if (this.startsWith(it)) {
            return true
        }
    }
    return false
}

private fun String.isApiMapperClass(): Boolean {
    return this.startsWith(API_MAPPER_CLASS_PREFIX)
}

private fun String.mayAndroidApiCallerClass(): Boolean {
    // Hooking in this package could cause unexpected errors.
    if (this.startsWithAny(MEANINGLESS_API_CALLER_CLASS_PREFIXES)) {
        return false
    }
    // Always ignore ApiMapper classes.
    if (this.isApiMapperClass()) {
        return false
    }
    return this.startsWithAny(API_CALLER_CLASS_PREFIXES)
}

private fun String.mayAndroidApiClass(): Boolean {
    if (this.isApiMapperClass()) {
        return false
    }
    return this.startsWithAny(API_CLASS_PREFIXES)
}

private fun String.isMeaninglessAndroidApiClass(): Boolean {
    return this.startsWithAny(MEANINGLESS_API_CLASS_PREFIXES)
}

private fun anyJunit4Annotation(annotations: List<AnnotationNode>?): Boolean {
    if (annotations == null) {
        return false
    }
    annotations.forEach {
        val type = Type.getType(it.desc).className
        if (type.startsWithAny(JUNIT4_ANNOTATION_PREFIXED)) {
            return true
        }
    }
    return false
}

/** Check whether the class or its super classes is using Junit4 annotations. */
private fun ClassNode.hasJunit4Annotation(classNodes: ClassNodes): Boolean {
    var currentClassNode = classNodes.findClass(this.name)
    while (currentClassNode != null) {
        if (anyJunit4Annotation(currentClassNode.visibleAnnotations)) {
            return true
        }
        currentClassNode.methods.forEach {
            if (anyJunit4Annotation(it.visibleAnnotations)) {
                return true
            }
        }
        val superName = currentClassNode.superName
        currentClassNode = classNodes.findClass(superName)
    }
    return false
}
