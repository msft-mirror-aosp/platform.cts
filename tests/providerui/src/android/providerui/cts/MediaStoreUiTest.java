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

package android.providerui.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_MEDIA_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO;
import static android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION;
import static android.media.MediaMetadataRetriever.METADATA_KEY_DURATION;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.providerui.cts.GetResultActivity.Result;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.content.FileProvider;
import androidx.test.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MediaStoreUiTest extends InstrumentationTestCase {
    private static final String TAG = "MediaStoreUiTest";

    private static final int REQUEST_CODE = 42;
    private static final String CONTENT = "Test";

    private UiDevice mDevice;
    private GetResultActivity mActivity;

    private File mFile;
    private Uri mMediaStoreUri;
    private String mTargetPackageName;

    @Override
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());

        final Context context = getInstrumentation().getContext();
        mActivity = launchActivity(context.getPackageName(), GetResultActivity.class, null);
        mActivity.clearResult();
    }

    @Override
    public void tearDown() throws Exception {
        if (mFile != null) {
            mFile.delete();
        }

        final ContentResolver resolver = mActivity.getContentResolver();
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            mActivity.revokeUriPermission(
                    permission.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        mActivity.finish();
    }

    public void testGetDocumentUri() throws Exception {
        if (!supportsHardware()) return;

        prepareFile();

        final Uri treeUri = acquireAccess(mFile, Environment.DIRECTORY_DOCUMENTS);
        assertNotNull(treeUri);

        final Uri docUri = MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
        assertNotNull(docUri);

        final ContentResolver resolver = mActivity.getContentResolver();
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(docUri, "rw")) {
            // Test reading
            try (final BufferedReader reader =
                         new BufferedReader(new FileReader(fd.getFileDescriptor()))) {
                assertEquals(CONTENT, reader.readLine());
            }

            // Test writing
            try (final OutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
                out.write(CONTENT.getBytes());
            }
        }
    }

    public void testGetDocumentUri_ThrowsWithoutPermission() throws Exception {
        if (!supportsHardware()) return;

        prepareFile();

        try {
            MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
            fail("Expecting SecurityException.");
        } catch (SecurityException e) {
            // Expected
        }
    }

    public void testGetDocumentUri_Symmetry() throws Exception {
        if (!supportsHardware()) return;

        prepareFile();

        final Uri treeUri = acquireAccess(mFile, Environment.DIRECTORY_DOCUMENTS);
        assertNotNull(treeUri);

        final Uri docUri = MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
        assertNotNull(docUri);

        final Uri mediaUri = MediaStore.getMediaUri(mActivity, docUri);
        assertNotNull(mediaUri);

        assertEquals(mMediaStoreUri, mediaUri);
    }

    private void maybeClick(UiSelector sel) {
        try { mDevice.findObject(sel).click(); } catch (Throwable ignored) { }
    }

    private void maybeClick(BySelector sel) {
        try { mDevice.findObject(sel).click(); } catch (Throwable ignored) { }
    }

    private void maybeGrantRuntimePermission(String pkg, Set<String> requested, String permission)
            throws NameNotFoundException {
        // We only need to grant dangerous permissions
        final Context context = getInstrumentation().getContext();
        if ((context.getPackageManager().getPermissionInfo(permission, 0).getProtection()
                & PermissionInfo.PROTECTION_DANGEROUS) == 0) {
            return;
        }

        if (requested.contains(permission)) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(pkg, permission);
        }
    }

    private void maybeRevokeRuntimePermission(String pkg, Set<String> requested, String permission)
            throws NameNotFoundException {
        if (requested.contains(permission)) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .revokeRuntimePermission(pkg, permission);
        }
    }

    /**
     * Verify that whoever handles {@link MediaStore#ACTION_IMAGE_CAPTURE} can
     * correctly write the contents into a passed {@code content://} Uri.
     */
    public void testImageCaptureWithInadequeteLocationPermissions() throws Exception {
        Set<String> perms = new HashSet<>();
        perms.add(ACCESS_COARSE_LOCATION);
        perms.add(ACCESS_BACKGROUND_LOCATION);
        perms.add(ACCESS_MEDIA_LOCATION);
        testImageCaptureWithoutLocation(perms);
    }
     /**
     * Helper function to verify that whoever handles {@link MediaStore#ACTION_IMAGE_CAPTURE} can
     * correctly write the contents into a passed {@code content://} Uri, without location
     * information, necessarily, when ACCESS_FINE_LOCATION permissions aren't given.
     */
    private void testImageCaptureWithoutLocation(Set<String> locationPermissions)
            throws Exception {
        assertFalse("testImageCaptureWithoutLocation should not be passed ACCESS_FINE_LOCATION",
                locationPermissions.contains(ACCESS_FINE_LOCATION));
        final Context context = getInstrumentation().getContext();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Log.d(TAG, "Skipping due to lack of camera");
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        final File targetDir = new File(context.getFilesDir(), "debug");
        final File target = new File(targetDir, timeStamp  + "capture.jpg");

        targetDir.mkdirs();
        assertFalse(target.exists());

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(context, "android.providerui.cts.fileprovider", target));

        // Figure out who is going to answer the phone
        final ResolveInfo ri = context.getPackageManager().resolveActivity(intent, 0);
        final String pkg = ri.activityInfo.packageName;
        Log.d(TAG, "We're probably launching " + ri);

        final PackageInfo pi = context.getPackageManager().getPackageInfo(pkg,
                PackageManager.GET_PERMISSIONS);
        final Set<String> req = new HashSet<>();
        req.addAll(Arrays.asList(pi.requestedPermissions));

        grantRequisitePermissions(pkg, req, locationPermissions);

        Result result = getImageCaptureIntentResult(intent, pkg);

        assertTrue("exists", target.exists());
        assertTrue("has data", target.length() > 65536);

        // At the very least we expect photos generated by the device to have
        // sane baseline EXIF data
        final ExifInterface exif = new ExifInterface(new FileInputStream(target));
        assertAttribute(exif, ExifInterface.TAG_MAKE);
        assertAttribute(exif, ExifInterface.TAG_MODEL);
        assertAttribute(exif, ExifInterface.TAG_DATETIME);
        float[] latLong = new float[2];
        Boolean hasLocation = exif.getLatLong(latLong);
        assertTrue("Should not contain location information latitude: " + latLong[0] +
                " longitude: " + latLong[1], !hasLocation);
        revokeRequisitePermissions(pkg, req, locationPermissions);
    }

    private void grantRequisitePermissions(String pkg, Set<String> req,
            Set<String> locationPermissions) throws Exception {
        // Grant them all the permissions they might want.
        maybeGrantRuntimePermission(pkg, req, CAMERA);
        maybeGrantRuntimePermission(pkg, req, RECORD_AUDIO);
        maybeGrantRuntimePermission(pkg, req, READ_EXTERNAL_STORAGE);
        maybeGrantRuntimePermission(pkg, req, WRITE_EXTERNAL_STORAGE);
        SystemClock.sleep(DateUtils.SECOND_IN_MILLIS);
        for (String perm : locationPermissions) {
            maybeGrantRuntimePermission(pkg, req, perm);
        }
    }

    private void revokeRequisitePermissions(String pkg, Set<String> req, Set<String> perms)
            throws Exception {
        // So that the other tests don't start with this permission granted.
        for (String perm : perms) {
            maybeRevokeRuntimePermission(pkg, req, perm);
        }
    }

    private Result getImageCaptureIntentResult(Intent intent, String pkg)
            throws Exception {

        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();

        // To ensure camera app is launched
        SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);

        // Try a couple different strategies for taking a photo / capturing a video: first capture
        // and confirm using hardware keys.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_CAMERA);
        mDevice.waitForIdle();
        SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
        // We're done.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);
        mDevice.waitForIdle();

        // Maybe that gave us a result?
        Result result = mActivity.getResult(15, TimeUnit.SECONDS);
        Log.d(TAG, "First pass result was " + result);

        // Hrm, that didn't work; let's try an alternative approach of digging
        // around for a shutter button
        if (result == null) {
            maybeClick(new UiSelector().resourceId(pkg + ":id/shutter_button"));
            mDevice.waitForIdle();
            SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
            maybeClick(new UiSelector().resourceId(pkg + ":id/shutter_button"));
            mDevice.waitForIdle();
            maybeClick(new UiSelector().resourceId(pkg + ":id/done_button"));
            mDevice.waitForIdle();

            result = mActivity.getResult(15, TimeUnit.SECONDS);
            Log.d(TAG, "Second pass result was " + result);
        }

        // Grr, let's try hunting around even more
        if (result == null) {
            maybeClick(By.pkg(pkg).descContains("Capture"));
            mDevice.waitForIdle();
            SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
            maybeClick(By.pkg(pkg).descContains("Done"));
            mDevice.waitForIdle();

            result = mActivity.getResult(15, TimeUnit.SECONDS);
            Log.d(TAG, "Third pass result was " + result);
        }

        assertNotNull("Expected to get a IMAGE_CAPTURE result; your camera app should "
                + "respond to the CAMERA and DPAD_CENTER keycodes", result);
        return result;
    }

    private static void assertAttribute(ExifInterface exif, String tag) {
        final String res = exif.getAttribute(tag);
        if (res == null || res.length() == 0) {
            Log.d(TAG, "Expected valid EXIF tag for tag " + tag);
        }
    }

    private boolean supportsHardware() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        return !pm.hasSystemFeature("android.hardware.type.television")
                && !pm.hasSystemFeature("android.hardware.type.watch");
    }

    private void prepareFile() throws Exception {
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());

        final File documents =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        documents.mkdirs();
        assertTrue(documents.isDirectory());

        mFile = new File(documents, "test.jpg");
        try (OutputStream os = new FileOutputStream(mFile)) {
            os.write(CONTENT.getBytes());
        }

        final CountDownLatch latch = new CountDownLatch(1);
        MediaScannerConnection.scanFile(
                mActivity,
                new String[]{ mFile.getAbsolutePath() },
                new String[]{ "image/jpeg" },
                (String path, Uri uri) -> onScanCompleted(uri, latch)
        );
        assertTrue(
                "MediaScanner didn't finish scanning in 30s.", latch.await(30, TimeUnit.SECONDS));
    }

    private void onScanCompleted(Uri uri, CountDownLatch latch) {
        final String volumeName = MediaStore.getVolumeName(uri);
        mMediaStoreUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(volumeName),
                ContentUris.parseId(uri));
        latch.countDown();
    }

    private Uri acquireAccess(File file, String directoryName) {
        StorageManager storageManager =
                (StorageManager) mActivity.getSystemService(Context.STORAGE_SERVICE);

        // Request access from DocumentsUI
        final StorageVolume volume = storageManager.getStorageVolume(file);
        final Intent intent = volume.createOpenDocumentTreeIntent();
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        if (mTargetPackageName == null) {
            mTargetPackageName = getTargetPackageName(mActivity);
        }

        // Granting the access
        BySelector buttonPanelSelector = By.pkg(mTargetPackageName)
                .res(mTargetPackageName + ":id/container_save");
        mDevice.wait(Until.hasObject(buttonPanelSelector), 30 * DateUtils.SECOND_IN_MILLIS);
        final UiObject2 buttonPanel = mDevice.findObject(buttonPanelSelector);
        final UiObject2 allowButton = buttonPanel.findObject(By.res("android:id/button1"));
        allowButton.click();
        mDevice.waitForIdle();

        // Granting the access by click "allow" in confirm dialog
        final BySelector dialogButtonPanelSelector = By.pkg(mTargetPackageName)
                .res(mTargetPackageName + ":id/buttonPanel");
        mDevice.wait(Until.hasObject(dialogButtonPanelSelector), 30 * DateUtils.SECOND_IN_MILLIS);
        final UiObject2 positiveButton = mDevice.findObject(dialogButtonPanelSelector)
                .findObject(By.res("android:id/button1"));
        positiveButton.click();
        mDevice.waitForIdle();

        // Check granting result and take persistent permission
        final Result result = mActivity.getResult();
        assertEquals(Activity.RESULT_OK, result.resultCode);

        final Intent resultIntent = result.data;
        final Uri resultUri = resultIntent.getData();
        final int flags = resultIntent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        mActivity.getContentResolver().takePersistableUriPermission(resultUri, flags);
        return resultUri;
    }

    private static String getTargetPackageName(Context context) {
        final PackageManager pm = context.getPackageManager();

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        final ResolveInfo ri = pm.resolveActivity(intent, 0);
        return ri.activityInfo.packageName;
    }
}
