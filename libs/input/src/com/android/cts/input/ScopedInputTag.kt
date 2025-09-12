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

import com.android.compatibility.common.util.SystemUtil

/**
 * An [AutoCloseable] class that enables debug logging for a single input log tag for the duration
 * of its scope.
 *
 * Example usage:
 * ```
 * ScopedInputTag("InputReaderRawEvents").use {
 *     // Code that runs with InputReader raw event debug logs enabled.
 * }
 * ```
 *
 * Note: This will only work on debuggable builds (e.g. eng, userdebug).
 */
class ScopedInputTag(private val tag: String) : AutoCloseable {
    private val initialValue: String

    init {
        initialValue = SystemUtil.runShellCommandOrThrow("getprop log.tag.$tag")!!.trim()
        SystemUtil.runShellCommandOrThrow("setprop log.tag.$tag DEBUG")
    }

    override fun close() {
        val value = initialValue.ifBlank { "UNKNOWN" }
        SystemUtil.runShellCommandOrThrow("setprop log.tag.$tag $value")
    }
}
