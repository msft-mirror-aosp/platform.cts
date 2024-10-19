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

import android.content.Intent
import android.os.Bundle
import android.security.cts.camera.open.lib.IOpenCameraActivity
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class CameraProxyActivity : AppCompatActivity() {
  private lateinit var keys: IntentKeys

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    keys = IntentKeys(packageName)

    try {
      val aidlInterface =
          IOpenCameraActivity.Stub.asInterface(
              intent.getBundleExtra(keys.aidlInterface)!!.getBinder(keys.aidlInterface)!!)

      val result =
          if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
            aidlInterface.openCamera1(attributionSource)
          } else if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
            aidlInterface.openCamera2(attributionSource)
          } else {
            throw RuntimeException("Expected one of shouldOpenCamera1 or shouldOpenCamera2")
          }
      setResult(RESULT_OK, result)
      finish()
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e::class.java.simpleName}/${e.message}")
      setResult(
          RESULT_OK,
          Intent().apply { putExtra(keys.exception, e.message ?: e::class.java.simpleName) })
      finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.v(TAG, "onDestroy")
  }

  private companion object {
    val TAG = CameraProxyActivity::class.java.simpleName
  }
}
