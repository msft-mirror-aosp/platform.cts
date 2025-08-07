/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package android.text.cts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.DynamicLayout
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
class LayoutDrawShiftTest(val p: Param) {
    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    // In this test case, the SIMPLE and HIGH_QUALITY line breaker produces the same line break
    // output.
    data class Param(val isOptimal: Boolean, val useDynamicLayout: Boolean) {
        override fun toString(): String = if (isOptimal) {
            if (useDynamicLayout) {
                "Dynamic/Optimal"
            } else {
                "Static/Optimal"
            }
        } else {
            if (useDynamicLayout) {
                "Dynamic/Greedy"
            } else {
                "Static/Greedy"
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getParams(): List<Param> = listOf(
                Param(true, true),
                Param(false, true),
                Param(true, false),
                Param(false, false)
        )
    }

    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    // The OvershootTest.ttf has the following coverage, extent, width and bbox.
    // U+0061(a), U+05D0(denoted as A in test comment): 1em, (   0, 0) - (1,   1)
    // U+0062(b), U+05D1(denoted as B in test comment): 1em, (   0, 0) - (1.5, 1)
    // U+0063(c), U+05D2(denoted as C in test comment): 1em, (   0, 0) - (2,   1)
    // U+0064(d), U+05D3(denoted as D in test comment): 1em, (   0, 0) - (2.5, 1)
    // U+0065(e), U+05D4(denoted as E in test comment): 1em, (-0.5, 0) - (1,   1)
    // U+0066(f), U+05D5(denoted as F in test comment): 1em, (-1.0, 0) - (1,   1)
    // U+0067(g), U+05D6(denoted as G in test comment): 1em, (-1.5, 0) - (1,   1)
    // U+0068(h), U+05D7(denoted as H in test comment): 1em, ( 0.5, 0) - (1,   1)
    // U+0068(i), U+05D8(denoted as I in test comment): 1em, (   0, 0) - (0.5, 1)
    private val overshootFont = Typeface.createFromAsset(context.assets, "fonts/OvershootTest.ttf")
    private val overshootPaint = TextPaint().apply {
        typeface = overshootFont
        textSize = 10f // make 1em = 10px
    }

    private fun buildLayout(text: String, widthPx: Int, shiftDrawOffset: Boolean = false) =
            if (p.useDynamicLayout) {
                DynamicLayout.Builder.obtain(text, overshootPaint, widthPx)
                        .setUseBoundsForWidth(true)
                        .setBreakStrategy(if (p.isOptimal) {
                            LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
                        } else {
                            LineBreaker.BREAK_STRATEGY_SIMPLE
                        })
                        .setShiftDrawingOffsetForStartOverhang(shiftDrawOffset)
                        .build()
                        .also {
                            assertThat(it.useBoundsForWidth).isTrue()
                            assertThat(it.shiftDrawingOffsetForStartOverhang)
                                    .isEqualTo(shiftDrawOffset)
                        }
            } else {
                StaticLayout.Builder.obtain(text, 0, text.length, overshootPaint, widthPx)
                        .setUseBoundsForWidth(true)
                        .setBreakStrategy(if (p.isOptimal) {
                            LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
                        } else {
                            LineBreaker.BREAK_STRATEGY_SIMPLE
                        })
                        .setShiftDrawingOffsetForStartOverhang(shiftDrawOffset)
                        .build()
                        .also {
                            assertThat(it.useBoundsForWidth).isTrue()
                            assertThat(it.shiftDrawingOffsetForStartOverhang)
                                    .isEqualTo(shiftDrawOffset)
                        }
            }

    private fun getDrawingXOffset(layout: Layout, lineNo: Int): Int {
        val bmp = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val drawOffsets = mutableListOf<Int>()
        layout.drawText(canvas, 0, layout.lineCount - 1, { x, y ->
            drawOffsets.add(x)
        })
        return drawOffsets[lineNo]
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_trailing_LTR() {
        val text = "aaaa bbbb cccc dddd"

        // Width constraint: 1000px
        // |aaaa bbbb cccc dddd     : width: 205, max: 205
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(0)

        // Width constraint: 150px
        // |aaaa bbbb cccc     |: width: 150, max 150
        // |dddd               |: width: 55, max 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(0)

        // Width constraint: 105px
        // |aaaa bbbb    |: width: 100, max: 95
        // |cccc dddd    |: width: 105, max 105
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(0)

        // Width constraint: 95px
        // |aaaa bbbb|: width: 100, max: 95
        // |cccc     |: width: 50, max: 50
        // |dddd     |: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(0)

        // Width constraint: 55px
        // |aaaa|: width: 50, max: 40
        // |bbbb|: width: 50, max: 45
        // |cccc|: width: 50, max: 50
        // |dddd|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(0)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_trailing_RTL() {
        val text = "\u05D0\u05D0\u05D0\u05D0 \u05D1\u05D1\u05D1\u05D1 " +
                "\u05D2\u05D2\u05D2\u05D2 \u05D3\u05D3\u05D3\u05D3"

        // Width constraint: 1000px
        // DDDD CCCC BBBB AAAA|: width: 190, max: 190
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(1000)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(1000)

        // Width constraint: 150px
        // |CCCC BBBB AAAA|: width: 150, max: 140
        // |          DDDD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(150)
        // DDDD has 15px right overshoots, so shifting drawing offset 15px left.
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(135)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(135)

        // Width constraint: 105px
        // |BBBB AAAA|: width: 100, max: 90
        // |DDDD CCCC|: width: 100, max 100
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(105)
        // CCCC has 10px right overshoots, so shifting drawing offset 10px left.
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(95)

        // Width constraint: 95px
        // |BBBB AAAA|: width: 100, max: 90
        // |     CCCC|: width: 60, max: 50
        // |     DDDD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(95)
        // DDDD has 15px right overshoots, CCCC has 10px right overshoot, so shifting drawing offset
        // 15px left which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(80)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(80)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(80)

        // Width constraint: 55px
        // |AAAA|: width: 50, max: 40
        // |BBBB|: width: 55, max: 45
        // |CCCC|: width: 60, max: 50
        // |DDDD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(55)
        // DDDD has 15px right overshoots, CCCC has 10px right overshoot and BBBB has 5px right
        // overshoot, so shifting drawing offset 15px left which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(40)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_trailing_Bidi_LTRFirst() {
        val text = "a\u05D0\u05D0a b\u05D1\u05D1b c\u05D2\u05D2c d\u05D3\u05D3d"

        // Width constraint: 1000px
        // |aAAa bBBb cCCc dDDd     : width: 205, max: 205
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(0)

        // Width constraint: 150px
        // |aAAa bBBb cCCc     |: width: 150, max 150
        // |dDDd               |: width: 55, max 150
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(0)

        // Width constraint: 105px
        // |aAAa bBBb    |: width: 100, max: 95
        // |cCCc dDDd    |: width: 105, max 105
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(0)

        // Width constraint: 95px
        // |aAAa bBBb|: width: 100, max: 95
        // |cCCc     |: width: 50, max: 50
        // |dDDd     |: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(0)

        // Width constraint: 55px
        // |aAAa|: width: 50, max: 40
        // |bBBb|: width: 50, max: 45
        // |cCCc|: width: 50, max: 50
        // |dDDd|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(0)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_trailing_Bidi_RTLFirst() {
        val text = "\u05D0aa\u05D0 \u05D1bb\u05D1 \u05D2cc\u05D2 \u05D3dd\u05D3"

        // Width constraint: 1000px
        // DddD CccC BbbB AaaA|: width: 190, max: 190
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(1000)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(1000)

        // Width constraint: 150px
        // |CccC BbbB AaaA|: width: 150, max: 140
        // |          DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(150)
        // DddD has 15px right overshoots, so shifting drawing offset 15px left.
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(135)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(135)

        // Width constraint: 105px
        // |BbbB AaaA|: width: 100, max: 90
        // |DddD CccC|: width: 100, max 100
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(105)
        // CccC has 10px right overshoots, so shifting drawing offset 10px left.
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(95)

        // Width constraint: 95px
        // |BbbB AaaA|: width: 100, max: 90
        // |     CccC|: width: 60, max: 50
        // |     DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(95)
        // DddD has 15px right overshoots, CccC has 10px right overshoot, so shifting drawing offset
        // 15px left which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(80)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(80)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(80)

        // Width constraint: 55px
        // |AaaA|: width: 50, max: 40
        // |BbbB|: width: 55, max: 45
        // |CccC|: width: 60, max: 50
        // |DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(55)
        // DddD has 15px right overshoots, CccC has 10px right overshoot and BbbB has 5px right
        // overshoot, so shifting drawing offset 15px left which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(40)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(40)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_preceding_LTR() {
        val text = "aaaa eeee ffff gggg"

        // Width constraint: 1000px
        // |aaaa eeee ffff gggg     : width: 190, max: 190
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(0)

        // Width constraint: 150px
        // |aaaa eeee ffff     |: width: 150, max 140
        // |gggg               |: width: 55, max 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(0)
        // gggg has 15px left overshoots, so shifting drawing offset 15px right.
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(15)

        // Width constraint: 105px
        // |aaaa eeee    |: width: 100, max: 90
        // |ffff gggg    |: width: 100, max 100
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(0)
        // ffff has 10px left overshoots, so shifting drawing offset 10px right.
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(10)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(10)

        // Width constraint: 95px
        // |aaaa eeee|: width: 100, max: 90
        // |ffff     |: width: 60, max: 50
        // |gggg     |: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(0)
        // gggg has 15px left overshoots, ffff has 10px left overshoot, so shifting drawing offset
        // 15px right which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(15)

        // Width constraint: 55px
        // |aaaa|: width: 50, max: 40
        // |bbbb|: width: 55, max: 45
        // |cccc|: width: 60, max: 50
        // |dddd|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(0)
        // gggg has 15px left overshoots, ffff has 10px left overshoot and bbbb has 5px right
        // overshoot, so shifting drawing offset 15px right which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(15)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_preceding_RTL() {
        val text = "\u05D0\u05D0\u05D0\u05D0 \u05D4\u05D4\u05D4\u05D4 " +
                "\u05D5\u05D5\u05D5\u05D5 \u05D6\u05D6\u05D6\u05D6"

        // Width constraint: 1000px
        // GGGG FFFF EEEE AAAA|: width: 205, max: 205
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(1000)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(1000)

        // Width constraint: 150px
        // |FFFF EEEE AAAA|: width: 150, max: 150
        // |          GGGG|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(150)

        // Width constraint: 105px
        // |EEEE AAAA|: width: 100, max: 95
        // |GGGG FFFF|: width: 105, max 105
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(105)

        // Width constraint: 95px
        // |EEEE AAAA|: width: 100, max: 95
        // |     FFFF|: width: 50, max: 50
        // |     GGGG|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(95)

        // Width constraint: 55px
        // |AAAA|: width: 50, max: 40
        // |EEEE|: width: 50, max: 45
        // |FFFF|: width: 50, max: 50
        // |GGGG|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(55)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_preceding_Bidi_LTRFirst() {
        val text = "a\u05D0\u05D0a e\u05D4\u05D4e f\u05D5\u05D5f g\u05D6\u05D6g"

        // Width constraint: 1000px
        // |aAAa eEEe fFFf gGGg     : width: 190, max: 190
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(0)

        // Width constraint: 150px
        // |aAAa eEEe fFFf     |: width: 150, max 140
        // |gGGg               |: width: 55, max 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(0)
        // gGGg has 15px left overshoots, so shifting drawing offset 15px right.
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(15)

        // Width constraint: 105px
        // |aAAa eEEe    |: width: 100, max: 90
        // |fFFf gGGg    |: width: 100, max 100
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(0)
        // fFFf has 10px left overshoots, so shifting drawing offset 10px right.
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(10)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(10)

        // Width constraint: 95px
        // |aAAa eEEe|: width: 100, max: 90
        // |fFFf     |: width: 60, max: 50
        // |gGGg     |: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(0)
        // gGGg has 15px left overshoots, fFFf has 10px left overshoot, so shifting drawing offset
        // 15px right which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(15)

        // Width constraint: 55px
        // |aAAa|: width: 50, max: 40
        // |eEEe|: width: 55, max: 45
        // |fFFf|: width: 60, max: 50
        // |gGGg|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(0)
        // gGGg has 15px left overshoots, fFFf has 10px left overshoot and eEEe has 5px right
        // overshoot, so shifting drawing offset 15px right which is maximum overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(15)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(15)
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_preceding_Bidi_RTLFirst() {
        val text = "\u05D0aa\u05D0 \u05D4ee\u05D4 \u05D5ff\u05D5 \u05D6gg\u05D6"

        // Width constraint: 1000px
        // DddD CccC BbbB AaaA|: width: 205, max: 205
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false), 0)).isEqualTo(1000)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true), 0)).isEqualTo(1000)

        // Width constraint: 150px
        // |CccC BbbB AaaA|: width: 150, max: 150
        // |          DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, false), 1)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 0)).isEqualTo(150)
        assertThat(getDrawingXOffset(buildLayout(text, 150, true), 1)).isEqualTo(150)

        // Width constraint: 105px
        // |BbbB AaaA|: width: 100, max: 95
        // |DddD CccC|: width: 105, max 105
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, false), 1)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 0)).isEqualTo(105)
        assertThat(getDrawingXOffset(buildLayout(text, 105, true), 1)).isEqualTo(105)

        // Width constraint: 95px
        // |BbbB AaaA|: width: 100, max: 95
        // |     CccC|: width: 50, max: 50
        // |     DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, false), 2)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 0)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 1)).isEqualTo(95)
        assertThat(getDrawingXOffset(buildLayout(text, 95, true), 2)).isEqualTo(95)

        // Width constraint: 55px
        // |AaaA|: width: 50, max: 40
        // |BbbB|: width: 50, max: 45
        // |CccC|: width: 50, max: 50
        // |DddD|: width: 55, max: 55
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, false), 3)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 0)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 1)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 2)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(55)
        assertThat(getDrawingXOffset(buildLayout(text, 55, true), 3)).isEqualTo(55)
    }
}
