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

import android.app.supervision.PackageUsagePolicy
import android.app.supervision.flags.Flags
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.EventLogs
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(
    Flags.FLAG_ENABLE_APP_SERVICE_CONNECTION_CALLBACKS,
    Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE,
    Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
)
class SupervisionPolicyTest : BaseSupervisionTest() {

    @Before
    fun setUp() {
        EventLogs.resetLogs()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_packagePolicy_blocked_successfullyHidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_BLOCKED)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_packagePolicy_allowed_successfullyUnhidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_ALLOWED)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.PackageUsagePolicy#getPackageName",
                "android.app.supervision.PackageUsagePolicy#getType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_multiplePolicies_successfullyApplied() {
        withTestApp(MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME) { multipleActivitiesTestApp ->
            withTestApp(NOT_EMPTY_TEST_APP_PACKAGE_NAME) { notEmptyTestApp ->
                withSupervisionApps(count = 1) { (supervisionApp) ->
                    setSupervisionEnabled(true)
                    assertThat(supervisionApp.events().supervisionEnabled()).eventOccurred()

                    val policy1 =
                        PackageUsagePolicy.Builder(
                                multipleActivitiesTestApp.packageName(),
                                PackageUsagePolicy.TYPE_BLOCKED,
                            )
                            .build()
                    val policy2 =
                        PackageUsagePolicy.Builder(
                                notEmptyTestApp.packageName(),
                                PackageUsagePolicy.TYPE_ALLOWED,
                            )
                            .build()

                    setAndVerifyPackageUsagePolicy(policy1, supervisionApp)
                    setAndVerifyPackageUsagePolicy(policy2, supervisionApp)

                }
            }
        }
    }

    @Test
    fun setPolicy_multipleSupervisionApps_mostRecentPolicyApplied() {
        withTestApp(EMPTY_TEST_APP_PACKAGE_NAME) { _ ->
            withSupervisionApps(count = 2) { apps ->
                setSupervisionEnabled(true)
                runBlocking {
                    apps.forEachParallel {
                        assertThat(it.events().supervisionEnabled()).eventOccurred()
                    }
                }
                val policy1 = EMPTY_TEST_APP_BLOCKED_POLICY
                val policy2 = PackageUsagePolicy.Builder(
                    EMPTY_TEST_APP_PACKAGE_NAME,
                    PackageUsagePolicy.TYPE_ALLOWED
                ).setVersion(1).build()

                val (app1, app2) = apps
                setAndVerifyPackageUsagePolicy(policy1, app1)
                setAndVerifyPackageUsagePolicy(policy2, app2)
            }
        }
    }

    @Test
    fun setPolicy_multipleSupervisionApps_oneSetsAllNotified() {
        withSupervisionApps(count = 3) { apps ->

            setSupervisionEnabled(true)
            runBlocking {
                apps.forEachParallel {
                    assertThat(it.events().supervisionEnabled()).eventOccurred()
                }
            }

            apps[0].supervisionManager().setPolicy(EMPTY_TEST_APP_BLOCKED_POLICY)

            runBlocking {
                apps.forEachParallel {
                    assertThat(it.events().policyChanged()).eventOccurred()
                }
            }
        }
    }

    private fun verifySetPackageUsagePolicy(type: Int) {
        withTestApp(MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME) { testApp ->
            withSupervisionApps(count = 1) { (supervisionApp) ->
                setSupervisionEnabled(true)
                assertThat(supervisionApp.events().supervisionEnabled()).eventOccurred()
                val policy = PackageUsagePolicy.Builder(testApp.packageName(), type).build()
                setAndVerifyPackageUsagePolicy(policy, supervisionApp)
            }
        }
    }

    private fun setAndVerifyPackageUsagePolicy(
        policy: PackageUsagePolicy,
        supervisionApp: TestAppInstance,
    ) {
        val expectedEnabledState =
            when (policy.type) {
                PackageUsagePolicy.TYPE_ALLOWED -> true
                PackageUsagePolicy.TYPE_BLOCKED -> false
                else -> throw IllegalArgumentException("Unsupported policy type: ${policy.type}")
            }
        // If the app is already in the expected state, then no broadcast will be sent.
        val expectBroadcast = getApplicationEnabledState(policy.packageName) != expectedEnabledState
        val expectedPolicy =
            PackageUsagePolicy.Builder(policy).setVersion(policy.version + 1).build()

        // Set up a broadcast receiver to wait for the broadcast.
        val latch = CountDownLatch(1)
        val broadcastReceiver = TestBroadcastReceiver(policy.packageName, latch)
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        try {
            supervisionApp.supervisionManager().setPolicy(policy)
            // Verify that the onPolicyChanged event is logged with the correct policy.
            val onPolicyChangedEvent =
                supervisionApp.events().policyChanged().wherePolicy().isEqualTo(expectedPolicy)
            assertThat(onPolicyChangedEvent).eventOccurredWithin(Duration.ofSeconds(TIMEOUT))
            assertThat(supervisionApp.supervisionManager().getPolicies())
                .contains(expectedPolicy)
            if (expectBroadcast) {
                assertThat(latch.await(TIMEOUT, TimeUnit.SECONDS)).isEqualTo(true)
            }
        } finally {
            context.unregisterReceiver(broadcastReceiver)
        }

        assertThat(getApplicationEnabledState(policy.packageName)).isEqualTo(expectedEnabledState)
    }


    private fun withTestApp(packageName: String, action: (app: TestAppInstance) -> Unit) {
        val app: TestApp = TestAppProvider().query().wherePackageName().isEqualTo(packageName).get()

        installAndWaitForBroadcast(app)
        val appInstance = app.instance(TestApis.users().instrumented())

        try {
            action(appInstance)
        } finally {
            uninstallAndWaitForBroadcast(appInstance)
        }
    }

    private fun getApplicationEnabledState(packageName: String): Boolean {
        val pm: PackageManager = context.packageManager
        try {
            // First, check for installed apps. This will return the correct enabled state.
            val appInfo = pm.getApplicationInfo(packageName, 0)
            return appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            // Not found in installed apps, check uninstalled apps.
            try {
                pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                // If found in uninstalled, it is not enabled.
                return false
            } catch (e2: PackageManager.NameNotFoundException) {
                // Not found in either installed or uninstalled.
                throw IllegalStateException(
                    "Package $packageName not found in installed or uninstalled apps"
                )
            }
        }
    }

    private companion object {
        const val EMPTY_TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.EmptyTestApp"
        const val MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.testapp.MultipleActivitiesTestApp"
        const val NOT_EMPTY_TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.NotEmptyTestApp"
        val EMPTY_TEST_APP_BLOCKED_POLICY =
            PackageUsagePolicy.Builder(
                EMPTY_TEST_APP_PACKAGE_NAME,
                PackageUsagePolicy.TYPE_BLOCKED,
            )
                .build()
    }
}
