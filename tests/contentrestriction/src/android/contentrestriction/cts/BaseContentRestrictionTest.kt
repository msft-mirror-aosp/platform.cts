/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.contentrestriction.cts

import android.app.admin.DevicePolicyManager
import android.app.admin.PolicyIdentifier
import android.app.contentrestriction.ContentRestrictionAppService
import android.app.contentrestriction.ContentRestrictionManager
import android.content.Context
import android.content.Intent
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.queryable.queries.IntentFilterQuery.intentFilter
import com.android.queryable.queries.ServiceQuery.service
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before


/** Base class for content restriction CTS tests. */
open class BaseContentRestrictionTest {

    fun setRestrictionApps(apps: List<String>) {
        runWithShellPermissionIdentity({
            devicePolicyManager.setPolicy(
                PolicyIdentifier.CONTENT_RESTRICTION_APPS,
                DevicePolicyManager.POLICY_SCOPE_USER,
                apps
            )
        }, "android.permission.MANAGE_DEVICE_POLICY_CONTENT_RESTRICTION_APPS")
    }

    /**
     * Installs and sets up the specified [count] of content restriction apps to execute the given [action].
     *
     * Once the [action] completes (or fails), all installed apps are automatically uninstalled
     * to ensure a clean test environment.
     *
     * @param count The number of content restriction apps to install.
     * @param action The block of code to execute, receiving the list of [TestAppInstance]s.
     */
    fun withContentRestrictionApps(
        count: Int = 1,
        action: (List<TestAppInstance>) -> Unit
    ) {
        val testAppProvider = TestAppProvider()
        val apps = installContentRestrictionApps(testAppProvider, count)
        val packageNames = apps.map { it.packageName() }

        try {
            setRestrictionApps(packageNames)
            action(apps)
        } finally {
            setRestrictionApps(emptyList())
            runBlocking {
                apps.forEachParallel { it.uninstall() }
            }
        }
    }

    suspend fun <T> Iterable<T>.forEachParallel(action: suspend (T) -> Unit) = coroutineScope {
        map { item ->
            launch(Dispatchers.IO) {
                action(item)
            }
        }.joinAll()
    }

    fun installContentRestrictionApps(testAppProvider: TestAppProvider, count: Int):
            List<TestAppInstance> {
        val testApps = testAppProvider.query()
            .whereServices().contains(
                service().where().intentFilters().contains(
                    intentFilter().where().actions().contains(
                        ContentRestrictionAppService.ACTION_CONTENT_RESTRICTION_APP_SERVICE
                    )
                )
            )
            .all.take(count)
        check(testApps.size == count) {
            "Could not find ${count} app(s) with service action " +
                    ContentRestrictionAppService.ACTION_CONTENT_RESTRICTION_APP_SERVICE
        }

        runBlocking {
            testApps.forEachParallel {
                checkNotNull(it.install(TestApis.users().instrumented())) {
                    "Failed to install ${it.packageName()}."
                }
            }
        }

        return testApps.map { it.instance(TestApis.users().instrumented()) }
    }

    @Before
    fun setUpBase() {
        devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)!!
        contentRestrictionManager =
            context.getSystemService(ContentRestrictionManager::class.java)!!
    }

    companion object {
        val context: Context = TestApis.context().instrumentedContext()
        lateinit var devicePolicyManager: DevicePolicyManager
        lateinit var contentRestrictionManager: ContentRestrictionManager
    }
}
