#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Please do not try to convert this .mk file to a .bp file, since this file
# is only used in the build_libs.sh script, NOT in the build system.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libsplitappjni
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_SRC_FILES := com_android_cts_splitapp_Native.cpp

LOCAL_LDLIBS += -llog

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests

include $(BUILD_SHARED_LIBRARY)
