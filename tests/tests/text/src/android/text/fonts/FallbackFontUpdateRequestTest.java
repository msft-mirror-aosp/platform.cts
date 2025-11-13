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

package android.text.fonts;

import static android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC;
import static android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT;
import static android.graphics.fonts.FontStyle.FONT_WEIGHT_NORMAL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.graphics.fonts.FallbackFontUpdateRequest;
import android.graphics.fonts.FontFamilyUpdateRequest;
import android.graphics.fonts.FontStyle;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class FallbackFontUpdateRequestTest {

    @Test
    public void testBuilder() {
        FontFamilyUpdateRequest.Font font1 =
                new FontFamilyUpdateRequest.Font.Builder(
                                "TestFont1", new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT))
                        .build();
        FontFamilyUpdateRequest.Font font2 =
                new FontFamilyUpdateRequest.Font.Builder(
                                "TestFont2", new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_ITALIC))
                        .build();
        String languages = "en-US,ja-JP";
        int priority = 10;

        FallbackFontUpdateRequest request =
                new FallbackFontUpdateRequest.Builder()
                        .addFont(font1)
                        .addFont(font2)
                        .setLanguages(languages)
                        .setPriority(priority)
                        .build();

        assertThat(request.getFonts()).containsExactly(font1, font2).inOrder();
        assertThat(request.getLanguages()).isEqualTo(languages);
    }

    @Test
    public void testBuilder_singleFont() {
        FontFamilyUpdateRequest.Font font =
                new FontFamilyUpdateRequest.Font.Builder(
                                "TestFont", new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT))
                        .build();
        String languages = "zh-CN";
        int priority = 10;

        FallbackFontUpdateRequest request =
                new FallbackFontUpdateRequest.Builder()
                        .addFont(font)
                        .setLanguages(languages)
                        .setPriority(priority)
                        .build();

        assertThat(request.getFonts()).containsExactly(font);
        assertThat(request.getLanguages()).isEqualTo(languages);
    }

    @Test
    public void testBuilder_noLanguages() {
        FontFamilyUpdateRequest.Font font =
                new FontFamilyUpdateRequest.Font.Builder(
                                "TestFont", new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT))
                        .build();
        int priority = 10;

        FallbackFontUpdateRequest request =
                new FallbackFontUpdateRequest.Builder().addFont(font).setPriority(priority).build();

        assertThat(request.getFonts()).containsExactly(font);
        assertThat(request.getLanguages()).isEmpty();
    }

    @Test
    public void testBuilder_priority() {
        FontFamilyUpdateRequest.Font font =
                new FontFamilyUpdateRequest.Font.Builder(
                                "TestFont", new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT))
                        .build();
        String languages = "en-US";
        int priority = 100;

        FallbackFontUpdateRequest request =
                new FallbackFontUpdateRequest.Builder()
                        .addFont(font)
                        .setLanguages(languages)
                        .setPriority(priority)
                        .build();

        assertThat(request.getFonts()).containsExactly(font);
        assertThat(request.getLanguages()).isEqualTo(languages);
        assertThat(request.getPriority()).isEqualTo(priority);
    }

    @Test
    public void testBuilder_noFonts_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new FallbackFontUpdateRequest.Builder().setLanguages("en-US").build();
                });
    }

    @Test
    public void testBuilder_noPriority_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    FontFamilyUpdateRequest.Font font =
                            new FontFamilyUpdateRequest.Font.Builder(
                                            "TestFont",
                                            new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT))
                                    .build();
                    new FallbackFontUpdateRequest.Builder().addFont(font).build();
                });
    }
}
