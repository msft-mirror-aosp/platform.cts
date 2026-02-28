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

import android.Manifest.permission.MANAGE_CONTEXTUAL_MODES
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.modes.ContextualMode
import android.app.modes.ContextualMode.STATE_ACTIVE
import android.app.modes.ContextualMode.STATE_INACTIVE
import android.app.modes.ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB
import android.app.modes.ContextualModeManager
import android.app.modes.ContextualModeManager.ContextualModeListener
import android.app.modes.ContextualModesMutation
import android.companion.CompanionDeviceManager.FLAG_UNIVERSAL_MODES
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.CddTest
import com.android.crossdevicesync.lib.api.CallbackWaiter
import com.android.crossdevicesync.lib.api.ContextualModeSyncDocument
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextualModeSyncTest : CrossDeviceSyncTestBase() {
    private lateinit var doc: ContextualModeSyncDocument
    private lateinit var notificationManager: NotificationManager
    private lateinit var contextualModeManager: ContextualModeManager
    private lateinit var dndModeId: String
    private var modeSyncWasEnabled: Boolean = false
    private var dndWasActive: Boolean = false
    private var modeListener: ContextualModeListener? = null

    override fun setUp() {
        notificationManager = context.getSystemService(NotificationManager::class.java)
        contextualModeManager = context.getSystemService(ContextualModeManager::class.java)
        assumeTrue("Mode sync needs to be supported!", contextualModeManager.isModeSyncSupported)

        withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES, WRITE_SECURE_SETTINGS) {
            assumeTrue("Mode needs to be supported!", !contextualModeManager.modes.isEmpty())
            dndModeId = getDndModeId()!!
            // Enable mode sync
            modeSyncWasEnabled = contextualModeManager.isModeSyncEnabled
            contextualModeManager.isModeSyncEnabled = true
        }

        // Disable DND
        dndWasActive = isDndActive()
        setDndActive(false)

        super.setUp()

        doc = testLib.modeSyncDocument

        // Sleep 500ms before setting data sync flag to ensure the association update has been
        // completed.
        Thread.sleep(500)
        Log.i(TAG, "setUp: enabling system data sync")
        cdm.enableSystemDataSyncForTypes(associationId, FLAG_UNIVERSAL_MODES)
    }

    override fun tearDown() {
        // Restore mode sync enabled setting.
        if (::dndModeId.isInitialized) {
            withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES, WRITE_SECURE_SETTINGS) {
                modeListener?.let { contextualModeManager.unregisterModeListener(it) }
                // Restore mode sync
                contextualModeManager.isModeSyncEnabled = modeSyncWasEnabled
            }
            // Restore DND
            setDndActive(dndWasActive)
        }
        super.tearDown()
    }

    @CddTest(requirement = "3.21/C-1-1,C-1-2")
    @Test
    fun testOutboundDndSync() {
        val waiter: CallbackWaiter<Boolean> = CallbackWaiter()
        doc.addOnRemoteDndChangedListener(SIMPLE_EXECUTOR, waiter)

        // Enable DND.
        setDndActive(true)
        // Ensure interruption filter is set
        assertTrue(hasInterruptionFilter())
        // Verify the DND is synced outbound
        assertTrue(waiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue(doc.isDndEnabled())

        // Disable DND
        setDndActive(false)
        // Ensure interruption filter is unset
        assertFalse(hasInterruptionFilter())
        // Verify the DND is synced outbound
        assertTrue(waiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertFalse(doc.isDndEnabled())
    }

    @CddTest(requirement = "3.21/C-1-1,C-1-2")
    @Test
    fun testInboundDndSync() {
        val waiter: CallbackWaiter<List<ContextualMode>> = CallbackWaiter()
        modeListener = newTestModeListener(changeCallbackWaiter = waiter)
        withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES) {
            contextualModeManager.registerModeListener(SIMPLE_EXECUTOR, modeListener!!)
        }

        // Remote device enables DND.
        doc.setDndEnabled(true)
        // Verify the DND is synced inbound
        assertTrue(
            waiter.waitFor(
                listOf(
                    ContextualMode.Builder(dndModeId)
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_ACTIVE)
                        .build()
                ),
                SYNC_TIMEOUT_SEC,
                TimeUnit.SECONDS
            )
        )
        assertTrue(isDndActive())
        assertTrue(hasInterruptionFilter())

        // Remote device disables DND.
        doc.setDndEnabled(false)
        // Verify the DND is synced inbound
        assertTrue(
            waiter.waitFor(
                listOf(
                    ContextualMode.Builder(dndModeId)
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_INACTIVE)
                        .build()
                ),
                SYNC_TIMEOUT_SEC,
                TimeUnit.SECONDS
            )
        )
        assertFalse(isDndActive())
        assertFalse(hasInterruptionFilter())
    }

    @CddTest(requirement = "3.21/C-1-1,C-1-3")
    @Test
    fun testDndSyncControlledByApi() {
        val outboundSyncWaiter: CallbackWaiter<Boolean> = CallbackWaiter()
        doc.addOnRemoteDndChangedListener(SIMPLE_EXECUTOR, outboundSyncWaiter)
        val inboundSyncWaiter: CallbackWaiter<List<ContextualMode>> = CallbackWaiter()
        modeListener = newTestModeListener(changeCallbackWaiter = inboundSyncWaiter)
        withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES) {
            contextualModeManager.registerModeListener(SIMPLE_EXECUTOR, modeListener!!)
        }

        // Disable DND sync.
        withShellPermissionIdentity(WRITE_SECURE_SETTINGS) {
            contextualModeManager.isModeSyncEnabled = false
        }
        // Wait 2s so that the sync implementation observed the change.
        Thread.sleep(2000)

        // Verify that outbound sync doesn't happen.
        setDndActive(true)
        assertFalse(outboundSyncWaiter.waitFor(true, 2, TimeUnit.SECONDS))
        assertFalse(doc.isDndEnabled)
        // Reset.
        setDndActive(false)
        outboundSyncWaiter.reset()
        inboundSyncWaiter.reset()

        // Verify that inbound sync doesn't happen.
        doc.setDndEnabled(true)
        assertFalse(
            inboundSyncWaiter.waitFor(
                listOf(
                    ContextualMode.Builder(dndModeId)
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_ACTIVE)
                        .build()
                ),
                2,
                TimeUnit.SECONDS
            )
        )
        assertFalse(isDndActive())
        assertFalse(hasInterruptionFilter())
        // Reset.
        doc.setDndEnabled(false)
        outboundSyncWaiter.reset()
        inboundSyncWaiter.reset()

        // Enable DND sync.
        withShellPermissionIdentity(WRITE_SECURE_SETTINGS) {
            contextualModeManager.isModeSyncEnabled = true
        }
        // Wait 2s so that the sync implementation observed the change.
        Thread.sleep(2000)

        // Verify that outbound sync is enabled.
        setDndActive(true)
        assertTrue(outboundSyncWaiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue(doc.isDndEnabled)

        // Verify that inbound sync is enabled.
        doc.setDndEnabled(false)
        assertTrue(
            inboundSyncWaiter.waitFor(
                listOf(
                    ContextualMode.Builder(dndModeId)
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_INACTIVE)
                        .build()
                ),
                SYNC_TIMEOUT_SEC,
                TimeUnit.SECONDS
            )
        )
        assertFalse(isDndActive())
        assertFalse(hasInterruptionFilter())
    }

    private fun getDndModeId(): String? {
        for (mode in contextualModeManager.modes) {
            if (mode.type == TYPE_MANUAL_DO_NOT_DISTURB) {
                return mode.id
            }
        }
        return null
    }

    private fun isDndActive(): Boolean {
        for (mode in withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES) {
            contextualModeManager.modes
        }) {
            if (mode.type == TYPE_MANUAL_DO_NOT_DISTURB) {
                return mode.state == STATE_ACTIVE
            }
        }
        return false
    }

    private fun setDndActive(active: Boolean) {
        withShellPermissionIdentity(MANAGE_CONTEXTUAL_MODES) {
            contextualModeManager.mutateModes(
                ContextualModesMutation.Builder()
                    .addUpdatedMode(
                        ContextualMode.Builder(dndModeId)
                            .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                            .setState(if (active) STATE_ACTIVE else STATE_INACTIVE)
                            .build()
                    )
                    .build()
            )
        }
        // Verify the change is effective.
        assertEquals(active, isDndActive())
    }

    private fun hasInterruptionFilter(): Boolean {
        return notificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL
    }

    private fun newTestModeListener(
        changeCallbackWaiter: CallbackWaiter<List<ContextualMode>>? = null,
        removedCallbackWaiter: CallbackWaiter<String?>? = null
    ): ContextualModeListener {
        return object : ContextualModeListener {
            override fun onModesChanged(modes: List<ContextualMode>) {
                changeCallbackWaiter?.accept(modes)
            }

            override fun onModeRemoved(modeId: String) {
                removedCallbackWaiter?.accept(modeId)
            }
        }
    }

    private companion object {
        const val TAG: String = "ContextualModeSyncTest"
        const val SYNC_TIMEOUT_SEC = 5L
    }
}
