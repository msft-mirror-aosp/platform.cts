/*
 * Copyright 2025 The Android Open Source Project
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

#include <jni.h>
#include <memory>
#include <stdint.h>
#include <sys/sysinfo.h>

#include "JavaNativeFloatFifo.h"

JavaNativeFloatFifo::JavaNativeFloatFifo(uint8_t *dataStorageAddress,
                                         uint32_t capacityInFrames)
        : FifoBuffer(sizeof(float), capacityInFrames,
                     &mReadCounter, &mWriteCounter, dataStorageAddress) {
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_createNativeToken(
        JNIEnv* env, jobject /* thiz */, jobject byteBuffer) {
    void *data = env->GetDirectBufferAddress(byteBuffer);
    if (data == nullptr) return 0;
    int64_t capacity = env->GetDirectBufferCapacity(byteBuffer);
    auto capacityInFloats = (int32_t) (capacity / sizeof(float));
    auto *fifo = new JavaNativeFloatFifo((uint8_t *) data,
                                         capacityInFloats);
    return (jlong)fifo;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_deleteNativeToken(
        JNIEnv* /* env */, jobject /* thiz */, jlong token) {
    auto fifo = reinterpret_cast<JavaNativeFloatFifo*>(token);
    delete fifo;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_getReadCounter(JNIEnv* /* env */,
                                                                         jobject /* thiz */,
                                                                         jlong token) {
    auto fifo = reinterpret_cast<JavaNativeFloatFifo*>(token);
    return (jlong)fifo->getReadCounter();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_getWriteCounter(JNIEnv* /* env */,
                                                                          jobject /* thiz */,
                                                                          jlong token) {
    auto fifo = reinterpret_cast<JavaNativeFloatFifo*>(token);
    return (jlong)fifo->getWriteCounter();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_setReadCounter(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong token,
        jlong count) {
    auto fifo = reinterpret_cast<JavaNativeFloatFifo*>(token);
    fifo->setReadCounter(count);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_JavaNativeFloatFifo_setWriteCounter(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong token,
        jlong count) {
    auto fifo = reinterpret_cast<JavaNativeFloatFifo*>(token);
    fifo->setWriteCounter(count);
}
