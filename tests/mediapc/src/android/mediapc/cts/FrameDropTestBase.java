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

import static android.mediapc.cts.CodecTestBase.codecFilter;
import static android.mediapc.cts.CodecTestBase.codecPrefix;
import static android.mediapc.cts.CodecTestBase.mediaTypePrefix;
import static android.mediapc.cts.CodecTestBase.selectCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;

import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediapc.cts.common.Precondition;
import android.mediapc.cts.common.Utils;
import android.os.Build;
import android.view.Surface;

import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrameDropTestBase {
    private static final String LOG_TAG = FrameDropTestBase.class.getSimpleName();
    static final boolean[] boolStates = {false, true};
    static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    static final String VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    static final String VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
    static final String AV1 = MediaFormat.MIMETYPE_VIDEO_AV1;
    static final String AAC = MediaFormat.MIMETYPE_AUDIO_AAC;
    static final String AAC_LOAD_FILE_NAME = "bbb_1c_128kbps_aac_audio.mp4";
    static final String AVC_LOAD_FILE_NAME = "bbb_1920x1080_8mbps_60fps_avc.mp4";
    static final long DECODE_31S = 31000; // In ms
    static final int MAX_FRAME_DROP_FOR_30S;
    // For perf class R, one frame drop per 10 seconds at 30 fps i.e. 3 drops per 30 seconds
    static final int MAX_FRAME_DROP_FOR_30S_30FPS_PC_R = 3;
    // For perf class S, two frame drops per 10 seconds at 60 fps i.e. 6 drops per 30 seconds
    static final int MAX_FRAME_DROP_FOR_30S_60FPS_PC_S = 6;
    // For perf class T, one frame drop per 10 seconds at 60 fps i.e. 3 drops per 30 seconds
    static final int MAX_FRAME_DROP_FOR_30S_60FPS_PC_T = 3;

    final String mMediaType;
    final String mDecoderName;
    final boolean mIsAsync;
    Surface mSurface;

    private LoadStatus mLoadStatus = null;
    private Thread mTranscodeLoadThread = null;
    private Thread mAudioPlaybackLoadThread = null;
    private Exception mTranscodeLoadException = null;
    private Exception mAudioPlaybackLoadException = null;

    static String AVC_DECODER_NAME;
    static String AVC_ENCODER_NAME;
    static String AAC_DECODER_NAME;
    static Map<String, String> m540p30FpsTestFiles = new HashMap<>();
    static Map<String, String> m1080p30FpsTestFiles = new HashMap<>();
    static Map<String, String> m540p60FpsTestFiles = new HashMap<>();
    static Map<String, String> m1080p60FpsTestFiles = new HashMap<>();
    static Map<String, String> m2160p60FpsTestFiles = new HashMap<>();
    static {
        m540p60FpsTestFiles.put(AVC, "bbb_960x540_3mbps_60fps_avc.mp4");
        m540p60FpsTestFiles.put(HEVC, "bbb_960x540_3mbps_60fps_hevc.mp4");
        m540p60FpsTestFiles.put(VP8, "bbb_960x540_3mbps_60fps_vp8.webm");
        m540p60FpsTestFiles.put(VP9, "bbb_960x540_3mbps_60fps_vp9.webm");
        m540p60FpsTestFiles.put(AV1, "bbb_960x540_3mbps_60fps_av1.mp4");

        m1080p60FpsTestFiles.put(AVC, "bbb_1920x1080_8mbps_60fps_avc.mp4");
        m1080p60FpsTestFiles.put(HEVC, "bbb_1920x1080_6mbps_60fps_hevc.mp4");
        m1080p60FpsTestFiles.put(VP8, "bbb_1920x1080_8mbps_60fps_vp8.webm");
        m1080p60FpsTestFiles.put(VP9, "bbb_1920x1080_6mbps_60fps_vp9.webm");
        m1080p60FpsTestFiles.put(AV1, "bbb_1920x1080_6mbps_60fps_av1.mp4");

        m2160p60FpsTestFiles.put(AVC, "bbb_3840x2160_24mbps_60fps_avc.mp4");
        m2160p60FpsTestFiles.put(HEVC, "bbb_3840x2160_18mbps_60fps_hevc.mkv");
        m2160p60FpsTestFiles.put(VP8, "bbb_3840x2160_24mbps_60fps_vp8.webm");
        m2160p60FpsTestFiles.put(VP9, "bbb_3840x2160_18mbps_60fps_vp9.webm");
        // Limit AV1 4k tests to 1080p as per PC14 requirements
        m2160p60FpsTestFiles.put(AV1, "bbb_1920x1080_6mbps_60fps_av1.mp4");

        m540p30FpsTestFiles.put(AVC, "bbb_960x540_2mbps_30fps_avc.mp4");
        m540p30FpsTestFiles.put(HEVC, "bbb_960x540_2mbps_30fps_hevc.mp4");
        m540p30FpsTestFiles.put(VP8, "bbb_960x540_2mbps_30fps_vp8.webm");
        m540p30FpsTestFiles.put(VP9, "bbb_960x540_2mbps_30fps_vp9.webm");
        m540p30FpsTestFiles.put(AV1, "bbb_960x540_2mbps_30fps_av1.mp4");

        m1080p30FpsTestFiles.put(AVC, "bbb_1920x1080_6mbps_30fps_avc.mp4");
        m1080p30FpsTestFiles.put(HEVC, "bbb_1920x1080_4mbps_30fps_hevc.mp4");
        m1080p30FpsTestFiles.put(VP8, "bbb_1920x1080_6mbps_30fps_vp8.webm");
        m1080p30FpsTestFiles.put(VP9, "bbb_1920x1080_4mbps_30fps_vp9.webm");
        m1080p30FpsTestFiles.put(AV1, "bbb_1920x1080_4mbps_30fps_av1.mp4");

        switch (Utils.getPerfClass()) {
            case Build.VERSION_CODES.TIRAMISU:
                MAX_FRAME_DROP_FOR_30S = MAX_FRAME_DROP_FOR_30S_60FPS_PC_T;
                break;
            case Build.VERSION_CODES.S:
                MAX_FRAME_DROP_FOR_30S = MAX_FRAME_DROP_FOR_30S_60FPS_PC_S;
                break;
            case Build.VERSION_CODES.R:
            default:
                MAX_FRAME_DROP_FOR_30S = MAX_FRAME_DROP_FOR_30S_30FPS_PC_R;
                break;
        }
    }

    private static boolean initAacDecoderName() {
        ArrayList<String> listOfAacDecoders = selectCodecs(AAC, null, null, false);
        if (listOfAacDecoders.isEmpty()) {
            return false;
        }
        AAC_DECODER_NAME = listOfAacDecoders.getFirst();
        return true;
    }

    private static boolean initHwAvcEncoderName() {
        ArrayList<String> listOfAvcHwEncoders = selectHardwareCodecs(AVC, null, null, true);
        if (listOfAvcHwEncoders.isEmpty()) {
            return false;
        }
        AVC_ENCODER_NAME = listOfAvcHwEncoders.getFirst();
        return true;
    }

    private static boolean initHwAvcDecoderName() {
        ArrayList<String> listOfAvcHwDecoders = selectHardwareCodecs(AVC, null, null, false);
        if (listOfAvcHwDecoders.isEmpty()) {
            return false;
        }
        AVC_DECODER_NAME = listOfAvcHwDecoders.getFirst();
        return true;
    }

    public static final Precondition REQUIRES_AAC_DECODER =
            Precondition.create("Test requires aac decoder", FrameDropTestBase::initAacDecoderName);

    public static final Precondition AVC_PRE_CONDITIONS =
            Precondition.inOrder(
                    Precondition.create(
                            "Test requires h/w avc decoder",
                            FrameDropTestBase::initHwAvcEncoderName),
                    Precondition.create(
                            "Test requires h/w avc encoder",
                            FrameDropTestBase::initHwAvcDecoderName),
                    Precondition.create(
                            "The device doesn't support running at least four 1920x1080 avc"
                                    + " instances concurrently",
                            Utils.MEETS_AVC_CODEC_PRECONDITIONS));

    @Rule(order = 2)
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        createSurface();
        startLoad();
    }

    @After
    public void tearDown() throws Exception {
        stopLoad();
        releaseSurface();
    }

    public FrameDropTestBase(String mediaType, String decoderName, boolean isAsync) {
        mMediaType = mediaType;
        mDecoderName = decoderName;
        mIsAsync = isAsync;
    }

    // Returns the list of objects with mediaTypes and their hardware decoders supporting the
    // given features combining with sync and async modes.
    static List<Object[]> prepareArgumentsList(String[] features) {
        final List<Object[]> argsList = new ArrayList<>();
        final String[] mediaTypesList = new String[] {AVC, HEVC, VP8, VP9, AV1};
        for (String mediaType : mediaTypesList) {
            if (mediaTypePrefix != null && !mediaType.startsWith(mediaTypePrefix)) {
                continue;
            }
            MediaFormat format = MediaFormat.createVideoFormat(mediaType, 1920, 1080);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            ArrayList<MediaFormat> formats = new ArrayList<>();
            formats.add(format);
            ArrayList<String> listOfDecoders =
                    selectHardwareCodecs(mediaType, formats, features, false);
            for (String decoder : listOfDecoders) {
                if ((codecPrefix != null && !decoder.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(decoder).matches())) {
                    continue;
                }
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mediaType, decoder, isAsync});
                }
            }
        }
        return argsList;
    }

    protected int getAchievedPerfClass(int frameRate, int frameDropCount) {
        int pc = 0;
        if (frameRate == 30) {
            pc = frameDropCount <= MAX_FRAME_DROP_FOR_30S_30FPS_PC_R ? Build.VERSION_CODES.R : 0;
        } else {
            pc = frameDropCount <= MAX_FRAME_DROP_FOR_30S_60FPS_PC_T ? Build.VERSION_CODES.TIRAMISU
                    : frameDropCount <= MAX_FRAME_DROP_FOR_30S_60FPS_PC_S ? Build.VERSION_CODES.S
                    : 0;
        }
        return pc;
    }

    private void createSurface() throws InterruptedException {
        mActivityRule.getActivity().waitTillSurfaceIsCreated();
        mSurface = mActivityRule.getActivity().getSurface();
        assertTrue("Surface created is null.", mSurface != null);
        assertTrue("Surface created is invalid.", mSurface.isValid());
        // As we display 1920x1080 and 960x540 only which are of same aspect ratio, we will
        // be setting screen params to 1920x1080
        mActivityRule.getActivity().setScreenParams(1920, 1080, true);
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    private Thread createTranscodeLoad() {
        Thread transcodeLoadThread = new Thread(() -> {
            try {
                TranscodeLoad transcodeLoad = new TranscodeLoad(AVC, AVC_LOAD_FILE_NAME,
                        AVC_DECODER_NAME, AVC_ENCODER_NAME, mLoadStatus);
                transcodeLoad.doTranscode();
            } catch (Exception e) {
                mTranscodeLoadException = e;
            }
        });
        return transcodeLoadThread;
    }

    private Thread createAudioPlaybackLoad() {
        Thread audioPlaybackLoadThread = new Thread(() -> {
            try {
                AudioPlaybackLoad audioPlaybackLoad = new AudioPlaybackLoad(AAC, AAC_LOAD_FILE_NAME,
                        AAC_DECODER_NAME, mLoadStatus);
                audioPlaybackLoad.doDecodeAndPlayback();
            } catch (Exception e) {
                mAudioPlaybackLoadException = e;
            }
        });
        return audioPlaybackLoadThread;
    }

    private void startLoad() {
        // Start Transcode load (Decoder(1080p) + Encoder(720p))
        mLoadStatus = new LoadStatus();
        mTranscodeLoadThread = createTranscodeLoad();
        mTranscodeLoadThread.start();
        // Start 128kbps AAC audio playback
        mAudioPlaybackLoadThread = createAudioPlaybackLoad();
        mAudioPlaybackLoadThread.start();
    }

    private void stopLoad() throws Exception {
        if (mLoadStatus != null) {
            mLoadStatus.setLoadFinished();
        }
        if (mTranscodeLoadThread != null) {
            mTranscodeLoadThread.join();
            mTranscodeLoadThread = null;
        }
        if (mAudioPlaybackLoadThread != null) {
            mAudioPlaybackLoadThread.join();
            mAudioPlaybackLoadThread = null;
        }
        if (mTranscodeLoadException != null) throw mTranscodeLoadException;
        if (mAudioPlaybackLoadException != null) throw mAudioPlaybackLoadException;
        mLoadStatus = null;
    }
}
