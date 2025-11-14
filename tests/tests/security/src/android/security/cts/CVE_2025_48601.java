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

import static android.Manifest.permission.STATUS_BAR_SERVICE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ParceledListSlice;
import android.os.Process;
import android.os.ServiceManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48601 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 426207912)
    public void testPocCVE_2025_48601() {
        try {
            final INotificationManager notificationManager =
                    INotificationManager.Stub.asInterface(
                            ServiceManager.getService(Context.NOTIFICATION_SERVICE));

            // Create notification channel for non-existing package.
            final String notificationChannelId = "cve_2025_48601_id";
            final String notificationChannelName = "cve_2025_48601_name";
            final String missingPkgName = "cve_2025_48601_missing_package_name";
            final NotificationChannel notificationChannel =
                    new NotificationChannel(
                            notificationChannelId,
                            notificationChannelName,
                            NotificationManager.IMPORTANCE_DEFAULT);

            // Invoke the vulnerable method.
            final CompletableFuture<Boolean> isChannelCreated = new CompletableFuture<Boolean>();
            runWithShellPermissionIdentity(
                    () -> {
                        // Invoke the vulnerable method. Without fix, channel is created for
                        // non-existing package and invalid uid. With fix,
                        // 'IllegalArgumentException' exception is thrown with message "Valid uid
                        // required to get settings of".
                        try {
                            notificationManager.createNotificationChannelsForPackage(
                                    missingPkgName,
                                    Process.INVALID_UID,
                                    new ParceledListSlice<NotificationChannel>(
                                            List.of(notificationChannel)));
                        } catch (Exception e) {
                            // Ignore the exception.
                            Log.e("cve_2025_48601", "Exception occurred : " + e.getMessage());
                        }

                        // Check if channel is created for non-existing package and invalid uid.
                        final ParceledListSlice<NotificationChannel> parceledListSlice =
                                notificationManager.getNotificationChannelsForPackage(
                                        missingPkgName,
                                        Process.INVALID_UID,
                                        false /* includeDeleted */);
                        if (parceledListSlice.getList().size() != 0) {
                            for (NotificationChannel channel : parceledListSlice.getList()) {
                                if (notificationChannelId.equals(channel.getId())) {
                                    isChannelCreated.complete(true);
                                    break;
                                }
                            }
                        }
                        isChannelCreated.complete(false);
                    },
                    STATUS_BAR_SERVICE);

            // Without fix, Notification channel is created for non-existing
            // package.
            final Boolean isVulnerable =
                    isChannelCreated.get(5_000 /*timeout*/, TimeUnit.MILLISECONDS);
            assertWithMessage(
                            "Device is vulnerable b/426207912 !!, Channel created"
                                    + " for missing package")
                    .that(isVulnerable)
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
