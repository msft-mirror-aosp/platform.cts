/*
 * Copyright 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.MediaStore;

import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.PollValueFailedException;
import com.android.bedstead.permissions.PermissionContext;
import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
public class HighlightQueryResultsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final String READ_DEVICE_CONFIG_PERMISSION = "android.permission.READ_DEVICE_CONFIG";
    private Instrumentation mInstrumentation;
    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        UiDevice mDevice = UiDevice.getInstance(mInstrumentation);
        mContext = mInstrumentation.getTargetContext();
        mPackageManager = mContext.getPackageManager();
        Assume.assumeTrue(isHardwareSupported());
        Assume.assumeTrue(isModernPickerEnabled());

        // Wake up the device and dismiss the keyguard before the test starts
        mDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mDevice.executeShellCommand("wm dismiss-keyguard");

        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
        TestApis.activities().clearAllActivities();
    }

    boolean isHardwareSupported() {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        return !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_EMBEDDED)
                && !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_WATCH)
                && !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_LEANBACK);
    }

    boolean isModernPickerEnabled() {
        try (PermissionContext p =
                TestApis.permissions().withPermission(READ_DEVICE_CONFIG_PERMISSION)) {
            return DeviceConfig.getBoolean("mediaprovider", "enable_modern_picker", false);
        }
    }

    @Test
    public void testIntentResolvesToKotlinPickerWithHighlightResultsExtraForNonAlbumQuery()
            throws Exception {
        final String highlightQuery = "dog";
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle highlightResultsBundle = new Bundle();
        highlightResultsBundle.putString(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_MEDIA_TEXT_QUERY, highlightQuery);
        highlightResultsBundle.putInt(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_TYPE,
                MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_COLLAPSED);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA, highlightResultsBundle);

        // Fetch the activity and package to resolve ACTION_PICK_IMAGES
        Intent pickImagesIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(pickImagesIntent, 0);
        String modernPickerPackageName = resolveInfo.activityInfo.packageName;
        String modernPickerMainActivityName = resolveInfo.activityInfo.name;

        ComponentName resolvedActivityInfo = intent.resolveActivity(mPackageManager);
        // Assert that the intent resolves to the kotlin photopicker since ACTION_PICK_IMAGES
        // is an implicit intent and can't be asserted by an IntentMatcher for its
        // activity/package resolution.
        assertThat(resolvedActivityInfo.getPackageName()).isEqualTo(modernPickerPackageName);
        assertThat(resolvedActivityInfo.getClassName()).isEqualTo(modernPickerMainActivityName);

        // Also assert on the intent being launched for the system to intercept and
        // find corresponding matches
        TestApis.activities().startActivity(intent);
        Intents.intended(IntentMatchers.hasAction(MediaStore.ACTION_PICK_IMAGES));
        Intents.intended(
                IntentMatchers.hasExtraWithKey(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA));
        Bundle addedHighlightBundle =
                intent.getBundleExtra(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA);
        assertThat(
                        addedHighlightBundle.getString(
                                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_MEDIA_TEXT_QUERY))
                .isEqualTo(highlightQuery);
        assertThat(addedHighlightBundle.getInt(MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_TYPE))
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_COLLAPSED);
    }

    @Test
    public void testIntentResolvesToKotlinPickerWithHighlightResultsExtraForAlbumQuery()
            throws Exception {

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle highlightResultsBundle = new Bundle();
        highlightResultsBundle.putString(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_MEDIA_TEXT_QUERY,
                MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES);
        highlightResultsBundle.putInt(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_TYPE,
                MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_COLLAPSED);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA, highlightResultsBundle);

        // Fetch the activity and package to resolve ACTION_PICK_IMAGES
        Intent pickImagesIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(pickImagesIntent, 0);
        String modernPickerPackageName = resolveInfo.activityInfo.packageName;
        String modernPickerMainActivityName = resolveInfo.activityInfo.name;

        ComponentName resolvedActivityInfo = intent.resolveActivity(mPackageManager);
        // Assert that the intent resolves to the kotlin photopicker since ACTION_PICK_IMAGES
        // is an implicit intent and can't be asserted by an IntentMatcher for its
        // activity/package resolution.
        assertThat(resolvedActivityInfo.getPackageName()).isEqualTo(modernPickerPackageName);
        assertThat(resolvedActivityInfo.getClassName()).isEqualTo(modernPickerMainActivityName);

        // Also assert on the intent being launched for the system to intercept and
        // find corresponding matches
        TestApis.activities().startActivity(intent);
        Intents.intended(IntentMatchers.hasAction(MediaStore.ACTION_PICK_IMAGES));
        Intents.intended(
                IntentMatchers.hasExtraWithKey(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA));
        Bundle addedHighlightBundle =
                intent.getBundleExtra(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA);
        assertThat(
                        addedHighlightBundle.getString(
                                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_MEDIA_TEXT_QUERY))
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES);
        assertThat(addedHighlightBundle.getInt(MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_TYPE))
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_COLLAPSED);
    }

    @Test
    public void testIntentResolvesToKotlinPickerWithHighlightResultsExtraForNullQuery()
            throws Exception {

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle highlightResultsBundle = new Bundle();
        highlightResultsBundle.putString(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_MEDIA_TEXT_QUERY, null);
        highlightResultsBundle.putInt(
                MediaStore.KEY_PICK_IMAGES_HIGHLIGHT_TYPE,
                MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_COLLAPSED);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_HIGHLIGHT_MEDIA, highlightResultsBundle);

        // Fetch the activity and package to resolve ACTION_PICK_IMAGES
        Intent pickImagesIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(pickImagesIntent, 0);
        String modernPickerPackageName = resolveInfo.activityInfo.packageName;
        String modernPickerMainActivityName = resolveInfo.activityInfo.name;

        ComponentName resolvedActivityInfo = intent.resolveActivity(mPackageManager);
        // Assert that the intent resolves to the kotlin photopicker since ACTION_PICK_IMAGES
        // is an implicit intent and can't be asserted by an IntentMatcher for its
        // activity/package resolution.
        assertThat(resolvedActivityInfo.getPackageName()).isEqualTo(modernPickerPackageName);
        assertThat(resolvedActivityInfo.getClassName()).isEqualTo(modernPickerMainActivityName);

        // Picker will throw IllegalArgumentException when TestApis() tries to launch it.
        // TestApis() fails and throws its own error when it couldn't launch the activity
        // which is a PollValueFailedException at the moment for their implementation.
        Assert.assertThrows(
                PollValueFailedException.class, () -> TestApis.activities().startActivity(intent));
    }
}
