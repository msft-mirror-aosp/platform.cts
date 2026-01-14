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

package android.app.notificationsizeverifier.cts

import android.compat.cts.CompatChangeGatingTestCase
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ddmlib.testrunner.TestResult.TestStatus
import com.android.tradefed.log.LogUtil.CLog
import com.android.tradefed.result.CollectingTestListener
import com.android.tradefed.util.RunUtil
import com.google.common.truth.Truth.assertWithMessage

class NotificationSizeVerifierHostTest : CompatChangeGatingTestCase() {
    private var apkInstalled = false

    companion object {
        private const val DEVICE_TEST_APK = "CtsNotificationSizeVerifierDeviceTest.apk"
        internal const val DEVICE_TEST_PKG = "com.android.test.notificationsizeverifier"
        private const val DEVICE_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
        private const val DEVICE_TEST_CLASS = "$DEVICE_TEST_PKG.NotificationSizeVerifierDeviceTest"
        private const val CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS = 270553691L
        private const val TIMEOUT_SLEEP_MS = 1000L
        private const val POLLING_INTERVAL_MS = 100L
    }

    override fun setUp() {
        super.setUp()
        installIfNotInstalled(DEVICE_TEST_APK)

        val userId = device.currentUser
        CLog.i("Running as user: $userId")

        val grantPermissionCmd =
            "pm grant --user $userId $DEVICE_TEST_PKG android.permission.POST_NOTIFICATIONS"
        device.executeShellCommand(grantPermissionCmd)
        val resetBanCmd = "cmd notification reset_package_ban $DEVICE_TEST_PKG"
        device.executeShellCommand(resetBanCmd)
        val allowNotificationsCmd =
            "cmd notification set_notifications_enabled_for_package --user $userId " +
                    "$DEVICE_TEST_PKG true"
        device.executeShellCommand(allowNotificationsCmd)

        // Wait for a short period to allow the settings changes to propagate
        // within the system. Without this, subsequent operations might flake.
        RunUtil.getDefault().sleep(TIMEOUT_SLEEP_MS)
        clearAllNotifications()
    }

    private fun installIfNotInstalled(apkName: String) {
        if (!device.isPackageInstalled(DEVICE_TEST_PKG)) {
            installPackage(apkName, true)
        }
        apkInstalled = true
    }

    override fun tearDown() {
        resetCompatConfig(
            DEVICE_TEST_PKG,
            setOf(CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS),
            setOf(CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS)
        )
        clearAllNotifications()
        super.tearDown()
    }

    private fun clearAllNotifications() {
        val clearNotifsCmd = "cmd notification cancel_all"
        device.executeShellCommand(clearNotifsCmd)

        // Wait for notifications to be dismissed, with a timeout.
        val startTime = System.currentTimeMillis()
        var notificationsCleared = false
        while (System.currentTimeMillis() - startTime < TIMEOUT_SLEEP_MS) {
            val listNotifsCmd = "cmd notification list"
            val output = device.executeShellCommand(listNotifsCmd).trim()

            // Check if the output indicates no notifications are present.
            if (output.isEmpty() || !output.contains(DEVICE_TEST_PKG)) {
                CLog.i("All notifications appear to be cleared.")
                notificationsCleared = true
                break
            }
            try {
                RunUtil.getDefault().sleep(POLLING_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                CLog.e("Sleep interrupted while waiting for notifications to clear", e)
                break
            }
        }

        if (!notificationsCleared) {
            CLog.w("Notifications may not have cleared within ${TIMEOUT_SLEEP_MS}ms.")
        }
    }

    private fun runDeviceTest(methodName: String, compatChangeEnabledForTestPkg: Boolean = false) {
        val userId = device.currentUser
        val enabledChanges: Set<Long> =
            if (compatChangeEnabledForTestPkg) {
                setOf(CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS)
            } else {
                emptySet()
            }
        val disabledChanges: Set<Long> =
            if (compatChangeEnabledForTestPkg) {
                emptySet()
            } else {
                setOf(CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS)
            }

        setCompatConfig(enabledChanges, disabledChanges, DEVICE_TEST_PKG)

        try {
            val testRunner =
                RemoteAndroidTestRunner(DEVICE_TEST_PKG, DEVICE_TEST_RUNNER, device.iDevice)
            testRunner.setMethodName(DEVICE_TEST_CLASS, methodName)
            testRunner.addInstrumentationArg("ENABLE_MANUAL", "true")

            val listener = CollectingTestListener()

            val success = device.runInstrumentationTestsAsUser(testRunner, userId, listener)
            assertWithMessage("Instrumentation run failed to start for $methodName")
                .that(success)
                .isTrue()

            val result = listener.currentRunResults
            assertWithMessage(
                "Device test run failed for ${result.name}: ${result.runFailureMessage}"
            )
                .that(result.isRunFailure)
                .isFalse()

            if (result.numTests == 0) {
                throw AssertionError("No tests were run on the device for $methodName")
            }
            assertWithMessage(
                "Should run only exactly one test method! Found: ${result.testResults.keys}"
            )
                .that(result.numTests)
                .isEqualTo(1)

            val resultEntry = result.testResults.entries.iterator().next()
            val testStatus = resultEntry.value.status
            if (testStatus != TestStatus.PASSED) {
                if (testStatus == TestStatus.ASSUMPTION_FAILURE) {
                    CLog.w(
                        "Assumption failed in device test: ${resultEntry.key}\n" +
                                "${resultEntry.value.stackTrace}"
                    )
                } else {
                    throw AssertionError(
                        "On-device test failed: ${resultEntry.key}\n" +
                                "${resultEntry.value.stackTrace}"
                    )
                }
            }
        } finally {
            CLog.i("Resetting compat changes for $DEVICE_TEST_PKG")
            resetCompatConfig(DEVICE_TEST_PKG, enabledChanges, disabledChanges)
            clearAllNotifications()
        }
    }

    fun testBitmapOverLimit_ChangeEnabled() {
        runDeviceTest("bitmapOverLimit_ChangeEnabled", true)
    }

    fun testBitmapUnderLimit_ChangeEnabled() {
        runDeviceTest("bitmapUnderLimit_ChangeEnabled", true)
    }

    fun testUriOverLimit_ChangeEnabled() {
        runDeviceTest("uriOverLimit_ChangeEnabled", true)
    }

    fun testUriUnderLimit_ChangeEnabled() {
        runDeviceTest("uriUnderLimit_ChangeEnabled", true)
    }

    fun testBitmapOverLimit_ChangeDisabled() {
        runDeviceTest("bitmapOverLimit_ChangeDisabled", false)
    }

    fun testBitmapUnderLimit_ChangeDisabled() {
        runDeviceTest("bitmapUnderLimit_ChangeDisabled", false)
    }

    fun testUriOverLimit_ChangeDisabled() {
        runDeviceTest("uriOverLimit_ChangeDisabled", false)
    }

    fun testUriUnderLimit_ChangeDisabled() {
        runDeviceTest("uriUnderLimit_ChangeDisabled", false)
    }
}
