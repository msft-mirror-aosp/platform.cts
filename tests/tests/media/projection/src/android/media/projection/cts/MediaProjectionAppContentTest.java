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

package android.media.projection.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.media.projection.MediaProjectionAppContent;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.projection.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link MediaProjectionAppContent}.
 *
 * <p>Run with: atest CtsMediaProjectionTestCases:MediaProjectionAppContentTest
 */
@FrameworkSpecificTest
public class MediaProjectionAppContentTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    @Test
    public void parcel_unparcel() {
        int width = 10;
        int height = 20;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        String title = "title";
        int id = 1234;
        MediaProjectionAppContent appContent = new MediaProjectionAppContent(bitmap, title, id);
        assertThat(appContent.getId()).isEqualTo(id);
        assertThat(appContent.getTitle()).isEqualTo(title);
        assertThat(appContent.getThumbnail().getWidth()).isEqualTo(width);
        assertThat(appContent.getThumbnail().getHeight()).isEqualTo(height);
        Parcel parcel = Parcel.obtain();
        appContent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaProjectionAppContent unparcel =
                MediaProjectionAppContent.CREATOR.createFromParcel(parcel);
        assertThat(unparcel.getId()).isEqualTo(id);
        assertThat(unparcel.getTitle()).isEqualTo(title);
        assertThat(unparcel.getThumbnail().getWidth()).isEqualTo(width);
        assertThat(unparcel.getThumbnail().getHeight()).isEqualTo(height);
    }
}
