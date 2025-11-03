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

#include <android/log.h>
#include <jni.h>
#include <sys/sysinfo.h>

#include <cassert>
#include <cstring>
#include <vector>

#include "common/OboeDebug.h"
#include "cpu/AudioWorkloadTest.h"
#include "synth/IncludeMeOnce.h"

static AudioWorkloadTest sAudioWorkload;
static jclass g_callbackStatusClass = nullptr;
static jmethodID g_callbackStatusConstructor = nullptr;
static jclass g_arrayListClass = nullptr;
static jmethodID g_arrayListConstructor = nullptr;
static jmethodID g_arrayListAddMethod = nullptr;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_open(JNIEnv* env, jobject thiz) {
    return sAudioWorkload.open();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getFramesPerBurst(JNIEnv* env,
                                                                                jobject thiz) {
    return sAudioWorkload.getFramesPerBurst();
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getSampleRate(
        JNIEnv* env, jobject thiz) {
    return sAudioWorkload.getSampleRate();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getBufferSizeInFrames(JNIEnv* env,
                                                                                    jobject thiz) {
    return sAudioWorkload.getBufferSizeInFrames();
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_start(
        JNIEnv* env, jobject thiz, jint targetDurationMs, jint numBursts, jint numVoices,
        jint numAlternateVoices, jint alternatingPeriodMs, jboolean adpfEnabled,
        jboolean adpfWorkloadIncreaseEnabled, jboolean hearWorkload) {
    return sAudioWorkload.start(targetDurationMs, numBursts, numVoices, numAlternateVoices,
                                alternatingPeriodMs, adpfEnabled, adpfWorkloadIncreaseEnabled,
                                hearWorkload);
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getCpuCount(
        JNIEnv* env, jobject thiz) {
    return AudioWorkloadTest::getCpuCount();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_setCpuAffinityForCallback(
        JNIEnv* env, jobject thiz, jint mask) {
    return AudioWorkloadTest::setCpuAffinityForCallback(mask);
}

JNIEXPORT jint JNICALL Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getXRunCount(
        JNIEnv* env, jobject thiz) {
    return sAudioWorkload.getXRunCount();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getCallbackCount(JNIEnv* env,
                                                                               jobject thiz) {
    return sAudioWorkload.getCallbackCount();
}

JNIEXPORT jlong JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getLastDurationNs(JNIEnv* env,
                                                                                jobject thiz) {
    return sAudioWorkload.getLastDurationNs();
}

JNIEXPORT jboolean JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_isRunning(JNIEnv* env, jobject thiz) {
    return sAudioWorkload.isRunning();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_stop(JNIEnv* env, jobject thiz) {
    return sAudioWorkload.stop();
}

JNIEXPORT jint JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_close(JNIEnv* env, jobject thiz) {
    return sAudioWorkload.close();
}

// Cache jni classes and methods for getCallbackStatistics
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: Failed to get JNIEnv.");
        return JNI_ERR;
    }

    const char* callbackStatusClassName =
            "com/android/cts/verifier/audio/AudioWorkloadTestActivity$CallbackStatus";
    jclass localCallbackStatusClass = env->FindClass(callbackStatusClassName);
    if (localCallbackStatusClass == nullptr) {
        LOGE("JNI_OnLoad: Could not find class %s", callbackStatusClassName);
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }
    // Create a global reference for the class
    g_callbackStatusClass = (jclass)env->NewGlobalRef(localCallbackStatusClass);
    env->DeleteLocalRef(localCallbackStatusClass); // Clean up the local reference
    if (g_callbackStatusClass == nullptr) {
        LOGE("JNI_OnLoad: Could not create global ref for %s", callbackStatusClassName);
        return JNI_ERR;
    }

    g_callbackStatusConstructor = env->GetMethodID(g_callbackStatusClass, "<init>", "(IJJII)V");
    if (g_callbackStatusConstructor == nullptr) {
        LOGE("JNI_OnLoad: Could not find constructor for %s", callbackStatusClassName);
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }

    const char* arrayListClassName = "java/util/ArrayList";
    jclass localArrayListClass = env->FindClass(arrayListClassName);
    if (localArrayListClass == nullptr) {
        LOGE("JNI_OnLoad: Could not find class %s", arrayListClassName);
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }
    g_arrayListClass = (jclass)env->NewGlobalRef(localArrayListClass);
    env->DeleteLocalRef(localArrayListClass); // Clean up local reference
    if (g_arrayListClass == nullptr) {
        LOGE("JNI_OnLoad: Could not create global ref for %s", arrayListClassName);
        return JNI_ERR;
    }

    g_arrayListConstructor = env->GetMethodID(g_arrayListClass, "<init>", "()V");
    if (g_arrayListConstructor == nullptr) {
        LOGE("JNI_OnLoad: Could not find constructor for %s", arrayListClassName);
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }

    g_arrayListAddMethod = env->GetMethodID(g_arrayListClass, "add", "(Ljava/lang/Object;)Z");
    if (g_arrayListAddMethod == nullptr) {
        LOGE("JNI_OnLoad: Could not find 'add' method for %s", arrayListClassName);
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

// Unload the jni classes and methods for getCallbackStatistics
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnUnload: Failed to get JNIEnv.");
        return;
    }

    // Delete global references
    if (g_callbackStatusClass != nullptr) {
        env->DeleteGlobalRef(g_callbackStatusClass);
        g_callbackStatusClass = nullptr;
    }
    if (g_arrayListClass != nullptr) {
        env->DeleteGlobalRef(g_arrayListClass);
        g_arrayListClass = nullptr;
    }

    g_callbackStatusConstructor = nullptr;
    g_arrayListConstructor = nullptr;
    g_arrayListAddMethod = nullptr;
}

JNIEXPORT jobject JNICALL
Java_com_android_cts_verifier_audio_AudioWorkloadTestActivity_getCallbackStatistics(JNIEnv* env,
                                                                                    jobject obj) {
    if (g_callbackStatusClass == nullptr || g_callbackStatusConstructor == nullptr ||
        g_arrayListClass == nullptr || g_arrayListConstructor == nullptr ||
        g_arrayListAddMethod == nullptr) {
        LOGE("Error: JNI IDs not cached. Initialization in JNI_OnLoad might have failed.");
        return nullptr;
    }

    std::vector<AudioWorkloadTest::CallbackStatus> cppCallbackStats =
            sAudioWorkload.getCallbackStatistics();

    jobject javaList = env->NewObject(g_arrayListClass, g_arrayListConstructor);
    if (javaList == nullptr) {
        LOGE("Error: Could not create new ArrayList object.");
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return nullptr;
    }

    for (const auto& status : cppCallbackStats) {
        jobject javaStatus = env->NewObject(g_callbackStatusClass, g_callbackStatusConstructor,
                                            (jint)status.numVoices, (jlong)status.beginTimeNs,
                                            (jlong)status.finishTimeNs, (jint)status.xRunCount,
                                            (jint)status.cpuIndex);
        if (javaStatus == nullptr) {
            LOGE("Error: Could not create new CallbackStatus object.");
            if (env->ExceptionCheck()) env->ExceptionDescribe();
            env->DeleteLocalRef(javaList);
            return nullptr;
        }

        env->CallBooleanMethod(javaList, g_arrayListAddMethod, javaStatus);
        env->DeleteLocalRef(javaStatus);
    }

    return javaList;
}

} // extern "C"
