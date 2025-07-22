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

package android.security.cts.CVE_2025_0083;

import static android.os.Process.myUid;
import static android.os.UserHandle.getUserHandleForUid;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_0083 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 376259166)
    public void testPocCVE_2025_0083() {
        try {
            // Check if the device supports multiple users or not
            final Context context = getApplicationContext();
            final UserManager userManager = context.getSystemService(UserManager.class);
            assume().withMessage("This device does not support multiple users")
                    .that(userManager.supportsMultipleUsers())
                    .isTrue();

            final long notCurrentUserId =
                    context.getSystemService(UserManager.class)
                                    .getSerialNumberForUser(getUserHandleForUid(myUid()))
                            + 1;
            final String uriString =
                    "content://" + notCurrentUserId + "%" + Integer.toHexString('@');
            Icon targetImageIcon = Icon.createWithContentUri(uriString);

            // Creating a phoneAccountHandle for the phone account
            final TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
            PhoneAccountHandle phoneAccountHandle =
                    new PhoneAccountHandle(
                            new ComponentName(context, PocVoipService.class), /* componentName */
                            "cve_2025_0083_id" /* id */);

            // Register phone account
            // No account will be registered with fix
            try {
                telecomManager.registerPhoneAccount(
                        new PhoneAccount.Builder(
                                        phoneAccountHandle, "cve_2025_0083_label" /* label */)
                                .setIcon(targetImageIcon)
                                .build());
            } catch (Exception e) {
                // Ignore the exception thrown from validateAccountIconUserBoundary() with fix
                if (!e.getMessage()
                        .equals(
                                "Attempting to register a phone account with an image icon"
                                        + " belonging to another user.")) {
                    throw e;
                }
            }

            // Fail the test if phone account got registered and its icon is secondary user's Image
            final PhoneAccount[] phoneAccount = {null};
            runWithShellPermissionIdentity(
                    () -> {
                        phoneAccount[0] = telecomManager.getPhoneAccount(phoneAccountHandle);
                    },
                    android.Manifest.permission.READ_PHONE_NUMBERS);
            if (phoneAccount[0] != null) {
                assertWithMessage(
                                "Device is vulnerable to b/376259166 hence image can be revealed"
                                        + " across users")
                        .that(targetImageIcon.getUriString())
                        .isNotEqualTo(phoneAccount[0].getIcon().getUriString());
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
