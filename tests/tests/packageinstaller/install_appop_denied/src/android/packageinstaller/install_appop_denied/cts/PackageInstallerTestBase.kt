/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.packageinstaller.install_appop_denied.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.FutureResultActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule

private const val TAG = "PackageInstallerTestBase"
const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
const val TEST_APK_EXTERNAL_LOCATION = "/data/local/tmp/cts/packageinstaller"
const val INSTALL_ACTION_CB = "PackageInstallerTestBase.install_cb"

const val CONTENT_AUTHORITY = "android.packageinstaller.install_appop_denied.cts.fileprovider"

const val FIND_OBJECT_TIMEOUT = 1000L
const val WAIT_FOR_UI_TIMEOUT = 5000L
const val WAIT_FOR_RESULT_TIMEOUT = 120000L
const val APP_OP_STR = "REQUEST_INSTALL_PACKAGES"

open class PackageInstallerTestBase {
    @get:Rule
    val installDialogStarter = ActivityTestRule(FutureResultActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    private val pm = context.packageManager
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val apkFile = File(context.filesDir, TEST_APK_NAME)

    /** If a status was received the value of the status, otherwise null */
    private var installSessionResult = LinkedBlockingQueue<Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)

            if (status == STATUS_PENDING_USER_ACTION) {
                val activityIntent = intent.getParcelableExtra<Intent>(EXTRA_INTENT)
                activityIntent!!.addFlags(FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
                installDialogStarter.activity.startActivityForResult(activityIntent)
            }

            installSessionResult.offer(status)
        }
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
    }

    @Before
    fun wakeUpScreen() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun assertTestPackageNotInstalled() {
        try {
            context.packageManager.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    /**
     * Executes a shell command to deny the appOp here,
     * instead of using {@link com.android.tradefed.targetprep.RunCommandTargetPreparer}.
     * This is because the operation from RunCommandTargetPreparer can be executed before the test
     * package is installed for the target user.
     */
    @Before
    fun denyAppOp() {
        val testUserId = context.user.identifier
        val packageName = context.packageName
        uiDevice.executeShellCommand("appops set --user $testUserId $packageName $APP_OP_STR deny")
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(
            receiver,
            IntentFilter(INSTALL_ACTION_CB),
            Context.RECEIVER_EXPORTED
        )
    }

    @Before
    fun waitForUIIdle() {
        uiDevice.waitForIdle()
    }

    /**
     * Wait for session's install result and return it
     */
    protected fun getInstallSessionResult(timeout: Long = WAIT_FOR_RESULT_TIMEOUT): Int? {
        return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Start an installation via a session
     */
    protected fun startInstallationViaSession(): CompletableFuture<Int> {
        val pi = pm.packageInstaller

        // Create session
        val sessionId = pi.createSession(PackageInstaller.SessionParams(MODE_FULL_INSTALL))
        val session = pi.openSession(sessionId)!!

        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }

        // Commit session
        val dialog = FutureResultActivity.doAndAwaitStart {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                    Intent(INSTALL_ACTION_CB).setPackage(context.packageName)
                            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                    FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
        }

        // The system should have asked us to launch the installer
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, getInstallSessionResult())

        return dialog
    }

    /**
     * Start an installation via a session
     */
    protected fun startInstallationViaIntent(): CompletableFuture<Int> {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        return installDialogStarter.activity.startActivityForResult(intent)
    }

    /**
     * Sets the given secure setting to the provided value.
     */
    fun setSecureSetting(secureSetting: String, value: Int) {
        uiDevice.executeShellCommand("settings put secure $secureSetting $value")
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

    /**
     * Find a object in the UI of the installer app
     *
     * @param bySelector The bySelector of the object
     */
    fun findInstallerUIObject(
        bySelector: BySelector,
        errorMessage: String?,
        checkNull: Boolean = true
    ): UiObject2? {
        // Wait for the UI to become idle.
        uiDevice.waitForIdle()

        val message = errorMessage ?: "Failed to find the object: $bySelector"
        var uiObject2: UiObject2? = null
        val startTime = System.currentTimeMillis()
        while (startTime + WAIT_FOR_UI_TIMEOUT > System.currentTimeMillis()) {
            try {
                uiObject2 = uiDevice.wait(Until.findObject(bySelector), FIND_OBJECT_TIMEOUT)
                if (uiObject2 != null) {
                    Log.d(
                        TAG,
                        "Found bounds: ${uiObject2.getVisibleBounds()} of object $bySelector," +
                                " text: ${uiObject2.getText()}," +
                                " package: ${uiObject2.getApplicationPackage()}"
                    )
                    return uiObject2
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
}
