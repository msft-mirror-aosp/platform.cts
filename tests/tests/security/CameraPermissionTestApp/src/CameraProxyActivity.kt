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

package android.security.cts.camera.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.security.cts.camera.open.lib.IOpenCameraActivity
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class CameraProxyActivity : AppCompatActivity() {
  private lateinit var keys: IntentKeys
  private lateinit var broadcastReceiver: BroadcastReceiver
  private lateinit var openCameraActivity: IOpenCameraActivity
  private val openCameraExecutor = Executors.newSingleThreadExecutor()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    keys = IntentKeys(packageName)

    tryOrFinish {
      openCameraActivity =
          IOpenCameraActivity.Stub.asInterface(
              intent.getBundleExtra(keys.aidlInterface)!!.getBinder(keys.aidlInterface)!!)
    }

    openCameraExecutor.execute {
      tryOrFinish {
        val shouldStream = intent.getBooleanExtra(keys.shouldStream, false)
        val shouldRepeat = intent.getBooleanExtra(keys.shouldRepeat, false)
        val result =
            if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
              openCameraActivity.openCamera1(attributionSource, shouldStream, shouldRepeat)
            } else if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
              openCameraActivity.openCamera2(attributionSource, shouldStream, shouldRepeat)
            } else {
              throw RuntimeException("Expected one of shouldOpenCamera1 or shouldOpenCamera2")
            }
        lifecycleScope.launch {
          setResult(RESULT_OK, result)
          finish()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    Log.v(TAG, "onResume")

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.v(TAG, "onReceive")
            when (intent.action) {
              keys.stopRepeating -> {
                Log.v(TAG, "onReceive: stopRepeating")
                tryOrFinish { openCameraActivity.stopRepeating() }
              }
            }
          }
        }

    val filter = IntentFilter().apply { addAction(keys.stopRepeating) }
    registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
  }

  override fun onPause() {
    super.onPause()
    Log.v(TAG, "onPause")
    unregisterReceiver(broadcastReceiver)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.v(TAG, "onDestroy")
  }

  private fun tryOrFinish(callback: () -> Unit) {
    try {
      callback()
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e::class.java.simpleName}/${e.message}")

      lifecycleScope.launch {
        setResult(
            RESULT_OK,
            Intent().apply { putExtra(keys.exception, e.message ?: e::class.java.simpleName) })
        finish()
      }
    }
  }

  private companion object {
    val TAG = CameraProxyActivity::class.java.simpleName
  }
}
