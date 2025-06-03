/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.cts.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.ux.material.libmonet.hct.Hct
import kotlin.math.abs
import platform.test.screenshot.matchers.BitmapMatcher
import platform.test.screenshot.matchers.MatchResult
import platform.test.screenshot.proto.ScreenshotResultProto

class Material2021SpecMatcher : BitmapMatcher() {
    override fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect>,
    ): MatchResult {
        val filter = getFilter(width, height, regions)
        var different = 0
        var same = 0
        var similar = 0
        var ignored = 0
        val diffArray by lazy { IntArray(width * height) { Color.TRANSPARENT } }

        expected.indices.forEach { index ->
            when {
                !filter[index] -> ignored++
                expected[index] == given[index] -> same++
                getHCTDiff(given[index], expected[index]) < 3 -> {
                    diffArray[index] = Color.CYAN
                    similar++
                }

                else -> {
                    diffArray[index] = Color.MAGENTA
                    different++
                }
            }
        }

        val stats = ScreenshotResultProto.DiffResult.ComparisonStatistics.newBuilder()
                .setNumberPixelsCompared(width * height)
                .setNumberPixelsIdentical(same)
                .setNumberPixelsDifferent(different)
                .setNumberPixelsSimilar(similar)
                .setNumberPixelsIgnored(ignored)
                .build()

        return if (different > 0) {
            val diff = Bitmap.createBitmap(diffArray, width, height, Bitmap.Config.ARGB_8888)
            MatchResult(
                matches = false,
                diff = diff,
                comparisonStatistics = stats
            )
        } else {
            MatchResult(matches = true, diff = null, comparisonStatistics = stats)
        }
    }

    private fun getHCTDiff(color1: Int, color2: Int): Double {
        val hct1 = Hct.fromInt(color1)
        val hct2 = Hct.fromInt(color2)
        return abs(hct1.tone - hct2.tone) +
                abs(hct1.chroma - hct2.chroma) +
                abs(hct1.hue - hct2.hue)
    }
}
