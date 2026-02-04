/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.media.codec.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.TestArgs;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Verification test for video encoder and decoder.
 *
 * <p>A raw yv12 stream is encoded at various settings and written to an IVF file. Encoded stream
 * bitrate and key frame interval are checked against target values. The stream is later decoded by
 * video decoder to verify frames are decodable and to calculate PSNR values for various bitrates.
 */
@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
public class VideoCodecTest extends VideoCodecTestBase {

    private static final String ENCODED_IVF_BASE = "football";
    private static final String INPUT_YUV = null;
    private static final String OUTPUT_YUV =
            SDCARD_DIR + File.separator + ENCODED_IVF_BASE + "_out.yuv";

    // YUV stream properties.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int FPS = 30;
    // Default encoding bitrate.
    private static final int BITRATE = 400000;
    // List of bitrates used in quality and basic bitrate tests.
    private static final int[] TEST_BITRATES_SET = {300000, 500000, 700000, 900000};
    // Maximum allowed bitrate variation from the target value.
    // Keep in sync with the variation at libmediandkjni/native_media_utils.h
    // used in some tests along with BITRATE
    private static final double MAX_BITRATE_VARIATION = 0.2;
    // The tolerance varies by the bitrate, because lower bitrates interact with
    // video quality standards introduced in Android 12.
    private static final double[] MAX_CBR_BITRATE_VARIATIONS = {0.20, 0.20, 0.20, 0.20};
    private static final double[] MAX_VBR_BITRATE_VARIATIONS = {0.50, 0.30, 0.30, 0.30};
    // Average PSNR values for reference Google Video codec for the above bitrates.
    private static final double[] REFERENCE_AVERAGE_PSNR = {33.1, 35.2, 36.6, 37.8};
    // Minimum PSNR values for reference Google Video codec for the above bitrates.
    private static final double[] REFERENCE_MINIMUM_PSNR = {25.9, 27.5, 28.4, 30.3};
    // Maximum allowed average PSNR difference of encoder comparing to reference Google encoder.
    private static final double MAX_AVERAGE_PSNR_DIFFERENCE = 2;
    // Maximum allowed minimum PSNR difference of encoder comparing to reference Google encoder.
    private static final double MAX_MINIMUM_PSNR_DIFFERENCE = 4;
    // Maximum allowed average PSNR difference of the encoder running in a looper thread with 0 ms
    // buffer dequeue timeout comparing to the encoder running in a callee's thread with 100 ms
    // buffer dequeue timeout.
    private static final double MAX_ASYNC_AVERAGE_PSNR_DIFFERENCE = 1.5;
    // Maximum allowed minimum PSNR difference of the encoder running in a looper thread
    // comparing to the encoder running in a callee's thread.
    private static final double MAX_ASYNC_MINIMUM_PSNR_DIFFERENCE = 2;
    // Maximum allowed average key frame interval variation from the target value.
    private static final int MAX_AVERAGE_KEYFRAME_INTERVAL_VARIATION = 1;
    // Maximum allowed key frame interval variation from the target value.
    private static final int MAX_KEYFRAME_INTERVAL_VARIATION = 3;

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public String mCodecMimeType;

    @Parameterized.Parameter(2)
    public int mBitRateMode;

