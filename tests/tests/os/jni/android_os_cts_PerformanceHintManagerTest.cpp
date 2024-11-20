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
#include <android/performance_hint.h>
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

class SessionWrapper {
public:
    explicit SessionWrapper(APerformanceHintSession* session) : mSession(session) {}
    SessionWrapper(SessionWrapper&& other) : mSession(other.mSession) {
        other.mSession = nullptr;
    }
    ~SessionWrapper() {
        if (mSession) {
            APerformanceHint_closeSession(mSession);
        }
    }

    SessionWrapper(const SessionWrapper&) = delete;
    SessionWrapper& operator=(const SessionWrapper&) = delete;

    APerformanceHintSession* session() const { return mSession; }

private:
    APerformanceHintSession* mSession;
};

struct HelperThread {
    HelperThread() : helpThread(&HelperThread::run, &(*this)) {}
    ~HelperThread() { closurePromise.set_value(true); }

    // calling getTid() more than once would break
    pid_t getTid() { return pidFuture.get(); }

    void run() {
        pidPromise.set_value(getTid());
        closureFuture.get();
    }

    std::promise<pid_t> pidPromise{};
    std::future<pid_t> pidFuture = pidPromise.get_future();
    std::promise<bool> closurePromise{};
    std::future<bool> closureFuture = closurePromise.get_future();
    std::thread helpThread;
};

static SessionWrapper createSession(APerformanceHintManager* manager) {
    int32_t pid = getpid();
    return SessionWrapper(APerformanceHint_createSession(manager, &pid, 1u, DEFAULT_TARGET_NS));
}

struct WorkDurationCreator {
    int64_t workPeriodStart;
    int64_t totalDuration;
    int64_t cpuDuration;
    int64_t gpuDuration;
};

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
    SessionWrapper a = createSession(manager);
    SessionWrapper b = createSession(manager);
    if (a.session() == nullptr) {
        return toJString(env, "a is null");
    } else if (b.session() == nullptr) {
        return toJString(env, "b is null");
    } else if (a.session() == b.session()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestCreateHintSessionUsingConfig(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    ASessionCreationConfig* config = ASessionCreationConfig_create();
    if (config == nullptr) {
        return toJString(env, "config is null");
    }
    int32_t pid = getpid();
    int ret = ASessionCreationConfig_setTids(config, &pid, 1u);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTids ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setTargetWorkDurationNanos(config, DEFAULT_TARGET_NS);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTargetWorkDurationNanos ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    SessionWrapper a = SessionWrapper(APerformanceHint_createSessionUsingConfig(manager, config));
    SessionWrapper b = SessionWrapper(APerformanceHint_createSessionUsingConfig(manager, config));
    if (a.session() == nullptr) {
        return toJString(env, "a is null");
    } else if (b.session() == nullptr) {
        return toJString(env, "b is null");
    } else if (a.session() == b.session()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestGetPreferredUpdateRateNanos(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() != nullptr) {
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
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "updateTargetWorkDuration did not return 0");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDurationWithNegativeDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), -1);
    if (result != EINVAL) {
        return toJString(env, "updateTargetWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeReportActualWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1000);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1000) did not return 0");
    }

    return nullptr;
}

static jstring nativeReportActualWorkDurationWithIllegalArgument(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), -1);
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
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) {
        return nullptr;
    }

    std::vector<pid_t> tids;
    tids.push_back(2);
    int result = APerformanceHint_setThreads(wrapper.session(), tids.data(), 1);
    if (result != EPERM) {
        return toJString(env, "setThreads did not return EPERM");
    }
    return nullptr;
}


static jstring nativeSetPreferPowerEfficiency(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), false);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(false) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }
    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

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
        int result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), testObj);
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
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;


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
        int result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), testObj);
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
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result =
            APerformanceHint_notifyWorkloadIncrease(wrapper.session(), true, false, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(wrapper.session(), false, true, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadReset(false, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(wrapper.session(), true, true, "CTS hint");
    if (result != 0 && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(wrapper.session(), false, false, "CTS hint");
    if (result != EINVAL && result != ENOTSUP) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(false, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(wrapper.session(), false, false, "CTS hint");
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

    ASessionCreationConfig* config = ASessionCreationConfig_create();
    int32_t pid = getpid();
    int ret = ASessionCreationConfig_setTids(config, &pid, 1u);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTids ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setTargetWorkDurationNanos(config, DEFAULT_TARGET_NS);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTargetWorkDurationNanos ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    ret = ASessionCreationConfig_setGraphicsPipeline(config, true);
    if (ret == EINVAL) {
        return toJString(env, "creation config setGraphicsPipeline ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }

    std::list<struct HelperThread> threads(count);
    std::vector<int32_t> tids;
    for (auto&& thread : threads) {
        tids.push_back(thread.getTid());
    }
    ret = ASessionCreationConfig_setTids(config, tids.data(), (size_t)count);
    if (ret == EINVAL) {
        return toJString(env, "creation config setTids ret is EINVAL");
    } else if (ret == ENOTSUP) {
        return nullptr;
    }
    SessionWrapper a = SessionWrapper(APerformanceHint_createSessionUsingConfig(manager, config));
    if (a.session() != nullptr) {
        return toJString(env, "a is not null given max graphics pipeline threads limit reached");
    }

    ASessionCreationConfig_release(config);
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
};

int register_android_os_cts_PerformanceHintManagerTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/os/cts/PerformanceHintManagerTest");

    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
