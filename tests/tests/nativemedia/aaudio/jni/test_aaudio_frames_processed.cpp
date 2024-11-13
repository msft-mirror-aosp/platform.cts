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

// Tests that a reasonable number of frames were read and written.

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <unistd.h>

#include <queue>

#include "utils.h"

using TestAAudioFramesProcessedParams = std::tuple<aaudio_direction_t, int>;

enum {
    PARAM_DIRECTION = 0,
    PARAM_SAMPLE_RATE,
};

template <typename T>
class AtomicQueue {
public:
    void push(const T& value) {
        std::lock_guard<std::mutex> lock(mMutex);
        mQueue.push(value);
    }

    void pop() {
        std::lock_guard<std::mutex> lock(mMutex);
        mQueue.pop();
    }

    T front() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mQueue.front();
    }

private:
    std::queue<T> mQueue;
    mutable std::mutex mMutex;
};

class TestAAudioFramesProcessed
      : public AAudioCtsBase,
        public ::testing::WithParamInterface<TestAAudioFramesProcessedParams> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<TestAAudioFramesProcessedParams>& info) {
        return std::string() + aaudioDirectionToString(std::get<PARAM_DIRECTION>(info.param)) +
                "__" + std::to_string(std::get<PARAM_SAMPLE_RATE>(info.param));
    }

protected:
    static const char* aaudioDirectionToString(aaudio_direction_t direction) {
        switch (direction) {
            case AAUDIO_DIRECTION_OUTPUT:
                return "OUTPUT";
            case AAUDIO_DIRECTION_INPUT:
                return "INPUT";
        }
        return "UNKNOWN";
    }

    void testConfiguration(aaudio_direction_t direction, int sampleRate) {
        if (direction == AAUDIO_DIRECTION_INPUT) {
            if (!deviceSupportsFeature(FEATURE_RECORDING)) return;
        } else {
            if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
        }

        AAudioStreamBuilder* aaudioBuilder = nullptr;
        AAudioStream* aaudioStream = nullptr;

        // Use an AAudioStreamBuilder to contain requested parameters.
        ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

        // Request stream properties.
        AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setDirection(aaudioBuilder, direction);
        AAudioStreamBuilder_setFormat(aaudioBuilder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setSampleRate(aaudioBuilder, sampleRate);
        AAudioStreamBuilder_setDataCallback(aaudioBuilder, SampleRateDataCallbackProc, this);

        // Create an AAudioStream using the Builder.
        ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
        AAudioStreamBuilder_delete(aaudioBuilder);

        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream));

        sleep(kProcessTimeSeconds);

        ASSERT_GT(mCallbackCount, kInitialSkippedCallbackCount);
        ASSERT_GT((mTimestamps.front() - mInitialCallbackTime) / (double)NANOS_PER_SECOND,
                  kMinimumElapsedTimeSeconds);
        ASSERT_NEAR(sampleRate, mMeasuredWrittenSampleRates.front(),
                    sampleRate * kSampleRateToleranceRatio);
        ASSERT_NEAR(sampleRate, mMeasuredReadSampleRates.front(),
                    sampleRate * kSampleRateToleranceRatio);

        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream));

        EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }

    static aaudio_data_callback_result_t SampleRateDataCallbackProc(AAudioStream* stream,
                                                                    void* userData,
                                                                    void* /*audioData*/,
                                                                    int32_t /*numFrames*/) {
        TestAAudioFramesProcessed* testClass = (TestAAudioFramesProcessed*)userData;

        if (testClass->mCallbackCount == kInitialSkippedCallbackCount) {
            testClass->mInitialCallbackTime = getNanoseconds(CLOCK_MONOTONIC);
            testClass->mInitialFramesWritten = AAudioStream_getFramesWritten(stream);
            testClass->mInitialFramesRead = AAudioStream_getFramesRead(stream);
        }
        if (testClass->mCallbackCount > kInitialSkippedCallbackCount) {
            int64_t currentTime = getNanoseconds(CLOCK_MONOTONIC);
            int64_t framesWritten = AAudioStream_getFramesWritten(stream);
            int64_t framesRead = AAudioStream_getFramesRead(stream);
            double seconds =
                    (currentTime - testClass->mInitialCallbackTime) / (double)NANOS_PER_SECOND;
            testClass->mTimestamps.push(currentTime);
            testClass->mMeasuredWrittenSampleRates.push(
                    (framesWritten - testClass->mInitialFramesWritten) / seconds);
            testClass->mMeasuredReadSampleRates.push((framesRead - testClass->mInitialFramesRead) /
                                                     seconds);
            if (testClass->mCallbackCount >
                kInitialSkippedCallbackCount + kFinalSkippedCallbackCount) {
                testClass->mTimestamps.pop();
                testClass->mMeasuredWrittenSampleRates.pop();
                testClass->mMeasuredReadSampleRates.pop();
            }
        }
        testClass->mCallbackCount++;
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static constexpr int kProcessTimeSeconds = 5;
    static constexpr int kMinimumElapsedTimeSeconds = 1;
    static constexpr double kSampleRateToleranceRatio = .05;
    // Number of callbacks to ignore initially to let the callbacks stabilize.
    static constexpr int kInitialSkippedCallbackCount = 5;
    // Number of callbacks to skip at the end. This number of callbacks will be queued.
    static constexpr int kFinalSkippedCallbackCount = 5;

    std::atomic<int> mCallbackCount = 0;
    std::atomic<int64_t> mInitialFramesRead = 0;
    std::atomic<int64_t> mInitialFramesWritten = 0;
    std::atomic<int64_t> mInitialCallbackTime = 0;
    AtomicQueue<int64_t> mTimestamps;
    AtomicQueue<double> mMeasuredWrittenSampleRates;
    AtomicQueue<double> mMeasuredReadSampleRates;
};

TEST_P(TestAAudioFramesProcessed, TestFramesProcessed) {
    testConfiguration(std::get<PARAM_DIRECTION>(GetParam()),
                      std::get<PARAM_SAMPLE_RATE>(GetParam()));
}

INSTANTIATE_TEST_SUITE_P(
        AAudioFramesProcessed, TestAAudioFramesProcessed,
        ::testing::Values(TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 8000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 8000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 44100}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 44100}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 96000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 96000})),
        &TestAAudioFramesProcessed::getTestName);