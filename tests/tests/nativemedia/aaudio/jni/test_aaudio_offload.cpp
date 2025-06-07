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

// Test AAudio offload.

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <memory>

#include "utils.h"

static constexpr int64_t WAIT_END_OF_PRESENTATION_MICROSECONDS = 1200000;

class AAudioOffloadTest : public AAudioCtsBase,
                          public ::testing::WithParamInterface<aaudio_format_t> {
protected:
    void SetUp() override;
    void TearDown() override;

    bool mEndOfPresentation = false;
    struct DataCallbackUserData {
        DataCallbackUserData(int32_t framesOfStream) : mFramesOfStream(framesOfStream) {}
        const int32_t mFramesOfStream;
        int32_t mFramesWritten = 0;
        std::atomic_bool mIgnoreDataCallbackForFlushFrom{false};
        std::atomic_bool mSetStreamEnd{true};
    };
    static void MyPresentationEndCallbackProc(AAudioStream *stream, void *userData);
    static aaudio_data_callback_result_t MyPartialDataCallbackProc(AAudioStream *stream,
                                                                   void *userData, void *audioData,
                                                                   int32_t numFrames);

    std::unique_ptr<DataCallbackUserData> mCbData;
    AAudioStream *mStream = nullptr;

    static constexpr int32_t kSampleRate = 48000;
    static constexpr int32_t kDelay = 8;
    static constexpr int32_t kPadding = 16;
};

void AAudioOffloadTest::SetUp() {
    AAudioCtsBase::SetUp();

    AAudioStreamBuilder *builder = nullptr;
    mStream = nullptr;

    const aaudio_format_t format = GetParam();

    EXPECT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&builder));
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED);
    AAudioStreamBuilder_setFormat(builder, format);
    AAudioStreamBuilder_setChannelMask(builder, AAUDIO_CHANNEL_STEREO);
    AAudioStreamBuilder_setSampleRate(builder, kSampleRate);
    mEndOfPresentation = false;
    AAudioStreamBuilder_setPresentationEndCallback(builder, &MyPresentationEndCallbackProc,
                                                   &mEndOfPresentation);
    mCbData.reset(new DataCallbackUserData(kSampleRate));
    AAudioStreamBuilder_setPartialDataCallback(builder, &MyPartialDataCallbackProc, mCbData.get());
    // Use a small callback so that when framework is asking for a big callback data,
    // the test can still only provide a second of data.
    AAudioStreamBuilder_setFramesPerDataCallback(builder, kSampleRate / 100);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &mStream);
    AAudioStreamBuilder_delete(builder);
    ASSERT_EQ(isOffloadSupported(format, AAUDIO_CHANNEL_STEREO, kSampleRate), result == AAUDIO_OK);
    if (result != AAUDIO_OK) {
        // Offload is not supported for the requested configuration, return from here.
        ASSERT_EQ(nullptr, mStream);
        return;
    }
    ASSERT_EQ(AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED,
              AAudioStream_getPerformanceMode(mStream));
}

void AAudioOffloadTest::TearDown() {
    AAudioStream_close(mStream);
    AAudioCtsBase::TearDown();
}

// static
void AAudioOffloadTest::MyPresentationEndCallbackProc(AAudioStream * /*stream*/, void *userData) {
    bool *myData = static_cast<bool *>(userData);
    *myData = true;
}

// static
int32_t AAudioOffloadTest::MyPartialDataCallbackProc(AAudioStream *stream, void *userData,
                                                     void * /*audioData*/, int32_t numFrames) {
    DataCallbackUserData *myData = static_cast<DataCallbackUserData *>(userData);

    if (myData->mIgnoreDataCallbackForFlushFrom) {
        return 0;
    }

    int32_t dataProcessed = numFrames;
    if (myData->mSetStreamEnd.load(std::memory_order_acquire)) {
        dataProcessed = std::min(dataProcessed, myData->mFramesOfStream - myData->mFramesWritten);
        if (dataProcessed != numFrames) {
            AAudioStream_setOffloadEndOfStream(stream);
        }
    }
    myData->mFramesWritten += dataProcessed;
    return dataProcessed;
}

