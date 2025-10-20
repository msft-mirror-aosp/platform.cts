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

constexpr float kAmplitude = 0.5;
constexpr int kSampleRate = 48000;
constexpr int kDurationInSeconds = 10;
constexpr int kStereoChannelCount = 2;

/**
 * @brief Helper to cast a jlong handle from Java to a native AAudioStream pointer.
 * @return A pointer to the AAudioStream, or nullptr if the handle is invalid.
 */
static inline AAudioStream* nativeHandleToStream(jlong handle) {
    return reinterpret_cast<AAudioStream*>(handle);
}

/**
 * @brief Helper to cast a native AAudioStream pointer to a jlong handle for Java.
 * @return A jlong representing the pointer, or 0 if the stream is nullptr.
 */
static inline jlong streamToNativeHandle(AAudioStream* stream) {
    return reinterpret_cast<jlong>(stream);
}

/**
 * @brief Opens and configures an AAudio stream for offloaded playback.
 * This function creates a stream with properties suitable for power-saving offload mode.
 *
 * @return A jlong handle to the created AAudioStream on success. Returns 0 on failure.
 * The handle is an opaque integer used by Java to reference the native stream object.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_android_media_audio_cts_AudioOffloadNativeEffectsTest_nativeOpenStream(JNIEnv* /*env*/,
                                                                            jclass /*clazz*/) {
    ALOGI("Starting nativeOpenStream");

    // Create a builder
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || builder == nullptr) {
        ALOGE("Failed to create AAudioStreamBuilder: %s", AAudio_convertResultToText(result));
        return 0;
    }

    AAudioStreamBuilder_setBufferCapacityInFrames(builder, kSampleRate * kDurationInSeconds);
    AAudioStreamBuilder_setChannelMask(builder, AAUDIO_CHANNEL_STEREO);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED);
    AAudioStreamBuilder_setSampleRate(builder, kSampleRate);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setSessionId(builder, AAUDIO_SESSION_ID_ALLOCATE);

    // Try to open an offloaded stream
    AAudioStream* stream = nullptr;
    result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || stream == nullptr) {
        ALOGE("Failed to open offloaded stream: %s", AAudio_convertResultToText(result));
        return 0;
    }

    return streamToNativeHandle(stream);
}

/**
 * @brief Retrieves the audio session ID from a given stream handle.
 *
 * @param streamHandle The jlong handle to the AAudioStream.
 * @return The integer audio session ID on success. Returns a negative AAudio error code
 * (e.g., AAUDIO_ERROR_NULL) on failure. A positive session ID is required to attach
 * audio effects.
 */
extern "C" JNIEXPORT jint JNICALL
Java_android_media_audio_cts_AudioOffloadNativeEffectsTest_nativeGetSessionId(JNIEnv* /*env*/,
                                                                              jclass /*clazz*/,
                                                                              jlong streamHandle) {
    AAudioStream* stream = nativeHandleToStream(streamHandle);
    if (stream == nullptr) {
        ALOGE("nativeGetSessionId: Invalid stream handle.");
        return AAUDIO_ERROR_NULL;
    }

    return AAudioStream_getSessionId(stream);
}

/**
 * @brief Generates and plays a sine wave, then signals the end of the stream for offload.
 * This function stops any previous playback, generates a full buffer of sine wave data,
 * starts the stream, writes all the data, and finally calls setOffloadEndOfStream.
 *
 * @param streamHandle The jlong handle to the AAudioStream.
 * @param testFrequencyHz The frequency of the sine wave to generate, in Hz.
 * @return AAUDIO_OK (0) on success. Returns a negative AAudio error code on failure.
 */
extern "C" JNIEXPORT jint JNICALL
Java_android_media_audio_cts_AudioOffloadNativeEffectsTest_nativePlayAndSignalEnd(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong streamHandle, jfloat testFrequencyHz) {
    ALOGI("Starting nativePlayAndSignalEnd");

    AAudioStream* stream = nativeHandleToStream(streamHandle);
    if (stream == nullptr) {
        ALOGE("nativePlayAndSignalEnd: Invalid stream handle.");
        return AAUDIO_ERROR_NULL;
    }

    // Reset stream before playback to ensure it is in a known state.
    AAudioStream_requestStop(stream);

    const int numberOfStreamFrames = kSampleRate * kDurationInSeconds;
    std::vector<float> data(numberOfStreamFrames * kStereoChannelCount);

    // Generate input sine wave of kAmplitude and testFrequency
    for (int i = 0; i < numberOfStreamFrames; i++) {
        float time = (float)i / kSampleRate;
        float sample = kAmplitude * sinf(2.0f * M_PI * testFrequencyHz * time);
        data[i * kStereoChannelCount] = sample;
        data[i * kStereoChannelCount + 1] = sample;
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
    aaudio_result_t result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
        return result;
    }

    int totalFramesWritten = 0;
    int framesLeft = numberOfStreamFrames;
    while (framesLeft > 0) {
        auto framesWritten =
                AAudioStream_write(stream,
                                   static_cast<void*>(
                                           &data[totalFramesWritten * kStereoChannelCount]),
                                   framesLeft, NANOS_PER_SECOND);
        if (framesWritten < 0) {
            ALOGE("Failed to write data, error=%d", framesWritten);
            return framesWritten;
        }
        ALOGV("Write data succeed, frames=%d", framesWritten);
        framesLeft -= framesWritten;
        totalFramesWritten += framesWritten;
    }

    result = AAudioStream_setOffloadEndOfStream(stream);
    if (result != AAUDIO_OK) {
        ALOGE("Failed to set offload end of stream: %s", AAudio_convertResultToText(result));
        return result;
    }

    ALOGI("nativePlayAndSignalEnd finished successfully.");
    return result;
}

/**
 * @brief Stops and closes the AAudio stream, releasing all native resources.
 *
 * @param streamHandle The jlong handle to the AAudioStream to be closed.
 * This function has no return value.
 */
extern "C" JNIEXPORT void JNICALL
Java_android_media_audio_cts_AudioOffloadNativeEffectsTest_nativeCloseStream(JNIEnv* /*env*/,
                                                                             jclass /*clazz*/,
                                                                             jlong streamHandle) {
    ALOGI("nativeCloseStream: Closing stream.");
    AAudioStream* stream = nativeHandleToStream(streamHandle);
    if (stream != nullptr) {
        AAudioStream_requestPause(stream);
        AAudioStream_close(stream);
    }
}
