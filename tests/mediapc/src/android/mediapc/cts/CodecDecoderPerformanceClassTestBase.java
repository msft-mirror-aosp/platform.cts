/*
 * Copyright (C) 2025 The Android Open Source Project
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


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.util.Log;

import org.junit.After;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Wrapper class for trying and testing decoder components.
 */
public class CodecDecoderPerformanceClassTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG =
            CodecDecoderPerformanceClassTestBase.class.getSimpleName();
    // Widevine Content Protection Identifier https://dashif.org/identifiers/content_protection/
    static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    static final String MEDIA_DIR = WorkDir.getMediaDirString();

    protected final boolean mSecureMode;

    // Callback for each time the output count changes.
    // This can be used to measure codec performance.
    protected Consumer<Integer> mOutputCountListener;
    protected byte[] mSessionID;
    protected MediaDrm mDrm = null;
    protected MediaCrypto mCrypto = null;

    CodecDecoderPerformanceClassTestBase(String mediaType, String testFile, String codecName,
            boolean secureMode) {
        super(codecName, mediaType, MEDIA_DIR + testFile, "params not filled by test suite");
        mSecureMode = secureMode;
    }

    CodecDecoderPerformanceClassTestBase(String mediaType, String testFile, String codecName) {
        this(mediaType, testFile, codecName, false);
    }

    @After
    public void tearDownCodecDecoderPerformanceClassTestBase() {
        if (mCrypto != null) {
            mCrypto.release();
            mCrypto = null;
        }
        if (mDrm != null) {
            mDrm.close();
            mDrm = null;
        }
    }

    // must not be called during doWork
    protected void setOutputCountListener(Consumer<Integer> listener) {
        mOutputCountListener = listener;
    }

    private byte[] openSession(MediaDrm drm) throws InterruptedException {
        byte[] sessionId = null;
        int retryCount = 3;
        while (retryCount-- > 0) {
            try {
                sessionId = drm.openSession();
                break;
            } catch (NotProvisionedException eNotProvisioned) {
                Log.i(LOG_TAG, "Missing certificate, provisioning");
                try {
                    final ProvisionRequester provisionRequester = new ProvisionRequester(drm);
                    provisionRequester.send();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Provisioning fails because " + e.toString());
                }
            } catch (ResourceBusyException eResourceBusy) {
                Log.w(LOG_TAG, "Resource busy in openSession, retrying...");
                Thread.sleep(1000);
            }
        }
        return sessionId;
    }

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder, String serverURL) throws Exception {
        if (mSecureMode && serverURL != null) {
            configureCodecCommon(format, isAsync, signalEOSWithLastFrame, isEncoder, 0);

            if (mDrm == null) {
                mDrm = new MediaDrm(WIDEVINE_UUID);
            }
            if (mCrypto == null) {
                mSessionID = openSession(mDrm);
                assertNotNull("Failed to provision device.", mSessionID);
                mCrypto = new MediaCrypto(WIDEVINE_UUID, mSessionID);
            }
            mCodec.configure(format, mSurface, mCrypto,
                    isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);

            Map<UUID, byte[]> psshInfo = mExtractor.getPsshInfo();
            byte[] emeInitData = null;

            // TODO(b/230682028) Remove the following once webm extractor returns PSSH info for VP9
            if (psshInfo == null && mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                if (format.getInteger(MediaFormat.KEY_HEIGHT) == 1080) {
                    emeInitData = new byte[]{8, 1, 18, 1, 51, 26, 13, 119, 105, 100, 101, 118,
                            105, 110, 101, 95, 116, 101, 115, 116, 34, 10, 50, 48, 49, 53,
                            95, 116, 101, 97, 114, 115, 42, 2, 72, 68};
                } else if (format.getInteger(MediaFormat.KEY_HEIGHT) == 2160) {
                    emeInitData = new byte[]{8, 1, 18, 1, 56, 26, 13, 119, 105, 100, 101, 118,
                            105, 110, 101, 95, 116, 101, 115, 116, 34, 10, 50, 48, 49, 53,
                            95, 116, 101, 97, 114, 115, 42, 4, 85, 72, 68, 49};
                } else {
                    fail("unable to get pssh info for the given resolution in vp9");
                }
            } else {
                assertNotNull("Extractor is missing pssh info", psshInfo);
                emeInitData = psshInfo.get(WIDEVINE_UUID);
            }
            assertNotNull("Extractor pssh info is missing data for scheme: " + WIDEVINE_UUID,
                    emeInitData);
            KeyRequester requester =
                    new KeyRequester(mDrm, mSessionID, MediaDrm.KEY_TYPE_STREAMING, mMediaType,
                            emeInitData, serverURL, WIDEVINE_UUID);
            requester.send();
            return;
        }
        super.configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            int size = mExtractor.readSampleData(inputBuffer, 0);
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
            boolean isEncrypted = mExtractor.getSampleCryptoInfo(info);
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            if (mSecureMode && isEncrypted) {
                mCodec.queueSecureInputBuffer(bufferIndex, 0, info, pts, codecFlags);
            } else {
                mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            }
            if (size > 0 && (codecFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mInputCount++;
            }
        }
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }

        int outputCount = mOutputCount;
        // handle output count prior to releasing the buffer as that can take time
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputCount++;
            if (mOutputCountListener != null) {
                mOutputCountListener.accept(mOutputCount);
            }
        }
        releaseOutput(outputCount, bufferIndex, info);
    }

    protected void releaseOutput(int outputCount, int bufferIndex, MediaCodec.BufferInfo info) {
        releaseOutput(bufferIndex, info);
    }

    protected void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void validateTestState() {}
}
