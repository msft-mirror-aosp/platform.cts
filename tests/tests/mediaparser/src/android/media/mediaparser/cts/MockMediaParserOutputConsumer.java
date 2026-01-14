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

package android.media.mediaparser.cts;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaParser;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DataReader;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MockMediaParserOutputConsumer implements MediaParser.OutputConsumer {

    private final boolean mUsingInBandCryptoInfo;
    private final FakeExtractorOutput mFakeExtractorOutput;
    private final Map<Integer, TrackOutput> mTrackOutputs;

    @Nullable private MediaParser.SeekMap mSeekMap;
    private int mCompletedSampleCount;
    @Nullable private MediaCodec.CryptoInfo mLastOutputCryptoInfo;
    private int mTrackCount;

    public MockMediaParserOutputConsumer() {
        this(/* usingInBandCryptoInfo= */ false);
    }

    public MockMediaParserOutputConsumer(boolean usingInBandCryptoInfo) {
        mUsingInBandCryptoInfo = usingInBandCryptoInfo;
        mFakeExtractorOutput =
                new FakeExtractorOutput(
                        /* trackOutputFactory= */ (id, type) ->
                                new FakeTrackOutput(
                                        type, /* deduplicateConsecutiveFormats= */ true));
        mTrackOutputs = new HashMap<>();
    }

    public int getCompletedSampleCount() {
        return mCompletedSampleCount;
    }

    @Nullable
    public MediaCodec.CryptoInfo getLastOutputCryptoInfo() {
        return mLastOutputCryptoInfo;
    }

    public void clearTrackOutputs() {
        mFakeExtractorOutput.clearTrackOutputs();
    }

    // OutputConsumer implementation.

    @Override
    public void onSeekMapFound(MediaParser.SeekMap seekMap) {
        mSeekMap = seekMap;
        mFakeExtractorOutput.seekMap(
                new SeekMap() {
                    @Override
                    public boolean isSeekable() {
                        return seekMap.isSeekable();
                    }

                    @Override
                    public long getDurationUs() {
                        long durationUs = seekMap.getDurationMicros();
                        return durationUs != MediaParser.SeekMap.UNKNOWN_DURATION
                                ? durationUs
                                : C.TIME_UNSET;
                    }

                    @Override
                    public SeekPoints getSeekPoints(long timeUs) {
                        return toMedia3SeekPoints(seekMap.getSeekPoints(timeUs));
                    }
                });
    }

    @Override
    public void onTrackCountFound(int numberOfTracks) {
        mTrackCount = numberOfTracks;
    }

    @Override
    public void onTrackDataFound(int trackIndex, MediaParser.TrackData trackData) {
        if (!mTrackOutputs.containsKey(trackIndex)) {
            mTrackOutputs.put(trackIndex, mFakeExtractorOutput.track(trackIndex, C.TRACK_TYPE_UNKNOWN));
        }
        mTrackOutputs.get(trackIndex).format(toMedia3Format(trackData));
    }

    @Override
    public void onSampleDataFound(int trackIndex, MediaParser.InputReader inputReader)
            throws IOException {
        mFakeExtractorOutput
                .track(trackIndex, C.TRACK_TYPE_UNKNOWN)
                .sampleData(
                        new DataReaderAdapter(inputReader), (int) inputReader.getLength(), false);
    }

    @Override
    public void onSampleCompleted(
            int trackIndex,
            long timeUs,
            int flags,
            int size,
            int offset,
            @Nullable MediaCodec.CryptoInfo cryptoInfo) {
        mCompletedSampleCount++;
        if (!mUsingInBandCryptoInfo) {
            mLastOutputCryptoInfo = cryptoInfo;
        }
    }

    // Internal methods.

    private static SeekMap.SeekPoints toMedia3SeekPoints(
            Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> seekPoints) {
        return new SeekMap.SeekPoints(
                toMedia3SeekPoint(seekPoints.first), toMedia3SeekPoint(seekPoints.second));
    }

    private static SeekPoint toMedia3SeekPoint(MediaParser.SeekPoint seekPoint) {
        return new SeekPoint(seekPoint.timeMicros, seekPoint.position);
    }

    private static Format toMedia3Format(MediaParser.TrackData trackData) {
        MediaFormat mediaFormat = trackData.mediaFormat;
        String sampleMimeType =
                mediaFormat.getString(MediaFormat.KEY_MIME, /* defaultValue= */ null);
        String id =
                mediaFormat.containsKey(MediaFormat.KEY_TRACK_ID)
                        ? String.valueOf(mediaFormat.getInteger(MediaFormat.KEY_TRACK_ID))
                        : null;
        String codecs =
                mediaFormat.getString(MediaFormat.KEY_CODECS_STRING, /* defaultValue= */ null);
        int bitrate =
                mediaFormat.getInteger(
                        MediaFormat.KEY_BIT_RATE, /* defaultValue= */ Format.NO_VALUE);
        int maxInputSize =
                mediaFormat.getInteger(
                        MediaFormat.KEY_MAX_INPUT_SIZE, /* defaultValue= */ Format.NO_VALUE);
        int width =
                mediaFormat.getInteger(MediaFormat.KEY_WIDTH, /* defaultValue= */ Format.NO_VALUE);
        int height =
                mediaFormat.getInteger(MediaFormat.KEY_HEIGHT, /* defaultValue= */ Format.NO_VALUE);
        float frameRate =
                mediaFormat.getFloat(
                        MediaFormat.KEY_FRAME_RATE, /* defaultValue= */ Format.NO_VALUE);
        int rotationDegrees =
                mediaFormat.getInteger(
                        MediaFormat.KEY_ROTATION, /* defaultValue= */ Format.NO_VALUE);
        ArrayList<byte[]> initData = null;
        if (mediaFormat.containsKey("csd-0")) {
            initData = new ArrayList<>();
            int index = 0;
            while (mediaFormat.containsKey("csd-" + index)) {
                initData.add(mediaFormat.getByteBuffer("csd-" + index++).array());
            }
        }
        float pixelAspectWidth =
                (float)
                        mediaFormat.getInteger(
                                MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH, /* defaultValue= */ 0);
        float pixelAspectHeight =
                (float)
                        mediaFormat.getInteger(
                                MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT, /* defaultValue= */ 0);
        float pixelAspectRatio =
                pixelAspectHeight == 0 || pixelAspectWidth == 0
                        ? Format.NO_VALUE
                        : pixelAspectWidth / pixelAspectHeight;
        ColorInfo colorInfo = getMedia3ColorInfo(mediaFormat);
        DrmInitData drmInitData =
                getMedia3DrmInitData(
                        mediaFormat.getString("crypto-mode-fourcc"), trackData.drmInitData);

        int selectionFlags =
                mediaFormat.getInteger(MediaFormat.KEY_IS_AUTOSELECT, /* defaultValue= */ 0) != 0
                        ? C.SELECTION_FLAG_AUTOSELECT
                        : 0;
        selectionFlags |=
                mediaFormat.getInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE, /* defaultValue= */ 0)
                                != 0
                        ? C.SELECTION_FLAG_FORCED
                        : 0;
        selectionFlags |=
                mediaFormat.getInteger(MediaFormat.KEY_IS_DEFAULT, /* defaultValue= */ 0) != 0
                        ? C.SELECTION_FLAG_DEFAULT
                        : 0;

        String language = mediaFormat.getString(MediaFormat.KEY_LANGUAGE, /* defaultValue= */ null);
        int channels =
                mediaFormat.getInteger(
                        MediaFormat.KEY_CHANNEL_COUNT, /* defaultValue= */ Format.NO_VALUE);
        int sampleRate =
                mediaFormat.getInteger(
                        MediaFormat.KEY_SAMPLE_RATE, /* defaultValue= */ Format.NO_VALUE);
        int accessibilityChannel =
                mediaFormat.getInteger(
                        MediaFormat.KEY_CAPTION_SERVICE_NUMBER,
                        /* defaultValue= */ Format.NO_VALUE);

        return new Format.Builder()
                .setId(id)
                .setSampleMimeType(sampleMimeType)
                .setCodecs(codecs)
                .setPeakBitrate(bitrate)
                .setMaxInputSize(maxInputSize)
                .setWidth(width)
                .setHeight(height)
                .setFrameRate(frameRate)
                .setInitializationData(initData)
                .setRotationDegrees(rotationDegrees)
                .setPixelWidthHeightRatio(pixelAspectRatio)
                .setColorInfo(colorInfo)
                .setDrmInitData(drmInitData)
                .setChannelCount(channels)
                .setSampleRate(sampleRate)
                .setSelectionFlags(selectionFlags)
                .setLanguage(language)
                .setAccessibilityChannel(accessibilityChannel)
                .build();
    }

    @Nullable
    private static DrmInitData getMedia3DrmInitData(
            @Nullable String encryptionScheme, @Nullable android.media.DrmInitData drmInitData) {
        if (drmInitData == null) {
            return null;
        }
        DrmInitData.SchemeData[] schemeDatas =
                new DrmInitData.SchemeData[drmInitData.getSchemeInitDataCount()];
        for (int i = 0; i < schemeDatas.length; i++) {
            android.media.DrmInitData.SchemeInitData schemeInitData =
                    drmInitData.getSchemeInitDataAt(i);
            schemeDatas[i] =
                    new DrmInitData.SchemeData(
                            schemeInitData.uuid, schemeInitData.mimeType, schemeInitData.data);
        }
        return new DrmInitData(encryptionScheme, schemeDatas);
    }

    private static ColorInfo getMedia3ColorInfo(MediaFormat mediaFormat) {
        int colorSpace = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)) {
                case MediaFormat.COLOR_STANDARD_BT601_NTSC:
                case MediaFormat.COLOR_STANDARD_BT601_PAL:
                    colorSpace = C.COLOR_SPACE_BT601;
                    break;
                case MediaFormat.COLOR_STANDARD_BT709:
                    colorSpace = C.COLOR_SPACE_BT709;
                    break;
                case MediaFormat.COLOR_STANDARD_BT2020:
                    colorSpace = C.COLOR_SPACE_BT2020;
                    break;
                default:
                    colorSpace = Format.NO_VALUE;
            }
        }

        int colorRange = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE)) {
                case MediaFormat.COLOR_RANGE_FULL:
                    colorRange = C.COLOR_RANGE_FULL;
                    break;
                case MediaFormat.COLOR_RANGE_LIMITED:
                    colorRange = C.COLOR_RANGE_LIMITED;
                    break;
                default:
                    colorRange = Format.NO_VALUE;
            }
        }

        int colorTransfer = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                case MediaFormat.COLOR_TRANSFER_HLG:
                    colorTransfer = C.COLOR_TRANSFER_HLG;
                    break;
                case MediaFormat.COLOR_TRANSFER_SDR_VIDEO:
                    colorTransfer = C.COLOR_TRANSFER_SDR;
                    break;
                case MediaFormat.COLOR_TRANSFER_ST2084:
                    colorTransfer = C.COLOR_TRANSFER_ST2084;
                    break;
                case MediaFormat.COLOR_TRANSFER_LINEAR:
                // Fall through, there's no mapping.
                default:
                    colorTransfer = Format.NO_VALUE;
            }
        }
        boolean hasHdrInfo = mediaFormat.containsKey(MediaFormat.KEY_HDR_STATIC_INFO);
        if (colorSpace == Format.NO_VALUE
                && colorRange == Format.NO_VALUE
                && colorTransfer == Format.NO_VALUE
                && !hasHdrInfo) {
            return null;
        } else {
            ColorInfo.Builder colorInfoBuilder =
                    new ColorInfo.Builder()
                            .setColorSpace(colorSpace)
                            .setColorRange(colorRange)
                            .setColorTransfer(colorTransfer);
            if (hasHdrInfo) {
                colorInfoBuilder.setHdrStaticInfo(
                        mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO).array());
            }
            return colorInfoBuilder.build();
        }
    }

    public MediaParser.SeekMap getSeekMap() {
        return mSeekMap;
    }

    public int getTrackCount() {
        return mTrackCount;
    }

    public Set<Integer> getTrackIndices() {
        return mTrackOutputs.keySet();
    }

    // Internal classes.

    private static class DataReaderAdapter implements DataReader {

        private final MediaParser.InputReader mInputReader;

        private DataReaderAdapter(MediaParser.InputReader inputReader) {
            mInputReader = inputReader;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            return mInputReader.read(target, offset, length);
        }
    }
}
