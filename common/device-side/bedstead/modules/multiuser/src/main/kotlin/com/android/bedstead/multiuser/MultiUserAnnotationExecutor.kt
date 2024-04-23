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
package com.android.bedstead.multiuser

import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.nene.TestApis.users
import org.junit.Assume

@Suppress("unused")
class MultiUserAnnotationExecutor(private val deviceState: DeviceState) : AnnotationExecutor {
    override fun applyAnnotation(annotation: Annotation) {
        when (annotation) {
            is EnsureCanAddUser -> deviceState.ensureCanAddUser(
                annotation.number,
                annotation.failureMode
            )
            is RequireHasMainUser -> requireHasMainUser(annotation.reason)
        }
    }

    private fun requireHasMainUser(reason: String) {
        Assume.assumeTrue(reason, users().main() != null)
    }
}
