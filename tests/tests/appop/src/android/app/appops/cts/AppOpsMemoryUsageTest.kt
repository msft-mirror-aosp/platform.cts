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

package android.app.appops.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.content.Context
import android.content.Intent
import android.os.Process
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOpsMemoryUsageTest {
    val context: Context = InstrumentationRegistry.getInstrumentation().context!!
    val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    val attributionTag = "cts_test_tag"
    val proxiedPkg = "com.android.shell"
    val api29Pkg = "android.app.appops.cts.api29appops"
    val sdkSandboxPkg = "android.app.appops.cts.appopssdksandboxloader"
    val rootPkg = "root"
    val proxiedUid = Process.SHELL_UID
    val op = AppOpsManager.OPSTR_VIBRATE
    val intentExtra = "extra_op"

    // Proxy apps could use the attribution tag validation exemption for certain system apps to
    // overwhelm the app ops system memory
    @Test
    @AsbSecurityTest(cveBugId = [375623125])
    fun testCannotApplyArbitraryAttributionWithProxyOp() {
        assertAttrTagOverwrittenToNull(proxiedUid, proxiedPkg)
        // Test twice, to verify the call to finishOp doesn't incorrectly mark the op as verified
        assertAttrTagOverwrittenToNull(proxiedUid, proxiedPkg)
    }

    // Proxy apps could use the attribution tag validation exemption for root uid to
    // overwhelm the app ops system memory
    @Test
    @AsbSecurityTest(cveBugId = [416491779])
    fun testCannotApplyArbitraryAttributionWithProxyOpForRoot() {
        assertAttrTagOverwrittenToNull(Process.ROOT_UID, rootPkg)
    }

    private fun assertAttrTagOverwrittenToNull(proxiedUid: Int, proxiedPkg: String) {
        val result = appOpsManager.startProxyOpNoThrow(
            op,
            proxiedUid,
            proxiedPkg,
            attributionTag,
            null
        )
        assertEquals("expected to be able to start a VIBRATE op", MODE_ALLOWED, result)
        val activeOps = callWithShellPermissionIdentity {
            appOpsManager.getOpsForPackage(
                proxiedUid,
                proxiedPkg,
                op
            )
        }
        var foundNullAttrOp = false
        for (pkgOp in activeOps) {
            for (opEntry in pkgOp.ops) {
                if (opEntry.opStr != op) {
                    continue
                }
                for ((tag, attrOp) in opEntry.attributedOpEntries) {
                    if (!attrOp.isRunning) {
                        continue
                    }
                    assertNotEquals("found $attributionTag running op", attributionTag, tag)
                    if (tag == null) {
                        foundNullAttrOp = true
                        // We found a null op, but don't break out of the loop yet. We need to
                        // verify that "attributionTag" is not found
                    }
                }
            }
        }
        assertTrue("expected to find a running op with the null tag", foundNullAttrOp)
        finishOps()
    }

    @Test
    @AsbSecurityTest(cveBugId = [417987184])
    fun testApi29AppCannotSupplyArbitraryAttribution() {
        val notedLatch = CountDownLatch(1)
        var tag: String? = null
        runWithShellPermissionIdentity {
            appOpsManager.startWatchingNoted(arrayOf(op)) { _, _, packageName, attrTag, _, _ ->
                if (packageName == api29Pkg) {
                    tag = attrTag
                    notedLatch.countDown()
                }
            }
        }
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setPackage(api29Pkg)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(intentExtra, op)
        )
        notedLatch.await()
        assertNull("Expected a null attribution tag", tag)
    }

    @Test
    @AsbSecurityTest(cveBugId = [443742082])
    @Ignore("b/444627795 - SDKRuntime is disabled through flag and moving to no-op implementation & deprecation.")
    fun testSdkSandboxAppCannotSupplyArbitraryAttribution() {
        val notedLatch = CountDownLatch(1)
        var tag: String? = null
        runWithShellPermissionIdentity {
            appOpsManager.startWatchingNoted(arrayOf(op)) { _, uid, _, attrTag, _, _ ->
                if (uid == Process.ROOT_UID) {
                    tag = attrTag
                    notedLatch.countDown()
                }
            }
        }
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setPackage(sdkSandboxPkg)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(intentExtra, op)
        )
        assertTrue("Timed out waiting for op to be noted", notedLatch.await(10, TimeUnit.SECONDS))
        assertNull(tag)
    }

    @After
    fun tearDown() {
        finishOps()
    }

    private fun finishOps() {
        appOpsManager.finishProxyOp(
            op,
            proxiedUid,
            proxiedPkg,
            attributionTag
        )
        appOpsManager.finishProxyOp(
            op,
            proxiedUid,
            proxiedPkg,
            null
        )
    }
}
