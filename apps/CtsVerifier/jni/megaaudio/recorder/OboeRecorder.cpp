/*
 * Copyright 2020 The Android Open Source Project
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
#include <android/log.h>

#include "OboeRecorder.h"

#include "AudioSink.h"

static const char * const TAG = "OboeRecorder(native)";
static const bool LOG = true;

using namespace oboe;

constexpr int32_t kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)

OboeRecorder::OboeRecorder(JavaNativeFloatFifo* floatFifoPtr, int32_t subtype)
        // The Recorder will delete the sink when it is deleted.
        : Recorder(std::make_shared<AppCallbackAudioSink>(floatFifoPtr), subtype),
          mInputPreset(-1)
{}

//
// State
//
StreamBase::Result OboeRecorder::buildStream(int32_t channelCount, int32_t sampleRate,
                        int32_t performanceMode, int32_t sharingMode, int32_t routeDeviceId,
                        int32_t inputPreset)
{
    //TODO much of this could be pulled up into OboeStream.

    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }
    std::lock_guard<std::mutex> lock(mStreamLock);

    oboe::Result result = oboe::Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "ERROR_INVALID_STATE - Stream Already Open");
        return ERROR_INVALID_STATE;
    } else {
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        mRouteDeviceId = routeDeviceId;
        mInputPreset = inputPreset;

        // Create an audio stream
        mBuilder.setChannelCount(mChannelCount);
        mBuilder.setSampleRate(mSampleRate);
        mBuilder.setCallback(this);
        if (mInputPreset != DEFAULT_INPUT_NONE) {
            mBuilder.setInputPreset((enum InputPreset)mInputPreset);
        }
        mBuilder.setPerformanceMode((PerformanceMode) performanceMode);
        mBuilder.setSharingMode((SharingMode) sharingMode);
        mBuilder.setSampleRateConversionQuality(SampleRateConversionQuality::None);
        mBuilder.setDirection(Direction::Input);

        if (mRouteDeviceId != -1) {
            mBuilder.setDeviceId(mRouteDeviceId);
        }

        if (mSubtype == SUB_TYPE_OBOE_AAUDIO) {
            mBuilder.setAudioApi(AudioApi::AAudio);
        } else if (mSubtype == SUB_TYPE_OBOE_OPENSL_ES) {
            mBuilder.setAudioApi(AudioApi::OpenSLES);
        }

        result = oboe::Result::OK;
    }

    if (LOG) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                        __FUNCTION__, OboeErrorToMegaAudioError(result));
    }
    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboeRecorder::openStream() {
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }
    oboe::Result result = mBuilder.openStream(mAudioStream);
    if (result != oboe::Result::OK){
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "openStream failed. Error: %s", convertToText(result));
    } else {
        mBufferSizeInFrames = mAudioStream->getFramesPerBurst();
        mAudioSink->init(mBufferSizeInFrames, mChannelCount);
    }

    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                            __FUNCTION__, OboeErrorToMegaAudioError(result));
    }
    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboeRecorder::startStream() {
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }
    StreamBase::Result result = Recorder::startStream();
    if (result == StreamBase::Result::OK) {
        mAudioSink->start();
    }
    // this is already a StreamBase::Result, so no need to convert
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                            __FUNCTION__, result);
    }
    return result;
}

oboe::DataCallbackResult OboeRecorder::onAudioReady(
        oboe::AudioStream *audioStream, void *audioData, int numFrames) {
    mAudioSink->push((float*)audioData, numFrames, mChannelCount);
    return oboe::DataCallbackResult::Continue;
}

#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_allocNativeRecorder(JNIEnv *env, jobject thiz, jlong nativeFifoPtrLong, jint recorderSubtype) {
    auto nativeFifoPtr = reinterpret_cast<JavaNativeFloatFifo*>(nativeFifoPtrLong);
    OboeRecorder* recorder = new OboeRecorder(nativeFifoPtr, recorderSubtype);
    return (jlong)recorder;
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_deleteNativeRecorder(JNIEnv *env, jobject thiz, jlong native_recorder) {
    delete reinterpret_cast<OboeRecorder*>(native_recorder);
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getBufferFrameCountN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->getNumBufferFrames();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_setInputPresetN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint input_preset) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    oboeRecorder->setInputPreset(input_preset);
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_buildStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint channel_count, jint sample_rate,
        jint performanceMode, jint sharingMode, jint route_device_id, jint input_preset) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->buildStream(
            channel_count, sample_rate, performanceMode, sharingMode, route_device_id,
            input_preset);
}

JNIEXPORT jint JNICALL
        Java_org_hyphonate_megaaudio_recorder_OboeRecorder_openStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->openStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_teardownStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->teardownStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_startStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder, jint recorder_subtype) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->startStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_stopStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->stopStream();
}

JNIEXPORT jint JNICALL
        Java_org_hyphonate_megaaudio_recorder_OboeRecorder_closeStreamN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->closeStream();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_isRecordingN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->isRecording();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getNumBufferFramesN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->getNumBufferFrames();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getRoutedDeviceIdN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->getRoutedDeviceId();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getSharingModeN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->getSharingMode();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getChannelCountN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->getChannelCount();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_isMMapN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return oboeRecorder->isMMap();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getStreamStateN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return (int)oboeRecorder->getState();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getLastErrorCallbackResultN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    auto oboeRecorder = reinterpret_cast<OboeRecorder*>(native_recorder);
    return (int)oboeRecorder->getLastErrorCallbackResult();
}

}   // extern "C"
