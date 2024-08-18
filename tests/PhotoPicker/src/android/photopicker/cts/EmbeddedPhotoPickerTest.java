/*
 * Copyright 2024 The Android Open Source Project
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

package android.photopicker.cts;

import static android.photopicker.cts.PhotoPickerBaseTest.isHardwareSupported;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.assertUiObjectExistsWithId;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAndClickUiObjectWithId;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getMediaItem;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getUiObjectMatchingDescription;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getUiObjectMatchingText;

import static com.android.providers.media.flags.Flags.enableEmbeddedPhotopicker;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.view.SurfaceView;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class EmbeddedPhotoPickerTest {
    private static final String RESOURCE_ID_REGEX_PREFIX = ".*:id/";
    private static final String LAUNCH_EMBEDDED_BUTTON_ID =
            RESOURCE_ID_REGEX_PREFIX + "open_embedded_picker_session_button";
    private static final String EMBEDDED_SURFACE_ID =
            RESOURCE_ID_REGEX_PREFIX + "embedded_picker_surface";
    private static final String PHOTOS_TAB_LABEL = "Photos";
    private static final String ACTION_EMBEDDED_PHOTOPICKER_SERVICE =
            "com.android.photopicker.core.embedded.EmbeddedService.BIND";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final UiDevice sDevice = UiDevice.getInstance(sInstrumentation);

    private final Context mContext = sInstrumentation.getContext();
    private final List<Uri> mUriList = new ArrayList<>();
    private EmbeddedTestActivity mActivity;

    private Intent mIntent = new Intent(ACTION_EMBEDDED_PHOTOPICKER_SERVICE);

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(isHardwareSupported());
        Assume.assumeTrue(enableEmbeddedPhotopicker());

        // Wake up the device and dismiss the keyguard before the test starts
        sDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sDevice.executeShellCommand("wm dismiss-keyguard");
    }

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }

        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();
    }

    @Test
    public void testOnSessionOpened_sessionNotNull() throws Exception {
        addMediaAndLaunchActivity(1);
        assertThat(mActivity.getSession()).isNull();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();
    }

    @Test
    public void testOnItemSelected_selectedUrisNotEmptyAndHasAccess() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(0);

        // 2. Get media item and perform click
        clickAndWait(sDevice, getMediaItem(sDevice));

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(1);
        Uri selectedUri = mActivity.getSelectedUris().get(0);
        assertThat(hasUriPermission(selectedUri)).isTrue();
    }

    @Test
    public void testOnItemDeselected_selectedUrisEmptyAndAccessRevoked() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(0);

        // 2. Get media item and perform click
        UiObject mediaItem = getMediaItem(sDevice);
        clickAndWait(sDevice, mediaItem);

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(1);
        Uri selectedUri = mActivity.getSelectedUris().get(0);

        // 3. Deselect the previously selected media item
        clickAndWait(sDevice, mediaItem);
        assertThat(mActivity.getSelectedUris().size()).isEqualTo(0);
        assertThat(hasUriPermission(selectedUri)).isFalse();
    }

    @Test
    public void testOnSessionError_sessionIsNull() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(0);

        // 2. Get media item and perform click
        clickAndWait(sDevice, getMediaItem(sDevice));

        assertThat(mActivity.getSelectedUris().size()).isEqualTo(1);

        // 3. Kill the PhotoPicker process
        sDevice.executeShellCommand("am force-stop " + getExplicitPackageName());

        assertThat(mActivity.getSession()).isNull();
        assertThat(mActivity.getSelectedUris().size()).isEqualTo(0);
    }

    @Test
    public void testClose_surfacePackageReleased() throws Exception {
        addMediaAndLaunchActivity(1);

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();
        assertThat(getMediaItem(sDevice).exists()).isTrue();

        // 2. Close the session
        mActivity.getSession().close();

        // 3. Assert the embedded ui (surface package) is released
        assertThat(getMediaItem(sDevice).exists()).isFalse();
    }

    @Test
    public void testIsPhotoPickerExpanded_expandedStateTrue_photosTabVisible() throws Exception {
        launchTestActivity();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        // 2. Assert that "Photos" tab is hidden when expanded state is set to false
        UiObject photosTab = getUiObjectMatchingText(PHOTOS_TAB_LABEL, sDevice);
        assertThat(photosTab.exists()).isFalse();

        // 3. Set the expanded state to true and assert that the "Photos" tab is visible
        mActivity.getSession().notifyPhotoPickerExpanded(true);
        photosTab = getUiObjectMatchingText(PHOTOS_TAB_LABEL, sDevice);
        assertThat(photosTab.exists()).isTrue();
        assertThat(photosTab.getText()).isEqualTo(PHOTOS_TAB_LABEL);
    }

    @Test
    public void testNotifyResized_surfacePackageResized() throws Exception {
        launchTestActivity();

        // 1. Launch the embedded session
        launchEmbeddedSession();
        assertThat(mActivity.getSession()).isNotNull();

        // 2. Set the expanded state to true so that navigation bar is visible and the view that
        // constitutes the surface package can be extracted
        mActivity.getSession().notifyPhotoPickerExpanded(true);
        UiObject2 surfacePackage = getUiObjectMatchingDescription(PHOTOS_TAB_LABEL, sDevice)
                .getParent().getParent().getParent();
        assertThat(surfacePackage).isNotNull();
        int oldWidth = surfacePackage.getVisibleBounds().width();
        int oldHeight = surfacePackage.getVisibleBounds().height();

        // 3. Notify resize
        final SurfaceView surfaceView = mActivity.getSurfaceView();
        mActivity.getSession().notifyResized(surfaceView.getWidth() / 2,
                surfaceView.getHeight() / 2);

        // 4. Get the new surface package and its dimensions
        UiObject2 newSurfacePackage = getUiObjectMatchingDescription(PHOTOS_TAB_LABEL, sDevice)
                .getParent().getParent().getParent();
        assertThat(newSurfacePackage).isNotNull();
        int newWidth = newSurfacePackage.getVisibleBounds().width();
        int newHeight = newSurfacePackage.getVisibleBounds().height();
        assertThat(newWidth).isNotEqualTo(oldWidth);
        assertThat(newWidth).isEqualTo(surfaceView.getWidth() / 2);
        assertThat(newHeight).isNotEqualTo(oldHeight);
        assertThat(newHeight).isEqualTo(surfaceView.getHeight() / 2);
    }

    private void addMediaAndLaunchActivity(int itemCount) throws Exception {
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));
        launchTestActivity();
    }

    private void launchEmbeddedSession() throws Exception {
        assertUiObjectExistsWithId(LAUNCH_EMBEDDED_BUTTON_ID, sDevice);
        assertUiObjectExistsWithId(EMBEDDED_SURFACE_ID, sDevice);
        findAndClickUiObjectWithId(LAUNCH_EMBEDDED_BUTTON_ID, sDevice);
    }


    private void launchTestActivity() {
        final Intent intent = new Intent(mContext, EmbeddedTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        mActivity = (EmbeddedTestActivity) sInstrumentation.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        sInstrumentation.waitForIdleSync();
        sDevice.waitForIdle();
    }

    /**
     * Get an explicit package name that limit the component {@link #mIntent} intent will
     * resolve to.
     */
    private String getExplicitPackageName() {
        // Use {@link PackageManager.MATCH_SYSTEM_ONLY} flag to match services only
        // by system apps.
        List<ResolveInfo> services = mActivity.getApplicationContext().getPackageManager()
                .queryIntentServices(mIntent, PackageManager.MATCH_SYSTEM_ONLY);
        // There should only be one matching service.
        if (services == null || services.isEmpty()) {
            Assert.fail("Failed to find embedded photopicker service!");
        } else if (services.size() != 1) {
            Assert.fail(String.format(
                    "Found more than 1 (%d) service by intent %s!",
                    services.size(), ACTION_EMBEDDED_PHOTOPICKER_SERVICE));
        }

        // Check that the service info contains package name.
        ServiceInfo embeddedService = services.get(0).serviceInfo;
        if (embeddedService != null && embeddedService.packageName != null) {
            return embeddedService.packageName;
        } else {
            Assert.fail("Failed to get valid service info or package info!");
        }
        return null;
    }

    private boolean hasUriPermission(Uri uri) {
        int res = mActivity.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return res == PackageManager.PERMISSION_GRANTED;
    }
}
