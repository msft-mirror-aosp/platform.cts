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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.IPackageManagerNative;
import android.content.pm.PackageInfoNative;
import android.os.Process;
import android.os.ServiceManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PackageManagerNativeTest {
    private Context mContext;
    private IPackageManagerNative mPackageManagerNative;
    private String mPackageName;
    private int mUserId;

    @Before
    public void setUp() throws Exception {
        mPackageManagerNative =
                IPackageManagerNative.Stub.asInterface(ServiceManager.getService("package_native"));
        assertNotNull("Could not get IPackageManagerNative service", mPackageManagerNative);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull("Context is null", mContext);
        mPackageName = mContext.getPackageName();
        assertNotNull("Package name is null", mPackageName);
        mUserId = UserHandle.myUserId();
    }

    @Test
    public void testGetPackageInfoWithSigningInfo() throws Exception {
        PackageInfoNative packageInfo =
                mPackageManagerNative.getPackageInfoWithSigningInfo(mPackageName, mUserId);

        assertNotNull("getPackageInfo returned null", packageInfo);
        assertEquals("Package name mismatch", mPackageName, packageInfo.packageName);
        assertNotNull("Signing info is null", packageInfo.signingInfo);
        assertNotNull("apkContentSigners is null", packageInfo.signingInfo.apkContentSigners);
        assertEquals("One signer expected", 1, packageInfo.signingInfo.apkContentSigners.length);
        assertNotNull("Signature is null", packageInfo.signingInfo.apkContentSigners[0].signature);
        assertTrue(
                "Signature is empty",
                packageInfo.signingInfo.apkContentSigners[0].signature.length > 0);
        // We don't try to validate the signing key for CTS because it may not be fixed.
    }

    @Test
    public void testGetPackageInfoWithSigningInfoForUid() throws Exception {
        int uid = Process.myUid();
        String currentPackageName = mContext.getPackageName();

        PackageInfoNative[] packageInfos =
                mPackageManagerNative.getPackageInfoWithSigningInfoForUid(uid);

        assertNotNull("getPackageSigningInfoWithSigningInfoForUid returned null", packageInfos);
        assertTrue("No package info returned for UID " + uid, packageInfos.length > 0);

        boolean foundCurrentPackage = false;
        for (PackageInfoNative packageInfo : packageInfos) {
            assertNotNull("PackageInfoNative in array is null", packageInfo);
            assertNotNull("Package name is null for UID " + uid, packageInfo.packageName);

            assertNotNull(
                    "Signing info is null for package: " + packageInfo.packageName,
                    packageInfo.signingInfo);
            assertNotNull(
                    "apkContentSigners is null for package: " + packageInfo.packageName,
                    packageInfo.signingInfo.apkContentSigners);
            assertTrue(
                    "No signers found for package: " + packageInfo.packageName,
                    packageInfo.signingInfo.apkContentSigners.length > 0);
            assertNotNull(
                    "Signature is null for package: " + packageInfo.packageName,
                    packageInfo.signingInfo.apkContentSigners[0].signature);
            assertTrue(
                    "Signature is empty for package: " + packageInfo.packageName,
                    packageInfo.signingInfo.apkContentSigners[0].signature.length > 0);

            if (currentPackageName.equals(packageInfo.packageName)) {
                foundCurrentPackage = true;
            }
        }

        assertTrue(
                "Current package (" + currentPackageName + ") not found in results for UID " + uid,
                foundCurrentPackage);
    }
}
