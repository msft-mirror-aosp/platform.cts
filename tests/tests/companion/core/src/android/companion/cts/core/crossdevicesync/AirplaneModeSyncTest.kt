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
package android.companion.cts.core.crossdevicesync

import android.Manifest.permission.NETWORK_SETTINGS
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager.FLAG_AIRPLANE_MODE
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.CddTest
import com.android.crossdevicesync.lib.api.AirplaneModeSyncDocument
import com.android.crossdevicesync.lib.api.CallbackWaiter
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirplaneModeSyncTest : CrossDeviceSyncTestBase() {
    private lateinit var doc: AirplaneModeSyncDocument
    private lateinit var connectivityManager: ConnectivityManager
    private var apmSyncWasEnabled: Boolean = false
    private var apmWasEnabled: Boolean = false

    private val apmWaiter = CallbackWaiter<Boolean>()
    private val apmObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val enabled = isAirplaneModeEnabled()
            Log.i(TAG, "ContentObserver: airplane mode changed to $enabled")
            apmWaiter.accept(enabled)
        }
    }

    override fun setUp() {
        val configEnabled = try {
            Resources.getSystem().getBoolean(
                Resources.getSystem()
                    .getIdentifier("config_supportAirplaneModeSync", "bool", "android")
            )
        } catch (_: Exception) {
            false
        }
        assumeTrue("Airplane Mode Sync needs to be supported!", configEnabled)
        connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
            false,
            apmObserver
        )

        withShellPermissionIdentity(NETWORK_SETTINGS, WRITE_SECURE_SETTINGS) {
            // Enable airplane mode sync
            apmSyncWasEnabled = isAirplaneModeSyncEnabled()
            setAirplaneModeSyncEnabled(true)

            // Disable Airplane Mode
            apmWasEnabled = isAirplaneModeEnabled()
            setAirplaneModeActive(false)
        }

        // Airplane Mode Sync is enabled if this device or the connected device is a watch.
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            remoteDeviceProfile = AssociationRequest.DEVICE_PROFILE_WATCH
        }
        super.setUp()

        doc = testLib.airplaneModeSyncDocument

        // Sleep 500ms before setting data sync flag to ensure the association update has been
        // completed.
        Thread.sleep(500)
        Log.i(TAG, "setUp: enabling airplane mode sync")
        cdm.enableSystemDataSyncForTypes(associationId, FLAG_AIRPLANE_MODE)
    }

    override fun tearDown() {
        if (::connectivityManager.isInitialized) {
            withShellPermissionIdentity(NETWORK_SETTINGS, WRITE_SECURE_SETTINGS) {
                // Restore airplane mode sync
                setAirplaneModeSyncEnabled(apmSyncWasEnabled)
                // Restore Airplane Mode
                setAirplaneModeActive(apmWasEnabled)
            }
        }
        context.contentResolver.unregisterContentObserver(apmObserver)
        super.tearDown()
    }

    @CddTest(requirement = "3.21/C-1-4,C-1-5")
    @Test
    fun testOutboundAirplaneModeSync() {
        val waiter: CallbackWaiter<Boolean> = CallbackWaiter()
        doc.addOnRemoteAirplaneModeChangedListener(SIMPLE_EXECUTOR, waiter)

        // Enable Airplane Mode.
        setAirplaneModeActive(true)
        // Verify the Airplane Mode is synced outbound
        assertTrue(waiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue(doc.isAirplaneModeEnabled())

        // Disable Airplane Mode
        setAirplaneModeActive(false)
        // Verify the Airplane Mode is synced outbound
        assertTrue(waiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertFalse(doc.isAirplaneModeEnabled())
    }

    @CddTest(requirement = "3.21/C-1-4,C-1-5")
    @Test
    fun testInboundAirplaneModeSync() {
        // Remote device enables Airplane Mode.
        apmWaiter.reset()
        doc.setAirplaneModeEnabled(true)
        // Verify the Airplane Mode is synced inbound
        assertTrue(apmWaiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue(isAirplaneModeEnabled())

        // Remote device disables Airplane Mode.
        apmWaiter.reset()
        doc.setAirplaneModeEnabled(false)
        // Verify the Airplane Mode is synced inbound
        assertTrue(apmWaiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertFalse(isAirplaneModeEnabled())
    }

    @CddTest(requirement = "3.21/C-1-4,C-1-5")
    @Test
    fun testAirplaneModeSyncControlledBySyncSetting() {
        val outboundSyncWaiter: CallbackWaiter<Boolean> = CallbackWaiter()
        doc.addOnRemoteAirplaneModeChangedListener(SIMPLE_EXECUTOR, outboundSyncWaiter)

        // Disable Airplane Mode sync.
        setAirplaneModeSyncEnabled(false)
        // Wait 2s so that the sync implementation observed the change.
        Thread.sleep(2000)

        // Verify that outbound sync doesn't happen.
        setAirplaneModeActive(true)
        assertFalse(outboundSyncWaiter.waitFor(true, 2, TimeUnit.SECONDS))
        assertFalse(doc.isAirplaneModeEnabled)
        // Reset.
        setAirplaneModeActive(false)
        outboundSyncWaiter.reset()

        // Verify that inbound sync doesn't happen.
        apmWaiter.reset()
        doc.setAirplaneModeEnabled(true)
        assertFalse(apmWaiter.waitFor(true, 2, TimeUnit.SECONDS))
        assertFalse(isAirplaneModeEnabled())
        // Reset.
        doc.setAirplaneModeEnabled(false)
        outboundSyncWaiter.reset()

        // Enable Airplane Mode sync.
        setAirplaneModeSyncEnabled(true)
        // Wait 2s so that the sync implementation observed the change.
        Thread.sleep(2000)

        // Verify that outbound sync is enabled.
        setAirplaneModeActive(true)
        assertTrue(outboundSyncWaiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue(doc.isAirplaneModeEnabled)

        // Verify that inbound sync is enabled.
        apmWaiter.reset()
        doc.setAirplaneModeEnabled(false)
        assertTrue(apmWaiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertFalse(isAirplaneModeEnabled())
    }

    private fun isAirplaneModeEnabled() =
        Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun isAirplaneModeSyncEnabled() =
        Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_SYNC, 1) == 1

    private fun setAirplaneModeSyncEnabled(enabled: Boolean) {
        withShellPermissionIdentity(WRITE_SECURE_SETTINGS) {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_SYNC,
                if (enabled) 1 else 0
            )
        }
    }

    private fun setAirplaneModeActive(active: Boolean) {
        Log.i(TAG, "setAirplaneModeActive : try $active")
        if (isAirplaneModeEnabled() == active) {
            return
        }
        apmWaiter.reset()
        withShellPermissionIdentity(NETWORK_SETTINGS) {
            connectivityManager.setAirplaneMode(active)
        }
        // Verify the change is effective.
        assertTrue(apmWaiter.waitFor(active, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        Log.i(TAG, "setAirplaneModeActive: set to $active")
    }

    private companion object {
        const val TAG: String = "AirplaneModeSyncTest"
        const val SYNC_TIMEOUT_SEC = 5L
    }
}
