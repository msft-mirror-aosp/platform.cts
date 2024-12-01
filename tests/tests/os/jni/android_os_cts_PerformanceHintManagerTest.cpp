/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <android/native_window_jni.h>
#include <android/performance_hint.h>
#include <android/surface_control_jni.h>
#include <errno.h>
#include <jni.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <future>
#include <list>
#include <sstream>
#include <vector>

static jstring toJString(JNIEnv *env, const char* c_str) {
    return env->NewStringUTF(c_str);
}

constexpr int64_t DEFAULT_TARGET_NS = 16666666L;

template <class T, void (*D)(T*)>
std::shared_ptr<T> wrapSP(T* incoming) {
    return std::shared_ptr<T>(incoming, [](T* ptr) { D(ptr); });
}

// Preferable to template specialization bc if we wrap something unsupported, it's more obvious
constexpr auto&& wrapSession = wrapSP<APerformanceHintSession, APerformanceHint_closeSession>;
constexpr auto&& wrapConfig = wrapSP<ASessionCreationConfig, ASessionCreationConfig_release>;
constexpr auto&& wrapSurfaceControl = wrapSP<ASurfaceControl, ASurfaceControl_release>;
constexpr auto&& wrapNativeWindow = wrapSP<ANativeWindow, ANativeWindow_release>;

std::shared_ptr<APerformanceHintSession> createSession(APerformanceHintManager* manager) {
    int32_t pid = getpid();
    return wrapSession(APerformanceHint_createSession(manager, &pid, 1u, DEFAULT_TARGET_NS));
}

std::shared_ptr<APerformanceHintSession> createSessionWithConfig(
        APerformanceHintManager* manager, std::shared_ptr<ASessionCreationConfig> config) {
    return wrapSession(APerformanceHint_createSessionUsingConfig(manager, config.get()));
}

std::shared_ptr<ASessionCreationConfig> createConfig() {
    return wrapConfig(ASessionCreationConfig_create());
}

struct WorkDurationCreator {
    int64_t workPeriodStart;
    int64_t totalDuration;
    int64_t cpuDuration;
    int64_t gpuDuration;
};

struct ConfigCreator {
    std::vector<int32_t> tids{getpid()};
    int64_t targetDuration = DEFAULT_TARGET_NS;
    bool powerEfficient = false;
    bool graphicsPipeline = false;
    std::vector<ANativeWindow*> nativeWindows{};
    std::vector<ASurfaceControl*> surfaceControls{};
    bool autoCpu = false;
    bool autoGpu = false;
};

std::shared_ptr<ASessionCreationConfig> configFromCreator(ConfigCreator& creator, std::string& out,
                                                          int& failureCode) {
    auto config = createConfig();

#define WRAP_FAILURE(featureName)                                                           \
    if (failureCode != 0) {                                                                 \
        if (failureCode != ENOTSUP) {                                                       \
            out += std::format("setting {} failed with code {}", featureName, failureCode); \
        }                                                                                   \
        return nullptr;                                                                     \
    }

    failureCode =
            ASessionCreationConfig_setTids(config.get(), creator.tids.data(), creator.tids.size());
    WRAP_FAILURE("tids")

    if (creator.targetDuration > 0) {
        failureCode = ASessionCreationConfig_setTargetWorkDurationNanos(config.get(),
                                                                        creator.targetDuration);
        WRAP_FAILURE("target duration")
    }

    if (creator.powerEfficient) {
        failureCode = ASessionCreationConfig_setPreferPowerEfficiency(config.get(),
                                                                      creator.powerEfficient);
        WRAP_FAILURE("power efficient")
    }

    if (creator.graphicsPipeline) {
        failureCode =
                ASessionCreationConfig_setGraphicsPipeline(config.get(), creator.graphicsPipeline);
        WRAP_FAILURE("graphics pipeline")
    }

    if (creator.nativeWindows.size() > 0 || creator.surfaceControls.size() > 0) {
        failureCode =
                ASessionCreationConfig_setNativeSurfaces(config.get(),
                                                         creator.nativeWindows.size() > 0
                                                                 ? creator.nativeWindows.data()
                                                                 : nullptr,
                                                         creator.nativeWindows.size(),
                                                         creator.surfaceControls.size() > 0
                                                                 ? creator.surfaceControls.data()
                                                                 : nullptr,
                                                         creator.surfaceControls.size());
        WRAP_FAILURE("native surfaces")
    }

    if (creator.autoCpu || creator.autoGpu) {
        failureCode = ASessionCreationConfig_setUseAutoTiming(config.get(), creator.autoCpu,
                                                              creator.autoGpu);
        WRAP_FAILURE("auto mode")
    }

    return config;
}

