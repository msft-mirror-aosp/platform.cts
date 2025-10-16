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

package android.voiceinteraction.cts

import android.app.voiceinteraction.VoiceInteractionManager
import android.app.voiceinteraction.VoiceInteractionManager.ACTION_REQUEST_ASSIST_STRUCTURE
import android.content.Context
import android.permission.flags.Flags.FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for {@link android.app.voiceinteraction.VoiceInteractionManager} APIs. */
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED)
class VoiceInteractionManagerTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(VoiceInteractionManager::class.java)

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#createRequestAssistStructureIntent"]
    )
    @Test
    fun testCreateRequestAssistStructureIntent() {
        val testPkgName = "com.android.test"
        val intent = manager.createRequestAssistStructureIntent()
        assertWithMessage(
            "Expected intent action to be ${ACTION_REQUEST_ASSIST_STRUCTURE}"
        )
            .that(intent.action)
            .isEqualTo(ACTION_REQUEST_ASSIST_STRUCTURE)
        assertWithMessage("Expected intent to be directed to PermissionController")
            .that(intent.getPackage())
            .isEqualTo(context.packageManager.permissionControllerPackageName)
    }
}
