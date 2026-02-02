/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import static android.media.audio.Flags.iamfDefinitionsApi;
import static android.media.codec.Flags.apvSupport;
import static android.mediapc.cts.CodecEncoderPerformanceClassTestBase.getAudioEncoderCfgParams;
import static android.mediapc.cts.CodecEncoderPerformanceClassTestBase.getVideoEncoderCfgParams;
import static android.mediapc.cts.DolbyVisionParamPreparer.getDvResForInitializationLatencyTest;
import static android.mediav2.common.cts.CodecTestBase.BOARD_SDK_IS_AT_LEAST_202604;
import static android.mediav2.common.cts.CodecTestBase.IS_AT_LEAST_B;
import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.codecFilter;
import static android.mediav2.common.cts.CodecTestBase.codecPrefix;
import static android.mediav2.common.cts.CodecTestBase.compileMediaTypesList;
import static android.mediav2.common.cts.CodecTestBase.getCodecCapabilities;
import static android.mediav2.common.cts.CodecTestBase.getCodecInfo;
import static android.mediav2.common.cts.CodecTestBase.mediaTypePrefix;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;
import static android.mediav2.common.cts.CodecTestBase.selectHardwareCodecs;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;
import static com.android.media.extractor.flags.Flags.extractorMp4EnableIamf;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.PerformanceClassTestRule;
import android.mediapc.cts.common.Precondition;
import android.mediapc.cts.common.Preconditions;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Utils;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.CodecTestBase.ComponentClass;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The following test class validates the codec initialization latency (time for codec create +
 * configure) for the audio codecs and hardware video codecs available in the device, under the
 * load condition (Transcode + MediaRecorder session Audio(Microphone) and 1080p Video(Camera)).
 */
@RunWith(Parameterized.class)
public class CodecInitializationLatencyTest {
    private static final String LOG_TAG = CodecInitializationLatencyTest.class.getSimpleName();
    private static final boolean[] boolStates = {false, true};

    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String AVC_TRANSCODE_FILE = "bbb_1920x1080_8mbps_60fps_avc.mp4";
    private static String AVC_DECODER_NAME;
    private static String AVC_ENCODER_NAME;
    private static final Map<String, String> mTestFiles = new HashMap<>();

