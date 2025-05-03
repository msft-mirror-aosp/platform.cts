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

package android.graphics.text.cts

import android.graphics.Paint
import android.graphics.Typeface
import android.os.LocaleList
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4ClassRunner::class)
class LanguageSpecificVerticalMetrics {
    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()
    private val mContext = mInstrumentation.targetContext

    // This font has ascent = -0.8em, descent = 0.2em in hhea table.
    // This font also has ascent = -1.0em, descent = 0.4em BASE table for Vietnamese language.
    private val mFontPath = "fonts/vertical_metrics/base_table_font.ttf"

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(Flags.FLAG_LANGUAGE_SPECIFIC_EXTENT)
    @Test
    fun languageSpecificVerticalExtent_withText() {
        val fmi = Paint.FontMetricsInt()
        val paint = Paint().apply {
            textSize = 100f // make 1em = 100px
            typeface = Typeface.createFromAsset(mContext.assets, mFontPath)
        }

        paint.textLocales = LocaleList.forLanguageTags("en-US")
        paint.getFontMetricsInt("abc", 0, 3, 0, 3, false, fmi)
        // Vertical metrics for Latin language.
        assertThat(fmi.ascent).isEqualTo(-80)
        assertThat(fmi.descent).isEqualTo(20)

        paint.textLocales = LocaleList.forLanguageTags("vi-VI")
        paint.getFontMetricsInt("abc", 0, 3, 0, 3, false, fmi)
        // Vertical metrics for Vietnamese language.
        assertThat(fmi.ascent).isEqualTo(-100)
        assertThat(fmi.descent).isEqualTo(40)
    }

    @RequiresFlagsEnabled(Flags.FLAG_LANGUAGE_SPECIFIC_EXTENT)
    @Test
    fun languageSpecificVerticalExtent_withoutText() {
        val fmi = Paint.FontMetricsInt()
        val paint = Paint().apply {
            textSize = 100f // make 1em = 100px
            typeface = Typeface.createFromAsset(mContext.assets, mFontPath)
        }

        paint.textLocales = LocaleList.forLanguageTags("en-US")
        paint.getFontMetricsIntForLocale(fmi)
        // Vertical metrics for Latin language.
        assertThat(fmi.ascent).isEqualTo(-80)
        assertThat(fmi.descent).isEqualTo(20)

        paint.textLocales = LocaleList.forLanguageTags("vi-VI")
        paint.getFontMetricsIntForLocale(fmi)
        // Vertical metrics for Vietnamese language.
        assertThat(fmi.ascent).isEqualTo(-100)
        assertThat(fmi.descent).isEqualTo(40)
    }
}
