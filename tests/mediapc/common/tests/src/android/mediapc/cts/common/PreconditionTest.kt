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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [Precondition]. */
@RunWith(JUnit4::class)
class PreconditionTest {

    @Test
    fun chainedPrecondition_message() {
        val p = Precondition.create("1", true).then(Precondition.create("2", true))
        assertThat(p.message).isEqualTo("\"1\" then \"2\"")
    }

    @Test
    fun chainedPrecondition_allTrue_meetsPrecondition() {
        val p = Precondition.create("1", true).then(Precondition.create("2", true))
        assertThat(p.meetsPrecondition).isTrue()
        assertThat(p.failureMessage).isNull()
    }

    @Test
    fun chainedPrecondition_firstFalse_doesNotMeetPrecondition() {
        val p = Precondition.create("1", false)
            .then(Precondition.create("2", true))
        assertThat(p.meetsPrecondition).isFalse()
        assertThat(p.failureMessage).isEqualTo("1")
    }

    @Test
    fun chainedPrecondition_secondFalse_doesNotMeetPrecondition() {
        val p = Precondition
            .create("1", true)
            .then(Precondition.create("2", false))
        assertThat(p.meetsPrecondition).isFalse()
        assertThat(p.failureMessage).isEqualTo("2")
    }

    @Test
    fun chainedPrecondition_allFalse_doesNotMeetPrecondition() {
        val p = Precondition.create("1", false)
            .then(Precondition.create("2", false))
        assertThat(p.meetsPrecondition).isFalse()
        assertThat(p.failureMessage).isEqualTo("1")
    }

    @Test
    fun lazyPrecondition_true_meetsPrecondition() {
        var called = false
        val p = Precondition.createLazy("lazy", { called = true; true })
        assertThat(called).isFalse()
        assertThat(p.meetsPrecondition).isTrue()
        assertThat(called).isTrue()
    }

    @Test
    fun lazyPrecondition_message_lazyNotCalled() {
        var called = false
        val p = Precondition.createLazy("lazy") { called = true; true }
        assertThat(called).isFalse()
        assertThat(p.message).isEqualTo("lazy")
        assertThat(called).isFalse()
    }

    @Test
    fun lazyPrecondition_failureMessage_lazyNotCalled() {
        var called = false
        val p = Precondition.createLazy("lazy") { called = true; true }
        assertThat(called).isFalse()
        assertThat(p.failureMessage).isNull()
        assertThat(called).isTrue()
    }
}
