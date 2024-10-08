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
import android.platform.test.flag.junit.SetFlagsRule
import android.security.cts.camera.IntentKeys
import android.util.Log
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
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
  @get:Rule val activityRule = ActivityTestRule(StartForFutureActivity::class.java, false, false)

  @get:Rule val cameraPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

  @get:Rule val setFlagsRule = SetFlagsRule()

  private val keys = IntentKeys(APP_PACKAGE_NAME)

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
  private val onResumeFuture = CompletableFuture<Boolean>()
  private var activityResultFuture: CompletableFuture<Instrumentation.ActivityResult>? = null

  private lateinit var cameraManager: CameraManager

  @Before
  fun setUp() {
    val context = instrumentation.context
    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    assumeTrue(cameraManager.getCameraIdList().size > 0)

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive")
            when (intent.action) {
              keys.onResume -> {
                onResumeFuture.complete(true)
              }
            }
          }
        }

    val filter = IntentFilter(keys.onResume)
    context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
  }

  @After
  fun tearDown() {
    finishActivity()

    instrumentation.context.unregisterReceiver(broadcastReceiver)
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnectDevice_useContextAttributionSource_off() {
    testConnectDevice()
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnectDevice_useContextAttributionSource_on() {
    testConnectDevice()
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnect_useContextAttributionSource_off() {
    testConnect()
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_USE_CONTEXT_ATTRIBUTION_SOURCE)
  fun testConnect_useContextAttributionSource_on() {
    testConnect()
  }

  @Test
  fun testAppConnectDevice() {
    activityResultFuture = startActivityForFuture(openCamera2 = true)
    checkAppOpenedCamera(keys.cameraOpened2)
  }

  @Test
  fun testAppConnect() {
    activityResultFuture = startActivityForFuture(openCamera1 = true)
    checkAppOpenedCamera(keys.cameraOpened1)
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

  private fun checkAppOpenedCamera(openCameraKey: String) {
    val result = activityResultFuture!!.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    assertEquals(Activity.RESULT_OK, result.resultCode)

    result.resultData?.let {
      assumeFalse(it.getBooleanExtra(keys.noCamera, false))
      assertEquals(null, it.getStringExtra(keys.exception))
      assertEquals(0, it.getIntExtra(keys.error, 0))
      assertEquals(true, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun testConnectDevice() {
    val clientAttribution = instrumentation.context.getAttributionSource().asState()
    val expectedError =
        if (Flags.useContextAttributionSource()) {
          0
        } else {
          ICameraService.ERROR_PERMISSION_DENIED
        }
    testConnectDeviceWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnect() {
    val clientAttribution = instrumentation.context.getAttributionSource().asState()
    val expectedError =
        if (Flags.useContextAttributionSource()) {
          0
        } else {
          ICameraService.ERROR_PERMISSION_DENIED
        }
    testConnectWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnectDeviceWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int
  ) {
    val cameraService = getCameraService()
    val dummyCallbacks = DummyCameraDeviceCallbacks()

    var errorCode = 0
    try {
      val cameraId = cameraManager.getCameraIdList()[0]

      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        cameraService.connectDevice(
            dummyCallbacks,
            cameraId,
            0 /*oomScoreOffset*/,
            instrumentation.context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            clientAttribution,
            DEVICE_POLICY_DEFAULT
        )
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun testConnectWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int
  ) {
    val cameraService = getCameraService()
    val dummyCallbacks = DummyCameraClient()

    var errorCode = 0
    try {
      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        cameraService.connect(
            dummyCallbacks,
            /* cameraId= */
            0,
            instrumentation.context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            /* forceSlowJpegMode= */
            false,
            clientAttribution,
            DEVICE_POLICY_DEFAULT
        )
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun startActivityForSpoofing(): AttributionSourceState {
    activityResultFuture = startActivityForFuture()

    assertTrue(onResumeFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

    val uid = instrumentation.context.packageManager.getApplicationInfo(APP_PACKAGE_NAME, 0).uid

    val activityManager =
        instrumentation.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningAppList = activityManager.getRunningAppProcesses()

    var pid = -1
    for (process in runningAppList) {
      if (process.processName.contains(APP_PACKAGE_NAME)) {
        pid = process.pid
      }
    }

    val context = instrumentation.context
    val contextAttribution = context.getAttributionSource().asState()
    val clientAttribution = AttributionSourceState()
    clientAttribution.uid = uid
    clientAttribution.pid = pid
    clientAttribution.deviceId = contextAttribution.deviceId
    clientAttribution.packageName = APP_PACKAGE_NAME
    clientAttribution.next = arrayOf<AttributionSourceState>()

    Log.i(
        TAG,
        "Spoofing client uid = $uid, pid = $pid : myUid = ${Process.myUid()}, myPid = ${Process.myPid()}"
    )

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
    activityResultFuture?.let {
      val finishIntent = Intent(keys.finish)
      finishIntent.setPackage(APP_PACKAGE_NAME)
      instrumentation.context.sendBroadcast(finishIntent)

      val activityResult = it.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      assertEquals(Activity.RESULT_OK, activityResult.resultCode)
    }
  }

  private fun startActivityForFuture(
      openCamera1: Boolean = false,
      openCamera2: Boolean = false,
  ): CompletableFuture<Instrumentation.ActivityResult> =
      CompletableFuture<Instrumentation.ActivityResult>().also {
        activityRule
            .launchActivity(null)
            .startActivityForFuture(
                Intent().apply {
                  component =
                      ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.OpenCameraActivity")
                  putExtra(keys.shouldOpenCamera1, openCamera1)
                  putExtra(keys.shouldOpenCamera2, openCamera2)
                },
                it
            )
      }

  companion object {
    private val TAG = CameraPermissionTest::class.java.simpleName
    private const val APP_PACKAGE_NAME = "android.security.cts.camera"
    private val TEST_APP =
        TestApp(
            "CameraPermissionTestApp",
            APP_PACKAGE_NAME,
            30,
            false,
            "CameraPermissionTestApp.apk"
        )
    const val TIMEOUT_MILLIS: Long = 10000

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(APP_PACKAGE_NAME)
      }

      TestApis.permissions().withPermission(Manifest.permission.INSTALL_PACKAGES).use {
        Install.single(TEST_APP).commit()
      }

      TestApis.packages().find(APP_PACKAGE_NAME).grantPermission(Manifest.permission.CAMERA)
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(APP_PACKAGE_NAME)
      }
    }
  }
}
