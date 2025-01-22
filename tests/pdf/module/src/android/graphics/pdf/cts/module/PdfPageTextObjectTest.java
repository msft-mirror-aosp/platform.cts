/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.graphics.pdf.cts.module;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.component.PdfPageTextObject;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_TEXT_OBJECTS)
public class PdfPageTextObjectTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEXT = "Hello World";
    private static final String NEW_TEXT = "Bye World";
    private static final float FONT_SIZE = 5.0F;
    private static final float NEW_FONT_SIZE = 10.0F;
    private static final Color STROKE_COLOR = Color.valueOf(Color.YELLOW);
    private static final float STROKE_WIDTH = 1.0F;
    private static final Color FILL_COLOR = Color.valueOf(Color.GREEN);

    private static final Typeface TYPE_FACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NEW_TYPE_FACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC);

    @Test
    public void textPageObject_test() {
        PdfPageTextObject pageTextObject = new PdfPageTextObject(TEXT, TYPE_FACE, FONT_SIZE);
        assertThat(pageTextObject.getText()).isEqualTo(TEXT);
        assertThat(pageTextObject.getFontSize()).isEqualTo(FONT_SIZE);
        assertThat(pageTextObject.getTypeface()).isEqualTo(TYPE_FACE);

        pageTextObject.setText(NEW_TEXT);
        assertThat(pageTextObject.getText()).isEqualTo(NEW_TEXT);

        pageTextObject.setFontSize(NEW_FONT_SIZE);
        assertThat(pageTextObject.getFontSize()).isEqualTo(NEW_FONT_SIZE);

        pageTextObject.setStrokeColor(STROKE_COLOR);
        assertThat(pageTextObject.getStrokeColor()).isEqualTo(STROKE_COLOR);

        pageTextObject.setStrokeWidth(STROKE_WIDTH);
        assertThat(pageTextObject.getStrokeWidth()).isEqualTo(STROKE_WIDTH);

        pageTextObject.setTypeface(NEW_TYPE_FACE);
        assertThat(pageTextObject.getTypeface()).isEqualTo(NEW_TYPE_FACE);

        pageTextObject.setFillColor(FILL_COLOR);
        assertThat(pageTextObject.getFillColor()).isEqualTo(FILL_COLOR);
    }
}
