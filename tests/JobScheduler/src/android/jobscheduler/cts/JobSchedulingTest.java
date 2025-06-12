/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.Manifest.permission.RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.OP_RUN_USER_INITIATED_JOBS;
import static android.app.AppOpsManager.opToPermission;
import static android.jobscheduler.cts.JobThrottlingTest.setTestPackageStandbyBucket;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.PendingJobReasonsInfo;
import android.jobscheduler.MockJobService.TestEnvironment;
import android.jobscheduler.MockJobService.TestEnvironment.Event;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.SystemClock;
import android.os.Temperature;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThermalUtils;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Tests related to scheduling jobs. */
@TargetApi(30)
@RunWith(AndroidJUnit4.class)
public final class JobSchedulingTest extends BaseJobSchedulerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final int MIN_SCHEDULE_QUOTA = 250;
    private static final int JOB_ID = JobSchedulingTest.class.hashCode();
    // The maximum number of jobs that can run concurrently.
    private static final int MAX_JOB_CONTEXTS_COUNT = 64;

    @Override
    @After
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler reset-schedule-quota");
        BatteryUtils.runDumpsysBatteryReset();
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);

        // The super method should be called at the end.
        super.tearDown();
    }

    /** Tests that an ANR happens if the job is blocked in onStartJob. */
    @Test
    public void testAnr_onStartJob() throws Exception {
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                AnrMonitor monitor = AnrMonitor.start(getInstrumentation(), TEST_APP_PACKAGE)) {

            setTestPackageStandbyBucket(
                    UiDevice.getInstance(getInstrumentation()), JobThrottlingTest.Bucket.ACTIVE);
            SystemUtil.runShellCommand(getInstrumentation(),
                    "am compat enable ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES " + TEST_APP_PACKAGE);

            testAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                            TestJobSchedulerReceiver.EXTRA_SLOW_START, true),
                    Collections.emptyMap());

            testAppInterface.forceRunJob();
            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    /** Tests that an ANR happens if the job is blocked in onStopJob. */
    @Test
    public void testAnr_onStopJob() throws Exception {
        try (TestAppInterface testAppInterface = new TestAppInterface(getContext(), JOB_ID);
                AnrMonitor monitor = AnrMonitor.start(getInstrumentation(), TEST_APP_PACKAGE)) {

            setTestPackageStandbyBucket(
                    UiDevice.getInstance(getInstrumentation()), JobThrottlingTest.Bucket.ACTIVE);
            SystemUtil.runShellCommand(getInstrumentation(),
                    "am compat enable ANR_PRE_UDC_APIS_ON_SLOW_RESPONSES " + TEST_APP_PACKAGE);

            testAppInterface.scheduleJob(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                            TestJobSchedulerReceiver.EXTRA_SLOW_STOP, true),
                    Collections.emptyMap());

            testAppInterface.forceRunJob();
            assertWithMessage("Job did not start after scheduling")
                    .that(testAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS))
                    .isTrue();

            testAppInterface.cancelJob();

            // Confirm ANR
            monitor.waitForAnrAndReturnUptime(30_000);
        }
    }

    @Test
    public void testCancel_runningJob() throws Exception {
        JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent).setExpedited(true).build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setExpectedStopped();
        kTestEnvironment.setRequestReschedule();
        mJobScheduler.schedule(jobInfo);
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        mJobScheduler.cancelAll();
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitStopped()).isTrue();
        SystemClock.sleep(5000); // Give some time for JS to finish its internal processing.
        assertThat(mJobScheduler.getAllPendingJobs()).isEmpty();
    }

    @Test
    public void testCanRunUserInitiatedJobs() throws Exception {
        final boolean isAppOpPermission = isRunUserInitiatedJobsPermissionAppOp();

        // Default is allowed.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);
        assertThat(mJobScheduler.canRunUserInitiatedJobs()).isTrue();

        // Toggle the appop won't make a change of JobScheduler#canRunUserInitiatedJobs if it's not
        // an appop permission.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ERRORED);
        assertThat(isAppOpPermission ^ mJobScheduler.canRunUserInitiatedJobs()).isTrue();

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ALLOWED);
        assertThat(mJobScheduler.canRunUserInitiatedJobs()).isTrue();

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_IGNORED);
        assertThat(isAppOpPermission ^ mJobScheduler.canRunUserInitiatedJobs()).isTrue();
    }

    /**
     * Test that apps can call schedule at least the minimum amount of times without being blocked.
     */
    @Test
    public void testMinSuccessfulSchedulingQuota() {
        JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60 * 60 * 1000L)
                        .setPersisted(true)
                        .build();

        for (int i = 0; i < MIN_SCHEDULE_QUOTA; ++i) {
            assertThat(mJobScheduler.schedule(jobInfo)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        }
    }

    /** Test that scheduling fails once an app hits the schedule quota limit. */
    @Test
    public void testFailingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 300000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        final JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60 * 60 * 1000L)
                        .setPersisted(true)
                        .build();

        for (int i = 0; i < 500; ++i) {
            final int expected =
                    i < 300 ? JobScheduler.RESULT_SUCCESS : JobScheduler.RESULT_FAILURE;
            assertWithMessage("Got unexpected result for schedule #%s", i + 1)
                    .that(mJobScheduler.schedule(jobInfo))
                    .isEqualTo(expected);
        }
    }

    /** Test that scheduling succeeds even after an app hits the schedule quota limit. */
    @Test
    public void testContinuingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setBoolean("enable_api_quotas", true)
                        .setInt("aq_schedule_count", 300)
                        .setLong("aq_schedule_window_ms", 300000)
                        .setBoolean("aq_schedule_throw_exception", false)
                        .setBoolean("aq_schedule_return_failure", false)
                        .build());

        JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60 * 60 * 1000L)
                        .setPersisted(true)
                        .build();

        for (int i = 0; i < 500; ++i) {
            assertWithMessage("Got unexpected result for schedule #%s", i + 1)
                    .that(mJobScheduler.schedule(jobInfo))
                    .isEqualTo(JobScheduler.RESULT_SUCCESS);
        }
    }

    /** Test that non-persisted jobs aren't limited by quota. */
    @Test
    public void testNonPersistedJobsNotLimited() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 60000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        final JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60 * 60 * 1000L)
                        .setPersisted(false)
                        .build();

        for (int i = 0; i < 500; ++i) {
            assertThat(mJobScheduler.schedule(jobInfo)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        }
    }

    @Test
    public void testHigherPriorityJobRunsFirst() throws Exception {
        setStorageStateLow(true);
        final int higherPriorityJobId = JOB_ID;
        final int numMinPriorityJobs = 2 * MAX_JOB_CONTEXTS_COUNT;
        kTestEnvironment.setExpectedExecutions(1 + numMinPriorityJobs);
        for (int i = 0; i < numMinPriorityJobs; ++i) {
            JobInfo job = new JobInfo.Builder(higherPriorityJobId + 1 + i, kJobServiceComponent)
                    .setPriority(JobInfo.PRIORITY_MIN)
                    .setRequiresStorageNotLow(true)
                    .build();
            mJobScheduler.schedule(job);
        }
        // Schedule the higher priority job last since the default sorting is by enqueue time.
        JobInfo jobMax =
                new JobInfo.Builder(higherPriorityJobId, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_DEFAULT)
                        .setRequiresStorageNotLow(true)
                        .build();
        mJobScheduler.schedule(jobMax);

        setStorageStateLow(false);
        kTestEnvironment.awaitExecution();

        final Event jobHigherExecution =
                new Event(TestEnvironment.Event.EVENT_START_JOB, higherPriorityJobId);
        List<Event> executedEvents = kTestEnvironment.getExecutedEvents();
        boolean higherExecutedFirst = false;
        // Due to racing, we can't just check the very first item in the array. We can however
        // make sure it was in the first set of jobs to run.
        for (int i = 0; i < executedEvents.size() && i < MAX_JOB_CONTEXTS_COUNT; ++i) {
            if (executedEvents.get(i).equals(jobHigherExecution)) {
                higherExecutedFirst = true;
                break;
            }
        }
        assertWithMessage(
                        "Higher priority job (%s) didn't run in first batch: %s",
                        higherPriorityJobId, executedEvents)
                .that(higherExecutedFirst)
                .isTrue();
    }

    @Test
    public void testNamespaceSetting() {
        JobScheduler js = getContext().getSystemService(JobScheduler.class);

        assertThat(js.getNamespace()).isNull();

        js = js.forNamespace("A");
        assertThat(js.getNamespace()).isEqualTo("A");
        js = js.forNamespace("B");
        assertThat(js.getNamespace()).isEqualTo("B");
        js = js.forNamespace("AB");
        assertThat(js.getNamespace()).isEqualTo("AB");
        js = js.forNamespace("A");
        assertThat(js.getNamespace()).isEqualTo("A");

        final JobScheduler js2 = getContext().getSystemService(JobScheduler.class);
        assertThat(js2.getNamespace()).isNull();

        assertThrows(
                "Successfully retrieved instance with null namespace",
                NullPointerException.class,
                () -> js2.forNamespace(null));
        assertThrows(
                "Successfully retrieved instance with empty namespace",
                IllegalArgumentException.class,
                () -> js2.forNamespace(""));
        assertThrows(
                "Successfully retrieved instance with whitespace-only namespace",
                IllegalArgumentException.class,
                () -> js2.forNamespace("        "));
    }

    @Test
    public void testNamespace_schedule() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPersisted(true)
                        .build();
        JobInfo jobB =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_001)
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .setPersisted(true)
                        .build();

        assertThat(jobA).isNotEqualTo(jobB);
        assertThat(jsA.schedule(jobA)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        assertThat(jsB.schedule(jobB)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        assertThat(mJobScheduler.getPendingJob(JOB_ID)).isNull();
        assertThat(jsA.getPendingJob(JOB_ID)).isEqualTo(jobA);
        assertThat(jsB.getPendingJob(JOB_ID)).isEqualTo(jobB);

        // App global
        Map<String, List<JobInfo>> allJobs = mJobScheduler.getPendingJobsInAllNamespaces();
        Map<String, List<JobInfo>> allJobsA = jsA.getPendingJobsInAllNamespaces();
        Map<String, List<JobInfo>> allJobsB = jsB.getPendingJobsInAllNamespaces();
        assertThat(allJobs).isEqualTo(allJobsA);
        assertThat(allJobsA).isEqualTo(allJobsB);
        assertThat(allJobsA).hasSize(2);
        assertThat(allJobsA.get("A")).hasSize(1);
        assertThat(allJobsA.get("B")).hasSize(1);
        assertThat(allJobsA.get("A")).contains(jobA);
        assertThat(allJobsA.get("B")).contains(jobB);

        // In namespace
        List<JobInfo> namespaceJobs = mJobScheduler.getAllPendingJobs();
        List<JobInfo> namespaceJobsA = jsA.getAllPendingJobs();
        List<JobInfo> namespaceJobsB = jsB.getAllPendingJobs();
        assertThat(namespaceJobsA).isNotEqualTo(namespaceJobsB);
        assertThat(namespaceJobs).isEmpty();
        assertThat(namespaceJobsA).hasSize(1);
        assertThat(namespaceJobsB).hasSize(1);
        assertThat(namespaceJobsA).containsExactly(jobA);
        assertThat(namespaceJobsB).containsExactly(jobB);
    }

    @Test
    public void testNamespace_cancel() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPersisted(true)
                        .build();
        JobInfo jobB =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_001)
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .setPersisted(true)
                        .build();

        assertThat(jobA).isNotEqualTo(jobB);
        assertThat(jsA.schedule(jobA)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        assertThat(jsB.schedule(jobB)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        jsA.cancel(JOB_ID);
        assertThat(jsA.getPendingJob(JOB_ID)).isNull();
        assertThat(jsB.getPendingJob(JOB_ID)).isEqualTo(jobB);

        jsB.cancel(JOB_ID);
        assertThat(jsA.getPendingJob(JOB_ID)).isNull();
        assertThat(jsB.getPendingJob(JOB_ID)).isNull();
    }

    @Test
    public void testNamespace_cancelInAllNamespaces() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPersisted(true)
                        .build();
        JobInfo jobB =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_001)
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .setPersisted(true)
                        .build();

        assertThat(jobA).isNotEqualTo(jobB);
        assertThat(jsA.schedule(jobA)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        assertThat(jsB.schedule(jobB)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        mJobScheduler.cancelInAllNamespaces();
        assertThat(jsA.getPendingJob(JOB_ID)).isNull();
        assertThat(jsB.getPendingJob(JOB_ID)).isNull();
    }

    @Test
    public void testNamespace_cancelAllInNamespace() {
        JobScheduler jsA = getContext().getSystemService(JobScheduler.class).forNamespace("A");
        JobScheduler jsB = getContext().getSystemService(JobScheduler.class).forNamespace("B");
        JobInfo jobA =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPersisted(true)
                        .build();
        JobInfo jobB =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_001)
                        .setPriority(JobInfo.PRIORITY_LOW)
                        .setPersisted(true)
                        .build();

        assertThat(jobA).isNotEqualTo(jobB);
        assertThat(jsA.schedule(jobA)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        assertThat(jsB.schedule(jobB)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        mJobScheduler.cancelAll();
        assertThat(jsA.getPendingJob(JOB_ID)).isEqualTo(jobA);
        assertThat(jsB.getPendingJob(JOB_ID)).isEqualTo(jobB);

        jsA.cancelAll();
        assertThat(jsA.getPendingJob(JOB_ID)).isNull();
        assertThat(jsB.getPendingJob(JOB_ID)).isEqualTo(jobB);

        jsB.cancelAll();
        assertThat(jsA.getPendingJob(JOB_ID)).isNull();
        assertThat(jsB.getPendingJob(JOB_ID)).isNull();
    }

    @Test
    public void testPendingJobReason_noJob() {
        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID);
    }

    @Test
    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    public void testPendingJobReasons_noJob() {
        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID});
    }

    @Test
    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_HISTORY_API)
    public void testPendingJobReasonsHistory_noJob() {
        assertThrows(
                "Expected IllegalArgumentException for an invalid job id",
                IllegalArgumentException.class,
                () -> mJobScheduler.getPendingJobReasonsHistory(JOB_ID));
    }

    @Test
    public void testPendingJobReason_alreadyRunning() throws Exception {
        JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent).setExpedited(true).build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_EXECUTING);
    }

    @Test
    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    public void testPendingJobReasons_alreadyRunning() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_EXECUTING});
    }

    @Test
    public void testPendingJobReason_batteryNotLow() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }

        setBatteryState(false, 5);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW);
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_batteryNotLow() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }
        setBatteryState(false, 5);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW});
    }

    @Test
    public void testPendingJobReason_charging() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }

        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING);
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_charging() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }
        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING});
    }

    @Test
    public void testPendingJobReason_connectivity() throws Exception {
        final NetworkingHelper networkingHelper =
                new NetworkingHelper(getInstrumentation(), getContext());

        if (networkingHelper.hasEthernetConnection()) {
            // Can't test while there's an active ethernet connection.
            return;
        }

        try {
            networkingHelper.setAllNetworksEnabled(false);
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();

            mJobScheduler.schedule(jobInfo);
            assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                    .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY);
        } finally {
            networkingHelper.tearDown();
        }
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_connectivity() throws Exception {
        final NetworkingHelper networkingHelper =
                new NetworkingHelper(getInstrumentation(), getContext());

        if (networkingHelper.hasEthernetConnection()) {
            // Can't test while there's an active ethernet connection.
            return;
        }

        try {
            networkingHelper.setAllNetworksEnabled(false);
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();
            mJobScheduler.schedule(jobInfo);

            assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                    .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY});
        } finally {
            networkingHelper.tearDown();
        }
    }

    @Test
    public void testPendingJobReason_contentTrigger() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        TriggerContentTest.MEDIA_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .build();

        mJobScheduler.schedule(jobInfo);
        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER);
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_contentTrigger() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        TriggerContentTest.MEDIA_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .build();

        mJobScheduler.schedule(jobInfo);
        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER});
    }

    @Test
    public void testPendingJobReason_minimumLatency() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();

        mJobScheduler.schedule(jobInfo);
        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY);
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_minimumLatency() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();

        mJobScheduler.schedule(jobInfo);
        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY});
    }

    @Test
    public void testPendingJobReason_storageNotLow() throws Exception {
        setStorageStateLow(true);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW);
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_storageNotLow() throws Exception {
        setStorageStateLow(true);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW});
    }

    /** Verify that any caching isn't JobScheduler doesn't result in returning invalid reasons. */
    @Test
    public void testPendingJobReason_reasonCanChange() throws Exception {
        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY);

        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_EXECUTING);

        mJobScheduler.cancel(JOB_ID);
        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID);

        setStorageStateLow(true);
        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();

        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReason(JOB_ID))
                .isEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW);
    }

    /** Verify that any caching isn't JobScheduler doesn't result in returning invalid reasons. */
    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_reasonCanChange() throws Exception {
        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID});

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY});

        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        mJobScheduler.schedule(jobInfo);
        assertWithMessage("Job didn't start").that(kTestEnvironment.awaitExecution()).isTrue();

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_EXECUTING});

        mJobScheduler.cancel(JOB_ID);
        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID});

        setStorageStateLow(true);
        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_CONSTRAINT_STORAGE_NOT_LOW});
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_idleChargingLatency() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }
        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(
                        new int[] {
                            JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING,
                            JobScheduler.PENDING_JOB_REASON_CONSTRAINT_DEVICE_IDLE,
                            JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY
                        });
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_deadlineAndLatency() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .setOverrideDeadline(2 * HOUR_IN_MILLIS)
                .build();
        mJobScheduler.schedule(jobInfo);

        assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                .isEqualTo(
                        new int[] {
                            JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY,
                            JobScheduler.PENDING_JOB_REASON_CONSTRAINT_DEADLINE
                        });
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_API)
    @Test
    public void testPendingJobReasons_thermal() throws Exception {
        try {
            ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_CRITICAL);

            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
            mJobScheduler.schedule(jobInfo);

            assertThat(mJobScheduler.getPendingJobReasons(JOB_ID))
                    .isEqualTo(new int[] {JobScheduler.PENDING_JOB_REASON_DEVICE_STATE});
        } finally {
            ThermalUtils.resetThermalStatus();
        }
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_HISTORY_API)
    @Test
    public void testPendingJobReasonsHistory_updatesCorrectly() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }
        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .setRequiresCharging(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        List<PendingJobReasonsInfo> reasons = mJobScheduler.getPendingJobReasonsHistory(JOB_ID);
        assertThat(reasons).isNotNull();
        int initialSize = reasons.size();

        setBatteryState(true, 100); // trigger a constraint change
        List<PendingJobReasonsInfo> newReasons = mJobScheduler.getPendingJobReasonsHistory(JOB_ID);
        assertThat(newReasons).hasSize(initialSize + 1);
        // ensure that all previous elements are unchanged
        for (int i = 0; i < reasons.size(); i++) {
            PendingJobReasonsInfo originalReason = reasons.get(i);
            PendingJobReasonsInfo newReason = newReasons.get(i);
            assertThat(originalReason.getTimestampMillis())
                    .isEqualTo(newReason.getTimestampMillis());
            assertThat(originalReason.getPendingJobReasons())
                    .isEqualTo(newReason.getPendingJobReasons());
        }

        PendingJobReasonsInfo newConstraintChange = newReasons.getLast();
        assertThat(newConstraintChange.getPendingJobReasons()).isNotNull();
        for (int r : newConstraintChange.getPendingJobReasons()) {
            // ensure that the battery constraint is not in the latest constraint change
            assertThat(r).isNotEqualTo(JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING);
        }
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_GET_PENDING_JOB_REASONS_HISTORY_API)
    @Test
    public void testPendingJobReasonsHistory_trimsToSize() throws Exception {
        if (!BatteryUtils.hasBattery()) {
            // Can't test while the device doesn't have battery
            return;
        }
        setBatteryState(false, 100);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(HOUR_IN_MILLIS)
                .setRequiresCharging(true)
                .build();
        mJobScheduler.schedule(jobInfo);

        List<PendingJobReasonsInfo> reasons = mJobScheduler.getPendingJobReasonsHistory(JOB_ID);
        assertThat(reasons).isNotNull();
        int initialSize = reasons.size();

        // trigger a constraint change 12 times (limit is 10 so trigger a little more)
        for (int i = 0; i < 12; i++) {
            setBatteryState(i % 2 == 0, 100); // flip state on odd/even
        }

        List<PendingJobReasonsInfo> newReasons = mJobScheduler.getPendingJobReasonsHistory(JOB_ID);
        assertThat(newReasons.size()).isGreaterThan(initialSize);
        assertThat(newReasons).hasSize(10);
    }

    @Test
    public void testRunUserInitiatedJobsPermissionRequirement() throws Exception {
        startAndKeepTestActivity();
        final boolean isAppOpPermission = isRunUserInitiatedJobsPermissionAppOp();
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        // Default is allowed.
        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_DEFAULT);
        assertThat(mJobScheduler.schedule(ji)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ERRORED);
        if (isAppOpPermission) {
            assertThrows(
                    "Successfully scheduled user-initiated job without permission",
                    Exception.class,
                    () -> mJobScheduler.schedule(ji));
        } else {
            assertThat(mJobScheduler.schedule(ji)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        }

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_ALLOWED);
        assertThat(mJobScheduler.schedule(ji)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        AppOpsUtils.setOpMode(MY_PACKAGE, OPSTR_RUN_USER_INITIATED_JOBS, MODE_IGNORED);
        // TODO(263159631): uncomment to enable testing this scenario
        // assertEquals(JobScheduler.RESULT_FAILURE, mJobScheduler.schedule(ji));
    }

    @RequiresFlagsEnabled(android.app.job.Flags.FLAG_ADD_TYPE_INFO_TO_WAKELOCK_TAG)
    @Test
    public void testJobWakelockTag() throws Exception {
        // Regular job
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(jobInfo);
        assertJobWakelockTag("*job*r");
        kTestEnvironment.readyToRun();

        // Expediated job
        jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).setExpedited(true).build();
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(jobInfo);
        assertJobWakelockTag("*job*e");
        kTestEnvironment.readyToRun();

        // UIDT job
        jobInfo =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(jobInfo);
        assertJobWakelockTag("*job*u");
        kTestEnvironment.readyToRun();
    }

    /**
     * @return {@code true} if the RUN_USER_INITIATED_JOBS is an appop permission.
     */
    private boolean isRunUserInitiatedJobsPermissionAppOp() {
        return TextUtils.equals(RUN_USER_INITIATED_JOBS,
                opToPermission(OP_RUN_USER_INITIATED_JOBS));
    }

    private void assertJobWakelockTag(String tagPrefix) throws Exception {
        final String jobWakelockTag = getJobWakelockTag();
        assertWithMessage("Job unexpected wakelock tag: %s", jobWakelockTag)
                .that(jobWakelockTag)
                .startsWith(tagPrefix);
    }

    private String getJobWakelockTag() throws Exception {
        return SystemUtil.runShellCommand(
                        "cmd jobscheduler get-job-wakelock-tag --user cur "
                                + kJobServiceComponent.getPackageName()
                                + " "
                                + JOB_ID)
                .trim();
    }
}
