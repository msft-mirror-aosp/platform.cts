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

package android.supervision.cts

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.OBSERVE_ROLE_HOLDERS
import android.app.supervision.flags.Flags
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.RequireNotAutomotive
import com.android.bedstead.harrier.annotations.RequireNotTv
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.supervision.withSupervisionRoleHeld
import com.android.cts.install.lib.InstallUtils.getPackageInfo
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.common.truth.TruthJUnit.assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_SCREEN)
class SupervisionDashboardTest : BaseSupervisionTest() {

    @Test
    @ApiTest(apis = ["android.provider.Settings#ACTION_SUPERVISION_SETTINGS"])
    @RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    @RequireNotAutomotive(reason = "Activity not present on automotive")
    fun supervisionSettingsIntent_resolvesToSettings() {
        assume().that(getPackageInfo("com.android.settings")).isNotNull()

        val intent = Intent(Settings.ACTION_SUPERVISION_SETTINGS)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        assertThat(resolveInfos.size).isEqualTo(1)
        val resolveInfo = resolveInfos[0]
        assertThat(resolveInfo.activityInfo.packageName).isEqualTo("com.android.settings")
    }

    @Test
    @ApiTest(apis = ["android.provider.Settings#MANAGE_SUPERVISION_APP_SETTINGS"])
    @RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    @RequireNotAutomotive(reason = "Supervision Settings Activity not present on automotive")
    @RequireNotTv(reason = "Redirected to a different activity in TV")
    @RequireNotWatch(reason = "Redirected to a different activity in watch")
    @RequireNotHeadlessSystemUserMode(
        reason = "b/434645293 - SYSTEM_SUPERVISION role qualification bypass does not support HSUM"
    )
    @EnsureHasPermission(BYPASS_ROLE_QUALIFICATION, MANAGE_ROLE_HOLDERS, OBSERVE_ROLE_HOLDERS)
    fun supervisionAppSettingsIntent_withSupervisionRoleHeld() {
        withSupervisionRoleHeld {
            launchSupervisionSettings()
            assertWithMessage("Supervision App label missing from dashboard")
                .that(scrollAndWaitUntilSupervisionAppLabelFound("Test Package"))
        }
    }

    private fun launchSupervisionSettings() {
        TestApis.activities()
            .startActivity(
                Intent(Settings.ACTION_SUPERVISION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
    }

    private fun scrollAndWaitUntilSupervisionAppLabelFound(text: String): UiObject2 {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        scrollTo(text)
        return device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS)
    }

    private fun scrollTo(text: String) {
        try {
            val scrollable = UiScrollable(UiSelector().scrollable(true))
            scrollable.scrollTextIntoView(text)
        } catch (e: UiObjectNotFoundException) {
            // Ignore the exception if there's no scroll bar.
        }
    }

    companion object {
        private const val UI_TIMEOUT_MS = 5000L
    }
}
