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

package android.ondeviceintelligence.cts.coveragetests;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2;

import static com.google.common.truth.Truth.assertThat;

import android.app.ondeviceintelligence.imagedescription.ImageDescriptionRequest;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse.ImageDescription;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ImageDescriptionRequestResponseTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testImageDescriptionRequest() {
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        ImageDescriptionRequest request =
                new ImageDescriptionRequest(bitmap, "prompt", java.util.Locale.US);
        assertThat(request.getContent().getParts().get(0).getImage().sameAs(bitmap)).isTrue();
        assertThat(request.getContent().getParts().get(1).getText()).isEqualTo("prompt");
        assertThat(request.getLocale()).isEqualTo(java.util.Locale.US);
        assertThat(request.getContent()).isNotNull();

        ImageDescriptionRequest request2 = new ImageDescriptionRequest(bitmap, "prompt");
        assertThat(request2.getLocale()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testImageDescriptionRequestClose() {
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        ImageDescriptionRequest request =
                new ImageDescriptionRequest(bitmap, "prompt", java.util.Locale.US);

        // Get the internal shared bitmap that should be cleaned up
        android.graphics.Bitmap internalBitmap = request.getContent().getParts().get(0).getImage();
        assertThat(internalBitmap).isNotNull();
        assertThat(internalBitmap).isNotEqualTo(bitmap); // It should be a new shared copy
        assertThat(internalBitmap.isRecycled()).isFalse();

        try {
            request.close();
        } catch (java.io.IOException e) {
            // ignore
        }
        // Verify internal bitmap is recycled
        assertThat(internalBitmap.isRecycled()).isTrue();

        // Original bitmap should still be valid
        assertThat(bitmap.isRecycled()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testImageDescriptionResponse() {
        ImageDescription desc = new ImageDescription("desc", 0.9f);
        assertThat(desc.getDescription().toString()).isEqualTo("desc");
        assertThat(desc.getScore()).isEqualTo(0.9f);

        ImageDescriptionResponse response = new ImageDescriptionResponse(List.of(desc));
        assertThat(response.getImageDescriptions()).containsExactly(desc);
    }
}
