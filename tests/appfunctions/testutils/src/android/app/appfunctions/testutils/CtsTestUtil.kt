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

package android.app.appfunctions.testutils

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.utils.ShellCommand
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import org.junit.Assert.fail

/** Contains testing utilities related to AppFunction's Sidecar library. */
object CtsTestUtil {
    /** Runs a block with shell permissions. */
    suspend fun runWithShellPermission(vararg permissions: String, block: suspend () -> Unit) {
        permissions().withPermission(*permissions).use { block() }
    }

    fun interface ThrowRunnable {
        @Throws(Throwable::class) suspend fun run()
    }

    /**
     * Retries an assertion with a delay between attempts. If the assertion fails, the test will
     * continue.
     */
    suspend fun safeRetryAssert(
        checkInterval: Long = RETRY_CHECK_INTERVAL_MILLIS,
        maxIntervals: Long = RETRY_MAX_INTERVALS,
        runnable: ThrowRunnable,
    ) {
        var lastError: Throwable? = null
        for (attempt in 0 until maxIntervals) {
            try {
                runnable.run()
                return
            } catch (e: Throwable) {
                lastError = e
                delay(checkInterval)
            }
        }
    }

    /** Retries an assertion with a delay between attempts. */
    @Throws(Throwable::class)
    suspend fun retryAssert(
        checkInterval: Long = RETRY_CHECK_INTERVAL_MILLIS,
        maxIntervals: Long = RETRY_MAX_INTERVALS,
        runnable: ThrowRunnable,
    ) {
        var lastError: Throwable? = null

        for (attempt in 0 until maxIntervals) {
            try {
                runnable.run()
                return
            } catch (e: Throwable) {
                lastError = e
                delay(checkInterval)
            }
        }
        throw lastError!!
    }

    fun assertReadAccessible(contentResolver: ContentResolver, uri: Uri) {
        try {
            contentResolver.openAssetFile(uri, "r", null).use { fd ->
                if (fd != null) {
                    return
                }
            }
        } catch (e: Exception) {
            Log.d("DEBUG", e.message!!)
        }
        fail("Uri $uri is not read accessible")
    }

    fun assertReadInaccessible(contentResolver: ContentResolver, uri: Uri) {
        try {
            contentResolver.openAssetFile(uri, "r", null).use { fd -> }
        } catch (e: SecurityException) {
            return
        }
        fail("Uri $uri is still read accessible")
    }

    fun assertWriteAccessible(contentResolver: ContentResolver, uri: Uri) {
        try {
            val result =
                contentResolver.update(
                    uri,
                    ContentValues().apply { put("echo_value", 100) },
                    Bundle.EMPTY,
                )
            if (result == 100) {
                return
            }
        } catch (e: Exception) {}
        fail("Uri $uri is not write accessible")
    }

    fun assertWriteInaccessible(contentResolver: ContentResolver, uri: Uri) {
        try {
            contentResolver.update(
                uri,
                ContentValues().apply { put("echo_value", 100) },
                Bundle.EMPTY,
            )
        } catch (e: Exception) {
            return
        }
        fail("Uri $uri is still write accessible")
    }

    /** Freezes a process and waits for it to be frozen. */
    suspend fun freezeProcess(context: Context, packageName: String, processName: String? = null) {
        runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
            val targetProcess =
                if (processName == null) {
                    packageName
                } else {
                    "$packageName:$processName"
                }
            ShellCommand.builder("am freeze $targetProcess").execute()

            retryAssert {
                val isFrozenOutput = ShellCommand.builder("am isfrozen $targetProcess").execute()
                assertThat(isFrozenOutput).isEqualTo("true\n")
            }
        }
    }

    /** Unfreezes a process and waits for it to be unfrozen. */
    suspend fun unfreezeProcess(
        context: Context,
        packageName: String,
        processName: String? = null,
    ) {
        runWithShellPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL) {
            val targetProcess =
                if (processName == null) {
                    packageName
                } else {
                    "$packageName:$processName"
                }
            ShellCommand.builder("am unfreeze $targetProcess").execute()

            retryAssert {
                val isFrozenOutput = ShellCommand.builder("am isfrozen $targetProcess").execute()
                assertThat(isFrozenOutput).isEqualTo("false\n")
            }
        }
    }

    /** Same as [unfreezeProcess], but does not throw an exception if the process is not frozen. */
    suspend fun safeUnfreezeProcess(
        context: Context,
        packageName: String,
        processName: String? = null,
    ) {
        try {
            unfreezeProcess(context, packageName, processName)
        } catch (_: Exception) {}
    }

    private const val RETRY_CHECK_INTERVAL_MILLIS: Long = 1000
    private const val RETRY_MAX_INTERVALS: Long = 10
}
