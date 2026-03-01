/*
 * Copyright 2026 The Android Open Source Project
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

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <unistd.h>

#include <atomic>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <string>
#include <tuple>
#include <vector>

#include "utils.h"

class AAudioXRunsTestBase : public AAudioCtsBase {
protected:
    aaudio_direction_t mStreamDirection = AAUDIO_DIRECTION_OUTPUT;
    int32_t mSampleRate = AAUDIO_UNSPECIFIED;
    int32_t mChannelCount = AAUDIO_UNSPECIFIED;
    aaudio_channel_mask_t mChannelMask = AAUDIO_UNSPECIFIED;
    aaudio_format_t mFormat = AAUDIO_FORMAT_UNSPECIFIED;
    aaudio_performance_mode_t mPerfMode = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
    aaudio_usage_t mUsage = AAUDIO_UNSPECIFIED;
    aaudio_content_type_t mContentType = AAUDIO_UNSPECIFIED;
    aaudio_input_preset_t mInputPreset = AAUDIO_UNSPECIFIED;
    bool mUseCallback = false;
    bool mWasSetupSuccessful = false;
    AAudioStream* mAAudioStream = nullptr;

    static const char* useCallbackToString(bool useCallback) {
        return useCallback ? "callback" : "no_callback";
    }

    void SetUp() override {
        AAudioCtsBase::SetUp();
        readParameters();
        mWasSetupSuccessful = false;
        if (!deviceSupportsFeature(mStreamDirection == AAUDIO_DIRECTION_INPUT ?
                FEATURE_RECORDING : FEATURE_PLAYBACK)) {
            GTEST_SKIP();
        }

        if ((mPerfMode == AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED) &&
                !isOffloadSupported(mFormat, mChannelMask, mSampleRate)) {
            GTEST_SKIP();
        }

        AAudioStreamBuilder* aaudioBuilder = nullptr;

        ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

        AAudioStreamBuilder_setDirection(aaudioBuilder, mStreamDirection);
        if (mSampleRate != AAUDIO_UNSPECIFIED)
            AAudioStreamBuilder_setSampleRate(aaudioBuilder, mSampleRate);
        if (mFormat != AAUDIO_FORMAT_UNSPECIFIED)
            AAudioStreamBuilder_setFormat(aaudioBuilder, mFormat);

        if (mChannelMask != AAUDIO_UNSPECIFIED) {
            AAudioStreamBuilder_setChannelMask(aaudioBuilder, mChannelMask);
        } else if (mChannelCount != AAUDIO_UNSPECIFIED) {
            AAudioStreamBuilder_setChannelCount(aaudioBuilder, mChannelCount);
        }

        AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, mPerfMode);
        if (mUsage != AAUDIO_UNSPECIFIED) {
            AAudioStreamBuilder_setUsage(aaudioBuilder, mUsage);
        }
        if (mContentType != AAUDIO_UNSPECIFIED) {
            AAudioStreamBuilder_setContentType(aaudioBuilder, mContentType);
        }
        if (mInputPreset != AAUDIO_UNSPECIFIED) {
            AAudioStreamBuilder_setInputPreset(aaudioBuilder, mInputPreset);
        }

        if (mUseCallback) {
            AAudioStreamBuilder_setDataCallback(aaudioBuilder, XRunDataCallbackProc, this);
        }

        aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &mAAudioStream);

        AAudioStreamBuilder_delete(aaudioBuilder);
        aaudioBuilder = nullptr;

        ASSERT_EQ(AAUDIO_OK, result);
        ASSERT_NE(nullptr, mAAudioStream);
        mWasSetupSuccessful = true;
    }

    void TearDown() override {
        if (mWasSetupSuccessful) {
            AAudioStream_close(mAAudioStream);
        }
        AAudioCtsBase::TearDown();
    }

    void testXRunsConfiguration() {
        if (!mWasSetupSuccessful) {
            GTEST_SKIP();
        }
        int32_t framesPerBurst = AAudioStream_getFramesPerBurst(mAAudioStream);
        int32_t channelCount = AAudioStream_getChannelCount(mAAudioStream);
        int32_t sampleRate = AAudioStream_getSampleRate(mAAudioStream);
        aaudio_format_t format = AAudioStream_getFormat(mAAudioStream);

        int32_t bytesPerFrame = 0;
        if (format == AAUDIO_FORMAT_PCM_I16) {
            bytesPerFrame = sizeof(int16_t) * channelCount;
        } else if (format == AAUDIO_FORMAT_PCM_FLOAT) {
            bytesPerFrame = sizeof(float) * channelCount;
        } else if (format == AAUDIO_FORMAT_PCM_I32) {
            bytesPerFrame = sizeof(int32_t) * channelCount;
        } else if (format == AAUDIO_FORMAT_PCM_I24_PACKED) {
            bytesPerFrame = 3 * channelCount;
        }
        ASSERT_GT(bytesPerFrame, 0);

        int32_t bufferSizeInBytes = framesPerBurst * bytesPerFrame;
        std::vector<char> audioBuffer(bufferSizeInBytes);

        ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(mAAudioStream));

        processData(sampleRate, framesPerBurst, audioBuffer, kWarmUpTimeSeconds);

        int32_t initialXRuns = AAudioStream_getXRunCount(mAAudioStream);
        EXPECT_GE(initialXRuns, 0);

        processData(sampleRate, framesPerBurst, audioBuffer, kTestDurationSeconds);

        int32_t finalXRuns = AAudioStream_getXRunCount(mAAudioStream);

        // Pause on output streams as offload streams may leave data in the buffer.
        if (mStreamDirection == AAUDIO_DIRECTION_OUTPUT) {
            EXPECT_EQ(AAUDIO_OK, AAudioStream_requestPause(mAAudioStream));
        } else {
            EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(mAAudioStream));
        }

        // We may decrease this in the future
        EXPECT_GE(initialXRuns + 10, finalXRuns);
        sleep(kTimeBetweenTestsSeconds);
    }

    void processData(int32_t sampleRate, int32_t framesPerBurst, std::vector<char>& audioBuffer,
                     int32_t durationSeconds) {
        if (mUseCallback) {
            sleep(durationSeconds);
        } else {
            int32_t framesToProcess = sampleRate * durationSeconds;
            int32_t framesProcessed = 0;

            while (framesProcessed < framesToProcess) {
                if (mStreamDirection == AAUDIO_DIRECTION_OUTPUT) {
                    int32_t framesWritten =
                            AAudioStream_write(mAAudioStream, audioBuffer.data(), framesPerBurst,
                                               kReadWriteTimeoutNanoseconds);
                    EXPECT_GE(framesWritten, 0);
                    if (framesWritten > 0) {
                        framesProcessed += framesWritten;
                    } else {
                        return;
                    }
                } else {
                    int32_t framesRead =
                            AAudioStream_read(mAAudioStream, audioBuffer.data(), framesPerBurst,
                                              kReadWriteTimeoutNanoseconds);
                    EXPECT_GE(framesRead, 0);
                    if (framesRead > 0) {
                        framesProcessed += framesRead;
                    } else {
                        return;
                    }
                }
            }
        }
    }

    static aaudio_data_callback_result_t XRunDataCallbackProc(AAudioStream* /*stream*/,
                                                              void* /*userData*/,
                                                              void* /*audioData*/,
                                                              int32_t /*numFrames*/) {
        // Do nothing in the callback
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    virtual void readParameters() = 0;

    static constexpr int64_t kReadWriteTimeoutNanoseconds = NANOS_PER_SECOND / 4;
    static constexpr int kTotalStreamTimeSeconds = 3;
    static constexpr int kWarmUpTimeSeconds = 1;
    static constexpr int kTestDurationSeconds = kTotalStreamTimeSeconds - kWarmUpTimeSeconds;
    static constexpr int kTimeBetweenTestsSeconds = 1;
};

