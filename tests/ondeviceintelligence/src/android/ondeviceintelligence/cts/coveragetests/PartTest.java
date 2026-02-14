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

import static org.junit.Assert.fail;

import android.app.ondeviceintelligence.Part;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PartTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testPartMethods() {
        // Test TYPE_TEXT
        Part textPart = Part.createText("test text");
        assertThat(textPart.getType()).isEqualTo(1); // Part.TYPE_TEXT
        assertThat(textPart.getText()).isEqualTo("test text");
        assertThat(textPart.getBlob()).isNull();
        assertThat(textPart.getImage()).isNull();

        // Test TYPE_IMAGE
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        Part imagePart = Part.createImage(bitmap);
        assertThat(imagePart.getType()).isEqualTo(2); // Part.TYPE_IMAGE
        assertThat(imagePart.getImage()).isEqualTo(bitmap);
        assertThat(imagePart.getText()).isNull();
        assertThat(imagePart.getBlob()).isNull();

        // Test TYPE_AUDIO (using createBlob)
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            Part audioPart = Part.createBlob(pipe[0], 3); // Part.TYPE_AUDIO
            assertThat(audioPart.getType()).isEqualTo(3);
            assertThat(audioPart.getBlob()).isEqualTo(pipe[0]);
            assertThat(audioPart.getText()).isNull();
            assertThat(audioPart.getImage()).isNull();
        } catch (java.io.IOException e) {
            fail("Failed to create pipe: " + e);
        } finally {
            try {
                if (pipe != null) {
                    pipe[0].close();
                    pipe[1].close();
                }
            } catch (java.io.IOException e) {
                // ignore
            }
        }
    }
}
