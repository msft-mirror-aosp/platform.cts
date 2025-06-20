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

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.provider.OpenableColumns.DISPLAY_NAME;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_26453 extends StsExtraBusinessLogicTestCase {
    int mUserId = -1;

    @Test
    @AsbSecurityTest(cveBugId = 395643490)
    public void testPocCVE_2025_26453() {
        try {
            final Context context = getApplicationContext();
            final PackageManager packageManager = context.getPackageManager();
            final UserManager userManager = context.getSystemService(UserManager.class);

            // Skip the test if the device does not support multiple users
            assume().withMessage("This device does not support multiple users")
                    .that(userManager.supportsMultipleUsers())
                    .isTrue();

            // Skip the test for non bluetooth devices
            assume().withMessage("The device does not support bluetooth.")
                    .that(packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
                    .isTrue();

            // Fetch bluetooth app's package name
            final String bluetoothPackageName =
                    packageManager.resolveService(
                                    new Intent("android.bluetooth.IBluetooth"),
                                    PackageManager.MATCH_SYSTEM_ONLY)
                            .serviceInfo
                            .packageName;

            // Fetch bluetooth app's context and get 'generateFileInfoMethod'
            final Context bluetoothContext =
                    context.createPackageContext(
                            bluetoothPackageName,
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);

            final Class<?> bluetoothOppSendFileInfoClass =
                    bluetoothContext
                            .getClassLoader()
                            .loadClass("com.android.bluetooth.opp.BluetoothOppSendFileInfo");

            final Method generateFileInfoMethod =
                    bluetoothOppSendFileInfoClass.getDeclaredMethod(
                            "generateFileInfo",
                            Context.class,
                            Uri.class,
                            String.class,
                            boolean.class);
            generateFileInfoMethod.setAccessible(true);

            // Create 'cve_2025_26453_user'
            // Invoke BluetoothOppSendFileInfo#generateFileInfo() and pass a content uri.
            // Without fix, the '_display_name' column is queried and a IllegalArgumentException
            // wrapped inside InvocationTargetException is thrown.
            // With fix, the uri is not queried due to checks on the uri's user information.
            try (AutoCloseable testUser =
                    withTestUserStarted(userManager, "cve_2025_26453_user", context)) {
                // Assumption fail if mUserId is -1
                assume().that(mUserId != -1).isTrue();

                // Invoke generateFileInfo()
                runWithShellPermissionIdentity(
                        () ->
                                generateFileInfoMethod.invoke(
                                        null /* Object*/,
                                        bluetoothContext,
                                        Uri.parse("content://" + mUserId + "%40settings/global"),
                                        "text/*",
                                        false /* fromExternal*/),
                        INTERACT_ACROSS_USERS_FULL);
            } catch (RuntimeException e) {
                // Get full stack trace
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));

                // Fail if "java.lang.IllegalArgumentException: Invalid column: _display_name"
                // exception is observed
                assertWithMessage(
                                "Device is vulnerable to b/395643490. Files can be revealed across"
                                        + " users via bluetooth")
                        .that(stringWriter.toString().contains(DISPLAY_NAME))
                        .isFalse();
                throw e;
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withTestUserStarted(
            UserManager userManager, String username, Context context) throws Exception {
        // Create 'cve_2025_26453_user'
        UserInfo userInfo =
                runWithShellPermissionIdentity(
                        () -> userManager.createUser(username, UserInfo.FLAG_GUEST), CREATE_USERS);
        assume().withMessage("Failed to create test user").that(userInfo).isNotNull();

        // Start 'cve_2025_26453_user' in the background
        mUserId = userInfo.id;
        runWithShellPermissionIdentity(
                () -> {
                    ActivityManager.getService().startUserInBackground(mUserId);
                    assume().withMessage(String.format("Failed to start user %d", mUserId))
                            .that(
                                    poll(
                                            () ->
                                                    context.getSystemService(ActivityManager.class)
                                                            .isUserRunning(mUserId)))
                            .isTrue();
                },
                INTERACT_ACROSS_USERS_FULL,
                INTERACT_ACROSS_USERS);

        // Remove 'cve_2025_26453_user'
        return () -> {
            if (userInfo != null) {
                runWithShellPermissionIdentity(
                        () -> userManager.removeUser(userInfo.id), CREATE_USERS);
            }
        };
    }
}
