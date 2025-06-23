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

import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_SUPERVISION
import android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION
import android.app.supervision.SupervisionManager
import android.content.Context
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

fun withSystemSupervisionRoleHeld(action: () -> Unit) =
    withRoleHeld(ROLE_SYSTEM_SUPERVISION, action)

fun withSupervisionRoleHeld(action: () -> Unit) =
    withRoleHeld(ROLE_SUPERVISION, action)

private fun withRoleHeld(roleName: String, action: () -> Unit) {
    val context = TestApis.context().instrumentedContext()
    val roleManager = context.getSystemService(RoleManager::class.java)
    val supervisionManager = context.getSystemService(SupervisionManager::class.java)

    supervisionManager.setSupervisionEnabled(false)
    assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

    try {
        roleManager.addRoleHolder(context, roleName)
        action()
    } finally {
        roleManager.removeRoleHolder(context, roleName)
        supervisionManager.setSupervisionEnabled(false)
    }
}

private val TIMEOUT = 5000L

/**
 * Adds this package as the role holder for the [roleName] role.
 *
 * This function then blocks until it can confirm the role is held. It waits for the callback from
 * `addRoleHolderAsUser` and for the `OnRoleHoldersChangedListener` to be called, before finally
 * verifying that `getRoleHolders` includes this package.
 */
private fun RoleManager.addRoleHolder(context: Context, roleName: String) {
    val channel = Channel<String>()
    val user = context.getUser()
    val listener = OnRoleHoldersChangedListener { changedRoleName, _ ->
        if (roleName == changedRoleName) {
            runBlocking {
                channel.send(roleName)
            }
        }
    }
    addOnRoleHoldersChangedListenerAsUser(context.getMainExecutor(), listener, user)

    try {
        setBypassingRoleQualification(true)
        val callbackResult = runBlocking {
            withTimeout(TIMEOUT) {
                addRoleHolderInternal(context, roleName)
            }
        }
        assertThat(callbackResult).isTrue()

        val listenerResult = runBlocking {
            withTimeout(TIMEOUT) {
                channel.receive()
            }
        }
        assertThat(listenerResult).isEqualTo(roleName)

        assertThat(getRoleHolders(roleName)).contains(context.packageName)
    } finally {
        setBypassingRoleQualification(false)
        removeOnRoleHoldersChangedListenerAsUser(listener, user)
    }
}

private fun RoleManager.removeRoleHolder(context: Context, roleName: String) {
    val result = runBlocking {
        withTimeout(TIMEOUT) {
            removeRoleHolderInternal(context, roleName)
        }
    }
    assertThat(result).isTrue()
}

private suspend fun RoleManager.addRoleHolderInternal(
    context: Context,
    roleName: String
): Boolean =
    suspendCancellableCoroutine { continuation ->
        addRoleHolderAsUser(
            roleName,
            context.getPackageName(),
            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
            context.getUser(),
            context.getMainExecutor(),
            { successful ->
                continuation.resume(successful) { _, _, _ -> }
            }
        )
    }

private suspend fun RoleManager.removeRoleHolderInternal(
    context: Context,
    roleName: String
): Boolean =
    suspendCancellableCoroutine { continuation ->
        removeRoleHolderAsUser(
            roleName,
            context.getPackageName(),
            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
            context.getUser(),
            context.getMainExecutor(),
            { successful ->
                continuation.resume(successful) { _, _, _ -> }
            }
        )
    }
