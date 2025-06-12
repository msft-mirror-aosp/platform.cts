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

package android.jobscheduler.cts;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.ApplicationInfo;
import android.jobscheduler.cts.UserInitiatedJobTest.WatchUidRunner;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * Tests related to attaching notifications to jobs via {@link
 * JobService#setNotification(JobParameters, int, Notification, int)}
 */
@RunWith(AndroidJUnit4.class)
public final class NotificationTest extends BaseJobSchedulerTest {
    private static final String TAG = NotificationTest.class.getSimpleName();
    private static final int JOB_ID = NotificationTest.class.hashCode();
    private static final int TEST_NOTIFICATION_ID = 123;
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final String NOTIFICATION_CHANNEL_ID =
            NotificationTest.class.getSimpleName() + "_channel";

    private NotificationManager mNotificationManager;
    private NetworkingHelper mNetworkingHelper;
    private UserHelper mUserHelper;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mNotificationManager = getContext().getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NotificationTest.class.getSimpleName(), NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());
        mUserHelper = new UserHelper(getContext());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        mNotificationManager.cancelAll();
        mNetworkingHelper.tearDown();

        // The super method should be called at the end.
        super.tearDown();
    }

    @Test
    public void testNotificationJobEndDetach() throws Exception {
        mNotificationManager.cancelAll();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(
                TEST_NOTIFICATION_ID, notification, JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
        mJobScheduler.schedule(jobInfo);

        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        waitUntil(
                "Notification wasn't posted",
                15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == TEST_NOTIFICATION_ID;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);

        assertThat(kTestEnvironment.awaitStopped()).isTrue();

        SystemClock.sleep(1000); // Wait a bit for NotificationManager to catch up
        // Notification should remain
        StatusBarNotification[] activeNotifications = mNotificationManager.getActiveNotifications();
        assertThat(activeNotifications).hasLength(1);
        assertThat(activeNotifications[0].getId()).isEqualTo(TEST_NOTIFICATION_ID);
    }

    @Test
    public void testNotificationJobEndRemove() throws Exception {
        mNotificationManager.cancelAll();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(
                TEST_NOTIFICATION_ID, notification, JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);

        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        waitUntil(
                "Notification wasn't posted",
                15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == TEST_NOTIFICATION_ID;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);
        assertThat(kTestEnvironment.awaitStopped()).isTrue();

        waitUntil("Notification wasn't removed", 15 /* seconds */,
                () -> {
                    // Notification should be gone
                    return mNotificationManager.getActiveNotifications().length == 0;
                });
    }

    @Test
    public void testNotificationRemovedOnForceStop() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TestAppInterface.TEST_APP_PACKAGE)) {
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            NETWORK_TYPE_ANY));

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertThat(jobNotification).isNotNull();

            testAppInterface.forceStopApp();

            notificationHelper.assertNotificationsRemoved();
        }
    }

    @Test
    public void testNotificationRemovedOnPackageRestriction() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        String initialActivityManagerConstants = null;
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TestAppInterface.TEST_APP_PACKAGE)) {
            initialActivityManagerConstants =
                    Settings.Global.getString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS);
            SystemUtil.runShellCommand("am set-deterministic-uid-idle true");
            // Set background_settle_time to 0 so that the transition from UID active to UID idle
            // happens quickly.
            Settings.Global.putString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS, "background_settle_time=0");

            testAppInterface.setTestPackageRestricted(true);
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH
                    ));

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertThat(jobNotification).isNotNull();

            final ApplicationInfo testAppInfo =
                    getContext().getPackageManager().getApplicationInfo(TEST_APP_PACKAGE, 0);
            try (WatchUidRunner uidWatcher =
                    new WatchUidRunner(getInstrumentation(), testAppInfo.uid)) {
                // Close the activity so the app isn't considered TOP.
                testAppInterface.closeActivity(true);
                uidWatcher.waitFor(UserInitiatedJobTest.WatchUidRunner.CMD_IDLE);
                SystemClock.sleep(1000); // Wait a bit for JS to process.
            }

            assertThat(testAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT_MS)).isTrue();
            notificationHelper.assertNotificationsRemoved();
        } finally {
            Settings.Global.putString(getContext().getContentResolver(),
                    Settings.Global.ACTIVITY_MANAGER_CONSTANTS, initialActivityManagerConstants);
            SystemUtil.runShellCommand("am set-deterministic-uid-idle false");
        }
    }

    @Test
    public void testNotificationRemovedOnTaskManagerStop() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TestAppInterface.TEST_APP_PACKAGE)) {
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_DETACH,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            NETWORK_TYPE_ANY));

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertThat(jobNotification).isNotNull();

            // Use the same stop reasons as a Task Manager stop.
            testAppInterface.stopJob(
                    JobParameters.STOP_REASON_USER,
                    JobParameters.INTERNAL_STOP_REASON_USER_UI_STOP);

            notificationHelper.assertNotificationsRemoved();
        }
    }

    /**
     * Test that an ANR happens if the app is required to show a notification but doesn't provide
     * one.
     */
    @Test
    public void testNotification_userInitiated_anrWhenNotProvided() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                AnrMonitor monitor = AnrMonitor.start(getInstrumentation(), TEST_APP_PACKAGE);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, false
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    /**
     * Test that no ANR happens if the app is required to show a notification and it provides one.
     */
    @Test
    @LargeTest
    public void testNotification_userInitiated_noAnrWhenProvided() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                AnrMonitor monitor = AnrMonitor.start(getInstrumentation(), TEST_APP_PACKAGE);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            // Confirm no ANR
            monitor.assertNoAnr(25_000);
        }
    }

    /**
     * Test that no ANR happens if the app is not required to show a notification and it doesn't
     * provide one.
     */
    @Test
    @LargeTest
    public void testNotification_regular_noAnrWhenNotProvided() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                AnrMonitor monitor = AnrMonitor.start(getInstrumentation(), TEST_APP_PACKAGE);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TEST_APP_PACKAGE)) {

            testAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, false),
                    Collections.emptyMap());

            notificationHelper.clickNotification();

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            // Confirm no ANR
            monitor.assertNoAnr(25_000);
        }
    }

    @Test
    public void testUserInitiatedJob_hasUijNotificationFlag() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        mNetworkingHelper.setAllNetworksEnabled(true);
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TestAppInterface.TEST_APP_PACKAGE)) {
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true,
                            TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertThat(jobNotification).isNotNull();
            assertWithMessage("A user-initiated job notification should have the UIJ flag")
                    .that(jobNotification.getNotification().isUserInitiatedJob())
                    .isTrue();
        }
    }

    @Test
    public void testNonUserInitiatedJob_doesNotHaveUijNotificationFlag() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                TestNotificationListener.NotificationHelper notificationHelper =
                        new TestNotificationListener.NotificationHelper(
                                getContext(), TestAppInterface.TEST_APP_PACKAGE)) {
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION, true),
                    Collections.emptyMap());

            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            StatusBarNotification jobNotification = notificationHelper.getNotification();
            assertThat(jobNotification).isNotNull();
            assertWithMessage("A non user-initiated job notification should not have the UIJ flag")
                    .that(jobNotification.getNotification().isUserInitiatedJob())
                    .isFalse();
        }
    }

    /**
     * Test that a notification associated with a user-initiated job cannot be cancelled and that
     * its notification channel cannot be deleted.
     */
    @Test
    public void testUserInitiatedJobNotificationBehavior() throws Exception {
        mNotificationManager.cancelAll();
        mNetworkingHelper.setAllNetworksEnabled(true);
        startAndKeepTestActivity();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(NETWORK_TYPE_ANY)
                .build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(
                TEST_NOTIFICATION_ID, notification, JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        runSatisfiedJob(JOB_ID);

        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        waitUntil(
                "Notification wasn't posted",
                15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == TEST_NOTIFICATION_ID;
                });

        mNotificationManager.cancel(TEST_NOTIFICATION_ID);
        waitUntil(
                "A user-initiated job notification should not be cancellable by apps.",
                5 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == TEST_NOTIFICATION_ID;
                });

        try {
            mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
            assertWithMessage(
                            "A notification channel associated with a user-initiated job "
                                    + "should not be cancellable by apps.")
                    .fail();
        } catch (SecurityException expected) {
            assertThat(mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID))
                    .isNotNull();
        }
    }

    /**
     * Test that a notification associated with a non user-initiated job can be cancelled and that
     * its notification channel can be deleted.
     */
    @Test
    public void testNonUserInitiatedJobNotificationBehavior() throws Exception {
        mNotificationManager.cancelAll();
        mNetworkingHelper.setAllNetworksEnabled(true);
        startAndKeepTestActivity();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(
                TEST_NOTIFICATION_ID, notification, JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        runSatisfiedJob(JOB_ID);

        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        waitUntil(
                "Notification wasn't posted",
                15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == TEST_NOTIFICATION_ID;
                });

        mNotificationManager.cancel(TEST_NOTIFICATION_ID);
        waitUntil("A non user-initiated job notification should be cancellable by apps.",
                15 /* seconds */,
                () -> {
                    // Notification should be gone
                    return mNotificationManager.getActiveNotifications().length == 0;
                });

        try {
            mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
            assertThat(mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID))
                    .isNull();
        } catch (SecurityException e) {
            assertWithMessage(
                            "A notification channel associated with a non user-initiated job "
                                    + "should be cancellable by apps.")
                    .fail();
        }
    }
}
