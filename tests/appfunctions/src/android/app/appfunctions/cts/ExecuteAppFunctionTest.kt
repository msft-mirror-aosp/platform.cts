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

import android.Manifest
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.AppFunctionMetadataTestHelper.MultiServicesHelperApp
import android.app.appfunctions.testutils.AppFunctionUtils.executeAppFunction
import android.app.appfunctions.testutils.AppFunctionUtils.installPackage
import android.app.appfunctions.testutils.AppFunctionUtils.uninstallPackage
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appsearch.GenericDocument
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(BedsteadJUnit4::class)
class ExecuteAppFunctionTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var manager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        this@ExecuteAppFunctionTest.manager = manager
    }

    @After
    fun teardown() = doBlocking {
        uninstallPackage(MultiServicesHelperApp.PACKAGE_NAME, context, true)
    }

    @Test
    @EnsureHasPermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
    )
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MULTI_SERVICE_BUGFIX)
    fun executeAppFunction_multipleServices_invokeCorrectService() = doBlocking {
        installPackage(
            MultiServicesHelperApp.APK_PATH,
            MultiServicesHelperApp.PACKAGE_NAME,
            context,
            true,
        )

        run {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        MultiServicesHelperApp.PACKAGE_NAME,
                        MultiServicesHelperApp.Service1.FunctionNames.ADD.functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = manager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(response.getOrNull()!!.resultDocument.getPropertyLong(PROPERTY_RETURN_VALUE))
                .isEqualTo(3)
        }

        run {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("message", "hello")
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(
                        MultiServicesHelperApp.PACKAGE_NAME,
                        MultiServicesHelperApp.Service2.FunctionNames.ECHO.functionIdentifier,
                    )
                    .setParameters(parameters)
                    .build()

            val response = manager.executeAppFunction(request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response.getOrNull()!!.resultDocument.getPropertyString(PROPERTY_RETURN_VALUE)
                )
                .isEqualTo("hello")
        }
    }

    @Test
    @EnsureHasPermission(
        Manifest.permission.QUERY_ALL_PACKAGES,
        Manifest.permission.EXECUTE_APP_FUNCTIONS,
        Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE,
    )
    fun executeAppFunction_multipleServices_cannotInvokeDisabledServiceFunction() = doBlocking {
        installPackage(
            MultiServicesHelperApp.APK_PATH,
            MultiServicesHelperApp.PACKAGE_NAME,
            context,
            checkIndexation = true,
        )
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString("message", "hello")
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(
                    MultiServicesHelperApp.PACKAGE_NAME,
                    MultiServicesHelperApp.Service2.FunctionNames.ECHO.functionIdentifier,
                )
                .setParameters(parameters)
                .build()

        // Disable service 2.
        context
            .getPackageManager()
            .setComponentEnabledSetting(
                ComponentName(
                    MultiServicesHelperApp.PACKAGE_NAME,
                    MultiServicesHelperApp.Service2.TEST_SERVICE_NAME,
                ),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                /* flags= */ 0,
            )

        retryAssert {
            // Retry till the AppFunction indexer has completed a run.
            val response = manager.executeAppFunction(request)
            assertThat(response.isSuccess).isFalse()
            val exception = assertIs<AppFunctionException>(response.exceptionOrNull())
            assertThat(exception.errorCode).isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
        }
    }

    companion object {
        private fun doBlocking(block: suspend CoroutineScope.() -> Unit) =
            runBlocking(block = block)
    }
}
