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

package android.virtualdevice.cts.computercontrol

import android.app.KeyguardManager
import android.content.IntentSender
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.extensions.computercontrol.ComputerControlSession
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.use
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
class ComputerControlExtensionsTest {
    private class ComputerControlSessionCallbackImpl : ComputerControlSession.Callback {
        private val future = CompletableFuture<ComputerControlSession?>()
        private var errorCode = ComputerControlSession.ERROR_UNKNOWN

        fun awaitSessionAndClose(block: ((ComputerControlSession) -> Unit)? = null) {
            val session =
                future.get(TestAppAgent.SESSION_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertThat(session).isNotNull()

            val closeFuture = CompletableFuture<Void>()
            session!!.setLifecycleCallback(
                Executors.newSingleThreadExecutor(),
                object : ComputerControlSession.LifecycleCallback {
                    override fun onActive() {}

                    override fun onBlocked(reason: Int, blockingPackage: String?) {}

                    override fun onClosed(reason: Int) {
                        closeFuture.complete(null)
                    }
                }
            )

            session.use {
                block?.invoke(session)
            }

            // Wait for the session to be closed.
            closeFuture.get(TestAppAgent.SESSION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        fun awaitSessionCreationError(): Int {
            val session = future.get(DEADLINE_SECONDS, TimeUnit.SECONDS)
            assertThat(session).isNull()
            return errorCode
        }

        override fun onSessionPending(intentSender: IntentSender) {}

        override fun onSessionCreated(session: ComputerControlSession) {
            future.complete(session)
        }

        override fun onSessionCreationFailed(errorCode: Int) {
            this.errorCode = errorCode
            future.complete(null)
        }

        @Deprecated("Use LifecycleCallback instead") override fun onSessionClosed() {}
    }

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule
    @JvmField
    val adoptShellPermissionsRule: AdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            getInstrumentation().getUiAutomation(),
            "android.permission.ACCESS_COMPUTER_CONTROL",
            "android.permission.POST_NOTIFICATIONS",
        )

    @get:Rule val testName = TestName()

    private val context = getInstrumentation().context
    private var extension: ComputerControlExtensions? = null

    @Before
    fun setUp() {
        extension = ComputerControlExtensions.getInstance(context)
        assumeNotNull(extension)
    }

    @Test
    fun testGetInstance_nullContext() {
        assertThrows(NullPointerException::class.java) {
            ComputerControlExtensions.getInstance(null)
        }
    }

    @Test
    fun testGetInstance_withoutPermission_returnsNonNull() {
        getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        assertThat(ComputerControlExtensions.getInstance(context)).isNotNull()
    }

    @Test
    fun isSessionCreationAvailable_returnsTrue() {
        assertThat(ComputerControlExtensions.isSessionCreationAvailable(context)).isTrue()
    }

    @Test
    fun isSessionCreationAvailable_withoutPermission_returnsFalse() {
        getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        assertThat(ComputerControlExtensions.isSessionCreationAvailable(context)).isFalse()
    }

    @Test
    fun testRequestSession_emptySessionName() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
        }
    }

    @Test
    fun testRequestSession_multipleLiveSessionSameName() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback1 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback1)
        callback1.awaitSessionAndClose { session ->
            // Request a session with the same name again.
            val callback2 = ComputerControlSessionCallbackImpl()
            assertThrows(IllegalArgumentException::class.java) {
                extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback2)
            }
        }
    }

    @Test
    fun testRequestSession_closeAndReopenSessionWithSameName() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback1 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback1)
        callback1.awaitSessionAndClose()

        // Request a session with the same name again.
        val callback2 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback2)
        callback2.awaitSessionAndClose()
    }

    @Test
    fun testRequestSession_emptyTargetPackageNames() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf())
                .build()
        val callback = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
        }
    }

    @Test
    fun testRequestSession_invalidTargetPackageNames() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf("invalid.package.name"))
                .build()
        val callback = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
        }
    }

    @Test
    fun testRequestSession_validAndInvalidTargetPackageNames() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME, "invalid.package.name"))
                .build()
        val callback = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
        }
    }

    @Test
    fun testRequestSession_packageWithoutLauncher() {
        val params =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP2_WITHOUT_LAUNCHER_PACKAGE_NAME))
                .build()
        val callback = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
        }
    }

    @Test
    fun testRequestSession_failWithSessionLimitReached() {
        val params1 =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback1 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params1, Executors.newSingleThreadExecutor(), callback1)
        callback1.awaitSessionAndClose {
            // Request a session with the different name. It should only create one
            // session at the same time. So the second request should fail.
            val params2 =
                ComputerControlSession.Params.Builder(context)
                    .setName("${testName.methodName}2")
                    .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                    .build()
            val callback2 = ComputerControlSessionCallbackImpl()
            extension!!.requestSession(params2, Executors.newSingleThreadExecutor(), callback2)
            val errorCode = callback2.awaitSessionCreationError()
            assertThat(errorCode).isEqualTo(ComputerControlSession.ERROR_SESSION_LIMIT_REACHED)
        }
    }

    @Test
    fun testRequestSession_failWithDeviceLocked() {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        try {
            SystemUtil.runShellCommand("locksettings set-pin 1234")
            SystemUtil.runShellCommand("put secure lock_screen_lock_after_timeout 0")
            SystemUtil.runShellCommand("input keyevent SLEEP")
            PollingCheck.waitFor(DEADLINE_SECONDS * 1000L) { keyguardManager.isDeviceLocked() }
            val params =
                ComputerControlSession.Params.Builder(context)
                    .setName("${testName.methodName}")
                    .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                    .build()
            val callback = ComputerControlSessionCallbackImpl()
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
            val errorCode = callback.awaitSessionCreationError()
            assertThat(errorCode).isEqualTo(ComputerControlSession.ERROR_DEVICE_LOCKED)
        } finally {
            SystemUtil.runShellCommand("input keyevent WAKEUP")
            SystemUtil.runShellCommand("wm dismiss-keyguard")
            SystemUtil.runShellCommand("locksettings clear --old 1234")
            PollingCheck.waitFor(DEADLINE_SECONDS * 1000L) { !keyguardManager.isDeviceLocked() }
        }
    }

    @Test
    fun testRequestSession_failWithPermissionDenied() {
        try {
            SystemUtil.runShellCommand("appops set com.android.shell COMPUTER_CONTROL ignore")
            val params =
                ComputerControlSession.Params.Builder(context)
                    .setName("${testName.methodName}")
                    .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                    .build()
            val callback = ComputerControlSessionCallbackImpl()
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback)
            val errorCode = callback.awaitSessionCreationError()
            assertThat(errorCode).isEqualTo(ComputerControlSession.ERROR_PERMISSION_DENIED)
        } finally {
            SystemUtil.runShellCommand("appops set com.android.shell COMPUTER_CONTROL allow")
        }
    }

    companion object {
        private const val TAG = "ComputerControlExtensionsTest"
        private const val TEST_APP_PACKAGE_NAME = "android.computercontrol.testapp"
        private const val TEST_APP2_WITHOUT_LAUNCHER_PACKAGE_NAME =
            "android.computercontrol.testapp2withoutlauncher"
        private const val DEADLINE_SECONDS = 5L
    }
}
