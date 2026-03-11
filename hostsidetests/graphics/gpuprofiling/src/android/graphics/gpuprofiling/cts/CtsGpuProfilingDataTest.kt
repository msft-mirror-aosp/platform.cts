/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.graphics.gpuprofiling.cts

import com.android.tradefed.log.Log
import com.android.tradefed.log.LogUtil.CLog
import com.android.tradefed.result.FileInputStreamSource
import com.android.tradefed.result.LogDataType
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.CommandStatus
import com.android.tradefed.util.RunUtil
import com.google.protobuf.CodedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Duration
import java.util.Base64
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoConfig.TraceConfig
import perfetto.protos.PerfettoConfig.TracingServiceState
import perfetto.protos.PerfettoTrace.Trace

/**
 * Tests that ensure Perfetto producers exist for GPU profiling when the device claims to support
 * profilng.
 */
@RunWith(DeviceJUnit4ClassRunner::class)
class CtsGpuProfilingDataTest : BaseHostJUnit4Test() {
    private var initialDebugPropertyValue: String? = null
    private var mHasGpuCountersCapability = false

    private val mTraceFiles = mutableListOf<File>()

    @get:Rule
    val errorCollector = ErrorCollector()

    @get:Rule
    val testLogData = TestLogData()

    @get:Rule
    val testWatcher = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            for (file in mTraceFiles) {
                CLog.logAndDisplay(Log.LogLevel.INFO, "Trace files kept: ${file.name}")
                testLogData.addTestLog(
                    file.name,
                    LogDataType.PERFETTO,
                    FileInputStreamSource(file)
                )
                file.delete()
            }
            CLog.logAndDisplay(Log.LogLevel.ERROR, "TEST FAILED; trace files saved: $mTraceFiles.")
        }

        override fun succeeded(description: Description) {
            CLog.logAndDisplay(Log.LogLevel.INFO, "TEST SUCCEEDED; cleaning up trace files.")
            for (file in mTraceFiles) {
                file.delete()
            }
        }
    }

    private inner class ShellThread(private val mCmd: String) : Thread("ShellThread") {
        override fun run() {
            try {
                device.executeShellV2Command(mCmd)
            } catch (e: Exception) {
                CLog.e("Failed to start counters producer: ${e.message}")
            }
        }
    }

    /**
     * Kill the native process and remove the layer related settings after each test
     */
    @After
    fun cleanup() {
        initialDebugPropertyValue?.let {
            device.executeShellV2Command("killall $GPU_COUNTER_PRODUCER")
            device.executeShellV2Command("am force-stop $APP")
            device.executeShellV2Command("settings delete global gpu_debug_layers")
            device.executeShellV2Command("settings delete global enable_gpu_debug_layers")
            device.executeShellV2Command("settings delete global gpu_debug_app")
            device.executeShellV2Command("settings delete global gpu_debug_layer_app")
            device.setProperty(DEBUG_PROPERTY, it)
        }
    }

    /**
     * Clean up before starting any tests. Apply the necessary layer settings if we
     * need them
     */
    @Before
    fun init() {
        // We do not care about non-handheld devices
        bypassTestForFeatures(
            FEATURE_AUTOMOTIVE,
            FEATURE_EMBEDDED,
            FEATURE_LEANBACK_ONLY,
            FEATURE_WATCH,
            FEATURE_TELEVISION
        )

        initialDebugPropertyValue = device.getProperty(DEBUG_PROPERTY) ?: ""
        cleanup()
        val layerApp = device.getProperty(LAYER_PACKAGE_PROPERTY)
        if (!layerApp.isNullOrEmpty()) {
            device.executeShellV2Command("settings put global enable_gpu_debug_layers 1")
            device.executeShellV2Command("settings put global gpu_debug_app $APP")
            device.executeShellV2Command("settings put global gpu_debug_layer_app $layerApp")
            device.executeShellV2Command("settings put global gpu_debug_layers $LAYER_NAME")
        }
        installPackage(APK)
        device.setProperty(DEBUG_PROPERTY, "1")
        mHasGpuCountersCapability = getProperty(GPU_COUNTERS_CAPABILITY_PROPERTY)

        // Spin up a new thread to avoid blocking the main thread while the native
        // process waits to be killed.
        val shellThread = ShellThread(GPU_COUNTER_PRODUCER)
        shellThread.start()
    }

    /**
     * This is the primary test of the feature. We check that gpu.counters and
     * gpu.renderstages sources are available.
     */
    @Test
    fun testProfilingDataProducersAvailable() {
        if (!getProperty(PROFILING_PROPERTY)) {
            return
        }

        restartTestApp()

        val queryStatus = device.executeShellV2Command("perfetto --query-raw | base64")
        Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.status)
        val decodedBytes = Base64.getMimeDecoder().decode(queryStatus.stdout)
        val state = TracingServiceState.parseFrom(decodedBytes)

        val parsedData = DataSourceParser.parse(state)

        val validator = DataSourceValidator(errorCollector)
        if (getProperty(GPU_RENDER_STAGES_PROPERTY)) {
            validator.validateRenderStagesFound(parsedData)
        }

        var allCounterIds: Set<Int> = emptySet()
        var defaultCounterIds: Set<Int> = emptySet()
        var gpuCounterSpecsList: List<GpuCounterSpec> = emptyList()

        if (mHasGpuCountersCapability) {
            validator.validateGpuCountersFound(parsedData)
            validator.validateGpuCounters(parsedData)

            allCounterIds = parsedData.allGpuCounterIds
            defaultCounterIds = parsedData.defaultGpuCounterIds
            gpuCounterSpecsList = parsedData.gpuCounterSpecsList
        }

        val traceValidator = TraceValidator(errorCollector)

        val traceFrequencyRenderStagesDefaultCounters5Ms = TraceParser.parse(
            captureTrace(
                buildConfig(defaultCounterIds, TRACE_COUNTER_PERIOD_5MS, true),
                "cts-trace-default-frequency-render-stages-5ms"
            )
        )

        if (getProperty(GPU_RENDER_STAGES_PROPERTY)) {
            traceValidator.validateRenderStages(traceFrequencyRenderStagesDefaultCounters5Ms)

            if (getProperty(GPU_RENDER_STAGES_QUEUE_SUBMIT_PROPERTY)) {
                traceValidator.validateQueueSubmits(traceFrequencyRenderStagesDefaultCounters5Ms)
            }
        }

        if (getProperty(GPU_FREQUENCY_CAPABILITY_PROPERTY) && shouldCheckGpuFrequency()) {
            traceValidator.validateGpuFrequency(traceFrequencyRenderStagesDefaultCounters5Ms)
        }

        if (mHasGpuCountersCapability) {
            traceValidator.validateDefaultCounterValuesPresence(
                traceFrequencyRenderStagesDefaultCounters5Ms,
                defaultCounterIds
            )
            traceValidator.validateGpuUtilisationCounter(
                traceFrequencyRenderStagesDefaultCounters5Ms
            )

            if (getProperty(GPU_COUNTERS_ZEROES_OPTIMIZATION_PROPERTY)) {
                traceValidator.validateZeroesOptimization(
                    traceFrequencyRenderStagesDefaultCounters5Ms
                )
            }

            if (getProperty(GPU_COUNTERS_SAMPLING_PERIOD_PROPERTY)) {
                traceValidator.validateSamplingRate(
                    traceFrequencyRenderStagesDefaultCounters5Ms,
                    TRACE_COUNTER_PERIOD_5MS
                )

                // The supported sampling rate MUST be 1 ms or faster.
                val traceAllCounters1Ms = TraceParser.parse(
                    captureTrace(
                        buildConfig(allCounterIds, TRACE_COUNTER_PERIOD_1MS, false),
                        "cts-trace-all-counters-1ms"
                    )
                )
                traceValidator.validateSamplingRate(traceAllCounters1Ms, TRACE_COUNTER_PERIOD_1MS)
            }

            // Additionally try enabling *all* counters, and make sure descriptions are
            // present.
            val traceAllCounters5Ms = TraceParser.parse(
                captureTrace(
                    buildConfig(allCounterIds, TRACE_COUNTER_PERIOD_5MS, false),
                    "cts-trace-all-counters-5ms"
                )
            )

            traceValidator.validateAllGpuCountersReported(traceAllCounters5Ms, allCounterIds)

            if (getProperty(GPU_COUNTERS_GROUPS_PROPERTY)) {
                traceValidator.validateRequiredGroupsPresent(
                    traceAllCounters5Ms,
                    gpuCounterSpecsList
                )
            }
        }
    }

    private fun captureTrace(traceConfig: TraceConfig, traceFileName: String): Trace {
        restartTestApp()

        val configFile = File.createTempFile("perfetto", ".cfg")
        val traceFilePath = "$TRACE_FILE_PREFIX$traceFileName"
        FileOutputStream(configFile).use { traceConfig.writeTo(it) }

        val queryStatus = device.executeShellV2Command(
            "perfetto -c - -o $traceFilePath",
            configFile
        )
        Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.status)

        val traceResult = device.pullFile(traceFilePath)
        val trace = FileInputStream(traceResult).use {
            Trace.parseFrom(CodedInputStream.newInstance(it))
        }

        mTraceFiles.add(traceResult)
        configFile.delete()

        val deleteTraceStatus = device.executeShellV2Command("rm -f $traceFilePath")
        Assert.assertEquals(CommandStatus.SUCCESS, deleteTraceStatus.status)

        return trace
    }

    private fun restartTestApp() {
        val appStopStatus = device.executeShellV2Command("am force-stop $APP")
        Assert.assertEquals(CommandStatus.SUCCESS, appStopStatus.status)

        val activityStatus = device.executeShellV2Command("am start -n $ACTIVITY")
        Assert.assertEquals(CommandStatus.SUCCESS, activityStatus.status)

        // Wait for the native activity to start.
        val activityProcessStarted = (1..MAX_WAIT_FOR_ACTIVITY_SECONDS).any {
            RunUtil.getDefault().sleep(1000)
            device.executeShellV2Command("dumpsys package $APP").stdout?.contains(ACTIVITY) == true
        }
        Assert.assertTrue("NativeActivity failed to start", activityProcessStarted)
    }

    private fun getProperty(propertyName: String): Boolean =
        device.getProperty(propertyName) == "true"

    private fun shouldCheckGpuFrequency(): Boolean {
        val gpuFreqsStatus = device.executeShellV2Command(
            "cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
        )
        if (gpuFreqsStatus.status != CommandStatus.SUCCESS) return true

        val gpuFreqs = gpuFreqsStatus.stdout?.trim()
        return gpuFreqs.isNullOrEmpty() || gpuFreqs.split(Regex("\\s+")).size > 1
    }

    private fun bypassTestForFeatures(vararg features: String) {
        features.forEach { assumeFalse(hasDeviceFeature(it)) }
    }

    companion object {
        const val TAG = "GpuProfilingDataDeviceActivity"

        private const val GPU_COUNTER_PRODUCER = "gpu_counter_producer"
        private const val APK = "CtsGraphicsProfilingDataApp.apk"
        private const val ACTIVITY = "$APP/.GpuProfilingNativeActivity"
        private const val MAX_WAIT_FOR_ACTIVITY_SECONDS = 10
        private const val PROFILING_PROPERTY = "graphics.gpu.profiler.support"
        private const val GPU_FREQUENCY_CAPABILITY_PROPERTY =
            "graphics.gpu.profiler.support.gpu_frequency"
        private const val GPU_COUNTERS_CAPABILITY_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters"
        private const val GPU_COUNTERS_GROUPS_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.groups"
        private const val GPU_COUNTERS_ZEROES_OPTIMIZATION_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.zeroes_optimization"
        private const val GPU_COUNTERS_SAMPLING_PERIOD_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.period"
        private const val GPU_RENDER_STAGES_PROPERTY = "graphics.gpu.profiler.support.render_stages"
        private const val GPU_RENDER_STAGES_QUEUE_SUBMIT_PROPERTY =
            "graphics.gpu.profiler.support.render_stages.queue_submit"
        private const val LAYER_PACKAGE_PROPERTY = "graphics.gpu.profiler.vulkan_layer_apk"
        private const val LAYER_NAME = "VkRenderStagesProducer"
        private const val DEBUG_PROPERTY = "debug.graphics.gpu.profiler.perfetto"
        private val TRACE_COUNTER_PERIOD_1MS: Duration = Duration.ofMillis(1)
        private val TRACE_COUNTER_PERIOD_5MS: Duration = Duration.ofMillis(5)
        private const val TRACE_FILE_PREFIX = "/data/misc/perfetto-traces/"

        // Copied from PackageManager
        private const val FEATURE_AUTOMOTIVE = "android.hardware.type.automotive"
        private const val FEATURE_EMBEDDED = "android.hardware.type.embedded"
        private const val FEATURE_LEANBACK_ONLY = "android.software.leanback_only"
        private const val FEATURE_WATCH = "android.hardware.type.watch"
        private const val FEATURE_TELEVISION = "android.hardware.type.television"
    }
}
