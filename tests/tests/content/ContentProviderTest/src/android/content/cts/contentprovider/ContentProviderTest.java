/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.cts.contentprovider;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ContentProvider;
import android.content.ContentProvider.CallingIdentity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.permissions.PermissionContext;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Test {@link ContentProvider}. */
@RunWith(BedsteadJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class ContentProviderTest {
    private static final String TAG = ContentProviderTest.class.getSimpleName();
    private static final String TEST_PACKAGE_NAME = "android.content.cts";
    private static final String TEST_FILE_NAME = "testFile.tmp";
    private static final String TEST_DB_NAME = "test.db";
    private static final String CONTENT_TEST = "content://test";
    private static final String OK = "OK";
    private static final String WRONG = "wrong";
    private static final String READ_MODE = "r";

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() throws Exception {
        sContext.deleteDatabase(TEST_DB_NAME);
        sContext.deleteFile(TEST_FILE_NAME);
    }

    @Test
    public void testOpenAssetFile() throws IOException {
        MockContentProvider mockContentProvider = new MockContentProvider();
        Uri uri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(TEST_PACKAGE_NAME)
                        .appendPath(String.valueOf(R.raw.testimage))
                        .build();

        assertThrows(
                "Should always throw out FileNotFoundException!",
                FileNotFoundException.class,
                () -> mockContentProvider.openAssetFile(uri, READ_MODE));

        assertThrows(
                "Should always throw out FileNotFoundException!",
                FileNotFoundException.class,
                () -> mockContentProvider.openFile(null, null));
    }

    @Test
    public void testAttachInfo() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        ProviderInfo info1 = new ProviderInfo();
        info1.readPermission = "android.permission.READ_SMS";
        info1.writePermission = null; // Guarded by an app op not a permission.
        mockContentProvider.attachInfo(sContext, info1);
        assertThat(mockContentProvider.getContext()).isSameInstanceAs(sContext);
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(info1.readPermission);
        assertThat(mockContentProvider.getWritePermission()).isEqualTo(info1.writePermission);

        ProviderInfo info2 = new ProviderInfo();
        info2.readPermission = "android.permission.READ_CONTACTS";
        info2.writePermission = "android.permission.WRITE_CONTACTS";
        mockContentProvider.attachInfo(null, info2);
        assertThat(mockContentProvider.getContext()).isSameInstanceAs(sContext);
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(info1.readPermission);
        assertThat(mockContentProvider.getWritePermission()).isEqualTo(info1.writePermission);

        mockContentProvider = new MockContentProvider();
        mockContentProvider.attachInfo(null, null);
        assertThat(mockContentProvider.getContext()).isNull();
        assertThat(mockContentProvider.getReadPermission()).isNull();
        assertThat(mockContentProvider.getWritePermission()).isNull();

        mockContentProvider.attachInfo(null, info2);
        assertThat(mockContentProvider.getContext()).isNull();
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(info2.readPermission);
        assertThat(mockContentProvider.getWritePermission()).isEqualTo(info2.writePermission);

        mockContentProvider.attachInfo(sContext, info1);
        assertThat(mockContentProvider.getContext()).isSameInstanceAs(sContext);
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(info1.readPermission);
        assertThat(mockContentProvider.getWritePermission()).isEqualTo(info1.writePermission);
    }

    @Test
    public void testBulkInsert() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        int count = 2;
        ContentValues[] values = new ContentValues[count];
        for (int i = 0; i < count; i++) {
            values[i] = new ContentValues();
        }
        Uri uri = Uri.parse("content://browser/bookmarks");
        assertThat(mockContentProvider.bulkInsert(uri, values)).isEqualTo(count);
        assertThat(mockContentProvider.getInsertCount()).isEqualTo(count);

        mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.bulkInsert(null, values)).isEqualTo(count);
        assertThat(mockContentProvider.getInsertCount()).isEqualTo(count);
    }

    @Test
    public void testGetContext() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.getContext()).isNull();

        mockContentProvider.attachInfo(sContext, null);
        assertThat(mockContentProvider.getContext()).isSameInstanceAs(sContext);
        mockContentProvider.attachInfo(null, null);
        assertThat(mockContentProvider.getContext()).isSameInstanceAs(sContext);
    }

    @Test
    public void testAccessReadPermission() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.getReadPermission()).isNull();

        String expected = "android.permission.READ_CONTACTS";
        mockContentProvider.setReadPermissionWrapper(expected);
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(expected);

        expected = "android.permission.READ_SMS";
        mockContentProvider.setReadPermissionWrapper(expected);
        assertThat(mockContentProvider.getReadPermission()).isEqualTo(expected);

        mockContentProvider.setReadPermissionWrapper(null);
        assertThat(mockContentProvider.getReadPermission()).isNull();
    }

    @Test
    public void testAccessWritePermission() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.getWritePermission()).isNull();

        String expected = "android.permission.WRITE_CONTACTS";
        mockContentProvider.setWritePermissionWrapper(expected);
        assertThat(mockContentProvider.getWritePermission()).isEqualTo(expected);

        mockContentProvider.setWritePermissionWrapper(null);
        assertThat(mockContentProvider.getWritePermission()).isNull();
    }

    @Test
    public void testIsTemporary() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.isTemporary()).isFalse();
    }

    @Test
    public void testOpenFile() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThrows(
                "Should always throw out FileNotFoundException!",
                FileNotFoundException.class,
                () -> mockContentProvider.openFile(Uri.parse(CONTENT_TEST), READ_MODE));

        assertThrows(
                "Should always throw out FileNotFoundException!",
                FileNotFoundException.class,
                () -> mockContentProvider.openFile(null, null));
    }

    @Test
    public void testOpenFileHelper() throws IOException {
        // create a temporary File
        sContext.openFileOutput(TEST_FILE_NAME, Context.MODE_PRIVATE).close();
        File file = sContext.getFileStreamPath(TEST_FILE_NAME);
        assertThat(file.exists()).isTrue();

        ContentProvider cp = new OpenFilePipeContentProvider(file.getAbsolutePath(), TEST_DB_NAME);

        Uri uri = Uri.parse(CONTENT_TEST);
        assertThat(cp.openFile(uri, READ_MODE)).isNotNull();
        assertThrows(
                "Should throw IllegalArgumentException for bad mode!",
                IllegalArgumentException.class,
                () -> cp.openFile(Uri.parse(CONTENT_TEST), WRONG));
        // delete the temporary file
        file.delete();
        assertThrows(
                "Should throw FileNotFoundException!",
                FileNotFoundException.class,
                () -> cp.openFile(Uri.parse(CONTENT_TEST), READ_MODE));

        assertThrows(
                "Should always throw FileNotFoundException!",
                FileNotFoundException.class,
                () -> cp.openFile(null, READ_MODE));
    }

    private static void assertAssetFileContents(
            AssetFileDescriptor assetFileDescriptor, String message) throws IOException {
        assertThat(assetFileDescriptor).isNotNull();
        try (DataInputStream dis =
                new DataInputStream(new FileInputStream(assetFileDescriptor.getFileDescriptor()))) {
            assertThat(dis.readUTF()).isEqualTo(message);
        }
    }

    @Test
    public void testOpenPipeHelper() throws IOException {
        // create a temporary File
        sContext.openFileOutput(TEST_FILE_NAME, Context.MODE_PRIVATE).close();
        File file = sContext.getFileStreamPath(TEST_FILE_NAME);
        assertThat(file.exists()).isTrue();

        ContentProvider cp = new OpenFilePipeContentProvider(file.getAbsolutePath(), TEST_DB_NAME);

        Uri uri = Uri.parse(CONTENT_TEST);
        assertAssetFileContents(cp.openAssetFile(uri, READ_MODE), OK);

        uri = Uri.parse(CONTENT_TEST);
        assertAssetFileContents(
                cp.openAssetFile(uri, WRONG),
                "java.lang.IllegalArgumentException: Bad mode: wrong");

        // delete the temporary file
        file.delete();

        uri = Uri.parse(CONTENT_TEST);
        assertAssetFileContents(
                cp.openAssetFile(uri, READ_MODE),
                "java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)");

        assertAssetFileContents(
                cp.openAssetFile(null, READ_MODE),
                "java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)");
    }

    @Test
    public void testOnConfigurationChanged() {
        // cannot trigger this callback reliably
    }

    @Test
    public void testOnLowMemory() {
        // cannot trigger this callback reliably
    }

    @Test
    public void testRefresh_DefaultImplReturnsFalse() {
        MockContentProvider provider = new MockContentProvider();
        assertThat(provider.refresh(null, null, null)).isFalse();
    }

    @Test
    public void testGetIContentProvider() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertThat(mockContentProvider.getIContentProvider()).isNotNull();
    }

    @Test
    public void testClearCallingIdentity() {
        final MockContentProvider provider = new MockContentProvider();
        provider.attachInfo(sContext, new ProviderInfo());

        final CallingIdentity ident = provider.clearCallingIdentity();
        try {
            assertThat(Binder.getCallingUid()).isEqualTo(android.os.Process.myUid());
            assertThat(provider.getCallingPackage()).isNull();
        } finally {
            provider.restoreCallingIdentity(ident);
        }
    }

    @Test
    public void testCheckUriPermission() {
        MockContentProvider provider = new MockContentProvider();
        final Uri uri = Uri.parse(CONTENT_TEST);
        assertThat(provider.checkUriPermission(uri, android.os.Process.myUid(), 0))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void testCreateContentUriForUser_nullUri_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> ContentProvider.createContentUriForUser(null, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_nonContentUri_throwsIAE() {
        final Uri uri = Uri.parse("notcontent://test");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContentProvider.createContentUriForUser(uri, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_UriWithDifferentUserID_throwsIAE() {
        final Uri uri = Uri.parse("content://07@Test");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContentProvider.createContentUriForUser(uri, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_UriWithUserID_unchanged() {
        final Uri uri = Uri.parse("content://7@Test");
        assertThat(ContentProvider.createContentUriForUser(uri, UserHandle.of(7))).isEqualTo(uri);
    }

    @Test
    @EnsureHasWorkProfile
    @AppModeFull
    public void createContentUriForUser_returnsCorrectUri() {
        try (UserReference userReference = workProfile(sDeviceState)) {
            final ContentResolver profileContentResolver =
                    TestApis.context().androidContextAsUser(userReference).getContentResolver();
            try (PermissionContext p =
                    TestApis.permissions()
                            .withPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)) {

                final String testContentDisplayName = "testContent.mp3";
                final Uri workProfileUriWithoutUserId =
                        createAndInsertTestAudioFile(profileContentResolver);
                UserHandle userHandle = userReference.userHandle();
                final Uri workProfileUriWithUserId =
                        ContentProvider.createContentUriForUser(
                                workProfileUriWithoutUserId, userHandle);
                assertThat(
                                getAudioContentDisplayName(
                                        sContext.getContentResolver(), workProfileUriWithUserId))
                        .isEqualTo(testContentDisplayName);
            }
        }
    }

    private Uri createAndInsertTestAudioFile(ContentResolver resolver) {
        final Uri audioCollection =
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final ContentValues testContent = new ContentValues();
        testContent.put(MediaStore.Audio.Media.DISPLAY_NAME, "testContent.mp3");
        return resolver.insert(audioCollection, testContent);
    }

    private String getAudioContentDisplayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor =
                resolver.query(
                        uri,
                        /* projection= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* sortOrder= */ null)) {
            final int nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            if (cursor.moveToNext()) {
                name = cursor.getString(nameColumn);
            }
        }
        return name;
    }

    private static final class MockContentProvider extends ContentProvider {
        private int mInsertCount = 0;

        @Override
        public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public String getType(@NonNull Uri uri) {
            return null;
        }

        @Override
        public Uri insert(@NonNull Uri uri, ContentValues values) {
            mInsertCount++;
            return null;
        }

        public int getInsertCount() {
            return mInsertCount;
        }

        @Override
        public Cursor query(
                @NonNull Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder) {
            return null;
        }

        @Override
        public int update(
                @NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public boolean onCreate() {
            return false;
        }

        // wrapper or override for the protected methods
        public void setReadPermissionWrapper(String permission) {
            super.setReadPermission(permission);
        }

        public void setWritePermissionWrapper(String permission) {
            super.setWritePermission(permission);
        }

        @Override
        protected boolean isTemporary() {
            return super.isTemporary();
        }
    }

    /**
     * This provider implements openFile/openAssetFile() using
     * ContentProvider.openFileHelper/openPipeHelper().
     */
    private static final class OpenFilePipeContentProvider extends ContentProvider
            implements ContentProvider.PipeDataWriter<String> {
        private final SQLiteDatabase mDb;

        OpenFilePipeContentProvider(String fileName, String dbName) {
            // delete the database if it already exists
            sContext.deleteDatabase(dbName);
            mDb = sContext.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
            mDb.execSQL("CREATE TABLE files ( _data TEXT );");
            mDb.execSQL("INSERT INTO files VALUES ( \"" + fileName + "\");");
        }

        @Override
        public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public String getType(@NonNull Uri uri) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Uri insert(@NonNull Uri uri, ContentValues values) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(
                @NonNull Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder) {
            return mDb.query("files", projection, selection, selectionArgs, null, null, null);
        }

        @Override
        public int update(
                @NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
                throws FileNotFoundException {
            return openFileHelper(uri, mode);
        }

        @Override
        public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
                throws FileNotFoundException {
            return new AssetFileDescriptor(
                    openPipeHelper(uri, "text/html", null, mode, this),
                    0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        @Override
        public void writeDataToPipe(
                @NonNull ParcelFileDescriptor output,
                @NonNull Uri uri,
                @NonNull String mimeType,
                @Nullable Bundle opts,
                @Nullable String args) {
            try (DataOutputStream dos =
                    new DataOutputStream(new FileOutputStream(output.getFileDescriptor()))) {
                try (ParcelFileDescriptor parcelFileDescriptor = openFile(uri, args)) {
                    try (InputStream unused =
                            new FileInputStream(parcelFileDescriptor.getFileDescriptor())) {
                        dos.writeUTF(OK);
                    } catch (Throwable t) {
                        dos.writeUTF(t.toString());
                        Log.e(TAG, "Error in writing to pipe", t);
                    }
                } catch (Throwable t) {
                    dos.writeUTF(t.toString());
                    Log.e(TAG, "Error in opening parcel file descriptor", t);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
