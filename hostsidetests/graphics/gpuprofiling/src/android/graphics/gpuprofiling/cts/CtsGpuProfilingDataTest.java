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
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.COUNTERS_SOURCE_NAME;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.STAGES_SOURCE_NAME;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.buildConfig;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.calculateMostCommonIntervalNs;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.counterMatchesGpuUtilisation;
import static android.graphics.gpuprofiling.cts.ProfilingDataUtilsKt.getGpuUsageTimeline;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkQueueSubmitsMatchAppLogs;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkQueueSubmitsNotEmpty;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkQueueSubmitsStrictlyMonotonicallyIncreasing;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkRenderStagesMatchQueueSubmits;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkRenderStagesNotEmpty;
import static android.graphics.gpuprofiling.cts.RenderStagesChecksKt.checkRenderStagesValidity;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeFalse;

import static java.util.Collections.emptySet;

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

import perfetto.protos.PerfettoConfig.DataSourceDescriptor;
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterGroup;
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec;
import perfetto.protos.PerfettoConfig.TraceConfig;
import perfetto.protos.PerfettoConfig.TracingServiceState;
import perfetto.protos.PerfettoConfig.TracingServiceState.DataSource;
import perfetto.protos.PerfettoTrace.FtraceEvent;
import perfetto.protos.PerfettoTrace.FtraceEventBundle;
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor;
import perfetto.protos.PerfettoTrace.Trace;
import perfetto.protos.PerfettoTrace.TracePacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        boolean countersSourceFound = false;
        boolean stagesSourceFound = false;
        Set<Integer> allCounterIds = emptySet();
        Set<Integer> defaultCounterIds = emptySet();
        List<GpuCounterSpec> gpuCounterSpecsList = null;

        CommandResult queryStatus =
                getDevice().executeShellV2Command("perfetto --query-raw | base64");
        Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.getStatus());
        byte[] decodedBytes = Base64.getMimeDecoder().decode(queryStatus.getStdout());
        TracingServiceState state = TracingServiceState.parseFrom(decodedBytes);
        int count = state.getDataSourcesCount();
        Assert.assertTrue("No sources found", count > 0);
        for (int j = 0; j < count; j++) {
            DataSource source = state.getDataSources(j);
            DataSourceDescriptor descriptor = source.getDsDescriptor();
            if (mHasGpuCountersCapability && descriptor.getName().equals(COUNTERS_SOURCE_NAME)) {
                countersSourceFound = true;
                Assert.assertTrue(
                        "GpuCounterDescriptor field not found in data source descriptor ("
                                + COUNTERS_SOURCE_NAME
                                + ")",
                        descriptor.hasGpuCounterDescriptor());
                gpuCounterSpecsList = descriptor.getGpuCounterDescriptor().getSpecsList();
                for (GpuCounterSpec spec : gpuCounterSpecsList) {
                    errorCollector.checkThat(
                            "GpuCounterDescriptor must have a non-empty name. GPU counter id is"
                                    + " ["
                                    + spec.getCounterId()
                                    + "].",
                            spec.hasName() && !spec.getName().isEmpty(),
                            is(true));
                    errorCollector.checkThat(
                            "GpuCounterDescriptor must have a non-empty description. GPU"
                                    + " counter id is ["
                                    + spec.getCounterId()
                                    + "].",
                            spec.hasDescription() && !spec.getDescription().isEmpty(),
                            is(true));
                }

                List<Integer> counterIdsList =
                        gpuCounterSpecsList.stream().map(GpuCounterSpec::getCounterId).toList();
                allCounterIds = new HashSet<>(counterIdsList);
                errorCollector.checkThat(
                        "Counter IDs in DataSourceDescriptor must be unique.",
                        counterIdsList.size() == allCounterIds.size(),
                        is(true));

                defaultCounterIds =
                        gpuCounterSpecsList.stream()
                                .filter(GpuCounterSpec::getSelectByDefault)
                                .map(GpuCounterSpec::getCounterId)
                                .collect(Collectors.toSet());
                errorCollector.checkThat(
                        "No default counters set.", defaultCounterIds, not(empty()));
            }
            if (descriptor.getName().equals(STAGES_SOURCE_NAME)) {
                stagesSourceFound = true;
            }
            if (countersSourceFound && stagesSourceFound) {
                break;
            }
        }
        Assert.assertTrue("Producer " + STAGES_SOURCE_NAME + " not found", stagesSourceFound);
        if (mHasGpuCountersCapability) {
            Assert.assertTrue(
                    "Producer " + COUNTERS_SOURCE_NAME + " not found", countersSourceFound);
        }

        Trace traceFrequencyRenderStagesDefaultCounters5Ms =
                captureTrace(
                        buildConfig(defaultCounterIds, TRACE_COUNTER_PERIOD_5MS, true),
                        "cts-trace-default-frequency-render-stages-5ms");

        if (getProperty(GPU_RENDER_STAGES_PROPERTY)) {
            RenderStagesData renderStagesData =
                    new RenderStagesData(traceFrequencyRenderStagesDefaultCounters5Ms);
            List<RenderStageEvent> renderStages = renderStagesData.getRenderStages();

            checkRenderStagesNotEmpty(errorCollector, renderStages);
            checkRenderStagesValidity(errorCollector, renderStages);

            if (getProperty(GPU_RENDER_STAGES_QUEUE_SUBMIT_PROPERTY)) {
                List<QueueSubmitEvent> queueSubmits = renderStagesData.getQueueSubmits();

                checkQueueSubmitsNotEmpty(errorCollector, queueSubmits);
                checkQueueSubmitsStrictlyMonotonicallyIncreasing(errorCollector, queueSubmits);

                checkQueueSubmitsMatchAppLogs(
                        errorCollector, queueSubmits, renderStagesData.getAppQueueSubmits());

                checkRenderStagesMatchQueueSubmits(errorCollector, renderStagesData);
            }
        }

        if (getProperty(GPU_FREQUENCY_CAPABILITY_PROPERTY) && shouldCheckGpuFrequency()) {
            errorCollector.checkThat(
                    "Trace does not contain valid GPU frequency.",
                    containsGpuFrequencyEvent(traceFrequencyRenderStagesDefaultCounters5Ms),
                    is(true));
        }

        if (mHasGpuCountersCapability) {
            GpuCounters gpuCountersDefaultIds5Ms =
                    new GpuCounters(traceFrequencyRenderStagesDefaultCounters5Ms);

            errorCollector.checkThat(
                    "Trace failed to report one of the default GPU counter values.",
                    gpuCountersDefaultIds5Ms.getCounterValues().keySet().equals(defaultCounterIds),
                    is(true));

            List<GpuUsageEvent> gpuUsageTimelineFromTrace =
                    getGpuUsageTimeline(traceFrequencyRenderStagesDefaultCounters5Ms);
            boolean foundGpuUtilisationCounter =
                    gpuCountersDefaultIds5Ms.getCounterValues().entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            counterMatchesGpuUtilisation(
                                                    gpuCountersDefaultIds5Ms
                                                            .getCounterSpecs()
                                                            .get(entry.getKey()),
                                                    entry.getValue(),
                                                    gpuUsageTimelineFromTrace));
            errorCollector.checkThat(
                    "Trace does not contain a GPU counter that reflects GPU utilisation.",
                    foundGpuUtilisationCounter,
                    is(true));

            if (getProperty(GPU_COUNTERS_ZEROES_OPTIMIZATION_PROPERTY)) {
                errorCollector.checkThat(
                        "Some of the counters report 0 values unnecessarily: ",
                        getSummaryComplianceWithZeroesOptimization(gpuCountersDefaultIds5Ms),
                        is(""));
            }

            if (getProperty(GPU_COUNTERS_SAMPLING_PERIOD_PROPERTY)) {
                checkSamplingRate(
                        errorCollector,
                        gpuCountersDefaultIds5Ms.getEventTimestampsNs(),
                        TRACE_COUNTER_PERIOD_5MS);

                // The supported sampling rate MUST be 1 ms or faster.
                GpuCounters gpuCountersDefaultIds1Ms =
                        new GpuCounters(
                                captureTrace(
                                        buildConfig(
                                                defaultCounterIds, TRACE_COUNTER_PERIOD_1MS, true),
                                        "cts-trace-default-1ms"));

                checkSamplingRate(
                        errorCollector,
                        gpuCountersDefaultIds1Ms.getEventTimestampsNs(),
                        TRACE_COUNTER_PERIOD_1MS);
            }

            // Additionally try enabling *all* counters, and make sure descriptions are present.
            Trace traceAllCounters5Ms =
                    captureTrace(
                            buildConfig(allCounterIds, TRACE_COUNTER_PERIOD_5MS, false),
                            "cts-trace-all-counters-5ms");
            GpuCounters gpuCountersAllIds5Ms = new GpuCounters(traceAllCounters5Ms);

            errorCollector.checkThat(
                    "Trace does not contain valid and complete GPU counter descriptions: ",
                    getSummaryDescriptionOfIdsInTrace(gpuCountersAllIds5Ms, allCounterIds),
                    is(SUCCESS));

            if (getProperty(GPU_COUNTERS_GROUPS_PROPERTY)) {
                checkRequiredGroupsPresent(
                        errorCollector, gpuCounterSpecsList, traceAllCounters5Ms);
            }
        }
    }

    private static void checkSamplingRate(
            ErrorCollector errorCollector, List<Long> counterEventTimesNs, Duration expected) {
        long expectedNanos = expected.toNanos();

        // The mode of bucketed rates from trace should be within 50% of expected rate.
        long mostCommonRateBucket =
                calculateMostCommonIntervalNs(counterEventTimesNs, expectedNanos / 10);

        errorCollector.checkThat(
                "Most common sampling rate too different from expected: ",
                mostCommonRateBucket,
                both(greaterThan((long) (0.5 * expectedNanos)))
                        .and(lessThan((long) (1.5 * expectedNanos))));
    }

    private static String getSummaryDescriptionOfIdsInTrace(
            GpuCounters gpuCounters, Set<Integer> enabledIds) {
        if (!gpuCounters.getCounterDescriptorError().isEmpty()) {
            return gpuCounters.getCounterDescriptorError();
        }
        if (gpuCounters.getCounterSpecs().isEmpty()) {
            return "no counter descriptor events found";
        }
        for (Map.Entry<Integer, GpuCounterDescriptor.GpuCounterSpec> entry :
                gpuCounters.getCounterSpecs().entrySet()) {
            int id = entry.getKey();
            if (!enabledIds.contains(id)) return "unknown counter ID: " + id;
            if (!entry.getValue().hasName() || entry.getValue().getName().isEmpty()) {
                return "missing or empty name for counter id " + id;
            }
        }
        return gpuCounters.getCounterSpecs().size() == enabledIds.size()
                ? SUCCESS
                : "not all enabled counters were found in the descriptor, diff, expected: "
                        + Arrays.stream(enabledIds.toArray()).sorted().toList().toString()
                        + ", found: "
                        + gpuCounters.getCounterSpecs().keySet().stream().sorted().toList();
    }

    private static String getSummaryComplianceWithZeroesOptimization(GpuCounters gpuCounters) {
        StringBuilder zeroesOptimizationSummary = new StringBuilder();

        for (Map.Entry<Integer, List<GpuCounterValue>> entry :
                gpuCounters.getCounterValues().entrySet()) {
            if (ProfilingDataUtilsKt.containsThreeConsecutiveZeroes(entry.getValue())) {
                zeroesOptimizationSummary
                        .append("counter ")
                        .append(gpuCounters.getCounterSpecs().get(entry.getKey()).getName())
                        .append(" with ID ")
                        .append(entry.getKey())
                        .append(" ; ");
            }
        }
        return zeroesOptimizationSummary.toString();
    }

    private static boolean containsGpuFrequencyEvent(Trace trace) {
        for (TracePacket packet : trace.getPacketList()) {
            if (!packet.hasFtraceEvents()) continue;

            FtraceEventBundle eventBundle = packet.getFtraceEvents();
            for (FtraceEvent event : eventBundle.getEventList()) {
                if (!event.hasGpuFrequency()) continue;

                if (event.getGpuFrequency().hasGpuId()
                        && event.getGpuFrequency().hasState()
                        && event.getGpuFrequency().getState() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkRequiredGroupsPresent(
            ErrorCollector errorCollector, List<GpuCounterSpec> gpuCounterSpecsList, Trace trace) {
        Set<GpuCounterGroup> requiredGroups =
                new HashSet<>(
                        Arrays.asList(
                                GpuCounterGroup.COMPUTE,
                                GpuCounterGroup.FRAGMENTS,
                                GpuCounterGroup.MEMORY,
                                GpuCounterGroup.PRIMITIVES,
                                GpuCounterGroup.VERTICES));
        if (deviceSupportsRayTracing(trace)) {
            requiredGroups.add(GpuCounterGroup.RAY_TRACING);
        }
        Set<GpuCounterGroup> foundGroups = new HashSet<>();
        for (GpuCounterSpec spec : gpuCounterSpecsList) {
            foundGroups.addAll(spec.getGroupsList());
        }
        errorCollector.checkThat(
                "Required counter groups missing. Found: "
                        + foundGroups
                        + " Required: "
                        + requiredGroups,
                foundGroups.containsAll(requiredGroups),
                is(true));
    }

    private static boolean deviceSupportsRayTracing(Trace trace) {
        Boolean rayTracingSupportFromTrace = ProfilingDataUtilsKt.deviceSupportsRayTracing(trace);
        Assert.assertNotNull(
                "No raytracing status in trace! This is a native integration issue; aborting.",
                rayTracingSupportFromTrace);
        return rayTracingSupportFromTrace;
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
