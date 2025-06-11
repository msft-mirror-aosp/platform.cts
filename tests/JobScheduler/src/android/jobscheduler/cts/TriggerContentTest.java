/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.ContentResolver;
import android.content.Context;
import android.jobscheduler.DummyJobContentProvider;
import android.jobscheduler.TriggerContentJobService;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Schedules jobs that look for content URI changes and ensures they are triggered correctly. */
@TargetApi(23)
@RunWith(AndroidJUnit4.class)
public final class TriggerContentTest extends BaseJobSchedulerTest {
    public static final int TRIGGER_CONTENT_JOB_ID = TriggerContentTest.class.hashCode();

    // The root URI of the media provider, to monitor for generic changes to its content.
    static final Uri MEDIA_URI = Uri.parse("content://" + MediaStore.AUTHORITY + "/");

    // Media URI for all external media content.
    private static final Uri MEDIA_EXTERNAL_URI =
            Uri.parse("content://" + MediaStore.AUTHORITY + "/external");

    // This is the external storage directory where cameras place pictures.
    private static final String DCIM_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();

    private static final String PIC_1_NAME = "TriggerContentTest1_" + Process.myPid();
    private static final String PIC_2_NAME = "TriggerContentTest2_" + Process.myPid();

    private final File[] mActiveFiles = new File[5];
    private final Uri[] mActiveUris = new Uri[5];

    @Override
    @After
    public void tearDown() throws Exception {
        for (int i = 0; i < mActiveFiles.length; i++) {
            cleanupActive(i);
        }
        super.tearDown();
    }

