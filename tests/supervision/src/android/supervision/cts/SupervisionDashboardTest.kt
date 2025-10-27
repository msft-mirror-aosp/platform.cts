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

import android.app.supervision.flags.Flags
import android.content.Intent
import android.provider.Settings
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.RequireNotAutomotive
import com.android.compatibility.common.util.ApiTest
import com.android.cts.install.lib.InstallUtils.getPackageInfo
import com.google.common.truth.Truth.assertThat
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
}
