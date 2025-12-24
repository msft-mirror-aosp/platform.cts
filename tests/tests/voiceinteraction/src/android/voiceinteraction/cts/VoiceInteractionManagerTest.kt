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

import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.voiceinteraction.VoiceInteractionManager
import android.app.voiceinteraction.VoiceInteractionManager.ACTION_REQUEST_READ_SCREEN_CONTEXT
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.permission.flags.Flags.FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.voiceinteraction.cts.testcore.Helper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SettingsStateKeeperRule
import com.android.compatibility.common.util.SettingsStateManager
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.properties.Delegates
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for {@link android.app.voiceinteraction.VoiceInteractionManager} APIs. */
@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "No real use case for instant mode")
@RequiresFlagsEnabled(FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED)
class VoiceInteractionManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val assistStructureEnabledManagersKeeper =
        SettingsStateKeeperRule(context, ASSIST_STRUCTURE_ENABLED)
    @get:Rule val assistScreenshotEnabledManagersKeeper =
        SettingsStateKeeperRule(context, ASSIST_SCREENSHOT_ENABLED)
    @get:Rule val readScreenContextDeniedCountManagersKeeper =
        SettingsStateKeeperRule(context, READ_SCREEN_CONTEXT_REQUEST_DENIED_COUNT)
    @get:Rule val activityRule = ActivityTestRule(WaitForResultActivity::class.java)

    private val assistStructureEnabledManager =
        SettingsStateManager(context, ASSIST_STRUCTURE_ENABLED)
    private val assistScreenshotEnabledManager =
        SettingsStateManager(context, ASSIST_SCREENSHOT_ENABLED)
    private val readScreenContextDeniedCountManager =
        SettingsStateManager(context, READ_SCREEN_CONTEXT_REQUEST_DENIED_COUNT)

    private lateinit var appOpsManager: AppOpsManager
    private lateinit var roleManager: RoleManager

    // VoiceInteractionManager will be null if flag disabled, move init to setup method so it only
    // gets invoked when flag enabled
    private lateinit var voiceInteractionManager: VoiceInteractionManager

    private var assistantRoleHolderPackageUid by Delegates.notNull<Int>()
    private var originalRoleHolder: String? = null

    @Before
    fun setup() {
        appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        roleManager = context.getSystemService(RoleManager::class.java)!!
        voiceInteractionManager =
            context.getSystemService(VoiceInteractionManager::class.java)!!
        assistantRoleHolderPackageUid =
                context.packageManager.getPackageUid(ASSISTANT_ROLE_HOLDER_PACKAGE, 0)

        // assistant is singleton role
        originalRoleHolder = Helper.getAssistRoleHolders(roleManager).getOrNull(0)

        if (originalRoleHolder == ASSISTANT_ROLE_HOLDER_PACKAGE) {
            Helper.removeAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)
            originalRoleHolder = null
        }
    }

    @After
    fun cleanup() {
        Helper.removeAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)

        // Restore to original, assistant is singleton role
        originalRoleHolder?.let {
            Helper.addAssistRoleHolder(it, context, roleManager)
        }

        assertThat(Helper.getAssistRoleHolders(roleManager))
            .doesNotContain(ASSISTANT_ROLE_HOLDER_PACKAGE)
    }

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#createRequestReadScreenContextIntent"]
    )
    @Test
    fun testCreateRequestReadScreenContextIntent() {
        val intent = voiceInteractionManager.createRequestReadScreenContextIntent()
        assertWithMessage(
            "Expected intent action to be ${ACTION_REQUEST_READ_SCREEN_CONTEXT}"
        )
            .that(intent.action)
            .isEqualTo(ACTION_REQUEST_READ_SCREEN_CONTEXT)
        assertWithMessage("Expected intent to be directed to PermissionController")
            .that(intent.getPackage())
            .isEqualTo(context.packageManager.permissionControllerPackageName)
    }

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#getReadScreenContextRequestState"]
    )
    @Test
    fun testGetReadScreenContextRequestState() {
        Helper.addAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)

        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        assertReadScreenContextRequestState(
            VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED
        )

        setAppOpMode(AppOpsManager.MODE_IGNORED)
        assertReadScreenContextRequestState(
            VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_REQUESTABLE
        )

        setAppOpMode(AppOpsManager.MODE_IGNORED)
        setReadScreenContextRequestDeniedCount(BLOCKED_DENIED_COUNT)
        assertReadScreenContextRequestState(
            VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE
        )
    }

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#getReadScreenContextRequestState"]
    )
    @Test
    fun testGetReadScreenContextRequestState_nonRoldHolder() {
        assertThat(Helper.getAssistRoleHolders(roleManager))
            .doesNotContain(ASSISTANT_ROLE_HOLDER_PACKAGE)

        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        setReadScreenContextRequestDeniedCount(0)
        assertReadScreenContextRequestState(
            VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE
        )
    }

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#getReadScreenContextRequestState"]
    )
    @Test
    fun testGetReadScreenContextRequestStateForUid() {
        Helper.addAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)

        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        assertWithMessage(
            "Expected state to be" +
            " ${VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED} when" +
            " MODE_ALLOWED"
        )
            .that(getReadScreenContextRequestState())
            .isEqualTo(VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED)

        setAppOpMode(AppOpsManager.MODE_IGNORED)
        assertWithMessage(
            "Expected state to be" +
            " ${VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_REQUESTABLE} when" +
            " MODE_IGNORED"
        )
            .that(getReadScreenContextRequestState())
            .isEqualTo(VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_REQUESTABLE)

        setAppOpMode(AppOpsManager.MODE_IGNORED)
        setReadScreenContextRequestDeniedCount(BLOCKED_DENIED_COUNT)
        assertWithMessage(
            "Expected state to be" +
            " ${VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE} when" +
            " denied count $BLOCKED_DENIED_COUNT"
        )
            .that(getReadScreenContextRequestState())
            .isEqualTo(VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE)
    }

    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#getReadScreenContextRequestState"]
    )
    @Test
    fun testGetReadScreenContextRequestStateForUid_nonRoldHolder() {
        assertThat(Helper.getAssistRoleHolders(roleManager))
            .doesNotContain(ASSISTANT_ROLE_HOLDER_PACKAGE)

        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        setReadScreenContextRequestDeniedCount(0)
        assertWithMessage(
            "Expected state to be" +
            " ${VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE} when" +
            " not role holder"
        )
            .that(getReadScreenContextRequestState())
            .isEqualTo(VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_UNREQUESTABLE)
    }

    @Test
    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#canReadScreenContext"]
    )
    fun testCanReadAssistStructure() {
        Helper.addAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)

        // Mode Allowed
        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        assertCanReadScreenContext(true)
        eventually {
            assertThat(assistStructureEnabledManager.get()).isEqualTo("1")
            assertThat(assistScreenshotEnabledManager.get()).isEqualTo("1")
        }

        // Mode ignored
        setAppOpMode(AppOpsManager.MODE_IGNORED)
        assertCanReadScreenContext(false)
        eventually {
            assertThat(assistStructureEnabledManager.get()).isEqualTo("0")
            assertThat(assistScreenshotEnabledManager.get()).isEqualTo("0")
        }

        // Mode default
        setAppOpMode(AppOpsManager.MODE_DEFAULT)
        assertCanReadScreenContext(true)
        eventually {
            assertThat(assistStructureEnabledManager.get()).isEqualTo("1")
            assertThat(assistScreenshotEnabledManager.get()).isEqualTo("1")
        }

        // Mode errored
        setAppOpMode(AppOpsManager.MODE_ERRORED)
        assertCanReadScreenContext(false)
        eventually {
            assertThat(assistStructureEnabledManager.get()).isEqualTo("0")
            assertThat(assistScreenshotEnabledManager.get()).isEqualTo("0")
        }
    }

    @Test
    @ApiTest(
        apis = ["android.app.voiceinteraction.VoiceInteractionManager#canReadScreenContext"]
    )
    fun testCanReadAssistStructure_nonRoleHolder() {
        Helper.removeAssistRoleHolder(ASSISTANT_ROLE_HOLDER_PACKAGE, context, roleManager)

        val initialAssistStructureSecureSettings =
            assistStructureEnabledManager.get() ?: "0"
        val initialAssistScreenshotSecureSettings =
            assistScreenshotEnabledManager.get() ?: "0"

        // Mode Allowed
        setAppOpMode(AppOpsManager.MODE_ALLOWED)
        assertCanReadScreenContext(false)
        assertThat(assistStructureEnabledManager.get())
            .isEqualTo(initialAssistStructureSecureSettings)
        assertThat(assistScreenshotEnabledManager.get())
            .isEqualTo(initialAssistScreenshotSecureSettings)

        // Mode ignored
        setAppOpMode(AppOpsManager.MODE_IGNORED)
        assertCanReadScreenContext(false)
        assertThat(assistStructureEnabledManager.get())
            .isEqualTo(initialAssistStructureSecureSettings)
        assertThat(assistScreenshotEnabledManager.get())
            .isEqualTo(initialAssistScreenshotSecureSettings)

        // Mode default
        setAppOpMode(AppOpsManager.MODE_DEFAULT)
        assertCanReadScreenContext(false)
        assertThat(assistStructureEnabledManager.get())
            .isEqualTo(initialAssistStructureSecureSettings)
        assertThat(assistScreenshotEnabledManager.get())
            .isEqualTo(initialAssistScreenshotSecureSettings)

        // Mode errored
        setAppOpMode(AppOpsManager.MODE_ERRORED)
        assertCanReadScreenContext(false)
        assertThat(assistStructureEnabledManager.get())
            .isEqualTo(initialAssistStructureSecureSettings)
        assertThat(assistScreenshotEnabledManager.get())
            .isEqualTo(initialAssistScreenshotSecureSettings)
    }

    @ApiTest(
        apis = [
            "android.app.voiceinteraction.VoiceInteractionManager#incrementReadScreenContextRequestDeniedCount",
            "android.app.voiceinteraction.VoiceInteractionManager#clearReadScreenContextRequestDeniedCount",
        ]
    )
    @Test
    fun testUpdateReadScreenContextRequestDeniedCount() {
        clearReadScreenContextRequestDeniedCount()
        assertThat(getReadScreenContextRequestDeniedCount())
            .isEqualTo(0)
        incrementReadScreenContextRequestDeniedCount()
        assertThat(getReadScreenContextRequestDeniedCount())
            .isEqualTo(1)
        incrementReadScreenContextRequestDeniedCount()
        assertThat(getReadScreenContextRequestDeniedCount())
            .isEqualTo(2)
        clearReadScreenContextRequestDeniedCount()
        assertThat(getReadScreenContextRequestDeniedCount())
            .isEqualTo(0)
    }

    private fun getAppOpMode(): Int =
        runWithShellPermissionIdentity<Int> {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_READ_SCREEN_CONTEXT,
                assistantRoleHolderPackageUid,
                ASSISTANT_ROLE_HOLDER_PACKAGE,
            )
        }

    private fun setAppOpMode(mode: Int) =
        runWithShellPermissionIdentity {
            appOpsManager.setUidMode(
                AppOpsManager.OPSTR_READ_SCREEN_CONTEXT,
                assistantRoleHolderPackageUid,
                mode,
            )
        }

    private fun getReadScreenContextRequestDeniedCount(): Int =
        readScreenContextDeniedCountManager.get()?.toIntOrNull() ?: 0

    private fun setReadScreenContextRequestDeniedCount(count: Int) =
        readScreenContextDeniedCountManager.set(count.toString())

    private fun incrementReadScreenContextRequestDeniedCount() =
        runWithShellPermissionIdentity {
            voiceInteractionManager.incrementReadScreenContextRequestDeniedCount()
        }

    private fun clearReadScreenContextRequestDeniedCount() =
        runWithShellPermissionIdentity {
            voiceInteractionManager.clearReadScreenContextRequestDeniedCount()
        }

    private fun getReadScreenContextRequestState(): Int =
        runWithShellPermissionIdentity<Int> {
            voiceInteractionManager.getReadScreenContextRequestState(
                assistantRoleHolderPackageUid
            )
        }

    private fun assertReadScreenContextRequestState(expectedRequestState: Int) {
        val intent: Intent = Intent()
            .setComponent(
                ComponentName(
                    ASSISTANT_ROLE_HOLDER_PACKAGE,
                    APP_GET_READ_SCREEN_CONTEXT_REQUEST_STATE_ACTIVITY_NAME
                )
            )
        activityRule.activity.startActivityToWaitForResult(intent)

        val result = activityRule.activity.waitForActivityResult(ACTIVITY_WAIT_TIMEOUT_MILLIS)

        assertThat(result.first).isEqualTo(Activity.RESULT_OK)
        assertThat(result.second).isNotNull()
        assertThat(result.second.hasExtra(EXTRA_REQUEST_STATE)).isTrue()
        assertThat(result.second.getIntExtra(EXTRA_REQUEST_STATE, -1))
            .isEqualTo(expectedRequestState)
    }

    private fun assertCanReadScreenContext(isReadable: Boolean) {
        val intent: Intent = Intent()
            .setComponent(
                ComponentName(
                    ASSISTANT_ROLE_HOLDER_PACKAGE,
                    APP_CAN_READ_SCREEN_CONTEXT_ACTIVITY_NAME
                )
            )
        activityRule.activity.startActivityToWaitForResult(intent)

        val result = activityRule.activity.waitForActivityResult(ACTIVITY_WAIT_TIMEOUT_MILLIS)

        assertThat(result.first).isEqualTo(Activity.RESULT_OK)
        assertThat(result.second).isNotNull()
        assertThat(result.second.hasExtra(EXTRA_CAN_READ_SCREEN_CONTEXT)).isTrue()
        assertThat(result.second.getBooleanExtra(
            EXTRA_CAN_READ_SCREEN_CONTEXT,
            !isReadable
        ))
            .isEqualTo(isReadable)
    }

    private companion object {
        private const val ASSISTANT_ROLE_HOLDER_PACKAGE = "android.voiceinteraction.testassistant"
        private const val APP_CAN_READ_SCREEN_CONTEXT_ACTIVITY_NAME =
            "$ASSISTANT_ROLE_HOLDER_PACKAGE.CanReadScreenContextActivity"
        private const val APP_GET_READ_SCREEN_CONTEXT_REQUEST_STATE_ACTIVITY_NAME =
            "$ASSISTANT_ROLE_HOLDER_PACKAGE.GetReadScreenContextRequestStateActivity"
        private const val EXTRA_CAN_READ_SCREEN_CONTEXT =
            "android.voiceinteraction.testassistant.extra.CAN_READ_SCREEN_CONTEXT"
        private const val EXTRA_REQUEST_STATE =
            "android.voiceinteraction.testassistant.extra.REQUEST_STATE"
        private const val ASSIST_STRUCTURE_ENABLED = "assist_structure_enabled"
        private const val ASSIST_SCREENSHOT_ENABLED = "assist_screenshot_enabled"
        private const val READ_SCREEN_CONTEXT_REQUEST_DENIED_COUNT =
            "read_screen_context_request_denied_count"
        private const val ACTIVITY_WAIT_TIMEOUT_MILLIS: Long = 5000
        private const val BLOCKED_DENIED_COUNT = 10
    }
}
