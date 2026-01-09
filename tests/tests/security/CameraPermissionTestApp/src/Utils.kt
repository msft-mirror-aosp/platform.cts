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

package android.security.cts.camera.open

import android.content.Intent
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation

import java.util.concurrent.atomic.AtomicBoolean

private val TAG = OpenCameraActivity::class.java.simpleName

fun CancellableContinuation<Intent>.resumeIfActive(value: Intent,
        hasResumed: AtomicBoolean,
        tag: String = "") {
    if (hasResumed.compareAndSet(false, true)) {
        if (this.isActive) {
            Log.v(TAG, "Resuming $tag")
            this.resume(value)
        } else {
            Log.w(TAG, "Continuation not active, not resuming $tag")
        }
    } else {
        Log.d(TAG, "Already resumed, ignoring $tag")
    }
}

fun CancellableContinuation<Intent>.resumeIfActiveWithException(
    hasResumed: AtomicBoolean,
    e: Exception,
    keys: IntentKeys,
    result: Intent,
    tag: String = ""
) {
    if (hasResumed.compareAndSet(false, true)) {
        if (this.isActive) {
            Log.e(TAG, "Resuming with exception $tag: ${e.exceptionString}")
            result.putException(keys, e)
            this.resume(result)
        } else {
            Log.w(TAG, "Continuation not active, " +
                    " not resuming with exception $tag: ${e.exceptionString}")
        }
    } else {
        Log.d(TAG, "Already resumed, ignoring exception $tag: ${e.exceptionString}")
    }
}

fun CancellableContinuation<Intent>.tryOrResume(
    keys: IntentKeys,
    result: Intent,
    tag: String,
    hasResumed: AtomicBoolean,
    callback: () -> Unit
) {
    try {
        callback()
    } catch (e: Exception) {
        this.resumeIfActiveWithException(hasResumed, e, keys, result, tag)
    }
}

fun Intent.putException(keys: IntentKeys, e: Exception) {
  if (!hasExtra(keys.exception)) {
    putExtra(keys.exception, e.exceptionString)
  }
}

val Exception.exceptionString: String
  get() = "${this::class.java.simpleName}/${message}"
