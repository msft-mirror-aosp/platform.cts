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

import android.computercontrol.testapp.common.Action
import android.content.ComponentName
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerStateHelper
import android.util.Log
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.extensions.computercontrol.ComputerControlSession
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
class ComputerControlSessionManagementTest {
    private class StabilityListenerImpl : ComputerControlSession.StabilityListener {
        private val future = CompletableFuture<Void>()

        fun waitForSessionStable() {
            future.get(5, TimeUnit.SECONDS)
        }

        override fun onSessionStable() {
            future.complete(null)
        }
    }

    private class LifecycleCallbackImpl : ComputerControlSession.LifecycleCallback {
        private var onActiveCalled: Boolean = false
        private var onBlockedCalled: Boolean = false
        private var onClosedCalled: Boolean = false
        private var onActiveComplete: CompletableFuture<Void>? = null
        private var onBlockedComplete: CompletableFuture<Void>? = null
        private var onClosedComplete: CompletableFuture<Void>? = null
        var closeReason: Int = ComputerControlSession.CLOSE_REASON_UNKNOWN
        var blockReason: Int = ComputerControlSession.BLOCK_REASON_UNKNOWN
        var blockingPackage: String? = null

        fun assertInvokeNone(block: () -> Unit) {
            Log.d(TAG, "assertInvokeNone")
            onActiveCalled = false
            onBlockedCalled = false
            onClosedCalled = false
            block()
            assertThat(onActiveCalled).isFalse()
            assertThat(onBlockedCalled).isFalse()
            assertThat(onClosedCalled).isFalse()
        }

        fun assertInvokeOnActive(block: () -> Unit) {
            Log.d(TAG, "assertInvokeOnActive")
            onActiveComplete = CompletableFuture<Void>()
            block()
            onActiveComplete!!.get(5, TimeUnit.SECONDS)
        }

        fun assertInvokeOnBlocked(block: () -> Unit) {
            Log.d(TAG, "assertInvokeOnBlocked")
            onBlockedComplete = CompletableFuture<Void>()
            block()
            onBlockedComplete!!.get(5, TimeUnit.SECONDS)
        }

        fun assertInvokeOnClosed(block: () -> Unit) {
            Log.d(TAG, "assertInvokeOnClosed")
            onClosedComplete = CompletableFuture<Void>()
            block()
            onClosedComplete!!.get(5, TimeUnit.SECONDS)
        }

        override fun onActive() {
            Log.d(TAG, "onActive")
            onActiveCalled = true
            onActiveComplete?.complete(null)
        }

        override fun onBlocked(reason: Int, blockingPackage: String?) {
            Log.d(TAG, "onBlocked, reason: $reason, blockingPackage: $blockingPackage")
            onBlockedCalled = true
            this.blockReason = reason
            this.blockingPackage = blockingPackage
            onBlockedComplete?.complete(null)
        }

        override fun onClosed(reason: Int) {
            Log.d(TAG, "onClosed, reason: $reason")
            onClosedCalled = true
            this.closeReason = reason
            this.blockingPackage = null
            onClosedComplete?.complete(null)
        }
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
    private val windowManagerStateHelper = WindowManagerStateHelper()
    private val launcher = TestAppAgentLauncher()

    private fun launchTestAppAgent(
        packageNames: List<String> = listOf(TEST_APP_PACKAGE_NAME, TEST_APP2_PACKAGE_NAME)
    ): TestAppAgent {
        return TestAppAgent(
            context,
            launcher.requestComputerControlSession(testName.methodName, packageNames)!!,
        )
    }

    private fun withSession(
        methodName: String,
        packageNames: List<String>,
        block: (ComputerControlSession) -> Unit,
    ) {
        val sessionClosedFuture = CompletableFuture<Void>()
        val session =
            launcher.requestComputerControlSession(
                methodName,
                packageNames,
                onClose = { sessionClosedFuture.complete(null) },
            )!!
        session.use { block(it) }
        sessionClosedFuture.get(TestAppAgent.SESSION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    // Returns the display id of the given component name.
    fun getDisplayId(componentName: ComponentName): Int {
        val windowState = windowManagerStateHelper.getWindowState(componentName)
        // Assert that the activity is launched on some display.
        assertThat(windowState).isNotNull()
        return windowState.getDisplayId()
    }

    fun waitAndAssertActivityResumed(componentName: ComponentName) {
        windowManagerStateHelper.waitAndAssertActivityState(
            componentName,
            WindowManagerState.STATE_RESUMED,
        )
    }

    fun assertActivityResumedOnVirtualDisplay(componentName: ComponentName): Int {
        // Wait and assert that the activity is launched.
        waitAndAssertActivityResumed(componentName)
        // The activity is launched on the virtual display, not the default physical display.
        val displayId = getDisplayId(componentName)
        // TODO: expose display id to assert activity is launched on the virtual
        // display.
        assertThat(displayId).isNotEqualTo(Display.DEFAULT_DISPLAY)
        return displayId
    }

    @Test
    fun testLaunchApplication_emptyPackageName() {
        launchTestAppAgent().use { testAppAgent ->
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.launchApplication("")
            }
        }
    }

    @Test
    fun testLaunchApplication_invalidPackageName() {
        launchTestAppAgent().use { testAppAgent ->
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.launchApplication("invalid.package.name")
            }
        }
    }

