/*
 * Copyright (C) 2018 The Android Open Source Project
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
#define LOG_TAG "Cts-NdkBinderTest"

#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <gtest/gtest.h>
#include <nativetesthelper_jni/utils.h>

#include <condition_variable>
#include <mutex>
#include <vector>

#include "utilities.h"

void* NothingClass_onCreate(void* args) { return args; }
void NothingClass_onDestroy(void* /*userData*/) {}
binder_status_t NothingClass_onTransact(AIBinder*, transaction_code_t,
                                        const AParcel*, AParcel*) {
  return STATUS_UNKNOWN_ERROR;
}

static AIBinder_Class* kNothingClass =
    AIBinder_Class_define("nothing", NothingClass_onCreate,
                          NothingClass_onDestroy, NothingClass_onTransact);

class NdkBinderTest_AIBinder_Jni : public NdkBinderTest {};

TEST_F(NdkBinderTest_AIBinder_Jni, ConvertJni) {
  JNIEnv* env = GetEnv();
  ASSERT_NE(nullptr, env);

  AIBinder* binder = AIBinder_new(kNothingClass, nullptr);
  EXPECT_NE(nullptr, binder);

  jobject object = AIBinder_toJavaBinder(env, binder);
  EXPECT_NE(nullptr, object);

  AIBinder* fromJavaBinder = AIBinder_fromJavaBinder(env, object);
  EXPECT_EQ(binder, fromJavaBinder);

  AIBinder_decStrong(binder);
  AIBinder_decStrong(fromJavaBinder);
}

std::mutex gMutex;
std::condition_variable gCv;
std::vector<bool> gResults;

void OnFrozenStateChanged(void*, bool frozen) {
  std::unique_lock<std::mutex> lock(gMutex);
  gResults.push_back(frozen);
  gCv.notify_one();
}

void OnBinderUnlinked(void*) {}

TEST_F(NdkBinderTest_AIBinder_Jni, FrozenStateChangeCallback) {
  {
      std::unique_lock<std::mutex> lock(gMutex);
      gResults.clear();
  }
  JNIEnv* env = GetEnv();
  ASSERT_NE(nullptr, env);

  jclass ndkBinderTest = env->FindClass("android/binder/cts/NdkBinderTest");
  ASSERT_NE(nullptr, ndkBinderTest);
  jmethodID getRemoteNativeService =
      env->GetStaticMethodID(ndkBinderTest, "getRemoteNativeService", "()Landroid/os/IBinder;");
  ASSERT_NE(nullptr, getRemoteNativeService);
  jmethodID freezeRemote =
      env->GetStaticMethodID(ndkBinderTest, "freezeRemote", "()V");
  ASSERT_NE(nullptr, freezeRemote);
  jmethodID unfreezeRemote =
      env->GetStaticMethodID(ndkBinderTest, "unfreezeRemote", "()V");
  ASSERT_NE(nullptr, unfreezeRemote);

  // Ensure that the remote binder is unfrozen at the end of the test, regardless of
  // the outcome of the test.
  struct UnfreezeGuard {
      JNIEnv* env;
      jclass clazz;
      jmethodID method;
      ~UnfreezeGuard() { env->CallStaticVoidMethod(clazz, method); }
  } unfreezeGuard{env, ndkBinderTest, unfreezeRemote};

  jobject object = env->CallStaticObjectMethod(ndkBinderTest, getRemoteNativeService);
  ASSERT_NE(nullptr, object);

  AIBinder* binder = AIBinder_fromJavaBinder(env, object);
  ASSERT_NE(nullptr, binder);

  AIBinder_FrozenStateChangeCallback* callback =
      AIBinder_FrozenStateChangeCallback_new(OnFrozenStateChanged, OnBinderUnlinked);
  ASSERT_NE(nullptr, callback);

  EXPECT_EQ(STATUS_OK, AIBinder_addFrozenStateChangeCallback(binder, callback, nullptr));

  env->CallStaticVoidMethod(ndkBinderTest, freezeRemote);
  {
      std::unique_lock<std::mutex> lock(gMutex);
      using namespace std::chrono_literals;
      bool success = gCv.wait_for(lock, 5s, [] {
          return !gResults.empty() && gResults.back() == true;
      });
      EXPECT_TRUE(success) << "Timed out waiting for freeze callback";
  }

  env->CallStaticVoidMethod(ndkBinderTest, unfreezeRemote);
  {
      std::unique_lock<std::mutex> lock(gMutex);
      using namespace std::chrono_literals;
      bool success = gCv.wait_for(lock, 5s, [] {
          return !gResults.empty() && gResults.back() == false;
      });
      EXPECT_TRUE(success) << "Timed out waiting for unfreeze callback";
  }

  EXPECT_EQ(STATUS_OK, AIBinder_removeFrozenStateChangeCallback(binder, callback, nullptr));

  AIBinder_FrozenStateChangeCallback_delete(callback);
  AIBinder_decStrong(binder);
}
