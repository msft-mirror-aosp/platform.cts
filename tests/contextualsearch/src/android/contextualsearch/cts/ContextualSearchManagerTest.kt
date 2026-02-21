/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.contextualsearch.cts

import android.app.contextualsearch.CallbackToken
import android.app.contextualsearch.ContextualSearchConfig
import android.app.contextualsearch.ContextualSearchManager
import android.app.contextualsearch.ContextualSearchState
import android.app.contextualsearch.flags.Flags
import android.content.Context
import android.content.Intent
import android.contextualsearch.caller.BackgroundCallerActivity
import android.contextualsearch.caller.ContextualSearchExtras
import android.contextualsearch.caller.ContextualSearchMessage
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.OutcomeReceiver
import android.os.SystemClock
import android.os.UserManager
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.UserTest
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.BroadcastMessenger.Receiver
import com.android.compatibility.common.util.SystemUtil
import com.google.common.collect.Range
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ContextualSearchManagerTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var mManager: ContextualSearchManager

    private var mWatcher: CtsContextualSearchActivity.Watcher? = null

    @Before
    fun setup() {
        val manager = context.getSystemService(ContextualSearchManager::class.java)
        Assume.assumeNotNull(manager)
        mManager = manager

        setTemporaryPackage(TEMPORARY_PACKAGE)
        mWatcher = CtsContextualSearchActivity.Watcher()
        CtsContextualSearchActivity.WATCHER = mWatcher
        OverlayActivity.WATCHER = OverlayActivity.Watcher()
        BackgroundCallerActivity.WATCHER = BackgroundCallerActivity.Watcher()
    }

    @After
    fun teardown() {
        setTemporaryPackage()
        setTokenDuration()
        stopApp(CALLER_PACKAGE)
        mWatcher = null

        CtsContextualSearchActivity.WATCHER?.instance?.finish()
        OverlayActivity.WATCHER?.instance?.finish()
        BackgroundCallerActivity.WATCHER?.instance?.finish()

        CtsContextualSearchActivity.WATCHER = null
        OverlayActivity.WATCHER = null
        BackgroundCallerActivity.WATCHER = null
    }

    @Test
    @ApiTest(
        apis = ["android.app.contextualsearch.ContextualSearchManager#isContextualSearchAvailable"]
    )
    fun testIsContextualSearchAvailable() {
        // The default test package should always be available.
        assertThat(mManager.isContextualSearchAvailable()).isTrue()
    }

    @Test
    @ApiTest(
        apis = ["android.app.contextualsearch.ContextualSearchManager#isContextualSearchAvailable"]
    )
    fun testIsContextualSearchAvailable_contextualSearchActivityNonexistent() {
        setTemporaryPackage("com.nonexistent.package")
        assertThat(mManager.isContextualSearchAvailable()).isFalse()
    }

    @Test
    @ApiTest(
        apis = ["android.app.contextualsearch.ContextualSearchManager#isContextualSearchAvailable"]
    )
    @Ignore("b/432842114: Failing on all branches")
    fun testIsContextualSearchAvailable_contextualSearchActivityDisabled() {
        setTemporaryPackage(TEMPORARY_PACKAGE)
        try {
            setActivityEnabled(TEMPORARY_PACKAGE, "CtsContextualSearchActivity", false)
            assertThat(mManager.isContextualSearchAvailable()).isFalse()
        } catch (e: Exception) {
            throw RuntimeException("Failed to disable activity for test", e)
        } finally {
            // Re-enable the activity for subsequent tests
            setActivityEnabled(TEMPORARY_PACKAGE, "CtsContextualSearchActivity", true)
        }
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
            ]
    )
    fun testContextualSearchInvocation() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocationWithConfig() {
        val bounds = Rect(0, 0, 100, 100)
        val intentExtras = Bundle()
        intentExtras.putString("testKey", "testValue")
        val config =
            ContextualSearchConfig.Builder()
                .setSourceBounds(bounds)
                .setIntentExtras(intentExtras)
                .setLaunchFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .build()
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME, config)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")

        assertThat(mWatcher!!.launchIntent!!.sourceBounds).isEqualTo(bounds)
        assertThat(mWatcher!!.launchIntent!!.extras!!.getString("testKey")).isEqualTo("testValue")
        assertThat(mWatcher!!.launchIntent!!.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocation_setLaunchFlags() {
        val config =
            ContextualSearchConfig.Builder().setLaunchFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).build()
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME, config)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")

        assertThat(mWatcher!!.launchIntent!!.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // System always adds FLAG_ACTIVITY_NEW_TASK
        assertThat(mWatcher!!.launchIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocationForForegroundApp() {
        val bounds = Rect(0, 0, 100, 100)
        val config =
            ContextualSearchConfig.Builder()
                .setSourceBounds(bounds)
                .setLaunchFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .build()

        // Test startContextualSearch() from an out of process foreground activity.
        TestApis.context()
            .instrumentedContext()
            .startActivity(
                Intent().apply {
                    setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.CallerActivity")
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(ContextualSearchExtras.EXTRA_CONTEXTUAL_SEARCH_CONFIG, config)
                    putExtra(ContextualSearchExtras.EXTRA_USE_CONFIG_ONLY_API, true)
                }
            )
        // CallerActivity automatically calls startContextualSearch() upon onCreate(),
        // which is what we are awaiting here.
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")

        assertThat(mWatcher!!.launchIntent!!.sourceBounds).isEqualTo(bounds)
        assertThat(mWatcher!!.launchIntent!!.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .isEqualTo(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertThat(mWatcher!!.launchIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocationForForegroundApp_overlayWindow() {
        val bounds = Rect(0, 0, 100, 100)
        val config =
            ContextualSearchConfig.Builder()
                .setSourceBounds(bounds)
                .setLaunchFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .build()
        val ctx = TestApis.context().instrumentedContext()

        runShellCommand("appops set $CALLER_PACKAGE SYSTEM_ALERT_WINDOW allow")

        // Whitelist the test package to allow starting background services
        whitelistPackage(TEMPORARY_PACKAGE)
        // Also whitelist the caller package to be safe
        whitelistPackage(CALLER_PACKAGE)

        // Small delay to ensure the whitelist is applied
        SystemClock.sleep(500)

        // Test startContextualSearch() from an out of process service with an overlay window.
        try {
            ctx.startService(
                Intent().apply {
                    setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.OverlayService")
                    putExtra(ContextualSearchExtras.EXTRA_CONTEXTUAL_SEARCH_CONFIG, config)
                }
            )
        } finally {
            unwhitelistPackage(TEMPORARY_PACKAGE)
            unwhitelistPackage(CALLER_PACKAGE)
        }

        // Verify that the caller (OverlayService) received RESULT_EXCEPTION.
        // TODO(474429707): This should receive RESULT_OK because the window is in the foreground.
        Receiver<ContextualSearchMessage>(ctx, ContextualSearchMessage.TAG).use {
            val message: ContextualSearchMessage = it.waitForNextMessage(TEST_LIFECYCLE_TIMEOUT_MS)
            assertThat(message.result).isEqualTo(ContextualSearchMessage.RESULT_EXCEPTION)
        }

        // Verify that the contextual search activity was NOT started.
        assertThat(mWatcher?.created?.count).isEqualTo(1)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocationForForegroundApp_invalidDisplayId_throwsException() {
        val config =
            ContextualSearchConfig.Builder()
                // Not setting display ID (defaults to INVALID_DISPLAY)
                .build()
        assertThrows(IllegalArgumentException::class.java) {
            mManager.startContextualSearch(config)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.contextualsearch.ContextualSearchManager#startContextualSearch"])
    fun testContextualSearchInvocationForForegroundApp_nullConfig_throwsException() {
        assertThrows(NullPointerException::class.java) {
            val nullConfig: ContextualSearchConfig? = null
            mManager.startContextualSearch(nullConfig!!)
        }
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchInvocationForForegroundApp_backgroundActivity() {
        val bounds = Rect(0, 0, 100, 100)
        val config =
            ContextualSearchConfig.Builder()
                .setSourceBounds(bounds)
                .setLaunchFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .build()

        val ctx = TestApis.context().instrumentedContext()
        // Test startContextualSearch() from an out of process background activity.
        ctx.startActivity(
            Intent().apply {
                setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.BackgroundCallerActivity")
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ContextualSearchExtras.EXTRA_CONTEXTUAL_SEARCH_CONFIG, config)
                putExtra(ContextualSearchExtras.EXTRA_USE_CONFIG_ONLY_API, true)
            }
        )
        Receiver<ContextualSearchMessage>(ctx, BackgroundCallerActivity.RESUMED_TAG).use {
            val message = it.waitForNextMessage(TEST_LIFECYCLE_TIMEOUT_MS)
            Truth.assertWithMessage("Timeout waiting for BackgroundCallerActivity to be resumed.")
                .that(message)
                .isNotNull()
            assertThat((message as ContextualSearchMessage).result)
                .isEqualTo(ContextualSearchMessage.RESULT_OK)
        }

        // Start a new activity to push the test activity to the background.
        ctx.startActivity(
            Intent(ctx, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")

        // BackgroundCallerActivity attempts to startContextualSearch() in onStop().

        // Verify that the caller (BackgroundCallerActivity) received a security exception.
        Receiver<ContextualSearchMessage>(ctx, ContextualSearchMessage.TAG).use {
            val message: ContextualSearchMessage = it.waitForNextMessage()
            assertThat(message.result).isEqualTo(ContextualSearchMessage.RESULT_EXCEPTION)
        }

        // And verify that the contextual search activity was not started.
        assertThat(mWatcher?.created?.count).isEqualTo(1)
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE,
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_ENTRYPOINT",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_FLAG_SECURE_FOUND",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_SCREENSHOT",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_IS_MANAGED_PROFILE_VISIBLE",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_VISIBLE_PACKAGE_NAMES",
                "android.app.contextualsearch.ContextualSearchManager#EXTRA_TOKEN",
            ]
    )
    fun testContextualSearchExtras() {
        // Launch an activity for the current user.
        TestApis.activities()
            .startActivity(
                Intent(context, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")

        val beforeMs = SystemClock.uptimeMillis()
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the activity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchIntent!!.extras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
            .isEqualTo(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        // Setting the default to true to make sure that default is not being returned
        assertThat(extras.getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, true))
            .isFalse()
        assertThat(
                extras.getParcelable(ContextualSearchManager.EXTRA_SCREENSHOT, Bitmap::class.java)
            )
            .isNotNull()
        // The OverlayActivity should be visible and marked as managed profile or not accordingly.
        val isManagedProfile =
            (context.getSystemService(Context.USER_SERVICE) as UserManager).isManagedProfile
        assertThat(
                extras.getBoolean(
                    ContextualSearchManager.EXTRA_IS_MANAGED_PROFILE_VISIBLE,
                    !isManagedProfile, // ensure the default isn't being used.
                )
            )
            .isEqualTo(isManagedProfile)
        assertThat(
                extras.getParcelableArrayList(
                    ContextualSearchManager.EXTRA_VISIBLE_PACKAGE_NAMES,
                    String::class.java,
                )
            )
            .isNotEmpty()
        assertThat(extras.getLong(EXTRA_INVOCATION_TIME_MS))
            .isIn(Range.closed(beforeMs, SystemClock.uptimeMillis()))
        assertThat(extras.containsKey(ContextualSearchManager.EXTRA_TOKEN)).isTrue()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
            ]
    )
    fun testOwnSecureActivityCaptured() {
        context.startActivity(
            Intent(context, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")
        OverlayActivity.WATCHER!!.instance!!.addSecureFlag()
        waitForIdle()

        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity to be created.")

        assertThat(
                mWatcher!!
                    .launchIntent!!
                    .extras!!
                    .getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, false)
            )
            .isTrue()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
            ]
    )
    fun testOwnSecureOverlayNotCaptured() {
        context.startActivity(
            Intent(context, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")
        OverlayActivity.WATCHER!!.instance!!.addSecureOverlay()
        waitForIdle()

        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity to be created.")

        assertThat(
                mWatcher!!
                    .launchIntent!!
                    .extras!!
                    .getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, true)
            )
            .isFalse()
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_TOKEN_REFRESH)
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.CallbackToken#getContextualSearchState",
                "android.app.contextualsearch.ContextualSearchState#getStructure",
                "android.app.contextualsearch.ContextualSearchState#getContent",
                "android.app.contextualsearch.ContextualSearchState#getExtras",
            ]
    )
    fun testRequestContextualSearchState() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the activity has launched, we can get the token and register our callback.
        val token =
            mWatcher!!
                .launchIntent!!
                .extras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()
        token.getContextualSearchState(context.mainExecutor, callback)
        // Waiting for the service to post data.
        await(callback.resultLatch, "Waiting for the service to post data.")
        // Verifying that the data posted is as expected.
        assertThat(callback.result!!.structure).isNotNull()
        assertThat(callback.result!!.content).isNotNull()
        assertThat(callback.result!!.extras).isNotNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TOKEN_REFRESH)
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.CallbackToken#getContextualSearchState",
                "android.app.contextualsearch.ContextualSearchState#getStructure",
                "android.app.contextualsearch.ContextualSearchState#getContent",
                "android.app.contextualsearch.ContextualSearchState#getExtras",
            ]
    )
    fun testRequestContextualSearchStateWithTokenRefresh() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the activity has launched, we can get the token and register our callback.
        val token =
            mWatcher!!
                .launchIntent!!
                .extras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver(CountDownLatch(2))
        token.getContextualSearchState(context.mainExecutor, callback)
        // Waiting for the service to post data.
        await(callback.resultLatch, "Waiting for the service to post data.")
        // Verifying that the data posted is as expected.
        assertThat(
                callback.results
                    .get(0)
                    .extras
                    .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)
            )
            .isNotNull()
        assertThat(
                callback.results
                    .get(0)
                    .extras
                    .getParcelable(ContextualSearchManager.EXTRA_SCREENSHOT, Bitmap::class.java)
            )
            .isNotNull()
        assertThat(callback.results.get(1).structure).isNotNull()
        assertThat(callback.results.get(1).content).isNotNull()
        assertThat(callback.results.get(1).extras).isNotNull()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.CallbackToken#getContextualSearchState",
                "android.app.contextualsearch.ContextualSearchState#getStructure",
                "android.app.contextualsearch.ContextualSearchState#getContent",
                "android.app.contextualsearch.ContextualSearchState#getExtras",
            ]
    )
    fun testTokenWithinValidDuration() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the activity has launched, we can get the token and register our callback.
        val token =
            mWatcher!!
                .launchIntent!!
                .extras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.resultLatch, "Waiting for the service to post data.")
        // The token should now be expired. Using it again should invoke failure in the callback.
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.errorLatch, "Waiting for the service to throw error.")
        // Make sure no more results were posted.
        assertThat(callback.resultLatch.count).isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.app.contextualsearch.CallbackToken#getContextualSearchState"])
    fun testTokenAfterValidDuration() {
        // The token should expire immediately.
        setTokenDuration(1)
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the activity has launched, we can get the token and register our callback.
        val token =
            mWatcher!!
                .launchIntent!!
                .extras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()

        Thread.sleep(10)
        // The token should now be expired. Using it should invoke failure in the callback.
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.errorLatch, "Waiting for the service to throw error.")
        // Validate that there was an error.
        assertThat(callback.errorLatch.count).isEqualTo(0)
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE,
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
            ]
    )
    fun testContextualSearchFromOutOfProcessForegroundActivity() {
        // Test startContextualSearch() from an out of process foreground activity.
        TestApis.context()
            .instrumentedContext()
            .startActivity(
                Intent().apply {
                    setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.CallerActivity")
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        // CallerActivity automatically calls startContextualSearch() upon onCreate(),
        // which is what we are awaiting here.
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the CallerActivity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchIntent!!.extras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
            .isEqualTo(INTERNAL_ENTRYPOINT_APP)
        val isManagedProfile =
            (context.getSystemService(Context.USER_SERVICE) as UserManager).isManagedProfile
        assertThat(
                extras.getBoolean(
                    ContextualSearchManager.EXTRA_IS_MANAGED_PROFILE_VISIBLE,
                    !isManagedProfile, // ensure the default isn't being used.
                )
            )
            .isEqualTo(isManagedProfile)
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE,
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setSourceBounds",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#setIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig.Builder#build",
            ]
    )
    fun testContextualSearchFromOutOfProcessForegroundActivityWithConfig() {
        val bounds = Rect(0, 0, 100, 100)
        val intentExtras = Bundle()
        intentExtras.putString("testKey", "testValue")
        val config =
            ContextualSearchConfig.Builder()
                .setSourceBounds(bounds)
                .setIntentExtras(intentExtras)
                .build()

        // Test startContextualSearch() from an out of process foreground activity.
        TestApis.context()
            .instrumentedContext()
            .startActivity(
                Intent().apply {
                    setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.CallerActivity")
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(ContextualSearchExtras.EXTRA_CONTEXTUAL_SEARCH_CONFIG, config)
                }
            )
        // CallerActivity automatically calls startContextualSearch() upon onCreate(),
        // which is what we are awaiting here.
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity.onCreate to be called.")
        // Now that the CallerActivity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchIntent!!.extras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
            .isEqualTo(INTERNAL_ENTRYPOINT_APP)
        assertThat(mWatcher!!.launchIntent!!.sourceBounds).isEqualTo(bounds)
        assertThat(mWatcher!!.launchIntent!!.extras!!.getString("testKey")).isEqualTo("testValue")
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE,
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    @ApiTest(
        apis =
            [
                "android.app.contextualsearch.ContextualSearchManager#startContextualSearch",
                "android.app.contextualsearch.ContextualSearchConfig#getDisplayId",
                "android.app.contextualsearch.ContextualSearchConfig#getLaunchFlags",
                "android.app.contextualsearch.ContextualSearchConfig#getIntentExtras",
                "android.app.contextualsearch.ContextualSearchConfig#getSourceBounds",
            ]
    )
    fun testContextualSearchFromOutOfProcessActivity_backgroundActivity() {
        val ctx = TestApis.context().instrumentedContext()
        // Test startContextualSearch() from an out of process background activity.
        ctx.startActivity(
            Intent().apply {
                setClassName(CALLER_PACKAGE, "$CALLER_PACKAGE.BackgroundCallerActivity")
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        Receiver<ContextualSearchMessage>(ctx, BackgroundCallerActivity.RESUMED_TAG).use {
            val message = it.waitForNextMessage(TEST_LIFECYCLE_TIMEOUT_MS)
            Truth.assertWithMessage("Timeout waiting for BackgroundCallerActivity to be resumed.")
                .that(message)
                .isNotNull()
            assertThat((message as ContextualSearchMessage).result)
                .isEqualTo(ContextualSearchMessage.RESULT_OK)
        }

        // Start a new activity to push the test activity to the background.
        ctx.startActivity(
            Intent(ctx, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")

        // BackgroundCallerActivity attempts to startContextualSearch() in onStop().

        // Verify that the caller (BackgroundCallerActivity) received a security exception.
        Receiver<ContextualSearchMessage>(ctx, ContextualSearchMessage.TAG).use {
            val message: ContextualSearchMessage = it.waitForNextMessage()
            assertThat(message.result).isEqualTo(ContextualSearchMessage.RESULT_EXCEPTION)
        }

        // And verify that the contextual search activity was not started.
        assertThat(mWatcher?.created?.count).isEqualTo(1)
    }

    private class TestOutcomeReceiver(
        val resultLatch: CountDownLatch = CountDownLatch(1),
        val errorLatch: CountDownLatch = CountDownLatch(1),
    ) : OutcomeReceiver<ContextualSearchState, Throwable> {
        val results = mutableListOf<ContextualSearchState>()
        val result
            get() = results.getOrElse(0) { null }

        override fun onResult(result: ContextualSearchState?) {
            result?.let { this.results.add(it) }
            resultLatch.countDown()
        }

        override fun onError(error: Throwable) {
            Log.d(TAG, "onError: $error")
            errorLatch.countDown()
        }
    }

    companion object {
        private const val TEST_LIFECYCLE_TIMEOUT_MS: Long = 5000
        private val TAG = ContextualSearchManagerTest::class.java.simpleName
        private const val TEMPORARY_PACKAGE = "android.contextualsearch.cts"
        private const val CALLER_PACKAGE = "android.contextualsearch.caller"

        // Copied from ContextualSearchManagerService.
        private const val INTERNAL_ENTRYPOINT_APP = -1

        // TODO: remove in W
        private const val EXTRA_INVOCATION_TIME_MS =
            "android.app.contextualsearch.extra.INVOCATION_TIME_MS"

        private fun setTemporaryPackage(packageName: String? = null) {
            if (packageName != null) {
                runShellCommand("cmd contextual_search set temporary-package $packageName 60000")
            } else {
                runShellCommand("cmd contextual_search set")
            }
        }

        private fun setTokenDuration(durationMs: Int = 0) {
            if (durationMs > 0) {
                runShellCommand("cmd contextual_search set token-duration $durationMs")
            } else {
                runShellCommand("cmd contextual_search set token-duration")
            }
        }

        private fun whitelistPackage(packageName: String) {
            runShellCommand("cmd deviceidle whitelist +$packageName")
        }

        private fun unwhitelistPackage(packageName: String) {
            runShellCommand("cmd deviceidle whitelist -$packageName")
        }

        private fun stopApp(packageName: String) {
            runShellCommand("am stop-app $packageName")
        }

        private fun setActivityEnabled(
            packageName: String,
            activityName: String,
            enabled: Boolean,
        ) {
            val state = if (enabled) "enable" else "disable"
            runShellCommand("pm $state $packageName/$packageName.$activityName")
        }

        private fun runShellCommand(command: String) {
            Log.d(TAG, "runShellCommand: $command")
            try {
                SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command)
            } catch (e: Exception) {
                throw RuntimeException("Command '$command' failed: ", e)
            }
        }

        private fun waitForIdle() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.syncInputTransactions()
        }

        private fun await(latch: CountDownLatch?, message: String) {
            if (latch == null) {
                throw java.lang.IllegalStateException("Latch null while: $message")
            }
            try {
                Truth.assertWithMessage(message)
                    .that(latch.await(TEST_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Interrupted while: $message")
            }
        }
    }
}
