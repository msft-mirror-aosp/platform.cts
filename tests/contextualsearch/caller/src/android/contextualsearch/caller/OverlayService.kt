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
package android.contextualsearch.caller

import android.app.Service
import android.app.contextualsearch.ContextualSearchConfig
import android.app.contextualsearch.ContextualSearchManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.android.compatibility.common.util.BroadcastMessenger

class OverlayService : Service() {

    private var mWindowManager: WindowManager? = null
    private var mOverlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val config =
            intent.getParcelableExtra(
                ContextualSearchExtras.EXTRA_CONTEXTUAL_SEARCH_CONFIG,
                ContextualSearchConfig::class.java,
            )

        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mOverlayView = View(this)
        val params =
            WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )

        try {
            mWindowManager?.addView(mOverlayView, params)

            // Small delay to ensure the window is added and process is considered foreground
            Thread.sleep(500)

            val manager = getSystemService(ContextualSearchManager::class.java)
            @SuppressWarnings("deprecated")
            val displayId = mWindowManager?.defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
            val configWithDisplay =
                if (config == null || config.displayId == Display.INVALID_DISPLAY) {
                    val builder =
                        if (config != null) ContextualSearchConfig.Builder(config)
                        else ContextualSearchConfig.Builder()
                    builder.setDisplayId(displayId).build()
                } else {
                    config
                }
            manager.startContextualSearch(configWithDisplay)

            BroadcastMessenger.send(
                this,
                ContextualSearchMessage.TAG,
                ContextualSearchMessage(ContextualSearchMessage.RESULT_OK),
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ", e)
            BroadcastMessenger.send(
                this,
                ContextualSearchMessage.TAG,
                ContextualSearchMessage(ContextualSearchMessage.RESULT_EXCEPTION),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ", e)
            throw RuntimeException("Caught unexpected exception", e)
        } finally {
            mOverlayView?.let { mWindowManager?.removeView(it) }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "OverlayService"
    }
}
