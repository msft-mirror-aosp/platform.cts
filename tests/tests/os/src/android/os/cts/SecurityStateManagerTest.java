/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.os.cts;

import static android.os.SecurityStateManager.KEY_KERNEL_VERSION;
import static android.os.SecurityStateManager.KEY_SYSTEM_SPL;
import static android.os.SecurityStateManager.KEY_SYSTEM_SUPPLEMENTAL_PATCHES;
import static android.os.SecurityStateManager.KEY_VENDOR_SPL;
import static android.os.SecurityStateManager.KEY_VENDOR_SUPPLEMENTAL_PATCHES;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Flags;
import android.os.SecurityStateManager;
import android.os.SystemProperties;
import android.os.VintfRuntimeInfo;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.patches.SecurityPatches;
import android.security.patches.XmlParser;
import android.util.Log;
import android.webkit.WebViewUpdateService;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RequiresFlagsEnabled(Flags.FLAG_SECURITY_STATE_SERVICE)
public class SecurityStateManagerTest {

    private static final String TAG = "SecurityStateManagerTest";
    private static final String SYSTEM_SUPPLEMENTAL_PATCH_CONFIG_FILE =
            "/system/etc/security/supplemental_security_patches.xml";
    private static final String VENDOR_SUPPLEMENTAL_PATCH_CONFIG_FILE =
            "/vendor/etc/security/supplemental_security_patches.xml";

    private Context mContext;
    private Resources mResources;
    private PackageManager mPackageManager;
    private SecurityStateManager mSecurityStateManager;
    private PermissionContext mPermissionContext;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = getApplicationContext();
        mResources = mContext.getResources();
        mPackageManager = mContext.getPackageManager();
        mSecurityStateManager = mContext.getSystemService(SecurityStateManager.class);
        mPermissionContext = TestApis.permissions().withPermission(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @After
    public void tearDown() {
        if (mPermissionContext != null) {
            mPermissionContext.close();
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot restore binder identity")
    public void testGetGlobalSecurityState() throws Exception {
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)(.*)");
        Matcher matcher = pattern.matcher(VintfRuntimeInfo.getKernelVersion());
        String kernelVersion = "";
        if (matcher.matches()) {
            kernelVersion = matcher.group(1);
        }
        String defaultModuleMetadata = mContext.getString(
                mResources.getIdentifier("config_defaultModuleMetadataProvider",
                        "string", "android"));
        List<String> webViewPackages = Arrays.stream(WebViewUpdateService.getAllWebViewPackages())
                .map(info -> info.packageName).toList();
        List<String> securityStatePackages = Arrays.stream(mContext.getResources().getStringArray(
                mResources.getIdentifier("config_securityStatePackages",
                        "array", "android"))).toList();
        Bundle bundle = mSecurityStateManager.getGlobalSecurityState();

        assertEquals(bundle.getString(KEY_SYSTEM_SPL), Build.VERSION.SECURITY_PATCH);
        assertEquals(bundle.getString(KEY_VENDOR_SPL),
                SystemProperties.get("ro.vendor.build.security_patch", ""));
        assertEquals(bundle.getString(KEY_KERNEL_VERSION), kernelVersion);
        packageVersionNameCheck(bundle, defaultModuleMetadata);
        webViewPackages.forEach(p -> packageVersionNameCheck(bundle, p));
        securityStatePackages.forEach(p -> packageVersionNameCheck(bundle, p));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPLEMENTAL_SECURITY_PATCHES)
    @AppModeFull(reason = "Instant apps cannot restore binder identity")
    public void testGetGlobalSecurityState_checkCveIdsFormat() throws Exception {
        String [] expectedSystemCveIds = getExpectedCveIds(SYSTEM_SUPPLEMENTAL_PATCH_CONFIG_FILE);
        String [] expectedVendorCveIds = getExpectedCveIds(VENDOR_SUPPLEMENTAL_PATCH_CONFIG_FILE);

        Bundle bundle = mSecurityStateManager.getGlobalSecurityState();

        String[] actualSystemCveIds = bundle.getStringArray(KEY_SYSTEM_SUPPLEMENTAL_PATCHES);
        Arrays.sort(expectedSystemCveIds);
        Arrays.sort(actualSystemCveIds);

        String[] actualVendorCveIds = bundle.getStringArray(KEY_VENDOR_SUPPLEMENTAL_PATCHES);
        Arrays.sort(expectedVendorCveIds);
        Arrays.sort(actualVendorCveIds);

        assertArrayEquals(expectedSystemCveIds, actualSystemCveIds);
        assertArrayEquals(expectedVendorCveIds, actualVendorCveIds);
    }

    private String[] getExpectedCveIds(String configFilePath) {
        File file = new File(configFilePath);
        // skip the test if supplemental_security_patches.xml is not present.
        assumeTrue(file.exists());

        try (InputStream in = new FileInputStream(file)) {
            try {
                SecurityPatches securityPatches = XmlParser.read(in);

                return securityPatches.getPatch().stream().map(SecurityPatches.Patch::getId)
                        .collect(Collectors.toList()).toArray(new String[0]);
            } catch (Exception e) {
                Log.w(TAG, "Error parsing security patches configuration.", e);
            }
        } catch (IOException e) {
            Log.w(TAG, "Error opening security patches configuration file.", e);
        }

        return new String[0];
    }

    private void packageVersionNameCheck(Bundle bundle, String packageName) {
        if (bundle.containsKey(packageName)) {
            try {
                assertEquals(bundle.getString(packageName),
                        mPackageManager.getPackageInfo(packageName, 0 /* flags */).versionName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Error getting package info for " + packageName + ": " + e);
            }
        }
    }
}
