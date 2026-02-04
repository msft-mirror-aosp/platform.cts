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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_SELECTION_PARAMS_API)
public class PhotoPickerSelectionParamsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long MAX_MEDIA_ITEM_SIZE_BYTES = 1000L;
    private static final Duration MAX_VIDEO_DURATION = Duration.ofSeconds(60);
    private static final Duration MIN_VIDEO_DURATION = Duration.ofSeconds(10);
    private static final long MAX_RESOLUTION_PIXELS = 2000L;
    private static final long MIN_RESOLUTION_PIXELS = 500L;
    private static final List<String> MIME_TYPES = List.of("image/jpeg", "video/mp4");
    private static final long MAX_SELECTION_BATCH_SIZE_BYTES = 5000L;

    private static final long DEFAULT_LONG_VALUE = -1L;

    @Test
    public void testPhotoPickerSelectionParams_writeToParcel() {
        PhotoPickerSelectionParams originalSelectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(MAX_MEDIA_ITEM_SIZE_BYTES)
                        .setMaxVideoDuration(MAX_VIDEO_DURATION)
                        .setMinVideoDuration(MIN_VIDEO_DURATION)
                        .setMaxMediaItemResolutionInPixels(MAX_RESOLUTION_PIXELS)
                        .setMinMediaItemResolutionInPixels(MIN_RESOLUTION_PIXELS)
                        .setMimeTypes(MIME_TYPES)
                        .setMaxSelectionBatchSizeInBytes(MAX_SELECTION_BATCH_SIZE_BYTES)
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
                .that(createdSelectionParams.getMaxVideoDuration())
                .isEqualTo(originalSelectionParams.getMaxVideoDuration());
        assertWithMessage("selection params Min video duration should be preserved")
                .that(createdSelectionParams.getMinVideoDuration())
                .isEqualTo(originalSelectionParams.getMinVideoDuration());
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
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params Default max video duration should be null")
                .that(selectionParams.getMaxVideoDuration())
                .isNull();
        assertWithMessage("selection params Default min video duration should be null")
                .that(selectionParams.getMinVideoDuration())
                .isNull();
        assertWithMessage("selection params Default max media item resolution should be -1")
                .that(selectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params Default min media item resolution should be -1")
                .that(selectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params Default mime types should be empty")
                .that(selectionParams.getMimeTypes())
                .isEmpty();
        assertWithMessage("selection params Default max selection batch size should be -1")
                .that(selectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(DEFAULT_LONG_VALUE);
    }

    @Test
    public void testBuilder_setters() {
        PhotoPickerSelectionParams selectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(MAX_MEDIA_ITEM_SIZE_BYTES)
                        .setMaxVideoDuration(MAX_VIDEO_DURATION)
                        .setMinVideoDuration(MIN_VIDEO_DURATION)
                        .setMaxMediaItemResolutionInPixels(MAX_RESOLUTION_PIXELS)
                        .setMinMediaItemResolutionInPixels(MIN_RESOLUTION_PIXELS)
                        .setMimeTypes(MIME_TYPES)
                        .setMaxSelectionBatchSizeInBytes(MAX_SELECTION_BATCH_SIZE_BYTES)
                        .build();

        assertWithMessage("selection params Max media item size should match set value")
                .that(selectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(MAX_MEDIA_ITEM_SIZE_BYTES);
        assertWithMessage("selection params Max video duration should match set value")
                .that(selectionParams.getMaxVideoDuration())
                .isEqualTo(MAX_VIDEO_DURATION);
        assertWithMessage("selection params Min video duration should match set value")
                .that(selectionParams.getMinVideoDuration())
                .isEqualTo(MIN_VIDEO_DURATION);
        assertWithMessage("selection params Max media item resolution should match set value")
                .that(selectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(MAX_RESOLUTION_PIXELS);
        assertWithMessage("selection params Min media item resolution should match set value")
                .that(selectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(MIN_RESOLUTION_PIXELS);
        assertWithMessage("selection params Mime types should match set value")
                .that(selectionParams.getMimeTypes())
                .isEqualTo(MIME_TYPES);
        assertWithMessage("selection params Max selection batch size should match set value")
                .that(selectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(MAX_SELECTION_BATCH_SIZE_BYTES);
    }

    @Test
    public void testBuilder_clearMethods_unsetsConstraints() {
        PhotoPickerSelectionParams clearedSelectionParams =
                new PhotoPickerSelectionParams.Builder()
                        .setMaxMediaItemSizeInBytes(MAX_MEDIA_ITEM_SIZE_BYTES)
                        .setMaxVideoDuration(MAX_VIDEO_DURATION)
                        .setMinVideoDuration(MIN_VIDEO_DURATION)
                        .setMaxMediaItemResolutionInPixels(MAX_RESOLUTION_PIXELS)
                        .setMinMediaItemResolutionInPixels(MIN_RESOLUTION_PIXELS)
                        .setMimeTypes(MIME_TYPES)
                        .setMaxSelectionBatchSizeInBytes(MAX_SELECTION_BATCH_SIZE_BYTES)
                        .clearMaxMediaItemSize()
                        .clearMaxVideoDuration()
                        .clearMinVideoDuration()
                        .clearMaxMediaItemResolution()
                        .clearMinMediaItemResolution()
                        .clearMimeTypes()
                        .clearMaxSelectionBatchSize()
                        .build();

        assertWithMessage("selection params cleared Max media item size, it should be -1")
                .that(clearedSelectionParams.getMaxMediaItemSizeInBytes())
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params cleared Max video duration, it should be null")
                .that(clearedSelectionParams.getMaxVideoDuration())
                .isNull();
        assertWithMessage("selection params cleared Min video duration, it should be null")
                .that(clearedSelectionParams.getMinVideoDuration())
                .isNull();
        assertWithMessage("selection params cleared Max media item resolution, it should be -1")
                .that(clearedSelectionParams.getMaxMediaItemResolutionInPixels())
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params cleared Min media item resolution, it should be -1")
                .that(clearedSelectionParams.getMinMediaItemResolutionInPixels())
                .isEqualTo(DEFAULT_LONG_VALUE);
        assertWithMessage("selection params cleared Mime types, it should be empty")
                .that(clearedSelectionParams.getMimeTypes())
                .isEmpty();
        assertWithMessage("selection params cleared Max selection batch size, it should be -1")
                .that(clearedSelectionParams.getMaxSelectionBatchSizeInBytes())
                .isEqualTo(DEFAULT_LONG_VALUE);
    }

    @Test
    public void testBuilder_invalidMinMaxDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMinVideoDuration(Duration.ofSeconds(60))
                                .setMaxVideoDuration(Duration.ofSeconds(30)) // Less than min
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
        Duration duration = Duration.ofSeconds(30);
        long resolution = 1080L;
        PhotoPickerSelectionParams params =
                new PhotoPickerSelectionParams.Builder()
                        .setMinVideoDuration(duration)
                        .setMaxVideoDuration(duration)
                        .setMinMediaItemResolutionInPixels(resolution)
                        .setMaxMediaItemResolutionInPixels(resolution)
                        .build();

        assertWithMessage("Min video duration should match")
                .that(params.getMinVideoDuration())
                .isEqualTo(duration);
        assertWithMessage("Max video duration should match")
                .that(params.getMaxVideoDuration())
                .isEqualTo(duration);
        assertWithMessage("Min media item resolution should match")
                .that(params.getMinMediaItemResolutionInPixels())
                .isEqualTo(resolution);
        assertWithMessage("Max media item resolution should match")
                .that(params.getMaxMediaItemResolutionInPixels())
                .isEqualTo(resolution);
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
    public void testSetMaxVideoDuration_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMaxVideoDuration(Duration.ofSeconds(-1)),
                "Expected exception when setting negative max video duration");
    }

    @Test
    public void testSetMaxVideoDuration_zero_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMaxVideoDuration(Duration.ofSeconds(0)),
                "Expected exception when setting negative max video duration");
    }

    @Test
    public void testSetMaxVideoDuration_null_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMaxVideoDuration(null),
                "Expected exception when setting null max video duration");
    }

    @Test
    public void testSetMinVideoDuration_null_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerSelectionParams.Builder().setMinVideoDuration(null),
                "Expected exception when setting null min video duration");
    }

    @Test
    public void testSetMinVideoDuration_negative_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhotoPickerSelectionParams.Builder()
                                .setMinVideoDuration(Duration.ofSeconds(-1)),
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
