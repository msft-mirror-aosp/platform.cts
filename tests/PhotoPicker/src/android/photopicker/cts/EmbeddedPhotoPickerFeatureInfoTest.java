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
import android.cts.photopicker.lib.PhotoPickerTestRule;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo;
import android.widget.photopicker.PhotoPickerSelectionParams;
import android.widget.photopicker.PhotoPickerUiCustomizationParams;

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
        Assume.assumeTrue(PhotoPickerTestRule.Companion.isHardwareSupported());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PICKER_EXPANDED_HIGHLIGHT_TYPE_API)
    public void testEmbeddedFeatureInfoParameterisedConstructor() {
        String albumId = "albumId";
        List<String> mimeTypes = Arrays.asList("image/*", "video/*");
        long accentColor = 0xFF4285F4; // Google Blue
        boolean orderedSelection = true;
        int maxSelectionLimit = 10;
        List<Uri> preSelectedUris =
                Arrays.asList(
                        Uri.parse("content://media/external/images/media/1"),
                        Uri.parse("content://media/external/video/media/2"));
        int themeNightMode = Configuration.UI_MODE_NIGHT_YES;
        String highlightSearchMediaQuery = "Holidays";
        int highlightType = MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_EXPANDED;
        boolean launchedPickerInExpandedState = true;

        // Create an object and set some properties
        EmbeddedPhotoPickerFeatureInfo featureInfo =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setHighlightAlbumId(albumId)
                        .setMimeTypes(mimeTypes)
                        .setAccentColor(accentColor)
                        .setOrderedSelection(orderedSelection)
                        .setMaxSelectionLimit(maxSelectionLimit)
                        .setPreSelectedUris(preSelectedUris)
                        .setThemeNightMode(themeNightMode)
                        .setHighlightSearchMediaTextQuery(highlightSearchMediaQuery)
                        .setHighlightType(highlightType)
                        .setPickerLaunchedInExpandedState(launchedPickerInExpandedState)
                        .build();

        // Create a new object with the parametrised constructor
        EmbeddedPhotoPickerFeatureInfo parameterisedFeatureInfoObject =
                new EmbeddedPhotoPickerFeatureInfo.Builder(featureInfo).build();

        // Assert the properties of the new object
        assertWithMessage("Expected highlight album id should be equal to previously set album id")
                .that(parameterisedFeatureInfoObject.getHighlightAlbumId())
                .isEqualTo(albumId);

        assertWithMessage("Expected mime types should be equal to previously set mime types")
                .that(parameterisedFeatureInfoObject.getMimeTypes())
                .isEqualTo(mimeTypes);
        assertWithMessage("Mime types list instance should be different (deep copy)")
                .that(parameterisedFeatureInfoObject.getMimeTypes())
                .isNotSameInstanceAs(featureInfo.getMimeTypes());

        assertWithMessage("Expected accent color should be equal")
                .that(parameterisedFeatureInfoObject.getAccentColor())
                .isEqualTo(accentColor);

        assertWithMessage("Expected ordered selection should be equal")
                .that(parameterisedFeatureInfoObject.isOrderedSelection())
                .isEqualTo(orderedSelection);

        assertWithMessage("Expected max selection limit should be equal")
                .that(parameterisedFeatureInfoObject.getMaxSelectionLimit())
                .isEqualTo(maxSelectionLimit);

        assertWithMessage("Expected pre-selected URIs should be equal")
                .that(parameterisedFeatureInfoObject.getPreSelectedUris())
                .isEqualTo(preSelectedUris);
        assertWithMessage("Pre-selected URIs list instance should be different (deep copy)")
                .that(parameterisedFeatureInfoObject.getPreSelectedUris())
                .isNotSameInstanceAs(featureInfo.getPreSelectedUris());

        assertWithMessage("Expected theme night mode should be equal")
                .that(parameterisedFeatureInfoObject.getThemeNightMode())
                .isEqualTo(themeNightMode);

        assertWithMessage("Expected highlight search media query should be equal")
                .that(parameterisedFeatureInfoObject.getHighlightSearchMediaTextQuery())
                .isEqualTo(highlightSearchMediaQuery);

        assertWithMessage("Expected highlight type should be equal")
                .that(parameterisedFeatureInfoObject.getHighlightType())
                .isEqualTo(highlightType);

        assertWithMessage("Expected launched picker in expanded state should be equal")
                .that(parameterisedFeatureInfoObject.isPickerLaunchedInExpandedState())
                .isEqualTo(launchedPickerInExpandedState);
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
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setHighlightAlbumId(MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES)
                        .build();

        assertWithMessage("Expected highlight media query should be equal to input query")
                .that(info.getHighlightAlbumId())
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_ALBUM_FAVORITES);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PICKER_EXPANDED_HIGHLIGHT_TYPE_API)
    public void testSetHighlightType() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setHighlightType(MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_EXPANDED)
                        .build();

        assertWithMessage("Expected highlight media query should be equal to input query")
                .that(info.getHighlightType())
                .isEqualTo(MediaStore.PICK_IMAGES_HIGHLIGHT_TYPE_EXPANDED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PICKER_EXPANDED_HIGHLIGHT_TYPE_API)
    public void testIsLaunchedPickerInExpandedState() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setPickerLaunchedInExpandedState(true)
                        .build();

        assertWithMessage("Expected highlight media query should be equal to input query")
                .that(info.isPickerLaunchedInExpandedState())
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_HIGHLIGHT_SEARCH_RESULTS_APIS)
    public void testSetHighlightSearchMediaTextQueryForNullQuery() {
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
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EmbeddedPhotoPickerFeatureInfo.Builder().setHighlightAlbumId(null).build();
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EMBEDDED_PICKER_EXPANDED_HIGHLIGHT_TYPE_API)
    public void testSetHighlightTypeForInvalidType() {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EmbeddedPhotoPickerFeatureInfo.Builder().setHighlightType(-1).build();
                });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PICKER_LOCATION_METADATA_API)
    public void testSetRequestLocationMetadata() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setRequestLocationMetadata(true)
                        .build();

        assertWithMessage("Expected location metadata request to be set to true")
                .that(info.isLocationMetadataRequested())
                .isTrue();
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

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
    public void testSetSelectionParams_default_returnsNull() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected default selection params to be null")
                .that(info.getSelectionParams())
                .isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
    public void testSetSelectionParams_withValidParams_returnsSetParams() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder().setMaxMediaItemSizeInBytes(1024L).build();
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setSelectionParams(selectionParams)
                        .build();

        assertWithMessage("Expected selection params to be set")
                .that(info.getSelectionParams())
                .isEqualTo(selectionParams);

        Assert.assertNotNull(info.getSelectionParams());
        assertWithMessage("Expected selection params max media item size to be preserved")
                .that(info.getSelectionParams().getMaxMediaItemSizeInBytes())
                .isEqualTo(1024L);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
    public void testSetSelectionParams_withNull_returnsNull() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder().build();
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setSelectionParams(selectionParams) // Set initially
                        .setSelectionParams(null) // Then clear
                        .build();

        assertWithMessage("Expected selection params to be null after clearing")
                .that(info.getSelectionParams())
                .isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
    public void testFeatureInfoParceling_withSelectionParams() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder().setMinVideoDurationInSeconds(10L).build();
        EmbeddedPhotoPickerFeatureInfo original =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setSelectionParams(selectionParams)
                        .build();
        Assert.assertNotNull(original.getSelectionParams());

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EmbeddedPhotoPickerFeatureInfo created =
                EmbeddedPhotoPickerFeatureInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        Assert.assertNotNull(created.getSelectionParams());
        assertWithMessage("Expected selection params to be preserved after parceling")
                .that(created.getSelectionParams().getMinVideoDurationInSeconds())
                .isEqualTo(original.getSelectionParams().getMinVideoDurationInSeconds());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_UI_CUSTOMIZATION_PARAMS_API)
    public void testSetUiCustomizationParams_default_returnsNull() {
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder().build();

        assertWithMessage("Expected default ui customization params to be null")
                .that(info.getUiCustomizationParams())
                .isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_UI_CUSTOMIZATION_PARAMS_API)
    public void testSetUiCustomizationParams_withValidParams_returnsSetParams() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder()
                        .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
                        .build();
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setUiCustomizationParams(params)
                        .build();
        Assert.assertNotNull(info.getUiCustomizationParams());

        assertWithMessage("Expected ui customization params to be set")
                .that(info.getUiCustomizationParams())
                .isEqualTo(params);
        assertWithMessage("Expected aspect ratio to be preserved")
                .that(info.getUiCustomizationParams().getAspectRatio())
                .isEqualTo(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_UI_CUSTOMIZATION_PARAMS_API)
    public void testSetUiCustomizationParams_unsetWithNull_returnsNull() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder().build();
        final EmbeddedPhotoPickerFeatureInfo info =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setUiCustomizationParams(params) // Set initially
                        .setUiCustomizationParams(null) // Then clear
                        .build();

        assertWithMessage("Expected ui customization params to be null after clearing")
                .that(info.getUiCustomizationParams())
                .isNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_UI_CUSTOMIZATION_PARAMS_API)
    public void testFeatureInfoParceling_withUiCustomizationParams() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder()
                        .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
                        .build();
        EmbeddedPhotoPickerFeatureInfo original =
                new EmbeddedPhotoPickerFeatureInfo.Builder()
                        .setUiCustomizationParams(params)
                        .build();
        Assert.assertNotNull(original.getUiCustomizationParams());

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EmbeddedPhotoPickerFeatureInfo created =
                EmbeddedPhotoPickerFeatureInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        Assert.assertNotNull(created.getUiCustomizationParams());

        assertWithMessage("Expected ui customization params to be preserved after parceling")
                .that(created.getUiCustomizationParams().getAspectRatio())
                .isEqualTo(original.getUiCustomizationParams().getAspectRatio());
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
