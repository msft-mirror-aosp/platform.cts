/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.Context;
import android.content.pm.PackageManager;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have network connectivity
 * constraints. Requires manipulating the {@link android.net.wifi.WifiManager} to ensure an
 * unmetered network. Similarly, requires that the phone be connected to a wifi hotspot, or else the
 * test will fail.
 */
@TargetApi(21)
@RequiresDevice // Emulators don't always have access to wifi/network
@RunWith(AndroidJUnit4.class)
public final class ConnectivityConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "ConnectivityConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    private static final int CONNECTIVITY_JOB_ID = ConnectivityConstraintTest.class.hashCode();

    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 30000L; // 30 seconds.

    private NetworkingHelper mNetworkingHelper;
    private WifiManager mWifiManager;
    private ConnectivityManager mCm;
    private UserHelper mUserHelper;

    /** Whether the device running these tests supports WiFi. */
    private boolean mHasWifi;

    private JobInfo.Builder mBuilder;

    private TestAppInterface mTestAppInterface;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());

        PackageManager packageManager = mContext.getPackageManager();
        mHasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
        mBuilder = new JobInfo.Builder(CONNECTIVITY_JOB_ID, kJobServiceComponent);

        setDataSaverEnabled(false);
        mNetworkingHelper.setAllNetworksEnabled(true);
        mUserHelper = new UserHelper(mContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (mTestAppInterface != null) {
            mTestAppInterface.cleanup();
        }
        mJobScheduler.cancel(CONNECTIVITY_JOB_ID);

        BatteryUtils.runDumpsysBatteryReset();
        BatteryUtils.resetBatterySaver();

        // Ensure that we leave WiFi in its previous state.
        mNetworkingHelper.tearDown();

        super.tearDown();
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it executes when the device
     * is connected to WiFi. This will fail if a wifi connection is unavailable.
     */
    @Test
    public void testUnmeteredConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with unmetered constraint did not fire on WiFi.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /** Schedule a job with a connectivity constraint, and ensure that it executes on WiFi. */
    @Test
    public void testConnectivityConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with connectivity constraint did not fire on WiFi.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes on WiFi,
     * even with Data Saver on.
     */
    @Test
    public void testConnectivityConstraintExecutes_withWifi_DataSaverOn() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);
        setDataSaverEnabled(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with connectivity constraint did not fire on unmetered WiFi.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes on a
     * cellular data connection.
     */
    @Test
    public void testConnectivityConstraintExecutes_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with connectivity constraint did not fire on mobile.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes on a
     * metered wifi connection.
     */
    @Test
    public void testConnectivityConstraintExecutes_withMeteredWifi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            return;
        }
        setWifiMeteredState(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with connectivity constraint did not fire on metered wifi.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it isn't stopped when
     * the device transitions to WiFi, but is informed of the network change.
     */
    @Test
    public void testConnectivityConstraintExecutes_transitionNetworks() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setExpectedStopped();
        kTestEnvironment.setExpectedNetworkChange();
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job with connectivity constraint did not fire on mobile.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
        JobParameters startParams = kTestEnvironment.getLastStartJobParameters();

        connectToWifi();
        assertWithMessage(
                        "Job with connectivity constraint was stopped when network transitioned to"
                                + " WiFi.")
                .that(kTestEnvironment.awaitStopped())
                .isFalse();
        assertWithMessage("Job didn't get network change signal when network transitioned to WiFi.")
                .that(kTestEnvironment.awaitNetworkChange())
                .isTrue();
        JobParameters networkChangedParams = kTestEnvironment.getLastNetworkChangedJobParameters();
        assertThat(networkChangedParams.getNetwork()).isNotNull();
        assertThat(startParams.getNetwork()).isNotEqualTo(networkChangedParams.getNetwork());
    }

    /**
     * Schedule a job with a metered connectivity constraint, and ensure that it executes on a
     * mobile data connection.
     */
    @Test
    public void testConnectivityConstraintExecutes_metered_mobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertWithMessage("Job with metered connectivity constraint did not fire on mobile.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job with a metered connectivity constraint, and ensure that it executes on a
     * mobile data connection.
     */
    @Test
    public void testConnectivityConstraintExecutes_metered_Wifi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            return;
        }
        setWifiMeteredState(true);


        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED).build());

        // Since we equate "metered" to "cellular", the job shouldn't start.
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertWithMessage(
                        "Job with metered connectivity constraint fired on a metered wifi network.")
                .that(kTestEnvironment.awaitTimeout())
                .isTrue();
    }

    /**
     * Schedule a job with a cellular connectivity constraint, and ensure that it executes on a
     * mobile data connection and is not stopped when Data Saver is turned on because the app is in
     * the foreground.
     */
    @Test
    public void testCellularConstraintExecutedAndStopped_Foreground() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            // No mobile or wifi.
            return;
        }

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.startAndKeepTestActivity();
        toggleScreenOn(true);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, false);

        mTestAppInterface.runSatisfiedJob();
        assertWithMessage(
                        "Job with metered connectivity constraint did not fire on a metered"
                                + " network.")
                .that(mTestAppInterface.awaitJobStart(30_000))
                .isTrue();

        setDataSaverEnabled(true);
        assertWithMessage(
                        "Job with metered connectivity constraint for foreground app was stopped"
                                + " when Data Saver was turned on.")
                .that(mTestAppInterface.awaitJobStop(30_000))
                .isFalse();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when if an app is idle.
     */
    @Test
    public void testExpeditedJobExecutes_IdleApp() throws Exception {
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(false);
        } else if (!hasEthernetConnection()) {
            // We're skipping this test because we can't force cellular or other networks to be
            // unmetered. For now, we assume ethernet is always unmetered.
            Log.d(TAG, "Skipping test that requires an unmetered network.");
            return;
        }

        setDeviceConfigFlag("qc_max_session_count_restricted", "0", true);
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Expedited job requiring connectivity did not fire when app was idle.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Battery Saver is on.
     */
    @Test
    public void testExpeditedJobExecutes_BatterySaverOn() throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (hasEthernetConnection()) {
            // We need to use a metered network but can't control the ethernet connection.
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered.");
            return;
        }

        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage(
                        "Expedited job requiring connectivity did not fire with Battery Saver on.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Doze is on.
     */
    @Test
    public void testExpeditedJobExecutes_DozeOn() throws Exception {
        if (!isDeviceIdleFeatureEnabled()) {
            // Test requires device idle feature
            return;
        }

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mNetworkingHelper.setAllNetworksEnabled(true);
        toggleScreenOn(false);
        setDeviceIdleState(true);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);

        mTestAppInterface.runSatisfiedJob();
        assertWithMessage("UI job requiring connectivity did not fire with Doze on.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Data Saver is on and the device is not connected to WiFi.
     */
    @Test
    public void testFgExpeditedJobBypassesDataSaver() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.startAndKeepTestActivity();

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertWithMessage(
                        "FG expedited job requiring metered connectivity did not fire with Data"
                                + " Saver on.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when multiple firewalls are active.
     */
    @Test
    public void testExpeditedJobBypassesSimultaneousFirewalls_noDataSaver() throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }

        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);
        setDataSaverEnabled(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage(
                        "Expedited job requiring connectivity did not fire with multiple"
                                + " firewalls.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job that requires a network connection, and verify that it runs even after the
     * scheduling app is killed.
     *
     * <p>Note: This is a basic test similar to testConnectivityConstraintExecutes_withWifi, except
     * that it uses a helper app so the scheduling app's lifecycle and any resulting restrictions
     * can be managed freely by the system.
     */
    @Test
    public void testJobExecutes_afterAppIsKilled() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        // To ensure the job doesn't start immediately after scheduling.
        mNetworkingHelper.setAllNetworksEnabled(false);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_ANY, false);

        mTestAppInterface.kill();
        PollingCheck.waitFor(DEFAULT_TIMEOUT_MILLIS,
                mTestAppInterface::isNetworkBlockedByPolicy,
                "Test app did not lose network access after being stopped");
        // The job should run after network is connected, even though the app does not have access
        // due to policy right now.
        mNetworkingHelper.setAllNetworksEnabled(true);

        mTestAppInterface.runSatisfiedJob();
        assertWithMessage("Job requiring network did not start after the app was killed")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
    }

    /**
     * Schedule a user-initiated job that requires a network connection, and verify that it runs
     * even when Battery Saver is on.
     */
    @Test
    public void testUserInitiatedJobExecutes_BatterySaverOn() throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }

        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        // Put app in a valid state to schedule a UI job.
        mTestAppInterface.startAndKeepTestActivity();
        toggleScreenOn(true);

        mNetworkingHelper.setAllNetworksEnabled(true);
        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, false, true);

        mTestAppInterface.runSatisfiedJob();
        assertWithMessage("UI job requiring connectivity did not fire with Battery Saver on.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
    }

    /**
     * Schedule a user-initiated job that requires a network connection, and verify that it runs
     * even when Doze is on.
     */
    @Test
    public void testUserInitiatedJobExecutes_DozeOn() throws Exception {
        if (!isDeviceIdleFeatureEnabled()) {
            // Test requires device idle feature
            return;
        }

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
            // Put app in a valid state to schedule a UI job.
            mNetworkingHelper.setAllNetworksEnabled(true);
            toggleScreenOn(false);
            setDeviceIdleState(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );

            notificationHelper.clickNotification();

            assertWithMessage("UI job requiring connectivity did not fire with Doze on.")
                    .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                    .isTrue();
        }
    }

    // --------------------------------------------------------------------------------------------
    // Positives & Negatives - schedule jobs under conditions that require that pass initially and
    // then fail with a constraint change.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job with a cellular connectivity constraint, and ensure that it executes on a
     * mobile data connection and is stopped when Data Saver is turned on.
     */
    @Test
    public void testCellularConstraintExecutedAndStopped() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();
        setDataSaverEnabled(false);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, false);

        mTestAppInterface.runSatisfiedJob();
        assertWithMessage("Job with cellular constraint did not fire on mobile.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();

        setDataSaverEnabled(true);
        assertWithMessage(
                        "Job with cellular constraint was not stopped when Data Saver was turned"
                                + " on.")
                .that(mTestAppInterface.awaitJobStop(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
    }

    @Test
    public void testJobParametersNetwork() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);

        // Everything good.
        final NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        JobInfo ji = mBuilder.setRequiredNetwork(nr).build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getNetwork()).isNotNull();
        final NetworkCapabilities capabilities =
                getContext().getSystemService(ConnectivityManager.class)
                        .getNetworkCapabilities(params.getNetwork());
        assertThat(nr.canBeSatisfiedBy(capabilities)).isTrue();

        if (!hasEthernetConnection()) {
            // Deadline passed with no network satisfied.
            mNetworkingHelper.setAllNetworksEnabled(false);

            if (CompatChanges.isChangeEnabled(TestAppInterface.ENFORCE_MINIMUM_TIME_WINDOWS)) {
                SystemUtil.runWithShellPermissionIdentity(
                        () -> CompatChanges.putPackageOverrides(
                                TestAppInterface.TEST_APP_PACKAGE,
                                Map.of(TestAppInterface.ENFORCE_MINIMUM_TIME_WINDOWS,
                                        new PackageOverride.Builder().setEnabled(false).build())
                        ),
                        OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD, INTERACT_ACROSS_USERS_FULL);
            }
            mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
            mTestAppInterface.scheduleJob(
                    Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_DEADLINE, 0L)
            );
            mTestAppInterface.runSatisfiedJob();
            assertWithMessage("Job didn't fire immediately")
                    .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                    .isTrue();
            params = mTestAppInterface.getLastParams();
            assertThat(params.getNetwork()).isNull();
        }

        // No network requested
        mNetworkingHelper.setAllNetworksEnabled(true);
        ji = mBuilder.setRequiredNetwork(null).build();
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getNetwork()).isNull();
    }

    @Test
    public void testJobUidState() throws Exception {
        // Device that support visible background users might have different display groups
        // for each display.
        // When KEYCODE_SLEEP event is triggered, other display groups may not enter sleep mode,
        // unlike the default display group.
        if (mUserHelper.isVisibleBackgroundUserSupported()) {
            Log.d(TAG, "Skip the test on devices that support visible background users"
                    + ", because the device might not enter sleep mode even with KEYCODE_SLEEP.");
            return;
        }

        // Turn screen off so any lingering activity close processing from previous tests
        // don't affect this one.
        toggleScreenOn(false);
        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.scheduleJob(
                Map.of(TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                        JobInfo.NETWORK_TYPE_ANY
                )
        );
        mTestAppInterface.forceRunJob();
        assertWithMessage("Job did not start after scheduling")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isTrue();
        mTestAppInterface.assertJobUidState(new TestAppInterface.ExpectedJobUidState.Builder()
                .setProcState(ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND)
                .setExpectedCapability(0)
                .setUnexpectedCapability(ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
                        | ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK)
                .setOomScoreAdj(250 /* ProcessList.PERCEPTIBLE_LOW_APP_ADJ */)
                .build());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it fails when the device is
     * connected to a cellular provider. This test assumes that if the device supports a mobile data
     * connection, then this connection will be available.
     */
    @Test
    public void testUnmeteredConstraintFails_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job requiring unmetered connectivity still executed on mobile.")
                .that(kTestEnvironment.awaitTimeout())
                .isTrue();
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when the
     * device is not connected to WiFi and Data Saver is on.
     */
    @Test
    public void testMeteredConstraintFails_withMobile_DataSaverOn() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        disconnectWifiToConnectToMobile();
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, false);
        mTestAppInterface.runSatisfiedJob();

        assertWithMessage("Job requiring cellular connectivity executed with Data Saver on")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isFalse();
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when the
     * device is not connected to WiFi and Data Saver is on.
     */
    @Test
    public void testEJMeteredConstraintFails_withMobile_DataSaverOn() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        disconnectWifiToConnectToMobile();
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, true);
        mTestAppInterface.runSatisfiedJob();

        assertWithMessage(
                        "BG expedited job requiring cellular connectivity executed with Data Saver"
                                + " on")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isFalse();
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when the
     * device is connected to an unmetered WiFi provider. This test assumes that if the device
     * supports a mobile data connection, then this connection will be available.
     */
    @Test
    public void testMeteredConstraintFails_withWiFi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job requiring metered connectivity still executed on WiFi.")
                .that(kTestEnvironment.awaitTimeout())
                .isTrue();
    }

    /**
     * Schedule a job that requires an unmetered connection, and verify that it does not run when
     * the device is connected to a metered WiFi provider.
     */
    @Test
    public void testUnmeteredConstraintFails_withMeteredWiFi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(true);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job requiring unmetered connectivity still executed on metered WiFi.")
                .that(kTestEnvironment.awaitTimeout())
                .isTrue();
    }

    /**
     * Schedule a job that requires a cellular connection, and verify that it does not run when the
     * device is connected to a WiFi provider.
     */
    @Test
    public void testCellularConstraintFails_withWiFi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR).build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertWithMessage("Job requiring cellular connectivity still executed on WiFi.")
                .that(kTestEnvironment.awaitTimeout())
                .isTrue();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it doesn't run
     * when Data Saver is on and the device is not connected to WiFi.
     */
    @Test
    public void testBgExpeditedJobDoesNotBypassDataSaver() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertWithMessage("BG expedited job requiring connectivity fired with Data Saver on.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isFalse();
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when multiple firewalls are active.
     */
    @Test
    public void testExpeditedJobDoesNotBypassSimultaneousFirewalls_withDataSaver()
            throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }

        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertWithMessage("Expedited job fired with multiple firewalls, including data saver.")
                .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                .isFalse();
    }

    /**
     * Schedule a user-initiated job that requires a network connection, and verify that it runs
     * even when Data Saver is on and the device is not connected to WiFi.
     */
    @Test
    public void testBgUiJobBypassesDataSaver() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            setDataSaverEnabled(true);

            mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
            mTestAppInterface.closeActivity(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertWithMessage("BG UI job requiring connectivity didn't fire with Data Saver on.")
                    .that(mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS))
                    .isTrue();
        }
    }

    /**
     * Make sure that regular and expedited jobs don't run during data saver even if a
     * user-initiated job is running at the same time.
     */
    @Test
    public void testBgNonUiJobDoesNotBypassDataSaverWhenUiJobRunning() throws Exception {
        // TODO(b/380297485): Remove this check once NotificationListeners support
        // visible background users.
        if (mUserHelper.isVisibleBackgroundUser()) {
            Log.d(TAG, "Skipping test since "
                    + "NotificationListeners do not support visible background users");
            return;
        }
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            setDataSaverEnabled(true);

            final int uiJobId = CONNECTIVITY_JOB_ID;
            final int expJobId = CONNECTIVITY_JOB_ID + 1;
            final int regJobId = CONNECTIVITY_JOB_ID + 2;
            mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
            mTestAppInterface.closeActivity(true);
            // Regular job
            mTestAppInterface.scheduleJob(
                    Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, regJobId,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );
            // EJ
            mTestAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, expJobId,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );
            // UI job
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, uiJobId,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );

            // Clicking on the notification should put the app into a BAL approved state.
            notificationHelper.clickNotification();

            assertWithMessage("BG UI job requiring connectivity didn't fire with Data Saver on.")
                    .that(mTestAppInterface.awaitJobStart(uiJobId, DEFAULT_TIMEOUT_MILLIS))
                    .isTrue();
            // The UI job may have started immediately, so keep the standard timeout for the
            // EJ check to give enough time to confirm the job didn't start.
            assertWithMessage("BG EJ requiring connectivity fired with Data Saver on.")
                    .that(mTestAppInterface.awaitJobStart(expJobId, DEFAULT_TIMEOUT_MILLIS))
                    .isFalse();
            // At this point, there's been enough time for this job to start, so don't have
            // a long wait time.
            assertWithMessage("BG job requiring connectivity fired with Data Saver on.")
                    .that(mTestAppInterface.awaitJobStart(regJobId, 1000))
                    .isFalse();
        }
    }

    // --------------------------------------------------------------------------------------------
    // Utility methods
    // --------------------------------------------------------------------------------------------

    /**
     * Determine whether the device running these CTS tests should be subject to tests involving
     * mobile data.
     * @return True if this device will support a mobile data connection.
     */
    private boolean checkDeviceSupportsMobileData() throws Exception {
        return mNetworkingHelper.hasCellularNetwork();
    }

    private boolean hasEthernetConnection() {
        return mNetworkingHelper.hasEthernetConnection();
    }

    private void setWifiMeteredState(boolean metered) throws Exception {
        mNetworkingHelper.setWifiMeteredState(metered);
    }

    /**
     * Ensure WiFi is enabled, and block until we've verified that we are in fact connected.
     */
    private void connectToWifi() throws Exception {
        mNetworkingHelper.setWifiState(true);
    }

    /**
     * Ensure WiFi is disabled, and block until we've verified that we are in fact disconnected.
     */
    private void disconnectFromWifi() throws Exception {
        mNetworkingHelper.setWifiState(false);
    }

    /**
     * Disconnect from WiFi in an attempt to connect to cellular data. Worth noting that this is
     * best effort - there are no public APIs to force connecting to cell data. We disable WiFi
     * and wait for a broadcast that we're connected to cell.
     * We will not call into this function if the device doesn't support telephony.
     * @see NetworkingHelper#hasCellularNetwork
     * @see #checkDeviceSupportsMobileData()
     */
    private void disconnectWifiToConnectToMobile() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        if (mHasWifi && mWifiManager.isWifiEnabled()) {
            NetworkRequest nr = new NetworkRequest.Builder().clearCapabilities().build();
            NetworkCapabilities nc = new NetworkCapabilities.Builder()
                    .addTransportType(TRANSPORT_CELLULAR)
                    .build();
            NetworkingHelper.NetworkTracker tracker =
                    new NetworkingHelper.NetworkTracker(nc, true, mCm);
            mCm.registerNetworkCallback(nr, tracker);

            disconnectFromWifi();

            assertWithMessage("Device must have access to a metered network for this test.")
                    .that(tracker.waitForStateChange())
                    .isTrue();

            mCm.unregisterNetworkCallback(tracker);
        }
    }

    /**
     * Ensures that restrict background data usage policy is turned off.
     * If the policy is on, it interferes with tests that relies on metered connection.
     */
    private void setDataSaverEnabled(boolean enabled) throws Exception {
        mNetworkingHelper.setDataSaverEnabled(enabled);
    }
}
