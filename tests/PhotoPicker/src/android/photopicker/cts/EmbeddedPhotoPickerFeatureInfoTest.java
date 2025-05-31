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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo;

import androidx.annotation.ColorLong;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.providers.media.flags.Flags;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PHOTOPICKER)
public class EmbeddedPhotoPickerFeatureInfoTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long DEFAULT_ACCENT_COLOR = -1;
    private static final int DEFAULT_MAX_SELECTION_LIMIT = 100;
    private static final @ColorLong long ACCENT_COLOR = 0xFF4287F5L; // blue color

    private Instrumentation mInstrumentation;
    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mPackageManager = mContext.getPackageManager();
    }

    boolean isHardwareSupported() {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        return !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_EMBEDDED)
                && !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_WATCH)
                && !mPackageManager.hasSystemFeature(mPackageManager.FEATURE_LEANBACK);
    }

    @Test
    public void testSetMimeTypes_default_returnsAllMediaMimeTypes() {
        final List<String> defaultMimeTypes = Arrays.asList("image/*", "video/*");
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected all valid mime types to be present")
                .that(info.getMimeTypes())
                .isEqualTo(defaultMimeTypes);
    }

    @Test
    public void testSetMimeTypes_validMimeTypes_returnsSetMimeTypes() {
        final List<String> mimeTypes = Arrays.asList("image/jpeg", "video/mp4", "image/png");
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().setMimeTypes(mimeTypes).build();

        assertWithMessage("Expected set mime types to be present")
                .that(info.getMimeTypes())
                .isEqualTo(mimeTypes);
    }

    @Test
    public void testSetMimeTypes_invalidMimeType_throwsException() {
        final List<String> mimeTypes = Arrays.asList("image/jpeg", "video/mp4", "application/pdf");
        final EmbeddedPhotoPickerFeatureInfo.Builder builder =
                new EmbeddedPhotoPickerFeatureInfo.Builder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.setMimeTypes(mimeTypes),
                "Expected exception when calling setMimeTypes with a invalid mime type");
    }

    @Test
    public void testSetMimeTypes_null_throwsException() {
        final EmbeddedPhotoPickerFeatureInfo.Builder builder =
                new EmbeddedPhotoPickerFeatureInfo.Builder();

        assertThrows(
                NullPointerException.class,
                () -> builder.setMimeTypes(null),
                "Expected exception when calling setMimeTypes with a null value");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
    public void testSetHighlightSearchMediaQueryForValidQuery() {
        // Suppress HSR tests for wearables since the APIs aren't supported in wear devices
        Assume.assumeTrue(isHardwareSupported());
        final String highlightQuery = "android";
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setHighlightSearchMediaTextQuery(highlightQuery)
                        .build();

        assertWithMessage("Expected highlight media query should be equal to input query")
                .that(info.getHighlightSearchMediaTextQuery())
                .isEqualTo(highlightQuery);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
    public void testSetHighlightAlbumId() {
        // Suppress HSR tests for wearables since the APIs aren't supported in wear devices
        Assume.assumeTrue(isHardwareSupported());
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setHighlightAlbumId(MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES)
                        .build();

        assertWithMessage("Expected highlight media query should be equal to input query")
                .that(info.getHighlightAlbumId())
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
    public void testSetHighlightSearchMediaTextQueryForNullQuery() {
        // Suppress HSR tests for wearables since the APIs aren't supported in wear devices
        Assume.assumeTrue(isHardwareSupported());
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EmbeddedPhotoPickerFeatureInfo.Builder()
                            .setHighlightSearchMediaTextQuery(null)
                            .build();
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
    public void testSetHighlightAlbumIdForNullQuery() {
        // Suppress HSR tests for wearables since the APIs aren't supported in wear devices
        Assume.assumeTrue(isHardwareSupported());
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EmbeddedPhotoPickerFeatureInfo.Builder().setHighlightAlbumId(null).build();
                });
    }

    @Test
    public void testSetAccentColor_default() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected accent color to be set to default")
                .that(info.getAccentColor())
                .isEqualTo(DEFAULT_ACCENT_COLOR);
    }

    @Test
    public void testSetAccentColor_colorSet_returnSetColor() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().setAccentColor(ACCENT_COLOR).build();

        assertWithMessage("Expected accent color to be set to color provided")
                .that(info.getAccentColor())
                .isEqualTo(ACCENT_COLOR);
    }

    @Test
    public void testIsOrderedSelection_default_noOrderedSelection() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected ordered selection to be false")
                .that(info.isOrderedSelection())
                .isFalse();
    }

    @Test
    public void testIsOrderedSelection_orderedSelectionSet_returnsSetBoolean() {
        EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().setOrderedSelection(true).build();

        assertWithMessage("Expected ordered selection to be true")
                .that(info.isOrderedSelection())
                .isTrue();

        info = new EmbeddedPhotoPickerFeatureInfo.Builder().setOrderedSelection(false).build();

        assertWithMessage("Expected ordered selection to be false")
                .that(info.isOrderedSelection())
                .isFalse();
    }

    @Test
    public void testSetMaxSelectionLimit_default_limitIsSetToDefaultMax() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected the max selection limit to be set to default")
                .that(info.getMaxSelectionLimit())
                .isEqualTo(DEFAULT_MAX_SELECTION_LIMIT);
    }

    @Test
    public void testSetMaxSelectionLimit_valueSet_limitIsSetToProvidedValue() {
        final int maxSelectionLimit = 5;
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setMaxSelectionLimit(maxSelectionLimit)
                        .build();

        assertWithMessage("Expected the max selection limit to be set to provided value")
                .that(info.getMaxSelectionLimit())
                .isEqualTo(maxSelectionLimit);
    }

    @Test
    public void testSetPreSelectedUris_default_emptyListIsSet() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected list of preselected uris to be empty by default")
                .that(info.getPreSelectedUris())
                .isEmpty();
    }

    @Test
    public void testSetPreSelectedUris_nonEmptyList_providedListIsSet() {
        final Uri uri1 = Uri.parse("content://com.example.app.provider/media/1");
        final Uri uri2 = Uri.parse("content://com.example.app.provider/media/2");
        final List<Uri> preSelectedUris = Arrays.asList(uri1, uri2);

        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setPreSelectedUris(preSelectedUris)
                        .build();

        assertWithMessage("Expected list of preselected uris to be set to provided list")
                .that(info.getPreSelectedUris())
                .containsExactlyElementsIn(preSelectedUris);
    }

    @Test
    public void testSetPreSelectedUris_nullList_throwsException() {
        final EmbeddedPhotoPickerFeatureInfo.Builder builder =
                new EmbeddedPhotoPickerFeatureInfo.Builder();

        assertThrows(
                NullPointerException.class,
                () -> builder.setPreSelectedUris(null),
                "Expected exception when calling setPreSelectedUris with a null value");
    }

    @Test
    public void testSetThemeNightMode_default_returnsNightUndefinedTheme() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected theme to be set to UI_MODE_NIGHT_UNDEFINED by default")
                .that(info.getThemeNightMode())
                .isEqualTo(Configuration.UI_MODE_NIGHT_UNDEFINED);
    }

    @Test
    public void testSetThemeNightMode_validValue_nightThemeIsSetToProvidedValue() {
        EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setThemeNightMode(Configuration.UI_MODE_NIGHT_YES)
                        .build();

        assertWithMessage("Expected theme to be set to UI_MODE_NIGHT_YES")
                .that(info.getThemeNightMode())
                .isEqualTo(Configuration.UI_MODE_NIGHT_YES);

        info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setThemeNightMode(Configuration.UI_MODE_NIGHT_NO)
                        .build();

        assertWithMessage("Expected theme to be set to UI_MODE_NIGHT_NO")
                .that(info.getThemeNightMode())
                .isEqualTo(Configuration.UI_MODE_NIGHT_NO);

        info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setThemeNightMode(Configuration.UI_MODE_NIGHT_UNDEFINED)
                        .build();

        assertWithMessage("Expected theme to be set to UI_MODE_NIGHT_UNDEFINED")
                .that(info.getThemeNightMode())
                .isEqualTo(Configuration.UI_MODE_NIGHT_UNDEFINED);
    }

    @Test
    public void testSetThemeNightMode_invalidValue_throwsException() {
        final int invalidNightMode = Configuration.UI_MODE_NIGHT_MASK;
        final EmbeddedPhotoPickerFeatureInfo.Builder builder =
                new EmbeddedPhotoPickerFeatureInfo.Builder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.setThemeNightMode(invalidNightMode),
                "Expected exception when setThemeNightMode is called with invalid value");
    }

    private static <T extends Throwable> void assertThrows(
            Class<T> clazz, Runnable r, String message) {
        try {
            r.run();
        } catch (Exception expected) {
            assertThat(expected.getClass()).isAssignableTo(clazz);
            return;
        }
        fail(message);
    }
}
