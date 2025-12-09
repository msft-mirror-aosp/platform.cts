/**
 * Copyright (C) 2026 The Android Open Source Project
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

#define LOG_TAG "GTestNativeService"

#include "GTestNativeService.h"

#include <android/native_service.h>
#include <gtest/gtest.h>
#include <log/log.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <map>
#include <mutex>

namespace android {
namespace cts {

ndk::ScopedAStatus GTestNativeService::runGTest(const ndk::ScopedFileDescriptor& pfd,
                                                const std::vector<std::string>& args) {
    // GTest outputs to stdout and stderr, so we need to redirect them to the given fd.
    if (dup2(pfd.get(), STDOUT_FILENO) == -1 || dup2(pfd.get(), STDERR_FILENO) == -1) {
        ALOGE("Failed to dup2: errno %d", errno);
        return ndk::ScopedAStatus::fromExceptionCodeWithMessage(1, "Failed to dup2");
    }
    std::vector<char*> argv;
    argv.reserve(args.size());
    for (const auto& arg : args) {
        argv.push_back(const_cast<char*>(arg.c_str()));
    }
    int argc = argv.size();
    ::testing::InitGoogleTest(&argc, argv.data());
    int result = RUN_ALL_TESTS();
    if (result == 1) {
        return ndk::ScopedAStatus::fromExceptionCodeWithMessage(1, "GTest failed");
    }
    return ndk::ScopedAStatus::ok();
}

} // namespace cts
} // namespace android

namespace {

static std::mutex gGtestServiceMapMutex;
static std::map<ANativeService*, std::shared_ptr<android::cts::GTestNativeService>>
        gGtestServiceMap;

AIBinder* ANativeService_GTest_OnBindCallback(ANativeService* _Nonnull service,
                                              uint64_t /*bindToken*/,
                                              char const* _Nullable /*action*/,
                                              char const* _Nullable /*data*/) {
    ALOGI("ANativeService_GTest_OnBindCallback called.");
    std::lock_guard<std::mutex> lock(gGtestServiceMapMutex);
    return gGtestServiceMap.at(service)->asBinder().get();
}

bool ANativeService_GTest_OnUnbindCallback(ANativeService* _Nonnull service,
                                           uint64_t /*bindToken*/) {
    ALOGI("ANativeService_GTest_OnUnbindCallback called.");
    std::lock_guard<std::mutex> lock(gGtestServiceMapMutex);
    auto it = gGtestServiceMap.find(service);
    if (it != gGtestServiceMap.end()) {
        // Decrement strong ref count as the service is no longer in use.
        AIBinder_decStrong(it->second->asBinder().get());
        gGtestServiceMap.erase(it);
    }
    return false;
}

} // namespace

extern "C" void native_gtest_runner_service_createService(ANativeService* _Nonnull service) {
    ALOGI("native_gtest_runner_service_createService called.");
    std::lock_guard<std::mutex> lock(gGtestServiceMapMutex);
    std::shared_ptr<android::cts::GTestNativeService> native_service =
            ndk::SharedRefBase::make<android::cts::GTestNativeService>();
    gGtestServiceMap[service] = native_service;
    // Increment strong ref count to ensure the binder object lives as long as the service.
    AIBinder_incStrong(native_service->asBinder().get());

    ANativeService_setOnBindCallback(service, ANativeService_GTest_OnBindCallback);
    ANativeService_setOnUnbindCallback(service, ANativeService_GTest_OnUnbindCallback);
}
