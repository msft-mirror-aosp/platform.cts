/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_DISABLE_INSTALL_UNKNOWN_SOURCES)
public class DisallowInstallUnknownSourcesTest extends BaseAdvancedProtectionTest {
    private static final int TIMEOUT_S = 1;
    private static final String TEST_APP_PACKAGE =
            "android.security.cts.advancedprotection.testadvancedprotection";

    private Context mContext;
    private AppOpsManager mAppOpsManager;
    private UserManager mUserManager;

    @Override
    @Before
    public void setup() {
        super.setup();
        mContext = mInstrumentation.getContext();
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures",
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES"
    })
    @Test
    public void testGetFeatures() {
        assertEquals("The Disallow Install Unknown Sources feature is not in the feature list",
                1,
                mManager.getAdvancedProtectionFeatures()
                        .stream()
                        .filter(feature -> feature.getId().equals(
                                FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES))
                        .count());
    }

    // TODO(b/369361373): Replace sleep with a callback to ensure the restriction is set by the time
    //  we check it.
    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testEnableProtection() throws InterruptedException {
        mManager.setAdvancedProtectionEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);
        assertTrue("The DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testDisableProtection() throws InterruptedException {
        mManager.setAdvancedProtectionEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);
        assertFalse("The DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testStateAfterEnableAndDisableProtection_opRequestInstallPackagesIsModeErrored()
            throws InterruptedException {
        // 1. Set TestApp's OP_REQUEST_INSTALL_PACKAGES to MODE_ALLOWED.
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_APP_OPS_MODES);
        final int testAppUid;
        try {
            testAppUid = mInstrumentation.getContext().getPackageManager()
                    .getPackageUidAsUser(TEST_APP_PACKAGE, UserHandle.myUserId());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Couldn't retrieve uid for test package: " + e);
        }
        mAppOpsManager.setMode(AppOpsManager.OP_REQUEST_INSTALL_PACKAGES, testAppUid,
                TEST_APP_PACKAGE, AppOpsManager.MODE_ALLOWED);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        // 2. Assert the mode was set.
        assertEquals("Test App's OP_REQUEST_INSTALL_PACKAGES is not set to MODE_ALLOWED",
                AppOpsManager.MODE_ALLOWED, mAppOpsManager.checkOpNoThrow(
                        AppOpsManager.OP_REQUEST_INSTALL_PACKAGES, testAppUid, TEST_APP_PACKAGE));

        // 3. Enable advanced protection.
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mManager.setAdvancedProtectionEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);

        // 4. Disable advanced protection.
        mManager.setAdvancedProtectionEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        // 5. Assert TestApp's mode is MODE_ERRORED.
        assertEquals("Test App's OP_REQUEST_INSTALL_PACKAGES is not set to MODE_ERRORED",
                AppOpsManager.MODE_ERRORED, mAppOpsManager.checkOpNoThrow(
                        AppOpsManager.OP_REQUEST_INSTALL_PACKAGES, testAppUid, TEST_APP_PACKAGE));
    }
}
