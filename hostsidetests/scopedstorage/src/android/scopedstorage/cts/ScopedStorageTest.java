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

package android.scopedstorage.cts;

import static android.app.AppOpsManager.permissionToOp;
import static android.os.SystemProperties.getBoolean;
import static android.provider.MediaStore.MediaColumns;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMismatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadata;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromRawResource;
import static android.scopedstorage.cts.lib.TestUtils.ALARMS_DIR;
import static android.scopedstorage.cts.lib.TestUtils.ANDROID_DATA_DIR;
import static android.scopedstorage.cts.lib.TestUtils.ANDROID_MEDIA_DIR;
import static android.scopedstorage.cts.lib.TestUtils.AUDIOBOOKS_DIR;
import static android.scopedstorage.cts.lib.TestUtils.BYTES_DATA1;
import static android.scopedstorage.cts.lib.TestUtils.BYTES_DATA2;
import static android.scopedstorage.cts.lib.TestUtils.DCIM_DIR;
import static android.scopedstorage.cts.lib.TestUtils.DEFAULT_TOP_LEVEL_DIRS;
import static android.scopedstorage.cts.lib.TestUtils.DOCUMENTS_DIR;
import static android.scopedstorage.cts.lib.TestUtils.DOWNLOAD_DIR;
import static android.scopedstorage.cts.lib.TestUtils.MOVIES_DIR;
import static android.scopedstorage.cts.lib.TestUtils.MUSIC_DIR;
import static android.scopedstorage.cts.lib.TestUtils.NOTIFICATIONS_DIR;
import static android.scopedstorage.cts.lib.TestUtils.PICTURES_DIR;
import static android.scopedstorage.cts.lib.TestUtils.PODCASTS_DIR;
import static android.scopedstorage.cts.lib.TestUtils.RINGTONES_DIR;
import static android.scopedstorage.cts.lib.TestUtils.STR_DATA1;
import static android.scopedstorage.cts.lib.TestUtils.STR_DATA2;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameFile;
import static android.scopedstorage.cts.lib.TestUtils.assertCantRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertCantRenameFile;
import static android.scopedstorage.cts.lib.TestUtils.assertDirectoryContains;
import static android.scopedstorage.cts.lib.TestUtils.assertFileContent;
import static android.scopedstorage.cts.lib.TestUtils.assertThrows;
import static android.scopedstorage.cts.lib.TestUtils.canOpen;
import static android.scopedstorage.cts.lib.TestUtils.createFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAsNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.deleteRecursively;
import static android.scopedstorage.cts.lib.TestUtils.deleteWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.denyAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.executeShellCommand;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getFileMimeTypeFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileRowIdFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileUri;
import static android.scopedstorage.cts.lib.TestUtils.grantPermission;
import static android.scopedstorage.cts.lib.TestUtils.installApp;
import static android.scopedstorage.cts.lib.TestUtils.installAppWithStoragePermissions;
import static android.scopedstorage.cts.lib.TestUtils.listAs;
import static android.scopedstorage.cts.lib.TestUtils.openFileAs;
import static android.scopedstorage.cts.lib.TestUtils.openWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.pollForExternalStorageState;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;
import static android.scopedstorage.cts.lib.TestUtils.queryImageFile;
import static android.scopedstorage.cts.lib.TestUtils.readExifMetadataFromTestApp;
import static android.scopedstorage.cts.lib.TestUtils.revokePermission;
import static android.scopedstorage.cts.lib.TestUtils.setupDefaultDirectories;
import static android.scopedstorage.cts.lib.TestUtils.uninstallApp;
import static android.scopedstorage.cts.lib.TestUtils.uninstallAppNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.updateDisplayNameWithMediaProvider;
import static android.system.OsConstants.F_OK;
import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_EXCL;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.R_OK;
import static android.system.OsConstants.S_IRWXU;
import static android.system.OsConstants.W_OK;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

@RunWith(AndroidJUnit4.class)
public class ScopedStorageTest {
    static final String TAG = "ScopedStorageTest";
    static final String THIS_PACKAGE_NAME = getContext().getPackageName();

    static final File EXTERNAL_STORAGE_DIR = Environment.getExternalStorageDirectory();

    static final String TEST_DIRECTORY_NAME = "ScopedStorageTestDirectory";

    static final File EXTERNAL_FILES_DIR = getContext().getExternalFilesDir(null);
    static final File EXTERNAL_MEDIA_DIR = getContext().getExternalMediaDirs()[0];

    static final String AUDIO_FILE_NAME = "ScopedStorageTest_file.mp3";
    static final String PLAYLIST_FILE_NAME = "ScopedStorageTest_file.m3u";
    static final String SUBTITLE_FILE_NAME = "ScopedStorageTest_file.srt";
    static final String VIDEO_FILE_NAME = "ScopedStorageTest_file.mp4";
    static final String IMAGE_FILE_NAME = "ScopedStorageTest_file.jpg";
    static final String NONMEDIA_FILE_NAME = "ScopedStorageTest_file.pdf";

    static final String FILE_CREATION_ERROR_MESSAGE = "No such file or directory";
    private static final File ANDROID_DIR =
            new File(Environment.getExternalStorageDirectory(), "Android");

