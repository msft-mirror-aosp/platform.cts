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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_32341 extends StsExtraBusinessLogicTestCase {
    private String mSharedUserId = null;

    @AsbSecurityTest(cveBugId = 295549202)
    @Test
    public void testPocCVE_2025_32341() {
        try {
            final String ctsShimPackageName = "com.android.cts.ctsshim";

            // Try to fetch the sharedUserId for CtsShim app
            runWithShellPermissionIdentity(
                    () -> {
                        mSharedUserId =
                                getApplicationContext()
                                        .getPackageManager()
                                        .getPackageInfo(ctsShimPackageName, 0 /* flags */)
                                        .sharedUserId;
                    },
                    "android.permission.QUERY_ALL_PACKAGES");

            // With fix, mSharedUserId will be null
            if (mSharedUserId == null) {
                return;
            }

            // Without fix, a mSharedUserId will exists for CtsShim app and the test fails
            assertWithMessage(
                            "Device is vulnerable to b/295549202, Normal apps can bypass system"
                                    + " permission check")
                    .that(mSharedUserId.contains(ctsShimPackageName))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
