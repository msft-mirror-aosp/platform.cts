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

package com.android.bedstead.settings

import android.content.Intent
import android.util.Log
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.nene.TestApis.activities
import com.google.common.truth.Truth

/**
 * Utility functions containing common logic for Setting APIs.
 */
object SettingsTestUtils {

    /**
     * Verifies if the activity can be launched.
     *
     * If a SecurityException is thrown, the function logs the error and does not re-throw it.
     * @param logTag Used to identify the source of a log message.
     */
    fun verifyIntentLaunchesActivity(intent: Intent, logTag: String) {
        if (intent.component == null) {
            AnnotationExecutorUtil.failOrSkip(
                "No component present on the device to handle such an intent",
                FailureMode.SKIP
            )
        }

        activities().clearAllActivities()
        val pastForegroundActivity = activities().foregroundActivity()

        try {
            activities().startActivity(intent)
            val currentForegroundActivity = activities().foregroundActivity()
            Truth.assertThat(currentForegroundActivity).isNotNull()
            Truth.assertThat(currentForegroundActivity).isNotEqualTo(pastForegroundActivity)
        } catch (ex: SecurityException) {
            Log.d(
                logTag,
                "App tried to launch an intent, but couldn't due to missing " +
                        "permission(s). Which is enough for this test. Got: ${ex.cause}, ${ex.message}"
            )
        }
    }
}
