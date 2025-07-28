/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package android.cts.photopicker.lib

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * An [Activity] that can launch another activity for a result and returns that result as a
 * [CompletableFuture].
 */
class FutureResultActivity : Activity() {

    /**
     * A simple data class to encapsulate the result from [onActivityResult].
     *
     * @param resultCode The integer result code returned by the child activity.
     * @param data An [Intent] which can return result data to the caller.
     */
    data class Result(val resultCode: Int, val data: Intent?) {

        /**
         * Helper function to get the list of selected media uris from the result [Intent].
         *
         * @return The list of media uris from [Intent.getClipData], or an empty list if there is no
         *   data.
         */
        fun getSelectedMedia(): List<Uri> {
            return data?.getClipDataUris() ?: emptyList()
        }

        /**
         * Helper function to extract the list of Uris from a [ClipData] object found in an intent.
         */
        private fun Intent.getClipDataUris(): List<Uri> {
            // Use a LinkedHashSet to maintain any ordering that may be
            // present in the ClipData
            val resultSet = LinkedHashSet<Uri>()
            data?.let { data -> resultSet.add(data) }
            val clipData = clipData
            if (clipData == null && resultSet.isEmpty()) {
                return emptyList()
            } else if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        resultSet.add(uri)
                    }
                }
            }
            return ArrayList(resultSet)
        }
    }

    companion object {
        /** requestCode -> Future<Result> */
        private val requests = ConcurrentHashMap<Int, CompletableFuture<Result>>()
        private val nextRequestCode = AtomicInteger(0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val future = requests.remove(requestCode)
        future?.complete(Result(resultCode, data))
    }

    /**
     * Launches an activity for a result.
     *
     * @param intent The [Intent] to start.
     * @return A [CompletableFuture] which will be completed with a [Result] object when the
     *   launched activity finishes.
     */
    fun launchActivityForFutureResult(intent: Intent): CompletableFuture<Result> {
        val requestCode = nextRequestCode.getAndIncrement()
        val future = CompletableFuture<Result>()
        requests[requestCode] = future
        startActivityForResult(intent, requestCode)
        return future
    }
}
