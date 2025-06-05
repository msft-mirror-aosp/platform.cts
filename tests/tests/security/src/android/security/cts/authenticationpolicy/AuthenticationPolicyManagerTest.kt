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
package android.security.cts.authenticationpolicy

import android.Manifest.permission.MANAGE_BIOMETRIC
import android.Manifest.permission.MANAGE_SECURE_LOCK_DEVICE
import android.Manifest.permission.TEST_BIOMETRIC
import android.Manifest.permission.USE_BIOMETRIC_INTERNAL
import android.app.Instrumentation
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricTestSession
import android.hardware.biometrics.SensorProperties
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.security.Flags
import android.security.Flags.secureLockDevice
import android.security.Flags.secureLockdown
import android.security.authenticationpolicy.AuthenticationPolicyManager
import android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_NO_BIOMETRICS_ENROLLED
import android.security.authenticationpolicy.AuthenticationPolicyManager.SUCCESS
import android.security.authenticationpolicy.AuthenticationPolicyManager.SecureLockDeviceStatusListener
import android.security.authenticationpolicy.DisableSecureLockDeviceParams
import android.security.authenticationpolicy.EnableSecureLockDeviceParams
import android.security.cts.authenticationpolicy.AuthenticationPolicyManagerTest.Companion.TAG
import android.security.cts.authenticationpolicy.AuthenticationPolicyManagerTest.Companion.TIMEOUT_MS
import android.server.biometrics.util.Utils.enrollForSensor
import android.server.biometrics.util.Utils.waitForAllUnenrolled
import android.util.Log
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireNotAutomotive
import com.android.bedstead.harrier.annotations.RequireNotTv
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.Assert.assertDoesNotThrow
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@Presubmit
@SdkSuppress(minSdkVersion = 29)
@RequireNotAutomotive(reason = "Requires AuthenticationPolicyManager")
@RequireNotTv(reason = "Requires AuthenticationPolicyManager")
@RequireNotWatch(reason = "Requires AuthenticationPolicyManager")
@EnsureHasPermission(MANAGE_BIOMETRIC, TEST_BIOMETRIC, USE_BIOMETRIC_INTERNAL)
@RequiresFlagsEnabled(Flags.FLAG_SECURE_LOCK_DEVICE, Flags.FLAG_SECURE_LOCKDOWN)
class AuthenticationPolicyManagerTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val testExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val testListener: TestSecureLockDeviceStatusListener =
        TestSecureLockDeviceStatusListener()

    private lateinit var biometricManager: BiometricManager
    private lateinit var sensorProperties: MutableList<SensorProperties>
    private lateinit var authenticationPolicyManager: AuthenticationPolicyManager

    companion object {
        const val TAG = "AuthenticationPolicyManagerTest"
        const val TIMEOUT_MS = 1000L

        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()
    }

    @Before
    fun setUp() {
        biometricManager = context.getSystemService(BiometricManager::class.java)
        assertThat(biometricManager).isNotNull()

        sensorProperties = biometricManager.sensorProperties
        assertThat(sensorProperties).isNotNull()

        authenticationPolicyManager =
            context.getSystemService(AuthenticationPolicyManager::class.java)
        assumeNotNull(
            "setup | AuthenticationPolicyManager service should be " + "available",
            authenticationPolicyManager,
        )

        assumeTrue("setup | secure_lockdown flag must be enabled", secureLockdown())
        assumeTrue("setup | secure_lock_device flag must be enabled", secureLockDevice())
    }

    @After
    fun tearDown() {
        try {
            if (!testExecutor.isShutdown) {
                testExecutor.shutdown()
                if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    testExecutor.shutdownNow()
                }
            }

            TestApis.permissions().withPermission(MANAGE_SECURE_LOCK_DEVICE).use {
                try {
                    authenticationPolicyManager.unregisterSecureLockDeviceStatusListener(
                        testListener
                    )
                } catch (_: IllegalArgumentException) {
                    Log.d(TAG, "tearDown | testListener was not registered for this test.")
                } catch (e: Exception) {
                    Log.w(TAG, "tearDown | Exception during listener unregistration: ", e)
                }
                authenticationPolicyManager.disableSecureLockDevice(
                    DisableSecureLockDeviceParams("")
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "tearDown() | SecurityException, likely permission issue", e)
        } catch (e: Exception) {
            Log.w(TAG, "tearDown() | Exception during tearDown(): ", e)
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Ignore("b/421113198, b/420188634")
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testEnableSecureLockDevice_withAllPrerequisites_returnsSuccess() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val strongBiometricSensor = sensorProperties.findFirstStrongBiometricSensor()
        assumeNotNull(
            "Device must have at least one strong biometric sensor to run this test",
            strongBiometricSensor,
        )

        biometricManager.createTestSession(strongBiometricSensor!!.sensorId).use { session ->
            enrollForSensor(session, strongBiometricSensor.sensorId)

            assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable()).isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()

            val enableStatus =
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))

            assertThat(enableStatus).isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isTrue()
            assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable()).isEqualTo(SUCCESS)

            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Ignore("b/421112670, b/420188634")
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testEnableSecureLockDevice_whenAlreadyEnabled_returnsAlreadyEnabled() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val strongBiometricSensor = sensorProperties.findFirstStrongBiometricSensor()
        assumeNotNull(
            "Device must have at least one strong biometric sensor to run this test",
            strongBiometricSensor,
        )

        biometricManager.createTestSession(strongBiometricSensor!!.sensorId).use { session ->
            enrollForSensor(session, strongBiometricSensor.sensorId)

            assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable()).isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()

            val enableStatus =
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))

            assertThat(enableStatus).isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isTrue()

            val enableSecureLockDeviceStatus =
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))
            assertThat(enableSecureLockDeviceStatus)
                .isEqualTo(AuthenticationPolicyManager.ERROR_ALREADY_ENABLED)

            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testEnableSecureLockDevice_onlyNonStrongBiometricsEnrolled_returnsInsufficientBiometrics() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val nonStrongBiometricSensor = sensorProperties.findFirstNonStrongBiometricSensor()
        assumeTrue(
            "Device must have at least one non-strong biometric sensor to run this test",
            nonStrongBiometricSensor != null,
        )

        biometricManager.createTestSession(nonStrongBiometricSensor!!.sensorId).use { session ->
            enrollForSensor(session, nonStrongBiometricSensor.sensorId)

            assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable())
                .isEqualTo(AuthenticationPolicyManager.ERROR_INSUFFICIENT_BIOMETRICS)
            val enableStatus =
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))

            assertThat(enableStatus)
                .isEqualTo(AuthenticationPolicyManager.ERROR_INSUFFICIENT_BIOMETRICS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()

            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testEnableSecureLockDevice_whenNoBiometricsEnrolled_returnsNoBiometricsEnrolled() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        waitForAllUnenrolled()

        assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable())
            .isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED)
        val enableStatus =
            authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))

        assertThat(enableStatus).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED)
        assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#registerSecureLockDeviceStatusListener"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
            ]
    )
    @Ignore("b/420998029, b/420680233, b/420327508, b/420188634")
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testAvailableStatusUpdate_notifiesSecureLockDeviceStatusListeners() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val strongBiometricSensor = sensorProperties.findFirstStrongBiometricSensor()
        assumeTrue("Requires strong sensor", strongBiometricSensor != null)

        biometricManager.createTestSession(strongBiometricSensor!!.sensorId).use { session ->
            authenticationPolicyManager.registerSecureLockDeviceStatusListener(
                testExecutor,
                testListener,
            )

            // Initial callbacks on registration
            assertThat(testListener.awaitAvailableCallback()).isTrue()
            assertThat(testListener.awaitEnabledCallback()).isTrue()

            testListener.assertLastAvailableStatus(expectedAvailable = ERROR_NO_BIOMETRICS_ENROLLED)
            testListener.assertLastEnabledStatus(expectedEnabled = false)

            testListener.reset()

            // Enroll biometric, verify callbacks
            enrollForSensor(session, strongBiometricSensor.sensorId)

            assertThat(testListener.awaitAvailableCallback()).isTrue()
            testListener.assertLastAvailableStatus(expectedAvailable = SUCCESS)

            authenticationPolicyManager.unregisterSecureLockDeviceStatusListener(testListener)
            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#disableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Ignore("b/420952162, b/420188634")
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testDisableSecureLockDevice_returnsSuccess() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val strongBiometricSensor = sensorProperties.findFirstStrongBiometricSensor()
        assumeNotNull(
            "Device must have at least one strong biometric sensor to run this test",
            strongBiometricSensor,
        )

        biometricManager.createTestSession(strongBiometricSensor!!.sensorId).use { session ->
            enrollForSensor(session, strongBiometricSensor.sensorId)

            assertThat(
                    authenticationPolicyManager.enableSecureLockDevice(
                        EnableSecureLockDeviceParams("")
                    )
                )
                .isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isTrue()

            val disableStatus =
                authenticationPolicyManager.disableSecureLockDevice(
                    DisableSecureLockDeviceParams("")
                )

            assertThat(disableStatus).isEqualTo(SUCCESS)
            assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()
            assertThat(authenticationPolicyManager.isSecureLockDeviceAvailable()).isEqualTo(SUCCESS)
            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#disableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#registerSecureLockDeviceStatusListener"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#unregisterSecureLockDeviceStatusListener"),
            ]
    )
    @Ignore("b/420711730, b/420188634")
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testEnabledStatusUpdate_notifiesSecureLockDeviceStatusListeners() {
        assumeNotNull("test requires non-null BiometricManager", biometricManager)
        assumeNotNull("test requires non-null SensorProperties", sensorProperties)
        val strongBiometricSensor = sensorProperties.findFirstStrongBiometricSensor()
        assumeTrue("Requires strong sensor", strongBiometricSensor != null)

        biometricManager.createTestSession(strongBiometricSensor!!.sensorId).use { session ->
            enrollForSensor(session, strongBiometricSensor.sensorId)
            authenticationPolicyManager.registerSecureLockDeviceStatusListener(
                testExecutor,
                testListener,
            )

            // Initial callbacks on registration
            assertThat(testListener.awaitAvailableCallback()).isTrue()
            assertThat(testListener.awaitEnabledCallback()).isTrue()

            testListener.assertLastAvailableStatus(expectedAvailable = SUCCESS)
            testListener.assertLastEnabledStatus(expectedEnabled = false)

            testListener.reset()

            // Enable secure lock device, verify callbacks
            assertThat(
                    authenticationPolicyManager.enableSecureLockDevice(
                        EnableSecureLockDeviceParams("")
                    )
                )
                .isEqualTo(SUCCESS)

            assertThat(testListener.awaitAvailableCallback()).isTrue()
            assertThat(testListener.awaitEnabledCallback()).isTrue()
            testListener.assertLastAvailableStatus(expectedAvailable = SUCCESS)
            testListener.assertLastEnabledStatus(expectedEnabled = true)

            testListener.reset()

            // Disable secure lock device, verify callbacks
            assertThat(
                    authenticationPolicyManager.disableSecureLockDevice(
                        DisableSecureLockDeviceParams("")
                    )
                )
                .isEqualTo(SUCCESS)

            assertThat(testListener.awaitAvailableCallback()).isTrue()
            assertThat(testListener.awaitEnabledCallback()).isTrue()
            testListener.assertLastAvailableStatus(expectedAvailable = SUCCESS)
            testListener.assertLastEnabledStatus(expectedEnabled = false)

            authenticationPolicyManager.unregisterSecureLockDeviceStatusListener(testListener)
            cleanupSession(session)
        }
        waitForAllUnenrolled()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#disableSecureLockDevice"),
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled"),
            ]
    )
    @Test
    @EnsureHasPermission(MANAGE_SECURE_LOCK_DEVICE)
    fun testDisableSecureLockDevice_whenNotEnabled() {
        val disableStatus =
            authenticationPolicyManager.disableSecureLockDevice(DisableSecureLockDeviceParams(""))

        assertThat(disableStatus).isEqualTo(SUCCESS)
        assertThat(authenticationPolicyManager.isSecureLockDeviceEnabled).isFalse()
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#enableSecureLockDevice")
            ]
    )
    @Test
    fun testEnableSecureLockDevice_withPermission_doesNotThrowException() {
        TestApis.permissions().withPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertDoesNotThrow {
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))
            }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                        "#enableSecureLockDevice")
            ]
    )
    @Test
    fun testEnableSecureLockDevice_withoutPermission_throwsException() {
        TestApis.permissions().withoutPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertThrows(SecurityException::class.java) {
                authenticationPolicyManager.enableSecureLockDevice(EnableSecureLockDeviceParams(""))
            }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#disableSecureLockDevice")
            ]
    )
    @Test
    fun testDisableSecureLockDevice_withPermission_doesNotThrowException() {
        TestApis.permissions().withPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertDoesNotThrow {
                authenticationPolicyManager.disableSecureLockDevice(
                    DisableSecureLockDeviceParams("")
                )
            }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                        "#disableSecureLockDevice")
            ]
    )
    @Ignore("b/420461434")
    @Test
    fun testDisableSecureLockDevice_withoutPermission_throwsException() {
        TestApis.permissions().withoutPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertThrows(SecurityException::class.java) {
                authenticationPolicyManager.disableSecureLockDevice(
                    DisableSecureLockDeviceParams("")
                )
            }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceAvailable")
            ]
    )
    @Test
    fun testIsSecureLockDeviceAvailable_withPermission_doesNotThrowException() {
        TestApis.permissions().withPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertDoesNotThrow { authenticationPolicyManager.isSecureLockDeviceAvailable() }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                        "#isSecureLockDeviceAvailable")
            ]
    )
    @Test
    fun testIsSecureLockDeviceAvailable_withoutPermission_throwsException() {
        TestApis.permissions().withoutPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertThrows(SecurityException::class.java) {
                authenticationPolicyManager.isSecureLockDeviceAvailable()
            }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                    "#isSecureLockDeviceEnabled")
            ]
    )
    @Test
    fun testIsSecureLockDeviceEnabled_withPermission_doesNotThrowException() {
        TestApis.permissions().withPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertDoesNotThrow { authenticationPolicyManager.isSecureLockDeviceEnabled }
        }
    }

    @ApiTest(
        apis =
            [
                ("android.security.authenticationpolicy.AuthenticationPolicyManager" +
                        "#isSecureLockDeviceEnabled")
            ]
    )
    @Test
    fun testIsSecureLockDeviceEnabled_withoutPermission_throwsException() {
        TestApis.permissions().withoutPermission(MANAGE_SECURE_LOCK_DEVICE).use {
            assertThrows(SecurityException::class.java) {
                authenticationPolicyManager.isSecureLockDeviceEnabled
            }
        }
    }

    private fun cleanupSession(session: BiometricTestSession) {
        session.cleanupInternalState(context.userId)
    }
}

