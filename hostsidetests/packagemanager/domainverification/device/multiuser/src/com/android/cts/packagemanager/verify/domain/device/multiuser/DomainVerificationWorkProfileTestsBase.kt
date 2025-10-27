/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.packagemanager.verify.domain.device.multiuser

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.packages.Packages
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.ShellUtils
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_2_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.SharedVerifications
import com.android.cts.packagemanager.verify.domain.device.multiuser.DomainVerificationWorkProfileTestsBase.DomainVerificationWorkProfileTestsHelper.Companion.FORWARD_TO_PARENT
import com.android.cts.packagemanager.verify.domain.device.multiuser.DomainVerificationWorkProfileTestsBase.DomainVerificationWorkProfileTestsHelper.Companion.PERSONAL_APP
import com.android.cts.packagemanager.verify.domain.device.multiuser.DomainVerificationWorkProfileTestsBase.DomainVerificationWorkProfileTestsHelper.Companion.PERSONAL_COMPONENT
import com.android.cts.packagemanager.verify.domain.device.multiuser.DomainVerificationWorkProfileTestsBase.DomainVerificationWorkProfileTestsHelper.Companion.WORK_APP
import com.android.cts.packagemanager.verify.domain.device.multiuser.DomainVerificationWorkProfileTestsBase.DomainVerificationWorkProfileTestsHelper.Companion.WORK_COMPONENT
import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_UNHANDLED
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@EnsureHasWorkProfile(forUser = UserType.INITIAL_USER)
@RunWith(BedsteadJUnit4::class)
abstract class DomainVerificationWorkProfileTestsBase(
    private val helper: DomainVerificationWorkProfileTestsHelper
) {

    class DomainVerificationWorkProfileTestsHelper(private val deviceState: DeviceState) {

        companion object {
            private val TARGET_INTENT = Intent(Intent.ACTION_VIEW, Uri.parse("https://$DOMAIN_1"))
            private val BROWSER_INTENT =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://$DOMAIN_UNHANDLED"))

            val FORWARD_TO_PARENT =
                ComponentName("android", "com.android.internal.app.ForwardIntentToParent")

            val FORWARD_TO_MANAGED =
                ComponentName("android", "com.android.internal.app.ForwardIntentToManagedProfile")

            const val PERSONAL_APP = DECLARING_PKG_NAME_1

            const val WORK_APP = DECLARING_PKG_NAME_2

            val PERSONAL_COMPONENT = DECLARING_PKG_1_COMPONENT

            val WORK_COMPONENT = DECLARING_PKG_2_COMPONENT
        }

        lateinit var personalBrowsers: Collection<ComponentName>

        lateinit var workBrowsers: Collection<ComponentName>

        lateinit var personalUser: UserReference
        lateinit var workUser: UserReference

        fun installApks() {
            personalUser = deviceState.initialUser()
            workUser = deviceState.workProfile(UserType.INITIAL_USER)
            personalBrowsers = collectBrowsers(personalUser)
            workBrowsers = collectBrowsers(workUser)
            TestApis.packages().run {
                install(personalUser, Packages.JavaResource.javaResource(DECLARING_PKG_APK_1.value))
                install(workUser, Packages.JavaResource.javaResource(DECLARING_PKG_APK_2.value))
            }
        }

        fun uninstallApks() {
            TestApis.packages().run {
                find(PERSONAL_APP).uninstallFromAllUsers()
                find(WORK_APP).uninstallFromAllUsers()
            }
        }

        private fun collectBrowsers(user: UserReference) =
            withUserContext(user) { context ->
                context.packageManager
                    .queryIntentActivities(BROWSER_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
                    .map { it.activityInfo }
                    .map { ComponentName(it.packageName, it.name) }
                    .also { assumeTrue(it.isNotEmpty()) }
            }

        fun assertResolvesTo(vararg components: ComponentName) =
            assertResolvesTo(components.toList())

        fun assertResolvesTo(components: Collection<ComponentName>) {
            val results = TestApis.context()
                .instrumentedContext()
                .packageManager
                .queryIntentActivities(TARGET_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }
                .map { ComponentName(it.packageName, it.name) }
            assertThat(results).containsExactlyElementsIn(components)
        }

        fun verify(vararg packageNames: String) = packageNames.forEach {
            assertWithMessage("pm set-app-links should be empty on success").that(
                ShellUtils.runShellCommand(DomainUtils.setAppLinks(it, "STATE_APPROVED", DOMAIN_1))
            ).isEmpty()
        }
    }

    @Before
    @After
    fun resetState() {
        listOf(helper.personalUser, helper.workUser).forEach {
            withUserContext(it) {
                SharedVerifications.reset(it, resetEnable = true)
            }
        }
    }

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_unverified() {
        helper.assertResolvesTo(helper.personalBrowsers)
    }

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_verifiedInCurrentProfile() {
        helper.verify(PERSONAL_APP)

        helper.assertResolvesTo(PERSONAL_COMPONENT)
    }

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_verifiedInBothProfiles() {
        helper.verify(PERSONAL_APP, WORK_APP)

        helper.assertResolvesTo(PERSONAL_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_unverified() {
        helper.assertResolvesTo(helper.workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInCurrentProfile() {
        helper.verify(WORK_APP)

        helper.assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfile() {
        helper.verify(PERSONAL_APP)

        helper.assertResolvesTo(helper.workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfileDisabledApp() {
        helper.verify(PERSONAL_APP)
        disableApp(helper.personalUser, PERSONAL_APP)

        helper.assertResolvesTo(helper.workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfileDisabledComponent() {
        helper.verify(PERSONAL_APP)
        disableComponent(helper.personalUser, PERSONAL_COMPONENT)

        helper.assertResolvesTo(helper.workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfiles() {
        helper.verify(PERSONAL_APP, WORK_APP)

        helper.assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInOther() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableApp(helper.personalUser, PERSONAL_APP)

        helper.assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInOther() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableComponent(helper.personalUser, PERSONAL_COMPONENT)

        helper.assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInCurrent() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableApp(helper.workUser, WORK_APP)

        helper.assertResolvesTo(helper.workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInCurrent() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableComponent(helper.workUser, WORK_COMPONENT)

        helper.assertResolvesTo(helper.workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInBoth() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableApp(helper.personalUser, PERSONAL_APP)
        disableApp(helper.workUser, WORK_APP)

        helper.assertResolvesTo(helper.workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInBoth() {
        helper.verify(PERSONAL_APP, WORK_APP)
        disableComponent(helper.personalUser, PERSONAL_COMPONENT)
        disableComponent(helper.workUser, WORK_COMPONENT)

        helper.assertResolvesTo(helper.workBrowsers)
    }

    private fun disableApp(user: UserReference, packageName: String) {
        ShellCommand.builderForUser(user, "pm disable-user")
            .addOperand(packageName)
            .validate { it.trim().endsWith("new state: disabled-user") }
            .execute()
    }

    private fun disableComponent(user: UserReference, component: ComponentName) {
        ShellCommand.builderForUser(user, "pm disable")
            .addOperand(component.flattenToString())
            .validate { it.trim().endsWith("new state: disabled") }
            .execute()
    }
}
