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

import android.app.UiAutomation
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.bedstead.nene.TestApis.permissions
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.delay
import org.junit.Assert.fail

/** Contains testing utilities related to AppFunction's Sidecar library. */
object CtsTestUtil {
    /** Runs a block with shell permissions. */
    suspend fun runWithShellPermission(vararg permissions: String, block: suspend () -> Unit) {
        permissions().withPermission(*permissions).use { block() }
    }

    /** Runs a block with permissions removed. */
    suspend fun runWithoutPermission(vararg permissions: String, block: suspend () -> Unit) {
        permissions().withoutPermission(*permissions).use { block() }
    }

    fun interface ThrowRunnable {
        @Throws(Throwable::class) suspend fun run()
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

    /**
     * Gets a list of force queryable packages.
     *
     * When calling `adb shell dumpsys package queries`, a section started with "forceQueryable:"
     * will contain a list of system apps which are visible by all apps by default.
     */
    fun getForceQueryablePackages(uiAutomation: UiAutomation): List<String> {
        val pfd = uiAutomation.executeShellCommand("dumpsys package queries")
        return buildList {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    var insideForceQueryableBlock = false
                    var headerIndentationLevel = -1

                    while (line != null) {
                        val rawLine = line
                        val trimmedLine = rawLine.trim()

                        if (!insideForceQueryableBlock) {
                            if (trimmedLine == "forceQueryable:") {
                                insideForceQueryableBlock = true
                                headerIndentationLevel = rawLine.indexOf("forceQueryable:")
                            }
                        } else {
                            if (trimmedLine.isNotEmpty()) {
                                val currentIndentation = rawLine.indexOf(trimmedLine)
                                // End of force queryable section
                                if (currentIndentation <= headerIndentationLevel) {
                                    break
                                }

                                val cleanLine = trimmedLine.replace("[", "").replace("]", "")
                                val packageList = cleanLine.split(",")

                                for (pkg in packageList) {
                                    if (pkg.isNotBlank()) {
                                        add(pkg.trim())
                                    }
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        }
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

    private const val RETRY_CHECK_INTERVAL_MILLIS: Long = 1000
    private const val RETRY_MAX_INTERVALS: Long = 10
}
