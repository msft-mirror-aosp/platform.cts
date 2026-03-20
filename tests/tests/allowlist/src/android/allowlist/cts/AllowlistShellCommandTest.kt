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
package android.allowlist.cts

import android.annotation.SuppressLint
import android.app.appfunctions.flags.Flags
import android.content.pm.SignedPackage
import android.os.Bundle
import android.os.allowlist.AllowlistManager
import android.os.allowlist.AllowlistRequest
import android.os.allowlist.AllowlistResponse
import android.os.allowlist.SignedPackageMultiMap
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import java.util.HexFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
@AppModeFull(reason = "AllowlistManager cannot be accessed by instant apps")
@SuppressLint("MissingPermission")
class AllowlistShellCommandTest {
    private val testPackage1 = SignedPackage("test.package.1", byteArrayOf(0x1))
    private val testPackage2 = SignedPackage("test.package.2", byteArrayOf(0x2))
    private val testTarget1 = "test.target.1"
    private val testTarget2 = "test.target.2"
    private val hexFormat = HexFormat.of()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val allowlistManager = context.getSystemService(AllowlistManager::class.java)!!

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        runShellCommand("cmd allowlist clear-shell-allowlist ${AllowlistManager.ALLOWLIST_ID_TEST}")
    }

    @After
    fun tearDown() {
        runShellCommand("cmd allowlist clear-shell-allowlist ${AllowlistManager.ALLOWLIST_ID_TEST}")
        runWithShellPermissionIdentity {
            allowlistManager.setTestProviderEnabled(false)
        }
    }

    @Test
    fun testAddPackage_emptyFilter_returnsAllPackages() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage1.toShellString()},${testPackage2.toShellString()}"
        )

        val request = createRequest(arrayListOf())
        val allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).contains(testPackage1)
        assertThat(allowedPackages).contains(testPackage2)
    }

    @Test
    fun testAddPackage_nonEmptyFilter_returnsFilteredPackages() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage1.toShellString()},${testPackage2.toShellString()}"
        )

        val request = createRequest(arrayListOf(testPackage1))
        val allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(testPackage1)
    }

    @Test
    fun testAddPackage_addWildcard_returnsWildcardPackage() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} *"
        )

        val request = createRequest(arrayListOf(testPackage1, testPackage2))
        val allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(SignedPackage("*", null))
    }

    @Test
    fun testAddPackageMultiMap_emptyFilter_returnsAll() {
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage1.toShellString()} " +
                    "$testTarget1,$testTarget2"
        )

        val request = createRequest(arrayListOf(), arrayListOf())
        val allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap).containsKey(testPackage1)
        assertThat(allowedMap[testPackage1]?.map { it.packageName }).containsExactly(
            testTarget1,
            testTarget2
        )
    }

    @Test
    fun testAddPackageMultiMap_filterPackage_returnsFilteredPackagesAndTargets() {
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage1.toShellString()} " +
                    "$testTarget1,$testTarget2"
        )
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage2.toShellString()} " +
                    testTarget1
        )

        val signedTestTarget2 = SignedPackage(testTarget2, null)
        val request = createRequest(arrayListOf(testPackage1), arrayListOf(signedTestTarget2))
        val allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap).containsKey(testPackage1)
        assertThat(allowedMap[testPackage1]?.map { it.packageName }).containsExactly(testTarget2)
    }

    @Test
    fun testAddPackageMultiMap_addWildcardForPackage_returnsWildcardPackage() {
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} * $testTarget1,$testTarget2"
        )

        val signedTestTarget2 = SignedPackage(testTarget2, null)
        val request = createRequest(arrayListOf(testPackage1), arrayListOf(signedTestTarget2))
        val allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        val wildCardPackage = SignedPackage("*", null)
        assertThat(allowedMap).containsKey(wildCardPackage)
        assertThat(allowedMap[wildCardPackage]?.map { it.packageName }).containsExactly(testTarget2)
    }

    @Test
    fun testAddPackageMultiMap_addWildcardForTargets_returnsWildcardTargets() {
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage2.toShellString()} *"
        )

        val signedTestTarget1 = SignedPackage(testTarget1, null)
        val request = createRequest(arrayListOf(testPackage2), arrayListOf(signedTestTarget1))
        val allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap).containsKey(testPackage2)
        assertThat(allowedMap[testPackage2]?.map { it.packageName }).containsExactly("*")
    }

    @Test
    fun testRemovePackage_removedFromShellPackageAllowlist() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage1.toShellString()},${testPackage2.toShellString()}"
        )

        val request = createRequest(arrayListOf())
        var allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(testPackage1, testPackage2)

        runShellCommand(
            "cmd allowlist remove-package ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    testPackage1.toShellString()
        )

        allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(testPackage2)
    }

    @Test
    fun testRemovePackage_removedFromShellPackageTargetsAllowlist() {
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage1.toShellString()} " +
                    "$testTarget1,$testTarget2"
        )
        runShellCommand(
            "cmd allowlist add-package-multimap " +
                    "${AllowlistManager.ALLOWLIST_ID_TEST} ${testPackage2.toShellString()} " +
                    testTarget1
        )

        val request = createRequest(arrayListOf(), arrayListOf())
        var allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap.keys).containsExactly(testPackage1, testPackage2)

        runShellCommand(
            "cmd allowlist remove-package ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    testPackage1.toShellString()
        )
        allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap.keys).containsExactly( testPackage2)
    }

    @Test
    fun testClearShellAllowlist_allowlistCleared() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    testPackage1.toShellString()
        )
        runShellCommand(
            "cmd allowlist add-package-multimap ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage2.toShellString()} $testTarget1"
        )

        var output =
            runShellCommand("cmd allowlist list-shell-allowlist " +
                    AllowlistManager.ALLOWLIST_ID_TEST)
        assertThat(output)
            .contains("SignedPackage{packageName=test.package.1, certificateDigest=01}")

        runShellCommand("cmd allowlist clear-shell-allowlist " +
                AllowlistManager.ALLOWLIST_ID_TEST)
        output =
            runShellCommand("cmd allowlist list-shell-allowlist " +
                    AllowlistManager.ALLOWLIST_ID_TEST)
        assertThat(output).contains("No Shell allowlist for ID " +
                AllowlistManager.ALLOWLIST_ID_TEST)
    }

    @Test
    fun testListShellAllowlist_withPackageOnly_dumpedShellAllowlist() {
        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    testPackage1.toShellString()
        )

        val output =
            runShellCommand("cmd allowlist list-shell-allowlist " +
                    AllowlistManager.ALLOWLIST_ID_TEST)
        assertThat(output)
            .contains("SignedPackage{packageName=test.package.1, certificateDigest=01}")
    }

    @Test
    fun testListShellAllowlist_withPackageTargets_dumpedShellAllowlist() {
        runShellCommand(
            "cmd allowlist add-package-multimap ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage2.toShellString()} $testTarget1,$testTarget2"
        )

        val output =
            runShellCommand("cmd allowlist list-shell-allowlist " +
                    AllowlistManager.ALLOWLIST_ID_TEST)
        assertThat(output)
            .contains("SignedPackage{packageName=test.package.2, certificateDigest=02}")
        assertThat(output)
            .contains("SignedPackage{packageName=test.target.1, certificateDigest=null}")
        assertThat(output)
            .contains("SignedPackage{packageName=test.target.2, certificateDigest=null}")
    }

    @Test
    fun testQueryAllowlist_withTestProvider_mergedPackagesWithProvider() {
        runWithShellPermissionIdentity {
            allowlistManager.setTestProviderEnabled(true)
        }

        runShellCommand(
            "cmd allowlist add-packages ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage1.toShellString()},${testPackage2.toShellString()}"
        )

        var request = createRequest(arrayListOf(testPackage1))
        var allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(testPackage1)

        // TestAllowlistProvider naively returns the requested package. testPackage3 is not
        // overridden by adb shell command, we expect it to be returned by TestAllowlistProvider.
        val testPackage3 = SignedPackage("test.package.3", byteArrayOf(0x3))
        request = createRequest(arrayListOf(testPackage3))
        allowedPackages = queryAllowlistPackages(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedPackages).containsExactly(testPackage3)
    }

    @Test
    fun testQueryAllowlist_withTestProvider_mergedPackageTargetsWithProvider() {
        runWithShellPermissionIdentity {
            allowlistManager.setTestProviderEnabled(true)
        }

        runShellCommand(
            "cmd allowlist add-package-multimap ${AllowlistManager.ALLOWLIST_ID_TEST} " +
                    "${testPackage1.toShellString()} $testTarget1,$testTarget2"
        )

        var request = createRequest(arrayListOf(testPackage1), arrayListOf())
        var allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap).containsKey(testPackage1)
        assertThat(allowedMap[testPackage1]?.map { it.packageName }).containsExactly(
            testTarget1,
            testTarget2
        )

        val testTarget3 = SignedPackage("test.target.3", null)

        request = createRequest(arrayListOf(testPackage1), arrayListOf(testTarget3))
        allowedMap = queryAllowlistPackageTargets(AllowlistManager.ALLOWLIST_ID_TEST, request)
        assertThat(allowedMap).containsKey(testPackage1)
        assertThat(allowedMap[testPackage1]?.map { it.packageName })
            .containsExactly(testTarget3.packageName)
    }

    private fun queryAllowlistPackages(
        allowlistId: Int,
        request: AllowlistRequest
    ): List<SignedPackage> {
        val latch = CountDownLatch(1)
        var response: AllowlistResponse? = null

        runWithShellPermissionIdentity {
            allowlistManager.queryAllowlist(request, context.mainExecutor) { resp ->
                response = resp
                latch.countDown()
            }
        }
        assertThat(latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(response).isNotNull()
        assertThat(response!!.status).isEqualTo(AllowlistManager.RESPONSE_STATUS_SUCCESS)
        return response.data.getParcelableArrayList(
            AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
            SignedPackage::class.java
        ) ?: emptyList()
    }

    private fun queryAllowlistPackageTargets(
        allowlistId: Int,
        request: AllowlistRequest
    ): Map<SignedPackage, List<SignedPackage>> {
        val latch = CountDownLatch(1)
        var response: AllowlistResponse? = null

        runWithShellPermissionIdentity {
            allowlistManager.queryAllowlist(request, context.mainExecutor) { resp ->
                response = resp
                latch.countDown()
            }
        }
        assertThat(latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(response).isNotNull()
        assertThat(response!!.status).isEqualTo(AllowlistManager.RESPONSE_STATUS_SUCCESS)
        return response.data.getParcelable(
            AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
            SignedPackageMultiMap::class.java
        )?.map ?: emptyMap()
    }

    private fun SignedPackage.toShellString(): String =
        if (hasCertificateDigest()) {
            "$packageName:${hexFormat.formatHex(certificateDigest)}"
        } else {
            packageName
        }

    private fun createRequest(
        packages: ArrayList<SignedPackage>? = null,
        targets: ArrayList<SignedPackage>? = null
    ): AllowlistRequest =
        AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, Bundle().apply {
            packages?.let {
                putParcelableArrayList(AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, it)
            }
            targets?.let {
                putParcelableArrayList(AllowlistManager.REQUEST_KEY_FILTER_TARGETS, it)
            }
        })

    companion object {
        const val LATCH_TIMEOUT_MS = 5000L
    }
}
