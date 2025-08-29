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
 * limitations under the License
 */

package android.server.wm

/**
 * A specification for asserting the count of a given event.
 *
 * This class encapsulates a validation rule and an expected count for a specific event,
 * such as an activity lifecycle callback. It is primarily used in tests to define
 * an expected outcome.
 *
 * @param event the event being monitored, e.g. `ActivityCallback.ON_CREATE`.
 * @param rule the validation rule to apply to the event count (e.g. `EQUALS`, `GREATER_THAN`).
 * @param count the expected count to validate against.
 * @param message an optional custom message for assertion failures. If null, a default
 * message is generated based on the event and rule.
 */
class CountSpec<T> private constructor(
    val event: T,
    rule: Rule,
    private val count: Int,
    message: String? = null,
) {
    private val rule: Rule = if (count == DONT_CARE_COUNT) Rule.DONT_CARE else rule

    /** The validation message to display on failure. */
    val message: String = rule.formatMessage(event.toString(), count).let { baseMessage ->
        if (message != null) "$message $baseMessage" else baseMessage
    }

    /** @return `true` if the given value is satisfied the condition. */
    fun validate(value: Int): Boolean = rule.validate(actual = value, expected = count)

    /** Defines the comparison logic for validating an event count. */
    enum class Rule(private val descriptionFormat: String) {
        DONT_CARE("is not checked"),
        EQUALS("must be %d"),
        GREATER_THAN("must be > %d"),
        LESS_THAN("must be < %d"),
        GREATER_THAN_OR_EQUALS("must be >= %d");

        internal fun formatMessage(eventName: String, count: Int): String {
            return "Count of $eventName event ${descriptionFormat.format(count)}"
        }

        internal fun validate(actual: Int, expected: Int): Boolean = when (this) {
            DONT_CARE -> true
            EQUALS -> actual == expected
            GREATER_THAN -> actual > expected
            LESS_THAN -> actual < expected
            GREATER_THAN_OR_EQUALS -> actual >= expected
        }
    }

    companion object {
        /**
         * A special value for the `count` parameter that indicates the event count should not be
         * checked. When this value is provided to the constructor, the [rule] is automatically set
         * to [Rule.DONT_CARE], and any validation will always pass.
         */
        const val DONT_CARE_COUNT = Int.MIN_VALUE

        /** Creates a [CountSpec] to assert count equals with custom message. */
        @JvmOverloads
        @JvmStatic
        fun <T> T.hasCountEquals(count: Int, message: String? = null): CountSpec<T> {
            return CountSpec(this, Rule.EQUALS, count, message)
        }

        /** Creates a [CountSpec] to assert count is greater than with custom message. */
        @JvmOverloads
        @JvmStatic
        fun <T> T.hasCountGreaterThan(count: Int, message: String? = null): CountSpec<T> {
            return CountSpec(this, Rule.GREATER_THAN, count, message)
        }

        /** Creates a [CountSpec] to assert count is less than with custom message. */
        @JvmOverloads
        @JvmStatic
        fun <T> T.hasCountLessThan(count: Int, message: String? = null): CountSpec<T> {
            return CountSpec(this, Rule.LESS_THAN, count, message)
        }

        /** Creates a [CountSpec] to assert count is greater than or equals with custom message. */
        @JvmOverloads
        @JvmStatic
        fun <T> T.hasCountGreaterThanOrEquals(count: Int, message: String? = null): CountSpec<T> {
            return CountSpec(this, Rule.GREATER_THAN_OR_EQUALS, count, message)
        }
    }
}
