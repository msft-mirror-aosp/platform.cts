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

package android.jobscheduler.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.job.Flags;
import android.app.job.JobInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_JOB_CATEGORY_APIS)
@RunWith(AndroidJUnit4.class)
public final class JobCategoryTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = 555;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testSetGetJobCategory() {
        {
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();
            assertThat(jobInfo.getCategory()).isEqualTo(JobInfo.CATEGORY_UNKNOWN);
        }
        {
            JobInfo jobInfo =
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setCategory(JobInfo.CATEGORY_BACKUP)
                            .build();
            assertThat(jobInfo.getCategory()).isEqualTo(JobInfo.CATEGORY_BACKUP);
        }
    }

    @Test
    public void testSetJobCategory_invalidCategory() {
        {
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);
            assertThrows(IllegalArgumentException.class, () -> builder.setCategory(-1));
        }
        {
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> builder.setCategory(JobInfo.CATEGORY_MAX + 1));
        }
    }
}
