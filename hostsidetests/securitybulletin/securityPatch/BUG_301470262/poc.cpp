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

#include <stdlib.h>
#include "../includes/common.h"

#define LOG_TAG "BUG_301470262"

#include <android/binder_process.h>
#include <log/log.h>

#include <media/NdkMediaCodec.h>

#define DEQUEUE_BUFFER_TIMEOUT_MICROSECONDS 1000
#define TOTAL_TIMEOUT_MICROSECONDS (10 * 1000 * 1000)
#define FILE_SIZE 256
#define SAMPLE_RATE 8192
#define NUM_CHANNELS 1
#define BUFFER_SIZE 100

int main(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    if (argc != 1) {
        return EXIT_FAILURE;
    }

    AMediaCodec *codec;
    media_status_t status;
    int64_t inActiveTime = 0ll;
    bool isEncoder = false;

    // binder threads are needed to communicate with the codec framework
    ABinderProcess_startThreadPool();

    codec = AMediaCodec_createCodecByName("c2.android.raw.decoder");
    if (!codec) {
        return EXIT_FAILURE;
    }
    /* Set Format */
    AMediaFormat *format = AMediaFormat_new();
    if (!format) {
        AMediaCodec_delete(codec);
        return EXIT_FAILURE;
    }
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "audio/pcm");
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, FILE_SIZE);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, SAMPLE_RATE);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, NUM_CHANNELS);
    AMediaCodec_configure(codec, format, nullptr, nullptr, isEncoder);
    AMediaCodec_start(codec);

    size_t filePos = 0;
    bool inputEOS = false;
    size_t inputOffset = 0;
    int exitValue = EXIT_SUCCESS;

    while (inActiveTime < TOTAL_TIMEOUT_MICROSECONDS) {
        /* Queue input data */
        if (!inputEOS) {
            uint32_t bufferFlags = 0;
            ssize_t inIdx =
                    AMediaCodec_dequeueInputBuffer(codec, DEQUEUE_BUFFER_TIMEOUT_MICROSECONDS);
            if (inIdx >= 0) {
                size_t bufSize;
                uint8_t *buf = AMediaCodec_getInputBuffer(codec, inIdx, &bufSize);

                ALOGI("dqInBuf => %zd @%p+%zu => [%zu + %u]",
                        inIdx, buf, bufSize, inputOffset, BUFFER_SIZE);
                // Fill the buffer at increasing offset with each successive buffer
                // RAW decoder simply returns the queued inout, but the framework thus far
                // ends up copying the returned input into a separate output buffer to simplify
                // buffer lifecycle management. To prepare for a future where the input is
                // directly returned, fill each buffer with unique values, so buffer overwrite
                // is also detected. Hence the pattern is:
                // buffer #1: 100 101 102 103 .. 199
                // buffer #2: gap  99 100 101 ... 198
                // buffer #3: gap gap  98  99 ... 197
                for (uint8_t i = 0; i < BUFFER_SIZE && i + inputOffset < bufSize; i++) {
                    buf[i + inputOffset] = i + (BUFFER_SIZE - inputOffset);
                }

                if (inputOffset == BUFFER_SIZE) {
                    inputEOS = true;
                    bufferFlags |= AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
                }
                status = AMediaCodec_queueInputBuffer(
                        codec, inIdx, inputOffset, BUFFER_SIZE, inputOffset, bufferFlags);
                if (status != AMEDIA_OK) {
                    break;
                }
                inputOffset += 1;
                inActiveTime = 0;
            } else if (inIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                ALOGI("dqInBuf => TRY_AGAIN");
                inActiveTime += DEQUEUE_BUFFER_TIMEOUT_MICROSECONDS;
            } else {
                ALOGI("dqInBuf => %zd", inIdx);
                break;
            }
        }
        /* Dequeue output */
        AMediaCodecBufferInfo info;
        ssize_t outIdx =
                AMediaCodec_dequeueOutputBuffer(codec, &info, DEQUEUE_BUFFER_TIMEOUT_MICROSECONDS);
        if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED ||
            outIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            ALOGI("dqOutBuf => CHANGED");
            inActiveTime = 0;
        } else if (outIdx >= 0) {
            size_t bufSize;
            uint8_t *buf = AMediaCodec_getOutputBuffer(codec, outIdx, &bufSize);
            ALOGI("dqOutBuf => %zd @%p+%zu f%lld [%u + %u] ~%zd",
                  outIdx, buf, bufSize, (long long)info.flags,
                  info.offset, info.size, (ssize_t)info.presentationTimeUs);

            if (bufSize != info.size) {
                ALOGE("mismatch between dqOutBuf size and BufferInfo size");
                exitValue = EXIT_VULNERABLE;
                break;
            }
            if (0 != info.offset) {
                ALOGE("BufferInfo offset must be 0");
                exitValue = EXIT_VULNERABLE;
                break;
            }

            bool correct = true;
            for (uint8_t i = 0; i < BUFFER_SIZE && i < bufSize; i++) {
                if (buf[i] != i + (BUFFER_SIZE - info.presentationTimeUs)) {
                    ALOGI("dqOutBuf => INCORRECT @%u (%u vs %u)",
                            i, buf[i], (uint8_t)(i + (BUFFER_SIZE - info.presentationTimeUs)));
                    correct = false;
                    break;
                }
            }
            if (!correct) {
                exitValue = EXIT_VULNERABLE;
                break;
            }
            status = AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
            if (status != AMEDIA_OK) {
                break;
            }
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                break;
            }
            inActiveTime = 0;
        } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            ALOGI("dqOutBuf => TRY_AGAIN");
            inActiveTime += DEQUEUE_BUFFER_TIMEOUT_MICROSECONDS;
        } else {
            ALOGI("dqOutBuf => %zd", outIdx);
            break;
        }
    }
    AMediaFormat_delete(format);
    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);

    return exitValue;
}