/******************************************************************************
 * ChannelCountXRunTest
 *****************************************************************************/

using ChannelCountParam = std::tuple<aaudio_direction_t, int32_t, bool>;
class ChannelCountXRunTest : public AAudioXRunsTestBase,
                             public ::testing::WithParamInterface<ChannelCountParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<ChannelCountParam>& info) {
        std::stringstream ss;
        ss << directionToString(std::get<0>(info.param)) << "__";
        ss << std::get<1>(info.param) << "__";
        ss << useCallbackToString(std::get<2>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void ChannelCountXRunTest::readParameters() {
    mStreamDirection = std::get<0>(GetParam());
    mChannelCount = std::get<1>(GetParam());
    mUseCallback = std::get<2>(GetParam());
}

TEST_P(ChannelCountXRunTest, checkChannelCounts) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(AAudioTestXRuns, ChannelCountXRunTest,
                        ::testing::Combine(::testing::Values(AAUDIO_DIRECTION_OUTPUT,
                                                             AAUDIO_DIRECTION_INPUT),
                                           ::testing::Values(2, 4), ::testing::Values(false, true)),
                        &ChannelCountXRunTest::getTestName);

/******************************************************************************
 * SampleRateXRunTest
 *****************************************************************************/

using SampleRateParam = std::tuple<aaudio_direction_t, int32_t, bool>;
class SampleRateXRunTest : public AAudioXRunsTestBase,
                           public ::testing::WithParamInterface<SampleRateParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<SampleRateParam>& info) {
        std::stringstream ss;
        ss << directionToString(std::get<0>(info.param)) << "__";
        ss << std::get<1>(info.param) << "__";
        ss << useCallbackToString(std::get<2>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void SampleRateXRunTest::readParameters() {
    mStreamDirection = std::get<0>(GetParam());
    mSampleRate = std::get<1>(GetParam());
    mUseCallback = std::get<2>(GetParam());
}

TEST_P(SampleRateXRunTest, checkSampleRates) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestXRuns, SampleRateXRunTest,
        ::testing::Combine(::testing::Values(AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT),
                           ::testing::Values(48000, 44100), ::testing::Values(false, true)),
        &SampleRateXRunTest::getTestName);

/******************************************************************************
 * FormatXRunTest
 *****************************************************************************/

using FormatParam = std::tuple<aaudio_direction_t, aaudio_format_t, bool>;
class FormatXRunTest : public AAudioXRunsTestBase,
                       public ::testing::WithParamInterface<FormatParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<FormatParam>& info) {
        std::stringstream ss;
        ss << directionToString(std::get<0>(info.param)) << "__";
        ss << formatToString(std::get<1>(info.param)) << "__";
        ss << useCallbackToString(std::get<2>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void FormatXRunTest::readParameters() {
    mStreamDirection = std::get<0>(GetParam());
    mFormat = std::get<1>(GetParam());
    mUseCallback = std::get<2>(GetParam());
}

TEST_P(FormatXRunTest, checkFormats) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestXRuns, FormatXRunTest,
        ::testing::Combine(::testing::Values(AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT),
                           ::testing::Values(AAUDIO_FORMAT_PCM_FLOAT, AAUDIO_FORMAT_PCM_I16,
                                             AAUDIO_FORMAT_PCM_I24_PACKED, AAUDIO_FORMAT_PCM_I32),
                           ::testing::Values(false, true)),
        &FormatXRunTest::getTestName);

/******************************************************************************
 * PerfModeXRunTest
 *****************************************************************************/

using PerfModeParam = std::tuple<aaudio_direction_t, aaudio_performance_mode_t, bool>;
class PerfModeXRunTest : public AAudioXRunsTestBase,
                         public ::testing::WithParamInterface<PerfModeParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<PerfModeParam>& info) {
        std::stringstream ss;
        ss << directionToString(std::get<0>(info.param)) << "__";
        ss << performanceModeToString(std::get<1>(info.param)) << "__";
        ss << useCallbackToString(std::get<2>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void PerfModeXRunTest::readParameters() {
    mStreamDirection = std::get<0>(GetParam());
    mPerfMode = std::get<1>(GetParam());
    mUseCallback = std::get<2>(GetParam());
}

TEST_P(PerfModeXRunTest, checkPerfModes) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(AAudioTestXRuns, PerfModeXRunTest,
                        ::testing::Combine(::testing::Values(AAUDIO_DIRECTION_OUTPUT,
                                                             AAUDIO_DIRECTION_INPUT),
                                           ::testing::Values(AAUDIO_PERFORMANCE_MODE_NONE,
                                                             AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                                                             AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                                           ::testing::Values(false, true)),
                        &PerfModeXRunTest::getTestName);

/******************************************************************************
 * OffloadXRunTest
 *****************************************************************************/

using OffloadParam = std::tuple<bool>;
class OffloadXRunTest : public AAudioXRunsTestBase,
                        public ::testing::WithParamInterface<OffloadParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<OffloadParam>& info) {
        std::stringstream ss;
        ss << useCallbackToString(std::get<0>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void OffloadXRunTest::readParameters() {
    mFormat = AAUDIO_FORMAT_PCM_FLOAT;
    mChannelMask = AAUDIO_CHANNEL_STEREO;
    mSampleRate = 48000;
    mUseCallback = std::get<0>(GetParam());
    mPerfMode = AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED;
}

TEST_P(OffloadXRunTest, checkOffload) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(AAudioTestXRuns, OffloadXRunTest,
                        ::testing::Combine(::testing::Values(false, true)),
                        &OffloadXRunTest::getTestName);

/******************************************************************************
 * UsageXRunTest
 *****************************************************************************/

using UsageParam = std::tuple<aaudio_usage_t, bool>;
class UsageXRunTest : public AAudioXRunsTestBase, public ::testing::WithParamInterface<UsageParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<UsageParam>& info) {
        std::stringstream ss;
        ss << usageToString(std::get<0>(info.param)) << "__";
        ss << useCallbackToString(std::get<1>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void UsageXRunTest::readParameters() {
    mUsage = std::get<0>(GetParam());
    mUseCallback = std::get<1>(GetParam());
}

TEST_P(UsageXRunTest, checkUsages) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestXRuns, UsageXRunTest,
        ::testing::Combine(::testing::Values(AAUDIO_USAGE_MEDIA, AAUDIO_USAGE_VOICE_COMMUNICATION,
                                             AAUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING,
                                             AAUDIO_USAGE_ALARM, AAUDIO_USAGE_NOTIFICATION,
                                             AAUDIO_USAGE_ASSISTANCE_ACCESSIBILITY,
                                             AAUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                                             AAUDIO_USAGE_ASSISTANCE_SONIFICATION,
                                             AAUDIO_USAGE_GAME, AAUDIO_USAGE_ASSISTANT),
                           ::testing::Values(false, true)),
        &UsageXRunTest::getTestName);

/******************************************************************************
 * ContentTypeXRunTest
 *****************************************************************************/

using ContentTypeParam = std::tuple<aaudio_content_type_t, bool>;
class ContentTypeXRunTest : public AAudioXRunsTestBase,
                            public ::testing::WithParamInterface<ContentTypeParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<ContentTypeParam>& info) {
        std::stringstream ss;
        ss << contentTypeToString(std::get<0>(info.param)) << "__";
        ss << useCallbackToString(std::get<1>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void ContentTypeXRunTest::readParameters() {
    mContentType = std::get<0>(GetParam());
    mUseCallback = std::get<1>(GetParam());
}

TEST_P(ContentTypeXRunTest, checkContentTypes) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(AAudioTestXRuns, ContentTypeXRunTest,
                        ::testing::Combine(::testing::Values(AAUDIO_CONTENT_TYPE_SPEECH,
                                                             AAUDIO_CONTENT_TYPE_MUSIC,
                                                             AAUDIO_CONTENT_TYPE_MOVIE,
                                                             AAUDIO_CONTENT_TYPE_SONIFICATION),
                                           ::testing::Values(false, true)),
                        &ContentTypeXRunTest::getTestName);

/******************************************************************************
 * InputPresetXRunTest
 *****************************************************************************/

using InputPresetParam = std::tuple<aaudio_input_preset_t, bool>;
class InputPresetXRunTest : public AAudioXRunsTestBase,
                            public ::testing::WithParamInterface<InputPresetParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<InputPresetParam>& info) {
        std::stringstream ss;
        ss << inputPresetToString(std::get<0>(info.param)) << "__";
        ss << useCallbackToString(std::get<1>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void InputPresetXRunTest::readParameters() {
    mInputPreset = std::get<0>(GetParam());
    mUseCallback = std::get<1>(GetParam());
    mStreamDirection = AAUDIO_DIRECTION_INPUT;
}

TEST_P(InputPresetXRunTest, checkInputPresets) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestXRuns, InputPresetXRunTest,
        ::testing::Combine(::testing::Values(AAUDIO_INPUT_PRESET_GENERIC,
                                             AAUDIO_INPUT_PRESET_CAMCORDER,
                                             AAUDIO_INPUT_PRESET_VOICE_RECOGNITION,
                                             AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION,
                                             AAUDIO_INPUT_PRESET_UNPROCESSED,
                                             AAUDIO_INPUT_PRESET_VOICE_PERFORMANCE),
                           ::testing::Values(false, true)),
        &InputPresetXRunTest::getTestName);

/******************************************************************************
 * ChannelMaskXRunTest
 *****************************************************************************/

using ChannelMaskParam = std::tuple<aaudio_direction_t, aaudio_channel_mask_t, bool>;
class ChannelMaskXRunTest : public AAudioXRunsTestBase,
                            public ::testing::WithParamInterface<ChannelMaskParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<ChannelMaskParam>& info) {
        std::stringstream ss;
        ss << directionToString(std::get<0>(info.param)) << "__";
        ss << channelMaskToString(std::get<1>(info.param)) << "__";
        ss << useCallbackToString(std::get<2>(info.param));
        return ss.str();
    }

protected:
    void readParameters() override;
};

void ChannelMaskXRunTest::readParameters() {
    mStreamDirection = std::get<0>(GetParam());
    mChannelMask = std::get<1>(GetParam());
    mUseCallback = std::get<2>(GetParam());
}

TEST_P(ChannelMaskXRunTest, checkChannelMasks) {
    ASSERT_NO_FATAL_FAILURE(testXRunsConfiguration());
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestXRuns, ChannelMaskXRunTest,
        ::testing::Combine(::testing::Values(AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT),
                           ::testing::Values(AAUDIO_CHANNEL_MONO, AAUDIO_CHANNEL_STEREO),
                           ::testing::Values(false, true)),
        &ChannelMaskXRunTest::getTestName);
