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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.Intent;
import android.jobscheduler.MockJobService;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Tests related to created and reading JobWorkItem objects. */
@RunWith(AndroidJUnit4.class)
public class JobWorkItemTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = JobWorkItemTest.class.hashCode();
    private static final Intent TEST_INTENT = new Intent("some.random.action");

    @Test
    public void testAllInfoGivenToJob() throws Exception {
        final JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        final JobWorkItem expectedJwi = new JobWorkItem.Builder()
                .setIntent(TEST_INTENT)
                .setExtras(pb)
                .setEstimatedNetworkBytes(30, 20)
                .setMinimumNetworkChunkBytes(5)
                .build();
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(expectedJwi.getDeliveryCount()).isEqualTo(0);

        try (NetworkingHelper networkingHelper =
                     new NetworkingHelper(getInstrumentation(), getContext())) {
            networkingHelper.setAllNetworksEnabled(true);
            kTestEnvironment.setExpectedExecutions(1);
            kTestEnvironment.setExpectedWork(new MockJobService.TestWorkItem[]{
                    new MockJobService.TestWorkItem(TEST_INTENT)});
            kTestEnvironment.readyToWork();
            mJobScheduler.enqueue(jobInfo, expectedJwi);
            runSatisfiedJob(JOB_ID);
            assertWithMessage("Job didn't fire immediately")
                    .that(kTestEnvironment.awaitExecution())
                    .isTrue();
        }

        List<JobWorkItem> executedJwis = kTestEnvironment.getLastReceivedWork();
        assertThat(executedJwis).hasSize(1);

        final JobWorkItem actualJwi = executedJwis.get(0);
        assertThat(actualJwi.getDeliveryCount()).isEqualTo(1);

        final Intent actualIntent = actualJwi.getIntent();
        assertThat(actualIntent).isNotNull();
        assertThat(actualIntent.getAction()).isEqualTo(TEST_INTENT.getAction());

        final PersistableBundle extras = actualJwi.getExtras();
        assertThat(extras).isNotNull();
        assertThat(extras.keySet()).hasSize(1);
        assertThat(extras.getInt("random_key")).isEqualTo(42);
        assertThat(actualJwi.getEstimatedNetworkDownloadBytes()).isEqualTo(30);
        assertThat(actualJwi.getEstimatedNetworkUploadBytes()).isEqualTo(20);
        assertThat(actualJwi.getMinimumNetworkChunkBytes()).isEqualTo(5);
    }

    @Test
    public void testIntentOnlyItem_builder() {
        JobWorkItem jwi = new JobWorkItem.Builder().setIntent(TEST_INTENT).build();

        assertThat(jwi.getIntent()).isEqualTo(TEST_INTENT);
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getMinimumNetworkChunkBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();
    }

    @Test
    public void testIntentOnlyItem_ctor() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT);

        assertThat(jwi.getIntent()).isEqualTo(TEST_INTENT);
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getMinimumNetworkChunkBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();
    }

    @Test
    public void testItemWithEstimatedBytes_builder() {
        assertThrows(
                "Successfully created JobWorkItem with negative download bytes value",
                IllegalArgumentException.class,
                () -> new JobWorkItem.Builder().setEstimatedNetworkBytes(-10, 20).build());

        assertThrows(
                "Successfully created JobWorkItem with negative upload bytes value",
                IllegalArgumentException.class,
                () -> new JobWorkItem.Builder().setEstimatedNetworkBytes(10, -20).build());

        JobWorkItem jwi = new JobWorkItem.Builder().setEstimatedNetworkBytes(10, 20).build();

        assertThat(jwi.getIntent()).isNull();
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(10);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(20);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();
    }

    @Test
    public void testItemWithEstimatedBytes_ctor() {
        assertThrows(
                "Successfully created JobWorkItem with negative download bytes value",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, -10, 20));

        assertThrows(
                "Successfully created JobWorkItem with negative upload bytes value",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, 10, -20));

        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 20);

        assertThat(jwi.getIntent()).isEqualTo(TEST_INTENT);
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(10);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(20);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();
    }

    @Test
    public void testItemWithMinimumChunkBytes_builder() {
        JobWorkItem jwi = new JobWorkItem.Builder().setMinimumNetworkChunkBytes(3).build();

        assertThat(jwi.getIntent()).isNull();
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getMinimumNetworkChunkBytes()).isEqualTo(3);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();

        assertThrows(
                "Successfully created JobWorkItem with negative minimum chunk value",
                IllegalArgumentException.class,
                () -> new JobWorkItem.Builder().setMinimumNetworkChunkBytes(-3).build());

        assertThrows(
                "Successfully created JobWorkItem with 0 minimum chunk value",
                IllegalArgumentException.class,
                () -> new JobWorkItem.Builder().setMinimumNetworkChunkBytes(0).build());
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () ->
                        new JobWorkItem.Builder()
                                .setEstimatedNetworkBytes(10, 20)
                                .setMinimumNetworkChunkBytes(50)
                                .build());
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () ->
                        new JobWorkItem.Builder()
                                .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, 20)
                                .setMinimumNetworkChunkBytes(25)
                                .build());
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () ->
                        new JobWorkItem.Builder()
                                .setEstimatedNetworkBytes(10, JobInfo.NETWORK_BYTES_UNKNOWN)
                                .setMinimumNetworkChunkBytes(15)
                                .build());
    }

    @Test
    public void testItemWithMinimumChunkBytes_ctor() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 20, 3);

        assertThat(jwi.getIntent()).isEqualTo(TEST_INTENT);
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(10);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(20);
        assertThat(jwi.getMinimumNetworkChunkBytes()).isEqualTo(3);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);
        assertThat(jwi.getExtras().isEmpty()).isTrue();

        assertThrows(
                "Successfully created JobWorkItem with negative minimum chunk value",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, 10, 20, -3));
        assertThrows(
                "Successfully created JobWorkItem with 0 minimum chunk value",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, 10, 20, 0));
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, 10, 20, 50));
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, JobInfo.NETWORK_BYTES_UNKNOWN, 20, 25));
        assertThrows(
                "Successfully created JobWorkItem with minimum chunk value too large",
                IllegalArgumentException.class,
                () -> new JobWorkItem(TEST_INTENT, 10, JobInfo.NETWORK_BYTES_UNKNOWN, 15));
    }

    @Test
    public void testItemWithPersistableBundle() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobWorkItem jwi = new JobWorkItem.Builder().setExtras(pb).build();

        assertThat(jwi.getIntent()).isNull();
        assertThat(jwi.getEstimatedNetworkDownloadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getEstimatedNetworkUploadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(jwi.getMinimumNetworkChunkBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);

        final PersistableBundle extras = jwi.getExtras();
        assertThat(extras).isNotNull();
        assertThat(extras.keySet()).hasSize(1);
        assertThat(extras.getInt("random_key")).isEqualTo(42);

        assertThrows(
                "Successfully created null extras",
                IllegalArgumentException.class,
                () -> new JobWorkItem.Builder().setExtras(null).build());
    }

    @Test
    public void testDeliveryCountBumped() throws Exception {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT);
        // JobWorkItem hasn't been scheduled yet. Delivery count should be 0.
        assertThat(jwi.getDeliveryCount()).isEqualTo(0);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWork(new MockJobService.TestWorkItem[]{
                new MockJobService.TestWorkItem(TEST_INTENT)});
        kTestEnvironment.readyToWork();
        mJobScheduler.enqueue(jobInfo, jwi);
        runSatisfiedJob(JOB_ID);
        assertWithMessage("Job didn't fire immediately")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        List<JobWorkItem> executedJWIs = kTestEnvironment.getLastReceivedWork();
        assertThat(executedJWIs).hasSize(1);
        assertThat(executedJWIs.get(0).getDeliveryCount()).isEqualTo(1);
    }

    @Test
    public void testPersisted_withIntent() {
        JobWorkItem jwi = new JobWorkItem.Builder().setIntent(TEST_INTENT).build();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();
        assertThrows(
                "Successfully enqueued persisted JWI with intent",
                IllegalArgumentException.class,
                () -> mJobScheduler.enqueue(jobInfo, jwi));
    }

    @Test
    public void testPersisted_withPersistableBundle() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobWorkItem jwi = new JobWorkItem.Builder().setExtras(pb).build();
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();

        assertThat(mJobScheduler.enqueue(jobInfo, jwi)).isEqualTo(JobScheduler.RESULT_SUCCESS);
    }

    @Test
    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_download() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, JobInfo.NETWORK_BYTES_UNKNOWN);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
        assertThrows(
                "Successfully scheduled JobWorkItem with network implication"
                        + " and job with no network constraint",
                IllegalArgumentException.class,
                () -> mJobScheduler.enqueue(jobInfo, jwi));
    }

    @Test
    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_upload() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        assertThrows(
                "Successfully scheduled JobWorkItem with network implication"
                        + " and job with no network constraint",
                IllegalArgumentException.class,
                () -> mJobScheduler.enqueue(jobInfo, jwi));
    }

    @Test
    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint_minimumChunk() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT,
                JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        assertThrows(
                "Successfully scheduled JobWorkItem with network implication"
                        + "and job with no network constraint",
                IllegalArgumentException.class,
                () -> mJobScheduler.enqueue(jobInfo, jwi));
    }

    @Test
    public void testScheduleItemWithNetworkInfoAndNoNetworkConstraint() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT, 10, 10, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();

        assertThrows(
                "Successfully scheduled JobWorkItem with network implication"
                        + "and job with no network constraint",
                IllegalArgumentException.class,
                () -> mJobScheduler.enqueue(jobInfo, jwi));
    }

    @Test
    public void testScheduleItemWithNetworkInfoAndNetworkConstraint() {
        JobWorkItem jwi = new JobWorkItem(TEST_INTENT,
                JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, 10);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();

        assertThat(mJobScheduler.enqueue(jobInfo, jwi)).isEqualTo(JobScheduler.RESULT_SUCCESS);
    }
}
