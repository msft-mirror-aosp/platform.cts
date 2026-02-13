/*
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
package com.android.bedstead.settings.appfunctions

import android.content.Intent
import java.net.URISyntaxException

/**
 * A human readable representation of AppFunction's PerScreenDeviceStates.
 */
data class PerScreenDeviceStates(
    val intentUri: String?
) {

    /**
     * @return [Intent] object created from [intentUri].
     */
    @Throws(URISyntaxException::class)
    fun intent(): Intent? {
        return intentUri?.let {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }
    }
}
