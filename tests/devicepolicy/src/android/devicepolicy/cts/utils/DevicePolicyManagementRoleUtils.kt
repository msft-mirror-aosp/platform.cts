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

package android.devicepolicy.cts.utils

import android.R
import android.app.role.RoleManager
import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import com.android.bedstead.nene.TestApis

class DevicePolicyManagementRoleUtils {
    companion object {
        @JvmStatic
        fun removeNonDefaultRoleHolders(context: Context) {
            val users = TestApis.users().all()

            val defaultRoleHolder = getDefaultRoleHolderPackageName(context)

            users.forEach { userReference ->
                TestApis
                    .roles()
                    .getRoleHoldersAsUser(userReference, RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT)
                    .filterNot { it == defaultRoleHolder }
                    .forEach { packageName ->
                        val pkg = TestApis.packages().find(packageName)
                        TestApis
                            .devicePolicy()
                            .unsetDevicePolicyManagementRoleHolder(pkg, userReference)
                    }
            }
        }

        private fun getDefaultRoleHolderPackageName(context: Context): String? {
            val resId = Resources.getSystem().getIdentifier(
                "config_devicePolicyManagement",
                "string",
                "android"
            )
            if (resId == 0) return null
            val packageNameAndSignature = context.getString(R.string.config_devicePolicyManagement)
            if (TextUtils.isEmpty(packageNameAndSignature)) {
                return null
            }
            return packageNameAndSignature.substringBefore(':')
        }
    }
}
