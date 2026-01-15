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
import org.junit.After
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
    class ComputerControlSessionCallbackImpl : ComputerControlSession.Callback {
        private val future = CompletableFuture<ComputerControlSession?>()
        private var errorCode = ERROR_CODE_UNSET

        fun waitForSession(): ComputerControlSession? {
            return future.get(DEADLINE_SECONDS, TimeUnit.SECONDS)
        }

        fun getErrorCode(): Int {
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
    private var session: ComputerControlSession? = null

    @Before
    fun setUp() {
        extension = ComputerControlExtensions.getInstance(context)
        assumeNotNull(extension)
    }

    @After
    fun tearDown() {
        session?.close()
    }

    @Test
    fun testGetInstance_nullContext() {
        assertThrows(NullPointerException::class.java) {
            ComputerControlExtensions.getInstance(null)
        }
    }

    @Test
    fun testGetInstance_withoutPermission_returnsNull() {
        getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        assertThat(ComputerControlExtensions.getInstance(context)).isNull()
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
        session = callback1.waitForSession()
        assertThat(session).isNotNull()

        // Request a session with the same name again.
        val callback2 = ComputerControlSessionCallbackImpl()
        assertThrows(IllegalArgumentException::class.java) {
            extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback2)
        }
        session!!.close()
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
        session = callback1.waitForSession()
        assertThat(session).isNotNull()
        session!!.close()

        // Request a session with the same name again.
        val callback2 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params, Executors.newSingleThreadExecutor(), callback2)
        session = callback2.waitForSession()
        assertThat(session).isNotNull()
        session!!.close()
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
    fun testRequestSession_failWithSessionLimitReached() {
        val params1 =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback1 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params1, Executors.newSingleThreadExecutor(), callback1)
        session = callback1.waitForSession()
        assertThat(session).isNotNull()

        // Request a session with the different name. It should only create one
        // session at the same time. So the second request should fail.
        val params2 =
            ComputerControlSession.Params.Builder(context)
                .setName("${testName.methodName}2")
                .setTargetPackageNames(listOf(TEST_APP_PACKAGE_NAME))
                .build()
        val callback2 = ComputerControlSessionCallbackImpl()
        extension!!.requestSession(params2, Executors.newSingleThreadExecutor(), callback2)
        val session2 = callback2.waitForSession()
        assertThat(session2).isNull()
        assertThat(callback2.getErrorCode()).isEqualTo(ERROR_CODE_SESSION_LIMIT_REACHED)
        session!!.close()
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
            session = callback.waitForSession()
            assertThat(session).isNull()
            assertThat(callback.getErrorCode()).isEqualTo(ERROR_CODE_DEVICE_LOCKED)
        } finally {
            SystemUtil.runShellCommand("input keyevent WAKEUP")
            SystemUtil.runShellCommand("wm dismiss-keyguard")
            SystemUtil.runShellCommand("locksettings clear --old 1234")
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
            session = callback.waitForSession()
            assertThat(session).isNull()
            assertThat(callback.getErrorCode()).isEqualTo(ERROR_CODE_PERMISSION_DENIED)
        } finally {
            SystemUtil.runShellCommand("appops set com.android.shell COMPUTER_CONTROL allow")
        }
    }

    companion object {
        private const val TAG = "ComputerControlExtensionsTest"
        private const val TEST_APP_PACKAGE_NAME = "android.computercontrol.testapp"
        private const val DEADLINE_SECONDS = 5L

        // List of ComputerControlSession error codes.
        private const val ERROR_CODE_UNSET = -1
        // The error code is ComputerControlSession.ERROR_SESSION_LIMIT_REACHED.
        private const val ERROR_CODE_SESSION_LIMIT_REACHED = 1
        // The error code is ComputerControlSession.ERROR_DEVICE_LOCKED.
        private const val ERROR_CODE_DEVICE_LOCKED = 2
        // The error code is ComputerControlSession.ERROR_PERMISSION_DENIED.
        private const val ERROR_CODE_PERMISSION_DENIED = 3
    }
}
