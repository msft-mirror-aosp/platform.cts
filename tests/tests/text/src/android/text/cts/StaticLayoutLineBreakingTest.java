/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.text.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StaticLayoutLineBreakingTest {
    private static final boolean DEBUG = false;

    private static final float SPACE_MULTI = 1.0f;
    private static final float SPACE_ADD = 0.0f;
    private static final int WIDTH = 100;
    private static final Alignment ALIGN = Alignment.ALIGN_NORMAL;

    private static final char SURR_FIRST = '\uD800';
    private static final char SURR_SECOND = '\uDF31';

    private static final int[] NO_BREAK = new int[] {};

    private static final TextPaint sTextPaint = new TextPaint();

    static {
        // The test font has following coverage and width.
        // U+0020: 10em
        // U+002E (.): 10em
        // U+0043 (C): 100em
        // U+0049 (I): 1em
        // U+004C (L): 50em
        // U+0056 (V): 5em
        // U+0058 (X): 10em
        // U+005F (_): 0em
        // U+FFFD (invalid surrogate will be replaced to this): 7em
        // U+10331 (\uD800\uDF31): 10em
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sTextPaint.setTypeface(Typeface.createFromAsset(context.getAssets(),
                  "fonts/StaticLayoutLineBreakingTestFont.ttf"));
        sTextPaint.setTextSize(1.0f);  // Make 1em == 1px.
    }

    private static StaticLayout getStaticLayout(CharSequence source, int width,
            int breakStrategy) {
        return StaticLayout.Builder.obtain(source, 0, source.length(), sTextPaint, width)
                .setAlignment(ALIGN)
                .setLineSpacing(SPACE_ADD, SPACE_MULTI)
                .setIncludePad(false)
                .setBreakStrategy(breakStrategy)
                .build();
    }

    private static int[] getBreaks(CharSequence source, int width, int breakStrategy) {
        final StaticLayout staticLayout = getStaticLayout(source, width, breakStrategy);

        final int[] breaks = new int[staticLayout.getLineCount() - 1];
        for (int line = 0; line < breaks.length; line++) {
            breaks[line] = staticLayout.getLineEnd(line);
        }
        return breaks;
    }

    private static void debugLayout(CharSequence source, StaticLayout staticLayout) {
        if (DEBUG) {
            int count = staticLayout.getLineCount();
            Log.i("SLLBTest", "\"" + source.toString() + "\": "
                    + count + " lines");
            for (int line = 0; line < count; line++) {
                int lineStart = staticLayout.getLineStart(line);
                int lineEnd = staticLayout.getLineEnd(line);
                Log.i("SLLBTest", "Line " + line + " [" + lineStart + ".."
                        + lineEnd + "]\t" + source.subSequence(lineStart, lineEnd));
            }
        }
    }

    private static void layout(CharSequence source, int[] breaks) {
        layout(source, breaks, WIDTH);
    }

    private static void layout(CharSequence source, int[] breaks, int width) {
        final int[] breakStrategies = {Layout.BREAK_STRATEGY_SIMPLE,
                Layout.BREAK_STRATEGY_HIGH_QUALITY};
        for (int breakStrategy : breakStrategies) {
            final StaticLayout staticLayout = getStaticLayout(source, width, breakStrategy);

            debugLayout(source, staticLayout);

            final int lineCount = breaks.length + 1;
            assertEquals("Number of lines", lineCount, staticLayout.getLineCount());

            for (int line = 0; line < lineCount; line++) {
                final int lineStart = staticLayout.getLineStart(line);
                final int lineEnd = staticLayout.getLineEnd(line);

                if (line == 0) {
                    assertEquals("Line start for first line", 0, lineStart);
                } else {
                    assertEquals("Line start for line " + line, breaks[line - 1], lineStart);
                }

                if (line == lineCount - 1) {
                    assertEquals("Line end for last line", source.length(), lineEnd);
                } else {
                    assertEquals("Line end for line " + line, breaks[line], lineEnd);
                }
            }
        }
    }

    private static void layoutMaxLines(CharSequence source, int[] breaks, int maxLines) {
        final StaticLayout staticLayout = StaticLayout.Builder
                .obtain(source, 0, source.length(), sTextPaint, WIDTH)
                .setAlignment(ALIGN)
                .setTextDirection(TextDirectionHeuristics.LTR)
                .setLineSpacing(SPACE_ADD, SPACE_MULTI)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .build();

        debugLayout(source, staticLayout);

        final int lineCount = staticLayout.getLineCount();

        for (int line = 0; line < lineCount; line++) {
            int lineStart = staticLayout.getLineStart(line);
            int lineEnd = staticLayout.getLineEnd(line);

            if (line == 0) {
                assertEquals("Line start for first line", 0, lineStart);
            } else {
                assertEquals("Line start for line " + line, breaks[line - 1], lineStart);
            }

            if (line == lineCount - 1 && line != breaks.length - 1) {
                assertEquals("Line end for last line", source.length(), lineEnd);
            } else {
                assertEquals("Line end for line " + line, breaks[line], lineEnd);
            }
        }
    }

    @Test
    public void testNoLineBreak() {
        // Width lower than WIDTH
        layout("", NO_BREAK);
        layout("I", NO_BREAK);
        layout("V", NO_BREAK);
        layout("X", NO_BREAK);
        layout("L", NO_BREAK);
        layout("I VILI", NO_BREAK);
        layout("XXXX", NO_BREAK);
        layout("LXXXX", NO_BREAK);

        // Width equal to WIDTH
        layout("C", NO_BREAK);
        layout("LL", NO_BREAK);
        layout("L XXXX", NO_BREAK);
        layout("XXXXXXXXXX", NO_BREAK);
        layout("XXX XXXXXX", NO_BREAK);
        layout("XXX XXXX X", NO_BREAK);
        layout("XXX XXXXX ", NO_BREAK);
        layout(" XXXXXXXX ", NO_BREAK);
        layout("  XX  XXX ", NO_BREAK);
        //      0123456789

        // Width greater than WIDTH, but no break
        layout("  XX  XXX  ", NO_BREAK);
        layout("XX XXX XXX ", NO_BREAK);
        layout("XX XXX XXX     ", NO_BREAK);
        layout("XXXXXXXXXX     ", NO_BREAK);
        //      01234567890
    }

    @Test
    public void testOneLineBreak() {
        //      01234567890
        layout("XX XXX XXXX", new int[] {7});
        layout("XX XXXX XXX", new int[] {8});
        layout("XX XXXXX XX", new int[] {9});
        layout("XX XXXXXX X", new int[] {10});
        //      01234567890
        layout("XXXXXXXXXXX", new int[] {10});
        layout("XXXXXXXXX X", new int[] {10});
        layout("XXXXXXXX XX", new int[] {9});
        layout("XXXXXXX XXX", new int[] {8});
        layout("XXXXXX XXXX", new int[] {7});
        //      01234567890
        layout("LL LL", new int[] {3});
        layout("LLLL", new int[] {2});
        layout("C C", new int[] {2});
        layout("CC", new int[] {1});
    }

    @Test
    public void testSpaceAtBreak() {
        //      0123456789012
        layout("XXXX XXXXX X", new int[] {11});
        layout("XXXXXXXXXX X", new int[] {11});
        layout("XXXXXXXXXV X", new int[] {11});
        layout("C X", new int[] {2});
    }

    @Test
    public void testMultipleSpacesAtBreak() {
        //      0123456789012
        layout("LXX XXXX", new int[] {4});
        layout("LXX  XXXX", new int[] {5});
        layout("LXX   XXXX", new int[] {6});
        layout("LXX    XXXX", new int[] {7});
        layout("LXX     XXXX", new int[] {8});
    }

    @Test
    public void testZeroWidthCharacters() {
        //      0123456789012345678901234
        layout("X_X_X_X_X_X_X_X_X_X", NO_BREAK);
        layout("___X_X_X_X_X_X_X_X_X_X___", NO_BREAK);
        layout("C_X", new int[] {2});
        layout("C__X", new int[] {3});
    }

    public static String replace(String string, char c, char r) {
        return string.replaceAll(String.valueOf(c), String.valueOf(r));
    }

    @Test
    public void testWithSurrogate() {
        layout("LX" + SURR_FIRST + SURR_SECOND, NO_BREAK);
        layout("LXXXX" + SURR_FIRST + SURR_SECOND, NO_BREAK);
        // LXXXXI (91) + SURR_FIRST + SURR_SECOND (10). Do not break in the middle point of
        // surrogatge pair.
        layout("LXXXXI" + SURR_FIRST + SURR_SECOND, new int[] {6});

        // LXXXXI (91) + SURR_SECOND (replaced with REPLACEMENT CHARACTER. width is 7px) fits.
        // Break just after invalid trailing surrogate.
        layout("LXXXXI" + SURR_SECOND + SURR_FIRST, new int[] {7});

        layout("C" + SURR_FIRST + SURR_SECOND, new int[] {1});
    }

    @Test
    public void testNarrowWidth() {
        int[] widths = new int[] { 0, 4, 10 };
        String[] texts = new String[] { "", "X", " ", "XX", " X", "XXX" };

        for (String text: texts) {
            // 15 is such that only one character will fit
            int[] breaks = getBreaks(text, 15, Layout.BREAK_STRATEGY_SIMPLE);

            // Width under 15 should all lead to the same line break
            for (int width: widths) {
                layout(text, breaks, width);
            }
        }
    }

    @Test
    public void testNarrowWidthZeroWidth() {
        int[] widths = new int[] { 1, 4 };
        for (int width: widths) {
            layout("X.", new int[] {1}, width);
            layout("X__", NO_BREAK, width);
            layout("X__X", new int[] {3}, width);
            layout("X__X_", new int[] {3}, width);

            layout("_", NO_BREAK, width);
            layout("__", NO_BREAK, width);

            // TODO: The line breaking algorithms break the line too frequently in the presence of
            // zero-width characters. The following cases document how line-breaking should behave
            // in some cases, where the current implementation does not seem reasonable. (Breaking
            // between a zero-width character that start the line and a character with positive
            // width does not make sense.) Line-breaking should be fixed so that all the following
            // tests end up on one line, with no breaks.
            // layout("_X", NO_BREAK, width);
            // layout("_X_", NO_BREAK, width);
            // layout("__X__", NO_BREAK, width);
        }
    }

    @Test
    public void testMaxLines() {
        layoutMaxLines("C", NO_BREAK, 1);
        layoutMaxLines("C C", new int[] {2}, 1);
        layoutMaxLines("C C", new int[] {2}, 2);
        layoutMaxLines("CC", new int[] {1}, 1);
        layoutMaxLines("CC", new int[] {1}, 2);
    }

    // Test for b/114454225
    @Test
    public void test_staticlayout_doesNotAccessInvalidIndex() {
        final String str = "IIIII\nIIIII\nIIIII\n";
        // the following should not throw an exception.
        StaticLayout.Builder.obtain(str, 0, str.length(), sTextPaint, 4 /* width */)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(4)
            .setMaxLines(3).build();

    }
}
