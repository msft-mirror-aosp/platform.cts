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

import android.Manifest.permission.MANAGE_USERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import android.content.Intent
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.RequireNotAutomotive
import com.android.bedstead.harrier.annotations.RequireNotTv
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.cts.install.lib.InstallUtils.getPackageInfo
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
class SupervisionStateTest : BaseSupervisionTest() {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureHasPermission(MANAGE_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun isSupervisionEnabled_hasManageUsersPermission_returnsCurrentSupervisionState() {
        setSupervisionEnabled(true)
        assertThat(supervisionManager.isSupervisionEnabled()).isTrue()

        setSupervisionEnabled(false)
        assertThat(supervisionManager.isSupervisionEnabled()).isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureHasPermission(QUERY_USERS)
    fun isSupervisionEnabled_hasQueryUsersPermission_returnsCurrentSupervisionState() {
        setSupervisionEnabled(true)
        assertThat(supervisionManager.isSupervisionEnabled()).isTrue()

        setSupervisionEnabled(false)
        assertThat(supervisionManager.isSupervisionEnabled()).isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureDoesNotHavePermission(MANAGE_USERS, QUERY_USERS)
    fun isSupervisionEnabled_noPermission_throwsException() {
        assertFailsWith<SecurityException> {
            supervisionManager.isSupervisionEnabled()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#ACTION_ENABLE_SUPERVISION"])
    @AppModeFull(reason = "Test relies on seeing other apps")
    @RequireNotAutomotive(reason = "Only phones and tablets have the activity")
    @RequireNotWatch(reason = "Only phones and tablets have the activity")
    @RequireNotTv(reason = "Only phones and tablets have the activity")
    fun enableSupervisionIntent_resolvesToSettings() {
        assume().that(getPackageInfo("com.android.settings")).isNotNull()

        val intent = Intent(SupervisionManager.ACTION_ENABLE_SUPERVISION)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        assertThat(resolveInfos.size).isEqualTo(1)
        val resolveInfo = resolveInfos[0]
        assertThat(resolveInfo.activityInfo.packageName).isEqualTo("com.android.settings")
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#ACTION_DISABLE_SUPERVISION"])
    @AppModeFull(reason = "Test relies on seeing other apps")
    @RequireNotAutomotive(reason = "Only phones and tablets have the activity")
    @RequireNotWatch(reason = "Only phones and tablets have the activity")
    @RequireNotTv(reason = "Only phones and tablets have the activity")
    fun disableSupervisionIntent_resolvesToSettings() {
        assume().that(getPackageInfo("com.android.settings")).isNotNull()

        val intent = Intent(SupervisionManager.ACTION_DISABLE_SUPERVISION)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)

        assertThat(resolveInfos.size).isEqualTo(1)
        val resolveInfo = resolveInfos[0]
        assertThat(resolveInfo.activityInfo.packageName).isEqualTo("com.android.settings")
    }
}
