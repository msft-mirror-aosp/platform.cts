/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.packageinstaller.install.cts

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.platform.test.annotations.AppModeFull
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class PiaStagesLatencyTrackerTest : PackageInstallerTestBase() {

    @Before
    fun ensureCleanStateBefore() {
        // Failsafes the test in case a previous test crashed and left the app installed
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }

    @After
    fun cleanUpAfter() {
        // Removes the app after the test finishes
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }

    @Test
    fun testTracker_UriBasedInstall_ExecutesWithoutCrashing() {
        // 1. Automatically resolves the FileProvider URI and starts the Intent
        Log.d(TAG, "testTracker_UriBasedInstall_ExecutesWithoutCrashing()")
        Log.d(TAG, "startingInstallationViaIntent()")
        val installation = startInstallationViaIntent()

        // 2. Click through the UI
        Log.d(TAG, "clickInstallerUIButton(INSTALL_BUTTON_ID)")
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // 3. WAIT for the installer Activity to finish and return RESULT_OK
        assertEquals(RESULT_OK, installation.get(GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS))

        // 3. Verify installation was successful
        Log.d(TAG, "assertInstalled()")
        assertInstalled()
    }

    @Test
    fun testTracker_InstallFails_ExecutesWithoutCrashing() {
        // 1. Automatically resolves the FileProvider URI and starts the Intent
        Log.d(TAG, "testTracker_UriBasedInstall_ExecutesWithoutCrashing()")
        Log.d(TAG, "startingInstallationViaIntent()")
        val installation = startInstallationViaIntent()

        // 2. Click through the UI
        Log.d(TAG, "clickInstallerUIButton(CANCEL_BUTTON_ID)")
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // 3. WAIT for the installer Activity to finish and return RESULT_CANCELED
        assertEquals(RESULT_CANCELED, installation.get(GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS))

        // 3. Verify installation was successful
        Log.d(TAG, "assertNotInstalled()")
        assertNotInstalled()
    }
}