    private static final TestApp TEST_APP_A = new TestApp("TestAppA",
            "android.scopedstorage.cts.testapp.A", 1, false, "CtsScopedStorageTestAppA.apk");
    private static final TestApp TEST_APP_B = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B", 1, false, "CtsScopedStorageTestAppB.apk");
    private static final TestApp TEST_APP_C = new TestApp("TestAppC",
            "android.scopedstorage.cts.testapp.C", 1, false, "CtsScopedStorageTestAppC.apk");
    private static final TestApp TEST_APP_C_LEGACY = new TestApp("TestAppCLegacy",
            "android.scopedstorage.cts.testapp.C", 1, false, "CtsScopedStorageTestAppCLegacy.apk");
    private static final String[] SYSTEM_GALERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};
    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    @Before
    public void setup() throws Exception {
        // skips all test cases if FUSE is not active.
        assumeTrue(getBoolean("persist.sys.fuse", false));

        pollForExternalStorageState();
        EXTERNAL_FILES_DIR.mkdirs();
    }

    /**
     * This method needs to be called once before running the whole test.
     */
    @Test
    public void setupExternalStorage() {
        setupDefaultDirectories();
    }

    /**
     * Test that we enforce certain media types can only be created in certain directories.
     */
    @Test
    public void testTypePathConformity() throws Exception {
        // Only audio files can be created in Music
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MUSIC_DIR, NONMEDIA_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MUSIC_DIR, VIDEO_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MUSIC_DIR, IMAGE_FILE_NAME).createNewFile(); });
        // Only video files can be created in Movies
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MOVIES_DIR, NONMEDIA_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MOVIES_DIR, AUDIO_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(MOVIES_DIR, IMAGE_FILE_NAME).createNewFile(); });
        // Only image and video files can be created in DCIM
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(DCIM_DIR, NONMEDIA_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(DCIM_DIR, AUDIO_FILE_NAME).createNewFile(); });
        // Only image and video files can be created in Pictures
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(PICTURES_DIR, NONMEDIA_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(PICTURES_DIR, AUDIO_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(PICTURES_DIR, PLAYLIST_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(DCIM_DIR, SUBTITLE_FILE_NAME).createNewFile(); });

        assertCanCreateFile(new File(ALARMS_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(AUDIOBOOKS_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(DCIM_DIR, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(DCIM_DIR, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(DOCUMENTS_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(DOCUMENTS_DIR, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(DOCUMENTS_DIR, NONMEDIA_FILE_NAME));
        assertCanCreateFile(new File(DOCUMENTS_DIR, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(DOWNLOAD_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(DOWNLOAD_DIR, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME));
        assertCanCreateFile(new File(DOWNLOAD_DIR, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(MOVIES_DIR, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(MOVIES_DIR, SUBTITLE_FILE_NAME));
        assertCanCreateFile(new File(MUSIC_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(MUSIC_DIR, PLAYLIST_FILE_NAME));
        assertCanCreateFile(new File(NOTIFICATIONS_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(PICTURES_DIR, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(PICTURES_DIR, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(PODCASTS_DIR, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(RINGTONES_DIR, AUDIO_FILE_NAME));

        // No file whatsoever can be created in the top level directory
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(EXTERNAL_STORAGE_DIR, NONMEDIA_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(EXTERNAL_STORAGE_DIR, AUDIO_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(EXTERNAL_STORAGE_DIR, IMAGE_FILE_NAME).createNewFile(); });
        assertThrows(IOException.class, "Operation not permitted",
                () -> { new File(EXTERNAL_STORAGE_DIR, VIDEO_FILE_NAME).createNewFile(); });
    }

    /**
     * Test that we can create a file in app's external files directory,
     * and that we can write and read to/from the file.
     */
    @Test
    public void testCreateFileInAppExternalDir() throws Exception {
        final File file = new File(EXTERNAL_FILES_DIR, "text.txt");
        try {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.delete()).isTrue();
            // Ensure the file is properly deleted and can be created again
            assertThat(file.createNewFile()).isTrue();

            // Write to file
            try (final FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(BYTES_DATA1);
            }

            // Read the same data from file
            assertFileContent(file, BYTES_DATA1);
        } finally {
            file.delete();
        }
    }

    /**
     * Test that we can't create a file in another app's external files directory,
     * and that we'll get the same error regardless of whether the app exists or not.
     */
    @Test
    public void testCreateFileInOtherAppExternalDir() throws Exception {
        // Creating a file in a non existent package dir should return ENOENT, as expected
        final File nonexistentPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        final File file1 = new File(nonexistentPackageFileDir, NONMEDIA_FILE_NAME);
        assertThrows(
                IOException.class, FILE_CREATION_ERROR_MESSAGE, () -> { file1.createNewFile(); });

        // Creating a file in an existent package dir should give the same error string to avoid
        // leaking installed app names, and we know the following directory exists because shell
        // mkdirs it in test setup
        final File shellPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));
        final File file2 = new File(shellPackageFileDir, NONMEDIA_FILE_NAME);
        assertThrows(
                IOException.class, FILE_CREATION_ERROR_MESSAGE, () -> { file1.createNewFile(); });
    }

    /**
     * Test that we can contribute media without any permissions.
     */
    @Test
    public void testContributeMediaFile() throws Exception {
        final File imageFile = new File(DCIM_DIR, IMAGE_FILE_NAME);

        ContentResolver cr = getContentResolver();
        final String selection =
                MediaColumns.RELATIVE_PATH + " = ? AND " + MediaColumns.DISPLAY_NAME + " = ?";
        final String[] selectionArgs = {Environment.DIRECTORY_DCIM + '/', IMAGE_FILE_NAME};

        try {
            assertThat(imageFile.createNewFile()).isTrue();

            // Ensure that the file was successfully added to the MediaProvider database
            try (final Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                         /* projection */ new String[] {MediaColumns.OWNER_PACKAGE_NAME},
                         selection, selectionArgs, null)) {
                assertThat(c.getCount()).isEqualTo(1);
                c.moveToFirst();
                assertThat(c.getString(c.getColumnIndex(MediaColumns.OWNER_PACKAGE_NAME)))
                        .isEqualTo(THIS_PACKAGE_NAME);
            }

            // Try to write random data to the file
            try (final FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(BYTES_DATA1);
                fos.write(BYTES_DATA2);
            }

            final byte[] expected = (STR_DATA1 + STR_DATA2).getBytes();
            assertFileContent(imageFile, expected);

            // Closing the file after writing will not trigger a MediaScan. Call scanFile to update
            // file's entry in MediaProvider's database.
            assertThat(MediaStore.scanFile(getContentResolver(), imageFile)).isNotNull();

            // Ensure that the scan was completed and the file's size was updated.
            try (final Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                         /* projection */ new String[] {MediaColumns.SIZE}, selection,
                         selectionArgs, null)) {
                assertThat(c.getCount()).isEqualTo(1);
                c.moveToFirst();
                assertThat(c.getInt(c.getColumnIndex(MediaColumns.SIZE)))
                        .isEqualTo(BYTES_DATA1.length + BYTES_DATA2.length);
            }
        } finally {
            imageFile.delete();
        }
        // Ensure that delete makes a call to MediaProvider to remove the file from its database.
        try (final Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                     /* projection */ new String[] {MediaColumns.OWNER_PACKAGE_NAME}, selection,
                     selectionArgs, null)) {
            assertThat(c.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testCreateAndDeleteEmptyDir() throws Exception {
        // Remove directory in order to create it again
        EXTERNAL_FILES_DIR.delete();

        // Can create own external files dir
        assertThat(EXTERNAL_FILES_DIR.mkdir()).isTrue();

        final File dir1 = new File(EXTERNAL_FILES_DIR, "random_dir");
        // Can create dirs inside it
        assertThat(dir1.mkdir()).isTrue();

        final File dir2 = new File(dir1, "random_dir_inside_random_dir");
        // And create a dir inside the new dir
        assertThat(dir2.mkdir()).isTrue();

        // And can delete them all
        assertThat(dir2.delete()).isTrue();
        assertThat(dir1.delete()).isTrue();
        assertThat(EXTERNAL_FILES_DIR.delete()).isTrue();

        // Can't create external dir for other apps
        final File nonexistentPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        final File shellPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));

        assertThat(nonexistentPackageFileDir.mkdir()).isFalse();
        assertThat(shellPackageFileDir.mkdir()).isFalse();
    }

    @Test
    public void testCantAccessOtherAppsContents() throws Exception {
        final File mediaFile = new File(PICTURES_DIR, IMAGE_FILE_NAME);
        final File nonMediaFile = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME);
        try {
            installApp(TEST_APP_A);

            assertThat(createFileAs(TEST_APP_A, mediaFile.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, nonMediaFile.getPath())).isTrue();

            // We can still see that the files exist
            assertThat(mediaFile.exists()).isTrue();
            assertThat(nonMediaFile.exists()).isTrue();

            // But we can't access their content
            assertThat(canOpen(mediaFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(nonMediaFile, /* forWrite */ true)).isFalse();
            assertThat(canOpen(mediaFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(nonMediaFile, /* forWrite */ true)).isFalse();
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, nonMediaFile.getPath());
            deleteFileAsNoThrow(TEST_APP_A, mediaFile.getPath());
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    @Test
    public void testCantDeleteOtherAppsContents() throws Exception {
        final File dirInDownload = new File(DOWNLOAD_DIR, TEST_DIRECTORY_NAME);
        final File mediaFile = new File(dirInDownload, IMAGE_FILE_NAME);
        final File nonMediaFile = new File(dirInDownload, NONMEDIA_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertThat(dirInDownload.mkdir()).isTrue();
            // Have another app create a media file in the directory
            assertThat(createFileAs(TEST_APP_A, mediaFile.getPath())).isTrue();

            // Can't delete the directory since it contains another app's content
            assertThat(dirInDownload.delete()).isFalse();
            // Can't delete another app's content
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Have another app create a non-media file in the directory
            assertThat(createFileAs(TEST_APP_A, nonMediaFile.getPath())).isTrue();

            // Can't delete the directory since it contains another app's content
            assertThat(dirInDownload.delete()).isFalse();
            // Can't delete another app's content
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Delete only the media file and keep the non-media file
            assertThat(deleteFileAs(TEST_APP_A, mediaFile.getPath())).isTrue();
            // Directory now has only the non-media file contributed by another app, so we still
            // can't delete it nor its content
            assertThat(dirInDownload.delete()).isFalse();
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Delete the last file belonging to another app
            assertThat(deleteFileAs(TEST_APP_A, nonMediaFile.getPath())).isTrue();
            // Create our own file
            assertThat(nonMediaFile.createNewFile()).isTrue();

            // Now that the directory only has content that was contributed by us, we can delete it
            assertThat(deleteRecursively(dirInDownload)).isTrue();
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, nonMediaFile.getPath());
            deleteFileAsNoThrow(TEST_APP_A, mediaFile.getPath());
            // At this point, we're not sure who created this file, so we'll have both apps
            // deleting it
            mediaFile.delete();
            uninstallAppNoThrow(TEST_APP_A);
            dirInDownload.delete();
        }
    }

    /**
     * This test relies on the fact that {@link File#list} uses opendir internally, and that it
     * returns {@code null} if opendir fails.
     */
    @Test
    public void testOpendirRestrictions() throws Exception {
        // Opening a non existent package directory should fail, as expected
        final File nonexistentPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        assertThat(nonexistentPackageFileDir.list()).isNull();

        // Opening another package's external directory should fail as well, even if it exists
        final File shellPackageFileDir = new File(
                EXTERNAL_FILES_DIR.getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));
        assertThat(shellPackageFileDir.list()).isNull();

        // We can open our own external files directory
        final String[] filesList = EXTERNAL_FILES_DIR.list();
        assertThat(filesList).isNotNull();
        assertThat(filesList).isEmpty();

        // We can open any public directory in external storage
        assertThat(DCIM_DIR.list()).isNotNull();
        assertThat(DOWNLOAD_DIR.list()).isNotNull();
        assertThat(MOVIES_DIR.list()).isNotNull();
        assertThat(MUSIC_DIR.list()).isNotNull();

        // We can open the root directory of external storage
        final String[] topLevelDirs = EXTERNAL_STORAGE_DIR.list();
        assertThat(topLevelDirs).isNotNull();
        // TODO(b/145287327): This check fails on a device with no visible files.
        // This can be fixed if we display default directories.
        // assertThat(topLevelDirs).isNotEmpty();
    }

    @Test
    public void testLowLevelFileIO() throws Exception {
        String filePath = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME).toString();
        try {
            int createFlags = O_CREAT | O_RDWR;
            int createExclFlags = createFlags | O_EXCL;

            FileDescriptor fd = Os.open(filePath, createExclFlags, S_IRWXU);
            Os.close(fd);
            assertThrows(
                    ErrnoException.class, () -> { Os.open(filePath, createExclFlags, S_IRWXU); });

            fd = Os.open(filePath, createFlags, S_IRWXU);
            try {
                assertThat(Os.write(fd, ByteBuffer.wrap(BYTES_DATA1))).isEqualTo(BYTES_DATA1.length);
                assertFileContent(fd, BYTES_DATA1);
            } finally {
                Os.close(fd);
            }
            // should just append the data
            fd = Os.open(filePath, createFlags | O_APPEND, S_IRWXU);
            try {
                assertThat(Os.write(fd, ByteBuffer.wrap(BYTES_DATA2))).isEqualTo(BYTES_DATA2.length);
                final byte[] expected = (STR_DATA1 + STR_DATA2).getBytes();
                assertFileContent(fd, expected);
            } finally {
                Os.close(fd);
            }
            // should overwrite everything
            fd = Os.open(filePath, createFlags | O_TRUNC, S_IRWXU);
            try {
                final byte[] otherData = "this is different data".getBytes();
                assertThat(Os.write(fd, ByteBuffer.wrap(otherData))).isEqualTo(otherData.length);
                assertFileContent(fd, otherData);
            } finally {
                Os.close(fd);
            }
        } finally {
            new File(filePath).delete();
        }
    }

    /**
     * Test that media files from other packages are only visible to apps with storage permission.
     */
    @Test
    public void testListDirectoriesWithMediaFiles() throws Exception {
        final File dir = new File(DCIM_DIR, TEST_DIRECTORY_NAME);
        final File videoFile = new File(dir, VIDEO_FILE_NAME);
        final String videoFileName = videoFile.getName();
        try {
            if (!dir.exists()) {
                assertThat(dir.mkdir()).isTrue();
            }

            // Install TEST_APP_A and create media file in the new directory.
            installApp(TEST_APP_A);
            assertThat(createFileAs(TEST_APP_A, videoFile.getPath())).isTrue();
            // TEST_APP_A should see TEST_DIRECTORY in DCIM and new file in TEST_DIRECTORY.
            assertThat(listAs(TEST_APP_A, DCIM_DIR.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(TEST_APP_A, dir.getPath())).containsExactly(videoFileName);

            // Install TEST_APP_B with storage permission.
            installAppWithStoragePermissions(TEST_APP_B);
            // TEST_APP_B with storage permission should see TEST_DIRECTORY in DCIM and new file
            // in TEST_DIRECTORY.
            assertThat(listAs(TEST_APP_B, DCIM_DIR.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(TEST_APP_B, dir.getPath())).containsExactly(videoFileName);

            // Revoke storage permission for TEST_APP_B
            revokePermission(
                    TEST_APP_B.getPackageName(), Manifest.permission.READ_EXTERNAL_STORAGE);
            // TEST_APP_B without storage permission should see TEST_DIRECTORY in DCIM and should
            // not see new file in new TEST_DIRECTORY.
            assertThat(listAs(TEST_APP_B, DCIM_DIR.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(TEST_APP_B, dir.getPath())).doesNotContain(videoFileName);
        } finally {
            uninstallAppNoThrow(TEST_APP_B);
            deleteFileAsNoThrow(TEST_APP_A, videoFile.getPath());
            dir.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that app can't see non-media files created by other packages
     */
    @Test
    public void testListDirectoriesWithNonMediaFiles() throws Exception {
        final File dir = new File(DOWNLOAD_DIR, TEST_DIRECTORY_NAME);
        final File pdfFile = new File(dir, NONMEDIA_FILE_NAME);
        final String pdfFileName = pdfFile.getName();
        try {
            if (!dir.exists()) {
                assertThat(dir.mkdir()).isTrue();
            }

            // Install TEST_APP_A and create non media file in the new directory.
            installApp(TEST_APP_A);
            assertThat(createFileAs(TEST_APP_A, pdfFile.getPath())).isTrue();

            // TEST_APP_A should see TEST_DIRECTORY in DOWNLOAD_DIR and new non media file in
            // TEST_DIRECTORY.
            assertThat(listAs(TEST_APP_A, DOWNLOAD_DIR.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(TEST_APP_A, dir.getPath())).containsExactly(pdfFileName);

            // Install TEST_APP_B with storage permission.
            installAppWithStoragePermissions(TEST_APP_B);
            // TEST_APP_B with storage permission should see TEST_DIRECTORY in DOWNLOAD_DIR
            // and should not see new non media file in TEST_DIRECTORY.
            assertThat(listAs(TEST_APP_B, DOWNLOAD_DIR.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(TEST_APP_B, dir.getPath())).doesNotContain(pdfFileName);
        } finally {
            uninstallAppNoThrow(TEST_APP_B);
            deleteFileAsNoThrow(TEST_APP_A, pdfFile.getPath());
            dir.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that app can only see its directory in Android/data.
     */
    @Test
    public void testListFilesFromExternalFilesDirectory() throws Exception {
        final String packageName = THIS_PACKAGE_NAME;
        final File videoFile = new File(EXTERNAL_FILES_DIR, NONMEDIA_FILE_NAME);

        try {
            // Create a file in app's external files directory
            if (!videoFile.exists()) {
                assertThat(videoFile.createNewFile()).isTrue();
            }
            // App should see its directory and directories of shared packages. App should see all
            // files and directories in its external directory.
            assertDirectoryContains(videoFile.getParentFile(), videoFile);

            // Install TEST_APP_A with READ_EXTERNAL_STORAGE permission.
            // TEST_APP_A should not see other app's external files directory.
            installAppWithStoragePermissions(TEST_APP_A);

            assertThrows(IOException.class, () -> listAs(TEST_APP_A, ANDROID_DATA_DIR.getPath()));
            assertThrows(IOException.class, () -> listAs(TEST_APP_A, EXTERNAL_FILES_DIR.getPath()));
        } finally {
            videoFile.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that app can see files and directories in Android/media.
     */
    @Test
    public void testListFilesFromExternalMediaDirectory() throws Exception {
        final File videoFile = new File(EXTERNAL_MEDIA_DIR, VIDEO_FILE_NAME);

        try {
            // Create a file in app's external media directory
            if (!videoFile.exists()) {
                assertThat(videoFile.createNewFile()).isTrue();
            }

            // App should see its directory and other app's external media directories with media
            // files.
            assertDirectoryContains(videoFile.getParentFile(), videoFile);

            // Install TEST_APP_A with READ_EXTERNAL_STORAGE permission.
            // TEST_APP_A with storage permission should see other app's external media directory.
            installAppWithStoragePermissions(TEST_APP_A);
            // Apps with READ_EXTERNAL_STORAGE can list files in other app's external media directory.
            assertThat(listAs(TEST_APP_A, ANDROID_MEDIA_DIR.getPath())).contains(THIS_PACKAGE_NAME);
            assertThat(listAs(TEST_APP_A, EXTERNAL_MEDIA_DIR.getPath()))
                    .containsExactly(videoFile.getName());
        } finally {
            videoFile.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that readdir lists unsupported file types in default directories.
     */
    @Test
    public void testListUnsupportedFileType() throws Exception {
        final File pdfFile = new File(DCIM_DIR, NONMEDIA_FILE_NAME);
        final File videoFile = new File(MUSIC_DIR, VIDEO_FILE_NAME);
        try {
            // TEST_APP_A with storage permission should not see pdf file in DCIM
            executeShellCommand("touch " + pdfFile.getAbsolutePath());
            assertThat(pdfFile.exists()).isTrue();
            assertThat(MediaStore.scanFile(getContentResolver(), pdfFile)).isNotNull();

            installAppWithStoragePermissions(TEST_APP_A);
            assertThat(listAs(TEST_APP_A, DCIM_DIR.getPath())).doesNotContain(NONMEDIA_FILE_NAME);

            executeShellCommand("touch " + videoFile.getAbsolutePath());
            // We don't insert files to db for files created by shell.
            assertThat(MediaStore.scanFile(getContentResolver(), videoFile)).isNotNull();
            // TEST_APP_A with storage permission should see video file in Music directory.
            assertThat(listAs(TEST_APP_A, MUSIC_DIR.getPath())).contains(VIDEO_FILE_NAME);
        } finally {
            executeShellCommand("rm " + pdfFile.getAbsolutePath());
            executeShellCommand("rm " + videoFile.getAbsolutePath());
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    @Test
    public void testMetaDataRedaction() throws Exception {
        File jpgFile = new File(PICTURES_DIR, "img_metadata.jpg");
        try {
            if (jpgFile.exists()) {
                assertThat(jpgFile.delete()).isTrue();
            }

            HashMap<String, String> originalExif =
                    getExifMetadataFromRawResource(R.raw.img_with_metadata);

            try (InputStream in =
                            getContext().getResources().openRawResource(R.raw.img_with_metadata);
                    OutputStream out = new FileOutputStream(jpgFile)) {
                // Dump the image we have to external storage
                FileUtils.copy(in, out);
            }

            HashMap<String, String> exif = getExifMetadata(jpgFile);
            assertExifMetadataMatch(exif, originalExif);

            installAppWithStoragePermissions(TEST_APP_A);
            HashMap<String, String> exifFromTestApp =
                    readExifMetadataFromTestApp(TEST_APP_A, jpgFile.getPath());
            // Other apps shouldn't have access to the same metadata without explicit permission
            assertExifMetadataMismatch(exifFromTestApp, originalExif);

            // TODO(b/146346138): Test that if we give TEST_APP_A write URI permission,
            //  it would be able to access the metadata.
        } finally {
            jpgFile.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    @Test
    public void testOpenFilePathFirstWriteContentResolver() throws Exception {
        String displayName = "open_file_path_write_content_resolver.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            ParcelFileDescriptor readPfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");

            assertRWR(readPfd, writePfd);
            assertUpperFsFd(writePfd); // With cache
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverFirstWriteContentResolver() throws Exception {
        String displayName = "open_content_resolver_write_content_resolver.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");
            ParcelFileDescriptor readPfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);

            assertRWR(readPfd, writePfd);
            assertLowerFsFd(writePfd);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenFilePathFirstWriteFilePath() throws Exception {
        String displayName = "open_file_path_write_file_path.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            ParcelFileDescriptor writePfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw");

            assertRWR(readPfd, writePfd);
            assertUpperFsFd(readPfd); // With cache
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverFirstWriteFilePath() throws Exception {
        String displayName = "open_content_resolver_write_file_path.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw");
            ParcelFileDescriptor writePfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);

            assertRWR(readPfd, writePfd);
            assertLowerFsFd(readPfd);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverWriteOnly() throws Exception {
        String displayName = "open_content_resolver_write_only.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            // Since we can only place one F_WRLCK, the second open for readPfd will go
            // throuh FUSE
            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "w");
            ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw");

            assertRWR(readPfd, writePfd);
            assertLowerFsFd(writePfd);
            assertUpperFsFd(readPfd); // Without cache
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverDup() throws Exception {
        String displayName = "open_content_resolver_dup.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            file.delete();
            assertThat(file.createNewFile()).isTrue();

            // Even if we close the original fd, since we have a dup open
            // the FUSE IO should still bypass the cache
            try (ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw")) {
                try (ParcelFileDescriptor writePfdDup = writePfd.dup();
                        ParcelFileDescriptor readPfd = ParcelFileDescriptor.open(
                                file, ParcelFileDescriptor.MODE_READ_WRITE)) {
                    writePfd.close();

                    assertRWR(readPfd, writePfdDup);
                    assertLowerFsFd(writePfdDup);
                }
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverClose() throws Exception {
        String displayName = "open_content_resolver_close.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            byte[] readBuffer = new byte[10];
            byte[] writeBuffer = new byte[10];
            Arrays.fill(writeBuffer, (byte) 1);

            assertThat(file.createNewFile()).isTrue();

            // Lower fs open and write
            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");
            Os.pwrite(writePfd.getFileDescriptor(), writeBuffer, 0, 10, 0);

            // Close so upper fs open will not use direct_io
            writePfd.close();

            // Upper fs open and read without direct_io
            ParcelFileDescriptor readPfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            Os.pread(readPfd.getFileDescriptor(), readBuffer, 0, 10, 0);

            // Last write on lower fs is visible via upper fs
            assertThat(readBuffer).isEqualTo(writeBuffer);
            assertThat(readPfd.getStatSize()).isEqualTo(writeBuffer.length);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testContentResolverDelete() throws Exception {
        String displayName = "content_resolver_delete.jpg";
        File file = new File(DCIM_DIR, displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            deleteWithMediaProvider(file);

            assertThat(file.exists()).isFalse();
            assertThat(file.createNewFile()).isTrue();
        } finally {
            file.delete();
        }
    }

    @Test
    public void testContentResolverUpdate() throws Exception {
        String oldDisplayName = "content_resolver_update_old.jpg";
        String newDisplayName = "content_resolver_update_new.jpg";
        File oldFile = new File(DCIM_DIR, oldDisplayName);
        File newFile = new File(DCIM_DIR, newDisplayName);

        try {
            assertThat(oldFile.createNewFile()).isTrue();

            updateDisplayNameWithMediaProvider(
                    Environment.DIRECTORY_DCIM, oldDisplayName, newDisplayName);

            assertThat(oldFile.exists()).isFalse();
            assertThat(oldFile.createNewFile()).isTrue();
            assertThat(newFile.exists()).isTrue();
            assertThat(newFile.createNewFile()).isFalse();
        } finally {
            oldFile.delete();
            newFile.delete();
        }
    }

    @Test
    public void testCreateLowerCaseDeleteUpperCase() throws Exception {
        File upperCase = new File(DOWNLOAD_DIR, "CREATE_LOWER_DELETE_UPPER");
        File lowerCase = new File(DOWNLOAD_DIR, "create_lower_delete_upper");

        createDeleteCreate(lowerCase, upperCase);
    }

    @Test
    public void testCreateUpperCaseDeleteLowerCase() throws Exception {
        File upperCase = new File(DOWNLOAD_DIR, "CREATE_UPPER_DELETE_LOWER");
        File lowerCase = new File(DOWNLOAD_DIR, "create_upper_delete_lower");

        createDeleteCreate(upperCase, lowerCase);
    }

    @Test
    public void testCreateMixedCaseDeleteDifferentMixedCase() throws Exception {
        File mixedCase1 = new File(DOWNLOAD_DIR, "CrEaTe_MiXeD_dElEtE_mIxEd");
        File mixedCase2 = new File(DOWNLOAD_DIR, "cReAtE_mIxEd_DeLeTe_MiXeD");

        createDeleteCreate(mixedCase1, mixedCase2);
    }

    private void createDeleteCreate(File create, File delete) throws Exception {
        try {
            assertThat(create.createNewFile()).isTrue();
            Thread.sleep(5);

            assertThat(delete.delete()).isTrue();
            Thread.sleep(5);

            assertThat(create.createNewFile()).isTrue();
            Thread.sleep(5);
        } finally {
            create.delete();
            create.delete();
        }
    }

    @Test
    public void testReadStorageInvalidation() throws Exception {
        testAppOpInvalidation(TEST_APP_C, new File(DCIM_DIR, "read_storage.jpg"),
                Manifest.permission.READ_EXTERNAL_STORAGE,
                AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE, /* forWrite */ false);
    }

    @Test
    public void testWriteStorageInvalidation() throws Exception {
        testAppOpInvalidation(TEST_APP_C_LEGACY, new File(DCIM_DIR, "write_storage.jpg"),
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE, /* forWrite */ true);
    }

    @Test
    public void testManageStorageInvalidation() throws Exception {
        testAppOpInvalidation(TEST_APP_C, new File(DOWNLOAD_DIR, "manage_storage.pdf"),
                /* permission */ null, OPSTR_MANAGE_EXTERNAL_STORAGE, /* forWrite */ true);
    }

    @Test
    public void testWriteImagesInvalidation() throws Exception {
        testAppOpInvalidation(TEST_APP_C, new File(DCIM_DIR, "write_images.jpg"),
                /* permission */ null, AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, /* forWrite */ true);
    }

    @Test
    public void testWriteVideoInvalidation() throws Exception {
        testAppOpInvalidation(TEST_APP_C, new File(DCIM_DIR, "write_video.mp4"),
                /* permission */ null, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO, /* forWrite */ true);
    }

    @Test
    public void testAccessMediaLocationInvalidation() throws Exception {
        File imgFile = new File(DCIM_DIR, "access_media_location.jpg");

        try {
            // Setup image with sensitive data on external storage
            HashMap<String, String> originalExif =
                    getExifMetadataFromRawResource(R.raw.img_with_metadata);
            try (InputStream in =
                            getContext().getResources().openRawResource(R.raw.img_with_metadata);
                    OutputStream out = new FileOutputStream(imgFile)) {
                // Dump the image we have to external storage
                FileUtils.copy(in, out);
            }
            HashMap<String, String> exif = getExifMetadata(imgFile);
            assertExifMetadataMatch(exif, originalExif);

            // Install test app
            installAppWithStoragePermissions(TEST_APP_C);

            // Grant A_M_L and verify access to sensitive data
            grantPermission(TEST_APP_C.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            HashMap<String, String> exifFromTestApp =
                    readExifMetadataFromTestApp(TEST_APP_C, imgFile.getPath());
            assertExifMetadataMatch(exifFromTestApp, originalExif);

            // Revoke A_M_L and verify sensitive data redaction
            revokePermission(
                    TEST_APP_C.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            exifFromTestApp = readExifMetadataFromTestApp(TEST_APP_C, imgFile.getPath());
            assertExifMetadataMismatch(exifFromTestApp, originalExif);

            // Re-grant A_M_L and verify access to sensitive data
            grantPermission(TEST_APP_C.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            exifFromTestApp = readExifMetadataFromTestApp(TEST_APP_C, imgFile.getPath());
            assertExifMetadataMatch(exifFromTestApp, originalExif);
        } finally {
            imgFile.delete();
            uninstallAppNoThrow(TEST_APP_C);
        }
    }

    @Test
    public void testAppUpdateInvalidation() throws Exception {
        File file = new File(DCIM_DIR, "app_update.jpg");
        try {
            assertThat(file.createNewFile()).isTrue();

            // Install legacy
            installAppWithStoragePermissions(TEST_APP_C_LEGACY);
            grantPermission(TEST_APP_C_LEGACY.getPackageName(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE); // Grants write access for legacy
            // Legacy app can read and write media files contributed by others
            assertThat(openFileAs(TEST_APP_C_LEGACY, file.getPath(), /* forWrite */ false)).isTrue();
            assertThat(openFileAs(TEST_APP_C_LEGACY, file.getPath(), /* forWrite */ true)).isTrue();

            // Update to non-legacy
            installAppWithStoragePermissions(TEST_APP_C);
            grantPermission(TEST_APP_C_LEGACY.getPackageName(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE); // No effect for non-legacy
            // Non-legacy app can read media files contributed by others
            assertThat(openFileAs(TEST_APP_C, file.getPath(), /* forWrite */ false)).isTrue();
            // But cannot write
            assertThat(openFileAs(TEST_APP_C, file.getPath(), /* forWrite */ true)).isFalse();
        } finally {
            file.delete();
            uninstallAppNoThrow(TEST_APP_C);
        }
    }

    @Test
    public void testAppReinstallInvalidation() throws Exception {
        File file = new File(DCIM_DIR, "app_reinstall.jpg");

        try {
            assertThat(file.createNewFile()).isTrue();

            // Install
            installAppWithStoragePermissions(TEST_APP_C);
            assertThat(openFileAs(TEST_APP_C, file.getPath(), /* forWrite */ false)).isTrue();

            // Re-install
            uninstallAppNoThrow(TEST_APP_C);
            installApp(TEST_APP_C);
            assertThat(openFileAs(TEST_APP_C, file.getPath(), /* forWrite */ false)).isFalse();
        } finally {
            file.delete();
            uninstallAppNoThrow(TEST_APP_C);
        }
    }

    private void testAppOpInvalidation(TestApp app, File file, @Nullable String permission,
            String opstr, boolean forWrite) throws Exception {
        try {
            installApp(app);
            assertThat(file.createNewFile()).isTrue();
            assertAppOpInvalidation(app, file, permission, opstr, forWrite);
        } finally {
            file.delete();
            uninstallApp(app);
        }
    }

    /** If {@code permission} is null, appops are flipped, otherwise permissions are flipped */
    private void assertAppOpInvalidation(TestApp app, File file, @Nullable String permission,
            String opstr, boolean forWrite) throws Exception {
        String packageName = app.getPackageName();
        int uid = getContext().getPackageManager().getPackageUid(packageName, 0);

        // Deny
        if (permission != null) {
            revokePermission(packageName, permission);
        } else {
            denyAppOpsToUid(uid, opstr);
        }
        assertThat(openFileAs(app, file.getPath(), forWrite)).isFalse();

        // Grant
        if (permission != null) {
            grantPermission(packageName, permission);
        } else {
            allowAppOpsToUid(uid, opstr);
        }
        assertThat(openFileAs(app, file.getPath(), forWrite)).isTrue();

        // Deny
        if (permission != null) {
            revokePermission(packageName, permission);
        } else {
            denyAppOpsToUid(uid, opstr);
        }
        assertThat(openFileAs(app, file.getPath(), forWrite)).isFalse();
    }

    @Test
    public void testSystemGalleryAppHasFullAccessToImages() throws Exception {
        final File otherAppImageFile = new File(DCIM_DIR, "other_" + IMAGE_FILE_NAME);
        final File topLevelImageFile = new File(EXTERNAL_STORAGE_DIR, IMAGE_FILE_NAME);
        final File imageInAnObviouslyWrongPlace = new File(MUSIC_DIR, IMAGE_FILE_NAME);

        try {
            installApp(TEST_APP_A);
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create an image file
            assertThat(createFileAs(TEST_APP_A, otherAppImageFile.getPath())).isTrue();
            assertThat(otherAppImageFile.exists()).isTrue();

            // Assert we can write to the file
            try (final FileOutputStream fos = new FileOutputStream(otherAppImageFile)) {
                fos.write(BYTES_DATA1);
            }

            // Assert we can read from the file
            assertFileContent(otherAppImageFile, BYTES_DATA1);

            // Assert we can delete the file
            assertThat(otherAppImageFile.delete()).isTrue();
            assertThat(otherAppImageFile.exists()).isFalse();

            // Can create an image anywhere
            assertCanCreateFile(topLevelImageFile);
            assertCanCreateFile(imageInAnObviouslyWrongPlace);

            // Put the file back in its place and let TEST_APP_A delete it
            assertThat(otherAppImageFile.createNewFile()).isTrue();
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherAppImageFile.getAbsolutePath());
            otherAppImageFile.delete();
            uninstallApp(TEST_APP_A);
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryAppHasNoFullAccessToAudio() throws Exception {
        final File otherAppAudioFile = new File(MUSIC_DIR, "other_" + AUDIO_FILE_NAME);
        final File topLevelAudioFile = new File(EXTERNAL_STORAGE_DIR, AUDIO_FILE_NAME);
        final File audioInAnObviouslyWrongPlace = new File(PICTURES_DIR, AUDIO_FILE_NAME);

        try {
            installApp(TEST_APP_A);
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create an audio file
            assertThat(createFileAs(TEST_APP_A, otherAppAudioFile.getPath())).isTrue();
            assertThat(otherAppAudioFile.exists()).isTrue();

            // Assert we can't access the file
            assertThat(canOpen(otherAppAudioFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(otherAppAudioFile, /* forWrite */ true)).isFalse();

            // Assert we can't delete the file
            assertThat(otherAppAudioFile.delete()).isFalse();

            // Can't create an audio file where it doesn't belong
            assertThrows(IOException.class, "Operation not permitted",
                    () -> { topLevelAudioFile.createNewFile(); });
            assertThrows(IOException.class, "Operation not permitted",
                    () -> { audioInAnObviouslyWrongPlace.createNewFile(); });
        } finally {
            deleteFileAs(TEST_APP_A, otherAppAudioFile.getPath());
            uninstallApp(TEST_APP_A);
            topLevelAudioFile.delete();
            audioInAnObviouslyWrongPlace.delete();
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryCanRenameImagesAndVideos() throws Exception {
        final File otherAppVideoFile = new File(DCIM_DIR, "other_" + VIDEO_FILE_NAME);
        final File imageFile = new File(PICTURES_DIR, IMAGE_FILE_NAME);
        final File videoFile = new File(PICTURES_DIR, VIDEO_FILE_NAME);
        final File topLevelVideoFile = new File(EXTERNAL_STORAGE_DIR, VIDEO_FILE_NAME);
        final File musicFile = new File(MUSIC_DIR, AUDIO_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create a video file
            assertThat(createFileAs(TEST_APP_A, otherAppVideoFile.getPath())).isTrue();
            assertThat(otherAppVideoFile.exists()).isTrue();

            // Write some data to the file
            try (final FileOutputStream fos = new FileOutputStream(otherAppVideoFile)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(otherAppVideoFile, BYTES_DATA1);

            // Assert we can rename the file and ensure the file has the same content
            assertCanRenameFile(otherAppVideoFile, videoFile);
            assertFileContent(videoFile, BYTES_DATA1);
            // We can even move it to the top level directory
            assertCanRenameFile(videoFile, topLevelVideoFile);
            assertFileContent(topLevelVideoFile, BYTES_DATA1);
            // And we can even convert it into an image file, because why not?
            assertCanRenameFile(topLevelVideoFile, imageFile);
            assertFileContent(imageFile, BYTES_DATA1);

            // We can convert it to a music file, but we won't have access to music file after
            // renaming.
            assertThat(imageFile.renameTo(musicFile)).isTrue();
            assertThat(getFileRowIdFromDatabase(musicFile)).isEqualTo(-1);
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherAppVideoFile.getAbsolutePath());
            uninstallApp(TEST_APP_A);
            imageFile.delete();
            videoFile.delete();
            topLevelVideoFile.delete();
            executeShellCommand("rm  " + musicFile.getAbsolutePath());
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Test that basic file path restrictions are enforced on file rename.
     */
    @Test
    public void testRenameFile() throws Exception {
        final File nonMediaDir = new File(DOWNLOAD_DIR, TEST_DIRECTORY_NAME);
        final File pdfFile1 = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME);
        final File pdfFile2 = new File(nonMediaDir, NONMEDIA_FILE_NAME);
        final File videoFile1 = new File(DCIM_DIR, VIDEO_FILE_NAME);
        final File videoFile2 = new File(MOVIES_DIR, VIDEO_FILE_NAME);
        final File videoFile3 = new File(DOWNLOAD_DIR, VIDEO_FILE_NAME);

        try {
            // Renaming non media file to media directory is not allowed.
            assertThat(pdfFile1.createNewFile()).isTrue();
            assertCantRenameFile(pdfFile1, new File(DCIM_DIR, NONMEDIA_FILE_NAME));
            assertCantRenameFile(pdfFile1, new File(MUSIC_DIR, NONMEDIA_FILE_NAME));
            assertCantRenameFile(pdfFile1, new File(MOVIES_DIR, NONMEDIA_FILE_NAME));

            // Renaming non media files to non media directories is allowed.
            if (!nonMediaDir.exists()) {
                assertThat(nonMediaDir.mkdirs()).isTrue();
            }
            // App can rename pdfFile to non media directory.
            assertCanRenameFile(pdfFile1, pdfFile2);

            assertThat(videoFile1.createNewFile()).isTrue();
            // App can rename video file to Movies directory
            assertCanRenameFile(videoFile1, videoFile2);
            // App can rename video file to Download directory
            assertCanRenameFile(videoFile2, videoFile3);
        } finally {
            pdfFile1.delete();
            pdfFile2.delete();
            videoFile1.delete();
            videoFile2.delete();
            videoFile3.delete();
            nonMediaDir.delete();
        }
    }

    /**
     * Test that renaming file to different mime type is allowed.
     */
    @Test
    public void testRenameFileType() throws Exception {
        final File pdfFile = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME);
        final File videoFile = new File(DCIM_DIR, VIDEO_FILE_NAME);
        try {
            assertThat(pdfFile.createNewFile()).isTrue();
            assertThat(videoFile.exists()).isFalse();
            // Moving pdfFile to DCIM directory is not allowed.
            assertCantRenameFile(pdfFile, new File(DCIM_DIR, NONMEDIA_FILE_NAME));
            // However, moving pdfFile to DCIM directory with changing the mime type to video is
            // allowed.
            assertCanRenameFile(pdfFile, videoFile);

            // On rename, MediaProvider database entry for pdfFile should be updated with new
            // videoFile path and mime type should be updated to video/mp4.
            assertThat(getFileMimeTypeFromDatabase(videoFile)).isEqualTo("video/mp4");
        } finally {
            pdfFile.delete();
            videoFile.delete();
        }
    }

    /**
     * Test that renaming files overwrites files in newPath.
     */
    @Test
    public void testRenameAndReplaceFile() throws Exception {
        final File videoFile1 = new File(DCIM_DIR, VIDEO_FILE_NAME);
        final File videoFile2 = new File(MOVIES_DIR, VIDEO_FILE_NAME);
        final ContentResolver cr = getContentResolver();
        try {
            assertThat(videoFile1.createNewFile()).isTrue();
            assertThat(videoFile2.createNewFile()).isTrue();
            final Uri uriVideoFile1 = MediaStore.scanFile(cr, videoFile1);
            final Uri uriVideoFile2 = MediaStore.scanFile(cr, videoFile2);

            // Renaming a file which replaces file in newPath videoFile2 is allowed.
            assertCanRenameFile(videoFile1, videoFile2);

            // Uri of videoFile2 should be accessible after rename.
            assertThat(cr.openFileDescriptor(uriVideoFile2, "rw")).isNotNull();
            // Uri of videoFile1 should not be accessible after rename.
            assertThrows(FileNotFoundException.class,
                    () -> { cr.openFileDescriptor(uriVideoFile1, "rw"); });
        } finally {
            videoFile1.delete();
            videoFile2.delete();
        }
    }

    /**
     * Test that app without write permission for file can't update the file.
     */
    @Test
    public void testRenameFileNotOwned() throws Exception {
        final File videoFile1 = new File(DCIM_DIR, VIDEO_FILE_NAME);
        final File videoFile2 = new File(MOVIES_DIR, VIDEO_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertThat(createFileAs(TEST_APP_A, videoFile1.getAbsolutePath())).isTrue();
            // App can't rename a file owned by TEST_APP_A.
            assertCantRenameFile(videoFile1, videoFile2);

            assertThat(videoFile2.createNewFile()).isTrue();
            // App can't rename a file to videoFile1 which is owned by TEST_APP_A
            assertCantRenameFile(videoFile2, videoFile1);
            // TODO(b/146346138): Test that app with right URI permission should be able to rename
            // the corresponding file
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, videoFile1.getAbsolutePath());
            videoFile2.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that renaming directories is allowed and aligns to default directory restrictions.
     */
    @Test
    public void testRenameDirectory() throws Exception {
        final String nonMediaDirectoryName = TEST_DIRECTORY_NAME + "NonMedia";
        final File nonMediaDirectory = new File(DOWNLOAD_DIR, nonMediaDirectoryName);
        final File pdfFile = new File(nonMediaDirectory, NONMEDIA_FILE_NAME);

        final String mediaDirectoryName = TEST_DIRECTORY_NAME + "Media";
        final File mediaDirectory1 = new File(DCIM_DIR, mediaDirectoryName);
        final File videoFile1 = new File(mediaDirectory1, VIDEO_FILE_NAME);
        final File mediaDirectory2 = new File(DOWNLOAD_DIR, mediaDirectoryName);
        final File videoFile2 = new File(mediaDirectory2, VIDEO_FILE_NAME);
        final File mediaDirectory3 = new File(MOVIES_DIR, TEST_DIRECTORY_NAME);
        final File videoFile3 = new File(mediaDirectory3, VIDEO_FILE_NAME);
        final File mediaDirectory4 = new File(mediaDirectory3, mediaDirectoryName);

        try {
            if (!nonMediaDirectory.exists()) {
                assertThat(nonMediaDirectory.mkdirs()).isTrue();
            }
            assertThat(pdfFile.createNewFile()).isTrue();
            // Move directory with pdf file to DCIM directory is not allowed.
            assertThat(nonMediaDirectory.renameTo(new File(DCIM_DIR, nonMediaDirectoryName)))
                    .isFalse();

            if (!mediaDirectory1.exists()) {
                assertThat(mediaDirectory1.mkdirs()).isTrue();
            }
            assertThat(videoFile1.createNewFile()).isTrue();
            // Renaming to and from default directories is not allowed.
            assertThat(mediaDirectory1.renameTo(DCIM_DIR)).isFalse();
            // Moving top level default directories is not allowed.
            assertCantRenameDirectory(DOWNLOAD_DIR, new File(DCIM_DIR, TEST_DIRECTORY_NAME), null);

            // Moving media directory to Download directory is allowed.
            assertCanRenameDirectory(mediaDirectory1, mediaDirectory2, new File[] {videoFile1},
                    new File[] {videoFile2});

            // Moving media directory to Movies directory and renaming directory in new path is
            // allowed.
            assertCanRenameDirectory(mediaDirectory2, mediaDirectory3, new File[] {videoFile2},
                    new File[] {videoFile3});

            // Can't rename a mediaDirectory to non empty non Media directory.
            assertCantRenameDirectory(mediaDirectory3, nonMediaDirectory, new File[] {videoFile3});
            // Can't rename a file to a directory.
            assertCantRenameFile(videoFile3, mediaDirectory3);
            // Can't rename a directory to file.
            assertCantRenameDirectory(mediaDirectory3, pdfFile, null);
            if (!mediaDirectory4.exists()) {
                assertThat(mediaDirectory4.mkdir()).isTrue();
            }
            // Can't rename a directory to subdirectory of itself.
            assertCantRenameDirectory(mediaDirectory3, mediaDirectory4, new File[] {videoFile3});

        } finally {
            pdfFile.delete();
            nonMediaDirectory.delete();

            videoFile1.delete();
            videoFile2.delete();
            videoFile3.delete();
            mediaDirectory1.delete();
            mediaDirectory2.delete();
            mediaDirectory3.delete();
            mediaDirectory4.delete();
        }
    }

    /**
     * Test that renaming directory checks file ownership permissions.
     */
    @Test
    public void testRenameDirectoryNotOwned() throws Exception {
        final String mediaDirectoryName = TEST_DIRECTORY_NAME + "Media";
        File mediaDirectory1 = new File(DCIM_DIR, mediaDirectoryName);
        File mediaDirectory2 = new File(MOVIES_DIR, mediaDirectoryName);
        File videoFile = new File(mediaDirectory1, VIDEO_FILE_NAME);

        try {
            installApp(TEST_APP_A);

            if (!mediaDirectory1.exists()) {
                assertThat(mediaDirectory1.mkdirs()).isTrue();
            }
            assertThat(createFileAs(TEST_APP_A, videoFile.getAbsolutePath())).isTrue();
            // App doesn't have access to videoFile1, can't rename mediaDirectory1.
            assertThat(mediaDirectory1.renameTo(mediaDirectory2)).isFalse();
            assertThat(videoFile.exists()).isTrue();
            // Test app can delete the file since the file is not moved to new directory.
            assertThat(deleteFileAs(TEST_APP_A, videoFile.getAbsolutePath())).isTrue();
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, videoFile.getAbsolutePath());
            uninstallAppNoThrow(TEST_APP_A);
            mediaDirectory1.delete();
        }
    }

    /**
     * Test renaming empty directory is allowed
     */
    @Test
    public void testRenameEmptyDirectory() throws Exception {
        final String emptyDirectoryName = TEST_DIRECTORY_NAME + "Media";
        File emptyDirectoryOldPath = new File(DCIM_DIR, emptyDirectoryName);
        File emptyDirectoryNewPath = new File(MOVIES_DIR, TEST_DIRECTORY_NAME);
        try {
            if (emptyDirectoryOldPath.exists()) {
                executeShellCommand("rm -r " + emptyDirectoryOldPath.getPath());
            }
            assertThat(emptyDirectoryOldPath.mkdirs()).isTrue();
            assertCanRenameDirectory(emptyDirectoryOldPath, emptyDirectoryNewPath, null, null);
        } finally {
            emptyDirectoryOldPath.delete();
            emptyDirectoryNewPath.delete();
        }
    }

    @Test
    public void testManageExternalStorageCanCreateFilesAnywhere() throws Exception {
        final File topLevelPdf = new File(EXTERNAL_STORAGE_DIR, NONMEDIA_FILE_NAME);
        final File musicFileInMovies = new File(MOVIES_DIR, AUDIO_FILE_NAME);
        final File imageFileInDcim = new File(DCIM_DIR, IMAGE_FILE_NAME);
        try {
            allowAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
            // Nothing special about this, anyone can create an image file in DCIM
            assertCanCreateFile(imageFileInDcim);
            // This is where we see the special powers of MANAGE_EXTERNAL_STORAGE, because it can
            // create a top level file
            assertCanCreateFile(topLevelPdf);
            // It can even create a music file in Pictures
            assertCanCreateFile(musicFileInMovies);
        } finally {
            denyAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
        }
    }

    /**
     * Test that apps can create and delete hidden file.
     */
    @Test
    public void testCanCreateHiddenFile() throws Exception {
        final File hiddenImageFile = new File(DOWNLOAD_DIR, ".hiddenFile" + IMAGE_FILE_NAME);
        try {
            assertThat(hiddenImageFile.createNewFile()).isTrue();
            // Write to hidden file is allowed.
            try (final FileOutputStream fos = new FileOutputStream(hiddenImageFile)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(hiddenImageFile, BYTES_DATA1);

            assertNotMediaTypeImage(hiddenImageFile);

            assertDirectoryContains(DOWNLOAD_DIR, hiddenImageFile);
            assertThat(getFileRowIdFromDatabase(hiddenImageFile)).isNotEqualTo(-1);

            // We can delete hidden file
            assertThat(hiddenImageFile.delete()).isTrue();
            assertThat(hiddenImageFile.exists()).isFalse();
        } finally {
            hiddenImageFile.delete();
        }
    }

    /**
     * Test that apps can rename a hidden file.
     */
    @Test
    public void testCanRenameHiddenFile() throws Exception {
        final String hiddenFileName = ".hidden" + IMAGE_FILE_NAME;
        final File hiddenImageFile1 = new File(DCIM_DIR, hiddenFileName);
        final File hiddenImageFile2 = new File(DOWNLOAD_DIR, hiddenFileName);
        final File imageFile = new File(DOWNLOAD_DIR, IMAGE_FILE_NAME);
        try {
            assertThat(hiddenImageFile1.createNewFile()).isTrue();
            assertCanRenameFile(hiddenImageFile1, hiddenImageFile2);
            assertNotMediaTypeImage(hiddenImageFile2);

            // We can also rename hidden file to non-hidden
            assertCanRenameFile(hiddenImageFile2, imageFile);
            assertIsMediaTypeImage(imageFile);

            // We can rename non-hidden file to hidden
            assertCanRenameFile(imageFile, hiddenImageFile1);
            assertNotMediaTypeImage(hiddenImageFile1);
        } finally {
            hiddenImageFile1.delete();
            hiddenImageFile2.delete();
            imageFile.delete();
        }
    }

    /**
     * Test that files in hidden directory have MEDIA_TYPE=MEDIA_TYPE_NONE
     */
    @Test
    public void testHiddenDirectory() throws Exception {
        final File hiddenDir = new File(DOWNLOAD_DIR, ".hidden" + TEST_DIRECTORY_NAME);
        final File hiddenImageFile = new File(hiddenDir, IMAGE_FILE_NAME);
        final File nonHiddenDir = new File(DOWNLOAD_DIR, TEST_DIRECTORY_NAME);
        final File imageFile = new File(nonHiddenDir, IMAGE_FILE_NAME);
        try {
            if (!hiddenDir.exists()) {
                assertThat(hiddenDir.mkdir()).isTrue();
            }
            assertThat(hiddenImageFile.createNewFile()).isTrue();

            assertNotMediaTypeImage(hiddenImageFile);

            // Renaming hiddenDir to nonHiddenDir makes the imageFile non-hidden and vice versa
            assertCanRenameDirectory(
                    hiddenDir, nonHiddenDir, new File[] {hiddenImageFile}, new File[] {imageFile});
            assertIsMediaTypeImage(imageFile);

            assertCanRenameDirectory(
                    nonHiddenDir, hiddenDir, new File[] {imageFile}, new File[] {hiddenImageFile});
            assertNotMediaTypeImage(hiddenImageFile);
        } finally {
            hiddenImageFile.delete();
            imageFile.delete();
            hiddenDir.delete();
            nonHiddenDir.delete();
        }
    }

    /**
     * Test that files in directory with nomedia have MEDIA_TYPE=MEDIA_TYPE_NONE
     */
    @Test
    public void testHiddenDirectory_nomedia() throws Exception {
        final File directoryNoMedia = new File(DOWNLOAD_DIR, "nomedia" + TEST_DIRECTORY_NAME);
        final File noMediaFile = new File(directoryNoMedia, ".nomedia");
        final File imageFile = new File(directoryNoMedia, IMAGE_FILE_NAME);
        final File videoFile = new File(directoryNoMedia, VIDEO_FILE_NAME);
        try {
            if (!directoryNoMedia.exists()) {
                assertThat(directoryNoMedia.mkdir()).isTrue();
            }
            assertThat(noMediaFile.createNewFile()).isTrue();
            assertThat(imageFile.createNewFile()).isTrue();

            assertNotMediaTypeImage(imageFile);

            // Deleting the .nomedia file makes the parent directory non hidden.
            noMediaFile.delete();
            MediaStore.scanFile(getContentResolver(), directoryNoMedia);
            assertIsMediaTypeImage(imageFile);

            // Creating the .nomedia file makes the parent directory hidden again
            assertThat(noMediaFile.createNewFile()).isTrue();
            MediaStore.scanFile(getContentResolver(), directoryNoMedia);
            assertNotMediaTypeImage(imageFile);

            // Renaming the .nomedia file to non hidden file makes the parent directory non hidden.
            assertCanRenameFile(noMediaFile, videoFile);
            assertIsMediaTypeImage(imageFile);
        } finally {
            noMediaFile.delete();
            imageFile.delete();
            videoFile.delete();
            directoryNoMedia.delete();
        }
    }

    /**
     * Test that only file manager and app that created the hidden file can list it.
     */
    @Test
    public void testListHiddenFile() throws Exception {
        final String hiddenImageFileName = ".hidden" + IMAGE_FILE_NAME;
        final File hiddenImageFile = new File(DCIM_DIR, hiddenImageFileName);
        try {
            assertThat(hiddenImageFile.createNewFile()).isTrue();
            assertNotMediaTypeImage(hiddenImageFile);

            assertDirectoryContains(DCIM_DIR, hiddenImageFile);

            installApp(TEST_APP_A, true);
            // TestApp with read permissions can't see the hidden image file created by other app
            assertThat(listAs(TEST_APP_A, DCIM_DIR.getAbsolutePath()))
                    .doesNotContain(hiddenImageFileName);

            final int testAppUid =
                    getContext().getPackageManager().getPackageUid(TEST_APP_A.getPackageName(), 0);
            // FileManager can see the hidden image file created by other app
            try {
                allowAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
                assertThat(listAs(TEST_APP_A, DCIM_DIR.getAbsolutePath()))
                        .contains(hiddenImageFileName);
            } finally {
                denyAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
            }

            // Gallery can not see the hidden image file created by other app
            try {
                allowAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
                assertThat(listAs(TEST_APP_A, DCIM_DIR.getAbsolutePath()))
                        .doesNotContain(hiddenImageFileName);
            } finally {
                denyAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
            }
        } finally {
            hiddenImageFile.delete();
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    @Test
    public void testManageExternalStorageCanDeleteOtherAppsContents() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File otherAppImage = new File(DCIM_DIR, "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(MUSIC_DIR, "other" + AUDIO_FILE_NAME);
        try {
            installApp(TEST_APP_A);

            // Create all of the files as another app
            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppImage.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppMusic.getPath())).isTrue();

            allowAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);

            assertThat(otherAppPdf.delete()).isTrue();
            assertThat(otherAppPdf.exists()).isFalse();

            assertThat(otherAppImage.delete()).isTrue();
            assertThat(otherAppImage.exists()).isFalse();

            assertThat(otherAppMusic.delete()).isTrue();
            assertThat(otherAppMusic.exists()).isFalse();
        } finally {
            denyAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppImage.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppMusic.getAbsolutePath());
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testAccess_file() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);

        final File otherAppPdf = new File(DOWNLOAD_DIR, "other-" + NONMEDIA_FILE_NAME);
        final File otherAppImage = new File(DCIM_DIR, "other-" + IMAGE_FILE_NAME);
        final File myAppPdf = new File(DOWNLOAD_DIR, "my-" + NONMEDIA_FILE_NAME);
        final File doesntExistPdf = new File(DOWNLOAD_DIR, "nada-" + NONMEDIA_FILE_NAME);

        try {
            installApp(TEST_APP_A);

            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppImage.getPath())).isTrue();

            // We can read our image and pdf files.
            assertThat(myAppPdf.createNewFile()).isTrue();
            assertFileAccess_readWrite(myAppPdf);

            // We can read the other app's image file because we hold R_E_S, but we can only
            // check exists for the pdf file.
            assertFileAccess_readOnly(otherAppImage);
            assertFileAccess_existsOnly(otherAppPdf);
            assertAccess(doesntExistPdf, false, false, false);
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppImage.getAbsolutePath());
            myAppPdf.delete();
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testAccess_directory() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);
        pollForPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, /*granted*/ true);
        try {
            installApp(TEST_APP_A);

            // Let app A create a file in its data dir
            final File otherAppExternalDataDir = new File(EXTERNAL_FILES_DIR.getPath().replace(
                    THIS_PACKAGE_NAME, TEST_APP_A.getPackageName()));
            final File otherAppExternalDataSubDir = new File(otherAppExternalDataDir, "subdir");
            final File otherAppExternalDataFile = new File(otherAppExternalDataSubDir, "abc.jpg");
            assertThat(createFileAs(TEST_APP_A, otherAppExternalDataFile.getAbsolutePath()))
                    .isTrue();

            // TODO(152645823): Readd app data dir testss
            //            // We cannot read or write the file, but app A can.
            //            assertThat(canReadAndWriteAs(TEST_APP_A,
            //                    otherAppExternalDataFile.getAbsolutePath())).isTrue();
            //            assertAccess(otherAppExternalDataFile, true, false, false);
            //
            //            // We cannot read or write the dir, but app A can.
            //            assertThat(canReadAndWriteAs(TEST_APP_A,
            //                    otherAppExternalDataDir.getAbsolutePath())).isTrue();
            //            assertAccess(otherAppExternalDataDir, true, false, false);
            //
            //            // We cannot read or write the sub dir, but app A can.
            //            assertThat(canReadAndWriteAs(TEST_APP_A,
            //                    otherAppExternalDataSubDir.getAbsolutePath())).isTrue();
            //            assertAccess(otherAppExternalDataSubDir, true, false, false);
            //
            //            // We can read and write our own app dir, but app A cannot.
            //            assertThat(canReadAndWriteAs(TEST_APP_A,
            //                    EXTERNAL_FILES_DIR.getAbsolutePath())).isFalse();
            assertAccess(EXTERNAL_FILES_DIR, true, true, true);

            assertDirectoryAccess(DCIM_DIR, /* exists */ true);
            assertDirectoryAccess(EXTERNAL_STORAGE_DIR, true);
            assertDirectoryAccess(new File(EXTERNAL_STORAGE_DIR, "Android"), true);
            assertDirectoryAccess(new File(EXTERNAL_STORAGE_DIR, "doesnt/exist"), false);
        } finally {
            uninstallApp(TEST_APP_A); // Uninstalling deletes external app dirs
        }
    }

    @Test
    public void testManageExternalStorageCanRenameOtherAppsContents() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File pdf = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME);
        final File pdfInObviouslyWrongPlace = new File(PICTURES_DIR, NONMEDIA_FILE_NAME);
        final File topLevelPdf = new File(EXTERNAL_STORAGE_DIR, NONMEDIA_FILE_NAME);
        final File musicFile = new File(MUSIC_DIR, AUDIO_FILE_NAME);
        try {
            installApp(TEST_APP_A);

            // Have another app create a PDF
            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(otherAppPdf.exists()).isTrue();

            allowAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);

            // Write some data to the file
            try (final FileOutputStream fos = new FileOutputStream(otherAppPdf)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(otherAppPdf, BYTES_DATA1);

            // Assert we can rename the file and ensure the file has the same content
            assertCanRenameFile(otherAppPdf, pdf);
            assertFileContent(pdf, BYTES_DATA1);
            // We can even move it to the top level directory
            assertCanRenameFile(pdf, topLevelPdf);
            assertFileContent(topLevelPdf, BYTES_DATA1);
            // And even rename to a place where PDFs don't belong, because we're an omnipotent
            // external storage manager
            assertCanRenameFile(topLevelPdf, pdfInObviouslyWrongPlace);
            assertFileContent(pdfInObviouslyWrongPlace, BYTES_DATA1);

            // And we can even convert it into a music file, because why not?
            assertCanRenameFile(pdfInObviouslyWrongPlace, musicFile);
            assertFileContent(musicFile, BYTES_DATA1);
        } finally {
            pdf.delete();
            pdfInObviouslyWrongPlace.delete();
            topLevelPdf.delete();
            musicFile.delete();
            denyAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testCanCreateDefaultDirectory() throws Exception {
        try {
            if (PODCASTS_DIR.exists()) {
                // Apps can't delete top level directories, not even default directories, so we let
                // shell do the deed for us.
                executeShellCommand("rm -r " + PODCASTS_DIR);
            }
            assertThat(PODCASTS_DIR.mkdir()).isTrue();
        } finally {
            executeShellCommand("mkdir " + PODCASTS_DIR);
        }
    }

    @Test
    public void testManageExternalStorageReaddir() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(DCIM_DIR, "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(MUSIC_DIR, "other" + AUDIO_FILE_NAME);
        final File otherTopLevelFile = new File(EXTERNAL_STORAGE_DIR, "other" + NONMEDIA_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            executeShellCommand("touch " + otherTopLevelFile);

            allowAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);

            // We can list other apps' files
            assertDirectoryContains(otherAppPdf.getParentFile(), otherAppPdf);
            assertDirectoryContains(otherAppImg.getParentFile(), otherAppImg);
            assertDirectoryContains(otherAppMusic.getParentFile(), otherAppMusic);
            // We can list top level files
            assertDirectoryContains(EXTERNAL_STORAGE_DIR, otherTopLevelFile);

            // We can also list all top level directories
            assertDirectoryContains(EXTERNAL_STORAGE_DIR, DEFAULT_TOP_LEVEL_DIRS);
        } finally {
            denyAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
            executeShellCommand("rm " + otherTopLevelFile);
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testManageExternalStorageQueryOtherAppsFile() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(DCIM_DIR, "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(MUSIC_DIR, "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(PICTURES_DIR, ".otherHiddenFile.jpg");
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(
                    TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            // Once the test has permission to manage external storage, it can query for other
            // apps' files and open them for read and write
            allowAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);

            assertCanQueryAndOpenFile(otherAppPdf, "rw");
            assertCanQueryAndOpenFile(otherAppImg, "rw");
            assertCanQueryAndOpenFile(otherAppMusic, "rw");
            assertCanQueryAndOpenFile(otherHiddenFile, "rw");
        } finally {
            denyAppOpsToUid(Process.myUid(), OPSTR_MANAGE_EXTERNAL_STORAGE);
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testQueryOtherAppsFiles() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(DCIM_DIR, "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(MUSIC_DIR, "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(PICTURES_DIR, ".otherHiddenFile.jpg");
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(
                    TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            // Since the test doesn't have READ_EXTERNAL_STORAGE nor any other special permissions,
            // it can't query for another app's contents.
            assertCantQueryFile(otherAppImg);
            assertCantQueryFile(otherAppMusic);
            assertCantQueryFile(otherAppPdf);
            assertCantQueryFile(otherHiddenFile);
        } finally {
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testSystemGalleryQueryOtherAppsFiles() throws Exception {
        final File otherAppPdf = new File(DOWNLOAD_DIR, "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(DCIM_DIR, "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(MUSIC_DIR, "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(PICTURES_DIR, ".otherHiddenFile.jpg");
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(
                    TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            // System gallery apps have access to video and image files
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertCanQueryAndOpenFile(otherAppImg, "rw");
            // System gallery doesn't have access to hidden image files of other app
            assertCantQueryFile(otherHiddenFile);
            // But no access to PDFs or music files
            assertCantQueryFile(otherAppMusic);
            assertCantQueryFile(otherAppPdf);
        } finally {
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
            uninstallApp(TEST_APP_A);
        }
    }

    /**
     * Test that System Gallery app can rename any directory under the default directories
     * designated for images and videos, even if they contain other apps' contents that
     * System Gallery doesn't have read access to.
     */
    @Test
    public void testSystemGalleryCanRenameImageAndVideoDirs() throws Exception {
        final File dirInDcim = new File(DCIM_DIR, TEST_DIRECTORY_NAME);
        final File dirInPictures = new File(PICTURES_DIR, TEST_DIRECTORY_NAME);
        final File dirInPodcasts = new File(PODCASTS_DIR, TEST_DIRECTORY_NAME);
        final File otherAppImageFile1 = new File(dirInDcim, "other_" + IMAGE_FILE_NAME);
        final File otherAppVideoFile1 = new File(dirInDcim, "other_" + VIDEO_FILE_NAME);
        final File otherAppPdfFile1 = new File(dirInDcim, "other_" + NONMEDIA_FILE_NAME);
        final File otherAppImageFile2 = new File(dirInPictures, "other_" + IMAGE_FILE_NAME);
        final File otherAppVideoFile2 = new File(dirInPictures, "other_" + VIDEO_FILE_NAME);
        final File otherAppPdfFile2 = new File(dirInPictures, "other_" + NONMEDIA_FILE_NAME);
        try {
            assertThat(dirInDcim.exists() || dirInDcim.mkdir()).isTrue();

            executeShellCommand("touch " + otherAppPdfFile1);

            installAppWithStoragePermissions(TEST_APP_A);
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertCreateFilesAs(TEST_APP_A, otherAppImageFile1, otherAppVideoFile1);

            // System gallery privileges don't go beyond DCIM, Movies and Pictures boundaries.
            assertCantRenameDirectory(dirInDcim, dirInPodcasts, /*oldFilesList*/ null);

            // Rename should succeed, but System Gallery still can't access that PDF file!
            assertCanRenameDirectory(dirInDcim, dirInPictures,
                    new File[] {otherAppImageFile1, otherAppVideoFile1},
                    new File[] {otherAppImageFile2, otherAppVideoFile2});
            assertThat(getFileRowIdFromDatabase(otherAppPdfFile1)).isEqualTo(-1);
            assertThat(getFileRowIdFromDatabase(otherAppPdfFile2)).isEqualTo(-1);
        } finally {
            executeShellCommand("rm " + otherAppPdfFile1);
            executeShellCommand("rm " + otherAppPdfFile2);
            otherAppImageFile1.delete();
            otherAppImageFile2.delete();
            otherAppVideoFile1.delete();
            otherAppVideoFile2.delete();
            otherAppPdfFile1.delete();
            otherAppPdfFile2.delete();
            dirInDcim.delete();
            dirInPictures.delete();
            uninstallAppNoThrow(TEST_APP_A);
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Test that row ID corresponding to deleted path is restored on subsequent create.
     */
    @Test
    public void testCreateCanRestoreDeletedRowId() throws Exception {
        final File imageFile = new File(DCIM_DIR, IMAGE_FILE_NAME);
        final ContentResolver cr = getContentResolver();

        try {
            assertThat(imageFile.createNewFile()).isTrue();
            final long oldRowId = getFileRowIdFromDatabase(imageFile);
            assertThat(oldRowId).isNotEqualTo(-1);
            final Uri uriOfOldFile = MediaStore.scanFile(cr, imageFile);
            assertThat(uriOfOldFile).isNotNull();

            assertThat(imageFile.delete()).isTrue();
            // We should restore old row Id corresponding to deleted imageFile.
            assertThat(imageFile.createNewFile()).isTrue();
            assertThat(getFileRowIdFromDatabase(imageFile)).isEqualTo(oldRowId);
            assertThat(cr.openFileDescriptor(uriOfOldFile, "rw")).isNotNull();

            assertThat(imageFile.delete()).isTrue();
            installApp(TEST_APP_A);
            assertThat(createFileAs(TEST_APP_A, imageFile.getAbsolutePath())).isTrue();

            final Uri uriOfNewFile = MediaStore.scanFile(getContentResolver(), imageFile);
            assertThat(uriOfNewFile).isNotNull();
            // We shouldn't restore deleted row Id if delete & create are called from different apps
            assertThat(Integer.getInteger(uriOfNewFile.getLastPathSegment())).isNotEqualTo(oldRowId);
        } finally {
            imageFile.delete();
            deleteFileAsNoThrow(TEST_APP_A, imageFile.getAbsolutePath());
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    /**
     * Test that row ID corresponding to deleted path is restored on subsequent rename.
     */
    @Test
    public void testRenameCanRestoreDeletedRowId() throws Exception {
        final File imageFile = new File(DCIM_DIR, IMAGE_FILE_NAME);
        final File temporaryFile = new File(DOWNLOAD_DIR, IMAGE_FILE_NAME + "_.tmp");
        final ContentResolver cr = getContentResolver();

        try {
            assertThat(imageFile.createNewFile()).isTrue();
            final Uri oldUri = MediaStore.scanFile(cr, imageFile);
            assertThat(oldUri).isNotNull();

            Files.copy(imageFile, temporaryFile);
            assertThat(imageFile.delete()).isTrue();
            assertCanRenameFile(temporaryFile, imageFile);

            final Uri newUri = MediaStore.scanFile(cr, imageFile);
            assertThat(newUri).isNotNull();
            assertThat(newUri.getLastPathSegment()).isEqualTo(oldUri.getLastPathSegment());
            // oldUri of imageFile is still accessible after delete and rename.
            assertThat(cr.openFileDescriptor(oldUri, "rw")).isNotNull();
        } finally {
            imageFile.delete();
            temporaryFile.delete();
        }
    }

    @Test
    public void testCantCreateOrRenameFileWithInvalidName() throws Exception {
        File invalidFile = new File(DOWNLOAD_DIR, "<>");
        File validFile = new File(DOWNLOAD_DIR, NONMEDIA_FILE_NAME);
        try {
            assertThrows(IOException.class, "Operation not permitted",
                    () -> { invalidFile.createNewFile(); });

            assertThat(validFile.createNewFile()).isTrue();
            // We can't rename a file to a file name with invalid FAT characters.
            assertCantRenameFile(validFile, invalidFile);
        } finally {
            invalidFile.delete();
            validFile.delete();
        }
    }

    private static void assertIsMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(1, c.getCount());
    }

    private static void assertNotMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(0, c.getCount());
    }

    private static void assertCantQueryFile(File file) { assertThat(getFileUri(file)).isNull(); }

    private static void assertCreateFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            assertThat(createFileAs(testApp, file.getPath())).isTrue();
        }
    }

    private static void deleteFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            deleteFileAs(testApp, file.getPath());
        }
    }

    /**
     * For possible values of {@code mode}, look at {@link android.content.ContentProvider#openFile}
     */
    private static void assertCanQueryAndOpenFile(File file, String mode) throws IOException {
        // This call performs the query
        final Uri fileUri = getFileUri(file);
        // The query succeeds iff it didn't return null
        assertThat(fileUri).isNotNull();
        // Now we assert that we can open the file through ContentResolver
        try (final ParcelFileDescriptor pfd =
                        getContentResolver().openFileDescriptor(fileUri, mode)) {
            assertThat(pfd).isNotNull();
        }
    }

    /**
     * Assert that the last read in: read - write - read using {@code readFd} and {@code writeFd}
     * see the last write. {@code readFd} and {@code writeFd} are fds pointing to the same
     * underlying file on disk but may be derived from different mount points and in that case
     * have separate VFS caches.
     */
    private void assertRWR(ParcelFileDescriptor readPfd, ParcelFileDescriptor writePfd)
            throws Exception {
        FileDescriptor readFd = readPfd.getFileDescriptor();
        FileDescriptor writeFd = writePfd.getFileDescriptor();

        byte[] readBuffer = new byte[10];
        byte[] writeBuffer = new byte[10];
        Arrays.fill(writeBuffer, (byte) 1);

        // Write so readFd has content to read from next
        Os.pwrite(readFd, readBuffer, 0, 10, 0);
        // Read so readBuffer is in readFd's mount VFS cache
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that readBuffer is zeroes
        assertThat(readBuffer).isEqualTo(new byte[10]);

        // Write so writeFd and readFd should now see writeBuffer
        Os.pwrite(writeFd, writeBuffer, 0, 10, 0);

        // Read so the last write can be verified on readFd
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that the last write is indeed visible via readFd
        assertThat(readBuffer).isEqualTo(writeBuffer);
        assertThat(readPfd.getStatSize()).isEqualTo(writePfd.getStatSize());
    }

    private void assertLowerFsFd(ParcelFileDescriptor pfd) throws Exception {
        assertThat(Os.readlink("/proc/self/fd/" + pfd.getFd()).startsWith("/storage")).isTrue();
    }

    private void assertUpperFsFd(ParcelFileDescriptor pfd) throws Exception {
        assertThat(Os.readlink("/proc/self/fd/" + pfd.getFd()).startsWith("/mnt/user")).isTrue();
    }

    private static void assertCanCreateFile(File file) throws IOException {
        // If the file somehow managed to survive a previous run, then the test app was uninstalled
        // and MediaProvider will remove our its ownership of the file, so it's not guaranteed that
        // we can create nor delete it.
        if (!file.exists()) {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.delete()).isTrue();
        } else {
            Log.w(TAG,
                    "Couldn't assertCanCreateFile(" + file + ") because file existed prior to "
                            + "running the test!");
        }
    }

    private static void assertFileAccess_existsOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, false, false);
    }

    private static void assertFileAccess_readOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, true, false);
    }

    private static void assertFileAccess_readWrite(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, true, true);
    }

    private static void assertDirectoryAccess(File dir, boolean exists) throws Exception {
        // This util does not handle app data directories.
        assumeFalse(dir.getAbsolutePath().startsWith(ANDROID_DIR.getAbsolutePath())
                && !dir.equals(ANDROID_DIR));
        assertThat(dir.isDirectory()).isEqualTo(exists);
        // For non-app data directories, exists => canRead() and canWrite().
        assertAccess(dir, exists, exists, exists);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite)
            throws Exception {
        assertThat(file.exists()).isEqualTo(exists);
        assertThat(file.canRead()).isEqualTo(canRead);
        assertThat(file.canWrite()).isEqualTo(canWrite);
        if (file.isDirectory()) {
            assertThat(file.canExecute()).isEqualTo(exists);
        } else {
            assertThat(file.canExecute()).isFalse(); // Filesytem is mounted with MS_NOEXEC
        }

        // Test some combinations of mask.
        assertAccess(file, R_OK, canRead);
        assertAccess(file, W_OK, canWrite);
        assertAccess(file, R_OK | W_OK, canRead && canWrite);
        assertAccess(file, W_OK | F_OK, canWrite);
        assertAccess(file, F_OK, exists);
    }

    private static void assertAccess(File file, int mask, boolean expected) throws Exception {
        if (expected) {
            assertThat(Os.access(file.getAbsolutePath(), mask)).isTrue();
        } else {
            assertThrows(ErrnoException.class, () -> { Os.access(file.getAbsolutePath(), mask); });
        }
    }
}