    @Test
    public void testDescendantsObserver() throws Exception {
        String base = "content://" + DummyJobContentProvider.AUTHORITY + "/root";
        Uri uribase = Uri.parse(base);
        Uri uri1 = Uri.parse(base + "/sub1");
        Uri uri2 = Uri.parse(base + "/sub2");

        // Start watching.
        JobInfo triggerJob =
                makeJobInfo(uribase, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Report changes.
        getContext().getContentResolver().notifyChange(uribase, null, 0);
        getContext().getContentResolver().notifyChange(uri1, null, 0);

        // Wait and check results
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertWithMessage("Timed out waiting for trigger content.").that(executed).isTrue();

        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(2, uris);
        assertHasUri(uribase, uris);
        assertHasUri(uri1, uris);
        String[] auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(DummyJobContentProvider.AUTHORITY);

        // Report more changes, this time not letting it see the top-level change
        getContext().getContentResolver().notifyChange(uribase, null,
                ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS);
        getContext().getContentResolver().notifyChange(uri2, null, 0);

        // Wait for the job to wake up and verify it saw the change.
        assertWithMessage("Timed out waiting for trigger content.")
                .that(kTriggerTestEnvironment.awaitExecution())
                .isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(uri2);
        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(DummyJobContentProvider.AUTHORITY);
    }

    @Test
    public void testNonDescendantsObserver() throws Exception {
        String base = "content://" + DummyJobContentProvider.AUTHORITY + "/root";
        Uri uribase = Uri.parse(base);
        Uri uri1 = Uri.parse(base + "/sub1");
        Uri uri2 = Uri.parse(base + "/sub2");

        // Start watching.
        JobInfo triggerJob = makeJobInfo(uribase, 0);
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Report changes.
        getContext().getContentResolver().notifyChange(uribase, null, 0);
        getContext().getContentResolver().notifyChange(uri1, null, 0);

        // Wait and check results
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertWithMessage("Timed out waiting for trigger content.").that(executed).isTrue();
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(uribase);

        String[] auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(DummyJobContentProvider.AUTHORITY);

        // Report more changes, this time not letting it see the top-level change
        getContext().getContentResolver().notifyChange(uribase, null,
                ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS);
        getContext().getContentResolver().notifyChange(uri2, null, 0);

        // Wait for the job to wake up and verify it saw the change.
        assertWithMessage("Timed out waiting for trigger content.")
                .that(kTriggerTestEnvironment.awaitExecution())
                .isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(uribase);

        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(DummyJobContentProvider.AUTHORITY);
    }

    @Test
    public void testPhotoAdded_Reschedule() throws Exception {
        JobInfo triggerJob = makePhotosJobInfo();

        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Create a file that our job should see.
        makeActiveFile(0, new File(DCIM_DIR, PIC_1_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertThat(mActiveUris[0]).isNotNull();

        // Wait for the job to wake up with the change and verify it.
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertWithMessage("Timed out waiting for trigger content.").that(executed).isTrue();
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        for (Uri uri : uris) {
            assertUriDescendant(MEDIA_URI, uri);
        }
        String[] auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);

        // While the job is still running, create another file it should see.
        // (This tests that it will see changes that happen before the next job
        // is scheduled.)
        makeActiveFile(1, new File(DCIM_DIR, PIC_2_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertThat(mActiveUris[1]).isNotNull();

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertWithMessage("Timed out waiting for trigger content.").that(executed).isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        for (Uri uri : uris) {
            assertUriDescendant(MEDIA_URI, uri);
        }
        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);

        // Schedule a new job to look at what we see when deleting the files.
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(TriggerContentJobService.TestEnvironment.MODE_ONESHOT,
                triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Delete the files.  Note that this will result in a general change, not for specific URIs.
        cleanupActive(0);
        cleanupActive(1);

        // Wait for the job to wake up and verify it saw the change.
        assertWithMessage("Timed out waiting for trigger content.")
                .that(kTriggerTestEnvironment.awaitExecution())
                .isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        for (Uri uri : uris) {
            assertUriDescendant(MEDIA_URI, uri);
        }
        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);
    }

    // Doesn't work.  Should it?
    public void xxxtestPhotoAdded_FinishTrue() throws Exception {
        JobInfo triggerJob = makePhotosJobInfo();

        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_FINISH_TRUE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Create a file that our job should see.
        makeActiveFile(0, new File(DCIM_DIR, PIC_1_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertThat(mActiveUris[0]).isNotNull();

        // Wait for the job to wake up with the change and verify it.
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertWithMessage("Timed out waiting for trigger content.").that(executed).isTrue();
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(mActiveUris[0]);
        String[] auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);

        // While the job is still running, create another file it should see.
        // (This tests that it will see changes that happen before the next job
        // is scheduled.)
        makeActiveFile(1, new File(DCIM_DIR, PIC_2_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertThat(mActiveUris[1]).isNotNull();

        // Wait for the job to wake up and verify it saw the change.
        assertWithMessage("Timed out waiting for trigger content.")
                .that(kTriggerTestEnvironment.awaitExecution())
                .isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(mActiveUris[1]);
        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);

        // Schedule a new job to look at what we see when deleting the files.
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(TriggerContentJobService.TestEnvironment.MODE_ONESHOT,
                triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Delete the files.  Note that this will result in a general change, not for specific URIs.
        cleanupActive(0);
        cleanupActive(1);

        // Wait for the job to wake up and verify it saw the change.
        assertWithMessage("Timed out waiting for trigger content.")
                .that(kTriggerTestEnvironment.awaitExecution())
                .isTrue();
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertThat(uris[0]).isEqualTo(MEDIA_EXTERNAL_URI);
        auths = params.getTriggeredContentAuthorities();
        assertThat(auths).hasLength(1);
        assertThat(auths[0]).isEqualTo(MediaStore.AUTHORITY);
    }

    private static final class MediaScanner
            implements MediaScannerConnection.OnScanCompletedListener {
        private static final long DEFAULT_TIMEOUT_MILLIS = 1000L; // 1 second.

        private CountDownLatch mLatch;
        private Uri mScannedUri;

        public boolean scan(Context context, String file, String mimeType)
                throws InterruptedException {
            mLatch = new CountDownLatch(1);
            MediaScannerConnection.scanFile(
                    context, new String[] {file}, new String[] {mimeType}, this);
            return mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        public Uri getScannedUri() {
            synchronized (this) {
                return mScannedUri;
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            synchronized (this) {
                mScannedUri = uri;
                mLatch.countDown();
            }
        }
    }

    private JobInfo makeJobInfo(Uri uri, int flags) {
        final JobInfo.Builder builder =
                new JobInfo.Builder(TRIGGER_CONTENT_JOB_ID, kTriggerContentServiceComponent);

        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(uri, flags));
        // For testing purposes, react quickly.
        builder.setTriggerContentUpdateDelay(500);
        builder.setTriggerContentMaxDelay(500);
        return builder.build();
    }

    private JobInfo makePhotosJobInfo() {
        final JobInfo.Builder builder =
                new JobInfo.Builder(TRIGGER_CONTENT_JOB_ID, kTriggerContentServiceComponent);

        // Look for general reports of changes in the overall provider.
        builder.addTriggerContentUri(
                new JobInfo.TriggerContentUri(
                        MEDIA_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
        // For testing purposes, react quickly.
        builder.setTriggerContentUpdateDelay(500);
        builder.setTriggerContentMaxDelay(500);
        return builder.build();
    }

    private static void copyToFileOrThrow(InputStream inputStream, File destFile)
            throws IOException {
        if (destFile.exists()) {
            destFile.delete();
        }
        destFile.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(destFile);
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
        } finally {
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException e) {
                // expected
            }
            out.close();
            inputStream.close();
        }
    }

    private Uri createAndAddImage(File destFile, InputStream image)
            throws IOException, InterruptedException {
        copyToFileOrThrow(image, destFile);
        MediaScanner scanner = new MediaScanner();
        boolean success = scanner.scan(getContext(), destFile.toString(), "image/jpeg");
        if (success) {
            return scanner.getScannedUri();
        }
        return null;
    }

    private Uri makeActiveFile(int which, File file, InputStream source)
            throws IOException, InterruptedException {
        mActiveFiles[which] = file;
        mActiveUris[which] = createAndAddImage(file, source);
        return mActiveUris[which];
    }

    private static void assertHasUri(Uri wanted, Uri[] uris) {
        for (Uri uri : uris) {
            if (wanted.equals(uri)) {
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Don't have uri ");
        sb.append(wanted);
        sb.append(" in: ");
        for (int i = 0; i < uris.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(uris[i]);
        }
        assertWithMessage(sb.toString()).fail();
    }

    private static void assertUriArrayLength(int length, Uri[] uris) {
        if (uris.length != length) {
            StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(length);
            sb.append(" URI, got ");
            sb.append(uris.length);
            if (uris.length > 0) {
                sb.append(": ");
                for (int i = 0; i < uris.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(uris[i]);
                }
            }
            assertWithMessage(sb.toString()).fail();
        }
    }

    private static void assertUriDescendant(Uri expected, Uri actual) {
        assertThat(actual.getScheme()).isEqualTo(expected.getScheme());
        assertThat(actual.getAuthority()).isEqualTo(expected.getAuthority());

        final List<String> expectedPath = expected.getPathSegments();
        final List<String> actualPath = actual.getPathSegments();
        for (int i = 0; i < expectedPath.size(); i++) {
            assertThat(actualPath.get(i)).isEqualTo(expectedPath.get(i));
        }
    }

    private void cleanupActive(int which) {
        if (mActiveUris[which] != null) {
            getContext().getContentResolver().delete(mActiveUris[which], null, null);
            mActiveUris[which] = null;
        }
        if (mActiveFiles[which] != null) {
            mActiveFiles[which].delete();
            mActiveFiles[which] = null;
        }
    }
}
