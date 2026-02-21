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

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.CREATE_USERS
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.OBSERVE_ROLE_HOLDERS
import android.Manifest.permission.QUERY_USERS
import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.supervision.withSupervisionRoleHeld
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Base class for supervision CTS tests. */
@AppModeFull(reason = "The SupervisionManager API is not available in instant apps.")
open class BaseSupervisionTest {

    fun setSupervisionEnabled(enabled: Boolean) {
        callWithShellPermissionIdentity(BYPASS_ROLE_QUALIFICATION, QUERY_USERS) {
            if (supervisionManager.isSupervisionEnabled() != enabled) {
                supervisionManager.setSupervisionEnabled(enabled)
            }
            assertThat(supervisionManager.isSupervisionEnabled()).isEqualTo(enabled)
        }
    }

    /**
     * Creates a new supervising user and executes the given [action].
     *
     * By default, supervision is enabled and a PIN is set for the supervising user.
     *
     * When the [action] completes, the supervising user will be removed and supervision will be
     * disabled.
     *
     * @param supervisionEnabled Whether supervision will be enabled when executing the [action].
     * @param hasPin Whether a PIN will be set for the supervising user.
     * @param action The block of code to execute.
     */
    fun withSupervisingUser(
        supervisionEnabled: Boolean = true,
        hasPin: Boolean = true,
        action: () -> Unit = {},
    ) {
        setSupervisionEnabled(supervisionEnabled)

        val userHandle = userManager.createSupervisingUser()
        try {
            if (hasPin) {
                val pin = "1234"
                val result =
                    runShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        "locksettings set-pin --user ${userHandle.identifier} $pin",
                    )
                assertThat(result).contains(pin)
            }
            action()
        } finally {
            setSupervisionEnabled(false)
            userManager.removeSupervisingUser(userHandle)
        }
    }

    /**
     * Installs and sets up the specified [count] of supervision apps to execute the given [action].
     *
     * Once the [action] completes (or fails), all installed apps are automatically uninstalled
     * to ensure a clean test environment.
     *
     * @param count The number of supervision apps to install.
     * @param action The block of code to execute, receiving the list of [TestAppInstance]s.
     */
    fun withSupervisionApps(
        count: Int = 1,
        action: (List<TestAppInstance>) -> Unit
    ) {
        val testAppProvider = TestAppProvider()
        val apps = installSupervisionApps(testAppProvider, "SupervisionApp", count)
        val packageNames = apps.map { it.packageName() }

        try {
            callWithShellPermissionIdentity(
                BYPASS_ROLE_QUALIFICATION,
                MANAGE_ROLE_HOLDERS,
                QUERY_USERS,
                OBSERVE_ROLE_HOLDERS
            ) {
                withSupervisionRoleHeld(packageNames) {
                    action(apps)
                }
            }
        } finally {
            runBlocking {
                apps.forEachParallel { uninstallAndWaitForBroadcast(it) }
            }
            setSupervisionEnabled(false)
        }
    }

    suspend fun <T> Iterable<T>.forEachParallel(action: suspend (T) -> Unit) = coroutineScope {
        map { item ->
            launch(Dispatchers.IO) {
                action(item)
            }
        }.joinAll()
    }

    fun installSupervisionApps(testAppProvider: TestAppProvider, appLabel: String, count: Int):
            List<TestAppInstance> {
        val testApps = testAppProvider.query().whereLabel().isEqualTo(appLabel).all.take(count)
        check(testApps.size == count) {
            "Could not find ${count} app(s) with label ${appLabel}"
        }

        runBlocking {
            testApps.forEachParallel { installAndWaitForBroadcast(it) }
        }

        return testApps.map { it.instance(TestApis.users().instrumented()) }
    }


    class TestBroadcastReceiver(
        val targetPackageName: String,
        val latch: CountDownLatch = CountDownLatch(1),
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.encodedSchemeSpecificPart
            if (packageName != null && targetPackageName.equals(packageName)) {
                latch.countDown()
            }
        }
    }

    fun installAndWaitForBroadcast(testApp: TestApp) {
        waitForBroadcast(testApp.packageName(), Intent.ACTION_PACKAGE_ADDED) {
            checkNotNull(testApp.install(TestApis.users().instrumented())) {
                "Failed to install ${testApp.packageName()}."
            }
        }
    }

    fun uninstallAndWaitForBroadcast (appInstance: TestAppInstance) {
        waitForBroadcast(appInstance.packageName(), Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            waitForBroadcast(appInstance.packageName(), Intent.ACTION_PACKAGE_REMOVED) {
                appInstance.uninstall()
            }
        }
    }

    fun waitForBroadcast (packageName: String, type: String, action: () -> Unit) {
        val latch = CountDownLatch(1)
        val broadcastReceiver = TestBroadcastReceiver(packageName, latch)
        val filter = IntentFilter(type)
        filter.addDataScheme("package")

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        try {
            action()
            assertThat(latch.await(TIMEOUT, TimeUnit.SECONDS)).isEqualTo(true)
        } finally {
            context.unregisterReceiver(broadcastReceiver)
        }
    }

    companion object {
        val context: Context = TestApis.context().instrumentedContext()
        val TIMEOUT = 60.seconds.inWholeSeconds
        val supervisionManager = context.getSystemService(SupervisionManager::class.java)!!
        val userManager = context.getSystemService(UserManager::class.java)!!
        val roleManager = context.getSystemService(RoleManager::class.java)!!
    }
}

private fun UserManager.createSupervisingUser(): UserHandle =
    callWithShellPermissionIdentity(CREATE_USERS) {
        val userInfo = createUser("Supervising", UserManager.USER_TYPE_PROFILE_SUPERVISING, 0)
        assertThat(userInfo).isNotNull()
        assertThat(userInfo?.userHandle).isNotNull()
        userInfo?.userHandle!!
    }

private fun UserManager.removeSupervisingUser(userHandle: UserHandle) {
    callWithShellPermissionIdentity(CREATE_USERS) {
        val removed = removeUser(userHandle)
        assertThat(removed).isTrue()
    }
}
