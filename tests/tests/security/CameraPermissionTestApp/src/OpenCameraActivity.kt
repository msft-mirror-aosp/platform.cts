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
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.security.cts.camera.open.lib.IOpenCameraActivity
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class OpenCameraActivity : AppCompatActivity() {
  private val cameraProxyAppKeys = IntentKeys(CAMERA_PROXY_APP_PACKAGE_NAME)
  private lateinit var keys: IntentKeys
  private lateinit var cameraManager: CameraManager
  private lateinit var broadcastReceiver: BroadcastReceiver
  private lateinit var textureView: TextureView
  private var handlerThread: HandlerThread? = null
  private val startForResult =
      registerForActivityResult(StartActivityForResult()) { activityResult: ActivityResult ->
        setResult(activityResult.resultCode, activityResult.data)
        finish()
      }
  private val cameraExecutor = Executors.newSingleThreadExecutor()

  private var onStopRepeating: () -> Unit = {}

  private val aidlInterface =
      object : IOpenCameraActivity.Stub() {
        override fun openCamera1(
            attributionSource: AttributionSource,
            shouldStream: Boolean,
            shouldRepeat: Boolean
        ): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera1")
          openCamera1Async(shouldStream, shouldRepeat, attributionSource)
        }

        override fun openCamera2(
            attributionSource: AttributionSource,
            shouldStream: Boolean,
            shouldRepeat: Boolean
        ): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera2")
          openCamera2Async(shouldStream, shouldRepeat, attributionSource)
        }

        override fun stopRepeating() = onStopRepeating()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    textureView = TextureView(this)
    textureView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    setContentView(textureView)

    keys = IntentKeys(packageName)

    val shouldStream = intent.getBooleanExtra(keys.shouldStream, false)
    val shouldRepeat = intent.getBooleanExtra(keys.shouldRepeat, false)
    if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
      openCamera1AndFinish(shouldStream, shouldRepeat)
    } else if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
      openCamera2AndFinish(shouldStream, shouldRepeat)
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
                Log.v(TAG, "onReceive: finish")
                setResult(RESULT_OK)
                finish()
              }

              keys.openCamera1ByProxy -> {
                Log.v(TAG, "onReceive: openCamera1ByProxy")
                openCamera1ByProxy(
                    intent.getBooleanExtra(keys.shouldStream, false),
                    intent.getBooleanExtra(keys.shouldRepeat, false))
              }

              keys.openCamera2ByProxy -> {
                Log.v(TAG, "onReceive: openCamera2ByProxy")
                openCamera2ByProxy(
                    intent.getBooleanExtra(keys.shouldStream, false),
                    intent.getBooleanExtra(keys.shouldRepeat, false))
              }

              keys.stopRepeating -> {
                Log.v(TAG, "onReceive: stopRepeating")
                onStopRepeating()
              }
            }
          }
        }

    val filter =
        IntentFilter().apply {
          addAction(keys.finish)
          addAction(keys.openCamera1ByProxy)
          addAction(keys.openCamera2ByProxy)
          addAction(keys.stopRepeating)
        }
    registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

    val onResumeIntent = Intent(keys.onResume).apply { putExtra(keys.pid, Process.myPid()) }
    onResumeIntent.setPackage("android.security.cts")
    sendBroadcast(onResumeIntent)
  }

  override fun onPause() {
    super.onPause()
    Log.v(TAG, "onPause")
    unregisterReceiver(broadcastReceiver)
    handlerThread?.quitSafely()
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.v(TAG, "onDestroy")
  }

  private suspend fun openCamera1Async(
      shouldStream: Boolean,
      shouldRepeat: Boolean,
      attributionSource: AttributionSource? = null,
  ): Intent {
    val result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
    try {
      if (Camera.getNumberOfCameras() > 0) {
        val camera = Camera.open(0)
        result.putExtra(keys.cameraOpened1, true)
        if (shouldStream) {
          return openCamera1Stream(camera, shouldRepeat, result)
        } else {
          camera.release()
          return result
        }
      } else {
        return result.apply { putExtra(keys.noCamera, true) }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e.message}")
      return putException(result, e)
    }
  }

  private suspend fun openCamera2Async(
      shouldStream: Boolean,
      shouldRepeat: Boolean,
      attributionSource: AttributionSource? = null,
  ): Intent = suspendCancellableCoroutine { continuation ->
    val result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
    tryOrResume(continuation, result, "openCamera2Async") {
      var context: Context = this
      attributionSource?.let {
        val contextParams = ContextParams.Builder().setNextAttributionSource(it).build()
        context = createContext(contextParams)
      }
      cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      if (cameraManager.getCameraIdList().isEmpty()) {
        continuation.resume(result.apply { putExtra(keys.noCamera, true) })
      }

      val cameraId = cameraManager.getCameraIdList()[0]
      cameraManager.openCamera(
          cameraId,
          cameraExecutor,
          object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
              Log.v(TAG, "onOpened")

              result.putExtra(keys.cameraOpened2, true)
              if (shouldStream) {
                tryOrResume(continuation, result, "openCamera2Async/onOpened") {
                  openCamera2Stream(cameraDevice, result, continuation, shouldRepeat)
                }
              } else {
                cameraDevice.close()
                continuation.resume(result)
              }
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
              Log.v(TAG, "onDisconnected")
              tryOrResume(continuation, result, "openCamera2Async/onDisconnected") {
                cameraDevice.close()
              }
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
              Log.v(TAG, "onError: " + error)

              try {
                cameraDevice.close()
              } catch (e: Exception) {
                Log.e(TAG, "openCamera2Async/onDisconnected: Received exception: ${e.exceptionString}")
                putException(result, e)
              }

              if (continuation.isActive) { // continuation may already have been resumed
                continuation.resume(result.apply { putExtra(keys.error, error) })
              }
            }
          })
    }
  }

  private fun openCamera1AndFinish(shouldStream: Boolean, shouldRepeat: Boolean) {
    lifecycleScope.launch {
      setResult(RESULT_OK, openCamera1Async(shouldStream, shouldRepeat))
      finish()
    }
  }

  private fun openCamera2AndFinish(shouldStream: Boolean, shouldRepeat: Boolean) {
    lifecycleScope.launch {
      val result = openCamera2Async(shouldStream, shouldRepeat)
      Log.v(TAG, "finishing activity: ${result.getExtras().toString()}")
      setResult(RESULT_OK, result)
      finish()
    }
  }

  private fun openCameraByProxy(
      openCameraKey: String,
      shouldStream: Boolean,
      shouldRepeat: Boolean
  ) {
    startForResult.launch(
        Intent().apply {
          component = ComponentName(CAMERA_PROXY_APP_PACKAGE_NAME, CAMERA_PROXY_ACTIVITY)
          putExtra(openCameraKey, true)
          putExtra(cameraProxyAppKeys.shouldStream, shouldStream)
          putExtra(cameraProxyAppKeys.shouldRepeat, shouldRepeat)
          putExtra(
              cameraProxyAppKeys.aidlInterface,
              Bundle().apply { putBinder(cameraProxyAppKeys.aidlInterface, aidlInterface) })
        })
  }

  private fun openCamera1ByProxy(shouldStream: Boolean, shouldRepeat: Boolean) =
      openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera1, shouldStream, shouldRepeat)

  private fun openCamera2ByProxy(shouldStream: Boolean, shouldRepeat: Boolean) =
      openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera2, shouldStream, shouldRepeat)

  private suspend fun openCamera1Stream(
      camera: Camera,
      shouldRepeat: Boolean,
      result: Intent
  ): Intent = suspendCancellableCoroutine { continuation ->
    val params = camera.getParameters()
    val previewSize = params.getSupportedPreviewSizes()[0]
    params.setPreviewSize(previewSize.width, previewSize.height)
    camera.setParameters(params)

    if (textureView.surfaceTexture != null) {
      captureCamera1(camera, result, continuation, shouldRepeat)
    } else {
      textureView.setSurfaceTextureListener(
          object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
              surfaceTexture.let { captureCamera1(camera, result, continuation, shouldRepeat) }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
              Log.v(TAG, "openCamera1Stream/onSurfaceTextureDestroyed")
              return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
          })
    }
  }

  private fun captureCamera1(
      camera: Camera,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      shouldRepeat: Boolean,
  ) {
    camera.setPreviewTexture(textureView.surfaceTexture)

    val cleanup: () -> Unit = {
      Log.v(TAG, "captureCamera1/cleanup")
      tryOrResume(continuation, result, "captureCamera1/cleanup") {
        camera.stopPreview()
        camera.setPreviewTexture(null)
        camera.release()
      }
    }

    camera.setErrorCallback(
        object : Camera.ErrorCallback {
          override fun onError(error: Int, camera: Camera) {
            Log.v(TAG, "captureCamera1/onError: " + error)

            try {
              camera.release()
            } catch (e: Exception) {
              Log.e(TAG, "captureCamera1/onError: Received exception: ${e.exceptionString}")
              putException(result, e)
            }

            if (continuation.isActive) { // continuation may already have been resumed
              continuation.resume(result.apply { putExtra(keys.error, error) })
            }
          }
        })

    camera.setPreviewCallback(
        object : Camera.PreviewCallback {
          private var firstCaptureCompleted = false

          override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            if (!firstCaptureCompleted) {
              Log.v(TAG, "Camera.PreviewCallback.onPreviewFrame() (first)")
              onStopRepeating = {
                Log.v(TAG, "onStopRepeating")
                cleanup()

                if (continuation.isActive) {
                  continuation.resume(result.apply { putExtra(keys.stoppedRepeating, true) })
                }
              }

              signalStreamOpened(true, result)
              firstCaptureCompleted = true

              if (!shouldRepeat) {
                cleanup()
                continuation.resume(result)
              }
            } else {
              Log.v(TAG, "Camera.PreviewCallback.onPreviewFrame()")
            }
          }
        })

    camera.startPreview()
  }

  private fun openCamera2Stream(
      cameraDevice: CameraDevice,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      shouldRepeat: Boolean
  ) {
    handlerThread = HandlerThread("$packageName.CAPTURE").apply { start() }
    val captureHandler = Handler(handlerThread!!.getLooper())

    val characteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
    val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    val jpegSize = config.getOutputSizes(ImageFormat.JPEG)[0]
    val imageReader =
        ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1).apply {
          setOnImageAvailableListener(
              object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(imageReader: ImageReader) {
                  imageReader.acquireNextImage().close()
                }
              },
              captureHandler)
        }
    val outputConfiguration = OutputConfiguration(imageReader.surface)
    val sessionConfiguration =
        SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(outputConfiguration),
            cameraExecutor,
            object : CameraCaptureSession.StateCallback() {
              override fun onConfigured(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onConfigured()")
                tryOrResume(continuation, result, "openCamera2Stream/onConfigured") {
                  captureCamera2(
                      cameraDevice,
                      session,
                      result,
                      continuation,
                      imageReader.surface,
                      shouldRepeat,
                      captureHandler)
                }
              }

              override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onConfigureFailed()")
                session.close()
                cameraDevice.close()
                continuation.resume(signalStreamOpened(false, result))
              }

              override fun onReady(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onReady()")
              }
            })
    cameraDevice.createCaptureSession(sessionConfiguration)
  }

  private fun captureCamera2(
      cameraDevice: CameraDevice,
      session: CameraCaptureSession,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      target: Surface,
      shouldRepeat: Boolean,
      captureHandler: Handler
  ) {
    val cleanup: () -> Unit = {
      Log.v(TAG, "captureCamera2/cleanup")
      tryOrResume(continuation, result, "captureCamera2/cleanup") {
        session.stopRepeating()
        session.close()
        cameraDevice.close()
      }
    }

    val captureRequest =
        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).let {
          it.addTarget(target)
          it.build()
        }

    val captureCallback =
        object : CameraCaptureSession.CaptureCallback() {
          private var firstCaptureCompleted = false

          override fun onCaptureCompleted(
              session: CameraCaptureSession,
              request: CaptureRequest,
              captureResult: TotalCaptureResult,
          ) {
            if (!firstCaptureCompleted) {
              Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureCompleted() (first)")
              onStopRepeating = {
                Log.v(TAG, "onStopRepeating")
                cleanup()

                if (continuation.isActive) {
                  continuation.resume(result.apply { putExtra(keys.stoppedRepeating, true) })
                }
              }

              signalStreamOpened(true, result)
              firstCaptureCompleted = true
            } else {
              Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureCompleted()")
            }

            if (!shouldRepeat) {
              cleanup()
              continuation.resume(result)
            }
          }

          override fun onCaptureFailed(
              session: CameraCaptureSession,
              request: CaptureRequest,
              failure: CaptureFailure
          ) {
            Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureFailed()")
          }
        }

    if (shouldRepeat) {
      Log.v(TAG, "captureCamera2: setRepeatingRequest")
      session.setRepeatingRequest(captureRequest, captureCallback, captureHandler)
    } else {
      Log.v(TAG, "captureCamera2: capture")
      session.capture(captureRequest, captureCallback, captureHandler)
    }
  }

  private fun signalStreamOpened(streamOpened: Boolean, result: Intent): Intent {
    val streamOpenedIntent =
        Intent(keys.streamOpened).apply { putExtra(keys.streamOpened, streamOpened) }
    streamOpenedIntent.setPackage("android.security.cts")
    sendBroadcast(streamOpenedIntent)
    return result.apply { putExtra(keys.streamOpened, streamOpened) }
  }

  private fun tryOrResume(
      continuation: CancellableContinuation<Intent>,
      result: Intent,
      method: String,
      callback: () -> Unit
  ) {
    try {
      callback()
    } catch (e: Exception) {
      Log.e(TAG, "${method}: Received exception: ${e.exceptionString}")
      if (continuation.isActive) {
        continuation.resume(putException(result, e))
      }
    }
  }

  private fun putException(intent: Intent, e: Exception) =
      intent.apply {
        if (!hasExtra(keys.exception)) {
          putExtra(keys.exception, e.exceptionString)
        }
      }

  private companion object {
    val TAG = OpenCameraActivity::class.java.simpleName

    val Exception.exceptionString: String
        get() = "${this::class.java.simpleName}/${message}"

    const val CAMERA_PROXY_APP_PACKAGE_NAME = "android.security.cts.camera.proxy"
    const val CAMERA_PROXY_ACTIVITY = "$CAMERA_PROXY_APP_PACKAGE_NAME.CameraProxyActivity"
  }
}
