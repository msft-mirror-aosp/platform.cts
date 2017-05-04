/*
 * Copyright 2017 The Android Open Source Project
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
#include <atomic>
#include <utils/Log.h>

#include <aaudio/AAudio.h>
#include "test_aaudio.h"

typedef struct AAudioCallbackTestData {
    int32_t expectedFramesPerCallback;
    int32_t actualFramesPerCallback;
    int32_t minLatency;
    int32_t maxLatency;
    std::atomic<aaudio_result_t> callbackError;
    std::atomic<int32_t> callbackCount;
} AAudioCallbackTestData;

static int32_t measureLatency(AAudioStream *stream) {
    int64_t presentationTime = 0;
    int64_t presentationPosition = 0;
    int64_t now = getNanoseconds();
    int32_t sampleRate = AAudioStream_getSampleRate(stream);
    int64_t framesWritten = AAudioStream_getFramesWritten(stream);
    aaudio_result_t result = AAudioStream_getTimestamp(stream,
                                                       CLOCK_MONOTONIC,
                                                       &presentationPosition,
                                                       &presentationTime);
    if (result < 0) {
        return result;
    }
    // Calculate when the last frame written would be played.
    int64_t deltaFrames = framesWritten - presentationPosition;
    EXPECT_GE(framesWritten, presentationPosition);
    int64_t calculatedDeltaNanos = deltaFrames * NANOS_PER_SECOND / sampleRate;
    int64_t calculatedTimeNanos = presentationTime +  calculatedDeltaNanos;
    int64_t latencyNanos = calculatedTimeNanos - now;
    int32_t latencyMillis = (int32_t) ((latencyNanos + NANOS_PER_MILLISECOND - 1)
                            / NANOS_PER_MILLISECOND);
    return latencyMillis;
}

static void MyErrorCallbackProc(
        AAudioStream *stream,
        void *userData,
        aaudio_result_t error) {
    (void) stream;
    AAudioCallbackTestData *myData = (AAudioCallbackTestData *) userData;
    myData->callbackError = error;

}

// Callback function that fills the audio output buffer.
static aaudio_data_callback_result_t MyDataCallbackProc(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames
) {
    AAudioCallbackTestData *myData = (AAudioCallbackTestData *) userData;

    if (numFrames != myData->expectedFramesPerCallback) {
        // record unexpected framecounts
        myData->actualFramesPerCallback = numFrames;
    } else if (myData->actualFramesPerCallback == 0) {
        // record at least one frame count
        myData->actualFramesPerCallback = numFrames;
    }
    int32_t samplesPerFrame = AAudioStream_getSamplesPerFrame(stream);
    int32_t numSamples = samplesPerFrame * numFrames;
    if (AAudioStream_getFormat(stream) == AAUDIO_FORMAT_PCM_I16) {
        int16_t *shortData = (int16_t *) audioData;
        for (int i = 0; i < numSamples; i++) *shortData++ = 0;
    } else if (AAudioStream_getFormat(stream) == AAUDIO_FORMAT_PCM_FLOAT) {
        float *floatData = (float *) audioData;
        for (int i = 0; i < numSamples; i++) *floatData++ = 0.0f;
    }

    int32_t latency = measureLatency(stream);
    if (latency > 0) {
        if (latency < myData->minLatency) {
            myData->minLatency = latency;
        }
        if (latency > myData->maxLatency) {
            myData->maxLatency = latency;
        }
    }

    myData->callbackCount++;
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// Test Writing to an AAudioStream using a Callback
void runtest_aaudio_callback(aaudio_sharing_mode_t requestedSharingMode,
                             int32_t framesPerDataCallback) {
    AAudioCallbackTestData myTestData;
    const int32_t requestedSampleRate = 48000;
    const int32_t requestedSamplesPerFrame = 2;
    const aaudio_audio_format_t requestedDataFormat = AAUDIO_FORMAT_PCM_I16;

    int32_t actualSampleRate = -1;
    int32_t actualSamplesPerFrame = -1;
    aaudio_audio_format_t actualDataFormat = AAUDIO_FORMAT_INVALID;
    aaudio_sharing_mode_t actualSharingMode;
    int32_t framesPerBurst = -1;
    int32_t actualBufferSize = 0;
    int32_t actualFramesPerDataCallback = 0;

    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;
    AAudioStreamBuilder *builder = nullptr;
    AAudioStream *stream = nullptr;

    aaudio_result_t result = AAUDIO_OK;
    myTestData.callbackCount.store(0);
    myTestData.callbackError = AAUDIO_OK;
    myTestData.actualFramesPerCallback = 0;
    myTestData.expectedFramesPerCallback = 0;

    // Use an AAudioStreamBuilder to define the stream.
    result = AAudio_createStreamBuilder(&builder);
    ASSERT_EQ(AAUDIO_OK, result);

    // Request stream properties.
    AAudioStreamBuilder_setDeviceId(builder, AAUDIO_DEVICE_UNSPECIFIED);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, requestedSampleRate);
    AAudioStreamBuilder_setSamplesPerFrame(builder, requestedSamplesPerFrame);
    AAudioStreamBuilder_setFormat(builder, requestedDataFormat);
    AAudioStreamBuilder_setSharingMode(builder, requestedSharingMode);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, 2000);

    AAudioStreamBuilder_setErrorCallback(builder, MyErrorCallbackProc, &myTestData);
    AAudioStreamBuilder_setDataCallback(builder, MyDataCallbackProc, &myTestData);
    if (framesPerDataCallback != AAUDIO_UNSPECIFIED) {
        AAudioStreamBuilder_setFramesPerDataCallback(builder, framesPerDataCallback);
    }

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(builder, &stream));
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(builder));

    EXPECT_EQ(AAUDIO_STREAM_STATE_OPEN, AAudioStream_getState(stream));
    EXPECT_EQ(AAUDIO_DIRECTION_OUTPUT, AAudioStream_getDirection(stream));

    // Check to see what kind of stream we actually got.
    actualSampleRate = AAudioStream_getSampleRate(stream);
    ASSERT_TRUE(actualSampleRate >= 44100 && actualSampleRate <= 96000);  // TODO what is range?

    actualSamplesPerFrame = AAudioStream_getSamplesPerFrame(stream);
    ASSERT_TRUE(actualSamplesPerFrame >= 1 && actualSamplesPerFrame <= 16); // TODO what is max?

    actualSharingMode = AAudioStream_getSharingMode(stream);
    ASSERT_TRUE(actualSharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE
                || actualSharingMode == AAUDIO_SHARING_MODE_SHARED);

    actualDataFormat = AAudioStream_getFormat(stream);

    // TODO Why does getDeviceId() always return 0?
    // EXPECT_NE(AAUDIO_DEVICE_UNSPECIFIED, AAudioStream_getDeviceId(stream));

    framesPerBurst = AAudioStream_getFramesPerBurst(stream);
    ASSERT_TRUE(framesPerBurst >= 16 && framesPerBurst <= 1024); // TODO what is min/max?

    actualFramesPerDataCallback = AAudioStream_getFramesPerDataCallback(stream);
    if (framesPerDataCallback != AAUDIO_UNSPECIFIED) {
        ASSERT_EQ(framesPerDataCallback, actualFramesPerDataCallback);
    }

    actualBufferSize = AAudioStream_getBufferSizeInFrames(stream);
    actualBufferSize = AAudioStream_setBufferSizeInFrames(stream, actualBufferSize);
    ASSERT_TRUE(actualBufferSize > 0);

    // Start/stop more than once to see if it fails after the first time.
    // Write some data and measure the rate to see if the timing is OK.
    for (int loopIndex = 0; loopIndex < 2; loopIndex++) {

        myTestData.callbackCount = 0;
        myTestData.minLatency = INT32_MAX;
        myTestData.maxLatency = 0;
        myTestData.callbackCount.store(0);

        myTestData.expectedFramesPerCallback = actualFramesPerDataCallback;

        // Start and wait for server to respond.
        ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(stream));

        ASSERT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(stream,
                                                             AAUDIO_STREAM_STATE_STARTING,
                                                             &state,
                                                             DEFAULT_STATE_TIMEOUT));
        EXPECT_EQ(AAUDIO_STREAM_STATE_STARTED, state);

        sleep(2); // let the stream run

        ASSERT_EQ(myTestData.callbackError.load(), AAUDIO_OK);
        ASSERT_GT(myTestData.callbackCount.load(), 10);

        // For more coverage, alternate pausing and stopping.
        if ((loopIndex & 1) == 0) {
            // Request async pause and wait for server to say that it has completed the request.
            ASSERT_EQ(AAUDIO_OK, AAudioStream_requestPause(stream));
            EXPECT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(stream,
                                                                 AAUDIO_STREAM_STATE_PAUSING,
                                                                 &state,
                                                                 DEFAULT_STATE_TIMEOUT));
            EXPECT_EQ(AAUDIO_STREAM_STATE_PAUSED, state);
        } else {
            // Request async stop and wait for server to say that it has completed the request.
            ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStop(stream));
            EXPECT_EQ(AAUDIO_OK, AAudioStream_waitForStateChange(stream,
                                                                 AAUDIO_STREAM_STATE_STOPPING,
                                                                 &state,
                                                                 DEFAULT_STATE_TIMEOUT));
            EXPECT_EQ(AAUDIO_STREAM_STATE_STOPPED, state);
        }

        int32_t oldCallbackCount = myTestData.callbackCount.load();
        EXPECT_GT(oldCallbackCount, 10);
        sleep(1);
        EXPECT_EQ(oldCallbackCount, myTestData.callbackCount.load()); // expect not advancing

        if (framesPerDataCallback != AAUDIO_UNSPECIFIED) {
            ASSERT_EQ(framesPerDataCallback, myTestData.actualFramesPerCallback);
        }

        EXPECT_GE(myTestData.minLatency, 1);   // Absurdly low
        EXPECT_LE(myTestData.maxLatency, 200); // Absurdly high, should be < 30
        //printf("latency: %d, %d\n", myTestData.minLatency, myTestData.maxLatency);
    }

    ASSERT_EQ(myTestData.callbackError.load(), AAUDIO_OK);

    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(stream));
}

// Test Using an AAudioStream callback in SHARED mode.
TEST(test_aaudio, aaudio_callback_shared_unspecified) {
    runtest_aaudio_callback(AAUDIO_SHARING_MODE_SHARED, AAUDIO_UNSPECIFIED);
}

TEST(test_aaudio, aaudio_callback_shared_109) {
    runtest_aaudio_callback(AAUDIO_SHARING_MODE_SHARED, 109); // arbitrary prime number < 192
}

TEST(test_aaudio, aaudio_callback_shared_223) {
    runtest_aaudio_callback(AAUDIO_SHARING_MODE_SHARED, 223); // arbitrary prime number > 192
}
