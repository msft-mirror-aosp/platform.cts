/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.xts.root

import com.android.bedstead.adb.adb
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.Tags
import com.android.xts.root.Tags.ADB_ROOT
import com.android.xts.root.annotations.RequireAdbRoot

/**
 * [AnnotationExecutor] used for parsing [RequireAdbRoot].
 */
class RootAnnotationExecutor : AnnotationExecutor {

    override fun applyAnnotation(annotation: Annotation?) {
        if (annotation is RequireAdbRoot) {
            requireAdbRoot(annotation.failureMode)
        }
    }

    private fun requireAdbRoot(failureMode: FailureMode) {
        if (TestApis.adb().isRootAvailable()) {
            Tags.addTag(ADB_ROOT)
        } else {
            AnnotationExecutorUtil.failOrSkip("Device does not have root available.", failureMode)
        }
    }
    override fun teardownShareableState() {
    }

    override fun teardownNonShareableState() {
    }
}

/** True if the currently executing test is supposed to be run with ADB root capabilities. */
fun DeviceState.testUsesAdbRoot() = Tags.hasTag(ADB_ROOT)