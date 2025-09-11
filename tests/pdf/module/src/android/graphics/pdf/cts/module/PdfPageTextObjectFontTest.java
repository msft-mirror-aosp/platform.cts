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

package android.graphics.pdf.cts.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.pdf.component.PdfPageTextObjectFont;
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
public class PdfPageTextObjectFontTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int INVALID_FONT_FAMILY = -1;

    @Test
    public void testConstructor_validArguments_initializesCorrectly() {
        PdfPageTextObjectFont font =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_COURIER, true, false);

        assertEquals(PdfPageTextObjectFont.FONT_FAMILY_COURIER, font.getFontFamily());
        assertTrue(font.isBold());
        assertFalse(font.isItalic());
    }

    @Test
    public void testConstructor_invalidFontFamily_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfPageTextObjectFont(INVALID_FONT_FAMILY, true, false));
    }

    @Test
    public void testCopyConstructor_validArgument_copiesCorrectly() {
        PdfPageTextObjectFont originalFont =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_HELVETICA, false, true);
        PdfPageTextObjectFont copiedFont = new PdfPageTextObjectFont(originalFont);

        assertEquals(originalFont.getFontFamily(), copiedFont.getFontFamily());
        assertEquals(originalFont.isBold(), copiedFont.isBold());
        assertEquals(originalFont.isItalic(), copiedFont.isItalic());
    }

    @Test
    public void testCopyConstructor_nullArgument_throwsException() {
        assertThrows(NullPointerException.class, () -> new PdfPageTextObjectFont(null));
    }

    @Test
    public void testGetAndSetFontFamily_validFontFamily_setsCorrectly() {
        PdfPageTextObjectFont font =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_COURIER, false, false);
        font.setFontFamily(PdfPageTextObjectFont.FONT_FAMILY_TIMES_NEW_ROMAN);
        assertEquals(PdfPageTextObjectFont.FONT_FAMILY_TIMES_NEW_ROMAN, font.getFontFamily());
    }

    @Test
    public void testSetFontFamily_invalidFontFamily_throwsException() {
        PdfPageTextObjectFont font =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_SYMBOL, false, false);
        assertThrows(IllegalArgumentException.class, () -> font.setFontFamily(INVALID_FONT_FAMILY));
    }

    @Test
    public void testIsAndSetBold_setsCorrectly() {
        PdfPageTextObjectFont font =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_COURIER, false, false);
        assertFalse(font.isBold());
        font.setBold(true);
        assertTrue(font.isBold());
        font.setBold(false);
        assertFalse(font.isBold());
    }

    @Test
    public void testIsAndSetItalic_setsCorrectly() {
        PdfPageTextObjectFont font =
                new PdfPageTextObjectFont(PdfPageTextObjectFont.FONT_FAMILY_COURIER, false, false);
        assertFalse(font.isItalic());
        font.setItalic(true);
        assertTrue(font.isItalic());
        font.setItalic(false);
        assertFalse(font.isItalic());
    }
}
