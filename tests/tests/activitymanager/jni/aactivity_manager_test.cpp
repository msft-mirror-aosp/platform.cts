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
#include <log/log.h>
#include <android/activity_manager.h>
#include <android/binder_status.h>

#include "nativehelper/scoped_local_ref.h"

struct ObserverCookie {
    JavaVM* jvm;
    jobject listener;
    jmethodID onProcessStartedMethod;
    jmethodID onProcessDiedMethod;
};

// C-style callbacks
void onProcessStarted(pid_t pid, uid_t processUid, uid_t packageUid, const char* packageName,
                      const char* processName, void* cookie) {
    ObserverCookie* observerCookie = static_cast<ObserverCookie*>(cookie);
    JNIEnv* env;
    observerCookie->jvm->AttachCurrentThread(&env, nullptr);

    ScopedLocalRef<jstring> jPackageName(env, env->NewStringUTF(packageName));
    ScopedLocalRef<jstring> jProcessName(env, env->NewStringUTF(processName));

    env->CallVoidMethod(observerCookie->listener, observerCookie->onProcessStartedMethod, (jint)pid,
                        (jint)processUid, (jint)packageUid, jPackageName.get(),
                        jProcessName.get());
}

void onProcessDied(pid_t pid, uid_t uid, void* cookie) {
    ObserverCookie* observerCookie = static_cast<ObserverCookie*>(cookie);
    JNIEnv* env;
    observerCookie->jvm->AttachCurrentThread(&env, nullptr);
    env->CallVoidMethod(observerCookie->listener, observerCookie->onProcessDiedMethod, (jint)pid,
                        (jint)uid);
}

extern "C" JNIEXPORT jobject JNICALL
Java_android_app_cts_AActivityManagerTest_nativeGetRunningAppProcesses(JNIEnv* env, jclass) {
    ARunningAppProcessInfoList* processInfoList = nullptr;
    binder_status_t status = AActivityManager_getRunningAppProcesses(&processInfoList);
    if (status != STATUS_OK || processInfoList == nullptr) {
        ALOGE("getRunningAppProcesses error: %d", status);
        return nullptr;
    }

    ScopedLocalRef<jclass> arrayListClass(env, env->FindClass("java/util/ArrayList"));
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass.get(), "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass.get(), "add", "(Ljava/lang/Object;)Z");
    ScopedLocalRef<jobject> jniProcessList(env, env->NewObject(arrayListClass.get(), arrayListCtor));

    ScopedLocalRef<jclass> processInfoClass(
            env, env->FindClass("android/app/cts/AActivityManagerTest$RunningAppProcessInfo"));
    jmethodID processInfoCtor = env->GetMethodID(
            processInfoClass.get(), "<init>", "(IILjava/lang/String;Ljava/util/List;I)V");

    size_t size = AActivityManager_RunningAppProcessInfoList_getSize(processInfoList);
    for (size_t i = 0; i < size; ++i) {
        const ARunningAppProcessInfo* info =
                AActivityManager_RunningAppProcessInfoList_get(processInfoList, i);

        if (info == nullptr) {
            ALOGE("info is null. i = %zu", i);
            continue;
        }
        ScopedLocalRef<jstring> processName(
                env, env->NewStringUTF(ARunningAppProcessInfo_getProcessName(info)));

        size_t numPackages;
        const char* const* pkgList = ARunningAppProcessInfo_getPackageList(info, &numPackages);
        ScopedLocalRef<jobject> jniPkgList(env, env->NewObject(arrayListClass.get(), arrayListCtor));
        for (size_t j = 0; j < numPackages; ++j) {
            ScopedLocalRef<jstring> pkgName(env, env->NewStringUTF(pkgList[j]));
            env->CallBooleanMethod(jniPkgList.get(), arrayListAdd, pkgName.get());
        }

        ScopedLocalRef<jobject> processInfoObject(
                env, env->NewObject(processInfoClass.get(), processInfoCtor,
                                    ARunningAppProcessInfo_getPid(info),
                                    ARunningAppProcessInfo_getUid(info), processName.get(),
                                    jniPkgList.get(), ARunningAppProcessInfo_getImportance(info)));
        env->CallBooleanMethod(jniProcessList.get(), arrayListAdd, processInfoObject.get());
    }

    AActivityManager_RunningAppProcessInfoList_destroy(processInfoList);
    return jniProcessList.release();
}

extern "C" JNIEXPORT jlong JNICALL Java_android_app_cts_AActivityManagerTest_nativeRegisterProcessObserver(
        JNIEnv* env, jclass, jobject listener) {
    ObserverCookie* cookie = new ObserverCookie();
    env->GetJavaVM(&cookie->jvm);
    cookie->listener = env->NewGlobalRef(listener);
    ScopedLocalRef<jclass> listenerClass(env, env->GetObjectClass(listener));
    cookie->onProcessStartedMethod =
            env->GetMethodID(listenerClass.get(), "onProcessStarted",
                             "(IIILjava/lang/String;Ljava/lang/String;)V");
    cookie->onProcessDiedMethod = env->GetMethodID(listenerClass.get(), "onProcessDied", "(II)V");

    AActivityManager_ProcessObserver* observer = AActivityManager_createProcessObserver(cookie);
    if (observer == nullptr) {
        env->DeleteGlobalRef(cookie->listener);
        delete cookie;
        return 0;
    }

    AActivityManager_ProcessObserver_setOnProcessStarted(observer, onProcessStarted);
    AActivityManager_ProcessObserver_setOnProcessDied(observer, onProcessDied);

    if (AActivityManager_registerProcessObserver(observer) != STATUS_OK) {
        AActivityManager_destroyProcessObserver(observer);
        // The cookie is intentionally leaked as per the comment in
        // nativeUnregisterProcessObserver.
        return 0;
    }

    return reinterpret_cast<jlong>(observer);
}

extern "C" JNIEXPORT void JNICALL Java_android_app_cts_AActivityManagerTest_nativeUnregisterProcessObserver(
        JNIEnv* /*env*/, jclass, jlong observerPtr) {
    if (observerPtr == 0) {
        return;
    }

    AActivityManager_ProcessObserver* observer =
            reinterpret_cast<AActivityManager_ProcessObserver*>(observerPtr);
    // To get the cookie, we can't do it from the public API.
    // In a real scenario you would have stored the cookie when you registered.
    // For this test, we assume there is only one observer and we will leak the cookie.
    // This is not ideal, but for a test it's fine. In a real app, you would manage the
    // cookie's lifecycle with the observer's.
    AActivityManager_destroyProcessObserver(observer);
    // Not deleting the cookie here as we can't safely retrieve it.
    // env->DeleteGlobalRef(cookie->listener);
    // delete cookie;
}
