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

package android.app.appfunctions.cts

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_ALL
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_USER
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE
import android.content.Context
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertWithMessage
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(
    Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    Flags.FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
)
class AppFunctionAccessTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val appFunctionManager = context.getSystemService(AppFunctionManager::class.java)!!

    @After
    fun clearState() {
        setAppFunctionFlags(0)
        setAppFunctionFlags(0, context.packageName)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_deniedByDefault() {
        assertWithMessage("Expected access to be false in a freshly installed app")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_userDenied() {
        setAppFunctionFlags(ACCESS_FLAG_USER_DENIED)
        assertWithMessage("Expected access to be denied for user denied flag")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_otherDenied() {
        setAppFunctionFlags(ACCESS_FLAG_OTHER_DENIED)
        assertWithMessage("Expected access to be denied for other denied flag")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_otherOverrideUser() {
        setAppFunctionFlags(ACCESS_FLAG_OTHER_DENIED or ACCESS_FLAG_USER_GRANTED)
        assertWithMessage(
            "Expected access to be denied for other denied flag, even if user granted set"
        )
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_pregranted() {
        setAppFunctionFlags(ACCESS_FLAG_PREGRANTED)
        assertWithMessage("Expected access to be granted for pregranted flag")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_GRANTED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_userOverridePregrant() {
        setAppFunctionFlags(ACCESS_FLAG_PREGRANTED or ACCESS_FLAG_USER_DENIED)
        assertWithMessage("Expected access to be denied for user denied flag, even if pregranted")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_userGranted() {
        setAppFunctionFlags(ACCESS_FLAG_USER_GRANTED)
        assertWithMessage("Expected access to be granted for user granted flag")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_GRANTED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_otherGranted() {
        setAppFunctionFlags(ACCESS_FLAG_OTHER_GRANTED)
        assertWithMessage("Expected access to be granted for other granted flag")
            .that(getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_GRANTED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_targetNotInstalled_unrequestable() {
        assertWithMessage("Cannot request access for an invalid agent")
            .that(getAccessRequestState(AGENT_PKG_NAME, INVALID_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_UNREQUESTABLE)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_agentNotInstalled_unrequestable() {
        assertWithMessage("Cannot request access for an invalid agent")
            .that(getAccessRequestState(INVALID_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_UNREQUESTABLE)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_requiresPermissionIfAgentIsntSelf() {
        assertFailsWith<SecurityException>(
            "Expected getAccessFlags to throw a security exception without permission"
        ) {
            appFunctionManager.getAccessRequestState(AGENT_PKG_NAME, TARGET_PKG_NAME)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getSelfAccessRequestState_usesContextOpPackageName() {
        setAppFunctionFlags(ACCESS_FLAG_OTHER_GRANTED, context.packageName)
        val otherPkgAppFunctionManager = callWithShellPermissionIdentity {
            context
                .createPackageContext(AGENT_PKG_NAME, 0)
                .getSystemService(AppFunctionManager::class.java)!!
        }
        assertWithMessage(
                "Context should pull package name from the opPackageName for " +
                    "getSelfAppFunctionAppRequestState"
            )
            .that(otherPkgAppFunctionManager.getAccessRequestState(TARGET_PKG_NAME))
            .isEqualTo(ACCESS_REQUEST_STATE_GRANTED)
    }

    private fun getAccessRequestState(agentPackageName: String, targetPackageName: String): Int {
        return callWithShellPermissionIdentity {
            appFunctionManager.getAccessRequestState(agentPackageName, targetPackageName)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_agentNotAllowlisted_unrequestable() {
        // TODO implement when agent allowlist is
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_agentLacksPermission_unrequestable() {
        // TODO implement when agent filtering is
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessRequestState_targetHasNoService_unrequestable() {
        // TODO implement when target filtering is
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessRequestState"])
    @Test
    fun getAccessFlags_requiresPermission() {
        assertFailsWith<SecurityException>(
            "Expected getAccessFlags to throw a security exception"
        ) {
            appFunctionManager.getAccessFlags(AGENT_PKG_NAME, TARGET_PKG_NAME)
        }
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionManager#getAccessFlags",
                "android.app.appfunctions.AppFunctionManager#updateAccessFlags",
            ]
    )
    @Test
    fun getAndUpdateAppFunctionAccessFlags_basicTest() {
        setAppFunctionFlags(ACCESS_FLAG_PREGRANTED)
        assertWithMessage("expected flags to be $ACCESS_FLAG_PREGRANTED")
            .that(getAppFunctionFlags())
            .isEqualTo(ACCESS_FLAG_PREGRANTED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessFlags"])
    @Test
    fun getAccessFlags_invalidTargetApp() {
        setAppFunctionFlags(ACCESS_FLAG_PREGRANTED)
        assertWithMessage("expected no flags for invalid target")
            .that(getAppFunctionFlags(AGENT_PKG_NAME, INVALID_PKG_NAME))
            .isEqualTo(0)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getAccessFlags"])
    @Test
    fun getAccessFlags_invalidAgentApp() {
        setAppFunctionFlags(ACCESS_FLAG_PREGRANTED)
        assertWithMessage("expected no flags for invalid agent")
            .that(getAppFunctionFlags(INVALID_PKG_NAME, TARGET_PKG_NAME))
            .isEqualTo(0)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#updateAccessFlags"])
    @Test
    fun updateAccessFlags_requiresPermission() {
        assertFailsWith<SecurityException>(
            "Expected updateAccessFlags to throw a security exception without permission"
        ) {
            appFunctionManager.updateAccessFlags(AGENT_PKG_NAME, TARGET_PKG_NAME, 0, 0)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#updateAccessFlags"])
    @Test
    fun updateAccessFlags_invalidFlagsCauseException() {
        assertFailsWith<IllegalArgumentException>(
            "Expected updateAccessFlags to throw an exception for invalid flags"
        ) {
            updateFlags(INVALID_FLAGS, INVALID_FLAGS)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#updateAccessFlags"])
    @Test
    fun updateAccessFlags_settingOpposingFlagsTogetherCauseException() {
        assertFailsWith<IllegalArgumentException>(
            "Expected updateAccessFlags to throw an exception when setting both a " +
                "GRANTED and DENIED flag together"
        ) {
            setAppFunctionFlags(ACCESS_FLAG_MASK_USER)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#updateAccessFlags"])
    @Test
    fun updateAccessFlags_settingOpposingFlagWithoutClearingOtherThrowsException() {
        assertFailsWith<IllegalArgumentException>(
            "Expected updateAccessFlags to throw an exception when setting only a " +
                "denied or granted flag, but not clearing the other"
        ) {
            updateFlags(ACCESS_FLAG_USER_GRANTED, ACCESS_FLAG_USER_GRANTED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#updateAccessFlags"])
    @Test
    fun updateAccessFlags_settingFlagNotInMaskThrowsException() {
        assertFailsWith<IllegalArgumentException>(
            "Expected updateAccessFlags to throw a security exception when setting a " +
                "flag not in the flag mask"
        ) {
            updateFlags(ACCESS_FLAG_MASK_USER, ACCESS_FLAG_PREGRANTED)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#revokeSelfAccess"])
    @Test
    fun revokeSelfAccess_appliesOtherDeniedFlag() {
        setAppFunctionFlags(ACCESS_FLAG_USER_GRANTED, context.packageName, TARGET_PKG_NAME)
        appFunctionManager.revokeSelfAccess(TARGET_PKG_NAME)
        assertWithMessage("Expected to see the OTHER_DENIED flag set after revoking self access")
            .that(getAppFunctionFlags(context.packageName, TARGET_PKG_NAME))
            .isEqualTo(ACCESS_FLAG_OTHER_DENIED)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#revokeSelfAccess"])
    @Test
    fun revokeSelfAccess_appliesNoFlagsIfNoFlagsSet() {
        appFunctionManager.revokeSelfAccess(TARGET_PKG_NAME)
        assertWithMessage("Expected to see no flags set when revoke is called with no state")
            .that(getAppFunctionFlags(context.packageName, TARGET_PKG_NAME))
            .isEqualTo(0)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidAgents"])
    @Test
    fun getValidAgents_agentMustRequestPermission() {
        val agents = callWithShellPermissionIdentity { appFunctionManager.validAgents }
        assertWithMessage("Expected $AGENT_PKG_NAME to be in agents list")
            .that(agents)
            .contains(AGENT_PKG_NAME)
        assertWithMessage("Expected $TARGET_PKG_NAME to not be in agents list")
            .that(agents)
            .doesNotContain(TARGET_PKG_NAME)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidAgents"])
    @Test
    fun getValidAgents_requiresPermission() {
        assertFailsWith<SecurityException>(
            "Expected getValidAgents to throw a security exception without permission"
        ) {
            appFunctionManager.validAgents
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidAgents"])
    @Test
    fun getValidAgents_agentMustBeAllowlisted() {
        // TODO implement when agent allowlist is
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidTargets"])
    @Test
    fun getValidTargets_targetMustHaveAppFunctionService() {
        val targets = callWithShellPermissionIdentity { appFunctionManager.validTargets }
        assertWithMessage("Expected $AGENT_PKG_NAME to not be in target list")
            .that(targets)
            .doesNotContain(AGENT_PKG_NAME)
        assertWithMessage("Expected $TARGET_PKG_NAME to be in targets list")
            .that(targets)
            .contains(TARGET_PKG_NAME)
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidTargets"])
    @Test
    fun getValidTargets_requiresPermission() {
        assertFailsWith<SecurityException>(
            "Expected getValidTargets to throw a security exception without permission"
        ) {
            appFunctionManager.validTargets
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#getValidTargets"])
    @Test
    fun getValidTargets_deviceSettingPackagesShouldHaveAndroidPackageName() {
        val deviceSettingPackages = appFunctionManager.deviceSettingPackages

        val targets = callWithShellPermissionIdentity { appFunctionManager.validTargets }

        for (deviceSettingPackage in deviceSettingPackages) {
            assertWithMessage("Expected $deviceSettingPackage to not be in the target list")
                .that(targets)
                .doesNotContain(deviceSettingPackage)
        }
        if (deviceSettingPackages.isNotEmpty()) {
            assertWithMessage("Expected $ANDROID_PKG_NAME to be in the target list")
                .that(targets)
                .contains(ANDROID_PKG_NAME)
        }
    }

    private fun getAppFunctionFlags(
        agentPackageName: String = AGENT_PKG_NAME,
        targetPackageName: String = TARGET_PKG_NAME,
    ): Int = callWithShellPermissionIdentity {
        appFunctionManager.getAccessFlags(agentPackageName, targetPackageName)
    }

    private fun updateFlags(
        flagMask: Int,
        flags: Int,
        agentPackageName: String = AGENT_PKG_NAME,
        targetPackageName: String = TARGET_PKG_NAME,
    ) = callWithShellPermissionIdentity {
        appFunctionManager.updateAccessFlags(agentPackageName, targetPackageName, flagMask, flags)
    }

    private fun setAppFunctionFlags(
        flags: Int,
        agentPackageName: String = AGENT_PKG_NAME,
        targetPackageName: String = TARGET_PKG_NAME,
    ) = updateFlags(ACCESS_FLAG_MASK_ALL, flags, agentPackageName, targetPackageName)

    companion object {
        const val AGENT_PKG_NAME = "android.app.appfunctions.cts.agent.helper"
        const val TARGET_PKG_NAME = "android.app.appfunctions.cts.helper"
        const val INVALID_PKG_NAME = "invalid_pkg"
        const val ANDROID_PKG_NAME = "android"
        const val INVALID_FLAGS = ACCESS_FLAG_MASK_ALL.inv()
    }
}
