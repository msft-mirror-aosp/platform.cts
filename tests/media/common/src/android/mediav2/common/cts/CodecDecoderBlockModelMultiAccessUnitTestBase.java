/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.mediav2.common.cts;

import static android.mediav2.common.cts.CodecDecoderMultiAccessUnitTestBase.getCompressionRatio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import com.android.media.codec.flags.Flags;

import org.junit.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Wrapper class for trying and testing mediacodec decoder components in large buffer block model
 * mode
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled(Flags.FLAG_LARGE_AUDIO_FRAME)
public class CodecDecoderBlockModelMultiAccessUnitTestBase extends CodecDecoderBlockModelTestBase {
    private static final String LOG_TAG =
            CodecDecoderBlockModelMultiAccessUnitTestBase.class.getSimpleName();

    protected final CodecAsyncHandlerMultiAccessUnits mAsyncHandleMultiAccessUnits =
            new CodecAsyncHandlerMultiAccessUnits();
    protected int mMaxInputSize;
    protected int mMaxOutputSizeBytes;
    protected int mMaxInputLimitMs;

    public CodecDecoderBlockModelMultiAccessUnitTestBase(String decoder, String mediaType,
            String testFile, String allTestParams) {
        super(decoder, mediaType, testFile, allTestParams);
        mAsyncHandle = mAsyncHandleMultiAccessUnits;
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mMaxOutputSizeBytes = 0;
        mMaxInputLimitMs = 0;
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        ArrayDeque<MediaCodec.BufferInfo> infos = new ArrayDeque<>();
        mLinearInputBlock.allocateBlock(mCodecName, mMaxInputSize);
        int basePts = (int) mExtractor.getSampleTime();
        while (true) {
            int size = (int) mExtractor.getSampleSize();
            if (size <= 0) break;
            int deltaPts = (int) mExtractor.getSampleTime() - basePts;
            assertTrue("Difference between basePts: " + basePts + " and current pts: "
                    + mExtractor.getSampleTime() + " should be greater than or equal "
                    + "to zero.\n" + mTestConfig + mTestEnv, deltaPts >= 0);
            if (deltaPts / 1000 > mMaxInputLimitMs) {
                break;
            }
            if (mLinearInputBlock.getOffset() + size <= mLinearInputBlock.getBufferCapacity()) {
                mExtractor.readSampleData(mLinearInputBlock.getBuffer(),
                        mLinearInputBlock.getOffset());
            } else {
                if (mLinearInputBlock.getOffset() == 0) {
                    throw new RuntimeException(String.format(Locale.getDefault(),
                            "access unit size %d exceeds capacity of the buffer %d, unable to "
                                    + "queue input", size, mLinearInputBlock.getBufferCapacity()));
                }
                break;
            }
            int extractorFlags = mExtractor.getSampleFlags();
            long pts = mExtractor.getSampleTime();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.set(mLinearInputBlock.getOffset(), size, pts, codecFlags);
            mLinearInputBlock.setOffset(mLinearInputBlock.getOffset() + bufferInfo.size);
            infos.add(bufferInfo);
        }
        if (infos.size() > 0) {
            MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
            request.setMultiFrameLinearBlock(mLinearInputBlock.getBlock(), infos);
            request.queue();
            for (MediaCodec.BufferInfo info : infos) {
                if (info.size > 0 && (info.flags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                    mOutputBuff.saveInPTS(info.presentationTimeUs);
                    mInputCount++;
                }
                if (ENABLE_LOGS) {
                    Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + info.size
                            + " pts: " + info.presentationTimeUs + " flags: " + info.flags);
                }
            }
        }
    }

