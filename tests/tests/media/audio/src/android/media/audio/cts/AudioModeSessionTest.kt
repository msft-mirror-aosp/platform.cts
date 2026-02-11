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

package android.media.audio.cts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioModeSession
import android.media.audio.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.FrameworkSpecificTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@FrameworkSpecificTest
@RunWith(AndroidJUnit4::class)
class AudioModeSessionTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val shellPermRule =
        AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().uiAutomation,
            Manifest.permission.MODIFY_PHONE_STATE,
        )

    private lateinit var audioManager: AudioManager
    private lateinit var context: Context
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val WAIT_TIMEOUT_MS = 3000L
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().context
        audioManager = context.getSystemService(AudioManager::class.java)!!

        val pm = context.packageManager
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT))
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_TELECOM))
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
    }

    @org.junit.After
    fun tearDown() {
        executor.shutdown()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSessionRequest_builder() {
        val request =
            AudioModeSession.Request.Builder()
                .setInitialMode(AudioManager.MODE_IN_COMMUNICATION)
                .setDisplayActiveUseCase(true)
                .setClientAttribution(context.attributionSource)
                .setNoFocusModes(listOf(AudioManager.MODE_NORMAL))
                .build()

        assertThat(request.initialMode).isEqualTo(AudioManager.MODE_IN_COMMUNICATION)
        assertThat(request.isDisplayActiveUseCase).isTrue()
        assertThat(request.clientAttribution).isEqualTo(context.attributionSource)
        assertThat(request.noFocusModes).containsExactly(AudioManager.MODE_NORMAL)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioRoute_builderAndMethods() {
        val routesChannel = Channel<List<AudioModeSession.AudioRoute>>(Channel.UNLIMITED)
        val callback = sessionCallback(onRoutes = { routesChannel.trySend(it) })

        val request = AudioModeSession.Request.Builder().build()
        audioManager.createAudioModeSession(request, executor, callback).use {
            val routes = waitFor("routes") { routesChannel.receive() }
            assertThat(routes).isNotEmpty()

            val firstRoute = routes[0]
            assertThat(firstRoute.primaryDevice).isNotNull()

            assertThat(firstRoute.toString()).isNotEmpty()
            assertThat(firstRoute.hashCode()).isNotEqualTo(0)
            assertThat(firstRoute).isEqualTo(firstRoute)

            val builder = AudioModeSession.AudioRoute.Builder(firstRoute.primaryDevice)
            firstRoute.inputDevice?.let { builder.setInputDevice(it) }
            assertThat(builder.build()).isEqualTo(firstRoute)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_basicFlow() {
        val routesChannel = Channel<List<AudioModeSession.AudioRoute>>(Channel.UNLIMITED)
        val resultChannel = Channel<RoutingResult>(Channel.UNLIMITED)

        val callback =
            sessionCallback(
                onRoutes = { routesChannel.trySend(it) },
                onResult = { id, route, status ->
                    resultChannel.trySend(RoutingResult(id, route, status))
                },
            )

        val request =
            AudioModeSession.Request.Builder()
                .setInitialMode(AudioManager.MODE_IN_COMMUNICATION)
                .setClientAttribution(context.attributionSource)
                .setNoFocusModes(listOf(AudioManager.MODE_NORMAL))
                .build()

        audioManager.createAudioModeSession(request, executor, callback).use { session ->
            assertThat(session).isNotNull()

            val routes = waitFor("routes") { routesChannel.receive() }
            assertThat(routes).isNotEmpty()
            assertThat(session.availableRoutes).containsExactlyElementsIn(routes)

            val speakerRoute =
                routes.find { it.primaryDevice.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            assumeTrue("Speaker route not found", speakerRoute != null)

            ModeListener().use { modeListener ->
                modeListener.awaitMode(AudioManager.MODE_IN_COMMUNICATION)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_IN_COMMUNICATION)

                val requestId = session.setRequestedRoute(speakerRoute!!)
                val result = waitFor("routing result") { resultChannel.receive() }

                assertThat(result.requestId).isEqualTo(requestId)
                assertThat(result.status).isEqualTo(AudioModeSession.ROUTING_RESULT_SUCCESSFUL)
                assertThat(result.route).isEqualTo(speakerRoute)

                assertThat(audioManager.communicationDevice?.type)
                    .isEqualTo(speakerRoute.primaryDevice.type)
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_updateModeAndUseCase() {
        val request =
            AudioModeSession.Request.Builder().setInitialMode(AudioManager.MODE_NORMAL).build()

        audioManager.createAudioModeSession(request, executor, sessionCallback()).use { session ->
            assertThat(session).isNotNull()
            ModeListener().use { modeListener ->
                session.setMode(AudioManager.MODE_IN_CALL)
                modeListener.awaitMode(AudioManager.MODE_IN_CALL)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_IN_CALL)

                session.setDisplayActiveUseCase(true)

                session.setMode(AudioManager.MODE_NORMAL)
                modeListener.awaitMode(AudioManager.MODE_NORMAL)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_NORMAL)
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_clientPause() {
        val request =
            AudioModeSession.Request.Builder()
                .setInitialMode(AudioManager.MODE_IN_COMMUNICATION)
                .build()

        audioManager.createAudioModeSession(request, executor, sessionCallback()).use { session ->
            assertThat(session).isNotNull()
            ModeListener().use { modeListener ->
                modeListener.awaitMode(AudioManager.MODE_IN_COMMUNICATION)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_IN_COMMUNICATION)

                session.setClientPaused(true)
                modeListener.awaitMode(AudioManager.MODE_NORMAL)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_NORMAL)

                session.setClientPaused(false)
                modeListener.awaitMode(AudioManager.MODE_IN_COMMUNICATION)
                assertThat(audioManager.mode).isEqualTo(AudioManager.MODE_IN_COMMUNICATION)
            }
        }
    }

    private fun <T> waitFor(
        description: String,
        timeoutMs: Long = WAIT_TIMEOUT_MS,
        block: suspend () -> T,
    ): T {
        return runBlocking {
            try {
                withTimeout(timeoutMs) { block() }
            } catch (e: TimeoutCancellationException) {
                fail("Timeout while $description after ${timeoutMs}ms")
                throw e
            }
        }
    }

    private fun sessionCallback(
        onRoutes: (List<AudioModeSession.AudioRoute>) -> Unit = {},
        onResult: (Int, AudioModeSession.AudioRoute?, Int) -> Unit = { _, _, _ -> },
    ) =
        object : AudioModeSession.Callback {
            override fun onAvailableRoutesChanged(
                availableRoutes: List<AudioModeSession.AudioRoute>
            ) {
                onRoutes(availableRoutes)
            }

            override fun onExternalRequestedRouteChanged(
                newRoute: AudioModeSession.AudioRoute?,
                requestId: Int,
            ) {}

            override fun onPaused() {}

            override fun onResumed(requestId: Int) {}

            override fun onClosed() {}

            override fun onRoutingResult(
                requestId: Int,
                route: AudioModeSession.AudioRoute?,
                status: Int,
            ) {
                onResult(requestId, route, status)
            }
        }

    private data class RoutingResult(
        val requestId: Int,
        val route: AudioModeSession.AudioRoute?,
        val status: Int,
    )

    private inner class ModeListener : AudioManager.OnModeChangedListener, AutoCloseable {
        private val channel = Channel<Int>(Channel.UNLIMITED)

        init {
            audioManager.addOnModeChangedListener(executor, this)
            channel.trySend(audioManager.mode)
        }

        override fun onModeChanged(mode: Int) {
            channel.trySend(mode)
        }

        override fun close() {
            audioManager.removeOnModeChangedListener(this)
            channel.close()
        }

        fun awaitMode(expectedMode: Int, timeoutMs: Long = WAIT_TIMEOUT_MS) {
            runBlocking {
                try {
                    withTimeout(timeoutMs) {
                        while (true) {
                            if (channel.receive() == expectedMode) {
                                break
                            }
                        }
                    }
                } catch (e : TimeoutCancellationException) {
                    fail("Timed out waiting for mode $expectedMode after ${timeoutMs}ms. Current " +
                         "mode: ${audioManager.mode}")
                }
            }
        }
    }
}
