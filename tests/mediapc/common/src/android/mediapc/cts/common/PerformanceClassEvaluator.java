/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.media.MediaFormat;
import android.mediapc.cts.common.CameraRequirement.*;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.cts.verifier.CtsVerifierReportLog;

import com.google.common.base.Preconditions;

import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 *
 * <p> Nested classes are organized alphabetically, add[Requirement] functions are organized by
 * their requirement number in the order they appear in the Android CDD
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final String mTestName;
    private Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        Preconditions.checkNotNull(testName);
        String baseTestName = testName.getMethodName() != null ? testName.getMethodName() : "";
        this.mTestName = baseTestName.replace("{", "(").replace("}", ")");
        this.mRequirements = new HashSet<Requirement>();
    }

    String getTestName() {
        return mTestName;
    }

    public static class AudioTap2ToneLatencyRequirement extends Requirement {
        private static final String TAG = AudioTap2ToneLatencyRequirement.class.getSimpleName();

        private AudioTap2ToneLatencyRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setNativeLatency(double latency) {
            this.setMeasuredValue(RequirementConstants.API_NATIVE_LATENCY, latency);
        }

        public void setJavaLatency(double latency) {
            this.setMeasuredValue(RequirementConstants.API_JAVA_LATENCY, latency);
        }

        public static AudioTap2ToneLatencyRequirement createR5_6__H_1_1() {
            RequiredMeasurement<Double> apiNativeLatency = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.API_NATIVE_LATENCY)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 80.0)
                    .build();
            RequiredMeasurement<Double> apiJavaLatency = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.API_JAVA_LATENCY)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 100.0)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 80.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 80.0)
                    .build();

            return new AudioTap2ToneLatencyRequirement(
                    RequirementConstants.R5_6__H_1_1,
                    apiNativeLatency,
                    apiJavaLatency);
        }
    }

    public static class CodecInitLatencyRequirement extends Requirement {

        private static final String TAG = CodecInitLatencyRequirement.class.getSimpleName();

        private CodecInitLatencyRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setCodecInitLatencyMs(long codecInitLatencyMs) {
            this.setMeasuredValue(RequirementConstants.CODEC_INIT_LATENCY, codecInitLatencyMs);
        }

        /**
         * [2.2.7.1/5.1/H-1-7] MUST have a codec initialization latency of 65(R) / 50(S) / 40(T)
         * ms or less for a 1080p or smaller video encoding session for all hardware video
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization. For Dolby vision codec, the codec initialization
         * latency MUST be 50 ms or less.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_7(String mediaType) {
            long latency = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) ? 50L : 40L;
            RequiredMeasurement<Long> codec_init_latency =
                    RequiredMeasurement.<Long>builder().setId(
                                    RequirementConstants.CODEC_INIT_LATENCY)
                            .setPredicate(RequirementConstants.LONG_LTE)
                            .addRequiredValue(Build.VERSION_CODES.R, 65L)
                            .addRequiredValue(Build.VERSION_CODES.S, 50L)
                            .addRequiredValue(Build.VERSION_CODES.TIRAMISU, latency)
                            .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, latency)
                            .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, latency)
                            .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_7,
                    codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-8] MUST have a codec initialization latency of 50(R) / 40(S) / 30(T)
         * ms or less for a 128 kbps or lower bitrate audio encoding session for all audio
         * encoders when under load. Load here is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs together with the 1080p
         * audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_8() {
            RequiredMeasurement<Long> codec_init_latency =
                    RequiredMeasurement.<Long>builder().setId(
                                    RequirementConstants.CODEC_INIT_LATENCY)
                            .setPredicate(RequirementConstants.LONG_LTE)
                            .addRequiredValue(Build.VERSION_CODES.R, 50L)
                            .addRequiredValue(Build.VERSION_CODES.S, 40L)
                            .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 30L)
                            .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 30L)
                            .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 30L)
                            .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_8,
                    codec_init_latency);
        }

        /**
         * [2.2.7.1/5.1/H-1-12] Codec initialization latency of 40ms or less for a 1080p or
         * smaller video decoding session for all hardware video encoders when under load. Load
         * here is defined as a concurrent 1080p to 720p video-only transcoding session using
         * hardware video codecs together with the 1080p audio-video recording initialization.
         */
        public static CodecInitLatencyRequirement createR5_1__H_1_12() {
            RequiredMeasurement<Long> codec_init_latency =
                    RequiredMeasurement.<Long>builder().setId(
                                    RequirementConstants.CODEC_INIT_LATENCY)
                            .setPredicate(RequirementConstants.LONG_LTE)
                            .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 40L)
                            .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 40L)
                            .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 40L)
                            .build();

            return new CodecInitLatencyRequirement(RequirementConstants.R5_1__H_1_12,
                    codec_init_latency);
        }
    }

    // used for requirements [2.2.7.1/5.1/H-1-1], [2.2.7.1/5.1/H-1-2], [2.2.7.1/5.1/H-1-3],
    // [2.2.7.1/5.1/H-1-4], [2.2.7.1/5.1/H-1-5], [2.2.7.1/5.1/H-1-6], [2.2.7.1/5.1/H-1-9],
    // [2.2.7.1/5.1/H-1-10]
    public static class ConcurrentCodecRequirement extends Requirement {
        private static final String TAG = ConcurrentCodecRequirement.class.getSimpleName();
        // allowed tolerance in measured fps vs expected fps in percentage, i.e. codecs achieving
        // fps that is greater than (FPS_TOLERANCE_FACTOR * expectedFps) will be considered as
        // passing the test
        private static final double FPS_TOLERANCE_FACTOR = 0.95;
        private static final double FPS_30_TOLERANCE = 30.0 * FPS_TOLERANCE_FACTOR;
        static final int REQUIRED_MIN_CONCURRENT_INSTANCES = 6;
        static final int REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9 = 2;

        private ConcurrentCodecRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setConcurrentInstances(int concurrentInstances) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_SESSIONS,
                    concurrentInstances);
        }

        public void setConcurrentFps(double achievedFps) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_FPS, achievedFps);
        }

        public void setFrameDropsPerSecond(double fdps) {
            this.setMeasuredValue(RequirementConstants.FRAMES_DROPPED_PER_SECOND, fdps);
        }

        // copied from android.mediapc.cts.getReqMinConcurrentInstances due to build issues on aosp
        public static int getReqMinConcurrentInstances(int performanceClass, String mimeType1,
                String mimeType2, int resolution) {
            ArrayList<String> MEDIAPC_CONCURRENT_CODECS_R = new ArrayList<>(
                    Arrays.asList(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC));
            ArrayList<String> MEDIAPC_CONCURRENT_CODECS = new ArrayList<>(Arrays
                    .asList(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                            MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AV1));

            if (performanceClass >= Build.VERSION_CODES.TIRAMISU) {
                return resolution >= 1080 ? REQUIRED_MIN_CONCURRENT_INSTANCES : 0;
            } else if (performanceClass == Build.VERSION_CODES.S) {
                if (resolution >= 1080) {
                    return 0;
                }
                if (MEDIAPC_CONCURRENT_CODECS.contains(mimeType1) && MEDIAPC_CONCURRENT_CODECS
                        .contains(mimeType2)) {
                    if (MediaFormat.MIMETYPE_VIDEO_VP9.equalsIgnoreCase(mimeType1)
                            || MediaFormat.MIMETYPE_VIDEO_VP9.equalsIgnoreCase(mimeType2)) {
                        return REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9;
                    } else {
                        return REQUIRED_MIN_CONCURRENT_INSTANCES;
                    }
                } else {
                    return 0;
                }
            } else if (performanceClass == Build.VERSION_CODES.R) {
                if (resolution >= 1080) {
                    return 0;
                }
                if (MEDIAPC_CONCURRENT_CODECS_R.contains(mimeType1) && MEDIAPC_CONCURRENT_CODECS_R
                        .contains(mimeType2)) {
                    return REQUIRED_MIN_CONCURRENT_INSTANCES;
                } else {
                    return 0;
                }
            } else {
                return 0;
            }
        }

        private static double getReqMinConcurrentFps(int performanceClass, String mimeType1,
                String mimeType2, int resolution) {
            return FPS_30_TOLERANCE * getReqMinConcurrentInstances(performanceClass, mimeType1,
                    mimeType2, resolution);
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 720p
         */
        private static ConcurrentCodecRequirement create720p(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 720)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, 720);
            return req;
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 1080p
         */
        private static ConcurrentCodecRequirement create1080p(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, 1080);
            return req;
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 4k
         */
        private static ConcurrentCodecRequirement create4k(String requirementId,
                RequiredMeasurement<?> measure) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 2160)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(requirementId, measure,
                    testResolution);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, 2160);
            return req;
        }

        /**
         * Helper method used to create ConcurrentCodecRequirements, builds and fills out the
         * a requirement for tests ran with a resolution of 4k
         */
        private static ConcurrentCodecRequirement create4k(String requirementId,
                RequiredMeasurement<?> measure1, RequiredMeasurement<?> measure2) {
            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 2160)
                    .build();

            ConcurrentCodecRequirement req = new ConcurrentCodecRequirement(
                    requirementId, testResolution, measure1, measure2);
            req.setMeasuredValue(RequirementConstants.TEST_RESOLUTION,  2160);
            return req;
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-1] MUST advertise the maximum number of hardware video decoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_1_4k() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_1, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 720p(R,S)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                            getReqMinConcurrentFps(
                                    Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                            getReqMinConcurrentFps(
                                    Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_2, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 1080p(T)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_2, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-2] MUST support 6 instances of hardware video decoder sessions (AVC,
         * HEVC, VP9, AV1 or later) in any codec combination running concurrently with 3 sessions
         * at 1080p resolution@30 fps and 3 sessions at 4k(U) resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_2_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6 * FPS_30_TOLERANCE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 6 * FPS_30_TOLERANCE)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_2, reqConcurrentFps, frameDropsPerSec);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-3] MUST advertise the maximum number of hardware video encoder
         * sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_3_4K() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_3, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 720p(R,S)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_720p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.R, 0.0)
                    .addRequiredValue(Build.VERSION_CODES.S, 0.0)
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_4, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 1080p(T)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 0.0)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_4, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-4] MUST support 6 instances of hardware video encoder sessions (AVC,
         * HEVC, VP9* or later) in any codec combination running concurrently at 4k(U)
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_4_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Requirement not asserted since encoder test runs in byte buffer mode
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 0.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 0.0)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    // MUST NOT drop more than 1 frame per second
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_4, reqConcurrentFps, frameDropsPerSec);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.R, mimeType1, mimeType2, resolution))
                    .addRequiredValue(Build.VERSION_CODES.S,
                            getReqMinConcurrentInstances(
                                    Build.VERSION_CODES.S, mimeType1, mimeType2, resolution))
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_1080p() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-5] MUST advertise the maximum number of hardware video encoder and
         * decoder sessions that can be run concurrently in any codec combination via the
         * CodecCapabilities.getMaxSupportedInstances() and VideoCapabilities
         * .getSupportedPerformancePoints() methods.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_5_4k() {
            RequiredMeasurement<Integer> maxInstances = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.CONCURRENT_SESSIONS)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 6)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 6)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_5, maxInstances);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_720p(String mimeType1,
                String mimeType2, int resolution) {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.R,
                            getReqMinConcurrentFps(
                                    Build.VERSION_CODES.R, mimeType1, mimeType2, resolution)
                                    / 2)
                    .addRequiredValue(Build.VERSION_CODES.S,
                            getReqMinConcurrentFps(
                                    Build.VERSION_CODES.S, mimeType1, mimeType2, resolution)
                                    / 2)
                    .build();

            return create720p(RequirementConstants.R5_1__H_1_6, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 6 * FPS_30_TOLERANCE / 2)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_6, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-6] Support 6 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 720p(R,S) /1080p(T) /4k(U) @30fps resolution.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_6_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            6 * FPS_30_TOLERANCE / 2)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                            6 * FPS_30_TOLERANCE / 2)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_6, reqConcurrentFps, frameDropsPerSec);
        }

        /**
         * [2.2.7.1/5.1/H-1-9] Support 2 instances of secure hardware video decoder sessions
         * (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently at 1080p
         * resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_9_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 2 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_9, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-9] Support 2 instances of secure hardware video decoder sessions
         * (AVC, HEVC, VP9, AV1 or later) in any codec combination running concurrently at 4k
         * resolution@30 fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_9_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2 * FPS_30_TOLERANCE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 2 * FPS_30_TOLERANCE)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_9, reqConcurrentFps, frameDropsPerSec);
        }

        /**
         * [2.2.7.1/5.1/H-1-10] Support 3 instances of non-secure hardware video decoder sessions
         * together with 1 instance of secure hardware video decoder session (4 instances total)
         * (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently at 1080p
         * resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_10_1080p() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 4 * FPS_30_TOLERANCE)
                    .build();

            return create1080p(RequirementConstants.R5_1__H_1_10, reqConcurrentFps);
        }

        /**
         * [2.2.7.1/5.1/H-1-10] Support 3 instances of non-secure hardware video decoder sessions
         * together with 1 instance of secure hardware video decoder session (4 instances total)
         * (AVC, HEVC, VP9, AV1 or later) in any codec combination running concurrently with 3
         * sessions at 4K resolution@30 fps which includes one secure decoder session and 1
         * session at 1080p resolution@30fps.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_10_4k() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 4 * FPS_30_TOLERANCE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 4 * FPS_30_TOLERANCE)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_10, reqConcurrentFps, frameDropsPerSec);
        }

        /**
         * [2.2.7.1/5.1/H-1-19] Support 3 instances of hardware video decoder and hardware video
         * encoder sessions (AVC, HEVC, VP9 or AV1) in any codec combination running concurrently
         * at 4k(U) @30fps resolution for 10-bit with at most one encoder session.
         */
        public static ConcurrentCodecRequirement createR5_1__H_1_19() {
            RequiredMeasurement<Double> reqConcurrentFps = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.CONCURRENT_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    // Test transcoding, fps calculated for encoder and decoder combined so req / 2
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            3 * FPS_30_TOLERANCE / 2)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                            3 * FPS_30_TOLERANCE / 2)
                    .build();
            // Additionally for Performance Class V: For all sessions, there MUST NOT be more than
            // 1 frame dropped per second.
            RequiredMeasurement<Double> frameDropsPerSec = RequiredMeasurement.<Double>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED_PER_SECOND)
                    .setPredicate(RequirementConstants.DOUBLE_LTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Double.MAX_VALUE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1.0)
                    .build();

            return create4k(RequirementConstants.R5_1__H_1_19, reqConcurrentFps, frameDropsPerSec);
        }
    }

    // used for requirements [7.1.1.3/H-1-1], [7.1.1.3/H-2-1]
    public static class DensityRequirement extends Requirement {
        private static final String TAG = DensityRequirement.class.getSimpleName();

        private DensityRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setDisplayDensity(int displayDensity) {
            this.<Integer>setMeasuredValue(RequirementConstants.DISPLAY_DENSITY, displayDensity);
        }

        /**
         * [7.1.1.3/H-1-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_1_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.DISPLAY_DENSITY)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 400)
                    .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_1_1, display_density);
        }

        /**
         * [7.1.1.3/H-2-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_2_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.DISPLAY_DENSITY)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 400)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 400)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 400)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 400)
                    .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_2_1, display_density);
        }
    }

    public static class ExtYuvTargetRequirement extends Requirement {
        private static final String TAG = ExtYuvTargetRequirement.class.getSimpleName();

        private ExtYuvTargetRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setExtYuvTargetSupport(boolean extensionSupported) {
            this.setMeasuredValue(RequirementConstants.EXT_YUV_EXTENSION, extensionSupported);
        }

        /**
         * [5.12/H-1-3] MUST checks for EXT_YUV_target extension support.
         */
        public static ExtYuvTargetRequirement createExtensionReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.EXT_YUV_EXTENSION)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new ExtYuvTargetRequirement(RequirementConstants.R5_12__H_1_3, requirement);
        }
    }

    // used for requirements [2.2.7.1/5.3/H-1-1], [2.2.7.1/5.3/H-1-2]
    public static class FrameDropRequirement extends Requirement {
        private static final String TAG = FrameDropRequirement.class.getSimpleName();

        private FrameDropRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setFramesDropped(int framesDropped) {
            this.setMeasuredValue(RequirementConstants.FRAMES_DROPPED, framesDropped);
        }

        public void setFrameRate(double frameRate) {
            this.setMeasuredValue(RequirementConstants.FRAME_RATE, frameRate);
        }

        public void setTestResolution(int res) {
            this.setMeasuredValue(RequirementConstants.TEST_RESOLUTION, res);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 1 frames in 10 seconds (i.e less than 0.333
         * percent frame drop) for a 1080p 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128 kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.R, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 1 frame in 10 seconds during a video
         * resolution change in a 30 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_R() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.R, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 30.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds for a
         * 1080p 60 fps video session under load. Load is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs, as well as a 128 kbps AAC
         * audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.S, 6)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-1] MUST NOT drop more than 1 frame in 10 seconds for a
         * 4k 60 fps video session under load. Load is defined as a concurrent 1080p to 720p
         * video-only transcoding session using hardware video codecs, as well as a 128 kbps AAC
         * audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_1_U() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 3)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 60.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 2160)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_1, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 2(S) / 1(T) frames in 10 seconds during a
         * video resolution change in a 60 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_ST() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 2 frame in 10 seconds so 6 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.S, 6)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }

        /**
         * [2.2.7.1/5.3/H-1-2] MUST NOT drop more than 1 frames in 10 seconds during a
         * video resolution change in a 4k 60 fps video session under load. Load is defined as a
         * concurrent 1080p to 720p video-only transcoding session using hardware video codecs,
         * as well as a 128Kbps AAC audio playback.
         */
        public static FrameDropRequirement createR5_3__H_1_2_U() {
            RequiredMeasurement<Integer> frameDropped = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRAMES_DROPPED)
                    .setPredicate(RequirementConstants.INTEGER_LTE)
                    // MUST NOT drop more than 1 frame in 10 seconds so 3 frames for 30 seconds
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 3)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 3)
                    .build();

            RequiredMeasurement<Double> frameRate = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.FRAME_RATE)
                    .setPredicate(RequirementConstants.DOUBLE_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 60.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 60.0)
                    .build();

            RequiredMeasurement<Integer> testResolution = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.TEST_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 2160)
                    .build();

            return new FrameDropRequirement(
                    RequirementConstants.R5_3__H_1_2, frameDropped, frameRate, testResolution);
        }
    }

    // used for requirements [7.1.1.1/H-1-1], [7.1.1.1/H-2-1]
    public static class ResolutionRequirement extends Requirement {
        private static final String TAG = ResolutionRequirement.class.getSimpleName();

        private ResolutionRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setLongResolution(int longResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.LONG_RESOLUTION, longResolution);
        }

        public void setShortResolution(int shortResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.SHORT_RESOLUTION, shortResolution);
        }

        /**
         * [7.1.1.1/H-1-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_1_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.LONG_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1920)
                    .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.SHORT_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1080)
                    .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_1_1, long_resolution,
                    short_resolution);
        }

        /**
         * [7.1.1.1/H-2-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_2_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.LONG_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 1920)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1920)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1920)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1920)
                    .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.SHORT_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.S, 1080)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1080)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1080)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1080)
                    .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_2_1, long_resolution,
                    short_resolution);
        }
    }

    // used for requirements [2.2.7.1/5.1/H-1-11], [2.2.7.1/5.7/H-1-2]
    public static class SecureCodecRequirement extends Requirement {
        private static final String TAG = SecureCodecRequirement.class.getSimpleName();

        private SecureCodecRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setSecureReqSatisfied(boolean secureReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.SECURE_REQ_SATISFIED, secureReqSatisfied);
        }

        public void setNumCryptoHwSecureAllDec(int numCryptoHwSecureAllDec) {
            this.setMeasuredValue(RequirementConstants.NUM_CRYPTO_HW_SECURE_ALL_SUPPORT,
                    numCryptoHwSecureAllDec);
        }

        /**
         * [2.2.7.1/5.7/H-1-2] MUST support MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL with the below
         * content decryption capabilities.
         */
        public static SecureCodecRequirement createR5_7__H_1_2() {
            RequiredMeasurement<Integer> hw_secure_all = RequiredMeasurement.<Integer>builder()
                    .setId(RequirementConstants.NUM_CRYPTO_HW_SECURE_ALL_SUPPORT)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1)
                    .build();

            return new SecureCodecRequirement(RequirementConstants.R5_7__H_1_2, hw_secure_all);
        }

        /**
         * [2.2.7.1/5.1/H-1-11] Must support secure decoder when a corresponding AVC/VP9/HEVC or AV1
         * hardware decoder is available
         */
        public static SecureCodecRequirement createR5_1__H_1_11() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.SECURE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new SecureCodecRequirement(RequirementConstants.R5_1__H_1_11, requirement);
        }
    }

    public static class VideoCodecRequirement extends Requirement {
        private static final String TAG = VideoCodecRequirement.class.getSimpleName();

        private VideoCodecRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setAv1DecoderReq(boolean av1DecoderReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.AV1_DEC_REQ, av1DecoderReqSatisfied);
        }

        public void set4kHwDecoders(int num4kHwDecoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_DEC, num4kHwDecoders);
        }

        public void set4kHwEncoders(int num4kHwEncoders) {
            this.setMeasuredValue(RequirementConstants.NUM_4k_HW_ENC, num4kHwEncoders);
        }

        public void setAVIFDecoderReq(boolean avifDecoderReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.AVIF_DEC_REQ, avifDecoderReqSatisfied);
        }

        public void setAv1EncResolution(int resolution) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_RESOLUTION, resolution);
        }

        public void setAv1EncFps(double fps) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_FPS, fps);
        }

        public void setAv1EncBitrate(int bitrate) {
            this.setMeasuredValue(RequirementConstants.AV1_ENC_BITRATE, bitrate);
        }

        public void setHlgEditingSupportedReq(boolean HlgEditingSupported) {
            this.setMeasuredValue(RequirementConstants.HLG_EDITING, HlgEditingSupported);
        }

        public void setPortraitResolutionSupportreq(boolean isPortraitSupported) {
            this.setMeasuredValue(RequirementConstants.PORTRAIT_RESOLUTION, isPortraitSupported);
        }

        public void setColorFormatSupportReq(boolean colorFormatSupported) {
            this.setMeasuredValue(RequirementConstants.RGBA_1010102_COLOR_FORMAT_REQ,
                    colorFormatSupported);
        }

        public void setDynamicColorAspectsSupportReq(boolean dynamicColorAspectsSupported) {
            this.setMeasuredValue(RequirementConstants.DYNAMIC_COLOR_ASPECTS,
                    dynamicColorAspectsSupported);
        }

        /**
         * [2.2.7.1/5.1/H-1-15] Must have at least 1 HW video decoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwDecoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.NUM_4k_HW_DEC)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_15, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-16] Must have at least 1 HW video encoder supporting 4K60
         */
        public static VideoCodecRequirement createR4k60HwEncoder() {
            RequiredMeasurement<Integer> requirement = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.NUM_4k_HW_ENC)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_16, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-14] AV1 Hardware decoder: Main 10, Level 4.1, Film Grain
         */
        public static VideoCodecRequirement createRAV1DecoderReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.AV1_DEC_REQ)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_14, requirement);
        }

        /**
         * [5.1/H-1-17] MUST have at least 1 Hw image decoder supporting AVIF Baseline profile.
         */
        public static VideoCodecRequirement createRAVIFDecoderReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.AVIF_DEC_REQ)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_17, requirement);
        }

        /**
         * [2.2.7.1/5.1/H-1-18] MUST support AV1 encoder which can encode up to 480p resolution
         * at 30fps and 1Mbps.
         */
        public static VideoCodecRequirement createRAV1EncoderReq() {
            RequiredMeasurement<Integer> resolution = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.AV1_ENC_RESOLUTION)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 480)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 480)
                    .build();

            RequiredMeasurement<Double> fps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.AV1_ENC_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 30.0)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 30.0)
                    .build();

            RequiredMeasurement<Integer> bitrate = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.AV1_ENC_BITRATE)
                    .setPredicate(RequirementConstants.INTEGER_GTE)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_18, resolution, fps,
                    bitrate);
        }

        /**
         * [5.1/H-1-20] MUST support the Feature_HlgEditing feature for all hardware AV1 and HEVC
         * encoders present on the device at 4K resolution or the largest Camera-supported
         * resolution, whichever is less.
         */
        public static VideoCodecRequirement createR5_1__H_1_20() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.HLG_EDITING)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_20, requirement);
        }

        /**
         * [5.1/H-1-21] MUST support FEATURE_DynamicColorAspects for all hardware video decoders
         *  (AVC, HEVC, VP9, AV1 or later).
         */
        public static VideoCodecRequirement createR5_1__H_1_21() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.DYNAMIC_COLOR_ASPECTS)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_21, requirement);
        }

        /**
         * [5.12/H-1-22] MUST support both landscape and portrait resolution for all hardware
         * codecs. AV1 codecs are limited to only 1080p resolution while others should support
         * 4k or camera preferred resolution (whichever is less)
         */
        public static VideoCodecRequirement createR5_1__H_1_22() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PORTRAIT_RESOLUTION)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new VideoCodecRequirement(RequirementConstants.R5_1__H_1_22, requirement);
        }
    }

    public <R extends Requirement> R addRequirement(R req) {
        if (!this.mRequirements.add(req)) {
            throw new IllegalStateException("Requirement " + req.id() + " already added");
        }
        return req;
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_720p(String mimeType1, String mimeType2,
            int resolution) {
        return this.addRequirement(
                ConcurrentCodecRequirement.createR5_1__H_1_1_720p(mimeType1, mimeType2,
                        resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_1_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_1_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_1_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_720p(String mimeType1, String mimeType2,
            int resolution) {
        return this.addRequirement(
                ConcurrentCodecRequirement.createR5_1__H_1_2_720p(mimeType1, mimeType2,
                        resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_2_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_2_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_2_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_720p(String mimeType1, String mimeType2,
            int resolution) {
        return this.addRequirement(
                ConcurrentCodecRequirement.createR5_1__H_1_3_720p(mimeType1, mimeType2,
                        resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_3_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_3_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_3_4K());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_720p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_720p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_4_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_4_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_720p(String mimeType1, String mimeType2,
            int resolution) {
        return this.addRequirement(
                ConcurrentCodecRequirement.createR5_1__H_1_5_720p(mimeType1, mimeType2,
                        resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_5_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_5_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_5_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_720p(String mimeType1, String mimeType2,
            int resolution) {
        return this.addRequirement(
                ConcurrentCodecRequirement.createR5_1__H_1_6_720p(mimeType1, mimeType2,
                        resolution));
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_6_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_6_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_6_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_9_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_9_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_9_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_9_4k());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_10_1080p() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_10_1080p());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_10_4k() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_10_4k());
    }

    public SecureCodecRequirement addR5_1__H_1_11() {
        return this.addRequirement(SecureCodecRequirement.createR5_1__H_1_11());
    }

    public CodecInitLatencyRequirement addR5_1__H_1_12() {
        return this.addRequirement(CodecInitLatencyRequirement.createR5_1__H_1_12());
    }

    /* Adds requirement 5.1/H-1-14 */
    public VideoCodecRequirement addRAV1DecoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAV1DecoderReq());
    }

    /* Adds requirement 5.1/H-1-15 */
    public VideoCodecRequirement addR4k60HwDecoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwDecoder());
    }

    /* Adds requirement 5.1/H-1-16 */
    public VideoCodecRequirement addR4k60HwEncoder() {
        return this.addRequirement(VideoCodecRequirement.createR4k60HwEncoder());
    }

    /* Adds requirement 5.1/H-1-17 */
    public VideoCodecRequirement addRAVIFDecoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAVIFDecoderReq());
    }

    /* Adds requirement 5.1/H-1-18 */
    public VideoCodecRequirement addRAV1EncoderReq() {
        return this.addRequirement(VideoCodecRequirement.createRAV1EncoderReq());
    }

    public ConcurrentCodecRequirement addR5_1__H_1_19() {
        return this.addRequirement(ConcurrentCodecRequirement.createR5_1__H_1_19());
    }

    /* Adds requirement 5.1/H-1-20 */
    public VideoCodecRequirement addR5_1__H_1_20() {
        return this.addRequirement(VideoCodecRequirement.createR5_1__H_1_20());
    }

    /* Adds requirement 5.1/H-1-21 */
    public VideoCodecRequirement addR5_1__H_1_21() {
        return this.addRequirement(VideoCodecRequirement.createR5_1__H_1_21());
    }

    /* Adds requirement 5.1/H-1-22 */
    public VideoCodecRequirement addR5_1__H_1_22() {
        return this.addRequirement(VideoCodecRequirement.createR5_1__H_1_22());
    }

    public FrameDropRequirement addR5_3__H_1_1_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_R());
    }

    public FrameDropRequirement addR5_3__H_1_1_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_ST());
    }

    public FrameDropRequirement addR5_3__H_1_1_U() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_1_U());
    }

    public FrameDropRequirement addR5_3__H_1_2_R() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_R());
    }

    public FrameDropRequirement addR5_3__H_1_2_ST() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_ST());
    }

    public FrameDropRequirement addR5_3__H_1_2_U() {
        return this.addRequirement(FrameDropRequirement.createR5_3__H_1_2_U());
    }


    public AudioTap2ToneLatencyRequirement addR5_6__H_1_1() {
        return this.addRequirement(AudioTap2ToneLatencyRequirement.createR5_6__H_1_1());
    }


    public SecureCodecRequirement addR5_7__H_1_2() {
        return this.addRequirement(SecureCodecRequirement.createR5_7__H_1_2());
    }

    /* Adds requirement 5.12/H-1-3 */
    public ExtYuvTargetRequirement addExtYUVSupportReq() {
        return this.addRequirement(ExtYuvTargetRequirement.createExtensionReq());
    }

    /** Add requirement <b>7.1.4.1/H-1-2</b> */
    public EglRequirement addR7_1_4_1__H_1_2() {
        return this.addRequirement(EglRequirement.createR7_1_4_1__H_1_2());
    }

    /* Adds requirement 7.5/H-1-1 */
    public PrimaryCameraRequirement addPrimaryRearCameraReq() {
        return this.addRequirement(PrimaryCameraRequirement.createRearPrimaryCamera());
    }

    /* Adds requirement 7.5/H-1-2 */
    public PrimaryCameraRequirement addPrimaryFrontCameraReq() {
        return this.addRequirement(PrimaryCameraRequirement.createFrontPrimaryCamera());
    }

    public CameraTimestampSourceRequirement addR7_5__H_1_4() {
        return this.addRequirement(CameraTimestampSourceRequirement.createTimestampSourceReq());
    }

    public CameraLatencyRequirement addR7_5__H_1_5() {
        return this.addRequirement(CameraLatencyRequirement.createJpegLatencyReq());
    }

    public CameraLatencyRequirement addR7_5__H_1_6() {
        return this.addRequirement(CameraLatencyRequirement.createLaunchLatencyReq());
    }

    public CameraRawRequirement addR7_5__H_1_8() {
        return this.addRequirement(CameraRawRequirement.createRawReq());
    }

    public Camera240FpsRequirement addR7_5__H_1_9() {
        return this.addRequirement(Camera240FpsRequirement.create240FpsReq());
    }

    public UltraWideZoomRatioRequirement addR7_5__H_1_10() {
        return this.addRequirement(UltraWideZoomRatioRequirement.createUltrawideZoomRatioReq());
    }

    public ConcurrentRearFrontRequirement addR7_5__H_1_11() {
        return this.addRequirement(ConcurrentRearFrontRequirement.createConcurrentRearFrontReq());
    }

    public PreviewStabilizationRequirement addR7_5__H_1_12() {
        return this.addRequirement(PreviewStabilizationRequirement.createPreviewStabilizationReq());
    }

    public LogicalMultiCameraRequirement addR7_5__H_1_13() {
        return this.addRequirement(LogicalMultiCameraRequirement.createLogicalMultiCameraReq());
    }

    public StreamUseCaseRequirement addR7_5__H_1_14() {
        return this.addRequirement(StreamUseCaseRequirement.createStreamUseCaseReq());
    }

    public CameraExtensionRequirement addR7_5__H_1_15() {
        return this.addRequirement(CameraExtensionRequirement.createCameraExtensionReq());
    }

    public DynamicRangeTenBitsRequirement addR7_5__H_1_16() {
        return this.addRequirement(DynamicRangeTenBitsRequirement.createDynamicRangeTenBitsReq());
    }

    public FaceDetectionRequirement addR7_5__H_1_17() {
        return this.addRequirement(FaceDetectionRequirement.createFaceDetectionReq());
    }
    public JpegRRequirement addR7_5__H_1_18() {
        return this.addRequirement(JpegRRequirement.createJpegRReq());
    }

    /* Adds requirement 7.5/H-1-20 */
    public CameraUltraHdrRequirement addR7_5__H_1_20() {
        return this.addRequirement(CameraUltraHdrRequirement.createUltraHdrReq());
    }

    // TODO: b/329526179 Move all camera requirements to separate file
    public HLGCombinationRequirement addR7_5__H_1_19() {
        return this.addRequirement(HLGCombinationRequirement.createRearHLGCombinationReq());
    }

    public ResolutionRequirement addR7_1_1_1__H_1_1() {
        return this.<ResolutionRequirement>addRequirement(
                ResolutionRequirement.createR7_1_1_1__H_1_1());
    }

    public DensityRequirement addR7_1_1_3__H_1_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_1_1());
    }

    public ResolutionRequirement addR7_1_1_1__H_2_1() {
        return this.<ResolutionRequirement>addRequirement(
                ResolutionRequirement.createR7_1_1_1__H_2_1());
    }

    public DensityRequirement addR7_1_1_3__H_2_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_2_1());
    }


    private enum SubmitType {
        TRADEFED, VERIFIER
    }

    public void submitAndCheck() {
        boolean perfClassMet = submit(SubmitType.TRADEFED);

        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", Utils.isPerfClass());
        assertThat(perfClassMet).isTrue();
    }

    public void submitAndVerify() {
        boolean perfClassMet = submit(SubmitType.VERIFIER);

        if (!perfClassMet && Utils.isPerfClass()) {
            Log.w(TAG, "Device did not meet specified performance class: " + Utils.getPerfClass());
        }
    }

    private boolean submit(SubmitType type) {
        boolean perfClassMet = true;
        for (Requirement req : this.mRequirements) {
            switch (type) {
                case VERIFIER:
                    CtsVerifierReportLog verifierLog = new CtsVerifierReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(verifierLog, this.mTestName);
                    verifierLog.submit();
                    break;

                case TRADEFED:
                default:
                    DeviceReportLog tradefedLog = new DeviceReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(tradefedLog, this.mTestName);
                    tradefedLog.submit(InstrumentationRegistry.getInstrumentation());
                    break;
            }
        }
        this.mRequirements.clear(); // makes sure report isn't submitted twice
        return perfClassMet;
    }
}
