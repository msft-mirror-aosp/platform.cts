/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.nene.notifications.NotificationListenerQuerySubject.assertThat;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.flags.Flags;
import android.app.time.TimeConfiguration;
import android.app.time.TimeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.UserHandle;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.policies.MaximumTimeOff;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.NotificationsTest;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.notifications.NotificationListener;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class MaximumTimeOffTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = testApps(sDeviceState).query()
            .whereActivities().contains(
                    activity().where().exported().isTrue()
            ).get();

    @PolicyAppliesTest(policy = MaximumTimeOff.class)
    @NotificationsTest
    public void setManagedProfileMaximumTimeOff_timesOut_personalAppsAreSuspended()
            throws Exception {
        long originalMaximumTimeOff =
                dpc(sDeviceState).devicePolicyManager()
                        .getManagedProfileMaximumTimeOff(
                                dpc(sDeviceState).componentName());
        try (TestAppInstance personalInstance = sTestApp.install()) {
            TestAppActivityReference activity = personalInstance.activities().any();
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ 1);
            workProfile(sDeviceState).setQuietMode(true);

            assertPackageSuspended(sTestApp.pkg());

            startActivityWithoutBlocking(activity);
            assertBlockedByAdminDialogAppears();
        } finally {
            workProfile(sDeviceState).setQuietMode(false);
            dpc(sDeviceState)
                    .devicePolicyManager()
                    .setPersonalAppsSuspended(dpc(sDeviceState).componentName(), false);
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ originalMaximumTimeOff);
        }
    }

    @Test
    @PolicyAppliesTest(policy = MaximumTimeOff.class)
    @NotificationsTest
    @Postsubmit(reason = "New test")
    @RequireFlagsEnabled(Flags.FLAG_CHECK_PERSONAL_SUSPENSION_FOR_ALL_PROFILES)
    public void setManagedProfileMaximumTimeOff_timeAdjusted_personalAppsAreSuspended()
            throws Exception {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();
        boolean originalAutoTimeEnabled = dpm.getAutoTimeEnabled(dpc(sDeviceState).componentName());
        long originalTime = 0;
        try (TestAppInstance personalInstance = sTestApp.install()) {
            setAutoTimeEnabled(false);

            TestAppActivityReference activity = personalInstance.activities().any();

            dpm.setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ 1800_000);
            setQuietModeAndWaitForUserStopped(sDeviceState);

            originalTime = System.currentTimeMillis();
            setTime(originalTime + 3600_000);

            assertPackageSuspended(sTestApp.pkg());

            startActivityWithoutBlocking(activity);
            assertBlockedByAdminDialogAppears();
        } finally {
            // Do this first to make sure to restore time first. The other methods can throw
            // exceptions.
            if (originalTime != 0) {
                setTime(originalTime);
            }
            setAutoTimeEnabled(originalAutoTimeEnabled);
            workProfile(sDeviceState).setQuietMode(false);
            dpm.setPersonalAppsSuspended(dpc(sDeviceState).componentName(), false);
        }
    }

    private void setTime(long epochMillis) {
        try (PermissionContext p =
                TestApis.permissions().withPermission("android.permission.SET_TIME")) {
            SystemClock.setCurrentTimeMillis(epochMillis);
        }
    }

    private void setAutoTimeEnabled(boolean enabled) {
        try (PermissionContext p =
                TestApis.permissions()
                        .withPermission("android.permission.MANAGE_TIME_AND_ZONE_DETECTION")) {
            TimeManager timeManager =
                    TestApis.context().instrumentedContext().getSystemService(TimeManager.class);
            timeManager.updateTimeConfiguration(
                    new TimeConfiguration.Builder().setAutoDetectionEnabled(enabled).build());
        }
    }

    private void startActivityWithoutBlocking(TestAppActivityReference activity) {
        Intent intent = new Intent();
        intent.setComponent(activity.component().componentName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

        TestApis.context().instrumentedContext().startActivity(intent);
    }

    @PolicyAppliesTest(policy = MaximumTimeOff.class)
    @NotificationsTest
    public void setManagedProfileMaximumTimeOff_timesOut_notificationIsShown() {
        long originalMaximumTimeOff =
                dpc(sDeviceState).devicePolicyManager()
                        .getManagedProfileMaximumTimeOff(
                                dpc(sDeviceState).componentName());
        try (NotificationListener notifications = TestApis.notifications().createListener()) {
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ 1);

            workProfile(sDeviceState).setQuietMode(true);

            assertThat(
                    notifications.query()
                            .wherePackageName().isEqualTo("android")
                            .whereNotification().channelId().isEqualTo("DEVICE_ADMIN_ALERTS")
            ).wasPosted();
        } finally {
            workProfile(sDeviceState).setQuietMode(false);
            dpc(sDeviceState)
                    .devicePolicyManager()
                    .setPersonalAppsSuspended(dpc(sDeviceState).componentName(), false);
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ originalMaximumTimeOff);
        }
    }

    @CannotSetPolicyTest(policy = MaximumTimeOff.class, includeNonDeviceAdminStates = false)
    public void setManagedProfileMaximumTimeOff_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ 1);
        });
    }

    @CanSetPolicyTest(policy = MaximumTimeOff.class)
    public void getManagedProfileMaximumTimeOff_returnsSetValue() {
        long originalMaximumTimeOff =
                dpc(sDeviceState).devicePolicyManager()
                        .getManagedProfileMaximumTimeOff(
                                dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ 12345);

            assertThat(dpc(sDeviceState).devicePolicyManager().getManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName())).isEqualTo(12345);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setManagedProfileMaximumTimeOff(
                    dpc(sDeviceState).componentName(), /* timeoutMs= */ originalMaximumTimeOff);
        }
    }

    // TODO(264249662): Add missing coverage

    private static final String BLOCKED_BY_ADMIN_DIALOG_CLASSNAME =
            "com.android.settings.enterprise.ActionDisabledByAdminDialog";

    private void assertBlockedByAdminDialogAppears() {
        // TODO: We should move this into the enterprise/bedstead infra
        Poll.forValue("foreground activity", () -> TestApis.activities().foregroundActivity())
                .toMeet((v) -> v.className().equals(BLOCKED_BY_ADMIN_DIALOG_CLASSNAME))
                .errorOnFail()
                .await();
    }

    private static void assertPackageSuspended(Package pkg) {
        Poll.forValue("package suspended", () -> pkg.isSuspended(TestApis.users().instrumented()))
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    private static void setQuietModeAndWaitForUserStopped(DeviceState deviceState)
            throws InterruptedException {
        int workProfileUser = workProfile(deviceState).id();
        CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (Intent.ACTION_USER_STOPPED.equals(intent.getAction())) {
                            int user = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                            if (user == workProfileUser) {
                                latch.countDown();
                            }
                        }
                    }
                };
        TestApis.context()
                .instrumentedContext()
                .registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_STOPPED));
        try {
            workProfile(deviceState).setQuietMode(true);
            boolean success = latch.await(30, TimeUnit.SECONDS);
            assertWithMessage("Work profile did not enter quiet mode in 30 seconds")
                    .that(success)
                    .isTrue();
        } finally {
            TestApis.context().instrumentedContext().unregisterReceiver(receiver);
        }
    }
}