    static {
        // Audio media types
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_stereo_48kHz_128kbps_aac.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_mono_8kHz_12.2kbps_amrnb.3gp");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_23kbps_amrwb.3gp");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_12kHz_lvl4_flac.mka");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_2ch_8kHz_alaw.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_2ch_8kHz_mulaw.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_8kHz.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_stereo_48kHz_128kbps_vorbis.ogg");
        if (IS_AT_LEAST_B && iamfDefinitionsApi() && extractorMp4EnableIamf()) {
            mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_IAMF, "7_1_4_Opus_no_video.mp4");
        }
        if (BOARD_SDK_IS_AT_LEAST_202604) {
            mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AC3, "ac3_200_48kHz_128.mp4");
            mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AC4, "ac4_510_48kHz_128.mp4");
            mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_EAC3, "eac3_200_48kHz_128.mp4");
        }

        // Video media types
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1920x1080_4mbps_30fps_av1.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1920x1080_6mbps_30fps_avc.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_H263, "bbb_cif_768kbps_30fps_h263.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1920x1080_4mbps_30fps_hevc.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_1920x1080_12mbps_30fps_mpeg2.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_cif_768kbps_30fps_mpeg4.mkv");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_1920x1080_6mbps_30fps_vp8.webm");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1920x1080_4mbps_30fps_vp9.webm");
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            mTestFiles.put(
                    MediaFormat.MIMETYPE_VIDEO_APV, "pattern_1280x720_30fps_30mbps_apv_10bit.mp4");
        }
    }

    @Rule(order = 1)
    public final PerformanceClassTestRule pcRule =
            PerformanceClassTestRule.with(
                    Preconditions.BASELINE,
                    Precondition.create(
                            "Test requires h/w avc decoder",
                            CodecInitializationLatencyTest::initHwAvcEncoderName),
                    Precondition.create(
                            "Test requires h/w avc encoder",
                            CodecInitializationLatencyTest::initHwAvcDecoderName),
                    Precondition.create(
                            "The device doesn't support running at least four 1920x1080 avc"
                                    + " instances concurrently",
                            Utils.meetsAvcCodecPreconditions()),
                    Precondition.requireSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                    Precondition.requireSystemFeature(PackageManager.FEATURE_MICROPHONE));

    @Rule(order = 2)
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    private final String mMediaType;
    private final String mCodecName;

    private LoadStatus mTranscodeLoadStatus = null;
    private Thread mTranscodeLoadThread = null;
    private MediaRecorder mMediaRecorderLoad = null;
    private File mTempRecordedFile = null;
    private Surface mSurface = null;
    private Exception mException = null;

    @Before
    public void setUp() throws Exception {
        createSurface();
        startLoad();
    }

    private static boolean initHwAvcEncoderName() {
        ArrayList<String> listOfAvcHwEncoders = selectHardwareCodecs(AVC, null, null, true);
        if (listOfAvcHwEncoders.isEmpty()) {
            return false;
        }
        AVC_ENCODER_NAME = listOfAvcHwEncoders.get(0);
        return true;
    }

    private static boolean initHwAvcDecoderName() {
        ArrayList<String> listOfAvcHwDecoders = selectHardwareCodecs(AVC, null, null, false);
        if (listOfAvcHwDecoders.isEmpty()) {
            return false;
        }
        AVC_DECODER_NAME = listOfAvcHwDecoders.get(0);
        return true;
    }

    @After
    public void tearDown() throws Exception {
        stopLoad();
        releaseSurface();
    }

    public CodecInitializationLatencyTest(String mediaType, String codecName) {
        mMediaType = mediaType;
        mCodecName = codecName;
    }


    /**
     * Returns the list of parameters with mediaType and their codecs(for audio - all codecs,
     * video - hardware codecs).
     *
     * @return Collection of Parameters {0}_{1} -- MediaType_CodecName
     */
    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the required Hardware video codecs and all available
        // audio codecs present in the device.
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<String> mediaTypes =
                compileMediaTypesList(
                        ComponentClass.HARDWARE, false /* need audio */, true /* need video */);
        mediaTypes.addAll(
                compileMediaTypesList(
                        ComponentClass.ALL, true /* need audio */, false /* need video */));
        for (String mediaType : mediaTypes) {
            if (mediaTypePrefix != null && !mediaType.startsWith(mediaTypePrefix)) {
                continue;
            }
            ArrayList<String> listOfCodecs;
            if (mediaType.startsWith("audio/")) {
                listOfCodecs = selectCodecs(mediaType, null, null, true);
                listOfCodecs.addAll(selectCodecs(mediaType, null, null, false));
            } else {
                listOfCodecs = selectHardwareCodecs(mediaType, null, null, true);
                listOfCodecs.addAll(selectHardwareCodecs(mediaType, null, null, false));
            }
            for (String codec : listOfCodecs) {
                if ((codecPrefix != null && !codec.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(codec).matches())) {
                    continue;
                }
                argsList.add(new Object[]{mediaType, codec});
            }
        }
        return argsList;
    }

    private MediaRecorder createMediaRecorderLoad(Surface surface) throws Exception {
        MediaRecorder mediaRecorder = new MediaRecorder(Utils.getContext());
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(mMediaType.equalsIgnoreCase(HEVC) ?
                MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOutputFile(mTempRecordedFile);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setOrientationHint(0);
        mediaRecorder.setPreviewDisplay(surface);
        mediaRecorder.prepare();
        return mediaRecorder;
    }

    private void startLoad() throws Exception {
        // Create Transcode load (AVC Decoder(1080p) + AVC Encoder(720p))
        mTranscodeLoadStatus = new LoadStatus();
        mTranscodeLoadThread = new Thread(() -> {
            try {
                TranscodeLoad transcodeLoad = new TranscodeLoad(AVC, AVC_TRANSCODE_FILE,
                        AVC_DECODER_NAME, AVC_ENCODER_NAME, mTranscodeLoadStatus);
                transcodeLoad.doTranscode();
            } catch (Exception e) {
                mException = e;
            }
        });
        // Create MediaRecorder Session - Audio (Microphone) + 1080p Video (Camera)
        // Create a temp file to dump the MediaRecorder output. Later it will be deleted.
        mTempRecordedFile = new File(WorkDir.getMediaDirString() + "tempOut.mp4");
        mTempRecordedFile.createNewFile();
        mMediaRecorderLoad = createMediaRecorderLoad(mSurface);
        // Start the Loads
        mTranscodeLoadThread.start();
        mMediaRecorderLoad.start();
    }

    private void stopLoad() throws Exception {
        if (mTranscodeLoadStatus != null) {
            mTranscodeLoadStatus.setLoadFinished();
            mTranscodeLoadStatus = null;
        }
        if (mTranscodeLoadThread != null) {
            mTranscodeLoadThread.join();
            mTranscodeLoadThread = null;
        }
        if (mMediaRecorderLoad != null) {
            // Note that a RuntimeException is intentionally thrown to the application, if no valid
            // audio/video data has been received when stop() is called. This happens if stop() is
            // called immediately after start(). So sleep for 1000ms inorder to make sure some
            // data has been received between start() and stop().
            Thread.sleep(1000);
            mMediaRecorderLoad.stop();
            mMediaRecorderLoad.release();
            mMediaRecorderLoad = null;
            if (mTempRecordedFile != null && mTempRecordedFile.exists()) {
                mTempRecordedFile.delete();
                mTempRecordedFile = null;
            }
        }
        if (mException != null) throw mException;
    }

    private void createSurface() throws InterruptedException {
        mActivityRule.getActivity().waitTillSurfaceIsCreated();
        mSurface = mActivityRule.getActivity().getSurface();
        assertNotNull("Surface created is null.", mSurface);
        assertTrue("Surface created is invalid.", mSurface.isValid());
        mActivityRule.getActivity().setScreenParams(1920, 1080, true);
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * This test validates the initialization latency (time for codec create + configure) for
     * audio and hw video codecs.
     *
     * <p>Measurements are taken 5 * 2(sync/async) * [1 or 2]
     * (surface/non-surface for video) times. This also logs the stats: min, max, avg of the codec
     * initialization latencies.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
        "2.2.7.1/5.1/H-1-7",
        "2.2.7.1/5.1/H-1-8",
        "2.2.7.1/5.1/H-1-12",
        "2.2.7.1/5.1/H-1-13",})
    public void testInitializationLatency() throws Exception {
        boolean isEncoder = getCodecInfo(mCodecName).isEncoder();
        boolean isAudio = mMediaType.startsWith("audio/");
        EncoderConfigParams[] params = new EncoderConfigParams[1];
        if (isAudio) {
            int sampleRate = 48000;
            int channelCount = 2;
            int quality = 128000;
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
                sampleRate = 8000;
                channelCount = 1;
                quality = 12200;
            } else if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AMR_WB)) {
                sampleRate = 16000;
                channelCount = 1;
                quality = 23850;
            } else if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                quality = 5;
            }
            params[0] = getAudioEncoderCfgParams(mMediaType, quality, sampleRate, channelCount);
        } else {
            MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(mCodecName, mMediaType);
            MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
            assertNotNull("received null for video capabilities", videoCaps);
            int width = 176;
            int height = 144;
            int bitrate = 128000;
            if (videoCaps.isSizeSupported(1920, 1080)) {
                width = 1920;
                height = 1080;
                bitrate = 8000000;
            } else if (videoCaps.isSizeSupported(1280, 720)) {
                width = 1280;
                height = 720;
                bitrate = 5000000;
            } else if (videoCaps.isSizeSupported(640, 480)) {
                width = 640;
                height = 480;
                bitrate = 2000000;
            } else if (videoCaps.isSizeSupported(352, 288)) {
                width = 352;
                height = 288;
                bitrate = 512000;
            }
            params[0] = getVideoEncoderCfgParams(mMediaType, bitrate, width, height, 60,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        }
        final int NUM_MEASUREMENTS = 5;
        // Test gathers initialization latency for a number of iterations and
        // percentile is a variable used to control how many of these iterations
        // need to meet the pass criteria. For eg. if NUM_MEASUREMENTS = 5, audio, sync and Async
        // modes which is a total of 10 iterations, this translates to index 7.
        final int percentile = 70;
        long sumOfCodecInitializationLatencyMs = 0;
        int count = 0;
        int numOfActualMeasurements =
                NUM_MEASUREMENTS * boolStates.length * ((!isEncoder && !isAudio) ? 2 : 1);
        long[] codecInitializationLatencyMs = new long[numOfActualMeasurements];
        for (int i = 0; i < NUM_MEASUREMENTS; i++) {
            for (boolean isAsync : boolStates) {
                long latency;
                if (isEncoder) {
                    EncoderInitializationLatency encoderInitializationLatency =
                            new EncoderInitializationLatency(mMediaType, mCodecName, isAsync,
                                    params);
                    latency = encoderInitializationLatency.calculateInitLatency();
                    codecInitializationLatencyMs[count] = latency;
                    sumOfCodecInitializationLatencyMs += latency;
                    count++;
                } else {
                    String testFile;
                    if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)) {
                        testFile = getDvResForInitializationLatencyTest(mCodecName);
                    } else {
                        testFile = mTestFiles.get(mMediaType);
                    }
                    assumeTrue("Add test vector for media type: " + mMediaType, testFile != null);
                    if (isAudio) {
                        DecoderInitializationLatency decoderInitializationLatency =
                                new DecoderInitializationLatency(mMediaType, mCodecName, testFile,
                                        isAsync, false);
                        latency = decoderInitializationLatency.calculateInitLatency();
                        codecInitializationLatencyMs[count] = latency;
                        sumOfCodecInitializationLatencyMs += latency;
                        count++;
                    } else {
                        for (boolean surfaceMode : boolStates) {
                            DecoderInitializationLatency decoderInitializationLatency =
                                    new DecoderInitializationLatency(mMediaType, mCodecName,
                                            testFile,
                                            isAsync, surfaceMode);
                            latency = decoderInitializationLatency.calculateInitLatency();
                            codecInitializationLatencyMs[count] = latency;
                            sumOfCodecInitializationLatencyMs += latency;
                            count++;
                        }
                    }
                }
            }
        }
        Arrays.sort(codecInitializationLatencyMs);

        String statsLog = String.format("CodecInitialization latency for mediaType: %s, " +
                "Codec: %s, in Ms :: ", mMediaType, mCodecName);
        Log.i(LOG_TAG, "Min " + statsLog + codecInitializationLatencyMs[0]);
        Log.i(LOG_TAG, "Max " + statsLog + codecInitializationLatencyMs[count - 1]);
        Log.i(LOG_TAG, "Avg " + statsLog + (sumOfCodecInitializationLatencyMs / count));
        long initializationLatency = codecInitializationLatencyMs[percentile * count / 100];

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        if (isEncoder) {
            if (isAudio) {
                Requirements.addR5_1__H_1_8().to(pce).setCodecInitializationLatencyMs(
                        initializationLatency);
            } else {
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)) {
                    Requirements.addR5_1__H_1_7().withVariantDolby().to(pce).setCodecInitializationLatencyMs(
                            initializationLatency);
                } else {
                    Requirements.addR5_1__H_1_7().to(pce).setCodecInitializationLatencyMs(
                            initializationLatency);
                }
            }
        } else {
            if (isAudio) {
                Requirements.addR5_1__H_1_13().to(pce).setCodecInitializationLatencyMs(
                        initializationLatency);
            } else {
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)) {
                    Requirements.addR5_1__H_1_12().withVariantDolby().to(pce)
                            .setCodecInitializationLatencyMs(initializationLatency);
                } else {
                    Requirements.addR5_1__H_1_12().to(pce)
                            .setCodecInitializationLatencyMs(initializationLatency);
                }
            }
        }
    }

    /**
     * The following class calculates the encoder initialization latency (time for codec create +
     * configure).
     *
     * <p>And also logs the time taken by the encoder for:
     * (create + configure + start),
     * (create + configure + start + first frame to enqueue),
     * (create + configure + start + first frame to dequeue).
     */
    static class EncoderInitializationLatency extends CodecEncoderPerformanceClassTestBase {
        private static final String LOG_TAG = EncoderInitializationLatency.class.getSimpleName();

        private final boolean mIsAsync;

        EncoderInitializationLatency(String mediaType, String encoderName, boolean isAsync,
                EncoderConfigParams[] encCfgParams) {
            super(mediaType, encoderName, encCfgParams);
            mIsAsync = isAsync;
        }

        public long calculateInitLatency() throws IOException, InterruptedException {
            mActiveEncCfg = mEncCfgParams[0];
            MediaFormat format = mActiveEncCfg.getFormat();
            mActiveRawRes = mIsAudio ? INPUT_AUDIO_FILE : INPUT_VIDEO_FILE;
            setUpSource(mActiveRawRes.mFileName);
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            mOutputBuff = new OutputManager();
            long enqueueTimeStamp = 0;
            long dequeueTimeStamp = 0;
            long baseTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            resetContext(mIsAsync, false);
            mAsyncHandle.setCallBack(mCodec, mIsAsync);
            mCodec.configure(format, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
            long configureTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec.start();
            long startTimeStamp = SystemClock.elapsedRealtimeNanos();
            if (mIsAsync) {
                // We will keep on feeding the input to encoder until we see the first dequeued
                // frame.
                while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                    Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                    if (element != null) {
                        int bufferID = element.first;
                        MediaCodec.BufferInfo info = element.second;
                        if (info != null) {
                            dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            dequeueOutput(bufferID, info);
                            break;
                        } else {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(bufferID);
                        }
                    }
                }
            } else {
                while (!mSawOutputEOS) {
                    if (!mSawInputEOS) {
                        int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                        if (inputBufferId >= 0) {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(inputBufferId);
                        }
                    }
                    int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                    if (outputBufferId >= 0) {
                        dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                        dequeueOutput(outputBufferId, outInfo);
                        break;
                    }
                }
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
            Log.d(LOG_TAG, "Encode MediaType: " + mMediaType + " Encoder: " + mCodecName
                    + " Time for (create + configure) in ns: "
                    + (configureTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode MediaType: " + mMediaType + " Encoder: " + mCodecName
                    + " Time for (create + configure + start) in ns: "
                    + (startTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode MediaType: " + mMediaType + " Encoder: " + mCodecName
                    + " Time for (create + configure + start + first frame to enqueue) in ns: "
                    + (enqueueTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode MediaType: " + mMediaType + " Encoder: " + mCodecName
                    + " Time for (create + configure + start + first frame to dequeue) in ns: "
                    + (dequeueTimeStamp - baseTimeStamp));
            long timeToConfigureMs = (configureTimeStamp - baseTimeStamp) / 1000000;
            return timeToConfigureMs;
        }
    }

    /**
     * The following class calculates the decoder initialization latency (time for codec create +
     * configure).
     * And also logs the time taken by the decoder for:
     * (create + configure + start),
     * (create + configure + start + first frame to enqueue),
     * (create + configure + start + first frame to dequeue).
     */
    static class DecoderInitializationLatency extends CodecDecoderPerformanceClassTestBase {
        private static final String LOG_TAG = DecoderInitializationLatency.class.getSimpleName();

        private final boolean mIsAsync;

        DecoderInitializationLatency(String mediaType, String decoderName, String testFile,
                boolean isAsync, boolean surfaceMode) {
            super(mediaType, testFile, decoderName);
            mIsAsync = isAsync;
            mSurface = mIsAudio ? null :
                    surfaceMode ? MediaCodec.createPersistentInputSurface() : null;
        }

        public long calculateInitLatency() throws Exception {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            MediaFormat format = setUpSource(mTestFile);
            ArrayList<MediaFormat> formats = new ArrayList<>();
            formats.add(format);
            // If the decoder doesn't support the formats, then return Integer.MAX_VALUE to
            // indicate that all decode was not successful
            if (!areFormatsSupported(mCodecName, mMediaType, formats)) {
                return Integer.MAX_VALUE;
            }
            long enqueueTimeStamp = 0;
            long dequeueTimeStamp = 0;
            long baseTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            resetContext(mIsAsync, false);
            mAsyncHandle.setCallBack(mCodec, mIsAsync);
            mCodec.configure(format, mSurface, 0, null);
            long configureTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec.start();
            long startTimeStamp = SystemClock.elapsedRealtimeNanos();
            if (mIsAsync) {
                // We will keep on feeding the input to decoder until we see the first dequeued
                // frame.
                while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                    Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                    if (element != null) {
                        int bufferID = element.first;
                        MediaCodec.BufferInfo info = element.second;
                        if (info != null) {
                            dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            dequeueOutput(bufferID, info);
                            break;
                        } else {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(bufferID);
                        }
                    }
                }
            } else {
                while (!mSawOutputEOS) {
                    if (!mSawInputEOS) {
                        int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                        if (inputBufferId >= 0) {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(inputBufferId);
                        }
                    }
                    int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                    if (outputBufferId >= 0) {
                        dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                        dequeueOutput(outputBufferId, outInfo);
                        break;
                    }
                }
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
            if (mSurface != null) {
                mSurface.release();
                mSurface = null;
            }
            Log.d(LOG_TAG, "Decode MediaType: " + mMediaType + " Decoder: " + mCodecName
                    + " Time for (create + configure) in ns: "
                    + (configureTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode MediaType: " + mMediaType + " Decoder: " + mCodecName
                    + " Time for (create + configure + start) in ns: "
                    + (startTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode MediaType: " + mMediaType + " Decoder: " + mCodecName
                    + " Time for (create + configure + start + first frame to enqueue) in ns: "
                    + (enqueueTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode MediaType: " + mMediaType + " Decoder: " + mCodecName
                    + " Time for (create + configure + start + first frame to dequeue) in ns: "
                    + (dequeueTimeStamp - baseTimeStamp));
            long timeToConfigureMs = (configureTimeStamp - baseTimeStamp) / 1000000;
            return timeToConfigureMs;
        }
    }
}
