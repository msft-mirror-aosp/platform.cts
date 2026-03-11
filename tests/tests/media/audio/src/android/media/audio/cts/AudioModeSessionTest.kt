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
import android.media.AudioAttributes
import android.media.AudioAttributes.USAGE_ALARM
import android.media.AudioAttributes.USAGE_MEDIA
import android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
import android.media.AudioDeviceAttributes
import android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
import android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import android.media.AudioManager.GET_DEVICES_OUTPUTS
import android.media.AudioManager.MODE_IN_CALL
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioModeSession
import android.media.AudioModeSession.ROUTING_RESULT_SUCCESSFUL
import android.media.audio.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.FrameworkSpecificTest
import com.android.media.mediatestutils.withDefer
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@FrameworkSpecificTest
@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant app permissions")
class AudioModeSessionTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val shellPermRule =
        AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().uiAutomation,
            Manifest.permission.MODIFY_PHONE_STATE,
            Manifest.permission.MODIFY_AUDIO_ROUTING,
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
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    }

    @After
    fun tearDown() = executor.shutdown()

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSessionRequest_builder() {
        val request =
            AudioModeSession.Request.Builder()
                .apply {
                    setInitialMode(MODE_IN_COMMUNICATION)
                    setDisplayActiveUseCase(true)
                    setClientAttribution(context.attributionSource)
                    setNoFocusModes(listOf(MODE_NORMAL))
                }
                .build()

        assertThat(request.initialMode).isEqualTo(MODE_IN_COMMUNICATION)
        assertThat(request.isDisplayActiveUseCase).isTrue()
        assertThat(request.clientAttribution).isEqualTo(context.attributionSource)
        assertThat(request.noFocusModes).containsExactly(MODE_NORMAL)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioRoute_builderAndMethods() = withDefer {
        val routesChannel = Channel<List<AudioModeSession.AudioRoute>>(Channel.UNLIMITED)
        val callback = sessionCallback(onRoutes = { routesChannel.trySend(it) })

        val request = AudioModeSession.Request.Builder().build()
        val session = audioManager.createAudioModeSession(request, executor, callback)
        autoClose(session)
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

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_basicFlow() =  withDefer {
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
                .apply {
                    setInitialMode(MODE_IN_COMMUNICATION)
                    setClientAttribution(context.attributionSource)
                    setNoFocusModes(listOf(MODE_NORMAL))
                }
                .build()

        val session = audioManager.createAudioModeSession(request, executor, callback)
        autoClose(session)
        assertThat(session).isNotNull()

        val routes = waitFor("routes") { routesChannel.receive() }
        assertThat(routes).isNotEmpty()
        assertThat(session.availableRoutes).containsExactlyElementsIn(routes)

        val speakerRoute = routes.find { it.primaryDevice.type == TYPE_BUILTIN_SPEAKER }
        assumeTrue(
            "Speaker and Earpiece route required",
            speakerRoute != null &&
                routes.any { it.primaryDevice.type == TYPE_BUILTIN_EARPIECE },
        )

        val modeListener = ModeListener()
        autoClose(modeListener)
        modeListener.awaitMode(MODE_IN_COMMUNICATION)
        assertThat(audioManager.mode).isEqualTo(MODE_IN_COMMUNICATION)

        // consume the first default routing result
        waitFor("default routing result") { resultChannel.receive() }

        val requestId = session.setRequestedRoute(speakerRoute!!)
        val result = waitFor("routing result") { resultChannel.receive() }

        assertThat(result.requestId).isEqualTo(requestId)
        assertThat(result.status).isEqualTo(ROUTING_RESULT_SUCCESSFUL)
        assertThat(result.route).isEqualTo(speakerRoute)

        assertThat(audioManager.communicationDevice?.type)
            .isEqualTo(speakerRoute.primaryDevice.type)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_updateModeAndUseCase() = withDefer {
        val request = AudioModeSession.Request.Builder().build()

        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)
        assertThat(session).isNotNull()
        val modeListener = ModeListener()
        autoClose(modeListener)
        session.setMode(MODE_IN_CALL)
        modeListener.awaitMode(MODE_IN_CALL)
        assertThat(audioManager.mode).isEqualTo(MODE_IN_CALL)

        session.setDisplayActiveUseCase(true)

        session.setMode(MODE_NORMAL)
        modeListener.awaitMode(MODE_NORMAL)
        assertThat(audioManager.mode).isEqualTo(MODE_NORMAL)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_clientPause() = withDefer {
        val request = AudioModeSession.Request.Builder()
                .setInitialMode(MODE_IN_COMMUNICATION)
                .build()

        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)
        assertThat(session).isNotNull()
        val modeListener = ModeListener()
        autoClose(modeListener)
        modeListener.awaitMode(MODE_IN_COMMUNICATION)
        assertThat(audioManager.mode).isEqualTo(MODE_IN_COMMUNICATION)

        session.setClientPaused(true)
        modeListener.awaitMode(MODE_NORMAL)
        assertThat(audioManager.mode).isEqualTo(MODE_NORMAL)

        session.setClientPaused(false)
        modeListener.awaitMode(MODE_IN_COMMUNICATION)
        assertThat(audioManager.mode).isEqualTo(MODE_IN_COMMUNICATION)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_focusGain_sessionBegin() = withDefer {
        // focus holding mode
        val request = AudioModeSession.Request.Builder()
                .setInitialMode(MODE_IN_COMMUNICATION).build()

        val focusListener = FocusListener()
        autoClose(focusListener)
        val focusRequest = mediaFocusRequest(focusListener)
        assertThat(audioManager.requestAudioFocus(focusRequest))
            .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED)
        defer { audioManager.abandonAudioFocusRequest(focusRequest) }

        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)
        assertThat(session).isNotNull()

        val loss = focusListener.awaitFocusChange()
        assertThat(loss).isEqualTo(AUDIOFOCUS_LOSS_TRANSIENT)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_focusLocked() = withDefer {
        val request = AudioModeSession.Request.Builder()
            .setInitialMode(MODE_IN_COMMUNICATION).build()

        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)

        val focusListener = FocusListener()
        autoClose(focusListener)
        val focusRequest = mediaFocusRequest(focusListener)

        assertThat(audioManager.requestAudioFocus(focusRequest))
            .isEqualTo(AUDIOFOCUS_REQUEST_FAILED)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_focus_sessionEnd() = withDefer {
        val request = AudioModeSession.Request.Builder()
                .setInitialMode(MODE_IN_COMMUNICATION).build()

        val focusListener = FocusListener()
        autoClose(focusListener)
        val focusRequest = mediaFocusRequest(focusListener)

        assertThat(audioManager.requestAudioFocus(focusRequest))
            .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED)
        defer { audioManager.abandonAudioFocusRequest(focusRequest) }


        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)
        assertThat(session).isNotNull()

        val loss = focusListener.awaitFocusChange()
        assertThat(loss).isEqualTo(AUDIOFOCUS_LOSS_TRANSIENT)

        session.close()

        val gain = focusListener.awaitFocusChange()
        assertThat(gain).isEqualTo(AUDIOFOCUS_GAIN)
    }


    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_focusLocking_setMode() = withDefer {
        val request =
            AudioModeSession.Request.Builder()
                .apply { setInitialMode(MODE_IN_COMMUNICATION) }
                .build()

        val focusListener = FocusListener()
        autoClose(focusListener)
        val focusRequest = mediaFocusRequest(focusListener)
        assertThat(audioManager.requestAudioFocus(focusRequest))
            .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED)
        defer { audioManager.abandonAudioFocusRequest(focusRequest) }

        val session = audioManager.createAudioModeSession(request, executor, sessionCallback())
        autoClose(session)

        var change = focusListener.awaitFocusChange()
        assertThat(change).isEqualTo(AUDIOFOCUS_LOSS_TRANSIENT)

        session.setMode(MODE_NORMAL)
        change = focusListener.awaitFocusChange()
        assertThat(change).isEqualTo(AUDIOFOCUS_GAIN)

        session.setMode(MODE_IN_COMMUNICATION)
        change = focusListener.awaitFocusChange()
        assertThat(change).isEqualTo(AUDIOFOCUS_LOSS_TRANSIENT)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_defaultRoutingEarpiece() = withDefer {
        val resultChannel = Channel<RoutingResult>(Channel.UNLIMITED)
        val callback =
            sessionCallback(
                onResult = { id, route, status ->
                    resultChannel.trySend(RoutingResult(id, route, status))
                }
            )

        val request = AudioModeSession.Request.Builder().build()

        val session = audioManager.createAudioModeSession(request, executor, callback)
        autoClose(session)

        val earpieceRoute =
            session.availableRoutes.find { it.primaryDevice.type == TYPE_BUILTIN_EARPIECE }
        assumeTrue("Earpiece route not found", earpieceRoute != null)

        val result = waitFor("routing result") { resultChannel.receive() }
        assertThat(result.route?.primaryDevice?.type).isEqualTo(TYPE_BUILTIN_EARPIECE)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_defaultRoutingSpeaker() = withDefer {
        val resultChannel = Channel<RoutingResult>(Channel.UNLIMITED)
        val callback =
            sessionCallback(
                onResult = { id, route, status ->
                    resultChannel.trySend(RoutingResult(id, route, status))
                }
            )

        val request = AudioModeSession.Request.Builder()
            .setDisplayActiveUseCase(true)
            .build()

        val session = audioManager.createAudioModeSession(request, executor, callback)
        autoClose(session)

        val speakerRoute =
            session.availableRoutes.find { it.primaryDevice.type == TYPE_BUILTIN_SPEAKER }
        assumeTrue("Speaker route not found", speakerRoute != null)

        val result = waitFor("routing result") { resultChannel.receive() }
        assertThat(result.route?.primaryDevice?.type).isEqualTo(TYPE_BUILTIN_SPEAKER)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FUSED_TELECOM_ROUTE_API)
    fun testAudioModeSession_preferredDeviceForStrategy() = withDefer {
        val devices = audioManager.getDevices(GET_DEVICES_OUTPUTS)
        val speakerDevice = devices.find { it.type == TYPE_BUILTIN_SPEAKER }
        assumeTrue(
            "Speaker and earpiece required",
            speakerDevice != null && devices.any { it.type == TYPE_BUILTIN_EARPIECE },
        )

        val voiceAttr =
            AudioAttributes.Builder().apply { setUsage(USAGE_VOICE_COMMUNICATION) }.build()
        val strategies = AudioManager.getAudioProductStrategies()

        val strategyForVoice = strategies.firstOrNull { it.supportsAudioAttributes(voiceAttr) }
        assumeTrue("No strategy found for voice communication", strategyForVoice != null)

        val deviceAttributes = AudioDeviceAttributes(speakerDevice!!)

        audioManager.setPreferredDeviceForStrategy(strategyForVoice!!, deviceAttributes)
        defer { audioManager.removePreferredDeviceForStrategy(strategyForVoice!!) }

        val resultChannel = Channel<RoutingResult>(Channel.UNLIMITED)
        val callback =
            sessionCallback(
                onResult = { id, route, status ->
                    resultChannel.trySend(RoutingResult(id, route, status))
                }
            )

        val request =
            AudioModeSession.Request.Builder()
                .apply {
                    setInitialMode(MODE_IN_COMMUNICATION)
                    setDisplayActiveUseCase(false) // normally defaults to earpiece
                }
                .build()

        val session = audioManager.createAudioModeSession(request, executor, callback)
        autoClose(session)
        assertThat(session).isNotNull()

        val result = waitFor("routing result") { resultChannel.receive() }
        assertThat(result.route?.primaryDevice?.type).isEqualTo(TYPE_BUILTIN_SPEAKER)
    }

    private fun <T> waitFor(
        description: String,
        timeoutMs: Long = WAIT_TIMEOUT_MS,
        block: suspend () -> T,
    ): T = runBlocking {
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            fail("Timeout while $description after ${timeoutMs}ms")
            throw e
        }
    }

    private fun mediaFocusRequest(focusListener: AudioManager.OnAudioFocusChangeListener) =
        AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                .apply {
                    setOnAudioFocusChangeListener(focusListener)
                    setAudioAttributes(
                        AudioAttributes.Builder().apply { setUsage(USAGE_MEDIA) }.build()
                    )
                }
                .build()


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
                } catch (e: TimeoutCancellationException) {
                    fail(
                        "Timed out waiting for mode $expectedMode after ${timeoutMs}ms. Current " +
                            "mode: ${audioManager.mode}"
                    )
                }
            }
        }
    }

    private inner class FocusListener : AudioManager.OnAudioFocusChangeListener, AutoCloseable {
        private val channel = Channel<Int>(Channel.UNLIMITED)

        override fun onAudioFocusChange(focusChange: Int) {
            channel.trySend(focusChange)
        }

        fun awaitFocusChange(timeoutMs: Long = WAIT_TIMEOUT_MS): Int =
            waitFor("focus change", timeoutMs) { channel.receive() }

        override fun close() {
            channel.close()
        }
    }
}
