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

package android.security.cts.CVE_2024_40655;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.MANAGE_ROLE_HOLDERS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;


import static com.android.bedstead.nene.TestApis.activities;
import static com.android.bedstead.nene.TestApis.permissions;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.ActivityManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.permissions.PermissionContext;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_40655 extends StsExtraBusinessLogicTestCase {
    final Semaphore mIsRoleGranted = new Semaphore(0);

    @AsbSecurityTest(cveBugId = 300904123)
    @Test
    public void testPocCVE_2024_40655() {
        try {
            final Context context = getApplicationContext();

            // Assign CALL_SCREENING role to the test package.
            addRoleHolder(RoleManager.ROLE_CALL_SCREENING, context.getPackageName(), context);

            // Check for call screening role.
            assume().withMessage("Failed to grant 'CALL_SCREENING' role!!")
                    .that(mIsRoleGranted.tryAcquire(10, TimeUnit.SECONDS))
                    .isTrue();

            // Make a phone call.
            try (PermissionContext permissionContext = permissions().withPermission(CALL_PHONE)) {
                activities()
                        .startActivity(
                                new Intent(Intent.ACTION_CALL)
                                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .setData(Uri.fromParts("tel", "123", null)));
            }

            // Wait for 'PocService' to start
            final String pocServiceName = PocService.class.getName();
            final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
            assume().withMessage("Failed to start 'PocService' !!")
                    .that(poll(() -> isServiceRunning(activityManager, pocServiceName)))
                    .isTrue();

            // Without fix, 'PocService' does not get killed within the timeout.
            // With fix, the service gets killed in ~1 seconds. Keeping timeout as 30 seconds to
            // support slow execution on some devices.
            assertWithMessage(
                            "Device is vulnerable to b/300904123 !! Services extending"
                                + " 'CallScreeningService' can retain 'while in use' permission in"
                                + " the background")
                    .that(poll(() -> !isServiceRunning(activityManager, pocServiceName)))
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean isServiceRunning(ActivityManager activityManager, String serviceName) {
        return activityManager.getRunningServices(Integer.MAX_VALUE).stream()
                .map(runningServiceInfo -> runningServiceInfo.service)
                .filter(service -> service != null)
                .anyMatch(service -> serviceName.equals(service.getClassName()));
    }

    private AutoCloseable addRoleHolder(String roleName, String packageName, Context context)
            throws InterruptedException {
        runWithShellPermissionIdentity(
                () -> {
                    context.getSystemService(RoleManager.class)
                            .addRoleHolderAsUser(
                                    roleName,
                                    packageName,
                                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                                    context.getUser(),
                                    context.getMainExecutor(),
                                    successful -> {
                                        if (successful) {
                                            mIsRoleGranted.release();
                                        }
                                    });
                },
                MANAGE_ROLE_HOLDERS);
        return () -> removeRoleHolder(roleName, packageName, context);
    }

    private void removeRoleHolder(String roleName, String packageName, Context context)
            throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    context.getSystemService(RoleManager.class)
                            .removeRoleHolderAsUser(
                                    roleName,
                                    packageName,
                                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                                    context.getUser(),
                                    context.getMainExecutor(),
                                    successful -> {});
                },
                MANAGE_ROLE_HOLDERS);
    }
}
