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
#include <jni.h>

#if defined(__BIONIC__)
#include <sys/system_properties.h>
#endif

#include "android-base/macros.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_android_jvmti_attaching_cts_AttachingTest_runningWithNativeBridge(JNIEnv*, jclass) {
#if defined(__BIONIC__)
    static const prop_info* pi = __system_property_find("ro.dalvik.vm.isa." ABI_STRING);
    return pi != nullptr;
#else
    return false;
#endif // defined(__BIONIC__)
}
