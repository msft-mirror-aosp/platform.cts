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

#define LOG_TAG "audio-offload-native"

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>

#include <cmath>
#include <vector>

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

constexpr int64_t NANOS_PER_SECOND = 1'000'000'000;

extern "C" jint Java_android_media_audio_cts_AudioOffloadNativeTest_nativeTestMmapPcmOffload(
        JNIEnv* /*env*/, jclass /*clazz*/) {
    ALOGI("Starting nativeTestMmapPcmOffload");

    AAudioStreamBuilder* builder = nullptr;
    AAudioStream* stream = nullptr;

    // Create a builder
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to create AAudioStreamBuilder: %s", AAudio_convertResultToText(result));
        return result;
    }

    const int sampleRate = 48000;
    const int durationInSeconds = 1;
    const int stereoChannelCount = 2;
    const int numberOfStreamFrames = sampleRate * durationInSeconds;
    const float frequency = 440.0f;
    std::vector<float> data(numberOfStreamFrames * stereoChannelCount);

    for (int i = 0; i < numberOfStreamFrames; i++) {
        float time = (float)i / sampleRate;
        float sample = 0.5f * sinf(2.0f * M_PI * frequency * time);
        data[i * stereoChannelCount] = sample;
        data[i * stereoChannelCount + 1] = sample;
    }

    AAudioStreamBuilder_setBufferCapacityInFrames(builder, numberOfStreamFrames);
    AAudioStreamBuilder_setChannelMask(builder, AAUDIO_CHANNEL_STEREO);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);

    // Try to open an offloaded stream
    result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        ALOGE("Failed to open offloaded stream: %s", AAudio_convertResultToText(result));
        return result;
    }

    int32_t sizeInBursts = 2;
    int32_t framesPerBurst = AAudioStream_getFramesPerBurst(stream);
    int32_t bufferSizeFrames = sizeInBursts * framesPerBurst;

    AAudioStream_setBufferSizeInFrames(stream, bufferSizeFrames);

    int32_t capacityFrames = AAudioStream_getBufferCapacityInFrames(stream);
    ALOGV("actual capacity in frames: %d", capacityFrames);

    if (!AAudioStream_isMMapUsed(stream)) {
        ALOGI("MMap not used by the stream");
    }

    // Start the stream
    result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        AAudioStream_close(stream);
        ALOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
        return result;
    }

    int totalFramesWritten = 0;
    int framesLeft = numberOfStreamFrames;
    while (framesLeft > 0) {
        auto framesWritten =
                AAudioStream_write(stream,
                                   static_cast<void*>(
                                           &data[totalFramesWritten * stereoChannelCount]),
                                   framesLeft, NANOS_PER_SECOND);
        if (framesWritten < 0) {
            ALOGE("Failed to write data, error=%d", framesWritten);
            AAudioStream_close(stream);
            return framesWritten;
        }
        ALOGV("Write data succeed, frames=%d", framesWritten);
        framesLeft -= framesWritten;
        totalFramesWritten += framesWritten;
    }

    result = AAudioStream_setOffloadEndOfStream(stream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to set offload end of stream: %s", AAudio_convertResultToText(result));
    }

    // Cleanup
    AAudioStream_requestStop(stream);
    AAudioStream_close(stream);

    ALOGI("nativeTestMMapPcmOffload finished successfully.");
    return result;
}