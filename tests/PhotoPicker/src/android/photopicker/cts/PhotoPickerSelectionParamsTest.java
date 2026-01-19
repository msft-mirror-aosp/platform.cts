/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.widget.photopicker.PhotoPickerSelectionParams;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.providers.media.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
public class PhotoPickerSelectionParamsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testPhotoPickerSelectionParams_writeToParcel() {
        PhotoPickerSelectionParams originalSelectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(1000L)
                        .setMaxVideoDurationInSeconds(60L)
                        .setMinVideoDurationInSeconds(10L)
                        .setMaxMediaItemResolutionInPixels(2000L)
                        .setMinMediaItemResolutionInPixels(500L)
                        .setMimeTypes(List.of("image/jpeg", "video/mp4"))
                        .setMaxSelectionBatchSizeInBytes(5000L)
                        .build();

        Parcel parcel = Parcel.obtain();
        originalSelectionParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PhotoPickerSelectionParams createdSelectionParams =
                PhotoPickerSelectionParams.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertWithMessage("selection params Max media item size should be preserved")
                .that(createdSelectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(originalSelectionParams.getMaxMediaItemSizeInBytes());
        assertWithMessage("selection params Max video duration should be preserved")
                .that(createdSelectionParams.getMaxVideoDurationInSeconds())
                .isEqualTo(originalSelectionParams.getMaxVideoDurationInSeconds());
        assertWithMessage("selection params Min video duration should be preserved")
                .that(createdSelectionParams.getMinVideoDurationInSeconds())
                .isEqualTo(originalSelectionParams.getMinVideoDurationInSeconds());
        assertWithMessage("selection params Max media item resolution should be preserved")
                .that(createdSelectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(originalSelectionParams.getMaxMediaItemResolutionInPixels());
        assertWithMessage("selection params Min media item resolution should be preserved")
                .that(createdSelectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(originalSelectionParams.getMinMediaItemResolutionInPixels());
        assertWithMessage("selection params Mime types should be preserved")
                .that(createdSelectionParams.getMimeTypes())
                .isEqualTo(originalSelectionParams.getMimeTypes());
        assertWithMessage("selection params Max selection batch size should be preserved")
                .that(createdSelectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(originalSelectionParams.getMaxSelectionBatchSizeInBytes());
    }

    @Test
    public void testDescribeContents() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder().build();
        assertThat(selectionParams.describeContents()).isEqualTo(0);
    }

    @Test
    public void testBuilder_defaultValues() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder().build();

        assertWithMessage("selection params Default max media item size should be -1")
                .that(selectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(-1L);
        assertWithMessage("selection params Default max video duration should be -1")
                .that(selectionParams.getMaxVideoDurationInSeconds())
                .isEqualTo(-1L);
        assertWithMessage("selection params Default min video duration should be -1")
                .that(selectionParams.getMinVideoDurationInSeconds())
                .isEqualTo(-1L);
        assertWithMessage("selection params Default max media item resolution should be -1")
                .that(selectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(-1L);
        assertWithMessage("selection params Default min media item resolution should be -1")
                .that(selectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(-1L);
        assertWithMessage("selection params Default mime types should be empty")
                .that(selectionParams.getMimeTypes())
                .isEmpty();
        assertWithMessage("selection params Default max selection batch size should be -1")
                .that(selectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(-1L);
    }

    @Test
    public void testBuilder_setters() {
        long maxMediaSize = 1000L;
        long maxDuration = 60L;
        long minDuration = 10L;
        long maxResolution = 1080L * 1920L;
        long minResolution = 480L * 320L;
        List<String> mimeTypes = List.of("image/png", "video/mp4");
        long maxBatchSize = 5000L;

        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(maxMediaSize)
                        .setMaxVideoDurationInSeconds(maxDuration)
                        .setMinVideoDurationInSeconds(minDuration)
                        .setMaxMediaItemResolutionInPixels(maxResolution)
                        .setMinMediaItemResolutionInPixels(minResolution)
                        .setMimeTypes(mimeTypes)
                        .setMaxSelectionBatchSizeInBytes(maxBatchSize)
                        .build();

        assertWithMessage("selection params Max media item size should match set value")
                .that(selectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(maxMediaSize);
        assertWithMessage("selection params Max video duration should match set value")
                .that(selectionParams.getMaxVideoDurationInSeconds())
                .isEqualTo(maxDuration);
        assertWithMessage("selection params Min video duration should match set value")
                .that(selectionParams.getMinVideoDurationInSeconds())
                .isEqualTo(minDuration);
        assertWithMessage("selection params Max media item resolution should match set value")
                .that(selectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(maxResolution);
        assertWithMessage("selection params Min media item resolution should match set value")
                .that(selectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(minResolution);
        assertWithMessage("selection params Mime types should match set value")
                .that(selectionParams.getMimeTypes())
                .isEqualTo(mimeTypes);
        assertWithMessage("selection params Max selection batch size should match set value")
                .that(selectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(maxBatchSize);
    }

    @Test
    public void testBuilder_clearMethods_unsetsConstraints() {
        PhotoPickerSelectionParams clearedSelectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(2024L)
                        .setMaxVideoDurationInSeconds(100L)
                        .setMinVideoDurationInSeconds(10L)
                        .setMaxMediaItemResolutionInPixels(10000L)
                        .setMinMediaItemResolutionInPixels(1000L)
                        .setMimeTypes(List.of("image/png"))
                        .setMaxSelectionBatchSizeInBytes(100000L)
                        .clearMaxMediaItemSize()
                        .clearMaxVideoDuration()
                        .clearMinVideoDuration()
                        .clearMaxMediaItemResolution()
                        .clearMinMediaItemResolution()
                        .clearMimeTypes()
                        .clearMaxSelectionBatchSize()
                        .build();

        assertWithMessage("selection params Cleared Max media item size, it should be -1")
                .that(clearedSelectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(-1L);
        assertWithMessage("selection params Cleared Max video duration, it should be -1")
                .that(clearedSelectionParams.getMaxVideoDurationInSeconds())
                .isEqualTo(-1L);
        assertWithMessage("selection params Cleared Min video duration, it should be -1")
                .that(clearedSelectionParams.getMinVideoDurationInSeconds())
                .isEqualTo(-1L);
        assertWithMessage("selection params Cleared Max media item resolution, it should be -1")
                .that(clearedSelectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(-1L);
        assertWithMessage("selection params Cleared Min media item resolution, it should be -1")
                .that(clearedSelectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(-1L);
        assertWithMessage("selection params Cleared Mime types, it should be empty")
                .that(clearedSelectionParams.getMimeTypes())
                .isEmpty();
        assertWithMessage("selection params Cleared Max selection batch size, it should be -1")
                .that(clearedSelectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(-1L);
    }

    @Test
    public void testBuilder_invalidMinMaxDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMinVideoDurationInSeconds(60L)
                                .setMaxVideoDurationInSeconds(30L) // Less than min
                                .build(),
                "Expected exception when max video duration is less than min");
    }

    @Test
    public void testBuilder_invalidMinMaxResolution_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMaxMediaItemResolutionInPixels(100000L)
                                .setMinMediaItemResolutionInPixels(1000000L) // More than max
                                .build(),
                "Expected exception when max resolution is less than min");
    }

    @Test
    public void testBuilder_minMaxEqual_isAllowed() {
        PhotoPickerSelectionParams params =
                new PhotoPickerSelectionParams.Builder()
                        .setMinVideoDurationInSeconds(30L)
                        .setMaxVideoDurationInSeconds(30L)
                        .setMinMediaItemResolutionInPixels(1080L)
                        .setMaxMediaItemResolutionInPixels(1080L)
                        .build();

        assertWithMessage("Min video duration should be 30L")
                .that(params.getMinVideoDurationInSeconds())
                .isEqualTo(30L);
        assertWithMessage("Max video duration should be 30L")
                .that(params.getMaxVideoDurationInSeconds())
                .isEqualTo(30L);
        assertWithMessage("Min media item resolution should be 1080L")
                .that(params.getMinMediaItemResolutionInPixels())
                .isEqualTo(1080L);
        assertWithMessage("Max media item resolution should be 1080L")
                .that(params.getMaxMediaItemResolutionInPixels())
                .isEqualTo(1080L);
    }

    @Test
    public void testSetMimeTypes_invalidMimeType_throwsException() {
        // "application/pdf" is invalid and should throw exception
        List<String> mimeTypes = Arrays.asList("image/jpeg", "application/pdf");
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMimeTypes(mimeTypes).build(),
                "Expected IllegalArgumentException for invalid mime type");
    }

    @Test
    public void testSetMimeTypes_emptyList_throwsException() {
        List<String> mimeTypes = List.of();
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMimeTypes(mimeTypes).build(),
                "Expected IllegalArgumentException when setting mime types list to empty list.");
    }

    @Test
    public void testSetMimeTypes_nullElement_throwsException() {
        List<String> mimeTypes = Arrays.asList("image/jpeg", null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMimeTypes(mimeTypes),
                "Expected IllegalArgumentException when mime type list contains null");
    }

    @Test
    public void testSetMimeTypes_nullList_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMimeTypes(null),
                "Expected exception when setting null mime types");
    }

    @Test
    public void testSetMaxMediaItemSizeInBytes_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxMediaItemSizeInBytes(-1L),
                "Expected exception when setting negative max media item size");
    }

    @Test
    public void testSetMaxMediaItemSizeInBytes_zero_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxMediaItemSizeInBytes(0),
                "Expected exception when setting negative max media item size");
    }

    @Test
    public void testSetMaxVideoDurationInSeconds_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxVideoDurationInSeconds(-1L),
                "Expected exception when setting negative max video duration");
    }

    @Test
    public void testSetMaxVideoDurationInSeconds_zero_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxVideoDurationInSeconds(0),
                "Expected exception when setting negative max video duration");
    }

    @Test
    public void testSetMinVideoDurationInSeconds_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMinVideoDurationInSeconds(-1L),
                "Expected exception when setting negative min video duration");
    }

    @Test
    public void testSetMaxMediaItemResolutionInPixels_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMaxMediaItemResolutionInPixels(-1L),
                "Expected exception when setting negative max resolution");
    }

    @Test
    public void testSetMaxMediaItemResolutionInPixels_zero_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxMediaItemResolutionInPixels(0),
                "Expected exception when setting negative max resolution");
    }

    @Test
    public void testSetMinMediaItemResolutionInPixels_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMinMediaItemResolutionInPixels(-1L),
                "Expected exception when setting negative min resolution");
    }

    @Test
    public void testSetMaxSelectionBatchSizeInBytes_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxSelectionBatchSizeInBytes(-1L),
                "Expected exception when setting negative max batch size");
    }

    @Test
    public void testSetMaxSelectionBatchSizeInBytes_zero_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxSelectionBatchSizeInBytes(0),
                "Expected exception when setting negative max batch size");
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
