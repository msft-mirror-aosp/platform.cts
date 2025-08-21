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

package android.cts.compatchanges.device;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
import static android.scopedstorage.cts.device.StorageUtils.GrantModifications.GRANT;
import static android.scopedstorage.cts.device.StorageUtils.GrantModifications.REVOKE;
import static android.scopedstorage.cts.device.StorageUtils.getResultForFilesQuery;
import static android.scopedstorage.cts.device.StorageUtils.modifyReadAccess;
import static android.scopedstorage.cts.lib.TestUtils.executeShellCommand;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class OwnedPhotosTest {
    private static final long OWNED_PHOTOS_CHANGE_ID = 310703690L;
    private static final String THIS_PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();
    private static final ContentResolver sContentResolver = getContentResolver();

    /** Inits test with correct permissions. */
    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
    }

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
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isTrue();
    }

    @Test
    public void verifyCompatChangeIsDisabled() {
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRevokeOwnershipWhenOwnedPhotosEnabled() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isTrue();
        File testFile = new File(getDcimDir(), "testFile" + System.nanoTime() + ".jpg");
        testFile.createNewFile();
        try {
            /* Normal scenario, all files created by the app is owned by the app. */
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(1, c.getCount());
            }

            /*
             * Revoke access from testFile, of one of the owned files.
             * This will set owner_package_name as null in files table for this file.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(0, c.getCount());
            }

            /*
             * Grant access to testFile, not a owned file as access was previously revoked.
             * This will add entry in media_grants and give read access to this package for this
             * file. However, files table should still have owner_package_name as null.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, GRANT);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(1, c.getCount());
            }
        } finally {
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            executeShellCommand("rm " + testFile);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRevokeOwnershipWhenOwnedPhotosDisabled() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isFalse();
        File testFile = new File(getDcimDir(), "testFile" + System.nanoTime() + ".jpg");
        testFile.createNewFile();
        try {
            /* Normal scenario, all files created by the app is owned by the app. */
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(1, c.getCount());
            }

            /*
             * Try to revoke access from testFile, of one of the owned files.
             * This will not be able to revoke access since owned photos is disabled.
             * So total owned photos remain the same.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(1, c.getCount());
            }
        } finally {
            executeShellCommand("rm " + testFile);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRenameOperationInSharedStorageForOwnedPhotos() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isTrue();
        performRenameOperationsForDirectory(getDcimDir());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRenameOperationInMediaDirectoryForOwnedPhotos() throws Exception {
        assertThat(CompatChanges.isChangeEnabled(OWNED_PHOTOS_CHANGE_ID)).isTrue();
        performRenameOperationsForDirectory(getExternalMediaDir());
    }

    private static void performRenameOperationsForDirectory(File dir) throws IOException {
        File testFile = new File(dir, "testFile_" + System.nanoTime() + ".jpg");
        File renamedFile1 = new File(dir, "renamed1_" + System.nanoTime() + ".jpg");
        File renamedFile2 = new File(dir, "renamed2_" + System.nanoTime() + ".jpg");
        File renamedFile3 = new File(dir, "renamed3_" + System.nanoTime() + ".jpg");
        testFile.createNewFile();

        try {
            // only test file should originally exists and all other files should not exist
            assertTrue(testFile.exists());
            assertFalse(renamedFile1.exists());
            assertFalse(renamedFile2.exists());
            assertFalse(renamedFile3.exists());

            // the test file is owned by the package and hence has write access
            // so we should be able to rename it
            assertTrue(testFile.renameTo(renamedFile1));
            assertTrue(renamedFile1.exists());
            assertFalse(testFile.exists());

            // Revoke access of renamedFile1 and try to rename it.
            // The package does not have write access and hence should not be able to rename it
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, REVOKE);
            assertFalse(renamedFile1.renameTo(renamedFile2));
            assertTrue(renamedFile1.exists());
            assertFalse(renamedFile2.exists());

            // Grant access of renamedFile1 and try to rename it.
            // The package would have read access but not write access.
            // It should not be able to rename the file
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, GRANT);
            assertFalse(renamedFile1.renameTo(renamedFile3));
            assertTrue(renamedFile1.exists());
            assertFalse(renamedFile3.exists());
        } finally {
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, REVOKE);
            executeShellCommand("rm " + testFile);
            executeShellCommand("rm " + renamedFile1);
            executeShellCommand("rm " + renamedFile2);
            executeShellCommand("rm " + renamedFile3);
        }
    }

    private Bundle getQueryArgsForMediaFiles() {
        Bundle queryArgs = new Bundle();
        queryArgs.putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MEDIA_TYPE
                        + " IN ("
                        + MEDIA_TYPE_IMAGE
                        + ", "
                        + MEDIA_TYPE_VIDEO
                        + ", "
                        + MEDIA_TYPE_AUDIO
                        + ")");
        return queryArgs;
    }
}
