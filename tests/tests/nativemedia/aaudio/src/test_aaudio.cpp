/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <gtest/gtest.h>
#include <utils/Log.h>

#include <aaudio/AAudio.h>
#include "test_aaudio.h"

int64_t getNanoseconds(clockid_t clockId) {
    struct timespec time;
    int result = clock_gettime(clockId, &time);
    if (result < 0) {
        return -errno;
    }
    return (time.tv_sec * NANOS_PER_SECOND) + time.tv_nsec;
}

// Test AAudioStreamBuilder
TEST(test_aaudio, aaudio_stream_builder) {

    AAudioStreamBuilder* aaudioBuilder1 = nullptr;
    AAudioStreamBuilder* aaudioBuilder2 = nullptr;

    // Use an AAudioStreamBuilder to define the stream.
    aaudio_result_t result = AAudio_createStreamBuilder(&aaudioBuilder1);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, aaudioBuilder1);

    // Create a second builder and make sure they do not collide.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder2));
    ASSERT_NE(nullptr, aaudioBuilder2);

    ASSERT_NE(aaudioBuilder1, aaudioBuilder2);

    // Delete the first builder.
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder1));

    // Delete the second builder.
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder2));

}


// Test creating a default stream with specific devices
void runtest_aaudio_devices(int32_t deviceId, bool expectFail) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;

    // Use an AAudioStreamBuilder to define the stream.
    aaudio_result_t result = AAudio_createStreamBuilder(&aaudioBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, aaudioBuilder);

    AAudioStreamBuilder_setDeviceId(aaudioBuilder,deviceId);

    // Create an AAudioStream using the Builder.
    result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    if (expectFail) {
        ASSERT_NE(AAUDIO_OK, result);
        ASSERT_EQ(nullptr, aaudioStream);
    } else {
        // Pass or fail is OK. Just don't crash.
        ASSERT_TRUE(((result < 0) && (aaudioStream == nullptr))
                    || ((result == AAUDIO_OK) && (aaudioStream != nullptr)));
    }

    // Cleanup
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    if (aaudioStream != nullptr) {
        AAudioStream_close(aaudioStream);
    }
}

TEST(test_aaudio, aaudio_stream_device_unspecified) {
    runtest_aaudio_devices(AAUDIO_DEVICE_UNSPECIFIED, false);
}

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST(test_aaudio, aaudio_stream_device_absurd) {
    runtest_aaudio_devices(19736459, true);
}
*/
/* FIXME review
TEST(test_aaudio, aaudio_stream_device_reasonable) {
    runtest_aaudio_devices(1, false);
}
*/

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST(test_aaudio, aaudio_stream_device_negative) {
    runtest_aaudio_devices(-765, true);
}
*/

// Test creating a default stream with everything unspecified.
TEST(test_aaudio, aaudio_stream_unspecified) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;
    aaudio_result_t result = AAUDIO_OK;

    // Use an AAudioStreamBuilder to define the stream.
    result = AAudio_createStreamBuilder(&aaudioBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, aaudioBuilder);

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
    ASSERT_NE(nullptr, aaudioStream);

    // Cleanup
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
}