    private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] encodersForMime = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String encoder : encodersForMime) {
                if (TestArgs.shouldSkipCodec(encoder)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                testArgs[0] = encoder;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList =
                Arrays.asList(
                        new Object[][] {
                            {VP8_MIME, VIDEO_ControlRateConstant},
                            {VP8_MIME, VIDEO_ControlRateVariable},
                            {VP9_MIME, VIDEO_ControlRateConstant},
                            {VP9_MIME, VIDEO_ControlRateVariable},
                            {AVC_MIME, VIDEO_ControlRateConstant},
                            {AVC_MIME, VIDEO_ControlRateVariable},
                            {HEVC_MIME, VIDEO_ControlRateConstant},
                            {HEVC_MIME, VIDEO_ControlRateVariable},
                            {AV1_MIME, VIDEO_ControlRateConstant},
                            {AV1_MIME, VIDEO_ControlRateVariable},
                        });
        return prepareParamList(exhaustiveArgsList);
    }

    /**
     * A basic test for Video encoder.
     *
     * <p>Encodes 9 seconds of raw stream with default configuration options, and then decodes it to
     * verify the bitstream. Verifies the average bitrate is within allowed MAX_BITRATE_VARIATIONS[]
     * of the target value.
     */
    private void internalTestBasic(String codecName, String codecMimeType, int bitRateMode)
            throws Exception {
        int encodeSeconds = 9;
        boolean skipped = true;

        for (int i = 0; i < TEST_BITRATES_SET.length; i++) {
            int targetBitrate = TEST_BITRATES_SET[i];

            EncoderOutputStreamParameters params =
                    getDefaultEncodingParameters(
                            INPUT_YUV,
                            ENCODED_IVF_BASE,
                            codecName,
                            codecMimeType,
                            encodeSeconds,
                            WIDTH,
                            HEIGHT,
                            FPS,
                            bitRateMode,
                            targetBitrate,
                            true);
            ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();
            VideoEncodeOutput videoEncodeOutput = encode(params, codecConfigs);
            ArrayList<MediaCodec.BufferInfo> bufInfo = videoEncodeOutput.bufferInfo;
            if (bufInfo == null) {
                continue;
            }
            skipped = false;

            VideoEncodingStatistics statistics = computeEncodingStatistics(bufInfo);

            if (params.bitrateType == VIDEO_ControlRateConstant) {
                /* Constant bitrate -- variation applies to both over/under */
                double allowedVariance = MAX_CBR_BITRATE_VARIATIONS[i];
                assertEquals(
                        "Stream bitrate "
                                + statistics.mAverageBitrate
                                + " differs from the target "
                                + targetBitrate
                                + " by more than "
                                + allowedVariance * targetBitrate,
                        targetBitrate,
                        statistics.mAverageBitrate,
                        allowedVariance * targetBitrate);
            } else if (params.bitrateType == VIDEO_ControlRateVariable
                    && statistics.mAverageBitrate > targetBitrate) {
                /* VIDEO_ControlRateVariable mode only checks over-run */
                double allowedVariance = MAX_VBR_BITRATE_VARIATIONS[i];
                assertEquals(
                        "Stream bitrate "
                                + statistics.mAverageBitrate
                                + " above target "
                                + targetBitrate
                                + " by more than "
                                + allowedVariance * targetBitrate,
                        targetBitrate,
                        statistics.mAverageBitrate,
                        allowedVariance * targetBitrate);
            }

            decode(params.outputIvfFilename, null, codecMimeType, FPS, codecConfigs);
        }

        if (skipped) {
            Log.i(TAG, "SKIPPING testBasic(): codec is not supported");
        }
    }

    /**
     * Asynchronous encoding test for Video encoder.
     *
     * <p>Encodes 9 seconds of raw stream using synchronous and asynchronous calls. Checks the PSNR
     * difference between the encoded and decoded output and reference yuv input does not change
     * much for two different ways of the encoder call.
     */
    private void internalTestAsyncEncoding(String codecName, String codecMimeType, int bitRateMode)
            throws Exception {
        int encodeSeconds = 9;

        // First test the encoder running in a looper thread with buffer callbacks enabled.
        boolean syncEncoding = false;
        EncoderOutputStreamParameters params =
                getDefaultEncodingParameters(
                        INPUT_YUV,
                        ENCODED_IVF_BASE,
                        codecName,
                        codecMimeType,
                        encodeSeconds,
                        WIDTH,
                        HEIGHT,
                        FPS,
                        bitRateMode,
                        BITRATE,
                        syncEncoding);
        ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();
        VideoEncodeOutput videoEncodeOutput = encodeAsync(params, codecConfigs);
        ArrayList<MediaCodec.BufferInfo> bufInfos = videoEncodeOutput.bufferInfo;
        if (bufInfos == null) {
            Log.i(TAG, "SKIPPING testAsyncEncoding(): no suitable encoder found");
            return;
        }
        computeEncodingStatistics(bufInfos);
        decode(params.outputIvfFilename, OUTPUT_YUV, codecMimeType, FPS, codecConfigs);
        VideoDecodingStatistics statisticsAsync =
                computeDecodingStatistics(
                        params.inputYuvFilename,
                        "football_qvga.yuv",
                        OUTPUT_YUV,
                        params.frameWidth,
                        params.frameHeight);

        // Test the encoder running in a callee's thread.
        syncEncoding = true;
        params =
                getDefaultEncodingParameters(
                        INPUT_YUV,
                        ENCODED_IVF_BASE,
                        codecName,
                        codecMimeType,
                        encodeSeconds,
                        WIDTH,
                        HEIGHT,
                        FPS,
                        bitRateMode,
                        BITRATE,
                        syncEncoding);
        codecConfigs.clear();
        videoEncodeOutput = encode(params, codecConfigs);
        bufInfos = videoEncodeOutput.bufferInfo;
        if (bufInfos == null) {
            Log.i(TAG, "SKIPPING testAsyncEncoding(): no suitable encoder found");
            return;
        }
        computeEncodingStatistics(bufInfos);
        decode(params.outputIvfFilename, OUTPUT_YUV, codecMimeType, FPS, codecConfigs);
        VideoDecodingStatistics statisticsSync =
                computeDecodingStatistics(
                        params.inputYuvFilename,
                        "football_qvga.yuv",
                        OUTPUT_YUV,
                        params.frameWidth,
                        params.frameHeight);

        // Check PSNR difference.
        Log.d(
                TAG,
                "PSNR Average: Async: "
                        + statisticsAsync.mAveragePSNR
                        + ". Sync: "
                        + statisticsSync.mAveragePSNR);
        Log.d(
                TAG,
                "PSNR Minimum: Async: "
                        + statisticsAsync.mMinimumPSNR
                        + ". Sync: "
                        + statisticsSync.mMinimumPSNR);
        if ((Math.abs(statisticsAsync.mAveragePSNR - statisticsSync.mAveragePSNR)
                        > MAX_ASYNC_AVERAGE_PSNR_DIFFERENCE)
                || (Math.abs(statisticsAsync.mMinimumPSNR - statisticsSync.mMinimumPSNR)
                        > MAX_ASYNC_MINIMUM_PSNR_DIFFERENCE)) {
            throw new RuntimeException("Difference between PSNRs for async and sync encoders");
        }
    }

    /**
     * Check if MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME is honored.
     *
     * <p>Encodes 9 seconds of raw stream and requests a sync frame every second (30 frames). The
     * test does not verify the output stream.
     */
    private void internalTestSyncFrame(
            String codecName, String codecMimeType, int bitRateMode, boolean useNdk)
            throws Exception {
        int encodeSeconds = 9;

        EncoderOutputStreamParameters params =
                getDefaultEncodingParameters(
                        INPUT_YUV,
                        ENCODED_IVF_BASE,
                        codecName,
                        codecMimeType,
                        encodeSeconds,
                        WIDTH,
                        HEIGHT,
                        FPS,
                        bitRateMode,
                        BITRATE,
                        true);
        params.syncFrameInterval = encodeSeconds * FPS;
        params.syncForceFrameInterval = FPS;
        params.useNdk = useNdk;
        VideoEncodeOutput videoEncodeOutput = encode(params);
        ArrayList<MediaCodec.BufferInfo> bufInfo = videoEncodeOutput.bufferInfo;
        if (bufInfo == null) {
            Log.i(TAG, "SKIPPING testSyncFrame(): no suitable encoder found");
            return;
        }

        VideoEncodingStatistics statistics = computeEncodingStatistics(bufInfo);

        // First check if we got expected number of key frames.
        int actualKeyFrames = statistics.mKeyFrames.size();
        if (actualKeyFrames != encodeSeconds) {
            throw new RuntimeException(
                    "Number of key frames "
                            + actualKeyFrames
                            + " is different from the expected "
                            + encodeSeconds);
        }

        // Check key frame intervals:
        // Average value should be within +/- 1 frame of the target value,
        // maximum value should not be greater than target value + 3,
        // and minimum value should not be less that target value - 3.
        if (Math.abs(statistics.mAverageKeyFrameInterval - FPS)
                        > MAX_AVERAGE_KEYFRAME_INTERVAL_VARIATION
                || (statistics.mMaximumKeyFrameInterval - FPS > MAX_KEYFRAME_INTERVAL_VARIATION)
                || (FPS - statistics.mMinimumKeyFrameInterval > MAX_KEYFRAME_INTERVAL_VARIATION)) {
            throw new RuntimeException(
                    "Key frame intervals are different from the expected " + FPS);
        }
    }

    private String[] getSupportedLayeringSchemas(String codecName, String codecMimeType) {
        String[] schemas = new String[0];
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            if (!codecInfo.getName().equals(codecName)) {
                continue;
            }

            schemas =
                    codecInfo
                            .getCapabilitiesForType(codecMimeType)
                            .getEncoderCapabilities()
                            .getSupportedLayeringSchemas();
            break;
        }

        Log.d(
                TAG,
                codecName
                        + " ("
                        + codecMimeType
                        + ") supported layering schemas: "
                        + java.util.Arrays.toString(schemas));
        return schemas;
    }

    private static class BitrateStats {
        public final long totalFileBitrate;
        public final long firstHalfPayloadBitrate;
        public final long secondHalfPayloadBitrate;

        BitrateStats(long total, long first, long second) {
            this.totalFileBitrate = total;
            this.firstHalfPayloadBitrate = first;
            this.secondHalfPayloadBitrate = second;
        }
    }

    private BitrateStats computeBitrateStats(String filename, int encodeSeconds) {
        File f = new File(filename);
        long totalFileBitrate = f.length() * 8 / encodeSeconds;
        long firstHalfPayloadBitrate = 0;
        long secondHalfPayloadBitrate = 0;

        try {
            IvfReader reader = new IvfReader(filename);
            int frameCount = reader.getFrameCount();
            int halfCount = frameCount / 2;
            long bytesFirstHalf = 0;
            long bytesSecondHalf = 0;
            for (int i = 0; i < frameCount; i++) {
                byte[] frame = reader.readFrame(i);
                if (frame != null) {
                    if (i < halfCount) {
                        bytesFirstHalf += frame.length;
                    } else {
                        bytesSecondHalf += frame.length;
                    }
                }
            }
            reader.close();
            double durationHalf = encodeSeconds / 2.0;
            firstHalfPayloadBitrate = (long) (bytesFirstHalf * 8 / durationHalf);
            secondHalfPayloadBitrate = (long) (bytesSecondHalf * 8 / durationHalf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute split bitrate for: " + filename, e);
        }
        return new BitrateStats(
                totalFileBitrate, firstHalfPayloadBitrate, secondHalfPayloadBitrate);
    }

    private void internalTestTemporalLayerEncode(
            String codecName,
            String codecMimeType,
            int bitRateMode,
            boolean dynamicBitrateLayeringChange,
            boolean useNdk)
            throws Exception {
        int encodeSeconds = dynamicBitrateLayeringChange ? 30 : 15;
        String[] layeringSchemas = getSupportedLayeringSchemas(codecName, codecMimeType);
        assumeTrue("Temporal layer encoding may not be supported", layeringSchemas.length > 0);
        for (String schema : layeringSchemas) {
            int temporalLayers = 0;
            if (schema.contains("android.generic.2") || schema.contains("webrtc.svc.l1t2")) {
                temporalLayers = 2;
            } else if (schema.contains("android.generic.3") || schema.contains("webrtc.svc.l1t3")) {
                temporalLayers = 3;
            } else {
                continue;
            }

            Log.i(TAG, "Testing testTemporalLayerEncode(): " + schema);
            EncoderOutputStreamParameters params =
                    getDefaultEncodingParameters(
                            INPUT_YUV,
                            ENCODED_IVF_BASE,
                            codecName,
                            codecMimeType,
                            encodeSeconds,
                            WIDTH,
                            HEIGHT,
                            FPS,
                            bitRateMode,
                            // Use 500kbps for temporal layer encoding tests to compare against the
                            // reference.
                            500000,
                            true);
            params.useNdk = useNdk;
            params.temporalLayers = temporalLayers;
            params.layeringSchema = schema;

            params.bitrateLayeringRatios =
                    (temporalLayers == 2) ? BITRATE_LAYERING_2_LAYERS : BITRATE_LAYERING_3_LAYERS;
            params.dynamicBitrateLayeringRatios =
                    (temporalLayers == 2)
                            ? BITRATE_LAYERING_2_LAYERS_DYNAMIC
                            : BITRATE_LAYERING_3_LAYERS_DYNAMIC;
            params.dynamicBitrateUpdateFrameIndex =
                    dynamicBitrateLayeringChange ? FPS * encodeSeconds / 2 : -1;

            // We should increase the sync frame interval for temporal layer encoding tests to get a
            // more
            // accurate bitrate distribution statistics.
            params.syncFrameInterval = 100;

            String logStr =
                    String.format(
                            "Encoding params: Bitrate: %d, Mode: %d, Layers: %d, Schema:"
                                    + " %s, SyncInterval: %d, DynRatios: %s, UpdateIndex: %d",
                            params.bitrateSet[0],
                            bitRateMode,
                            temporalLayers,
                            schema,
                            params.syncFrameInterval,
                            params.dynamicBitrateLayeringRatios,
                            params.dynamicBitrateUpdateFrameIndex);
            Log.d(TAG, logStr);

            ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();
            VideoEncodeOutput videoEncodeOutput = encode(params, codecConfigs);
            decode(params.outputIvfFilename, null, codecMimeType, FPS, codecConfigs);

            List<Pair<String, String>> layers =
                    validateTemporalLayerSubstreams(
                            params.outputIvfFilename,
                            params.inputYuvFilename,
                            params.inputResource,
                            codecMimeType,
                            WIDTH,
                            HEIGHT,
                            temporalLayers,
                            0,
                            videoEncodeOutput.bufferInfo,
                            videoEncodeOutput.temporalLayerIds);

            double[] targetLayerRatios;
            if (dynamicBitrateLayeringChange) {
                targetLayerRatios =
                        (temporalLayers == 2)
                                ? BITRATE_RATIOS_2_LAYERS_DYNAMIC
                                : BITRATE_RATIOS_3_LAYERS_DYNAMIC;
            } else {
                targetLayerRatios =
                        (temporalLayers == 2) ? BITRATE_RATIOS_2_LAYERS : BITRATE_RATIOS_3_LAYERS;
            }

            // Index 1 corresponds to 500000 bps
            double allowedVariation =
                    (bitRateMode == VIDEO_ControlRateConstant)
                            ? MAX_CBR_BITRATE_VARIATIONS[1]
                            : MAX_VBR_BITRATE_VARIATIONS[1];
            int totalTargetBitrate = 500000;

            BitrateStats totalStats = computeBitrateStats(params.outputIvfFilename, encodeSeconds);

            // Verify total bitrate of the full stream is within the limit
            double differenceTotal = Math.abs(totalStats.totalFileBitrate - totalTargetBitrate);
            double percentageDifferenceTotal = differenceTotal / totalTargetBitrate;
            Log.d(
                    TAG,
                    String.format(
                            "Total bitrate: %d vs target: %d mode: %d diff: %.2f%% allowed: %.2f%%",
                            totalStats.totalFileBitrate,
                            totalTargetBitrate,
                            bitRateMode,
                            percentageDifferenceTotal * 100,
                            allowedVariation * 100));

            Assert.assertTrue(
                    "Total Bitrate variation "
                            + (percentageDifferenceTotal * 100)
                            + "% exceeds allowed "
                            + (allowedVariation * 100)
                            + "%",
                    percentageDifferenceTotal <= allowedVariation);

            // Verify each of the layers follow the expected bitrate ratios, and produces acceptable
            // PSNR..
            for (int i = 0; i < layers.size(); i++) {
                Pair<String, String> layer = layers.get(i);
                String filename = layer.first;
                String refYuv = layer.second;

                BitrateStats layerStats = computeBitrateStats(filename, encodeSeconds);

                if (dynamicBitrateLayeringChange) {
                    double[] targetRatiosFirst =
                            (temporalLayers == 2)
                                    ? BITRATE_RATIOS_2_LAYERS
                                    : BITRATE_RATIOS_3_LAYERS;
                    double[] targetRatiosSecond =
                            (temporalLayers == 2)
                                    ? BITRATE_RATIOS_2_LAYERS_DYNAMIC
                                    : BITRATE_RATIOS_3_LAYERS_DYNAMIC;

                    // First Half, before dynamic bitrate ratio change.
                    long expectedBitrateFirst =
                            (long) (totalStats.firstHalfPayloadBitrate * targetRatiosFirst[i]);
                    double differenceFirst =
                            Math.abs(layerStats.firstHalfPayloadBitrate - expectedBitrateFirst);
                    double percentageDifferenceFirst = differenceFirst / expectedBitrateFirst;

                    Log.d(
                            TAG,
                            String.format(
                                    "Decoding first half substream: %s bitrate: %d vs"
                                            + " target: %d mode: %d diff: %.2f%% allowed: %.2f%%",
                                    filename,
                                    layerStats.firstHalfPayloadBitrate,
                                    expectedBitrateFirst,
                                    bitRateMode,
                                    percentageDifferenceFirst * 100,
                                    allowedVariation * 100));

                    Assert.assertTrue(
                            "First Half Bitrate variation "
                                    + (percentageDifferenceFirst * 100)
                                    + "% exceeds allowed "
                                    + (allowedVariation * 100)
                                    + "%",
                            percentageDifferenceFirst <= allowedVariation);

                    // Second Half, after dynamic bitrate ratio change.
                    long expectedBitrateSecond =
                            (long) (totalStats.secondHalfPayloadBitrate * targetRatiosSecond[i]);
                    double differenceSecond =
                            Math.abs(layerStats.secondHalfPayloadBitrate - expectedBitrateSecond);
                    double percentageDifferenceSecond = differenceSecond / expectedBitrateSecond;

                    Log.d(
                            TAG,
                            String.format(
                                    "Decoding second half substream: %s bitrate: %d vs"
                                            + " target: %d mode: %d diff: %.2f%% allowed: %.2f%%",
                                    filename,
                                    layerStats.secondHalfPayloadBitrate,
                                    expectedBitrateSecond,
                                    bitRateMode,
                                    percentageDifferenceSecond * 100,
                                    allowedVariation * 100));

                    Assert.assertTrue(
                            "Second Half Bitrate variation "
                                    + (percentageDifferenceSecond * 100)
                                    + "% exceeds allowed "
                                    + (allowedVariation * 100)
                                    + "%",
                            percentageDifferenceSecond <= allowedVariation);
                } else {
                    double targetLayerBitrate = totalStats.totalFileBitrate * targetLayerRatios[i];
                    double difference = Math.abs(layerStats.totalFileBitrate - targetLayerBitrate);
                    double percentageDifference = difference / targetLayerBitrate;

                    Log.d(
                            TAG,
                            String.format(
                                    "Decoding substream: %s bitrate: %d vs target:"
                                            + " %.0f mode: %d diff: %.2f%% allowed: %.2f%%",
                                    filename,
                                    layerStats.totalFileBitrate,
                                    targetLayerBitrate,
                                    bitRateMode,
                                    percentageDifference * 100,
                                    allowedVariation * 100));

                    Assert.assertTrue(
                            "Bitrate variation "
                                    + (percentageDifference * 100)
                                    + "% exceeds allowed "
                                    + (allowedVariation * 100)
                                    + "%",
                            percentageDifference <= allowedVariation);
                }

                String decodedYuv = filename.replace(".ivf", "_decoded.yuv");
                decode(filename, decodedYuv, codecMimeType, FPS, codecConfigs);

                VideoDecodingStatistics stats =
                        computeDecodingStatistics(refYuv, null, decodedYuv, WIDTH, HEIGHT);
                // Index 1 corresponds to 500000 bps
                double referencePsnr = REFERENCE_AVERAGE_PSNR[1];
                Log.d(
                        TAG,
                        "Layer "
                                + i
                                + " "
                                + filename
                                + " PSNR: "
                                + stats.mAveragePSNR
                                + " vs Reference PSNR: "
                                + referencePsnr);
                Assert.assertTrue(
                        "Low layer PSNR: " + stats.mAveragePSNR + " expected: " + referencePsnr,
                        stats.mAveragePSNR >= referencePsnr - MAX_AVERAGE_PSNR_DIFFERENCE);
            }
        }
    }

    /**
     * Check if MediaCodec.PARAMETER_KEY_VIDEO_BITRATE is honored.
     *
     * <p>Run the the encoder for 12 seconds. Request changes to the bitrate after 6 seconds and
     * ensure the encoder responds.
     */
    private void internalTestDynamicBitrateChange(
            String codecName, String codecMimeType, int bitRateMode, boolean useNdk)
            throws Exception {
        int encodeSeconds = 12; // Encoding sequence duration in seconds.
        int[] bitrateTargetValues = {400000, 800000}; // List of bitrates to test.

        EncoderOutputStreamParameters params =
                getDefaultEncodingParameters(
                        INPUT_YUV,
                        ENCODED_IVF_BASE,
                        codecName,
                        codecMimeType,
                        encodeSeconds,
                        WIDTH,
                        HEIGHT,
                        FPS,
                        bitRateMode,
                        bitrateTargetValues[0],
                        true);

        // Number of seconds for each bitrate
        int stepSeconds = encodeSeconds / bitrateTargetValues.length;
        // Fill the bitrates values.
        params.bitrateSet = new int[encodeSeconds * FPS];
        for (int i = 0; i < bitrateTargetValues.length; i++) {
            Arrays.fill(
                    params.bitrateSet,
                    i * encodeSeconds * FPS / bitrateTargetValues.length,
                    (i + 1) * encodeSeconds * FPS / bitrateTargetValues.length,
                    bitrateTargetValues[i]);
        }

        params.useNdk = useNdk;
        VideoEncodeOutput videoEncodeOutput = encode(params);
        ArrayList<MediaCodec.BufferInfo> bufInfo = videoEncodeOutput.bufferInfo;
        if (bufInfo == null) {
            Log.i(TAG, "SKIPPING testDynamicBitrateChange(): no suitable encoder found");
            return;
        }

        VideoEncodingStatistics statistics = computeEncodingStatistics(bufInfo);

        // Calculate actual average bitrates  for every [stepSeconds] second.
        int[] bitrateActualValues = new int[bitrateTargetValues.length];
        for (int i = 0; i < bitrateTargetValues.length; i++) {
            bitrateActualValues[i] = 0;
            for (int j = i * stepSeconds; j < (i + 1) * stepSeconds; j++) {
                bitrateActualValues[i] += statistics.mBitrates.get(j);
            }
            bitrateActualValues[i] /= stepSeconds;
            Log.d(
                    TAG,
                    "Actual bitrate for interval #"
                            + i
                            + " : "
                            + bitrateActualValues[i]
                            + ". Target: "
                            + bitrateTargetValues[i]);

            // Compare actual bitrate values to make sure at least same increasing/decreasing
            // order as the target bitrate values.
            for (int j = 0; j < i; j++) {
                long differenceTarget = bitrateTargetValues[i] - bitrateTargetValues[j];
                long differenceActual = bitrateActualValues[i] - bitrateActualValues[j];
                if (differenceTarget * differenceActual < 0) {
                    throw new RuntimeException(
                            "Target bitrates: "
                                    + bitrateTargetValues[j]
                                    + " , "
                                    + bitrateTargetValues[i]
                                    + ". Actual bitrates: "
                                    + bitrateActualValues[j]
                                    + " , "
                                    + bitrateActualValues[i]);
                }
            }
        }
    }

    /**
     * Check if encoder and decoder can run simultaneously on different threads.
     *
     * <p>Encodes and decodes 9 seconds of raw stream sequentially in CBR mode, and then run
     * parallel encoding and decoding of the same streams. Compares average bitrate and PSNR for
     * sequential and parallel runs.
     */
    private void internalTestParallelEncodingAndDecoding(String codecName, String codecMimeType)
            throws Exception {
        // check for encoder up front, as by the time we detect lack of
        // encoder support, we may have already started decoding.
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat format = MediaFormat.createVideoFormat(codecMimeType, WIDTH, HEIGHT);
        if (mcl.findEncoderForFormat(format) == null) {
            Log.i(TAG, "SKIPPING testParallelEncodingAndDecoding(): no suitable encoder found");
            return;
        }

        int encodeSeconds = 9;
        final int[] bitrate = new int[1];
        final double[] psnr = new double[1];
        final Exception[] exceptionEncoder = new Exception[1];
        final Exception[] exceptionDecoder = new Exception[1];
        final EncoderOutputStreamParameters params =
                getDefaultEncodingParameters(
                        INPUT_YUV,
                        ENCODED_IVF_BASE,
                        codecName,
                        codecMimeType,
                        encodeSeconds,
                        WIDTH,
                        HEIGHT,
                        FPS,
                        VIDEO_ControlRateConstant,
                        BITRATE,
                        true);
        final String inputIvfFilename = params.outputIvfFilename;
        final ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();

        Runnable runEncoder =
                new Runnable() {
                    public void run() {
                        try {
                            ArrayList<MediaCodec.BufferInfo> bufInfo;
                            if (codecConfigs.isEmpty()) {
                                VideoEncodeOutput videoEncodeOutput = encode(params, codecConfigs);
                                bufInfo = videoEncodeOutput.bufferInfo;
                            } else {
                                VideoEncodeOutput videoEncodeOutput = encode(params);
                                bufInfo = videoEncodeOutput.bufferInfo;
                            }
                            VideoEncodingStatistics statistics = computeEncodingStatistics(bufInfo);
                            bitrate[0] = statistics.mAverageBitrate;
                        } catch (Exception e) {
                            Log.e(TAG, "Encoder error: " + e.toString());
                            exceptionEncoder[0] = e;
                        }
                    }
                };
        Runnable runDecoder =
                new Runnable() {
                    public void run() {
                        try {
                            decode(inputIvfFilename, OUTPUT_YUV, codecMimeType, FPS, codecConfigs);
                            VideoDecodingStatistics statistics =
                                    computeDecodingStatistics(
                                            params.inputYuvFilename,
                                            "football_qvga.yuv",
                                            OUTPUT_YUV,
                                            params.frameWidth,
                                            params.frameHeight);
                            psnr[0] = statistics.mAveragePSNR;
                        } catch (Exception e) {
                            Log.e(TAG, "Decoder error: " + e.toString());
                            exceptionDecoder[0] = e;
                        }
                    }
                };

        // Sequential encoding and decoding.
        runEncoder.run();
        if (exceptionEncoder[0] != null) {
            throw exceptionEncoder[0];
        }
        int referenceBitrate = bitrate[0];
        runDecoder.run();
        if (exceptionDecoder[0] != null) {
            throw exceptionDecoder[0];
        }
        double referencePsnr = psnr[0];

        // Parallel encoding and decoding.
        params.outputIvfFilename = SDCARD_DIR + File.separator + ENCODED_IVF_BASE + "_copy.ivf";
        Thread threadEncoder = new Thread(runEncoder);
        Thread threadDecoder = new Thread(runDecoder);
        threadEncoder.start();
        threadDecoder.start();
        threadEncoder.join();
        threadDecoder.join();
        if (exceptionEncoder[0] != null) {
            throw exceptionEncoder[0];
        }
        if (exceptionDecoder[0] != null) {
            throw exceptionDecoder[0];
        }

        // Compare bitrates and PSNRs for sequential and parallel cases.
        Log.d(TAG, "Sequential bitrate: " + referenceBitrate + ". PSNR: " + referencePsnr);
        Log.d(TAG, "Parallel bitrate: " + bitrate[0] + ". PSNR: " + psnr[0]);
        assertEquals(
                "Bitrate for sequenatial encoding"
                        + referenceBitrate
                        + " is different from parallel encoding "
                        + bitrate[0],
                referenceBitrate,
                bitrate[0],
                MAX_BITRATE_VARIATION * referenceBitrate);
        assertEquals(
                "PSNR for sequenatial encoding"
                        + referencePsnr
                        + " is different from parallel encoding "
                        + psnr[0],
                referencePsnr,
                psnr[0],
                MAX_ASYNC_AVERAGE_PSNR_DIFFERENCE);
    }

    /**
     * Check the encoder quality for various bitrates by calculating PSNR
     *
     * <p>Run the the encoder for 9 seconds for each bitrate and calculate PSNR for each encoded
     * stream. Video streams with higher bitrates should have higher PSNRs. Also compares average
     * and minimum PSNR of codec with PSNR values of reference Google codec.
     */
    private void internalTestEncoderQuality(String codecName, String codecMimeType, int bitRateMode)
            throws Exception {
        int encodeSeconds = 9; // Encoding sequence duration in seconds for each bitrate.
        double[] psnrPlatformCodecAverage = new double[TEST_BITRATES_SET.length];
        double[] psnrPlatformCodecMin = new double[TEST_BITRATES_SET.length];
        boolean[] completed = new boolean[TEST_BITRATES_SET.length];
        boolean skipped = true;

        // Run platform specific encoder for different bitrates
        // and compare PSNR of codec with PSNR of reference Google codec.
        for (int i = 0; i < TEST_BITRATES_SET.length; i++) {
            EncoderOutputStreamParameters params =
                    getDefaultEncodingParameters(
                            INPUT_YUV,
                            ENCODED_IVF_BASE,
                            codecName,
                            codecMimeType,
                            encodeSeconds,
                            WIDTH,
                            HEIGHT,
                            FPS,
                            bitRateMode,
                            TEST_BITRATES_SET[i],
                            true);
            ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();
            if (encode(params, codecConfigs) == null) {
                // parameters not supported, try other bitrates
                completed[i] = false;
                continue;
            }
            completed[i] = true;
            skipped = false;

            decode(params.outputIvfFilename, OUTPUT_YUV, codecMimeType, FPS, codecConfigs);
            VideoDecodingStatistics statistics =
                    computeDecodingStatistics(
                            params.inputYuvFilename,
                            "football_qvga.yuv",
                            OUTPUT_YUV,
                            params.frameWidth,
                            params.frameHeight);
            psnrPlatformCodecAverage[i] = statistics.mAveragePSNR;
            psnrPlatformCodecMin[i] = statistics.mMinimumPSNR;
        }

        if (skipped) {
            Log.i(TAG, "SKIPPING testEncoderQuality(): no bitrates supported");
            return;
        }

        // First do an initial check - higher bitrates should results in higher PSNR.
        for (int i = 1; i < TEST_BITRATES_SET.length; i++) {
            if (!completed[i]) {
                continue;
            }
            for (int j = 0; j < i; j++) {
                if (!completed[j]) {
                    continue;
                }
                double differenceBitrate = TEST_BITRATES_SET[i] - TEST_BITRATES_SET[j];
                double differencePSNR = psnrPlatformCodecAverage[i] - psnrPlatformCodecAverage[j];
                if (differenceBitrate * differencePSNR < 0) {
                    throw new RuntimeException(
                            "Target bitrates: "
                                    + TEST_BITRATES_SET[j]
                                    + ", "
                                    + TEST_BITRATES_SET[i]
                                    + ". Actual PSNRs: "
                                    + psnrPlatformCodecAverage[j]
                                    + ", "
                                    + psnrPlatformCodecAverage[i]);
                }
            }
        }

        // Then compare average and minimum PSNR of platform codec with reference Google codec -
        // average PSNR for platform codec should be no more than 2 dB less than reference PSNR
        // and minumum PSNR - no more than 4 dB less than reference minimum PSNR.
        // These PSNR difference numbers are arbitrary for now, will need further estimation
        // when more devices with HW video codec will appear.
        for (int i = 0; i < TEST_BITRATES_SET.length; i++) {
            if (!completed[i]) {
                continue;
            }

            Log.d(TAG, "Bitrate " + TEST_BITRATES_SET[i]);
            Log.d(
                    TAG,
                    "Reference: Average: "
                            + REFERENCE_AVERAGE_PSNR[i]
                            + ". Minimum: "
                            + REFERENCE_MINIMUM_PSNR[i]);
            Log.d(
                    TAG,
                    "Platform:  Average: "
                            + psnrPlatformCodecAverage[i]
                            + ". Minimum: "
                            + psnrPlatformCodecMin[i]);
            if (psnrPlatformCodecAverage[i]
                    < REFERENCE_AVERAGE_PSNR[i] - MAX_AVERAGE_PSNR_DIFFERENCE) {
                throw new RuntimeException(
                        "Low average PSNR "
                                + psnrPlatformCodecAverage[i]
                                + " comparing to reference PSNR "
                                + REFERENCE_AVERAGE_PSNR[i]
                                + " for bitrate "
                                + TEST_BITRATES_SET[i]);
            }
            if (psnrPlatformCodecMin[i] < REFERENCE_MINIMUM_PSNR[i] - MAX_MINIMUM_PSNR_DIFFERENCE) {
                throw new RuntimeException(
                        "Low minimum PSNR "
                                + psnrPlatformCodecMin[i]
                                + " comparing to reference PSNR "
                                + REFERENCE_MINIMUM_PSNR[i]
                                + " for bitrate "
                                + TEST_BITRATES_SET[i]);
            }
        }
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_BITRATE_MODE",
                "android.media.MediaFormat#KEY_BIT_RATE",
                "android.media.MediaFormat#KEY_COLOR_FORMAT",
                "android.media.MediaFormat#KEY_FRAME_RATE",
                "android.media.MediaFormat#KEY_I_FRAME_INTERVAL"
            })
    @Test
    public void testBasic() throws Exception {
        internalTestBasic(mCodecName, mCodecMimeType, mBitRateMode);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_BITRATE_MODE",
                "android.media.MediaFormat#KEY_BIT_RATE",
                "android.media.MediaFormat#KEY_COLOR_FORMAT",
                "android.media.MediaFormat#KEY_FRAME_RATE",
                "android.media.MediaFormat#KEY_I_FRAME_INTERVAL"
            })
    @Test
    public void testAsyncEncode() throws Exception {
        internalTestAsyncEncoding(mCodecName, mCodecMimeType, mBitRateMode);
    }

    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @Test
    public void testSyncFrame() throws Exception {
        internalTestSyncFrame(mCodecName, mCodecMimeType, mBitRateMode, false);
    }

    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @Test
    public void testSyncFrameNdk() throws Exception {
        internalTestSyncFrame(mCodecName, mCodecMimeType, mBitRateMode, true);
    }

    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @Test
    public void testDynamicBitrateChange() throws Exception {
        internalTestDynamicBitrateChange(mCodecName, mCodecMimeType, mBitRateMode, false);
    }

    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @Test
    public void testDynamicBitrateChangeNdk() throws Exception {
        internalTestDynamicBitrateChange(mCodecName, mCodecMimeType, mBitRateMode, true);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_BITRATE_MODE",
                "android.media.MediaFormat#KEY_BIT_RATE",
                "android.media.MediaFormat#KEY_COLOR_FORMAT",
                "android.media.MediaFormat#KEY_FRAME_RATE",
                "android.media.MediaFormat#KEY_I_FRAME_INTERVAL"
            })
    @Test
    public void testEncoderQuality() throws Exception {
        internalTestEncoderQuality(mCodecName, mCodecMimeType, mBitRateMode);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_BITRATE_MODE",
                "android.media.MediaFormat#KEY_BIT_RATE",
                "android.media.MediaFormat#KEY_COLOR_FORMAT",
                "android.media.MediaFormat#KEY_FRAME_RATE",
                "android.media.MediaFormat#KEY_I_FRAME_INTERVAL"
            })
    @Test
    public void testParallelEncodingAndDecoding() throws Exception {
        Assume.assumeTrue(
                "Parallel Encode Decode test is run only for VBR mode",
                mBitRateMode == VIDEO_ControlRateVariable);
        internalTestParallelEncodingAndDecoding(mCodecName, mCodecMimeType);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_VIDEO_BITRATE_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYER_ID"
            })
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
    public void testTemporalLayerEncode() throws Exception {
        internalTestTemporalLayerEncode(mCodecName, mCodecMimeType, mBitRateMode, false, false);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_VIDEO_BITRATE_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYER_ID"
            })
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
    public void testTemporalLayerEncodeNdk() throws Exception {
        internalTestTemporalLayerEncode(mCodecName, mCodecMimeType, mBitRateMode, false, true);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_VIDEO_BITRATE_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYER_ID"
            })
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
    public void testDynamicBitrateLayeringChange() throws Exception {
        internalTestTemporalLayerEncode(mCodecName, mCodecMimeType, mBitRateMode, true, false);
    }

    @ApiTest(
            apis = {
                "android.media.MediaFormat#KEY_VIDEO_BITRATE_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYERING",
                "android.media.MediaFormat#KEY_TEMPORAL_LAYER_ID"
            })
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
    public void testDynamicBitrateLayeringChangeNdk() throws Exception {
        internalTestTemporalLayerEncode(mCodecName, mCodecMimeType, mBitRateMode, true, true);
    }
}