static AWorkDuration* createWorkDuration(WorkDurationCreator creator) {
    AWorkDuration* out = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(out, creator.workPeriodStart);
    AWorkDuration_setActualTotalDurationNanos(out, creator.totalDuration);
    AWorkDuration_setActualCpuDurationNanos(out, creator.cpuDuration);
    AWorkDuration_setActualGpuDurationNanos(out, creator.gpuDuration);
    return out;
}

static jstring nativeTestCreateHintSession(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto a = createSession(manager);
    auto b = createSession(manager);
    if (a == nullptr) {
        return toJString(env, "a is null");
    } else if (b == nullptr) {
        return toJString(env, "b is null");
    } else if (a.get() == b.get()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestCreateHintSessionUsingConfig(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto config = createConfig();
    if (config == nullptr) {
        return toJString(env, "config is null");
    }
    int32_t pid = getpid();
    int ret = ASessionCreationConfig_setTids(config.get(), &pid, 1u);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTids ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setTargetWorkDurationNanos(config.get(), DEFAULT_TARGET_NS);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTargetWorkDurationNanos ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    auto a = wrapSession(APerformanceHint_createSessionUsingConfig(manager, config.get()));
    auto b = wrapSession(APerformanceHint_createSessionUsingConfig(manager, config.get()));
    if (a == nullptr) {
        return toJString(env, "a is null");
    } else if (b == nullptr) {
        return toJString(env, "b is null");
    } else if (a.get() == b.get()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestGetPreferredUpdateRateNanos(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session != nullptr) {
        bool positive = APerformanceHint_getPreferredUpdateRateNanos(manager) > 0;
        if (!positive)
          return toJString(env, "preferred rate is not positive");
    } else {
        if (APerformanceHint_getPreferredUpdateRateNanos(manager) != -1)
          return toJString(env, "preferred rate is not -1");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "updateTargetWorkDuration did not return 0");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDurationWithNegativeDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(session.get(), -1);
    if (result != EINVAL) {
        return toJString(env, "updateTargetWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeReportActualWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 1);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 1000);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1000) did not return 0");
    }

    return nullptr;
}

static jstring nativeReportActualWorkDurationWithIllegalArgument(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(session.get(), -1);
    if (result != EINVAL) {
        return toJString(env, "reportActualWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeTestSetThreadsWithInvalidTid(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) {
        return toJString(env, "null manager");
    }
    auto session = createSession(manager);
    if (session == nullptr) {
        return nullptr;
    }

    std::vector<pid_t> tids;
    tids.push_back(2);
    int result = APerformanceHint_setThreads(session.get(), tids.data(), 1);
    if (result != EPERM) {
        return toJString(env, "setThreads did not return EPERM");
    }
    return nullptr;
}


static jstring nativeSetPreferPowerEfficiency(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_setPreferPowerEfficiency(session.get(), false);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(false) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(session.get(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(session.get(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }
    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    std::vector<WorkDurationCreator> testCases = {
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1016,
            .totalDuration = 14,
            .cpuDuration = 12,
            .gpuDuration = 4
        },
        {
            .workPeriodStart = 1016,
            .totalDuration = 14,
            .cpuDuration = 12,
            .gpuDuration = 4
        },
        {
            .workPeriodStart = 900,
            .totalDuration = 20,
            .cpuDuration = 20,
            .gpuDuration = 0
        },
        {
            .workPeriodStart = 800,
            .totalDuration = 20,
            .cpuDuration = 0,
            .gpuDuration = 20
        }
    };

    for (auto && testCase : testCases) {
        AWorkDuration* testObj = createWorkDuration(testCase);
        int result = APerformanceHint_reportActualWorkDuration2(session.get(), testObj);
        if (result != 0) {
            std::stringstream stream;
            stream << "APerformanceHint_reportActualWorkDuration2({"
            << "workPeriodStartTimestampNanos = " << testCase.workPeriodStart << ", "
            << "actualTotalDurationNanos = " << testCase.totalDuration << ", "
            << "actualCpuDurationNanos = " << testCase.cpuDuration << ", "
            << "actualGpuDurationNanos = " << testCase.gpuDuration << "}) did not return 0";
            AWorkDuration_release(testObj);
            return toJString(env, stream.str().c_str());
        }
        AWorkDuration_release(testObj);
    }

    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2WithIllegalArgument(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    std::vector<WorkDurationCreator> testCases = {
        {
            .workPeriodStart = -1,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = -1,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = -1,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = -1
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 0,
            .gpuDuration = 0
        }
    };

    for (auto && testCase : testCases) {
        AWorkDuration* testObj = createWorkDuration(testCase);
        int result = APerformanceHint_reportActualWorkDuration2(session.get(), testObj);
        if (result != EINVAL) {
            std::stringstream stream;
            stream << "APerformanceHint_reportActualWorkDuration2({"
            << "workPeriodStartTimestampNanos = " << testCase.workPeriodStart << ", "
            << "actualTotalDurationNanos = " << testCase.totalDuration << ", "
            << "actualCpuDurationNanos = " << testCase.cpuDuration << ", "
            << "actualGpuDurationNanos = " << testCase.gpuDuration << "}) did not return EINVAL";
            AWorkDuration_release(testObj);
            return toJString(env, stream.str().c_str());
        }
        AWorkDuration_release(testObj);
    }

    return nullptr;
}

static jstring nativeTestLoadHints(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_notifyWorkloadIncrease(session.get(), true, false, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(session.get(), false, true, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadReset(false, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(session.get(), true, true, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(session.get(), false, false, "CTS hint");
    if (result != EINVAL && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(false, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(session.get(), false, false, "CTS hint");
    if (result != EINVAL && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadReset(false, false) returned {}", result)
                                 .c_str());
    }

    return nullptr;
}

static jlong nativeBorrowSessionFromJava(JNIEnv* env, jobject, jobject sessionObj) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return 0;

    APerformanceHintSession* session = APerformanceHint_borrowSessionFromJava(env, sessionObj);

    // Test a basic synchronous operation to ensure the session is valid.
    int32_t pid = getpid();
    int retval = APerformanceHint_setThreads(session, &pid, 1u);
    if (retval != 0) {
        return 0;
    }

    return reinterpret_cast<jlong>(session);
}
static jstring nativeTestCreateGraphicsPipelineSessionOverLimit(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    const int count = APerformanceHint_getMaxGraphicsPipelineThreadsCount(manager);

    auto config = createConfig();
    int32_t pid = getpid();
    int ret = ASessionCreationConfig_setTids(config.get(), &pid, 1u);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTids ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setTargetWorkDurationNanos(config.get(), DEFAULT_TARGET_NS);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTargetWorkDurationNanos ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setGraphicsPipeline(config.get(), true);
    if (ret == EINVAL) {
        return toJString(env, "creation config setGraphicsPipeline ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }

    return nullptr;
}

static jstring nativeTestSetNativeSurfaces(JNIEnv* env, jobject, jobject surfaceControlFromJava,
                                           jobject surfaceFromJava) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    std::string out;
    int errCode;

    auto surfaceControl = wrapSurfaceControl(ASurfaceControl_fromJava(env, surfaceControlFromJava));
    auto nativeWindow = wrapNativeWindow(ANativeWindow_fromSurface(env, surfaceFromJava));

    // Test native window session creation
    ConfigCreator nativeWindowCreator{
            .graphicsPipeline = true,
            .nativeWindows = {nativeWindow.get()},
    };

    auto nativeWindowConfig = configFromCreator(nativeWindowCreator, out, errCode);
    if (nativeWindowConfig == nullptr) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env, out.c_str());
    }

    auto nativeWindowSession = createSessionWithConfig(manager, nativeWindowConfig);
    if (nativeWindowSession == nullptr) {
        return toJString(env, "Session failed to be created with ANativeWindow");
    }

    // Test surfaceControl session creation
    ConfigCreator surfaceControlCreator{
            .graphicsPipeline = true,
            .surfaceControls = {surfaceControl.get()},
    };

    auto surfaceControlConfig = configFromCreator(surfaceControlCreator, out, errCode);
    if (surfaceControlConfig == nullptr) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env, out.c_str());
    }

    auto surfaceControlSession = createSessionWithConfig(manager, surfaceControlConfig);
    if (surfaceControlSession == nullptr) {
        return toJString(env, "Session failed to be created with ASurfaceControl");
    }

    // Test both
    ConfigCreator bothCreator{
            .graphicsPipeline = true,
            .nativeWindows = {nativeWindow.get()},
            .surfaceControls = {surfaceControl.get()},
    };

    auto bothConfig = configFromCreator(surfaceControlCreator, out, errCode);
    if (bothConfig == nullptr) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env, out.c_str());
    }

    auto bothSession = createSessionWithConfig(manager, bothConfig);
    if (bothSession == nullptr) {
        return toJString(env,
                         "Session failed to be created with both ASurfaceControl and "
                         "ANativeWindow");
    }

    // Test unsetting
    errCode = APerformanceHint_setNativeSurfaces(bothSession.get(), nullptr, 0, nullptr, 0);
    if (errCode != 0) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env,
                         std::format("Setting empty native surfaces failed with: {}", errCode)
                                 .c_str());
    }

    // Test setting it back
    ANativeWindow* windowArr[]{nativeWindow.get()};
    ASurfaceControl* controlArr[]{surfaceControl.get()};

    errCode = APerformanceHint_setNativeSurfaces(bothSession.get(), windowArr, 1, controlArr, 1);
    if (errCode != 0) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env,
                         std::format("Setting native surfaces on re-used empty session failed "
                                     "with: {}",
                                     errCode)
                                 .c_str());
    }

    // Create new empty sessions
    ConfigCreator unassociatedCreator{
            .graphicsPipeline = true,
    };

    auto unassociatedConfig = configFromCreator(unassociatedCreator, out, errCode);
    if (unassociatedConfig == nullptr) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env, out.c_str());
    }

    auto unassociatedSession = createSessionWithConfig(manager, unassociatedConfig);
    if (unassociatedSession == nullptr) {
        return toJString(env,
                         "Session failed to be created while unassociated to a native surface");
    }

    errCode = APerformanceHint_setNativeSurfaces(nativeWindowSession.get(), windowArr, 1,
                                                 controlArr, 1);
    if (errCode != 0) {
        if (errCode == ENOTSUP) {
            return nullptr;
        }
        return toJString(env,
                         std::format("Setting native surfaces on fresh unassociated session failed "
                                     "with: {}",
                                     errCode)
                                 .c_str());
    }

    return nullptr;
}

