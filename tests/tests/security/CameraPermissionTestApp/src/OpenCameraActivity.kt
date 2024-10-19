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

import android.content.AttributionSource
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Camera
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.security.cts.camera.open.lib.IOpenCameraActivity
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class OpenCameraActivity : AppCompatActivity() {
  private val cameraProxyAppKeys = IntentKeys(CAMERA_PROXY_APP_PACKAGE_NAME)
  private lateinit var keys: IntentKeys
  private lateinit var cameraManager: CameraManager
  private lateinit var broadcastReceiver: BroadcastReceiver
  private val startForResult =
      registerForActivityResult(StartActivityForResult()) { activityResult: ActivityResult ->
        setResult(activityResult.resultCode, activityResult.data)
        finish()
      }
  private val cameraExecutor = Executors.newSingleThreadExecutor()

  private val aidlInterface =
      object : IOpenCameraActivity.Stub() {
        override fun openCamera1(attributionSource: AttributionSource): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera1")
          openCamera1Async(attributionSource)
        }

        override fun openCamera2(attributionSource: AttributionSource): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera2")
          openCamera2Async(attributionSource)
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    keys = IntentKeys(packageName)

    if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
      openCamera1AndFinish()
    } else if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
      openCamera2AndFinish()
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
              keys.finish -> {
                setResult(RESULT_OK)
                finish()
              }

              keys.openCamera1ByProxy -> {
                Log.v(TAG, "openCamera1ByProxy")
                openCamera1ByProxy()
              }

              keys.openCamera2ByProxy -> {
                Log.v(TAG, "openCamera2ByProxy")
                openCamera2ByProxy()
              }
            }
          }
        }

    val filter =
        IntentFilter().apply {
          addAction(keys.finish)
          addAction(keys.openCamera1ByProxy)
          addAction(keys.openCamera2ByProxy)
        }
    registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

    val onResumeIntent = Intent(keys.onResume)
    onResumeIntent.setPackage("android.security.cts")
    sendBroadcast(onResumeIntent)
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

  private suspend fun openCamera1Async(attributionSource: AttributionSource? = null): Intent {
    val result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
    try {
      if (Camera.getNumberOfCameras() > 0) {
        Camera.open(0).release()
        return result.apply { putExtra(keys.cameraOpened1, true) }
      } else {
        return result.apply { putExtra(keys.noCamera, true) }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e.message}")
      return result.apply { putExtra(keys.exception, e.message) }
    }
  }

  private suspend fun openCamera2Async(attributionSource: AttributionSource? = null): Intent =
      suspendCancellableCoroutine { continuation ->
        val result =
            Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
        try {
          var context: Context = this
          attributionSource?.let {
            val contextParams = ContextParams.Builder().setNextAttributionSource(it).build()
            context = createContext(contextParams)
          }
          cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
          if (cameraManager.getCameraIdList().size > 0) {
            val cameraId = cameraManager.getCameraIdList()[0]
            cameraManager.openCamera(
                cameraId,
                cameraExecutor,
                object : CameraDevice.StateCallback() {
                  override fun onOpened(cameraDevice: CameraDevice) {
                    Log.v(TAG, "onOpened")
                    cameraDevice.close()
                    continuation.resume(result.apply { putExtra(keys.cameraOpened2, true) })
                  }

                  override fun onDisconnected(cameraDevice: CameraDevice) {
                    Log.v(TAG, "onDisconnected")
                    cameraDevice.close()
                  }

                  override fun onError(cameraDevice: CameraDevice, error: Int) {
                    Log.v(TAG, "onError: " + error)
                    cameraDevice.close()

                    if (continuation.isActive) { // continuation may already have been resumed
                      continuation.resume(result.apply { putExtra(keys.error, error) })
                    }
                  }
                })
          } else {
            continuation.resume(result.apply { putExtra(keys.noCamera, true) })
          }
        } catch (e: Exception) {
          Log.e(TAG, "Received exception: ${e::class.java.simpleName}/${e.message}")
          continuation.resume(result.apply { putExtra(keys.exception, e.message) })
        }
      }

  private fun openCamera1AndFinish() {
    lifecycleScope.launch {
      setResult(RESULT_OK, openCamera1Async())
      finish()
    }
  }

  private fun openCamera2AndFinish() {
    lifecycleScope.launch {
      setResult(RESULT_OK, openCamera2Async())
      finish()
    }
  }

  private fun openCameraByProxy(openCameraKey: String) {
    startForResult.launch(
        Intent().apply {
          component = ComponentName(CAMERA_PROXY_APP_PACKAGE_NAME, CAMERA_PROXY_ACTIVITY)
          putExtra(openCameraKey, true)
          putExtra(
              cameraProxyAppKeys.aidlInterface,
              Bundle().apply { putBinder(cameraProxyAppKeys.aidlInterface, aidlInterface) })
        })
  }

  private fun openCamera1ByProxy() = openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera1)

  private fun openCamera2ByProxy() = openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera2)

  private companion object {
    val TAG = OpenCameraActivity::class.java.simpleName

    const val CAMERA_PROXY_APP_PACKAGE_NAME = "android.security.cts.camera.proxy"
    const val CAMERA_PROXY_ACTIVITY = "$CAMERA_PROXY_APP_PACKAGE_NAME.CameraProxyActivity"
  }
}
