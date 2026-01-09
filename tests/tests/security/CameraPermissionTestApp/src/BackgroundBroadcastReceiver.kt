/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.security.cts.camera.open.lib.IntentKeys

class BackgroundBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BackgroundBroadcastReceiver"
        // Matches the action in CameraPermissionTest.kt and AndroidManifestOpen.xml
        const val START_IN_BACKGROUND = "android.security.cts.camera.open.START_IN_BACKGROUND"

    }

    private fun backgroundCameraOpen(context: Context) {
        val pendingResult = goAsync();
        Log.v(TAG, " Attempting to open camera from background!")
        CoroutineScope(Dispatchers.IO).launch {
            val textureView = TextureView(context)
            textureView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            val keys =  IntentKeys(context.packageName)
            val cameraOpener =
                    CameraOpener(context = context, keys, textureView, surfaceTexture = null)

            val result =
                    cameraOpener.openCamera2Async(/*shouldStream*/false, /*shouldRepeat*/false)
            Log.v(TAG, "got camera open resultextras : ${result.getExtras().toString()}")
            result.setAction(keys.backgroundCameraOpenFinished)
            context.sendBroadcast(result)
            pendingResult.finish()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive called with action: ${intent.action}")
        if (intent.action == START_IN_BACKGROUND) {
            backgroundCameraOpen(context)
        } else {
            Log.w(TAG, "Received unexpected action: ${intent.action}")
        }
    }
}