static jstring nativeTestAutoSessionTiming(JNIEnv* env, jobject, jobject surfaceControlFromJava) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    std::string out;
    int errCode;

    auto surfaceControl = wrapSurfaceControl(ASurfaceControl_fromJava(env, surfaceControlFromJava));

    // Create a surfaceControl-associated session for auto GPU
    ConfigCreator autoCpuSessionCreator{
            .graphicsPipeline = true,
            .surfaceControls = {surfaceControl.get()},
            .autoCpu = true,
    };

    auto autoCpuSessionConfig = configFromCreator(autoCpuSessionCreator, out, errCode);
    if (autoCpuSessionConfig == nullptr) {
        return toJString(env, out.c_str());
    }

    auto autoCpuSession = createSessionWithConfig(manager, autoCpuSessionConfig);
    if (autoCpuSessionConfig == nullptr) {
        return toJString(env, "Failed to create auto cpu+gpu session!");
    }

    // Create a surfaceControl-associated session for auto GPU
    ConfigCreator autoGpuSessionCreator{
            .graphicsPipeline = true,
            .surfaceControls = {surfaceControl.get()},
            .autoGpu = true,
    };

    auto autoGpuSessionConfig = configFromCreator(autoGpuSessionCreator, out, errCode);
    if (autoGpuSessionConfig == nullptr) {
        return toJString(env, out.c_str());
    }

    auto autoGpuSession = createSessionWithConfig(manager, autoGpuSessionConfig);
    if (autoGpuSessionConfig == nullptr) {
        return toJString(env, "Failed to create auto cpu+gpu session!");
    }

    // Create a surfaceControl-associated session
    ConfigCreator fullAutoSessionCreator{
            .graphicsPipeline = true,
            .surfaceControls = {surfaceControl.get()},
            .autoCpu = true,
            .autoGpu = true,
    };

    auto fullAutoSessionConfig = configFromCreator(fullAutoSessionCreator, out, errCode);
    if (fullAutoSessionConfig == nullptr) {
        return toJString(env, out.c_str());
    }

    auto fullAutoSession = createSessionWithConfig(manager, fullAutoSessionConfig);
    if (fullAutoSessionConfig == nullptr) {
        return toJString(env, "Failed to create auto cpu+gpu session!");
    }

    return nullptr;
}

