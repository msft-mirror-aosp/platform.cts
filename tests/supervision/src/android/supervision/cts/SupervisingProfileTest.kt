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

import android.Manifest.permission.CREATE_USERS
import android.Manifest.permission.MANAGE_USERS
import android.Manifest.permission.QUERY_USERS
import android.os.UserHandle
import android.os.UserManager
import android.os.UserManager.USER_TYPE_FULL_SECONDARY
import android.util.Log
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class SupervisingProfileTest : BaseSupervisionTest() {

    @Test
    @EnsureHasPermission(MANAGE_USERS, CREATE_USERS, QUERY_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    @RequireFlagsEnabled(
        android.multiuser.Flags.FLAG_DECOUPLE_MAX_USERS_FROM_PROFILES,
        android.multiuser.Flags.FLAG_CONSISTENT_MAX_USERS,
        android.multiuser.Flags.FLAG_ALLOW_SUPERVISING_PROFILE,
    )
    fun canCreateSupervisingProfile_atMaxSecondaryUsers() {
        withMaxSwitchableUsers { withSupervisingUser {} }
    }

    private fun withMaxSwitchableUsers(action: () -> Unit) {
        val users = userManager.createMaxSwitchableUsers()
        try {
            action()
        } finally {
            userManager.removeUsers(users)
        }
    }

    private fun UserManager.createMaxSwitchableUsers(): List<UserHandle> {
        val testUsers = mutableListOf<UserHandle>()
        var maxUsersReached = false
        while (!maxUsersReached) {
            val user = createUser("TestUser-${testUsers.size}", USER_TYPE_FULL_SECONDARY, 0)
            if (user != null) {
                Log.d(TAG, "Created test user ${user.id}")
                testUsers.add(user.userHandle)
            } else {
                maxUsersReached = true
            }
        }
        return testUsers
    }

    private fun UserManager.removeUsers(usersToRemove: List<UserHandle>) {
        for (userHandle in usersToRemove) {
            if (removeUser(userHandle)) {
                Log.d(TAG, "Removed test user ${userHandle.identifier}")
            } else {
                // Will fail the check below
                Log.w(TAG, "Failed to remove test user ${userHandle.identifier}")
            }
        }
        assertThat(users.map { it.userHandle }).containsNoneIn(usersToRemove)
    }

    companion object {
        private val TAG = SupervisingProfileTest::class.java.simpleName
    }
}