    private void validateOutputFormat(MediaFormat outFormat) {
        Assert.assertTrue("Output format " + outFormat + " does not contain key "
                        + MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE + ". \n"
                        + mTestConfig + mTestEnv,
                outFormat.containsKey(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE));
        mMaxOutputSizeBytes = outFormat.getInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE);
    }

    private void dequeueOutputs(int bufferIndex, ArrayDeque<MediaCodec.BufferInfo> infos) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex);
        }
        if (mOutputCount == 0) {
            validateOutputFormat(mCodec.getOutputFormat(bufferIndex));
        }
        MediaCodec.OutputFrame frame = mCodec.getOutputFrame(bufferIndex);
        ByteBuffer buf = frame.getLinearBlock() != null ? frame.getLinearBlock().map() : null;
        MediaCodec.BufferInfo[] frameInfos =
                frame.getBufferInfos().toArray(new MediaCodec.BufferInfo[0]);
        MediaCodec.BufferInfo[] callBackInfos = infos.toArray(new MediaCodec.BufferInfo[0]);
        assertEquals("Lengths of frameInfos received via callback and getBufferInfos api are not "
                        + "identical. \n" + mTestConfig + mTestEnv, frameInfos.length,
                callBackInfos.length);

        int totalSize = 0;
        for (int i = 0; i < frameInfos.length; ++i) {
            MediaCodec.BufferInfo frameInfo = frameInfos[i];
            MediaCodec.BufferInfo callBackInfo = callBackInfos[i];
            Assert.assertNotNull("received null entry in dequeueOutput frame infos list. \n"
                    + mTestConfig + mTestEnv, frameInfo);
            Assert.assertNotNull("received null entry in dequeueOutput callback infos list. \n"
                    + mTestConfig + mTestEnv, callBackInfo);

            long framePts = frameInfo.presentationTimeUs;
            long infoPts = callBackInfo.presentationTimeUs;
            int frameFlags = frameInfo.flags;
            int infoFlags = callBackInfo.flags;
            assertEquals("presentation timestamps from OutputFrame does not match with the value "
                    + "obtained from callback: framePts=" + framePts + ", infoPts=" + infoPts
                    + "\n" + mTestConfig + mTestEnv, framePts, infoPts);
            assertEquals("Flags from OutputFrame does not match with the value obtained from "
                    + "callback: frameFlags=" + frameFlags + ", infoFlags=" + infoFlags + "\n"
                    + mTestConfig + mTestEnv, frameFlags, infoFlags);
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "flags: " + infoFlags + " size: " + callBackInfo.size
                        + " timestamp: " + infoPts);
            }
            if ((infoFlags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSawOutputEOS = true;
            }
            if (callBackInfo.size > 0) {
                if (buf != null && mSaveToMem) {
                    mOutputBuff.saveToMemory(buf, callBackInfo);
                }
                if ((infoFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    mOutputBuff.saveOutPTS(infoPts);
                    mOutputCount++;
                }
            }
            totalSize += callBackInfo.size;
        }
        if (frame.getLinearBlock() != null) {
            frame.getLinearBlock().recycle();
        }
        assertTrue("Sum of all info sizes: " + totalSize + " exceeds max output size: "
                        + mMaxOutputSizeBytes + " \n" + mTestConfig + mTestEnv,
                totalSize <= mMaxOutputSizeBytes);
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void doWork(int frameLimit) throws InterruptedException, IOException {
        // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
        while (!mAsyncHandleMultiAccessUnits.hasSeenError() && !mSawInputEOS
                && mInputCount < frameLimit) {
            Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element =
                    mAsyncHandleMultiAccessUnits.getWorkList();
            if (element != null) {
                int bufferID = element.first;
                ArrayDeque<MediaCodec.BufferInfo> infos = element.second;
                if (infos != null) {
                    // <id, infos> corresponds to output callback. Handle it accordingly
                    dequeueOutputs(bufferID, infos);
                } else {
                    // <id, null> corresponds to input callback. Handle it accordingly
                    enqueueInput(bufferID);
                }
            }
        }
    }

    @Override
    protected void waitForAllOutputs() throws InterruptedException {
        while (!mAsyncHandleMultiAccessUnits.hasSeenError() && !mSawOutputEOS) {
            Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element =
                    mAsyncHandleMultiAccessUnits.getOutputs();
            if (element != null) {
                dequeueOutputs(element.first, element.second);
            }
        }
        validateTestState();
    }

    protected void configureKeysForLargeAudioBlockModelFrameMode(MediaFormat format,
            int maxInputSizeInBytes, int maxOutSizeInMs, int thresOutSizeInMs) {
        int bytesPerSample = AudioFormat.getBytesPerSample(
                format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT));
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int maxOutputSize = (maxOutSizeInMs * bytesPerSample * sampleRate * channelCount) / 1000;
        mMaxInputSize = Math.max(maxInputSizeInBytes,
                (int) (maxOutputSize * getCompressionRatio(mMediaType)));
        int thresholdOutputSize =
                (thresOutSizeInMs * bytesPerSample * sampleRate * channelCount) / 1000;
        format.setInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE, maxOutputSize);
        format.setInteger(MediaFormat.KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE,
                thresholdOutputSize);
    }
}