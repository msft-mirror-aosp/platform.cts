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

package android.accessibilityservice.cts

import android.Manifest.permission.MANAGE_ACCESSIBILITY
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule
import android.accessibility.cts.common.InstrumentedAccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.Flags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for APIs with unique behavior for services that the deice has declared as trusted.
 */
@RunWith(AndroidJUnit4::class)
@Presubmit
@SuppressLint("MissingPermission")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRUSTED_ACCESSIBILITY_SERVICE_API)
class TrustedAccessibilityServiceTest {

    private val mContext: Context = InstrumentationRegistry.getInstrumentation().context
    private val mAccessibilityManager: AccessibilityManager =
        mContext.getSystemService(AccessibilityManager::class.java)

    @get:Rule
    val mDumpOnFailureRule = AccessibilityDumpOnFailureRule()

    @get:Rule
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val mEnabledAccessibilityServicesSettingRule = SettingsStateChangerRule(
        mContext,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        null
    )

    @After
    fun tearDown() {
        setTrustedAccessibilityServiceForTesting(null)
    }

    @Test
    fun enableTrustedAccessibilityService_serviceTrusted_samePackage_startsService() {
        val componentName = ComponentName(mContext, InstrumentedAccessibilityService::class.java)
        setTrustedAccessibilityServiceForTesting(componentName)

        val result = mAccessibilityManager.enableTrustedAccessibilityService(componentName)

        assertThat(result).isTrue()
        PollingCheck.waitFor { getEnabledAccessibilityService() == componentName }
    }

    @Test
    fun enableTrustedAccessibilityService_serviceNotTrusted_doesNotStartService() {
        val componentName = ComponentName(mContext, InstrumentedAccessibilityService::class.java)
        setTrustedAccessibilityServiceForTesting(null)

        val result = mAccessibilityManager.enableTrustedAccessibilityService(componentName)

        assertThat(result).isFalse()
        assertThat(getEnabledAccessibilityService()).isNull()
    }

    @Test
    fun enableTrustedAccessibilityService_serviceTrusted_differentPackage_doesNotStartService() {
        val componentName =
            ComponentName("foo.bar.multipleservices", "foo.bar.multipleservices.StubService1")
        setTrustedAccessibilityServiceForTesting(componentName)

        val result = mAccessibilityManager.enableTrustedAccessibilityService(componentName)

        assertThat(result).isFalse()
        assertThat(getEnabledAccessibilityService()).isNull()
    }

    private fun getEnabledAccessibilityService(): ComponentName? {
        val enabledServices = Settings.Secure.getString(
            mContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServices != null) {
            return ComponentName.unflattenFromString(enabledServices)
        }
        return null
    }

    private fun setTrustedAccessibilityServiceForTesting(componentName: ComponentName?) {
        TestApis.permissions().withPermission(MANAGE_ACCESSIBILITY).use {
            mAccessibilityManager.setTrustedAccessibilityServiceForTesting(componentName)
        }
    }
}
