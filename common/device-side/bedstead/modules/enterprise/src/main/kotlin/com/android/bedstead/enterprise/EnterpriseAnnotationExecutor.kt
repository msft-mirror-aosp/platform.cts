/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.enterprise

import com.android.bedstead.enterprise.annotations.EnsureHasDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceAdmin
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.enterprise.annotations.EnsureHasNoDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoTestDeviceAdmin
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest
import com.android.bedstead.enterprise.annotations.RequireHasPolicyExemptApps
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.components.TestAppsComponent
import com.android.bedstead.multiuser.UsersComponent
import com.android.bedstead.testapp.TestAppProvider

@Suppress("unused")
class EnterpriseAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val enterpriseComponent: EnterpriseComponent by locator
    private val deviceOwnerComponent: DeviceOwnerComponent by locator
    private val profileOwnersComponent: ProfileOwnersComponent by locator
    private val deviceAdminComponent: DeviceAdminComponent by locator
    private val testAppsComponent: TestAppsComponent by locator
    private val usersComponent: UsersComponent by locator

    override fun applyAnnotation(annotation: Annotation) {
        when (annotation) {
            is EnsureHasDelegate -> enterpriseComponent.ensureHasDelegate(annotation)
            is EnsureHasDevicePolicyManagerRoleHolder ->
                enterpriseComponent.ensureHasDevicePolicyManagerRoleHolder(
                    annotation.onUser,
                    annotation.isPrimary
                )

            is EnsureHasDeviceOwner ->
                deviceOwnerComponent.ensureHasDeviceOwner(
                    annotation.failureMode,
                    annotation.isPrimary,
                    annotation.headlessDeviceOwnerType,
                    annotation.affiliationIds.toHashSet(),
                    annotation.type,
                    annotation.key,
                    TestAppProvider().query(annotation.dpc)
                )

            is EnsureHasNoDelegate -> enterpriseComponent.ensureHasNoDelegate(annotation.admin)
            is EnsureHasNoDeviceOwner -> deviceOwnerComponent.ensureHasNoDeviceOwner()

            is EnsureHasNoProfileOwner ->
                profileOwnersComponent.ensureHasNoProfileOwner(annotation.onUser)

            is EnsureHasProfileOwner ->
                profileOwnersComponent.ensureHasProfileOwner(annotation)

            is EnsureHasDeviceAdmin ->
                deviceAdminComponent.ensureHasDeviceAdmin(
                        annotation.key,
                        annotation.onUser,
                        annotation.isPrimary,
                        TestAppProvider().query(annotation.dpc)
                )

            is EnsureHasNoTestDeviceAdmin ->
                deviceAdminComponent.ensureHasNoTestDeviceAdmin(annotation.onUser)

            is RequireHasPolicyExemptApps -> annotation.logic()
            is MostImportantCoexistenceTest -> annotation.logic(
                deviceOwnerComponent,
                testAppsComponent
            )

            is MostRestrictiveCoexistenceTest -> annotation.logic(testAppsComponent)
            is EnsureHasWorkProfile -> enterpriseComponent.ensureHasWorkProfile(annotation)
            is RequireRunOnWorkProfile -> enterpriseComponent.requireRunOnWorkProfile(annotation)
            is EnsureHasNoWorkProfile -> usersComponent.ensureHasNoProfile(
                EnsureHasNoWorkProfile.PROFILE_TYPE,
                annotation.forUser
            )
        }
    }
}