private class TestSecureLockDeviceStatusListener : SecureLockDeviceStatusListener {
    private var enabledCallbackReceivedTime: Long = 0L
    private var availableCallbackReceivedTime: Long = 0L

    private var onEnabledLatch = CountDownLatch(1)
    private var onAvailableLatch = CountDownLatch(1)
    private val isSecureLockDeviceAvailable = AtomicReference<Int?>(null)
    private val isSecureLockDeviceEnabled = AtomicReference<Boolean?>(null)

    override fun onSecureLockDeviceEnabledStatusChanged(enabled: Boolean) {
        enabledCallbackReceivedTime = System.currentTimeMillis()
        Log.d(
            TAG,
            "onSecureLockDeviceEnabledStatusChanged received: $enabled at " +
                "$enabledCallbackReceivedTime",
        )

        isSecureLockDeviceEnabled.set(enabled)
        onEnabledLatch.countDown()
    }

    override fun onSecureLockDeviceAvailableStatusChanged(available: Int) {
        availableCallbackReceivedTime = System.currentTimeMillis()
        Log.d(
            TAG,
            "onSecureLockDeviceAvailableStatusChanged received: $available at " +
                "$availableCallbackReceivedTime",
        )

        isSecureLockDeviceAvailable.set(available)
        onAvailableLatch.countDown()
    }

