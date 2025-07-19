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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.compat.CompatChanges;
import android.app.job.Flags;
import android.app.job.JobInfo;
import android.content.ClipData;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Set;

/** Tests related to creating and reading JobInfo objects. */
@RunWith(AndroidJUnit4.class)
public class JobInfoTest extends BaseJobSchedulerTest {
    private static final String TAG = JobInfoTest.class.getSimpleName();
    private static final int JOB_ID = JobInfoTest.class.hashCode();

    private static final long REJECT_NEGATIVE_DELAYS_AND_DEADLINES = 323349338L;
    private static final long THROW_ON_UNSUPPORTED_BIAS_USAGE = 300477393L;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Override
    @After
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);

        // The super method should be called at the end.
        super.tearDown();
    }

    @Test
    public void testBackoffCriteria() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setBackoffCriteria(12345, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();

        assertThat(ji.getInitialBackoffMillis()).isEqualTo(12345);
        assertThat(ji.getBackoffPolicy()).isEqualTo(JobInfo.BACKOFF_POLICY_LINEAR);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setBackoffCriteria(54321, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();

        assertThat(ji.getInitialBackoffMillis()).isEqualTo(54321);
        assertThat(ji.getBackoffPolicy()).isEqualTo(JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testBatteryNotLow() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(true)
                .build();

        assertThat(ji.isRequireBatteryNotLow()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresBatteryNotLow(false)
                .build();
        assertThat(ji.isRequireBatteryNotLow()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testBias() throws Exception {
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);
        Method setBiasMethod = JobInfo.Builder.class.getDeclaredMethod("setBias", int.class);
        setBiasMethod.setAccessible(true);
        setBiasMethod.invoke(builder, 40);

        JobInfo ji = builder.build();
        // Confirm JobScheduler rejects the JobInfo object.
        // TODO(b/309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(THROW_ON_UNSUPPORTED_BIAS_USAGE)) {
            assertThrows(
                    "Successfully scheduled a job with a modified bias",
                    SecurityException.class,
                    () -> mJobScheduler.schedule(ji));
        } else {
            mJobScheduler.schedule(ji);
            assertWithMessage("Bias wasn't changed to default")
                    .that(getBias(mJobScheduler.getPendingJob(JOB_ID)))
                    .isEqualTo(0);
        }
    }

    @Test
    public void testCharging() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .build();

        assertThat(ji.isRequireCharging()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(false)
                .build();
        assertThat(ji.isRequireCharging()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testClipData() {
        final ClipData clipData = ClipData.newPlainText("test", "testText");
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setClipData(clipData, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .build();

        assertThat(ji.getClipData()).isEqualTo(clipData);
        assertThat(ji.getClipGrantFlags()).isEqualTo(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setClipData(null, 0)
                .build();
        assertThat(ji.getClipData()).isNull();
        assertThat(ji.getClipGrantFlags()).isEqualTo(0);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @RequiresFlagsEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS)
    @Test
    public void testDebugTags() {
        // Confirm defaults
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        assertThat(ji.getDebugTags()).hasSize(0);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag("a")
                .addDebugTag("b")
                .addDebugTag("c")
                .build();

        assertThat(ji.getDebugTags()).isEqualTo(Set.of("a", "b", "c"));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag("a")
                .addDebugTag("b")
                .addDebugTag("c")
                .removeDebugTag("b")
                .build();

        assertThat(ji.getDebugTags()).isEqualTo(Set.of("a", "c"));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Tag is at the character limit
        final String maxLengthDebugTag =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
                        + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addDebugTag(maxLengthDebugTag)
                .build();

        assertThat(ji.getDebugTags()).isEqualTo(Set.of(maxLengthDebugTag));
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        assertThrows(
                "Successfully built a JobInfo with a null debug tag",
                Exception.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).addDebugTag(null).build());
        assertThrows(
                "Successfully built a JobInfo with an empty debug tag",
                IllegalArgumentException.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).addDebugTag("").build());
        assertThrows(
                "Successfully built a JobInfo with a whitespace-only debug tag",
                IllegalArgumentException.class,
                () ->
                        new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                                .addDebugTag("        ")
                                .build());
        assertThrows(
                "Successfully built a JobInfo with a long debug tag",
                IllegalArgumentException.class,
                () ->
                        new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                                .setTraceTag(maxLengthDebugTag + "x")
                                .build());

        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent);
        for (int i = 0; i < 33; ++i) {
            jiBuilder.addDebugTag(Integer.toString(i));
        }
        assertBuildFails("Successfully built a JobInfo with too many debug tags", jiBuilder);
    }

    @Test
    public void testDeviceIdle() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresDeviceIdle(true)
                .build();

        assertThat(ji.isRequireDeviceIdle()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresDeviceIdle(false)
                .build();
        assertThat(ji.isRequireDeviceIdle()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testEstimatedNetworkBytes() {
        assertBuildFails(
                "Successfully built a JobInfo specifying estimated network bytes without"
                        + " requesting network",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setEstimatedNetworkBytes(500, 1000));

        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying a negative download bytes value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setEstimatedNetworkBytes(-500, JobInfo.NETWORK_BYTES_UNKNOWN));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying a negative upload bytes value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, -500));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(500, 1000)
                .build();
        assertThat(ji.getEstimatedNetworkDownloadBytes()).isEqualTo(500);
        assertThat(ji.getEstimatedNetworkUploadBytes()).isEqualTo(1000);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();
        assertThat(ji.getEstimatedNetworkDownloadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        assertThat(ji.getEstimatedNetworkUploadBytes()).isEqualTo(JobInfo.NETWORK_BYTES_UNKNOWN);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testExtras() {
        final PersistableBundle pb = new PersistableBundle();
        pb.putInt("random_key", 42);
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .setExtras(pb)
                .build();
        final PersistableBundle extras = ji.getExtras();

        assertThat(extras).isNotNull();
        assertThat(extras.keySet()).hasSize(1);
        assertThat(extras.getInt("random_key")).isEqualTo(42);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testExpeditedJob() {
        // Test all allowed constraints.
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .setPriority(JobInfo.PRIORITY_HIGH)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresStorageNotLow(true)
                .build();

        assertThat(ji.isExpedited()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm default priority for EJs.
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setExpedited(true)
                .build();
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_MAX);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Test disallowed constraints.
        final String failureMessage =
                "Successfully built an expedited JobInfo object with disallowed constraints";
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setMinimumLatency(100));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPeriodic(15 * 60_000));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPriority(JobInfo.PRIORITY_LOW));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPriority(JobInfo.PRIORITY_DEFAULT));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setPrefetch(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresDeviceIdle(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresBatteryNotLow(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setRequiresCharging(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setUserInitiated(true));

        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                Uri.parse("content://" + MediaStore.AUTHORITY + "/"),
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .addTriggerContentUri(tcu));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testImportantWhileForeground() {
        // Assert the value is false always
        final JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setImportantWhileForeground(true)
                .setPriority(JobInfo.PRIORITY_LOW)
                .build();

        assertThat(ji.isImportantWhileForeground()).isFalse();
        // No priority change.
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_LOW);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testMinimumChunkSizeBytes() {
        assertBuildFails(
                "Successfully built a JobInfo specifying minimum chunk bytes without"
                        + " requesting network",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumNetworkChunkBytes(500));
        try {
            assertBuildFails(
                    "Successfully built a JobInfo specifying minimum chunk bytes a negative value",
                    new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setMinimumNetworkChunkBytes(-500));
        } catch (IllegalArgumentException expected) {
            // Success. setMinimumNetworkChunkBytes() should throw the exception.
        }

        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(5, 5));
        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN, 5));
        assertBuildFails(
                "Successfully built a JobInfo with a higher minimum chunk size than total"
                        + " transfer size",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumNetworkChunkBytes(500)
                        .setEstimatedNetworkBytes(5, JobInfo.NETWORK_BYTES_UNKNOWN));

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumNetworkChunkBytes(500)
                .setEstimatedNetworkBytes(
                        JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN)
                .build();
        assertThat(ji.getMinimumNetworkChunkBytes()).isEqualTo(500);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testMinimumLatency() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(1337)
                .build();

        assertThat(ji.getMinLatencyMillis()).isEqualTo(1337);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testMinimumLatency_negative() {
        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(-1);

        // TODO(b/309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(REJECT_NEGATIVE_DELAYS_AND_DEADLINES)) {
            assertBuildFails("Successfully scheduled a job with a negative latency", jiBuilder);
        } else {
            // Confirm JobScheduler accepts the JobInfo object.
            JobInfo ji = jiBuilder.build();
            assertThat(ji.getMinLatencyMillis()).isEqualTo(0);
            mJobScheduler.schedule(ji);
        }
    }

    @Test
    public void testOverrideDeadline() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setOverrideDeadline(HOUR_IN_MILLIS)
                .build();

        // ...why are the set/get methods named differently?? >.>
        assertThat(ji.getMaxExecutionDelayMillis()).isEqualTo(HOUR_IN_MILLIS);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testOverrideDeadline_minimumTimeWindows() throws Exception {
        JobInfo.Builder jiBuilderShortFunctional = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .setMinimumLatency(MINUTE_IN_MILLIS)
                .setOverrideDeadline(16 * MINUTE_IN_MILLIS - 1);
        JobInfo.Builder jiBuilderShortNonfunctional =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(MINUTE_IN_MILLIS)
                        .setOverrideDeadline(16 * MINUTE_IN_MILLIS - 1);
        JobInfo.Builder jiBuilderLongFunctional = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresCharging(true)
                .setMinimumLatency(MINUTE_IN_MILLIS)
                .setOverrideDeadline(16 * MINUTE_IN_MILLIS);
        JobInfo.Builder jiBuilderLongNonfunctional =
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(MINUTE_IN_MILLIS)
                        .setOverrideDeadline(16 * MINUTE_IN_MILLIS);

        // Confirm JobScheduler accepts the JobInfo objects.
        mJobScheduler.schedule(jiBuilderShortFunctional.build());
        // Confirm JobScheduler accepts the good JobInfo objects.
        mJobScheduler.schedule(jiBuilderShortNonfunctional.build());
        mJobScheduler.schedule(jiBuilderLongFunctional.build());
        mJobScheduler.schedule(jiBuilderLongNonfunctional.build());
    }

    @Test
    public void testOverrideDeadline_negative() {
        JobInfo.Builder jiBuilder = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setOverrideDeadline(-1);

        // TODO(b/309023462): create separate tests for target SDK gated changes
        if (CompatChanges.isChangeEnabled(REJECT_NEGATIVE_DELAYS_AND_DEADLINES)) {
            assertBuildFails("Successfully scheduled a job with a negative deadline", jiBuilder);
        } else {
            // Confirm JobScheduler accepts the JobInfo object.
            JobInfo ji = jiBuilder.build();
            assertThat(ji.getMaxExecutionDelayMillis()).isAtLeast(0);
            mJobScheduler.schedule(ji);
        }
    }

    @Test
    public void testPeriodic() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPeriodic(60 * 60 * 1000L)
                .build();

        assertThat(ji.isPeriodic()).isTrue();
        assertThat(ji.getIntervalMillis()).isEqualTo(60 * 60 * 1000L);
        assertThat(ji.getFlexMillis()).isEqualTo(60 * 60 * 1000L);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPeriodic(120 * 60 * 1000L, 20 * 60 * 1000L)
                .build();
        assertThat(ji.isPeriodic()).isTrue();
        assertThat(ji.getIntervalMillis()).isEqualTo(120 * 60 * 1000L);
        assertThat(ji.getFlexMillis()).isEqualTo(20 * 60 * 1000L);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testPersisted() {
        // Assert the default value is false
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();

        assertThat(ji.isPersisted()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(true)
                .build();
        assertThat(ji.isPersisted()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPersisted(false)
                .build();
        assertThat(ji.isPersisted()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testPrefetch() {
        // Assert the default value is false
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();

        assertThat(ji.isPrefetch()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPrefetch(true)
                .build();
        assertThat(ji.isPrefetch()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPrefetch(false)
                .build();
        assertThat(ji.isPrefetch()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60_000L)
                .setPrefetch(true)
                .build();
        assertThat(ji.isPrefetch()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // CTS naturally targets latest SDK version. Compat change should be enabled by default.
        assertBuildFails("Modern prefetch jobs can't have a deadline",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setMinimumLatency(60_000L)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS)
                        .setPrefetch(true));
    }

    @Test
    public void testPriority() {
        // Assert the default value is DEFAULT
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_DEFAULT);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_LOW)
                .build();
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_LOW);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_MIN)
                .build();
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_MIN);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Attempt an invalid number
        // It's over 9000!!!
        assertThrows(
                "Successfully built a job with a large priority level",
                IllegalArgumentException.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(9001).build());
        // It's negative priority.
        assertThrows(
                "Successfully built a job with a negative priority level",
                IllegalArgumentException.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(-1).build());
        // Wrong priority level.
        assertThrows(
                "Successfully built a job with an invalid priority level",
                IllegalArgumentException.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).setPriority(123).build());

        // Test other invalid configurations.
        final String invalidConfigurationFailureMessage =
                "Successfully built a JobInfo object with disallowed priority configurations";
        assertBuildFails(
                invalidConfigurationFailureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_MAX));
        assertBuildFails(
                invalidConfigurationFailureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPrefetch(true));
        assertBuildFails(
                invalidConfigurationFailureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPriority(JobInfo.PRIORITY_HIGH)
                        .setPeriodic(JobInfo.getMinPeriodMillis()));
    }

    @Test
    public void testRequiredNetwork() {
        final NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetwork(nr)
                .build();

        assertThat(ji.getRequiredNetwork()).isEqualTo(nr);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetwork(null)
                .build();
        assertThat(ji.getRequiredNetwork()).isNull();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testRequiredNetworkType() {
        // Assert the default value is NONE
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .build();

        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_NONE);
        assertThat(ji.getRequiredNetwork()).isNull();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_ANY);
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                .isFalse();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build();
        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_UNMETERED);
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                .isFalse();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                .build();
        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_NOT_ROAMING);
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                .isFalse();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                .build();
        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_CELLULAR);
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .isTrue();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                .isFalse();
        assertThat(
                        ji.getRequiredNetwork()
                                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        assertThat(ji.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_NONE);
        assertThat(ji.getRequiredNetwork()).isNull();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testStorageNotLow() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(true)
                .build();

        assertThat(ji.isRequireStorageNotLow()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setRequiresStorageNotLow(false)
                .build();
        assertThat(ji.isRequireStorageNotLow()).isFalse();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @RequiresFlagsEnabled(Flags.FLAG_JOB_DEBUG_INFO_APIS)
    @Test
    public void testTraceTag() {
        // Confirm defaults
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        assertThat(ji.getTraceTag()).isNull();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTraceTag("tracing").build();
        assertThat(ji.getTraceTag()).isEqualTo("tracing");
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Tag is at the character limit
        final String maxLengthTraceTag =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
                        + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTraceTag(maxLengthTraceTag)
                .build();
        assertThat(ji.getTraceTag()).isEqualTo(maxLengthTraceTag);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTraceTag(null)
                .build();
        assertThat(ji.getTraceTag()).isNull();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
        assertThrows(
                "Successfully built a JobInfo with an empty trace tag",
                IllegalArgumentException.class,
                () -> new JobInfo.Builder(JOB_ID, kJobServiceComponent).setTraceTag("").build());

        assertThrows(
                "Successfully built a JobInfo with a whitespace-only trace tag",
                IllegalArgumentException.class,
                () ->
                        new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                                .setTraceTag("        ")
                                .build());
        assertThrows(
                "Successfully built a JobInfo with a long trace tag",
                IllegalArgumentException.class,
                () ->
                        new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                                .setTraceTag(maxLengthTraceTag + "x")
                                .build());
    }

    @Test
    public void testTransientExtras() {
        final Bundle b = new Bundle();
        b.putBoolean("random_bool", true);
        assertBuildFails("Successfully built a persisted JobInfo object with transient extras",
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setPersisted(true)
                        .setTransientExtras(b));

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTransientExtras(b)
                .build();
        assertThat(ji.getTransientExtras().size()).isEqualTo(b.size());
        for (String key : b.keySet()) {
            assertThat(ji.getTransientExtras().get(key)).isEqualTo(b.get(key));
        }
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testTriggerContentMaxDelay() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTriggerContentMaxDelay(1337)
                .build();

        assertThat(ji.getTriggerContentMaxDelay()).isEqualTo(1337);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testTriggerContentUpdateDelay() {
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setTriggerContentUpdateDelay(1337)
                .build();

        assertThat(ji.getTriggerContentUpdateDelay()).isEqualTo(1337);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testTriggerContentUri() {
        final Uri u = Uri.parse("content://" + MediaStore.AUTHORITY + "/");
        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                u, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);

        assertThat(tcu.getUri()).isEqualTo(u);
        assertThat(tcu.getFlags()).isEqualTo(JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);

        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(tcu)
                .build();
        assertThat(ji.getTriggerContentUris()).hasLength(1);
        assertThat(ji.getTriggerContentUris()[0]).isEqualTo(tcu);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        final Uri u2 = Uri.parse("content://" + ContactsContract.AUTHORITY + "/");
        final JobInfo.TriggerContentUri tcu2 = new JobInfo.TriggerContentUri(u2, 0);
        assertThat(tcu2.getUri()).isEqualTo(u2);
        assertThat(tcu2.getFlags()).isEqualTo(0);
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .addTriggerContentUri(tcu)
                .addTriggerContentUri(tcu2)
                .build();
        assertThat(ji.getTriggerContentUris()).hasLength(2);
        assertThat(ji.getTriggerContentUris()[0]).isEqualTo(tcu);
        assertThat(ji.getTriggerContentUris()[1]).isEqualTo(tcu2);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);
    }

    @Test
    public void testUserInitiatedJob() {
        // Test all allowed constraints.
        JobInfo ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPriority(JobInfo.PRIORITY_MAX)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build();

        assertThat(ji.isUserInitiated()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm default priority for UIJs.
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        assertThat(ji.getPriority()).isEqualTo(JobInfo.PRIORITY_MAX);
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Confirm linear backoff allowed
        ji = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        assertThat(ji.isUserInitiated()).isTrue();
        // Confirm JobScheduler accepts the JobInfo object.
        mJobScheduler.schedule(ji);

        // Test disallowed constraints.
        final String failureMessage =
                "Successfully built a user-initiated JobInfo object with disallowed constraints";
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(100));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setOverrideDeadline(24 * HOUR_IN_MILLIS));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(15 * 60_000));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_LOW));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_HIGH));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPriority(JobInfo.PRIORITY_DEFAULT));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPrefetch(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setRequiresDeviceIdle(true));
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setExpedited(true)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY));

        final JobInfo.TriggerContentUri tcu = new JobInfo.TriggerContentUri(
                Uri.parse("content://" + MediaStore.AUTHORITY + "/"),
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        assertBuildFails(failureMessage,
                new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                        .setUserInitiated(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .addTriggerContentUri(tcu));
    }

    private void assertBuildFails(String message, JobInfo.Builder builder) {
        assertThrows(message, IllegalArgumentException.class, builder::build);
    }

    private int getBias(JobInfo job) throws Exception {
        Method getBiasMethod = JobInfo.class.getDeclaredMethod("getBias");
        getBiasMethod.setAccessible(true);

        return (Integer) getBiasMethod.invoke(job);
    }

}
