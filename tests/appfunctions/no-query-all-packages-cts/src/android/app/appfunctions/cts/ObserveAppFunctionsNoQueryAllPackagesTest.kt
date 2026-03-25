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
package android.app.appfunctions.cts

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.DynamicSchemaHelperApp
import android.app.appfunctions.cts.AppFunctionMetadataTestHelper.LegacySchemaHelperApp
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.cts.AppFunctionUtils.installPackage
import android.app.appfunctions.cts.AppFunctionUtils.uninstallPackage
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import org.junit.Assert.assertThrows
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
class ObserveAppFunctionsNoQueryAllPackagesTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() {
        manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
    }

    @Test
    fun basicTest() = doBlocking {
        // Replace this with the real tests.
        installPackage(
            DynamicSchemaHelperApp.ApkPaths.BASE_APP,
            DynamicSchemaHelperApp.PACKAGE_NAME,
            context,
            checkIndexation = false,
        )

        installPackage(
            LegacySchemaHelperApp.APK_PATH,
            LegacySchemaHelperApp.PACKAGE_NAME,
            context,
            checkIndexation = false,
        )
        try {
            val packageManager = context.packageManager
            val dynamicAppInfo =
                packageManager.getPackageInfo(DynamicSchemaHelperApp.PACKAGE_NAME, 0)
            assertThat(dynamicAppInfo).isNotNull()

            assertThrows(NameNotFoundException::class.java) {
                packageManager.getPackageInfo(LegacySchemaHelperApp.PACKAGE_NAME, 0)
            }
        } finally {
            uninstallPackage(DynamicSchemaHelperApp.PACKAGE_NAME)
            uninstallPackage(LegacySchemaHelperApp.PACKAGE_NAME)
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        private fun uninstallPackage(packageName: String) {
            SystemUtil.runShellCommand("pm uninstall $packageName")
        }
    }
}
