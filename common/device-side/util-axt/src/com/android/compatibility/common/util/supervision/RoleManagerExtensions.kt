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

package com.android.compatibility.common.util.supervision

import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_SUPERVISION
import android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION
import android.app.supervision.SupervisionManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun withSystemSupervisionRoleHeld(action: () -> Unit) =
    withRoleHeld(ROLE_SYSTEM_SUPERVISION, action)

fun withSupervisionRoleHeld(action: () -> Unit) =
    withRoleHeld(ROLE_SUPERVISION, action)

fun withSupervisionRoleHeld(packageNames: List<String>, action: () -> Unit) =
    withRoleHeld(ROLE_SUPERVISION, action, packageNames)

/**
 * Executes the given [action] while the specified [packages] hold the [roleName].
 *
 * This method disables supervision and utilizes the `BYPASS_ROLE_QUALIFICATION` permission to grant
 * the role. Once the [action] is completed, it automatically removes the role holders and restores
 * the supervision state.
 */
private fun withRoleHeld(roleName: String, action: () -> Unit, packages: List<String>? = null) {
    val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    val roleManager = context.getSystemService(RoleManager::class.java)
    val supervisionManager = context.getSystemService(SupervisionManager::class.java)
    val roleHolderPackages = packages ?: listOf(context.packageName)
    try {
        supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(true)
        assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isTrue()
        roleManager.setBypassingRoleQualification(true)
        roleHolderPackages.forEach { roleManager.addRoleHolder(context, roleName, it) }
        action()
    } finally {
        roleHolderPackages.forEach { roleManager.removeRoleHolder(context, roleName, it) }
        roleManager.setBypassingRoleQualification(false)
        supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(false)
        assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isFalse()
    }
}

private val TIMEOUT = 5.seconds

/**
 * Adds this package as the role holder for the [roleName] role.
 *
 * This function then blocks until it can confirm the role is held. It waits for the callback from
 * `addRoleHolderAsUser` and for the `OnRoleHoldersChangedListener` to be called, before finally
 * verifying that `getRoleHolders` includes this package.
 */
private fun RoleManager.addRoleHolder(context: Context, roleName: String, packageName: String) {
    val listenerLatch = CountDownLatch(1)
    val user = context.getUser()
    val listener = OnRoleHoldersChangedListener { changedRoleName, _ ->
        if (roleName == changedRoleName) {
            listenerLatch.countDown()
        }
    }
    addOnRoleHoldersChangedListenerAsUser(context.getMainExecutor(), listener, user)

    try {
        val callbackResult = runBlocking {
            withTimeoutOrNull(TIMEOUT) {
                addRoleHolderInternal(context, roleName, packageName)
            }
        }
        assertThat(callbackResult).isTrue()

        val listenerCalled = listenerLatch.await(TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
        assertThat(listenerCalled).isTrue()
        assertThat(getRoleHolders(roleName)).contains(packageName)
    } finally {
        removeOnRoleHoldersChangedListenerAsUser(listener, user)
    }
}

private fun RoleManager.removeRoleHolder(context: Context, roleName: String, packageName: String) {
    val result = runBlocking {
        withTimeoutOrNull(TIMEOUT) {
            removeRoleHolderInternal(context, roleName, packageName)
        }
    }
    assertThat(result).isTrue()
}

private suspend fun RoleManager.addRoleHolderInternal(
    context: Context,
    roleName: String,
    packageName: String
): Boolean =
    suspendCancellableCoroutine { continuation ->
        addRoleHolderAsUser(
            roleName,
            packageName,
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
    roleName: String,
    packageName: String
): Boolean =
    suspendCancellableCoroutine { continuation ->
        removeRoleHolderAsUser(
            roleName,
            packageName,
            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
            context.getUser(),
            context.getMainExecutor(),
            { successful ->
                continuation.resume(successful) { _, _, _ -> }
            }
        )
    }
