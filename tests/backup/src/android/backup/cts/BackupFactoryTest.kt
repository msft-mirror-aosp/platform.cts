/*
 * Copyright 2026 The Android Open Source Project
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

package android.backup.cts

import android.app.Flags
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFactoryTest : BaseBackupCtsTest() {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    override fun setUp() {
        super.setUp()
        // Ensure transport is wiped to ensure our backup isn't skipped
        clearBackupDataInLocalTransport(BACKUP_APP_NAME)
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    @ApiTest(apis = ["android.app.AppComponentFactory#instantiateBackupAgent"])
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BACKUPAGENT_CREATION)
    fun testBackupAgent_isInstantiatedViaFactory() {
        assumeTrue("Device does not support backup", isBackupSupported())

        // Launch the main activity so the app qualifies for backup.
        createTestFileOfSize(BACKUP_APP_NAME, 1)

        val context = InstrumentationRegistry.getInstrumentation().context
        val latch = CountDownLatch(1)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_INSTANTIATED) {
                    latch.countDown()
                }
            }
        }

        // Register receiver for the cross-process signal from the FactoryApp
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION_INSTANTIATED),
            Context.RECEIVER_EXPORTED
        )

        try {
            // Trigger the backup.
            // This makes ActivityThread call the new instantiateBackupAgent method.
            SystemUtil.runShellCommand("bmgr backupnow $BACKUP_APP_NAME")

            // Wait for the signal from TestComponentFactory
            val wasInstantiated = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertWithMessage("AppComponentFactory.instantiateBackupAgent was not called")
                .that(wasInstantiated)
                .isTrue()
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    companion object {
        private const val BACKUP_APP_NAME = "android.backup.factory"
        private const val ACTION_INSTANTIATED = "android.backup.app.AGENT_INSTANTIATED"
        private const val TIMEOUT_SECONDS = 10L
    }
}
