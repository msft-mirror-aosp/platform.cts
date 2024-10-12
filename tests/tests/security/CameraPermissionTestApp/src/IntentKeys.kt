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

package android.security.cts.camera

class IntentKeys(private val packageName: String) {
    val shouldOpenCamera1: String
        get() = "$packageName.SHOULD_OPEN_CAMERA1"

    val shouldOpenCamera2: String
        get() = "$packageName.SHOULD_OPEN_CAMERA2"

    val finish: String
        get() = "$packageName.FINISH"

    val onResume: String
        get() = "$packageName.ON_RESUME"

    val cameraOpened1: String
        get() = "$packageName.CAMERA_OPENED1"

    val cameraOpened2: String
        get() = "$packageName.CAMERA_OPENED2"

    val error: String
        get() = "$packageName.ERROR"

    val noCamera: String
        get() = "$packageName.NO_CAMERA"

    val exception: String
        get() = "$packageName.EXCEPTION"
}
