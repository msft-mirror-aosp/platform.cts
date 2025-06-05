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

import static android.app.job.Flags.FLAG_HANDLE_ABANDONED_JOBS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests related to JobParameters objects. */
@RunWith(AndroidJUnit4.class)
public class JobParametersTest extends BaseJobSchedulerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int JOB_ID = JobParametersTest.class.hashCode();

    private NetworkingHelper mNetworkingHelper;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mNetworkingHelper.tearDown();
        super.tearDown();
    }

    @Test
    public void testClipData() throws Exception {
        final ClipData clipData = ClipData.newPlainText("test", "testText");
        final int grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        final JobInfo ji =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setClipData(clipData, grantFlags)
                        .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        final JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getClipData().getItemCount()).isEqualTo(clipData.getItemCount());
        assertThat(params.getClipData().getItemAt(0).getText().toString())
                .isEqualTo(clipData.getItemAt(0).getText().toString());
        assertThat(params.getClipGrantFlags()).isEqualTo(grantFlags);
    }

    @Test
    public void testExtras() throws Exception {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        final JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).setExtras(pb).build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        final PersistableBundle extras = kTestEnvironment.getLastStartJobParameters().getExtras();
        assertThat(extras).isNotNull();
        assertThat(extras.keySet()).hasSize(1);
        assertThat(extras.getInt("random_key")).isEqualTo(42);
    }

    @Test
    public void testExpedited() throws Exception {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        assertThat(kTestEnvironment.getLastStartJobParameters().isExpeditedJob()).isTrue();

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(false)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
        assertThat(kTestEnvironment.getLastStartJobParameters().isExpeditedJob()).isFalse();
    }

    @Test
    public void testUserInitiated() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        startAndKeepTestActivity();
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        assertThat(kTestEnvironment.getLastStartJobParameters().isUserInitiatedJob()).isTrue();

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(false)
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
        assertThat(kTestEnvironment.getLastStartJobParameters().isUserInitiatedJob()).isFalse();
    }

    @Test
    public void testJobId() throws Exception {
        final JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
        assertThat(kTestEnvironment.getLastStartJobParameters().getJobId()).isEqualTo(JOB_ID);
    }

    @Test
    public void testNamespaceJobParameters() throws Exception {
        final JobScheduler jsA = mJobScheduler.forNamespace("A");
        final JobScheduler jsB = mJobScheduler.forNamespace("B");
        final JobInfo jobA =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent).setExpedited(true).build();
        final JobInfo jobB =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiresStorageNotLow(true)
                        .build();

        kTestEnvironment.setExpectedExecutions(1);
        setStorageStateLow(true);
        assertThat(jsA.schedule(jobA)).isEqualTo(JobScheduler.RESULT_SUCCESS);
        assertThat(jsB.schedule(jobB)).isEqualTo(JobScheduler.RESULT_SUCCESS);

        runSatisfiedJob(JOB_ID, "A");
        runSatisfiedJob(JOB_ID, "B");
        assertWithMessage("Job A didn't fire").that(kTestEnvironment.awaitExecution()).isTrue();
        JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getJobNamespace()).isEqualTo("A");

        kTestEnvironment.setExpectedExecutions(1);
        setStorageStateLow(false);
        runSatisfiedJob(JOB_ID, "A");
        runSatisfiedJob(JOB_ID, "B");
        assertWithMessage("Job B didn't fire").that(kTestEnvironment.awaitExecution()).isTrue();
        params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getJobNamespace()).isEqualTo("B");
    }

    // JobParameters.getNetwork() tested in ConnectivityConstraintTest.

    @Test
    public void testStopReason() throws Exception {
        verifyStopReason(new JobInfo.Builder(JOB_ID, kJobServiceComponent).build(),
                JobParameters.STOP_REASON_TIMEOUT,
                () -> SystemUtil.runShellCommand(getInstrumentation(),
                        "cmd jobscheduler timeout"
                                + " -u " + UserHandle.myUserId()
                                + " " + kJobServiceComponent.getPackageName()
                                + " " + JOB_ID));

        if (BatteryUtils.hasBattery()) {
            setBatteryState(false, 100);
            verifyStopReason(new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiresBatteryNotLow(true).build(),
                    JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW,
                    () -> setBatteryState(false, 5));

            setBatteryState(true, 100);
            verifyStopReason(new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiresCharging(true).build(),
                    JobParameters.STOP_REASON_CONSTRAINT_CHARGING,
                    () -> setBatteryState(false, 100));
        }

        setStorageStateLow(false);
        verifyStopReason(new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiresStorageNotLow(true).build(),
                JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW,
                () -> setStorageStateLow(true));
    }

    @RequiresFlagsEnabled(FLAG_HANDLE_ABANDONED_JOBS)
    @Test
    public void testStopReasonAbandonedJob() throws Exception {
        verifyStopReason(
                new JobInfo.Builder(JOB_ID, kJobServiceComponent).build(),
                JobParameters.STOP_REASON_TIMEOUT_ABANDONED,
                () -> SystemUtil.runShellCommand(getInstrumentation(),
                        "cmd jobscheduler stop -s "
                                + JobParameters.STOP_REASON_TIMEOUT_ABANDONED + " -i "
                                + JobParameters.INTERNAL_STOP_REASON_TIMEOUT_ABANDONED
                                + " -u " + UserHandle.myUserId()
                                + " " + kJobServiceComponent.getPackageName()
                                + " " + JOB_ID));
    }

    @Test
    public void testTransientExtras() throws Exception {
        final Bundle b = new Bundle();
        b.putBoolean("random_bool", true);
        final JobInfo ji =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTransientExtras(b).build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getTransientExtras().size()).isEqualTo(b.size());
        for (String key : b.keySet()) {
            assertThat(params.getTransientExtras().get(key)).isEqualTo(b.get(key));
        }
    }

    // JobParameters.getTriggeredContentAuthorities() tested in TriggerContentTest.
    // JobParameters.getTriggeredContentUris() tested in TriggerContentTest.
    // JobParameters.isOverrideDeadlineExpired() tested in TimingConstraintTest.

    private void verifyStopReason(JobInfo ji, int stopReason, ExceptionRunnable stopCode)
            throws Exception {
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setExpectedStopped();
        mJobScheduler.schedule(ji);
        runSatisfiedJob(ji.getId());
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertThat(params.getStopReason()).isEqualTo(JobParameters.STOP_REASON_UNDEFINED);

        stopCode.run();
        assertWithMessage("Job didn't stop immediately")
                .that(kTestEnvironment.awaitStopped())
                .isTrue();
        params = kTestEnvironment.getLastStopJobParameters();
        assertThat(params.getStopReason()).isEqualTo(stopReason);
    }

    private interface ExceptionRunnable {
        void run() throws Exception;
    }
}
