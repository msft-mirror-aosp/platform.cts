/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaFormat;
import android.media.MediaParser;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.TestUtil;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class MediaParserTest {

    @Test
    public void testGetAllParserNames() {
        MediaFormat format = new MediaFormat();
        // By not providing a mime type, MediaParser should return all parser names.
        format.setString(MediaFormat.KEY_MIME, null);
        assertThat(MediaParser.getParserNames(format))
                .containsExactly(
                        MediaParser.PARSER_NAME_MATROSKA,
                        MediaParser.PARSER_NAME_FMP4,
                        MediaParser.PARSER_NAME_MP4,
                        MediaParser.PARSER_NAME_MP3,
                        MediaParser.PARSER_NAME_ADTS,
                        MediaParser.PARSER_NAME_AC3,
                        MediaParser.PARSER_NAME_TS,
                        MediaParser.PARSER_NAME_FLV,
                        MediaParser.PARSER_NAME_OGG,
                        MediaParser.PARSER_NAME_PS,
                        MediaParser.PARSER_NAME_WAV,
                        MediaParser.PARSER_NAME_AMR,
                        MediaParser.PARSER_NAME_AC4,
                        MediaParser.PARSER_NAME_FLAC);
    }

    @Test
    public void testGetParserNamesByMimeType() {
        // MimeTypes obtained from the W3C.
        assertParsers(MediaParser.PARSER_NAME_MATROSKA)
                .supportMimeTypes(
                        "video/x-matroska", "audio/x-matroska", "video/x-webm", "audio/x-webm");
        assertParsers(MediaParser.PARSER_NAME_MP4, MediaParser.PARSER_NAME_FMP4)
                .supportMimeTypes("video/mp4", "audio/mp4", "application/mp4");
        assertParsers(MediaParser.PARSER_NAME_MP3).supportMimeTypes("audio/mpeg");
        assertParsers(MediaParser.PARSER_NAME_ADTS).supportMimeTypes("audio/aac");
        assertParsers(MediaParser.PARSER_NAME_AC3).supportMimeTypes("audio/ac3");
        assertParsers(MediaParser.PARSER_NAME_TS).supportMimeTypes("video/mp2t", "audio/mp2t");
        assertParsers(MediaParser.PARSER_NAME_FLV).supportMimeTypes("video/x-flv");
        assertParsers(MediaParser.PARSER_NAME_OGG)
                .supportMimeTypes("video/ogg", "audio/ogg", "application/ogg");
        assertParsers(MediaParser.PARSER_NAME_PS).supportMimeTypes("video/mp2p", "video/mp1s");
        assertParsers(MediaParser.PARSER_NAME_WAV)
                .supportMimeTypes("audio/vnd.wave", "audio/wav", "audio/wave", "audio/x-wav");
        assertParsers(MediaParser.PARSER_NAME_AMR).supportMimeTypes("audio/amr");
        assertParsers(MediaParser.PARSER_NAME_AC4).supportMimeTypes("audio/ac4");
        assertParsers(MediaParser.PARSER_NAME_FLAC).supportMimeTypes("audio/flac", "audio/x-flac");
    }

    @Test
    public void testGetParserNamesForUnsupportedMimeType() {
        MediaFormat format = new MediaFormat();
        // None of the parser supports WebVTT.
        format.setString(MediaFormat.KEY_MIME, "text/vtt");
        assertThat(MediaParser.getParserNames(format)).isEmpty();
    }

    @Test
    public void testCreationByName() {
        testCreationByName(MediaParser.PARSER_NAME_MATROSKA);
        testCreationByName(MediaParser.PARSER_NAME_FMP4);
        testCreationByName(MediaParser.PARSER_NAME_MP4);
        testCreationByName(MediaParser.PARSER_NAME_MP3);
        testCreationByName(MediaParser.PARSER_NAME_ADTS);
        testCreationByName(MediaParser.PARSER_NAME_AC3);
        testCreationByName(MediaParser.PARSER_NAME_TS);
        testCreationByName(MediaParser.PARSER_NAME_FLV);
        testCreationByName(MediaParser.PARSER_NAME_OGG);
        testCreationByName(MediaParser.PARSER_NAME_PS);
        testCreationByName(MediaParser.PARSER_NAME_WAV);
        testCreationByName(MediaParser.PARSER_NAME_AMR);
        testCreationByName(MediaParser.PARSER_NAME_AC4);
        testCreationByName(MediaParser.PARSER_NAME_FLAC);
        try {
            testCreationByName("android.media.mediaparser.ExtractorThatDoesNotExist");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    // OGG.

    @Test
    public void testBearVorbisOgg() throws IOException, InterruptedException {
        testExtractAsset("ogg/bear_vorbis.ogg");
    }

    @Test
    public void testBearOgg() throws IOException, InterruptedException {
        testExtractAsset("ogg/bear.opus");
    }

    @Test
    public void testBearFlacOgg() throws IOException, InterruptedException {
        testExtractAsset("ogg/bear_flac.ogg");
    }

    @Test
    public void testNoFlacSeekTableOgg() throws IOException, InterruptedException {
        testExtractAsset("ogg/bear_flac_noseektable.ogg");
    }

    @Test
    public void testFlacHeaderOggSniff() throws IOException, InterruptedException {
        testSniffAsset("ogg/flac_header", /* expectedExtractorName= */ MediaParser.PARSER_NAME_OGG);
    }

    @Test
    public void testOpusHeaderOggSniff() throws IOException, InterruptedException {
        try {
            testSniffAsset(
                    "ogg/opus_header", /* expectedExtractorName= */ MediaParser.PARSER_NAME_OGG);
            Assert.fail();
        } catch (MediaParser.UnrecognizedInputFormatException e) {
            // Expected.
        }
    }

    @Test
    public void testInvalidHeaderOggSniff() throws IOException, InterruptedException {
        try {
            testSniffAsset(
                    "ogg/invalid_ogg_header",
                    /* expectedExtractorName= */ MediaParser.PARSER_NAME_OGG);
            Assert.fail();
        } catch (MediaParser.UnrecognizedInputFormatException e) {
            // Expected.
        }
    }

    @Test
    public void testInvalidHeaderSniff() throws IOException, InterruptedException {
        try {
            testSniffAsset(
                    "ogg/invalid_header", /* expectedExtractorName= */ MediaParser.PARSER_NAME_OGG);
            Assert.fail();
        } catch (MediaParser.UnrecognizedInputFormatException e) {
            // Expected.
        }
    }

    // FLAC.

    @Test
    public void testBearUncommonSampleRateFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_uncommon_sample_rate.flac");
    }

    @Test
    public void testBearNoSeekTableAndNoNumSamplesFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_no_seek_table_no_num_samples.flac");
    }

    @Test
    public void testBearWithPictureFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_with_picture.flac");
    }

    @Test
    public void testBearWithVorbisCommentsFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_with_vorbis_comments.flac");
    }

    @Test
    public void testOneMetadataBlockFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_one_metadata_block.flac");
    }

    @Test
    public void testBearNoMinMaxFrameSizeFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_no_min_max_frame_size.flac");
    }

    @Test
    public void testNoNumSamplesFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_no_num_samples.flac");
    }

    @Test
    public void testBearNoId3Flac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_with_id3_disabled.flac");
    }

    @Test
    public void testBearWithId3Flac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear_with_id3_enabled.flac");
    }

    @Test
    public void testBearFlac() throws IOException, InterruptedException {
        testExtractAsset("flac/bear.flac");
    }

    // MP3.

    @Test
    public void testTrimmedMp3() throws IOException, InterruptedException {
        testExtractAsset("mp3/play-trimmed.mp3");
    }

    @Test
    public void testBearMp3() throws IOException, InterruptedException {
        testExtractAsset("mp3/bear.mp3");
    }

    // WAV.

    @Test
    public void testWavWithImaAdpcm() throws IOException, InterruptedException {
        testExtractAsset("wav/sample_ima_adpcm.wav");
    }

    @Test
    public void testWav() throws IOException, InterruptedException {
        testExtractAsset("wav/sample.wav");
    }

    // AMR.

    @Test
    public void testNarrowBandSamplesWithConstantBitrateSeeking()
            throws IOException, InterruptedException {
        testExtractAsset("amr/sample_nb_cbr.amr");
    }

    @Test
    public void testNarrowBandSamples() throws IOException, InterruptedException {
        testExtractAsset("amr/sample_nb.amr");
    }

    @Test
    public void testWideBandSamples() throws IOException, InterruptedException {
        testExtractAsset("amr/sample_wb.amr");
    }

    @Test
    public void testWideBandSamplesWithConstantBitrateSeeking()
            throws IOException, InterruptedException {
        testExtractAsset("amr/sample_wb_cbr.amr");
    }

    // FLV.

    @Test
    public void testFlv() throws IOException, InterruptedException {
        testExtractAsset("flv/sample.flv");
    }

    // PS.

    // TODO: Enable once the timeout is fixed.
    @Test
    @Ignore
    public void testElphantsDreamPs() throws IOException, InterruptedException {
        testExtractAsset("ts/elephants_dream.mpg");
    }

    @Test
    public void testProgramStream() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.ps");
    }

    // ADTS.

    @Test
    public void testTruncatedAdtsWithConstantBitrateSeeking()
            throws IOException, InterruptedException {
        testExtractAsset("ts/sample_cbs_truncated.adts");
    }

    @Test
    public void testAdts() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.adts");
    }

    @Test
    public void testAdtsWithConstantBitrateSeeking() throws IOException, InterruptedException {
        testExtractAsset("ts/sample_cbs.adts");
    }

    // AC-3.

    @Test
    public void testAc3() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.ac3");
    }

    // AC-4.

    @Test
    public void testAc4() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.ac4");
    }

    // EAC-3.

    @Test
    public void testEac3() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.eac3");
    }

    // TS.

    @Test
    public void testBigBuckBunnyTs() throws IOException, InterruptedException {
        testExtractAsset("ts/bbb_2500ms.ts");
    }

    @Test
    public void testTransportStream() throws IOException, InterruptedException {
        testExtractAsset("ts/sample.ts");
    }

    @Test
    public void testTransportStreamWithSdt() throws IOException, InterruptedException {
        testExtractAsset("ts/sample_with_sdt.ts");
    }

    // MKV.

    @Test
    public void testSubsampleEncryptedNoAltref() throws IOException, InterruptedException {
        testExtractAsset("mkv/subsample_encrypted_noaltref.webm");
    }

    @Test
    public void testMatroskaFile() throws IOException, InterruptedException {
        testExtractAsset("mkv/sample.mkv");
    }

    @Test
    public void testFullBlocks() throws IOException, InterruptedException {
        testExtractAsset("mkv/full_blocks.mkv");
    }

    @Test
    public void testSubsampleEncryptedAltref() throws IOException, InterruptedException {
        testExtractAsset("mkv/subsample_encrypted_altref.webm");
    }

    // MP4.

    @Test
    public void testAc4Fragmented() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_ac4_fragmented.mp4");
    }

    @Test
    public void testAndrdoidSlowMotion() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_android_slow_motion.mp4");
    }

    @Test
    public void testFragmentedSei() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_fragmented_sei.mp4");
    }

    @Test
    public void testMp4WithAc4() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_ac4.mp4");
    }

    @Test
    public void testFragmentedSeekable() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_fragmented_seekable.mp4");
    }

    @Test
    public void testAc4Protected() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_ac4_protected.mp4");
    }

    @Test
    public void testMp4() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample.mp4");
    }

    @Test
    public void testMdatTooLong() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_mdat_too_long.mp4");
    }

    @Test
    public void testFragmented() throws IOException, InterruptedException {
        testExtractAsset("mp4/sample_fragmented.mp4");
    }

    private static void testCreationByName(String name) {
        MediaParser.createByName(name, new MockMediaParserOutputConsumer(new FakeExtractorOutput()))
                .release();
    }

    private static void testSniffAsset(String assetPath, String expectedParserName)
            throws IOException, InterruptedException {
        extractAsset(assetPath, expectedParserName);
    }

    private static void testExtractAsset(String assetPath)
            throws IOException, InterruptedException {
        extractAsset(assetPath, /* expectedParserName= */ null);
    }

    private static void extractAsset(String assetPath, String expectedParserName)
            throws IOException, InterruptedException {
        byte[] assetBytes =
                TestUtil.getByteArray(
                        InstrumentationRegistry.getInstrumentation().getContext(), assetPath);
        MockMediaParserInputReader mockInput =
                new MockMediaParserInputReader(
                        new FakeExtractorInput.Builder().setData(assetBytes).build());
        MockMediaParserOutputConsumer outputConsumer =
                new MockMediaParserOutputConsumer(new FakeExtractorOutput());
        MediaParser mediaParser = MediaParser.create(outputConsumer);

        mediaParser.advance(mockInput);
        if (expectedParserName != null) {
            assertThat(expectedParserName).isEqualTo(mediaParser.getParserName());
            // We are only checking that the extractor is the right one.
            mediaParser.release();
            return;
        }

        while (mediaParser.advance(mockInput)) {
            // Do nothing.
        }

        // If the SeekMap is seekable, test seeking in the stream.
        MediaParser.SeekMap seekMap = outputConsumer.getSeekMap();
        assertThat(seekMap).isNotNull();
        if (seekMap.isSeekable()) {
            long durationUs = seekMap.getDurationMicros();
            for (int j = 0; j < 4; j++) {
                outputConsumer.clearTrackOutputs();
                long timeUs =
                        durationUs == MediaParser.SeekMap.UNKNOWN_DURATION
                                ? 0
                                : (durationUs * j) / 3;
                MediaParser.SeekPoint seekPoint = seekMap.getSeekPoints(timeUs).first;
                mockInput.reset();
                mockInput.setPosition((int) seekPoint.position);
                mediaParser.seek(seekPoint);
                while (mediaParser.advance(mockInput)) {
                    // Do nothing.
                }
                if (durationUs == MediaParser.SeekMap.UNKNOWN_DURATION) {
                    break;
                }
            }
        }
        mediaParser.release();
    }

    private static FluentMediaParserSubject assertParsers(String... names) {
        return new FluentMediaParserSubject(names);
    }

    private static final class FluentMediaParserSubject {

        private final String[] mediaParserNames;

        private FluentMediaParserSubject(String[] mediaParserNames) {
            this.mediaParserNames = mediaParserNames;
        }

        public void supportMimeTypes(String... mimeTypes) {
            for (String mimeType : mimeTypes) {
                MediaFormat format = new MediaFormat();
                format.setString(MediaFormat.KEY_MIME, mimeType);
                assertThat(MediaParser.getParserNames(format))
                        .containsExactlyElementsIn(mediaParserNames);
            }
        }
    }
}