    @Test
    fun testLaunchApplication_validPackageName() {
        launchTestAppAgent().use { testAppAgent ->
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            // The activity is launched on the virtual display.
            assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)
        }
    }

    @Test
    fun testLaunchApplication_emptyComponentName() {
        launchTestAppAgent().use { testAppAgent ->
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.launchApplication("", "")
            }
        }
    }

    @Test
    fun testLaunchApplication_invalidComponentName() {
        launchTestAppAgent().use { testAppAgent ->
            // Invalid package name.
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.launchApplication("invalid.package.name", "invalid.class.name")
            }
            // Invalid class name.
            assertThrows(IllegalArgumentException::class.java) {
                testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME, "invalid.class.name")
            }
        }
    }

    @Test
    fun testLaunchApplication_validComponentName() {
        launchTestAppAgent().use { testAppAgent ->
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME, TEST_APP_CLASS_NAME)
            // The activity is launched on the virtual display.
            assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)
        }
    }

    @Test
    fun testLaunchApplication_multipleLaunchSameApplication() {
        launchTestAppAgent().use { testAppAgent ->
            // Launch the test app for the first time.
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            var displayId1 = assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)

            // Launch the test app for the second time.
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            var displayId2 = assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)
            // The display id is the same for the same application.
            assertThat(displayId1).isEqualTo(displayId2)
        }
    }

    @Test
    fun testLaunchApplication_multipleLaunchDifferentApplications() {
        launchTestAppAgent().use { testAppAgent ->
            // Launch the test app for the first time.
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)

            // Launch the test app2 for the first time.
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME)
            waitAndAssertActivityResumed(TEST_APP2_COMPONENT_NAME)

            // Launch the test app for the second time.
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME, TEST_APP_CLASS_NAME)
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)

            // Launch the test app2 for the second time.
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME, TEST_APP2_CLASS_NAME)
            waitAndAssertActivityResumed(TEST_APP2_COMPONENT_NAME)
        }
    }

    @Test
    fun testStabilityListener() {
        launchTestAppAgent().use { testAppAgent ->
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)
        }
    }

    @Test
    fun testStabilityListener_invalid() {
        val stabilityListener1 = StabilityListenerImpl()
        val stabilityListener2 = StabilityListenerImpl()
        withSession(testName.methodName, listOf(TEST_APP_PACKAGE_NAME)) { session ->
            session.setStabilityListener(Executors.newSingleThreadExecutor(), stabilityListener1)
            // Assert that stability listener can only be set once.
            assertThrows(IllegalStateException::class.java) {
                session.setStabilityListener(
                    Executors.newSingleThreadExecutor(),
                    stabilityListener2,
                )
            }
        }
    }

    @Test
    fun testLifecycleCallback_invalid() {
        val lifecycleCallback1 = LifecycleCallbackImpl()
        val lifecycleCallback2 = LifecycleCallbackImpl()
        withSession(testName.methodName, listOf(TEST_APP_PACKAGE_NAME)) { session ->
            session.setLifecycleCallback(Executors.newSingleThreadExecutor(), lifecycleCallback1)
            // Assert that lifecycle callback can only be set once.
            assertThrows(IllegalStateException::class.java) {
                session.setLifecycleCallback(
                    Executors.newSingleThreadExecutor(),
                    lifecycleCallback2,
                )
            }

            session.clearLifecycleCallback()
            assertThrows(IllegalStateException::class.java) {
                // Assert that lifecycle callback can only be cleared once.
                session.clearLifecycleCallback()
            }
        }
    }

    @Test
    fun testLifecycleCallback_onActive() {
        val lifecycleCallback = LifecycleCallbackImpl()
        withSession(testName.methodName, listOf(TEST_APP_PACKAGE_NAME)) { session ->
            lifecycleCallback.assertInvokeOnActive {
                session.setLifecycleCallback(Executors.newSingleThreadExecutor(), lifecycleCallback)
            }
        }
    }

    @Test
    fun testLifecycleCallback_onBlocked_secureContent() {
        launchTestAppAgent().use { testAppAgent ->
            // Tap to launch the secure content activity.
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME)
            lifecycleCallback.assertInvokeOnBlocked { testAppAgent.tap(0, 0) }
            assertThat(lifecycleCallback.blockReason)
                .isEqualTo(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT)
            assertThat(lifecycleCallback.blockingPackage).isEqualTo(TEST_APP2_PACKAGE_NAME)

            // Assert that interactions are no-op.
            assertThat(testAppAgent.getAccessibilityWindows()).isEmpty()
        }
    }

    @Test
    fun testLifecycleCallback_onBlocked_disallowedActivityLaunch() {
        launchTestAppAgent(listOf(TEST_APP2_PACKAGE_NAME)).use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME)

            // Long press to launch the test app which is not allowlisted.
            lifecycleCallback.assertInvokeOnBlocked { testAppAgent.longPress(0, 0) }
            assertThat(lifecycleCallback.blockReason)
                .isEqualTo(ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH)
            assertThat(lifecycleCallback.blockingPackage)
                .isEqualTo("android.computercontrol.testapp")

            // Assert that interactions are no-op.
            assertThat(testAppAgent.getAccessibilityWindows()).isEmpty()
        }
    }

    @Test
    fun testLifecycleCallback_onClosed_callerInitiated() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME)
            lifecycleCallback.assertInvokeOnClosed { testAppAgent.close() }
            assertThat(lifecycleCallback.closeReason)
                .isEqualTo(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED)
        }
    }

    @Test
    fun testLifecycleCallback_onClosed_sessionEmpty() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP2_PACKAGE_NAME)

            lifecycleCallback.assertInvokeOnClosed { testAppAgent.handOverApplications() }
            assertThat(lifecycleCallback.closeReason)
                .isEqualTo(ComputerControlSession.CLOSE_REASON_SESSION_EMPTY)
        }
    }

    @Test
    fun testHandOver() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)

            // Launch the test app on the virtual display.
            val virtualDisplayId = assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)

            lifecycleCallback.assertInvokeOnClosed { testAppAgent.handOverApplications() }
            // Assert the application moved from the virtual display to the default physical
            // display.
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)
            val displayId = getDisplayId(TEST_APP_COMPONENT_NAME)
            assertThat(displayId).isNotEqualTo(virtualDisplayId)
            assertThat(displayId).isEqualTo(Display.DEFAULT_DISPLAY)
            // Assert that activity isn't destroyed when handover applications.
            assertThat(testAppAgent.nextAction(Action.Destroy::class.java)).isNull()
        }

        // Request a new session and re-launch the test app. The activity should
        // be launched on the virtual display again.
        launchTestAppAgent().use { testAppAgent ->
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            assertActivityResumedOnVirtualDisplay(TEST_APP_COMPONENT_NAME)
        }
    }

    @Test
    fun testClose_multipleClose() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)
            lifecycleCallback.assertInvokeOnClosed { testAppAgent.close() }
            windowManagerStateHelper.waitAndAssertActivityRemoved(TEST_APP_COMPONENT_NAME)
            Log.d(TAG, "Closed testAppAgent")

            // Call close() again and assert that onClosed() isn't called again.
            lifecycleCallback.assertInvokeNone {
                testAppAgent.close()
                Log.d(TAG, "Closed testAppAgent again")
            }
        }
    }

    @Test
    fun testClose_closeWithoutLaunchApplication() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            lifecycleCallback.assertInvokeOnClosed { testAppAgent.close() }
            windowManagerStateHelper.waitAndAssertActivityRemoved(TEST_APP_COMPONENT_NAME)
        }
    }

    @Test
    fun testClose_interactionAfterClose() {
        launchTestAppAgent().use { testAppAgent ->
            val lifecycleCallback = LifecycleCallbackImpl()
            testAppAgent.lifecycleCallback.set(lifecycleCallback)
            testAppAgent.launchApplication(TEST_APP_PACKAGE_NAME)
            // Assert that launch the test app on the virtual display.
            waitAndAssertActivityResumed(TEST_APP_COMPONENT_NAME)
            lifecycleCallback.assertInvokeOnClosed { testAppAgent.close() }
            // Assert that the activity is removed.
            windowManagerStateHelper.waitAndAssertActivityRemoved(TEST_APP_COMPONENT_NAME)

            // Assert that the session is closed and interactions are no-op.
            testAppAgent.noOpTap()
            // Assert that test app isn't tapped.
            assertThat(testAppAgent.nextAction(Action.Tap::class.java)).isNull()
        }
    }

    companion object {
        private const val TAG = "ComputerControlSessionManagementTest"
        private const val TEST_APP_PACKAGE_NAME = "android.computercontrol.testapp"
        private const val TEST_APP_CLASS_NAME = "android.computercontrol.testapp.app.MainActivity"
        private const val TEST_APP2_PACKAGE_NAME = "android.computercontrol.testapp2"
        private const val TEST_APP2_CLASS_NAME = "android.computercontrol.testapp2.MainActivity"
        private val TEST_APP_COMPONENT_NAME =
            ComponentName(TEST_APP_PACKAGE_NAME, TEST_APP_CLASS_NAME)
        private val TEST_APP2_COMPONENT_NAME =
            ComponentName(TEST_APP2_PACKAGE_NAME, TEST_APP2_CLASS_NAME)
    }
}
