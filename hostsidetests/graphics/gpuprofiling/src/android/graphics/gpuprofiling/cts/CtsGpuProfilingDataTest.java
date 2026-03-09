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

package android.graphics.gpuprofiling.cts;

import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.APP;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.buildConfig;

import static org.junit.Assume.assumeFalse;

import android.graphics.gpuprofiling.cts.TraceParser.ParsedTrace;

import com.android.tradefed.log.Log;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.CodedInputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec;
import perfetto.protos.PerfettoConfig.TraceConfig;
import perfetto.protos.PerfettoConfig.TracingServiceState;
import perfetto.protos.PerfettoTrace.Trace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests that ensure Perfetto producers exist for GPU profiling when the device claims to support
 * profilng.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsGpuProfilingDataTest extends BaseHostJUnit4Test {
    public static final String TAG = "GpuProfilingDataDeviceActivity";

    // This test ensures that if a device reports ro.hardware.gpu.profiler.support if reports the
    // correct perfetto producers
    //
    // Positive tests
    // - Ensure the perfetto producers for render stages, counters, and ftrace gpu frequency are
    // available
    // - Ensure the validity of GPU counter values

    private static final String GPU_COUNTER_PRODUCER = "gpu_counter_producer";
    private static final String APK = "CtsGraphicsProfilingDataApp.apk";
    private static final String ACTIVITY = APP + "/.GpuProfilingNativeActivity";
    private static final int MAX_WAIT_FOR_ACTIVITY_SECONDS = 10;
    private static final String PROFILING_PROPERTY = "graphics.gpu.profiler.support";
    private static final String GPU_FREQUENCY_CAPABILITY_PROPERTY =
            "graphics.gpu.profiler.support.gpu_frequency";
    private static final String GPU_COUNTERS_CAPABILITY_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters";
    private static final String GPU_COUNTERS_GROUPS_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.groups";
    private static final String GPU_COUNTERS_ZEROES_OPTIMIZATION_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.zeroes_optimization";
    private static final String GPU_COUNTERS_SAMPLING_PERIOD_PROPERTY =
            "graphics.gpu.profiler.support.gpu_counters.period";
    private static final String GPU_RENDER_STAGES_PROPERTY =
            "graphics.gpu.profiler.support.render_stages";
    private static final String GPU_RENDER_STAGES_QUEUE_SUBMIT_PROPERTY =
            "graphics.gpu.profiler.support.render_stages.queue_submit";
    private static final String LAYER_PACKAGE_PROPERTY = "graphics.gpu.profiler.vulkan_layer_apk";
    private static final String LAYER_NAME = "VkRenderStagesProducer";
    private static final String DEBUG_PROPERTY = "debug.graphics.gpu.profiler.perfetto";
    private static final Duration TRACE_COUNTER_PERIOD_1MS = Duration.ofMillis(1);
    private static final Duration TRACE_COUNTER_PERIOD_5MS = Duration.ofMillis(5);
    private static final String TRACE_FILE_PREFIX = "/data/misc/perfetto-traces/";

    // Copied from PackageManager
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_EMBEDDED = "android.hardware.type.embedded";
    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String SUCCESS = "SUCCESS";

    private String initialDebugPropertyValue = null;
    private boolean mHasGpuCountersCapability = false;

    private final List<File> mTraceFiles = new ArrayList<>();

    @Rule public ErrorCollector errorCollector = new ErrorCollector();

    @Rule public TestLogData testLogData = new TestLogData();

    @Rule
    public TestWatcher testWatcher =
            new TestWatcher() {

                @Override
                protected void failed(Throwable e, Description description) {
                    for (File file : mTraceFiles) {
                        CLog.logAndDisplay(
                                Log.LogLevel.INFO, "Trace files kept: " + file.getName());
                        testLogData.addTestLog(
                                file.getName(),
                                LogDataType.PERFETTO,
                                new FileInputStreamSource(file));
                        file.delete();
                    }
                    CLog.logAndDisplay(
                            Log.LogLevel.ERROR,
                            "TEST FAILED; trace files saved: " + mTraceFiles + ".");
                }

                @Override
                protected void succeeded(Description description) {
                    CLog.logAndDisplay(
                            Log.LogLevel.INFO, "TEST SUCCEEDED; cleaning up trace files.");
                    for (File file : mTraceFiles) {
                        file.delete();
                    }
                }
            };

    private class ShellThread extends Thread {

        private final String mCmd;

        public ShellThread(String cmd) throws Exception {
            super("ShellThread");
            mCmd = cmd;
        }

        @Override
        public void run() {
            try {
                getDevice().executeShellV2Command(mCmd);
            } catch (Exception e) {
                CLog.e("Failed to start counters producer" + e.getMessage());
            }
        }
    }

    /** Kill the native process and remove the layer related settings after each test */
    @After
    public void cleanup() throws Exception {
        if (initialDebugPropertyValue != null) {
            getDevice().executeShellV2Command("killall " + GPU_COUNTER_PRODUCER);
            getDevice().executeShellV2Command("am force-stop " + APP);
            getDevice().executeShellV2Command("settings delete global gpu_debug_layers");
            getDevice().executeShellV2Command("settings delete global enable_gpu_debug_layers");
            getDevice().executeShellV2Command("settings delete global gpu_debug_app");
            getDevice().executeShellV2Command("settings delete global gpu_debug_layer_app");
            getDevice().setProperty(DEBUG_PROPERTY, initialDebugPropertyValue);
        }
    }

    /** Clean up before starting any tests. Apply the necessary layer settings if we need them */
    @Before
    public void init() throws Exception {
        // We do not care about non-handheld devices
        bypassTestForFeatures(
                FEATURE_AUTOMOTIVE,
                FEATURE_EMBEDDED,
                FEATURE_LEANBACK_ONLY,
                FEATURE_WATCH,
                FEATURE_TELEVISION);

        initialDebugPropertyValue = getDevice().getProperty(DEBUG_PROPERTY);
        if (initialDebugPropertyValue == null) {
            initialDebugPropertyValue = "";
        }
        cleanup();
        String layerApp = getDevice().getProperty(LAYER_PACKAGE_PROPERTY);
        if (layerApp != null && !layerApp.isEmpty()) {
            getDevice().executeShellV2Command("settings put global enable_gpu_debug_layers 1");
            getDevice().executeShellV2Command("settings put global gpu_debug_app " + APP);
            getDevice()
                    .executeShellV2Command("settings put global gpu_debug_layer_app " + layerApp);
            getDevice().executeShellV2Command("settings put global gpu_debug_layers " + LAYER_NAME);
        }
        installPackage(APK);
        getDevice().setProperty(DEBUG_PROPERTY, "1");
        mHasGpuCountersCapability = getProperty(GPU_COUNTERS_CAPABILITY_PROPERTY);

        // Spin up a new thread to avoid blocking the main thread while the native process waits to
        // be killed.
        ShellThread shellThread = new ShellThread(GPU_COUNTER_PRODUCER);
        shellThread.start();
    }

    /**
     * This is the primary test of the feature. We check that gpu.counters and gpu.renderstages
     * sources are available.
     */
    @Test
    public void testProfilingDataProducersAvailable() throws Exception {
        if (!getProperty(PROFILING_PROPERTY)) {
            return;
        }

        restartTestApp();

        CommandResult queryStatus =
                getDevice().executeShellV2Command("perfetto --query-raw | base64");
        Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.getStatus());
        byte[] decodedBytes = Base64.getMimeDecoder().decode(queryStatus.getStdout());
        TracingServiceState state = TracingServiceState.parseFrom(decodedBytes);

        DataSourceParser.DataSources parsedData = DataSourceParser.parse(state);

        DataSourceValidator validator = new DataSourceValidator(errorCollector);
        if (getProperty(GPU_RENDER_STAGES_PROPERTY)) {
            validator.validateRenderStagesFound(parsedData);
        }

        Set<Integer> allCounterIds = new HashSet<>();
        Set<Integer> defaultCounterIds = new HashSet<>();
        List<GpuCounterSpec> gpuCounterSpecsList = new ArrayList<>();

        if (mHasGpuCountersCapability) {
            validator.validateGpuCountersFound(parsedData);
            validator.validateGpuCounters(parsedData);

            allCounterIds = parsedData.getAllGpuCounterIds();
            defaultCounterIds = parsedData.getDefaultGpuCounterIds();
            gpuCounterSpecsList = parsedData.getGpuCounterSpecsList();
        }

        TraceValidator traceValidator = new TraceValidator(errorCollector);

        ParsedTrace traceFrequencyRenderStagesDefaultCounters5Ms =
                TraceParser.parse(
                        captureTrace(
                                buildConfig(defaultCounterIds, TRACE_COUNTER_PERIOD_5MS, true),
                                "cts-trace-default-frequency-render-stages-5ms"));

        if (getProperty(GPU_RENDER_STAGES_PROPERTY)) {
            traceValidator.validateRenderStages(traceFrequencyRenderStagesDefaultCounters5Ms);

            if (getProperty(GPU_RENDER_STAGES_QUEUE_SUBMIT_PROPERTY)) {
                traceValidator.validateQueueSubmits(traceFrequencyRenderStagesDefaultCounters5Ms);
            }
        }

        if (getProperty(GPU_FREQUENCY_CAPABILITY_PROPERTY) && shouldCheckGpuFrequency()) {
            traceValidator.validateGpuFrequency(traceFrequencyRenderStagesDefaultCounters5Ms);
        }

        if (mHasGpuCountersCapability) {
            traceValidator.validateDefaultCounterValuesPresence(
                    traceFrequencyRenderStagesDefaultCounters5Ms, defaultCounterIds);
            traceValidator.validateGpuUtilisationCounter(
                    traceFrequencyRenderStagesDefaultCounters5Ms);

            if (getProperty(GPU_COUNTERS_ZEROES_OPTIMIZATION_PROPERTY)) {
                traceValidator.validateZeroesOptimization(
                        traceFrequencyRenderStagesDefaultCounters5Ms);
            }

            if (getProperty(GPU_COUNTERS_SAMPLING_PERIOD_PROPERTY)) {
                traceValidator.validateSamplingRate(
                        traceFrequencyRenderStagesDefaultCounters5Ms, TRACE_COUNTER_PERIOD_5MS);

                // The supported sampling rate MUST be 1 ms or faster.
                ParsedTrace traceAllCounters1Ms =
                        TraceParser.parse(
                                captureTrace(
                                        buildConfig(
                                                allCounterIds, TRACE_COUNTER_PERIOD_1MS, false), //
                                        "cts-trace-all-counters-1ms"));
                traceValidator.validateSamplingRate(traceAllCounters1Ms, TRACE_COUNTER_PERIOD_1MS);
            }

            // Additionally try enabling *all* counters, and make sure descriptions are present.
            ParsedTrace traceAllCounters5Ms =
                    TraceParser.parse(
                            captureTrace(
                                    buildConfig(allCounterIds, TRACE_COUNTER_PERIOD_5MS, false), //
                                    "cts-trace-all-counters-5ms"));

            traceValidator.validateAllGpuCountersReported(traceAllCounters5Ms, allCounterIds);

            if (getProperty(GPU_COUNTERS_GROUPS_PROPERTY)) {
                traceValidator.validateRequiredGroupsPresent(
                        traceAllCounters5Ms, gpuCounterSpecsList);
            }
        }
    }

    private Trace captureTrace(TraceConfig traceConfig, String traceFileName) throws Exception {
        restartTestApp();

        File configFile = File.createTempFile("perfetto", ".cfg");
        String traceFilePath = TRACE_FILE_PREFIX + traceFileName;
        try (OutputStream out = new FileOutputStream(configFile)) {
            traceConfig.writeTo(out);
        }
        CommandResult queryStatus =
                getDevice().executeShellV2Command("perfetto -c - -o " + traceFilePath, configFile);
        Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.getStatus());

        File traceResult = getDevice().pullFile(traceFilePath);
        Trace trace;
        try (InputStream in = new FileInputStream(traceResult)) {
            trace = Trace.parseFrom(CodedInputStream.newInstance(in));
        }
        mTraceFiles.add(traceResult);
        configFile.delete();

        CommandResult deleteTraceStatus =
                getDevice().executeShellV2Command("rm -f " + traceFilePath);
        Assert.assertEquals(CommandStatus.SUCCESS, deleteTraceStatus.getStatus());

        return trace;
    }

    private void restartTestApp() throws Exception {
        CommandResult appStopStatus = getDevice().executeShellV2Command("am force-stop " + APP);
        Assert.assertEquals(CommandStatus.SUCCESS, appStopStatus.getStatus());

        CommandResult activityStatus = getDevice().executeShellV2Command("am start -n " + ACTIVITY);
        Assert.assertEquals(CommandStatus.SUCCESS, activityStatus.getStatus());
        // Wait for the native activity to start.
        boolean activityProcessStarted = false;
        for (int i = 0; i < MAX_WAIT_FOR_ACTIVITY_SECONDS; i++) {
            RunUtil.getDefault().sleep(1000);
            activityProcessStarted =
                    getDevice()
                            .executeShellV2Command("dumpsys package " + APP)
                            .getStdout()
                            .contains(ACTIVITY);
            if (activityProcessStarted) break;
        }
        Assert.assertTrue("NativeActivity failed to start", activityProcessStarted);
    }

    private boolean getProperty(String propertyName) throws Exception {
        String property = getDevice().getProperty(propertyName);
        return (property != null && property.equals("true"));
    }

    private boolean shouldCheckGpuFrequency() throws Exception {
        CommandResult gpuFreqsStatus =
                getDevice().executeShellV2Command(
                    "cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies");
        if (gpuFreqsStatus.getStatus() != CommandStatus.SUCCESS) {
            return true;
        }
        String gpuFreqs = gpuFreqsStatus.getStdout();
        if (gpuFreqs != null && !gpuFreqs.trim().isEmpty()) {
            String[] freqs = gpuFreqs.trim().split("\\s+");
            if (freqs.length <= 1) {
                return false;
            }
        }
        return true;
    }

    private void bypassTestForFeatures(String... features) throws Exception {
        for (String feature : features) {
            assumeFalse(hasDeviceFeature(feature));
        }
    }
}
