/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.cts.input

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.android.compatibility.common.util.PollingCheck
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertNull
import org.junit.Assert.fail

private fun <T> getEvent(queue: BlockingQueue<T>, timeout: Duration): T? {
    return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
}

private fun <T> peekEvent(queue: BlockingQueue<T>, timeout: Duration): T? {
    var event: T? = null
    PollingCheck.waitFor(timeout.toMillis()) {
        event = queue.peek()
        return@waitFor event != null
    }
    return event
}

class BlockingQueueEventVerifier(val queue: BlockingQueue<InputEvent>) {
    fun assertReceivedMotion(matcher: Matcher<MotionEvent>, msg: String? = null) {
        val event = getEventOfType(MotionEvent::class.java, msg)
        assertThat(msg ?: "MotionEvent checks", event, matcher)
    }

    /**
     * Peeks at the next input event, and consumes it if it is a {@link MotionEvent} that matches
     * the given matcher. Otherwise, leaves it in the queue for the next assert or accept call.
     *
     * This is useful for skipping no-op events that might be present in a stream, such as {@code
     * MOVE} events that don't actually move any pointers.
     */
    fun acceptOptionalMotion(matcher: Matcher<MotionEvent>) {
        val event: InputEvent? = peekEvent(queue, Duration.ofMillis(5000))
        if (matcher.matches(event)) {
            queue.take()
        }
    }

    @JvmOverloads
    fun assertReceivedKey(matcher: Matcher<KeyEvent>, msg: String? = null) {
        val event = getEventOfType(KeyEvent::class.java, msg)
        assertThat(msg ?: "KeyEvent checks", event, matcher)
    }

    fun assertNoEvents() {
        val event = getEvent(queue, Duration.ofMillis(50))
        assertNull(event)
    }

    private fun <T : InputEvent> getEventOfType(eventType: Class<T>, msg: String?): T {
        val event = getEvent(queue, Duration.ofMillis(5000))
        if (event == null) {
            fail("${if (msg != null) msg + ": " else ""}Did not get an event")
        }
        if (!eventType.isInstance(event)) {
            val prefix = if (msg != null) msg + ": " else ""
            fail("${prefix}Instead of ${eventType.simpleName}, got $event")
        }
        return eventType.cast(event)!!
    }
}
