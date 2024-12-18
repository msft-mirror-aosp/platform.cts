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
import android.app.contextualsearch.ContextualSearchManager
import android.app.contextualsearch.ContextualSearchState
import android.app.contextualsearch.flags.Flags
import android.content.Context
import android.graphics.Bitmap
import android.os.OutcomeReceiver
import android.os.SystemClock
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.google.common.collect.Range
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    }

    @After
    fun teardown() {
        setTemporaryPackage()
        setTokenDuration()
        mWatcher = null
        CtsContextualSearchActivity.WATCHER = null
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE)
    fun testContextualSearchInvocation() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE)
    fun testContextualSearchExtras() {
        val beforeMs = SystemClock.uptimeMillis()
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchExtras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
                .isEqualTo(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        // Setting the default to true to make sure that default is not being returned
        assertThat(extras.getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, true))
                .isFalse()
        assertThat(extras.getParcelable(
            ContextualSearchManager.EXTRA_SCREENSHOT,
            Bitmap::class.java
        )).isNotNull()
        // Setting the default to true to make sure that default is not being returned
        assertThat(extras.getBoolean(
            ContextualSearchManager.EXTRA_IS_MANAGED_PROFILE_VISIBLE,
            true
        )).isFalse()
        assertThat(extras.getParcelableArrayList(
            ContextualSearchManager.EXTRA_VISIBLE_PACKAGE_NAMES,
            String::class.java
        )).isNotEmpty()
        assertThat(extras.getLong(EXTRA_INVOCATION_TIME_MS))
            .isIn(Range.closed(beforeMs, SystemClock.uptimeMillis()))
        assertThat(extras.containsKey(ContextualSearchManager.EXTRA_TOKEN)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE)
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_TOKEN_REFRESH)
    fun testRequestContextualSearchState() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE, Flags.FLAG_ENABLE_TOKEN_REFRESH)
    fun testRequestContextualSearchStateWithTokenRefresh() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver(CountDownLatch(2))
        token.getContextualSearchState(context.mainExecutor, callback)
        // Waiting for the service to post data.
        await(callback.resultLatch, "Waiting for the service to post data.")
        // Verifying that the data posted is as expected.
        assertThat(
            callback.results.get(0).extras.getParcelable(
                ContextualSearchManager.EXTRA_TOKEN,
                CallbackToken::class.java
            )
        ).isNotNull()
        assertThat(
            callback.results.get(0).extras.getParcelable(
                ContextualSearchManager.EXTRA_SCREENSHOT,
                Bitmap::class.java
            )
        ).isNotNull()
        assertThat(callback.results.get(1).structure).isNotNull()
        assertThat(callback.results.get(1).content).isNotNull()
        assertThat(callback.results.get(1).extras).isNotNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE)
    fun testTokenWithinValidDuration() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
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
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SERVICE)
    fun testTokenAfterValidDuration() {
        // The token should expire immediately.
        setTokenDuration(1)
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
                mWatcher?.created,
                "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()

        Thread.sleep(10)
        // The token should now be expired. Using it should invoke failure in the callback.
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.errorLatch, "Waiting for the service to throw error.")
        // Validate that there was an error.
        assertThat(callback.errorLatch.count).isEqualTo(0)
    }

    private class TestOutcomeReceiver(
        val resultLatch: CountDownLatch = CountDownLatch(1),
        val errorLatch: CountDownLatch = CountDownLatch(1),
    ) : OutcomeReceiver<ContextualSearchState, Throwable> {
        val results = mutableListOf<ContextualSearchState>()
        val result get() = results.getOrElse(0) { null }
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

        // TODO: remove in W
        private const val EXTRA_INVOCATION_TIME_MS =
            "android.app.contextualsearch.extra.INVOCATION_TIME_MS"

        private fun setTemporaryPackage(packageName: String? = null) {
            if (packageName != null) {
                runShellCommand(
                    "cmd contextual_search set temporary-package $packageName 60000"
                )
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

        private fun runShellCommand(command: String) {
            Log.d(TAG, "runShellCommand: $command")
            try {
                SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command)
            } catch (e: Exception) {
                throw RuntimeException("Command '$command' failed: ", e)
            }
        }

        private fun await(latch: CountDownLatch?, message: String) {
            if (latch == null) {
                throw java.lang.IllegalStateException("Latch null while: $message")
            }
            try {
                Truth.assertWithMessage(message).that(
                    latch.await(TEST_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ).isTrue()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Interrupted while: $message")
            }
        }
    }
}
