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

package android.security.cts.camera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Camera
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log

class OpenCameraActivity : Activity() {
  private lateinit var keys: IntentKeys
  private lateinit var cameraManager: CameraManager
  private lateinit var broadcastReceiver: BroadcastReceiver

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "onCreate")

    keys = IntentKeys(packageName)

    try {
      if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
        openCamera2()
      } else if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
        openCamera1()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e.message}")
      setResult(RESULT_OK, Intent().apply { putExtra(keys.exception, e.message) })
      finish()
    }
  }

  override fun onResume() {
    super.onResume()

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive")
            when (intent.action) {
              keys.finish -> {
                setResult(RESULT_OK)
                finish()
              }
            }
          }
        }

    val filter = IntentFilter(keys.finish)
    registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

    val onResumeIntent = Intent(keys.onResume)
    onResumeIntent.setPackage("android.security.cts")
    sendBroadcast(onResumeIntent)
  }

  override fun onPause() {
    super.onPause()
    unregisterReceiver(broadcastReceiver)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "onDestroy")
  }

  private fun openCamera1() {
    if (Camera.getNumberOfCameras() > 0) {
      Camera.open(0).release()
      setResult(RESULT_OK, Intent().apply { putExtra(keys.cameraOpened1, true) })
      finish()
    } else {
      setResult(RESULT_OK, Intent().apply { putExtra(keys.noCamera, true) })
      finish()
    }
  }

  private fun openCamera2() {
    cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    if (cameraManager.getCameraIdList().size > 0) {
      val cameraId = cameraManager.getCameraIdList()[0]
      cameraManager.openCamera(
          cameraId,
          object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
              Log.i(TAG, "onOpened")
              cameraDevice.close()
              setResult(RESULT_OK, Intent().apply { putExtra(keys.cameraOpened2, true) })
              finish()
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
              Log.i(TAG, "onDisconnected")
              cameraDevice.close()
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
              Log.i(TAG, "onError: " + error)
              cameraDevice.close()
              setResult(RESULT_OK, Intent().apply { putExtra(keys.error, error) })
              finish()
            }
          },
          /* handler= */ null)
    } else {
      setResult(RESULT_OK, Intent().apply { putExtra(keys.noCamera, true) })
      finish()
    }
  }

  private companion object {
    val TAG = OpenCameraActivity::class.java.simpleName
  }
}
