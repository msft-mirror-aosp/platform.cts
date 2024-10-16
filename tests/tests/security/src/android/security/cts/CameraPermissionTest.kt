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

package android.security.cts

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT
import android.content.AttributionSourceState
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.ICameraClient
import android.hardware.ICameraService
import android.hardware.camera2.CameraManager
import android.hardware.camera2.ICameraDeviceCallbacks
import android.hardware.camera2.impl.CameraMetadataNative
import android.hardware.camera2.impl.CaptureResultExtras
import android.hardware.camera2.impl.PhysicalCaptureResultInfo
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.security.cts.camera.open.lib.IOpenCameraActivity
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.bedstead.nene.TestApis
import com.android.cts.install.lib.Install
import com.android.cts.install.lib.TestApp
import com.android.cts.install.lib.Uninstall
import com.android.internal.camera.flags.Flags
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests that cameraserver checks for permissions correctly. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CameraPermissionTest {
  @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

  private val openCameraAppKeys = IntentKeys(OPEN_CAMERA_APP.packageName)
  private val cameraProxyAppKeys = IntentKeys(CAMERA_PROXY_APP.packageName)

  class DummyCameraDeviceCallbacks : ICameraDeviceCallbacks.Stub() {
    override fun onDeviceError(errorCode: Int, resultExtras: CaptureResultExtras) {
      Log.i(TAG, "onDeviceError($errorCode)")
    }

    override fun onCaptureStarted(resultExtras: CaptureResultExtras, timestamp: Long) {
      Log.i(TAG, "onCaptureStated($timestamp)")
    }

    override fun onResultReceived(
        result: CameraMetadataNative,
        resultExtras: CaptureResultExtras,
        physicalResults: Array<PhysicalCaptureResultInfo>
    ) {
      Log.i(TAG, "onResultReceived()")
    }

    override fun onDeviceIdle() {
      Log.i(TAG, "onDeviceIdle()")
    }

    override fun onPrepared(streamId: Int) {
      Log.i(TAG, "onPrepared()")
    }

    override fun onRequestQueueEmpty() {
      Log.i(TAG, "onRequestQueueEmpty()")
    }

    override fun onRepeatingRequestError(lastFrameNumber: Long, repeatingRequestId: Int) {
      Log.i(TAG, "onRepeatingRequestError($lastFrameNumber, $repeatingRequestId)")
    }
  }

  abstract class DummyBase : Binder(), android.os.IInterface {
    override fun asBinder(): IBinder {
      return this
    }
  }

  class DummyCameraClient : DummyBase(), ICameraClient

  private lateinit var broadcastReceiver: BroadcastReceiver
  private lateinit var openCameraAppInterface: IOpenCameraActivity
  private val onResumeFuture = CompletableFuture<Boolean>()
  private var openCameraResultFuture: CompletableFuture<Instrumentation.ActivityResult>? = null

  private lateinit var cameraManager: CameraManager

  @Before
  fun setUp() {
    TestApis.packages()
        .find(OPEN_CAMERA_APP.packageName)
        .grantPermission(Manifest.permission.CAMERA)

    TestApis.packages()
        .find(CAMERA_PROXY_APP.packageName)
        .grantPermission(Manifest.permission.CAMERA)

    val context = instrumentation.context
    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    assumeTrue(cameraManager.getCameraIdList().size > 0)

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive")
            when (intent.action) {
              openCameraAppKeys.onResume -> {
                onResumeFuture.complete(true)
              }
            }
          }
        }

    val filter = IntentFilter(openCameraAppKeys.onResume)
    context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
  }

  @After
  fun tearDown() {
    finishActivity()

    if (this::broadcastReceiver.isInitialized) {
      instrumentation.context.unregisterReceiver(broadcastReceiver)
    }
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnectDevice_useContextAttributionSource_off() {
    testConnectDevice(expectDenial = true)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnectDevice_useContextAttributionSource_on() {
    testConnectDevice(expectDenial = false)
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnect_useContextAttributionSource_off() {
    testConnect(expectDenial = true)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnect_useContextAttributionSource_on() {
    testConnect(expectDenial = false)
  }

  @Test
  fun testAppConnectDevice() {
    openCameraResultFuture = startOpenCameraActivity(openCamera2 = true)
    checkAppOpenedCamera(openCameraAppKeys.cameraOpened2)
  }

  @Test
  fun testAppConnectDevice_noPermission() {
    denyAppPermission(OPEN_CAMERA_APP)
    openCameraResultFuture = startOpenCameraActivity(openCamera2 = true)
    checkAppFailedToOpenCamera(openCameraAppKeys.cameraOpened2)
  }

  @Test
  fun testAppConnect() {
    openCameraResultFuture = startOpenCameraActivity(openCamera1 = true)
    checkAppOpenedCamera(openCameraAppKeys.cameraOpened1)
  }

  @Test
  fun testAppConnect_noPermission() {
    denyAppPermission(OPEN_CAMERA_APP)
    openCameraResultFuture = startOpenCameraActivity(openCamera1 = true)
    checkAppFailedToOpenCamera(openCameraAppKeys.cameraOpened1)
  }

  @Test
  fun testProxyConnectDevice() {
    openCameraByProxy(openCameraAppKeys.openCamera2ByProxy)
    checkAppOpenedCameraByProxy(openCameraAppKeys.cameraOpened2)
  }

  @Test
  fun testProxyConnectDevice_noOpenCameraPermission() {
    denyAppPermission(OPEN_CAMERA_APP)
    openCameraByProxy(openCameraAppKeys.openCamera2ByProxy)
    checkAppFailedToOpenCameraByProxy(openCameraAppKeys.cameraOpened2)
  }

  @Test
  @RequiresFlagsEnabled(
      Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE, Flags.FLAG_CHECK_FULL_ATTRIBUTION_SOURCE_CHAIN)
  fun testProxyConnectDevice_noCameraProxyPermission() {
    denyAppPermission(CAMERA_PROXY_APP)
    openCameraByProxy(openCameraAppKeys.openCamera2ByProxy)
    checkAppFailedToOpenCameraByProxy(openCameraAppKeys.cameraOpened2)
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_CHECK_FULL_ATTRIBUTION_SOURCE_CHAIN)
  fun testProxyConnectDevice_noCameraProxyPermission_checkFullAttributionSourceChain_off() {
    denyAppPermission(CAMERA_PROXY_APP)
    openCameraByProxy(openCameraAppKeys.openCamera2ByProxy)
    checkAppOpenedCameraByProxy(openCameraAppKeys.cameraOpened2)
  }

  @Test
  fun testProxyConnect() {
    openCameraByProxy(openCameraAppKeys.openCamera1ByProxy)
    checkAppOpenedCameraByProxy(openCameraAppKeys.cameraOpened1)
  }

  @Test
  fun testSpoofedAttributionSourceConnectDevice() {
    val clientAttribution = startActivityForSpoofing()
    testConnectDeviceWithAttribution(clientAttribution, ICameraService.ERROR_PERMISSION_DENIED)
  }

  @Test
  fun testSpoofedAttributionSourceConnect() {
    val clientAttribution = startActivityForSpoofing()
    testConnectWithAttribution(clientAttribution, ICameraService.ERROR_PERMISSION_DENIED)
  }

  private fun openCameraByProxy(openCameraKey: String) {
    openCameraResultFuture = startOpenCameraActivity()
    assertTrue(onResumeFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
    instrumentation.context.sendBroadcast(
        Intent(openCameraKey).apply { setPackage(OPEN_CAMERA_APP.packageName) })
  }

  private fun requireActivityResultData() =
      openCameraResultFuture!!
          .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
          .apply { assertEquals(Activity.RESULT_OK, resultCode) }
          .resultData!!

  private fun checkAppOpenedCamera(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "checkAppOpenedCamera Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")
      assumeFalse(it.getBooleanExtra(openCameraAppKeys.noCamera, false))
      assertEquals(null, it.getStringExtra(openCameraAppKeys.exception))
      assertEquals(0, it.getIntExtra(openCameraAppKeys.error, 0))
      assertEquals(true, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun checkAppOpenedCameraByProxy(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "checkAppOpenedCameraByProxy Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")
      assumeFalse(it.getBooleanExtra(openCameraAppKeys.noCamera, false))
      assertEquals(null, it.getStringExtra(openCameraAppKeys.exception))
      assertEquals(null, it.getStringExtra(cameraProxyAppKeys.exception))
      assertEquals(0, it.getIntExtra(openCameraAppKeys.error, 0))
      assertEquals(true, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun checkAppFailedToOpenCamera(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      assumeFalse(it.getBooleanExtra(openCameraAppKeys.noCamera, false))
      assertNotNull(it.getStringExtra(openCameraAppKeys.exception))
      assertEquals(false, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun checkAppFailedToOpenCameraByProxy(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "testProxyConnectDevice_noOpenCameraPermission Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")
      assumeFalse(it.getBooleanExtra(openCameraAppKeys.noCamera, false))
      assertNotNull(it.getStringExtra(openCameraAppKeys.exception))
      assertEquals(null, it.getStringExtra(cameraProxyAppKeys.exception))
      assertEquals(false, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun testConnectDevice(expectDenial: Boolean) {
    val clientAttribution = instrumentation.context.getAttributionSource().asState()
    val expectedError =
        if (expectDenial) {
          ICameraService.ERROR_PERMISSION_DENIED
        } else {
          0
        }
    testConnectDeviceWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnect(expectDenial: Boolean) {
    val clientAttribution = instrumentation.context.getAttributionSource().asState()
    val expectedError =
        if (expectDenial) {
          ICameraService.ERROR_PERMISSION_DENIED
        } else {
          0
        }
    testConnectWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnectDeviceWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int,
  ) {
    var errorCode = 0
    try {
      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        connectDevice(clientAttribution)
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun connectDevice(clientAttribution: AttributionSourceState) {
    getCameraService()
        .connectDevice(
            DummyCameraDeviceCallbacks(),
            cameraManager.getCameraIdList()[0],
            0 /*oomScoreOffset*/,
            instrumentation.context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            clientAttribution,
            DEVICE_POLICY_DEFAULT)
        .disconnect()
  }

  private fun testConnectWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int,
  ) {
    var errorCode = 0
    try {
      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        connect(clientAttribution)
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun connect(clientAttribution: AttributionSourceState) {
    getCameraService()
        .connect(
            DummyCameraClient(),
            /* cameraId= */ 0,
            instrumentation.context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            /* forceSlowJpegMode= */ false,
            clientAttribution,
            DEVICE_POLICY_DEFAULT)
        .disconnect()
  }

  private fun startActivityForSpoofing(): AttributionSourceState {
    openCameraResultFuture = startOpenCameraActivity()

    assertTrue(onResumeFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

    val uid =
        instrumentation.context.packageManager
            .getApplicationInfo(OPEN_CAMERA_APP.packageName, 0)
            .uid

    val activityManager =
        instrumentation.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningAppList = activityManager.getRunningAppProcesses()

    var pid = -1
    for (process in runningAppList) {
      if (process.processName.contains(OPEN_CAMERA_APP.packageName)) {
        pid = process.pid
      }
    }

    val context = instrumentation.context
    val contextAttribution = context.getAttributionSource().asState()
    val clientAttribution = AttributionSourceState()
    clientAttribution.uid = uid
    clientAttribution.pid = pid
    clientAttribution.deviceId = contextAttribution.deviceId
    clientAttribution.packageName = OPEN_CAMERA_APP.packageName
    clientAttribution.next = arrayOf<AttributionSourceState>()

    Log.i(
        TAG,
        "Spoofing client uid = $uid, pid = $pid : myUid = ${Process.myUid()}, myPid = ${Process.myPid()}")

    return clientAttribution
  }

  private fun getCameraService(): ICameraService {
    val cameraServiceBinder = ServiceManager.getService("media.camera")
    assertNotNull("Camera service IBinder should not be null", cameraServiceBinder)

    val cameraService = ICameraService.Stub.asInterface(cameraServiceBinder)
    assertNotNull("Camera service should not be null", cameraService)

    return cameraService
  }

  private fun finishActivity() {
    openCameraResultFuture?.let {
      val finishIntent = Intent(openCameraAppKeys.finish)
      finishIntent.setPackage(OPEN_CAMERA_APP.packageName)
      instrumentation.context.sendBroadcast(finishIntent)

      val activityResult = it.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      assertEquals(Activity.RESULT_OK, activityResult.resultCode)
    }
  }

  private fun startOpenCameraActivity(
      openCamera1: Boolean = false,
      openCamera2: Boolean = false,
  ): CompletableFuture<Instrumentation.ActivityResult> =
      CompletableFuture<Instrumentation.ActivityResult>().also {
        ActivityScenario.launch(StartForFutureActivity::class.java).onActivity {
            startForFutureActivity ->
          startForFutureActivity.startActivityForFuture(
              Intent().apply {
                component = ComponentName(OPEN_CAMERA_APP.packageName, OPEN_CAMERA_ACTIVITY)
                putExtra(openCameraAppKeys.shouldOpenCamera1, openCamera1)
                putExtra(openCameraAppKeys.shouldOpenCamera2, openCamera2)
              },
              it)
        }
      }

  private fun maybePrintAttributionSource(intent: Intent) {
    intent.getStringExtra(openCameraAppKeys.attributionSource)?.let { Log.i(TAG, it) }
  }

  private fun denyAppPermission(app: TestApp) {
    TestApis.packages().find(app.packageName).denyPermission(Manifest.permission.CAMERA)
  }

  companion object {
    private val TAG = CameraPermissionTest::class.java.simpleName
    private val OPEN_CAMERA_APP =
        TestApp("OpenCameraApp", "android.security.cts.camera.open", 30, false, "OpenCameraApp.apk")
    private val CAMERA_PROXY_APP =
        TestApp(
            "CameraProxyApp", "android.security.cts.camera.proxy", 30, false, "CameraProxyApp.apk")
    private val OPEN_CAMERA_ACTIVITY = "${OPEN_CAMERA_APP.packageName}.OpenCameraActivity"
    private val CAMERA_PROXY_ACTIVITY = "${CAMERA_PROXY_APP.packageName}.CameraProxyActivity"
    const val TIMEOUT_MILLIS: Long = 10000

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(OPEN_CAMERA_APP.packageName, CAMERA_PROXY_APP.packageName)
      }

      TestApis.permissions().withPermission(Manifest.permission.INSTALL_PACKAGES).use {
        Install.multi(OPEN_CAMERA_APP, CAMERA_PROXY_APP).commit()
      }
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(OPEN_CAMERA_APP.packageName, CAMERA_PROXY_APP.packageName)
      }
    }
  }
}
