/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.TargetApi;
import android.app.job.JobInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Schedules jobs with the {@link android.app.job.JobScheduler} that have storage constraints. */
@TargetApi(26)
@RunWith(AndroidJUnit4.class)
public final class StorageConstraintTest extends BaseJobSchedulerTest {
    /** Unique identifier for the job scheduled by this suite of tests. */
    private static final int STORAGE_JOB_ID = StorageConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(STORAGE_JOB_ID, kJobServiceComponent);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mJobScheduler.cancel(STORAGE_JOB_ID);
        super.tearDown();
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /** Schedule a job that requires the device storage is not low, when it is actually not low. */
    @Test
    public void testNotLowConstraintExecutes() throws Exception {
        setStorageStateLow(false);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true).build());
        assertJobReady(STORAGE_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with storage not low constraint did not fire when storage not low.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /** Schedule a job that requires the device storage is not low, when it actually is low. */
    @Test
    public void testNotLowConstraintFails() throws Exception {
        setStorageStateLow(true);

        kTestEnvironment.setExpectedExecutions(0);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true).build());
        assertJobWaiting(STORAGE_JOB_ID);
        assertJobNotReady(STORAGE_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with storage now low constraint fired while low.")
                .that(kTestEnvironment.awaitExecution(250))
                .isFalse();

        // And for good measure, ensure the job runs once storage is okay.
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        setStorageStateLow(false);
        assertJobReady(STORAGE_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with storage not low constraint did not fire when storage not low.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Test that a job that requires the device storage is not low is stopped when it becomes low.
     */
    @Test
    public void testJobStoppedWhenStorageLow() throws Exception {
        setStorageStateLow(false);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setExpectedWaitForRun();
        kTestEnvironment.setExpectedStopped();
        mJobScheduler.schedule(mBuilder.setRequiresStorageNotLow(true).build());
        assertJobReady(STORAGE_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with storage not low constraint did not fire when storage not low.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        setStorageStateLow(true);
        assertWithMessage(
                        "Job with storage not low constraint was not stopped when storage became"
                                + " low.")
                .that(kTestEnvironment.awaitStopped())
                .isTrue();
    }
}
