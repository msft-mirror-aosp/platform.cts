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
package android.providerui.cts;

import static android.provider.cts.media.MediaProviderTestUtils.resolveVolumeName;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MediaStoreUiTestBase {
    private static final String TAG = "MediaStoreUiTestBase";

    protected static final String EXTERNAL_STORAGE_PROVIDER_AUTHORITY =
            "com.android.externalstorage.documents";

    protected Context mContext;
    protected UiDevice mDevice;
    protected GetResultActivity mActivity;

    protected File mFile;
    protected Uri mMediaStoreUri;
    protected String mTargetPackageName;
    protected String mDocumentsUiPackageId;

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(instrumentation);
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent2 = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent2.addCategory(Intent.CATEGORY_OPENABLE);
        intent2.setType("*/*");
        final ResolveInfo ri = pm.resolveActivity(intent2, 0);
        mDocumentsUiPackageId = ri.activityInfo.packageName;

        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity = (GetResultActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        mActivity.clearResult();
        mDevice.wakeUp();
    }

    @After
    public void tearDown() throws Exception {
        if (mFile != null) {
            mFile.delete();
        }

        if (mActivity != null) {
            final ContentResolver resolver = mActivity.getContentResolver();
            for (UriPermission permission : resolver.getPersistedUriPermissions()) {
                mActivity.revokeUriPermission(
                        permission.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            mActivity.finish();
        }
    }

    /** Clears the DocumentsUI package data. */
    protected void clearDocumentsUi() throws Exception {
        executeShellCommand("pm clear --user current " + getDocumentsUiPackageId());
    }

    protected boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return !pm.hasSystemFeature("android.hardware.type.television")
                && !pm.hasSystemFeature("android.hardware.type.watch");
    }

    protected String getDocumentsUiPackageId() {
        return mDocumentsUiPackageId;
    }

    protected File getVolumePath(String volumeName) {
        return mContext.getSystemService(StorageManager.class)
                .getStorageVolume(MediaStore.Files.getContentUri(volumeName))
                .getDirectory();
    }

    protected void prepareFile(String volumeName) throws Exception {
        final File dir =
                new File(
                        getVolumePath(resolveVolumeName(volumeName)),
                        Environment.DIRECTORY_DOCUMENTS);
        final File file = new File(dir, "cts" + System.nanoTime() + ".txt");

        mFile = stageFile(R.raw.text, file);
        mMediaStoreUri = MediaStore.scanFile(mContext.getContentResolver(), mFile);

        Log.v(TAG, "Staged " + mFile + " as " + mMediaStoreUri);
    }

    protected void prepareFile(String volumeName, String rawText) throws Exception {
        final File dir =
                new File(
                        getVolumePath(resolveVolumeName(volumeName)),
                        Environment.DIRECTORY_DOCUMENTS);
        final File file = new File(dir, "cts" + System.nanoTime() + ".txt");

        mFile = stageFileWithRawText(rawText, file);
        mMediaStoreUri = MediaStore.scanFile(mContext.getContentResolver(), mFile);

        Log.v(TAG, "Staged " + mFile + " as " + mMediaStoreUri);
    }

    protected Pair<Uri, File> prepareFileAndFetchDetails(String volumeName, String rawText)
            throws Exception {
        final File dir =
                new File(
                        getVolumePath(resolveVolumeName(volumeName)),
                        Environment.DIRECTORY_DOCUMENTS);
        final File file = new File(dir, "cts" + System.nanoTime() + ".txt");

        File stagedFile = stageFileWithRawText(rawText, file);

        Uri uri = MediaStore.scanFile(mContext.getContentResolver(), stagedFile);
        Log.v(TAG, "Staged " + stagedFile + " as " + uri);
        return Pair.create(uri, stagedFile);
    }

    // TODO: replace with ProviderTestUtils
    static File stageFile(int resId, File file) throws IOException {
        // The caller may be trying to stage into a location only available to
        // the shell user, so we need to perform the entire copy as the shell
        final Context context = InstrumentationRegistry.getTargetContext();
        final File dir = file.getParentFile();
        dir.mkdirs();
        if (!dir.exists()) {
            throw new FileNotFoundException("Failed to create parent for " + file);
        }
        try (InputStream source = context.getResources().openRawResource(resId);
                OutputStream target = new FileOutputStream(file)) {
            FileUtils.copy(source, target);
        }
        return file;
    }

    static File stageFileWithRawText(String rawText, File file) throws IOException {
        final File dir = file.getParentFile();
        dir.mkdirs();
        if (!dir.exists()) {
            throw new FileNotFoundException("Failed to create parent for " + file);
        }
        try (InputStream source =
                        new ByteArrayInputStream(rawText.getBytes(StandardCharsets.UTF_8));
                OutputStream target = new FileOutputStream(file)) {
            FileUtils.copy(source, target);
        }
        return file;
    }

    // TODO: replace with ProviderTestUtils
    static String executeShellCommand(String command) throws IOException {
        return executeShellCommand(
                command, InstrumentationRegistry.getInstrumentation().getUiAutomation());
    }

    // TODO: replace with ProviderTestUtils
    static String executeShellCommand(String command, UiAutomation uiAutomation)
            throws IOException {
        Log.v(TAG, "$ " + command);
        ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(command.toString());
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor()); ) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                Log.v(TAG, "> " + str);
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }
}
