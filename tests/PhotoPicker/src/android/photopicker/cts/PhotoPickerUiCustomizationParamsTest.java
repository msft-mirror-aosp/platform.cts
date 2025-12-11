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

package android.photopicker.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.widget.photopicker.PhotoPickerUiCustomizationParams;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.providers.media.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PHOTOPICKER_UI_CUSTOMIZATION_PARAMS_API)
public class PhotoPickerUiCustomizationParamsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testBuilder_defaultValues() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder().build();

        assertWithMessage("Default aspect ratio should be SQUARE_1_1")
                .that(params.getAspectRatio())
                .isEqualTo(PhotoPickerUiCustomizationParams.ASPECT_RATIO_SQUARE_1_1);
    }

    @Test
    public void testBuilder_setters() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder()
                        .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
                        .build();

        assertWithMessage("Aspect ratio should be PORTRAIT_9_16 after setter")
                .that(params.getAspectRatio())
                .isEqualTo(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16);
    }

    @Test
    public void testBuilder_clearMethods() {
        PhotoPickerUiCustomizationParams params =
                new PhotoPickerUiCustomizationParams.Builder()
                        .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
                        .clearAspectRatio()
                        .build();

        assertWithMessage("Aspect ratio should be reset to SQUARE_1_1 after clear")
                .that(params.getAspectRatio())
                .isEqualTo(PhotoPickerUiCustomizationParams.ASPECT_RATIO_SQUARE_1_1);
    }

    @Test
    public void testBuilder_invalidAspectRatioConstant_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhotoPickerUiCustomizationParams.Builder().setAspectRatio(100).build(),
                "Expected exception when unrecognized aspect ratio constant is used.");
    }

    @Test
    public void testParcelable() {
        PhotoPickerUiCustomizationParams original =
                new PhotoPickerUiCustomizationParams.Builder()
                        .setAspectRatio(PhotoPickerUiCustomizationParams.ASPECT_RATIO_PORTRAIT_9_16)
                        .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PhotoPickerUiCustomizationParams created =
                PhotoPickerUiCustomizationParams.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertWithMessage("Parceled object should have same aspect ratio as original")
                .that(created.getAspectRatio())
                .isEqualTo(original.getAspectRatio());
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
