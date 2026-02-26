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

#define LOG_TAG "CtsSimpleNativeService"

#include <android/binder_ibinder.h>
#include <dlfcn.h>
#include <log/log.h>
#include <unistd.h>

#include "SimpleNativeService.h"

ndk::ScopedAStatus SimpleNativeService::getPid(int32_t* pid) {
    *pid = getpid();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::getUid(int32_t* uid) {
    *uid = getuid();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::crash() {
    ALOGI("Crashing SimpleNativeService now");
    *((volatile int*)nullptr) = 1;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::redirectStdio(const ndk::ScopedFileDescriptor& stdoutFd,
                                                      const ndk::ScopedFileDescriptor& stderrFd) {
    if (stdoutFd.get() != -1) {
        dup2(stdoutFd.get(), STDOUT_FILENO);
    }
    if (stderrFd.get() != -1) {
        dup2(stderrFd.get(), STDERR_FILENO);
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::loadLibrary(const std::string& libName) {
    ALOGI("Loading library %s", libName.c_str());
    void* handle = dlopen(libName.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        ALOGE("Failed to load library %s: %s", libName.c_str(), dlerror());
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::callFunc(const std::string& funcName) {
    ALOGI("Calling function %s", funcName.c_str());
    void* handle = dlsym(RTLD_DEFAULT, funcName.c_str());
    if (handle == nullptr) {
        ALOGE("Failed to find function %s: %s", funcName.c_str(), dlerror());
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    reinterpret_cast<void (*)()>(handle)();
    return ndk::ScopedAStatus::ok();
}

// This instance doesn't need to be guarded by locks because it's only accessed by ANativeService
// callbacks, which are executed on the main thread.
std::shared_ptr<SimpleNativeService> gService;

extern "C" AIBinder* onBind(ANativeService* _Nonnull /* service */, uint64_t /* bindToken */,
                            char const* _Nullable /* action */, char const* _Nullable /* data */) {
    ndk::SpAIBinder binder = gService->asBinder();
    AIBinder_incStrong(binder.get());
    return binder.get();
}

extern "C" void onDestroy(ANativeService* _Nonnull /* service */) {
    gService = nullptr;
}

extern "C" void ANativeService_onCreate(ANativeService* service) {
    gService = ndk::SharedRefBase::make<SimpleNativeService>();

    ANativeService_setOnBindCallback(service, onBind);
    ANativeService_setOnDestroyCallback(service, onDestroy);
}
