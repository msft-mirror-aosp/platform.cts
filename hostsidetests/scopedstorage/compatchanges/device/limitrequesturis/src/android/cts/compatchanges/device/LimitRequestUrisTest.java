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

package android.cts.compatchanges.device.limitrequesturis;

import static android.scopedstorage.cts.lib.TestUtils.doEscalation;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.compat.CompatChanges;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(AndroidJUnit4.class)
public class LimitRequestUrisTest {
    private static final long LIMIT_CREATE_REQUEST_URIS = 203408344L;

    private static final ContentResolver sContentResolver = getContentResolver();

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final Context sContext = sInstrumentation.getContext();

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        "android.permission.LOG_COMPAT_CHANGE",
                        "android.permission.READ_COMPAT_CHANGE_CONFIG");
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void verifyCompatChangeIsEnabled() {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
    }

    @Test
    public void verifyCompatChangeIsDisabled() {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateWriteRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        assumeTrue(sContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA);

        final int numFiles = 2010;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createWriteRequest(sContentResolver, uris));
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateDeleteRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        assumeTrue(sContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA);

        final int numFiles = 2010;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createDeleteRequest(sContentResolver, uris));
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateFavoriteRequestLimitUris_throwsIllegalArgumentException()
            throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        assumeTrue(sContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA);

        final int numFiles = 2010;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createFavoriteRequest(sContentResolver, uris, true));
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateTrashRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        assumeTrue(sContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA);

        final int numFiles = 2010;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createTrashRequest(sContentResolver, uris, true));
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateWriteRequestLimitUris_success() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        final int numFiles = 2000;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            PendingIntent pi = MediaStore.createWriteRequest(sContentResolver, uris);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateDeleteRequestLimitUris_success() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        final int numFiles = 2000;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            PendingIntent pi = MediaStore.createDeleteRequest(sContentResolver, uris);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);
        } finally {
            // Assert that files are deleted from the Mediaprovider database
            try (Cursor c =
                    sContentResolver.query(
                            uris.iterator().next(),
                            new String[] {MediaColumns.DATA},
                            null,
                            null,
                            null)) {
                assertThat(c.getCount()).isEqualTo(0);
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateFavoriteRequestLimitUris_success() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        final int numFiles = 2000;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            PendingIntent pi = MediaStore.createFavoriteRequest(sContentResolver, uris, true);
            assertNotNull(pi);
            doEscalation(pi);
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testCreateTrashRequestLimitUris_success() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(LIMIT_CREATE_REQUEST_URIS)).isTrue();
        final int numFiles = 2000;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            PendingIntent pi = MediaStore.createTrashRequest(sContentResolver, uris, true);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);
        } finally {
            deleteTestFiles(uris);
        }
    }

    private Collection<Uri> createTestFiles(int numFiles) {
        Collection<Uri> uris = new ArrayList<Uri>();

        for (int i = 0; i < numFiles; i++) {
            final ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "testFile_" + i + ".png");
            Uri fileUri =
                    sContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            uris.add(fileUri);
        }

        assertThat(uris.size()).isEqualTo(numFiles);

        // Assert that files are inserted in the Mediaprovider database
        try (Cursor c =
                sContentResolver.query(
                        uris.iterator().next(),
                        new String[] {MediaColumns.DATA},
                        null,
                        null,
                        null)) {
            assertThat(c.getCount()).isEqualTo(1);
        }

        return uris;
    }

    private void deleteTestFiles(Collection<Uri> uris) throws Exception {
        // Delete all files created during the test
        String authority = uris.iterator().next().getAuthority();
        assertThat(authority).isNotNull();

        ArrayList<ContentProviderOperation> deleteOps = new ArrayList<>();
        for (Uri uri : uris) {
            deleteOps.add(ContentProviderOperation.newDelete(uri).build());
        }

        ContentProviderResult[] contentProviderResults =
                sContentResolver.applyBatch(authority, deleteOps);
        assertThat(contentProviderResults).isNotNull();
        assertThat(contentProviderResults.length).isEqualTo(uris.size());
    }
}
