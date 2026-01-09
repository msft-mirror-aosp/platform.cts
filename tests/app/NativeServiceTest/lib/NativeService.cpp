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

#define LOG_TAG "CtsSampleNativeService"

#include "NativeService.h"

#include <android/binder_ibinder.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <log/log.h>
#include <unistd.h>

#include <map>

#include "PreloadedLibrary.h"

#define ASSERT(a, ret)                                             \
    do {                                                           \
        if (!(a)) {                                                \
            ALOGE("Failure: %s at %s:%d", #a, __FILE__, __LINE__); \
            return (ret);                                          \
        }                                                          \
    } while (0)

#define ASSERT_VOID(a)                                             \
    do {                                                           \
        if (!(a)) {                                                \
            ALOGE("Failure: %s at %s:%d", #a, __FILE__, __LINE__); \
            return;                                                \
        }                                                          \
    } while (0)

enum class ServiceBindingState {
    kBound,
    kUnbound,
    kRebound,
};

struct ServiceBinding {
    ServiceBindingState mState;
    uint64_t mToken;
    std::string mIntentAction;
    std::shared_ptr<NativeServiceWrapper> mWrapper;

    ServiceBinding(uint64_t token, const char* action,
                   std::shared_ptr<NativeServiceWrapper> wrapper)
          : mState(ServiceBindingState::kBound),
            mToken(token),
            mIntentAction(action),
            mWrapper(wrapper) {}
};

// This map doesn't have to be guarded by a mutex because it's only accessed in
// native service callbacks and they are serialized in the main thread looper.
static std::map<ANativeService*, std::map<uint64_t, ServiceBinding>> gBindingMap;

ndk::ScopedAStatus NativeServiceWrapper::registerListener(
        const std::shared_ptr<INativeServiceListener>& listener) {
    std::lock_guard<std::mutex> lock(mutex_);
    listeners_.insert(listener);
    listener->onRegister();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus NativeServiceWrapper::isLibraryMarkedPreloaded(bool* result) {
    *result = isMarkedPreloaded();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus NativeServiceWrapper::getParentPid(int* result) {
    *result = getppid();
    return ndk::ScopedAStatus::ok();
}

void NativeServiceWrapper::doUnbind() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (const auto& listener : listeners_) {
        listener->onUnbind();
    }
}

void NativeServiceWrapper::doRebind() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (const auto& listener : listeners_) {
        listener->onRebind();
    }
}

extern "C" void native_service_onDestroy(ANativeService* _Nonnull service) {
    ALOGI("native_service_onDestroy – service %p", service);

    auto itr = gBindingMap.find(service);
    ASSERT_VOID(itr != gBindingMap.end());

    for (auto& binding : itr->second) {
        AIBinder_decStrong(binding.second.mWrapper->asBinder().get());
    }

    gBindingMap.erase(itr);
}

extern "C" AIBinder* native_service_onBind(ANativeService* _Nonnull service, uint64_t bindToken,
                                           char const* _Nullable action,
                                           char const* _Nullable data) {
    ALOGI("native_service_onBind – service %p, bindToken %" PRIu64 ", action: %s, data %s", service,
          bindToken, action, data);

    std::shared_ptr<NativeServiceWrapper> wrapper;

    if (gBindingMap.find(service) != gBindingMap.end() &&
        gBindingMap[service].find(bindToken) != gBindingMap[service].end()) {
        auto& conn = gBindingMap[service].find(bindToken)->second;
        ASSERT(conn.mState == ServiceBindingState::kUnbound, nullptr);
        wrapper = conn.mWrapper;
        conn.mState = ServiceBindingState::kBound;
    } else {
        wrapper = ndk::SharedRefBase::make<NativeServiceWrapper>();
        gBindingMap[service].emplace(bindToken, ServiceBinding(bindToken, action, wrapper));
    }
    AIBinder_incStrong(wrapper->asBinder().get());

    ASSERT(action != nullptr, nullptr);
    auto actionStr = std::string(action);
    ASSERT(actionStr == "TEST_ACTION 🂡 🂢 🂣" || actionStr == "TEST_ACTION_NOREBIND" ||
                   actionStr == "TEST_ACTION_REBIND" || actionStr == "TEST_ACTION_KEEPALIVE",
           nullptr);
    if (actionStr == "TEST_ACTION_KEEPALIVE") {
        ASSERT(data == nullptr, nullptr);
    } else {
        ASSERT(std::string(data) ==
                       R"(content://com.example/people/%F0%9F%82%A1%20%F0%9F%82%A2%20%F0%9F%82%A3)",
               nullptr);
    }

    return wrapper->asBinder().get();
}

extern "C" void native_service_onRebind(ANativeService* _Nonnull service, uint64_t bindToken) {
    ALOGI("native_service_onRebind – service %p, bindToken %" PRIu64, service, bindToken);

    auto itr = gBindingMap.find(service);
    ASSERT_VOID(itr != gBindingMap.end());

    auto bindingItr = itr->second.find(bindToken);
    ASSERT_VOID(bindingItr != itr->second.end());
    ASSERT_VOID(bindingItr->second.mState == ServiceBindingState::kUnbound);

    bindingItr->second.mState = ServiceBindingState::kRebound;

    auto wrapper = bindingItr->second.mWrapper;
    wrapper->doRebind();
}

extern "C" bool native_service_onUnbind(ANativeService* _Nonnull service, uint64_t bindToken) {
    ALOGI("native_service_onUnbind – service %p, bindToken %" PRIu64, service, bindToken);

    bool res = false;

    auto itr = gBindingMap.find(service);
    ASSERT(itr != gBindingMap.end(), false);

    auto bindingItr = itr->second.find(bindToken);
    ASSERT(bindingItr != itr->second.end(), false);
    ASSERT(bindingItr->second.mState == ServiceBindingState::kBound ||
                   bindingItr->second.mState == ServiceBindingState::kRebound,
           false);

    bindingItr->second.mState = ServiceBindingState::kUnbound;
    if (bindingItr->second.mIntentAction == "TEST_ACTION_REBIND") {
        res = true;
    }

    auto wrapper = bindingItr->second.mWrapper;
    wrapper->doUnbind();
    // Don't call `AIBinder_decStrong` here since the IBinder is cached in the AMS and may be
    // reused.

    ALOGI("native_service_onUnbind – res %d", res);
    return res;
}

extern "C" void native_service_onTrimMemory(ANativeService* _Nonnull service,
                                            ANativeServiceTrimMemoryLevel level) {
    ALOGI("native_service_onTrimMemory – service %p, level %d", service, level);
}

extern "C" void native_service_createService(ANativeService* service) {
    ALOGI("native_service_createService – service %p", service);

    ANativeService_setOnBindCallback(service, native_service_onBind);
    ANativeService_setOnUnbindCallback(service, native_service_onUnbind);
    ANativeService_setOnRebindCallback(service, native_service_onRebind);
    ANativeService_setOnDestroyCallback(service, native_service_onDestroy);
    ANativeService_setOnTrimMemoryCallback(service, native_service_onTrimMemory);
}
