/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.BoringLayout
import android.text.Layout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BoringLayoutDrawShiftTest {
    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

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
    private val overshootFont = Typeface.createFromAsset(context.assets, "fonts/OvershootTest.ttf")
    private val overshootPaint = TextPaint().apply {
        typeface = overshootFont
        textSize = 10f // make 1em = 10px
    }

    private fun buildLayout(text: String, widthPx: Int, shiftDrawingOffset: Boolean = false) =
            Layout.Builder(text, 0, text.length, overshootPaint, widthPx)
                    .setUseBoundsForWidth(true)
                    .setShiftDrawingOffsetForStartOverhang(shiftDrawingOffset)
                    .build().also {
                        assertThat(it).isInstanceOf(BoringLayout::class.java)
                        assertThat(it.useBoundsForWidth).isTrue()
                        assertThat(it.shiftDrawingOffsetForStartOverhang)
                                .isEqualTo(shiftDrawingOffset)
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
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true))).isEqualTo(0)
    }

    private fun getDrawingXOffset(layout: Layout): Int {
        val bmp = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val drawOffsets = mutableListOf<Int>()
        layout.drawText(canvas, 0, layout.lineCount - 1, { x, y ->
            drawOffsets.add(x)
        })
        return drawOffsets[0]
    }

    @RequiresFlagsEnabled(
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT,
        com.android.text.flags.Flags.FLAG_FIX_SHIFT_DRAWING_AMOUNT_TEST_API
    )
    @Test
    fun testBreakOvershoot_preceding_LTR() {
        val text = "gggg ffff eeee aaaa"

        // Width constraint: 1000px
        // |gggg ffff eeee aaaa     : width: 205, max: 205
        assertThat(getDrawingXOffset(buildLayout(text, 1000, false))).isEqualTo(0)
        // gggg has 15px left overshoot.
        assertThat(getDrawingXOffset(buildLayout(text, 1000, true))).isEqualTo(15)
    }
}