TEST_P(AAudioOffloadTest, testOffload) {
    if (!mmapPcmOffloadSupport()) {
        // No need to run the test if the flag is not enabled.
        return;
    }
    if (mStream == nullptr) {
        // Offload is not supported for the requested configuration, no need to run the test.
        return;
    }

    const aaudio_format_t format = GetParam();
    if (isCompressedFormat(format)) {
        // The HAL will return error if there is not real compressed data written. Skip the data
        // transfer for compressed format for now.
        // TODO(b/392178400): Use real compress data for testing
        return;
    }

    EXPECT_EQ(isCompressedFormat(format) ? AAUDIO_OK : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_setOffloadDelayPadding(mStream, kDelay, kPadding));
    EXPECT_EQ(isCompressedFormat(format) ? kDelay : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_getOffloadDelay(mStream));
    EXPECT_EQ(isCompressedFormat(format) ? kPadding : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_getOffloadPadding(mStream));

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(mStream));
    usleep(WAIT_END_OF_PRESENTATION_MICROSECONDS);
    ASSERT_TRUE(mEndOfPresentation);
}

TEST_P(AAudioOffloadTest, testFlushFromFrame) {
    if (!mmapPcmOffloadSupport()) {
        // No need to run the test if the flag is not enabled.
        return;
    }
    if (mStream == nullptr) {
        // Offload is not supported for the requested configuration, no need to run the test.
        return;
    }

    const aaudio_format_t format = GetParam();
    if (isCompressedFormat(format)) {
        // The HAL will return error if there is not real compressed data written. Skip the data
        // transfer for compressed format for now.
        // TODO(b/392178400): Use real compress data for testing
        return;
    }
    if (!AAudioStream_isMMapUsed(mStream)) {
        // AAudioStream_flushFromFrame is only supported on mmap path now
        return;
    }
    ASSERT_NE(nullptr, mStream);
    mCbData->mSetStreamEnd.store(false, std::memory_order_release);

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(mStream));
    // Sleep for 500ms to allow the stream active
    sleep(1);
    mCbData->mIgnoreDataCallbackForFlushFrom = true;
    int64_t framesRead = AAudioStream_getFramesRead(mStream);
    ASSERT_GT(framesRead, 0);
    int64_t framesWritten = AAudioStream_getFramesWritten(mStream);
    ASSERT_GE(framesWritten, framesRead);

    // Position should not be null. Build will fail with CTS if null is used.
    // EXPECT_EQ(AAUDIO_ERROR_OUT_OF_RANGE,
    //          AAudioStream_flushFromFrame(mStream, AAUDIO_FLUSH_FROM_ACCURACY_UNDEFINED,
    //                                      nullptr /*position*/));

    int64_t position = -1;
    // Negative position is invalid.
    EXPECT_EQ(AAUDIO_ERROR_OUT_OF_RANGE,
              AAudioStream_flushFromFrame(mStream, AAUDIO_FLUSH_FROM_ACCURACY_UNDEFINED,
                                          &position));

    position = framesWritten + kSampleRate;
    // Greater than frames written is invalid.
    EXPECT_EQ(AAUDIO_ERROR_OUT_OF_RANGE,
              AAudioStream_flushFromFrame(mStream, AAUDIO_FLUSH_FROM_ACCURACY_UNDEFINED,
                                          &position));
    position = 0;
    aaudio_result_t result =
            AAudioStream_flushFromFrame(mStream, AAUDIO_FLUSH_FROM_FRAME_ACCURATE, &position);
    EXPECT_EQ(AAUDIO_ERROR_OUT_OF_RANGE, result);
    EXPECT_GT(position, 0);
    EXPECT_EQ(framesWritten, AAudioStream_getFramesWritten(mStream));
    const int64_t requestedPosition = position;
    result = AAudioStream_flushFromFrame(mStream, AAUDIO_FLUSH_FROM_ACCURACY_UNDEFINED, &position);
    EXPECT_EQ(AAUDIO_OK, result);
    EXPECT_GE(position, requestedPosition);
    EXPECT_LE(position, framesWritten);
    EXPECT_EQ(position, AAudioStream_getFramesWritten(mStream));
}

INSTANTIATE_TEST_CASE_P(Offload, AAudioOffloadTest,
                        ::testing::Values(AAUDIO_FORMAT_PCM_I16, AAUDIO_FORMAT_MP3,
                                          AAUDIO_FORMAT_AAC_LC, AAUDIO_FORMAT_AAC_HE_V1,
                                          AAUDIO_FORMAT_AAC_HE_V2, AAUDIO_FORMAT_AAC_ELD,
                                          AAUDIO_FORMAT_AAC_XHE, AAUDIO_FORMAT_OPUS));
