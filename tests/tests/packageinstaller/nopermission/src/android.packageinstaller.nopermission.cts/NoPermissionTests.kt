/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.packageinstaller.nopermission.cts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertWithMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "NoPermissionTests"
private const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
private const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
private const val TEST_APK_EXTERNAL_LOCATION = "/data/local/tmp/cts/nopermission"
private const val CONTENT_AUTHORITY = "android.packageinstaller.nopermission.cts.fileprovider"
private const val INSTALL_CONFIRM_TEXT = "Install"
private const val WM_DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard"

private const val ACTION = "NoPermissionTests.install_cb"

private const val WAIT_FOR_UI_TIMEOUT = 5000L

private const val FIND_OBJECT_TIMEOUT = 1000L

@RunWith(AndroidJUnit4::class)
@MediumTest
@AppModeFull
class NoPermissionTests {
    private var context = InstrumentationRegistry.getInstrumentation().targetContext
    private var pm = context.packageManager
    private var packageName = context.packageName
    private var apkFile = File(context.filesDir, TEST_APK_NAME)
    private var uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)

            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val activityIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                activityIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(activityIntent)
            }
        }
    }

    @Before
    fun wakeUpScreen() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand(WM_DISMISS_KEYGUARD_COMMAND)
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION),
                Context.RECEIVER_EXPORTED
        )
    }

    @Before
    fun waitForUIIdle() {
        uiDevice.waitForIdle()
    }

    private fun launchPackageInstallerViaIntent() {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun launchPackageInstallerViaSession() {
        val pi = pm.packageInstaller

        // Create session
        val sessionId = pi.createSession(PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ))
        val session = pi.openSession(sessionId)!!

        // Write data to session
        File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME).inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }

        // Commit session
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
                Intent(ACTION).setPackage(context.packageName)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(pendingIntent.intentSender)
    }

    /**
     * Find a button in the UI of the installer app
     *
     * @param bySelector The bySelector of the button
     */
    fun findInstallerUIButton(
        bySelector: BySelector,
        errorMessage: String?,
        checkNull: Boolean = true
    ): UiObject2? {
        // Wait for a minimum 2000ms and maximum 10000ms for the UI to become idle.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(
            (2 * FIND_OBJECT_TIMEOUT),
            (10 * FIND_OBJECT_TIMEOUT)
        )

        val message = errorMessage ?: "Failed to find the button: $bySelector"
        var button: UiObject2? = null
        val startTime = System.currentTimeMillis()
        while (startTime + WAIT_FOR_UI_TIMEOUT > System.currentTimeMillis()) {
            try {
                button = uiDevice.wait(Until.findObject(bySelector), FIND_OBJECT_TIMEOUT)
                if (button != null) {
                    Log.d(
                        TAG,
                        "Found bounds: ${button.getVisibleBounds()} of button $bySelector," +
                                " text: ${button.getText()}," +
                                " package: ${button.getApplicationPackage()}"
                    )
                    return button
                } else {
                    // Maybe the screen is small. Scroll forward and attempt to click
                    scroll()
                }
            } catch (ignore: Throwable) {
            }
        }

        if (checkNull) {
            dumpWindowHierarchy()
            Assert.fail(message)
        }
        return null
    }

    private fun scroll() {
        UiScrollable(UiSelector().scrollable(true)).scrollForward()
    }

    @Throws(InterruptedException::class, IOException::class)
    fun dumpWindowHierarchy() {
        val outputStream = ByteArrayOutputStream()
        uiDevice.dumpWindowHierarchy(outputStream)
        val windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name())

        Log.w(TAG, "Window hierarchy:")
        for (line in windowHierarchy.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()) {
            Thread.sleep(10)
            Log.w(TAG, line)
        }
    }

    private fun assertInstallSucceeded(errorMessage: String) {
        val selector = By.text(Pattern.compile(
            INSTALL_CONFIRM_TEXT,
            Pattern.CASE_INSENSITIVE
        ))
        findInstallerUIButton(selector, errorMessage)
        uiDevice.pressBack()
    }

    private fun assertInstallFailed(errorMessage: String) {
        val selector = By.text(Pattern.compile(
            INSTALL_CONFIRM_TEXT,
            Pattern.CASE_INSENSITIVE
        ))
        assertWithMessage(errorMessage).that(findInstallerUIButton(
            selector,
            errorMessage,
            /* checkNull= */
            false
        )).isNull()
        uiDevice.pressBack()
    }

    @Test
    fun noPermissionsTestIntent() {
        launchPackageInstallerViaIntent()

        if (pm.getPackageInfo(packageName, 0).applicationInfo!!.targetSdkVersion
                >= Build.VERSION_CODES.O) {
            assertInstallFailed("Package Installer UI should not appear")
        } else {
            assertInstallSucceeded("Package Installer UI should appear")
        }
    }

    @Test
    fun noPermissionsTestSession() {
        launchPackageInstallerViaSession()

        if (pm.getPackageInfo(packageName, 0).applicationInfo!!.targetSdkVersion
                >= Build.VERSION_CODES.O) {
            assertInstallFailed("Package Installer UI should not appear")
        } else {
            assertInstallSucceeded("Package Installer UI should appear")
        }
    }

    @After
    fun unregisterInstallResultReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    @After
    fun uninstallTestPackage() {
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }
}