static JNINativeMethod gMethods[] = {
        {"nativeTestCreateHintSession", "()Ljava/lang/String;", (void*)nativeTestCreateHintSession},
        {"nativeTestCreateGraphicsPipelineSessionOverLimit", "()Ljava/lang/String;",
         (void*)nativeTestCreateGraphicsPipelineSessionOverLimit},
        {"nativeTestCreateHintSessionUsingConfig", "()Ljava/lang/String;",
         (void*)nativeTestCreateHintSessionUsingConfig},
        {"nativeTestGetPreferredUpdateRateNanos", "()Ljava/lang/String;",
         (void*)nativeTestGetPreferredUpdateRateNanos},
        {"nativeUpdateTargetWorkDuration", "()Ljava/lang/String;",
         (void*)nativeUpdateTargetWorkDuration},
        {"nativeUpdateTargetWorkDurationWithNegativeDuration", "()Ljava/lang/String;",
         (void*)nativeUpdateTargetWorkDurationWithNegativeDuration},
        {"nativeReportActualWorkDuration", "()Ljava/lang/String;",
         (void*)nativeReportActualWorkDuration},
        {"nativeReportActualWorkDurationWithIllegalArgument", "()Ljava/lang/String;",
         (void*)nativeReportActualWorkDurationWithIllegalArgument},
        {"nativeTestSetThreadsWithInvalidTid", "()Ljava/lang/String;",
         (void*)nativeTestSetThreadsWithInvalidTid},
        {"nativeSetPreferPowerEfficiency", "()Ljava/lang/String;",
         (void*)nativeSetPreferPowerEfficiency},
        {"nativeTestReportActualWorkDuration2", "()Ljava/lang/String;",
         (void*)nativeTestReportActualWorkDuration2},
        {"nativeTestReportActualWorkDuration2WithIllegalArgument", "()Ljava/lang/String;",
         (void*)nativeTestReportActualWorkDuration2WithIllegalArgument},
        {"nativeTestLoadHints", "()Ljava/lang/String;", (void*)nativeTestLoadHints},
        {"nativeBorrowSessionFromJava", "(Landroid/os/PerformanceHintManager$Session;)J",
         (void*)nativeBorrowSessionFromJava},
        {"nativeTestSetNativeSurfaces",
         "(Landroid/view/SurfaceControl;Landroid/view/Surface;)Ljava/lang/String;",
         (void*)nativeTestSetNativeSurfaces},
        {"nativeTestAutoSessionTiming", "(Landroid/view/SurfaceControl;)Ljava/lang/String;",
         (void*)nativeTestAutoSessionTiming},
};

int register_android_os_cts_PerformanceHintManagerTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/os/cts/PerformanceHintManagerTest");

    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
