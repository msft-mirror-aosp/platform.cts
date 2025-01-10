/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.packageinstaller.uninstall.cts

import android.content.Intent
import android.os.RemoteException
import androidx.test.uiautomator.By
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.AppOpsUtils
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assert
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class UninstallMultiUserTest : UninstallTestBase() {
    companion object {
        @JvmField
        @ClassRule(order = 0)
        @Rule
        val deviceState = DeviceState()

        private lateinit var secondaryUser: UserReference
    }

    @Before
    fun cacheUsers() {
        secondaryUser = deviceState.secondaryUser()
    }

    @Test
    @EnsureHasSecondaryUser
    @Throws(RemoteException::class)
    fun startUninstallAllUsersCrossUser() {
        // Uninstall from all users to start with a clean slate
        SystemUtil.runShellCommand("pm uninstall --user -1 $TEST_APK_PACKAGE_NAME")

        // Install CtsEmptyTestApp in secondaryUser
        SystemUtil.runShellCommand("pm install --user ${secondaryUser.id()} $APK")
        Assert.assertTrue(
            "$TEST_APK_PACKAGE_NAME is not installed in user $secondaryUser",
            isInstalled(secondaryUser)
        )
        Assert.assertFalse(
            "$TEST_APK_PACKAGE_NAME should not be installed in user ${context.user}",
            isInstalled()
        )

        val uninstallIntent = getUninstallIntent()
        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true)

        startUninstall(uninstallIntent)
        assertUninstallDialogShown(By.textContains("all users"))
        clickInstallerButton()

        for (i in 0..29) {
            // We can't detect the confirmation Toast with UiAutomator, so we'll poll
            Thread.sleep(500)
            if (!isInstalled(secondaryUser)) {
                break
            }
        }
        Assert.assertFalse("Package wasn't uninstalled.", isInstalled(secondaryUser))
        Assert.assertTrue(AppOpsUtils.allowedOperationLogged(context.packageName, APP_OP_STR))
    }
}