// Test Writing to an AAudioStream
void runtest_aaudio_stream(aaudio_sharing_mode_t requestedSharingMode) {
    const int32_t requestedSampleRate = 48000;
    const int32_t requestedSamplesPerFrame = 2;
    const aaudio_audio_format_t requestedDataFormat = AAUDIO_FORMAT_PCM_I16;

    int32_t actualSampleRate = -1;
    int32_t actualSamplesPerFrame = -1;
    aaudio_audio_format_t actualDataFormat = AAUDIO_FORMAT_INVALID;
    aaudio_sharing_mode_t actualSharingMode;
    int32_t framesPerBurst = -1;
    int writeLoops = 0;

    int32_t framesWritten = 0;
    int32_t actualBufferSize = 0;
    int64_t framesTotal = 0;
    int64_t aaudioFramesRead = 0;
    int64_t aaudioFramesRead1 = 0;
    int64_t aaudioFramesRead2 = 0;
    int64_t aaudioFramesWritten = 0;

    int64_t timeoutNanos;

    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;

    aaudio_result_t result = AAUDIO_OK;

    // Use an AAudioStreamBuilder to define the stream.
    result = AAudio_createStreamBuilder(&aaudioBuilder);
    ASSERT_EQ(AAUDIO_OK, result);

    // Request stream properties.
    AAudioStreamBuilder_setDeviceId(aaudioBuilder, AAUDIO_DEVICE_UNSPECIFIED);
    AAudioStreamBuilder_setDirection(aaudioBuilder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(aaudioBuilder, requestedSampleRate);
    AAudioStreamBuilder_setSamplesPerFrame(aaudioBuilder, requestedSamplesPerFrame);
    AAudioStreamBuilder_setFormat(aaudioBuilder, requestedDataFormat);
    AAudioStreamBuilder_setSharingMode(aaudioBuilder, requestedSharingMode);
    AAudioStreamBuilder_setBufferCapacityInFrames(aaudioBuilder, 2000);

    // Create an AAudioStream using the Builder.
    result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    if (requestedSharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE
        && result != AAUDIO_OK) {
        return; // EXCLUSIVE just may not be available. Should not crash.
    }
    ASSERT_EQ(AAUDIO_OK, result);
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));

    EXPECT_EQ(AAUDIO_STREAM_STATE_OPEN, AAudioStream_getState(aaudioStream));
    EXPECT_EQ(AAUDIO_DIRECTION_OUTPUT, AAudioStream_getDirection(aaudioStream));

    // Check to see what kind of stream we actually got.
    actualSampleRate = AAudioStream_getSampleRate(aaudioStream);
    ASSERT_GE(actualSampleRate, 44100);
    ASSERT_LE(actualSampleRate, 96000); // TODO what is min/max?

    actualSamplesPerFrame = AAudioStream_getSamplesPerFrame(aaudioStream);
    ASSERT_GE(actualSamplesPerFrame, 1);
    ASSERT_LE(actualSamplesPerFrame, 16); // TODO what is min/max?

    actualSharingMode = AAudioStream_getSharingMode(aaudioStream);
    ASSERT_TRUE(actualSharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE
                || actualSharingMode == AAUDIO_SHARING_MODE_SHARED);

    actualDataFormat = AAudioStream_getFormat(aaudioStream);

    // TODO test this on full build
    // ASSERT_NE(AAUDIO_DEVICE_UNSPECIFIED, AAudioStream_getDeviceId(aaudioStream));

    framesPerBurst = AAudioStream_getFramesPerBurst(aaudioStream);
    ASSERT_GE(framesPerBurst, 16);
    ASSERT_LE(framesPerBurst, 10000); // TODO what is min/max?

    // Allocate a buffer for the audio data.
    // TODO handle possibility of other data formats
    ASSERT_TRUE(actualDataFormat == AAUDIO_FORMAT_PCM_I16);
    size_t dataSizeSamples = framesPerBurst * actualSamplesPerFrame;
    int16_t *data = (int16_t *) calloc(dataSizeSamples, sizeof(int16_t));
    ASSERT_TRUE(nullptr != data);

    actualBufferSize = AAudioStream_getBufferSizeInFrames(aaudioStream);
    actualBufferSize = AAudioStream_setBufferSizeInFrames(aaudioStream, actualBufferSize);
    ASSERT_TRUE(actualBufferSize > 0);

    // Prime the buffer.
    timeoutNanos = 0;
    do {
        framesWritten = AAudioStream_write(aaudioStream, data, framesPerBurst, timeoutNanos);
        // There should be some room for priming the buffer.
        framesTotal += framesWritten;
        ASSERT_GE(framesWritten, 0);
        ASSERT_LE(framesWritten, framesPerBurst);
    } while (framesWritten > 0);
    ASSERT_TRUE(framesTotal > 0);

    // Start/write/pause more than once to see if it fails after the first time.
    // Write some data and measure the rate to see if the timing is OK.
    for (int numLoops = 0; numLoops < 2; numLoops++) {
        // Start and wait for server to respond.
        ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream));
        ASSERT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(aaudioStream,
                                                         AAUDIO_STREAM_STATE_STARTING,
                                                         &state,
                                                         DEFAULT_STATE_TIMEOUT));
        EXPECT_EQ(AAUDIO_STREAM_STATE_STARTED, state);

        // Write some data while we are running. Read counter should be advancing.
        writeLoops = 1 * actualSampleRate / framesPerBurst; // 1 second
        ASSERT_LT(2, writeLoops); // detect absurdly high framesPerBurst
        timeoutNanos = 100 * (NANOS_PER_SECOND * framesPerBurst / actualSampleRate); // N bursts
        framesWritten = 1;
        aaudioFramesRead = AAudioStream_getFramesRead(aaudioStream);
        aaudioFramesRead1 = aaudioFramesRead;
        int64_t beginTime = getNanoseconds(CLOCK_MONOTONIC);
        do {
            framesWritten = AAudioStream_write(aaudioStream, data, framesPerBurst, timeoutNanos);
            ASSERT_EQ(framesWritten, framesPerBurst);

            framesTotal += framesWritten;
            aaudioFramesWritten = AAudioStream_getFramesWritten(aaudioStream);
            EXPECT_EQ(framesTotal, aaudioFramesWritten);

            // Try to get a more accurate measure of the sample rate.
            if (beginTime == 0) {
                aaudioFramesRead = AAudioStream_getFramesRead(aaudioStream);
                if (aaudioFramesRead > aaudioFramesRead1) { // is read pointer advancing
                    beginTime = getNanoseconds(CLOCK_MONOTONIC);
                    aaudioFramesRead1 = aaudioFramesRead;
                }
            }
        } while (framesWritten > 0 && writeLoops-- > 0);

        aaudioFramesRead2 = AAudioStream_getFramesRead(aaudioStream);
        int64_t endTime = getNanoseconds(CLOCK_MONOTONIC);
        ASSERT_GT(aaudioFramesRead2, 0);
        EXPECT_GT(aaudioFramesRead2, aaudioFramesRead1);


        // TODO why is AudioTrack path so inaccurate?
        const double rateTolerance = 200.0; // arbitrary tolerance for sample rate
        if (requestedSharingMode != AAUDIO_SHARING_MODE_SHARED) {
            // Calculate approximate sample rate and compare with stream rate.
            double seconds = (endTime - beginTime) / (double) NANOS_PER_SECOND;
            double measuredRate = (aaudioFramesRead2 - aaudioFramesRead1) / seconds;
            ASSERT_NEAR(actualSampleRate, measuredRate, rateTolerance);
        }

        // Request async pause and wait for server to say that it has completed the pause.
        ASSERT_EQ(AAUDIO_OK, AAudioStream_requestPause(aaudioStream));
        EXPECT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(aaudioStream,
                                                AAUDIO_STREAM_STATE_PAUSING,
                                                &state,
                                                DEFAULT_STATE_TIMEOUT));
        EXPECT_EQ(AAUDIO_STREAM_STATE_PAUSED, state);
    }

    // Make sure the read counter is not advancing when we are paused.
    aaudioFramesRead = AAudioStream_getFramesRead(aaudioStream);
    ASSERT_GE(aaudioFramesRead, aaudioFramesRead2); // monotonic increase

    // Use this to sleep by waiting for a state that won't happen.
    timeoutNanos = 100 * NANOS_PER_MILLISECOND;
    AAudioStream_waitForStateChange(aaudioStream, AAUDIO_STREAM_STATE_OPEN, &state, timeoutNanos);
    aaudioFramesRead2 = AAudioStream_getFramesRead(aaudioStream);
    EXPECT_EQ(aaudioFramesRead, aaudioFramesRead2);

    // ------------------- TEST FLUSH -----------------
    // Prime the buffer.
    timeoutNanos = 0;
    writeLoops = 1000;
    do {
        framesWritten = AAudioStream_write(aaudioStream, data, framesPerBurst, timeoutNanos);
        framesTotal += framesWritten;
    } while (framesWritten > 0 && writeLoops-- > 0);
    EXPECT_EQ(0, framesWritten);

    // Flush and wait for server to respond.
    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestFlush(aaudioStream));
    EXPECT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(aaudioStream,
                                                     AAUDIO_STREAM_STATE_FLUSHING,
                                                     &state,
                                                     DEFAULT_STATE_TIMEOUT));
    EXPECT_EQ(AAUDIO_STREAM_STATE_FLUSHED, state);

    // After a flush, the read counter should be caught up with the write counter.
    aaudioFramesWritten = AAudioStream_getFramesWritten(aaudioStream);
    EXPECT_EQ(framesTotal, aaudioFramesWritten);
    aaudioFramesRead = AAudioStream_getFramesRead(aaudioStream);
    EXPECT_EQ(aaudioFramesRead, aaudioFramesWritten);

    sleep(1); // FIXME - The write returns 0 if we remove this sleep! Why?

    // The buffer should be empty after a flush so we should be able to write.
    framesWritten = AAudioStream_write(aaudioStream, data, framesPerBurst, timeoutNanos);
    // There should be some room for priming the buffer.
    ASSERT_GT(framesWritten, 0);
    ASSERT_LE(framesWritten, framesPerBurst);

    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    free(data);
}

// Test Writing to an AAudioStream using SHARED mode.
TEST(test_aaudio, aaudio_stream_shared) {
    runtest_aaudio_stream(AAUDIO_SHARING_MODE_SHARED);
}

// Test Writing to an AAudioStream using EXCLUSIVE sharing mode. It may fail gracefully.
TEST(test_aaudio, aaudio_stream_exclusive) {
    runtest_aaudio_stream(AAUDIO_SHARING_MODE_EXCLUSIVE);
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);

    return RUN_ALL_TESTS();
}
