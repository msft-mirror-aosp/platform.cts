/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.provider.cts.media.modern;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.IS_CALL_SUCCESSFUL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_TRASHED_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.callMarkFileAsRestored;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.callTrashFile;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.getFilesCountInDir;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.markIsFavoriteStatus;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.trashFileAndGetTrashedPath;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import com.android.cts.install.lib.TestApp;
import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.Set;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(Parameterized.class)
public class MediaStoreTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    static final String TAG = "MediaStoreTest";

    private static final long SIZE_DELTA = 32_000;
    private static final String[] SYSTEM_GALERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};

    private static final TestApp APP_A_HAS_R_M_I = new TestApp("TestAppA",
            "tests.mediaprovider.modern.testApp.TestAppA", 1, false,
            "CtsMediaProviderTestAppA.apk");
    private static final TestApp APP_B_NO_PERM = new TestApp("TestAppB",
            "tests.mediaprovider.modern.testApp.TestAppB", 1, false,
            "CtsMediaProviderTestAppB.apk");

    private static final TestApp APP_C_HAS_M_E_S = new TestApp("TestAppC",
            "tests.mediaprovider.modern.testApp.TestAppC", 1, false,
            "CtsMediaProviderTestAppC.apk");

    private Context mContext;
    private ContentResolver mContentResolver;

    private Uri mExternalImages;

    @Parameter(0)
    public String mVolumeName;

    @Parameters
    public static Iterable<? extends Object> data() {
        return MediaProviderTestUtils.getSharedVolumeNames();
    }

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName + " for user " + mContext.getUserId());
        mExternalImages = MediaStore.Images.Media.getContentUri(mVolumeName);

        setUpApps();
    }

    private void setUpApps() throws Exception {
        final int uid =
                getContext().getPackageManager().getPackageUid(APP_C_HAS_M_E_S.getPackageName(), 0);
        setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED,
                AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Sure this is pointless, but czars demand test coverage.
     */
    @Test
    public void testConstructors() {
        new MediaStore();
        new MediaStore.Audio();
        new MediaStore.Audio.Albums();
        new MediaStore.Audio.Artists();
        new MediaStore.Audio.Artists.Albums();
        new MediaStore.Audio.Genres();
        new MediaStore.Audio.Genres.Members();
        new MediaStore.Audio.Media();
        new MediaStore.Audio.Playlists();
        new MediaStore.Audio.Playlists.Members();
        new MediaStore.Files();
        new MediaStore.Images();
        new MediaStore.Images.Media();
        new MediaStore.Images.Thumbnails();
        new MediaStore.Video();
        new MediaStore.Video.Media();
        new MediaStore.Video.Thumbnails();
    }

    @Test
    public void testRequireOriginal() {
        assertFalse(MediaStore.getRequireOriginal(mExternalImages));
        assertTrue(MediaStore.getRequireOriginal(MediaStore.setRequireOriginal(mExternalImages)));
    }

    @Test
    public void testGetMediaScannerUri() {
        // query
        Cursor c = mContentResolver.query(MediaStore.getMediaScannerUri(), null,
                null, null, null);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void testGetVersion() {
        // We should have valid versions to help detect data wipes
        assertNotNull(MediaStore.getVersion(getContext()));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_INTERNAL));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_EXTERNAL));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    public void testGetExternalVolumeNames() {
        Set<String> volumeNames = MediaStore.getExternalVolumeNames(getContext());

        assertFalse(volumeNames.contains(MediaStore.VOLUME_INTERNAL));
        assertFalse(volumeNames.contains(MediaStore.VOLUME_EXTERNAL));
        assertTrue(volumeNames.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    public void testGetRecentExternalVolumeNames() {
        Set<String> volumeNames = MediaStore.getRecentExternalVolumeNames(getContext());

        assertFalse(volumeNames.contains(MediaStore.VOLUME_INTERNAL));
        assertFalse(volumeNames.contains(MediaStore.VOLUME_EXTERNAL));
        assertTrue(volumeNames.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (ParcelFileDescriptor pfd1 = mContext.getContentResolver()
                .openFileDescriptor(uri, "r", cg)) {
            try (ParcelFileDescriptor pfd2 = MediaStore
                    .openFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {
                long end1 = Os.lseek(pfd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(pfd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openAssetFileDescriptor(uri, "r", cg)) {
            try (AssetFileDescriptor afd2 = MediaStore
                    .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openTypedAssetFileDescriptor(uri, "*/*", null, cg)) {
            try (AssetFileDescriptor afd2 = MediaStore.openTypedAssetFileDescriptor(
                    mContext.getContentResolver(), uri, "*/*", null, cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (ParcelFileDescriptor pfd = MediaStore
                .openFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (AssetFileDescriptor afd = MediaStore
                .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (AssetFileDescriptor afd = MediaStore.openTypedAssetFileDescriptor(
                mContext.getContentResolver(), uri, "*/*", null, null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (ParcelFileDescriptor pfd = MediaStore
                .openFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (AssetFileDescriptor afd = MediaStore
                .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (AssetFileDescriptor afd = MediaStore
                .openTypedAssetFileDescriptor(
                        mContext.getContentResolver(), uri, "*/*", null, null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testGetStorageVolume() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);

        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        final StorageVolume sv = sm.getStorageVolume(uri);

        // We should always have a volume for media we just created
        assertNotNull(sv);

        if (MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(mVolumeName)) {
            assertEquals(sm.getPrimaryStorageVolume(), sv);
        }
    }

    @Test
    public void testGetStorageVolume_Unrelated() throws Exception {
        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        try {
            sm.getStorageVolume(Uri.parse("content://com.example/path/to/item/"));
            fail("getStorageVolume unrelated should throw exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testRewriteToLegacy() throws Exception {
        final Uri before = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final Uri after = MediaStore.rewriteToLegacy(before);

        assertEquals(MediaStore.AUTHORITY, before.getAuthority());
        assertEquals(MediaStore.AUTHORITY_LEGACY, after.getAuthority());
    }

    /**
     * When upgrading from an older device, we really need our legacy provider
     * to be present to ensure that we don't lose user data like
     * {@link BaseColumns#_ID} and {@link MediaColumns#IS_FAVORITE}.
     */
    @Test
    public void testLegacy() throws Exception {
        final ProviderInfo legacy = getContext().getPackageManager()
                .resolveContentProvider(MediaStore.AUTHORITY_LEGACY, 0);
        if (legacy == null) {
            if (Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.R) {
                // If we're a brand new device, we don't require a legacy
                // provider, since there's nothing to upgrade
                return;
            } else {
                fail("Upgrading devices must have a legacy MediaProvider at "
                        + "MediaStore.AUTHORITY_LEGACY to upgrade user data from");
            }
        }

        // Verify that legacy provider is protected
        assertEquals("Legacy provider at MediaStore.AUTHORITY_LEGACY must protect its data",
                android.Manifest.permission.WRITE_MEDIA_STORAGE, legacy.readPermission);
        assertEquals("Legacy provider at MediaStore.AUTHORITY_LEGACY must protect its data",
                android.Manifest.permission.WRITE_MEDIA_STORAGE, legacy.writePermission);

        // And finally verify that legacy provider is headless
        final PackageInfo legacyPackage = getContext().getPackageManager().getPackageInfo(
                legacy.packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES);
        assertEmpty("Headless legacy MediaProvider must have no activities",
                legacyPackage.activities);
        assertEquals("Headless legacy MediaProvider must have exactly one provider",
                1, legacyPackage.providers.length);
        assertEmpty("Headless legacy MediaProvider must have no receivers",
                legacyPackage.receivers);
        assertEmpty("Headless legacy MediaProvider must have no services",
                legacyPackage.services);
    }

    @Test
    public void testIsCurrentSystemGallery() throws Exception {
        assertThat(
                MediaStore.isCurrentSystemGallery(
                        mContentResolver, Process.myUid(), getContext().getPackageName()))
                .isFalse();

        try {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            assertThat(
                    MediaStore.isCurrentSystemGallery(
                            mContentResolver, Process.myUid(), getContext().getPackageName()))
                    .isTrue();
        } finally {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }

        assertThat(
                MediaStore.isCurrentSystemGallery(
                        mContentResolver, Process.myUid(), getContext().getPackageName()))
                .isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testCanManageMedia() throws Exception {
        final String opString = AppOpsManager.permissionToOp(Manifest.permission.MANAGE_MEDIA);

        // no access
        assertThat(MediaStore.canManageMedia(getContext())).isFalse();
        try {
            // grant access
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ALLOWED, opString);

            assertThat(MediaStore.canManageMedia(getContext())).isTrue();
        } finally {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ERRORED, opString);
        }
        // no access
        assertThat(MediaStore.canManageMedia(getContext())).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MARK_IS_FAVORITE_STATUS_API)
    public void testMarkMediaAsFavorite_onlyReadPermission_markIsFavoriteStatus()
            throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.lg_g4_iso_800_jpg, mExternalImages);

        assertFalse(isImageMarkedFavorite(uri));

        Bundle response = markIsFavoriteStatus(APP_A_HAS_R_M_I, uri, /* areFavorites */ true);

        assertTrue(response.getBoolean(IS_CALL_SUCCESSFUL));
        assertTrue(isImageMarkedFavorite(uri));

        response = markIsFavoriteStatus(APP_A_HAS_R_M_I, uri, /* areFavorites */ false);

        assertTrue(response.getBoolean(IS_CALL_SUCCESSFUL));
        assertFalse(isImageMarkedFavorite(uri));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MARK_IS_FAVORITE_STATUS_API)
    public void testMarkMediaAsFavorite_onlyManageExternalStorage_markIsFavoriteStatus()
            throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.lg_g4_iso_800_jpg, mExternalImages);

        assertFalse(isImageMarkedFavorite(uri));

        Bundle response = markIsFavoriteStatus(APP_C_HAS_M_E_S, uri, /* areFavorites */ true);

        assertTrue(response.getBoolean(IS_CALL_SUCCESSFUL));
        assertTrue(isImageMarkedFavorite(uri));

        response = markIsFavoriteStatus(APP_C_HAS_M_E_S, uri, /* areFavorites */ false);

        assertTrue(response.getBoolean(IS_CALL_SUCCESSFUL));
        assertFalse(isImageMarkedFavorite(uri));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MARK_IS_FAVORITE_STATUS_API)
    public void testMarkMediaAsFavorite_noPermission_throwsException() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.lg_g4_iso_800_jpg, mExternalImages);

        assertFalse(isImageMarkedFavorite(uri));

        Bundle response = markIsFavoriteStatus(APP_B_NO_PERM, uri, /* areFavorites */ true);

        assertFalse(response.getBoolean(IS_CALL_SUCCESSFUL));
        assertNotNull(response.getParcelable(FAV_API_EXCEPTION,
                UnsupportedOperationException.class));
        assertFalse(isImageMarkedFavorite(uri));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API)
    public void testMarkFileAsTrashedAndRestored_onlyManageExternalStorage() throws Exception {
        assumeFalse(MediaStore.VOLUME_EXTERNAL.equals(mVolumeName));

        final String testDirectoryName = "test_media_dir_" + System.nanoTime();
        final File downloadDir = MediaProviderTestUtils.stageDownloadDir(mVolumeName);
        final File baseTestDirectory = new File(downloadDir, testDirectoryName);

        final File nestedFolder = new File(baseTestDirectory, "nested_folder");
        final File deeplyNestedFolder = new File(nestedFolder, "deeply_nested_folder");

        final File trashedDir = new File(MediaProviderTestUtils.getVolumePath(mVolumeName),
                ".trash-storage");

        try {
            deeplyNestedFolder.mkdirs(); // Create the necessary directories

            final File testImageFile = new File(deeplyNestedFolder, "example_image.jpg");
            MediaProviderTestUtils.stageFile(R.raw.lg_g4_iso_800_jpg, testImageFile);

            // scan the file to ensure it's populated in the MediaStore
            MediaProviderTestUtils.scanFile(testImageFile);

            // before Trashing
            // nestedFolder, deeplyNestedFolder, testImageFile -> 3 nonTrashedCount
            // no trashed items -> 0 trashedCount
            verifyFileCounts(
                    "Before Trashing: All files should be non-trashed.",
                    /* expectedNonTrashedCount */ 3,
                    /* expectedTrashedCount */ 0,
                    baseTestDirectory.getName());

            // trashing deeplyNestedFolder
            String trashedPath =
                    trashFileAndGetTrashedPath(APP_C_HAS_M_E_S,
                            deeplyNestedFolder.getAbsolutePath());

            assertFalse("Original folder should not exist", deeplyNestedFolder.exists());

            assertNotNull("Trashed path should not be null", trashedPath);


            assertTrue("Trashed path should be inside .trash-storage",
                    trashedPath.startsWith(trashedDir.getAbsolutePath()));

            // Verify that the nested folder within .trash-storage exists
            // The trashed path will look something like /volume/
            // .trash-storage/Download/<testDirectoryName>/nested_folder/
            // .trashed-<ts>-deeply_nested_folder
            // So the parent folder will be the one holding the
            // .trashed-<ts>-deeply_nested_folder, which is nested_folder.
            // This 'nested_folder' should be directly inside the .trash-storage.
            File trashedDownloadDir = new File(trashedDir, downloadDir.getName());
            File trashedTestParentDir = new File(trashedDownloadDir, baseTestDirectory.getName());
            File trashedNestedDir = new File(trashedTestParentDir, nestedFolder.getName());

            assertTrue(trashedTestParentDir.getPath() + " should exist",
                    trashedTestParentDir.exists());

            assertTrue(trashedNestedDir.getPath() + " should exist",
                    trashedNestedDir.exists());

            // after Trashing, path should look like
            // <volume>/.trash-storage/nested_folder/.trashed-<ts>-deeply_nested_folder/
            // .trashed-<ts>-example_image
            // nestedFolder, <volume>/.trash-storage/nested_folder -> 2 nonTrashedCount
            // .trashed-<ts>-deeply_nested_folder, .trashed-<ts>-example_image -> 2 trashedCount
            verifyFileCounts("After Trashing: Expected 2 non-trashed and 2 trashed items.",
                    /* expectedNonTrashedCount */ 2,
                    /* expectedTrashedCount */ 2,
                    baseTestDirectory.getName());

            // restore the trashed folder
            // nestedFolder, deeplyNestedFolder, testImageFile -> 3 nonTrashedCount
            // no trashed items -> 0 trashedCount
            String restoredPath = callMarkFileAsRestored(APP_C_HAS_M_E_S, trashedPath);

            assertFalse("Trashed path should not exist", new File(trashedPath).exists());

            assertNotNull("Restored path should not be null", restoredPath);

            assertEquals(
                    "Original path should be equal to the Restored path",
                    deeplyNestedFolder.getAbsolutePath(),
                    restoredPath);

            verifyFileCounts(
                    "After Restoration: All files should be non-trashed again.",
                    /* expectedNonTrashedCount */ 3,
                    /* expectedTrashedCount */ 0,
                    baseTestDirectory.getName());


            // After restoration, the original directory should be restored, and the
            // temporary parent folder created inside .trash-storage should be removed.
            assertFalse(
                    "Test parent folder in .trash-storage should no longer exist"
                            + " after restoration",
                    trashedTestParentDir.exists());

            assertFalse(
                    "Nested parent folder in .trash-storage/<testDirectoryName> should no"
                            + " longer exist after restoration",
                    trashedNestedDir.exists());
        } finally {
            // delete all test files
            MediaProviderTestUtils.deleteContentsAndDir(baseTestDirectory);

            // if present in .trash-storage
            File trashedDownloadDir = new File(trashedDir, downloadDir.getName());
            if (trashedDownloadDir.exists()) {
                MediaProviderTestUtils.deleteContentsAndDir(trashedDownloadDir);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API)
    public void testMarkFileAsTrashed_noPermission_throwsException() throws Exception {
        assumeFalse(MediaStore.VOLUME_EXTERNAL.equals(mVolumeName));

        final String testDirectoryName = "test_media_dir_" + System.nanoTime();
        final File downloadDir = MediaProviderTestUtils.stageDownloadDir(mVolumeName);
        final File baseTestDirectory = new File(downloadDir, testDirectoryName);

        final File nestedFolder = new File(baseTestDirectory, "nested_folder");
        final File deeplyNestedFolder = new File(nestedFolder, "deeply_nested_folder");

        final File trashedDir = new File(MediaProviderTestUtils.getVolumePath(mVolumeName),
                ".trash-storage");

        try {
            deeplyNestedFolder.mkdirs(); // Create the necessary directories

            final File testImageFile = new File(deeplyNestedFolder, "example_image.jpg");
            MediaProviderTestUtils.stageFile(R.raw.lg_g4_iso_800_jpg, testImageFile);

            // scan the file to ensure it's populated in the MediaStore
            MediaProviderTestUtils.scanFile(testImageFile);

            // before Trashing
            // nestedFolder, deeplyNestedFolder, testImageFile -> 3 nonTrashedCount
            // no trashed items -> 0 trashedCount
            verifyFileCounts(
                    "Before Trashing: All files should be non-trashed.",
                    /* expectedNonTrashedCount */ 3,
                    /* expectedTrashedCount */ 0,
                    baseTestDirectory.getName());

            // trashing deeplyNestedFolder
            Bundle bundle =
                    callTrashFile(APP_B_NO_PERM, deeplyNestedFolder.getAbsolutePath());

            assertNotNull(bundle.getParcelable(MEDIASTORE_MARK_FILE_AS_TRASHED_EXCEPTION,
                    SecurityException.class));

            // after Trashing failed, path should look like
            // nestedFolder, deeplyNestedFolder, testImageFile -> 3 nonTrashedCount
            // no trashed items -> 0 trashedCount
            verifyFileCounts(
                    "After Trashing Failed: All files should be non-trashed.",
                    /* expectedNonTrashedCount */ 3,
                    /* expectedTrashedCount */ 0,
                    baseTestDirectory.getName());

            assertTrue("Original nested folder should exist", nestedFolder.exists());
            assertTrue("Original deeplyNestedFolder folder should exist",
                    deeplyNestedFolder.exists());
            assertTrue("Original file should exist", testImageFile.exists());

        } finally {
            // delete all test files
            MediaProviderTestUtils.deleteContentsAndDir(baseTestDirectory);

            // if present in .trash-storage
            File trashedDownloadDir = new File(trashedDir, downloadDir.getName());
            if (trashedDownloadDir.exists()) {
                MediaProviderTestUtils.deleteContentsAndDir(trashedDownloadDir);
            }
        }
    }

    /**
     * Helper method to verify the file counts (non-trashed and trashed) in a given directory.
     *
     * @param message A descriptive message for the assertion.
     * @param expectedNonTrashedCount The expected number of non-trashed items.
     * @param expectedTrashedCount The expected number of trashed items.
     * @param directoryName The name of the directory to check.
     */
    private void verifyFileCounts(
            String message,
            int expectedNonTrashedCount,
            int expectedTrashedCount,
            String directoryName)
            throws Exception {
        int actualNonTrashedCount =
                getFilesCountInDir(APP_C_HAS_M_E_S, directoryName, MediaStore.MATCH_EXCLUDE);
        int actualTrashedCount =
                getFilesCountInDir(APP_C_HAS_M_E_S, directoryName, MediaStore.MATCH_ONLY);

        assertEquals(
                message + " (Non-trashed count)", expectedNonTrashedCount, actualNonTrashedCount);
        assertEquals(message + " (Trashed count)", expectedTrashedCount, actualTrashedCount);
    }

    private boolean isImageMarkedFavorite(Uri uri) {
        final String[] projection = new String[]{MediaColumns.IS_FAVORITE};
        try (Cursor c = mContext.getContentResolver().query(uri, projection, null, null)) {
            assertNotNull(c);
            assertEquals(1, c.getCount());
            assertTrue(c.moveToFirst());

            return "1".equals(c.getString(0));
        }
    }

    private void setAppOpsModeForUid(int uid, int mode, @NonNull String... ops) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(null);
        try {
            for (String op : ops) {
                getContext().getSystemService(AppOpsManager.class).setUidMode(op, uid, mode);
            }
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static <T> void assertEmpty(String message, T[] array) {
        if (array != null && array.length > 0) {
            fail(message);
        }
    }
}