    fun reset() {
        onEnabledLatch = CountDownLatch(1)
        onAvailableLatch = CountDownLatch(1)
        isSecureLockDeviceAvailable.set(null)
        isSecureLockDeviceEnabled.set(null)
    }

    fun awaitEnabledCallback(timeoutMillis: Long = TIMEOUT_MS): Boolean {
        val startTime = System.currentTimeMillis()
        val result = onEnabledLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        val endTime = System.currentTimeMillis()
        if (!result) {
            Log.w(TAG, "Listener timed out waiting ${endTime - startTime}ms for enabled callback")
        } else {
            Log.d(TAG, "Listener callback received after ${endTime - startTime}ms")
        }
        return result
    }

    fun awaitAvailableCallback(timeoutMillis: Long = TIMEOUT_MS): Boolean {
        val startTime = System.currentTimeMillis()
        val result = onAvailableLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        val endTime = System.currentTimeMillis()
        if (!result) {
            Log.w(
                TAG,
                "Listener timed out waiting ${endTime - startTime}ms for available callback.",
            )
        } else {
            Log.d(TAG, "Listener callback received after ${endTime - startTime}ms")
        }
        return result
    }

    fun assertLastAvailableStatus(expectedAvailable: Int) {
        assertThat(isSecureLockDeviceAvailable.get()).isEqualTo(expectedAvailable)
    }

    fun assertLastEnabledStatus(expectedEnabled: Boolean) {
        if (expectedEnabled) {
            assertThat(isSecureLockDeviceEnabled.get()).isTrue()
        } else {
            assertThat(isSecureLockDeviceEnabled.get()).isFalse()
        }
    }
}

/**
 * Finds the first sensor in the list that has STRENGTH_STRONG.
 *
 * @return The [SensorProperties] of the first strong biometric sensor, or null if none is found.
 */
private fun List<SensorProperties>.findFirstStrongBiometricSensor(): SensorProperties? {
    return this.firstOrNull { it.sensorStrength == SensorProperties.STRENGTH_STRONG }
}

/**
 * Finds the first sensor in the list that does not have STRENGTH_STRONG.
 *
 * @return The [SensorProperties] of the first non-strong biometric sensor, or null if none is
 *   found.
 */
private fun List<SensorProperties>.findFirstNonStrongBiometricSensor(): SensorProperties? {
    return this.firstOrNull { it.sensorStrength != SensorProperties.STRENGTH_STRONG }
}
