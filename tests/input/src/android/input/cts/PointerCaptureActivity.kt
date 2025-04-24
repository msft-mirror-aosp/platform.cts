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

package android.input.cts

import com.android.cts.input.CaptureEventActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/** A test activity for capturing events when the pointer is captured. */
class PointerCaptureActivity : CaptureEventActivity() {
    private var latch: CountDownLatch? = null

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        latch?.takeIf { hasCapture }?.countDown()
    }

    /** Requests pointer capture, then blocks until it is granted. */
    fun ensurePointerCaptured() {
        latch = CountDownLatch(1)
        runOnUiThread { window.decorView.requestPointerCapture() }
        try {
            check(latch!!.await(60, TimeUnit.SECONDS)) {
                "Did not receive callback after enabling pointer capture."
            }
        } catch (e: InterruptedException) {
            throw IllegalStateException("Interrupted while waiting for the pointer to be captured.")
        } finally {
            latch = null
        }
        assertTrue("The view did not capture the pointer.", window.decorView.hasPointerCapture())
    }
}
