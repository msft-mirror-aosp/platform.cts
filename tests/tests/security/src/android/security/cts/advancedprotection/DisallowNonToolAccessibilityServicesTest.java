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

package android.security.cts.advancedprotection;

import static android.os.UserManager.DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;

@RequiresFlagsEnabled(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
public class DisallowNonToolAccessibilityServicesTest extends BaseAdvancedProtectionFeatureTest {
    private UserManager mUserManager;

    @Override
    @Before
    public void setup() {
        super.setup();

        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    @Override
    protected int getFeatureId() {
        return FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES;
    }

    @Override
    protected String getFeatureName() {
        return "restrict non tool a11y services";
    }

    @Override
    protected boolean isSupportedOnDevice() {
        return true;
    }

    @Override
    protected void assertFeatureEnabled() {
        try {
            PollingCheck.waitFor(
                    1000,
                    () -> mUserManager.hasUserRestriction(DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE));
        } catch (AssertionError ignored) {
        }
        assertTrue(
                "The DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE));
    }

    @Override
    protected void assertFeatureDisabled() {
        try {
            PollingCheck.waitFor(
                    1000,
                    () ->
                            !mUserManager.hasUserRestriction(
                                    DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE));
        } catch (AssertionError ignored) {
        }
        assertFalse(
                "The DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE));
    }
}
