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
package com.android.bedstead.permissions

import android.Manifest.permission
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.permissions
import android.app.AppOpsManager
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.utils.Assert.assertDoesNotThrow
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.permissions.CommonPermissions.CREATE_USERS
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS
import com.android.bedstead.permissions.CommonPermissions.MANAGE_USERS
import com.android.bedstead.permissions.annotations.EnsureDoesNotHaveAppOp
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasAppOp
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PermissionsTest {
    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.Q,
        reason = "drop shell permissions only available on Q+"
    )
    fun default_permissionIsNotGranted() {
        ShellCommandUtils.uiAutomation().dropShellPermissionIdentity()

        assertThat(context.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.Q,
        reason = "adopt shell permissions only available on Q+"
    )
    fun withPermission_shellPermission_permissionIsGranted() {
        TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL).use {
            assertThat(context.checkSelfPermission(
                PERMISSION_HELD_BY_SHELL
            ))
                    .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    @RequireSdkVersion(
        max = Build.VERSION_CODES.P,
        reason = "adopt shell permissions only available on Q+"
    )
    fun withoutPermission_alreadyGranted_androidPreQ_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withoutPermission(
                DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S
            )
        }
    }

    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.Q,
        reason = "adopt shell permissions only available on Q+"
    )
    fun withoutPermission_permissionIsNotGranted() {
        TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL).use {
            TestApis.permissions().withoutPermission(PERMISSION_HELD_BY_SHELL).use {
                assertThat(context.checkSelfPermission(
                    PERMISSION_HELD_BY_SHELL
                )).isEqualTo(PackageManager.PERMISSION_DENIED)
            }
        }
    }

    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.Q,
        reason = "adopt shell permissions only available on Q+"
    )
    fun autoclose_withoutPermission_permissionIsGrantedAgain() {
        TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL).use {
            TestApis.permissions().withoutPermission(PERMISSION_HELD_BY_SHELL).use { }

            assertThat(context.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                    .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    @RequireSdkVersion(
        max = Build.VERSION_CODES.P,
        reason = "adopt shell permissions only available on Q+"
    )
    fun withoutPermission_installPermission_androidPreQ_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withoutPermission(INSTALL_PERMISSION)
        }
    }

    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.Q,
        max = Build.VERSION_CODES.R,
        reason = "adopt shell permissions only available on Q+ - after S - all available" +
                " permissions are held by shell"
    )
    fun withoutPermission_permissionIsAlreadyGrantedInInstrumentedApp_permissionIsNotGranted() {
        TestApis.permissions().withoutPermission(
            DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S
        ).use {
            assertThat(
                context.checkSelfPermission(DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S)
            )
                    .isEqualTo(PackageManager.PERMISSION_DENIED)
        }
    }

    @Test
    @RequireSdkVersion(
        max = Build.VERSION_CODES.P,
        reason = "adopt shell permissions only available on Q+"
    )
    fun withoutPermission_permissionIsAlreadyGrantedInInstrumentedApp_androidPreQ_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withoutPermission(
                DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S
            )
        }
    }

    @Test
    fun withPermission_permissionIsAlreadyGrantedInInstrumentedApp_permissionIsGranted() {
        TestApis.permissions().withPermission(
            DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S
        ).use {
            assertThat(
                context.checkSelfPermission(DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S)
            )
                    .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    fun withPermission_nonExistingPermission_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withPermission(NON_EXISTING_PERMISSION)
        }
    }

    @Test
    fun withoutPermission_nonExistingPermission_doesNotThrowException() {
        TestApis.permissions().withoutPermission(NON_EXISTING_PERMISSION).use { }
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.R)
    fun withPermissionAndWithoutPermission_bothApplied() {
        TestApis.permissions().withPermission(PERMISSION_HELD_BY_SHELL)
                .withoutPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL).use {
                    assertThat(context.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                            .isEqualTo(PackageManager.PERMISSION_GRANTED)
                    assertThat(context.checkSelfPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL))
                            .isEqualTo(PackageManager.PERMISSION_DENIED)
                }
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.R)
    fun withoutPermissionAndWithPermission_bothApplied() {
        TestApis.permissions()
                .withoutPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL)
                .withPermission(PERMISSION_HELD_BY_SHELL).use {
                    assertThat(context.checkSelfPermission(PERMISSION_HELD_BY_SHELL))
                            .isEqualTo(PackageManager.PERMISSION_GRANTED)
                    assertThat(context.checkSelfPermission(DIFFERENT_PERMISSION_HELD_BY_SHELL))
                            .isEqualTo(PackageManager.PERMISSION_DENIED)
                }
    }

    @Test
    fun withPermissionAndWithoutPermission_contradictoryPermissions_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions()
                    .withPermission(PERMISSION_HELD_BY_SHELL)
                    .withoutPermission(PERMISSION_HELD_BY_SHELL)
        }
    }

    @Test
    fun withoutPermissionAndWithPermission_contradictoryPermissions_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions()
                    .withoutPermission(PERMISSION_HELD_BY_SHELL)
                    .withPermission(PERMISSION_HELD_BY_SHELL)
        }
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.R, max = Build.VERSION_CODES.R)
    fun withPermissionOnVersion_onVersion_hasPermission() {
        TestApis.permissions().withPermissionOnVersion(
            Build.VERSION_CODES.R,
            permission.MANAGE_EXTERNAL_STORAGE
        ).use {
            assertThat(
                context.checkSelfPermission(permission.MANAGE_EXTERNAL_STORAGE)
            )
                    .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    fun withPermissionOnVersion_notOnVersion_doesNotHavePermission() {
        TestApis.permissions().withPermissionOnVersion(
            Build.VERSION_CODES.R,
            permission.MANAGE_EXTERNAL_STORAGE
        ).use {
            assertThat(
                context.checkSelfPermission(permission.MANAGE_EXTERNAL_STORAGE)
            )
                    .isNotEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @Test
    @EnsureHasPermission(permission.INTERACT_ACROSS_USERS_FULL)
    fun hasPermission_permissionIsGranted_returnsTrue() {
        assertThat(
            TestApis.permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isTrue()
    }

    @Test
    @EnsureDoesNotHavePermission(permission.INTERACT_ACROSS_USERS_FULL)
    fun hasPermission_permissionIsNotGranted_returnsFalse() {
        assertThat(
            TestApis.permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isFalse()
    }

    @Test
    fun withAppOp_appOpIsGranted() {
        TestApis.permissions().withAppOp(APP_OP).use {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue()
        }
    }

    @Test
    fun withoutAppOp_appOpIsNotGranted() {
        TestApis.permissions().withoutAppOp(APP_OP).use {
            assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse()
        }
    }

    @Test
    fun withAppOpAndPermission_hasBoth() {
        TestApis.permissions().withAppOp(APP_OP)
                .withPermission(permission.INTERACT_ACROSS_USERS_FULL).use {
                    assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue()
                    assertThat(
                        TestApis.permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
                    )
                            .isTrue()
                }
    }

    @Test
    fun withoutAppOpAndWithPermission_hasPermissionButNotAppOp() {
        TestApis.permissions().withoutAppOp(APP_OP)
                .withPermission(permission.INTERACT_ACROSS_USERS_FULL).use {
                    assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse()
                    assertThat(
                        TestApis.permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
                    )
                            .isTrue()
                }
    }

    @Test
    @EnsureHasAppOp(APP_OP)
    fun hasAppOpAllowed_appOpAllowed_isTrue() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue()
    }

    @Test
    @EnsureDoesNotHaveAppOp(APP_OP)
    fun hasAppOpAllowed_appOpNotAllowed_isFalse() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse()
    }

    @Test
    fun withPermission_unadoptablePermission_withoutRoot_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withPermission(MANAGE_USERS)
        }
    }

    @Test
    fun withoutPermission_undroppablePermission_withoutRoot_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.permissions().withoutPermission(INSTALL_PERMISSION)
        }
    }

    @Test
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun withPermission_unadoptablePermission_withRoot_hasPermission() {
        TestApis.permissions().withPermission(MANAGE_USERS).use {
            assertThat(TestApis.permissions().hasPermission(MANAGE_USERS)).isTrue()
        }
    }

    @Test
    @RequireRootInstrumentation(reason = "Use of INSTALL_PERMISSION")
    fun withoutPermission_undroppablePermission_withRoot_doesNotHavePermission() {
        TestApis.permissions().withoutPermission(INSTALL_PERMISSION).use {
            assertThat(TestApis.permissions().hasPermission(INSTALL_PERMISSION)).isFalse()
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun ensureHasPermissionAnnotation_unadoptablePermission_withRoot_hasPermission() {
        assertThat(TestApis.permissions().hasPermission(MANAGE_USERS)).isTrue()
    }

    @Test
    @EnsureDoesNotHavePermission(INSTALL_PERMISSION)
    @RequireRootInstrumentation(reason = "Use of INSTALL_PERMISSION")
    fun ensureDoesNotHavePermissionAnnotation_undroppablePermission_withRoot_doesNotHavePermission() {
        assertThat(TestApis.permissions().hasPermission(INSTALL_PERMISSION)).isFalse()
    }

    @Test
    fun withPermission_flakeTest() {
        for (i in 0..100000) {
            TestApis.permissions().withPermission(CREATE_USERS, INTERACT_ACROSS_USERS).use {
                assertWithMessage("Attempt $i").that(TestApis.permissions()
                        .hasPermission(CREATE_USERS)).isTrue()
                assertWithMessage("Attempt $i").that(TestApis.permissions()
                        .hasPermission(INTERACT_ACROSS_USERS)).isTrue()
            }
            assertWithMessage("Attempt $i").that(TestApis.permissions().hasPermission(CREATE_USERS))
                    .isFalse()
            assertWithMessage("Attempt $i").that(TestApis.permissions()
                    .hasPermission(INTERACT_ACROSS_USERS)).isFalse()
        }
    }

    @Test
    fun withPermission_serverCall_flakeTest() {
        for (i in 0..100000) {
            TestApis.permissions().withPermission(CREATE_USERS).use {
                // Here we check with actual server calls to capture flakes in the system server that don't show uop in the hasPermission check
                assertDoesNotThrow("Attempt $i") { userManager.userName }

            }

            assertThrows("Expected to throw attempt $i") { userManager.userName }
        }
    }

    @Test
    fun withAppOp_flakeTest() {
        for (i in 0..100000) {
            TestApis.permissions().withAppOp(APP_OP).use {
                assertWithMessage("Attempt $i").that(TestApis.permissions().hasAppOpAllowed(APP_OP))
                        .isTrue()
            }
            assertWithMessage("Attempt $i").that(TestApis.permissions().hasAppOpAllowed(APP_OP))
                    .isFalse()

        }
    }

    @Test
    fun dump_dumpsState() {
        assertThat(permissions().dump()).isNotEmpty()
    }

    companion object {
        @ClassRule
        @Rule
        @JvmField
        val deviceState = DeviceState()

        private const val APP_OP = AppOpsManager.OPSTR_FINE_LOCATION
        private const val PERMISSION_HELD_BY_SHELL = "android.permission.INTERACT_ACROSS_PROFILES"
        private const val DIFFERENT_PERMISSION_HELD_BY_SHELL =
            "android.permission.INTERACT_ACROSS_USERS_FULL"
        private val context = TestApis.context().instrumentedContext()
        private const val NON_EXISTING_PERMISSION = "permissionWhichDoesNotExist"

        // We expect these permissions are listed in the Manifest
        private const val INSTALL_PERMISSION = "android.permission.CHANGE_WIFI_STATE"
        private const val DECLARED_PERMISSION_NOT_HELD_BY_SHELL_PRE_S =
            "android.permission.INTERNET"

        private val userManager = TestApis.context().instrumentedContext().getSystemService(
            UserManager::class.java)!!
    }
}
