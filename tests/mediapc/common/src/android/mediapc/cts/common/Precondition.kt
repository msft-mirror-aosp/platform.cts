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
package android.mediapc.cts.common

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Preconditions for Media Performance Class tests.
 *
 * Used in [PerformanceClassTestRule].
 */
interface Precondition {

    /** The message to display when the precondition fails. */
    val message: String

    /**
     * Whether the precondition passes.
     *
     * This may be called multiple times.  Expensive preconditions should use the lazy variant.
     */
    val meetsPrecondition: Boolean

    /**
     * The failure message or null if the precondition passes.
     *
     *
     *  Getting this value implicitly tests the precondition, if a lazy precondition is used it
     *  will evaluated.
     */
    val failureMessage: String?
        get() {
            return if (meetsPrecondition) null else message
        }

    /**
     * The minimum performance class level this precondition is required for.
     */
    val minPerformanceClassLevel: Int

    /**
     * The Media Performance Class level of the failure.
     *
     * If the declared media performance class is greater than or equal this value the precondition
     * failure is considered a test failure.
     *
     * Returns Int.MAX_VALUE if the precondition passes.
     *
     *  Getting this value implicitly tests the precondition, if a lazy precondition is used it
     *  will evaluated.
     */
    val failurePerformanceClassLevel: Int
        get() {
            return if (meetsPrecondition) Int.MAX_VALUE else minPerformanceClassLevel
        }

    /** Creates a new precondition that first tests this precondition, then tests the next, reporting the first failure. */
    fun then(next: Precondition): Precondition {
        return ChainedPreconditions(this, next)
    }

    companion object {

        /** Returns a Precondition that checks each precondition in the order given. */
        @JvmStatic
        fun inOrder(vararg preconditions: Precondition) = inOrder(preconditions.asList())

        /** Returns a Precondition that checks each precondition in the order given. */
        @JvmStatic
        fun inOrder(preconditions: List<Precondition>): Precondition {
            if (preconditions.isEmpty()) {
                return EMPTY
            }
            if (preconditions.size == 1) {
                return preconditions.first()
            }
            return ChainedPreconditions(preconditions)
        }

        /** Creates a precondition that always passes or fails. */
        @JvmStatic
        @JvmOverloads
        fun create(
            message: String,
            value: Boolean,
            minPerformanceClassLevel: Int = 30
        ): Precondition {
            return ConstPrecondition(message, value, minPerformanceClassLevel)
        }

        /** Creates a precondition using the given function. */
        @JvmStatic
        fun create(message: String, fn: () -> Boolean) = create(message, 30, fn)

        /** Creates a precondition using the given function with a minPerformanceClas of 30. */
        @JvmStatic
        fun create(
            message: String,
            minPerformanceClassLevel: Int,
            fn: () -> Boolean
        ): Precondition {
            return FnPrecondition(message, minPerformanceClassLevel, fn)
        }

        /** Creates a precondition using the given lazy function. */
        @JvmStatic
        @JvmOverloads
        fun create(
            message: String,
            lazyMeets: Lazy<Boolean>,
            minPerformanceClassLevel: Int = 30
        ): Precondition {
            return LazyPrecondition(
                message = message,
                lazyMeets = lazyMeets,
                minPerformanceClassLevel
            )
        }

        /** Creates a precondition that uses the lazy wrapped given function with a minPerformanceClas of 30. */
        @JvmStatic
        fun createLazy(message: String, fn: () -> Boolean) = createLazy(message, 30, fn)

        /** Creates a precondition that uses the lazy wrapped given function. */
        @JvmStatic
        fun createLazy(
            message: String,
            minPerformanceClassLevel: Int,
            fn: () -> Boolean
        ): Precondition {
            return LazyPrecondition(message, minPerformanceClassLevel, fn)
        }

        /** Creates a precondition that fails with the given message if the system feature is not available. */
        @JvmStatic
        @JvmOverloads
        fun requireSystemFeature(
            feature: String,
            minPerformanceClassLevel: Int = 30
        ): Precondition {
            return create("System feature $feature is not available", minPerformanceClassLevel) {
                InstrumentationRegistry.getInstrumentation().context.getPackageManager()
                    .hasSystemFeature(feature)
            }
        }
    }
}

private class ChainedPreconditions(val preconditions: List<Precondition> = emptyList()) :
    Precondition {
    constructor(vararg preconditions: Precondition) : this(flatten(preconditions.toList()))
    override val message: String
        get() {
            return preconditions.joinToString(" then ") { '"' + it.message + '"' }
        }

    override val minPerformanceClassLevel: Int
        get() {
            if (preconditions.isEmpty()) {
                return Int.MAX_VALUE
            }
            return preconditions.minOf { it.minPerformanceClassLevel }
        }
    override fun then(next: Precondition): Precondition {
        val m = preconditions.toMutableList()
        if (next is ChainedPreconditions) {
            m.addAll(next.preconditions)
        } else {
            m.add(next)
        }
        return ChainedPreconditions(m)
    }

    override val meetsPrecondition
        get() = preconditions.all { it.meetsPrecondition }

    override val failureMessage
        get() = preconditions.firstOrNull { !it.meetsPrecondition }?.failureMessage

    override val failurePerformanceClassLevel: Int
        get() {
            if (meetsPrecondition) {
                return Int.MAX_VALUE
            }
           return preconditions.first { !it.meetsPrecondition }.minPerformanceClassLevel
        }
}

private fun flatten(preconditions: List<Precondition>): List<Precondition> {
    return preconditions.flatMap { p ->
        if (p is ChainedPreconditions) {
            flatten(p.preconditions)
        } else {
            listOf(p)
        }
    }
}

private class ConstPrecondition(
    override val message: String,
    value: Boolean,
    override val minPerformanceClassLevel: Int,
) : Precondition {
    override val meetsPrecondition = value
}

private class FnPrecondition(
    override val message: String,
    override val minPerformanceClassLevel: Int,
    val fn: () -> Boolean
) : Precondition {
    override val meetsPrecondition: Boolean
        get() {
            return fn.invoke()
        }
}

private class LazyPrecondition(

    override val message: String,
    val lazyMeets: Lazy<Boolean>,
    override val minPerformanceClassLevel: Int
) : Precondition {
    constructor(
        message: String,
        minPerformanceClassLevel: Int,
        fn: () -> Boolean
    ) : this(message, lazy { fn() }, minPerformanceClassLevel)

    override val meetsPrecondition: Boolean
        get() {
            return lazyMeets.value
        }
}
