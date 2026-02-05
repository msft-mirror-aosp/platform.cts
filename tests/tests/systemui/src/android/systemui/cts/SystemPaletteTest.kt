/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.systemui.cts

import android.R
import android.content.Context
import android.content.theming.ThemeStyle
import android.graphics.Color
import android.platform.test.annotations.DisabledOnRavenwood
import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth.assertWithMessage
import com.google.ux.material.libmonet.contrast.Contrast
import com.google.ux.material.libmonet.hct.Hct
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@DisabledOnRavenwood(reason = "Cannot instantiate Parameterized runner")
class SystemPaletteTest(params: PaletteParams) : BasePaletteTest(params) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testData(): Collection<PaletteParams> {
            if (!isDynamicColorSupported) {
                return listOf(PaletteParams(listOf(), ThemeStyle.TONAL_SPOT, "standard", 0.0f))
            }

            val dataList = mutableListOf<PaletteParams>()

            getSeedColors().forEach { colors ->
                STYLES.forEach { style ->
                    dataList.add(
                        PaletteParams(colors, style, "standard", 0.0f)
                    )
                }
            }
            return dataList
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun testSystemPalette() {
        assertPaletteGolden("Palette") { SystemPaletteTable(params) }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun testShades0and1000() {
        fun assertColor(@ColorInt observed: Int, @ColorInt expected: Int) {
            Assert.assertEquals(
                "Color = ${Integer.toHexString(observed)}, " +
                        "${Integer.toHexString(expected)} expected",
                expected,
                observed,
            )
        }

        allPalettes().forEach { palette ->
            assertColor(palette.first(), Color.WHITE)
            assertColor(palette.last(), Color.BLACK)
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun testColorsMatchExpectedLuminance() {
        val labColor = doubleArrayOf(0.0, 0.0, 0.0)
        val expectedL =
            arrayOf(100.0, 99.0, 95.0, 90.0, 80.0, 70.0, 60.0, 49.0, 40.0, 30.0, 20.0, 10.0, 0.0)

        allPalettes().forEach { palette ->
            palette.forEachIndexed { i, paletteColor ->
                val expectedColor = expectedL[i]
                ColorUtils.colorToLAB(paletteColor, labColor)
                assertWithMessage(
                    "Color ${Integer.toHexString(paletteColor)} at index $i should " +
                            "have L $expectedColor in LAB space."
                )
                    .that(labColor[0])
                    .isWithin(3.0)
                    .of(expectedColor)
            }
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun testContrastRatio() {
        val atLeast4dot45 =
            listOf(
                Pair(0, 500),
                Pair(50, 600),
                Pair(100, 600),
                Pair(200, 700),
                Pair(300, 800),
                Pair(400, 900),
                Pair(500, 1000),
            )

        val atLeast3dot0 =
            listOf(
                Pair(0, 400),
                Pair(50, 500),
                Pair(100, 500),
                Pair(200, 600),
                Pair(300, 700),
                Pair(400, 800),
                Pair(500, 900),
                Pair(600, 1000),
            )

        fun shadeToArrayIndex(shade: Int): Int {
            return when (shade) {
                0 -> 0
                10 -> 1
                50 -> 2
                else -> {
                    shade / 100 + 2
                }
            }
        }

        fun pairContrastCheck(palette: IntArray, shades: Pair<Int, Int>, contrastLevel: Double) {
            val background = palette[shadeToArrayIndex(shades.first)]
            val foreground = palette[shadeToArrayIndex(shades.second)]

            val contrast =
                Contrast.ratioOfTones(Hct.fromInt(foreground).tone, Hct.fromInt(background).tone)

            assertWithMessage(
                "Shade ${shades.first} (#${Integer.toHexString(background)}) " +
                        "should have at least $contrastLevel contrast ratio against " +
                        "${shades.second} (#${
                            Integer.toHexString(
                                foreground
                            )
                        }), but had $contrast"
            )
                .that(contrast)
                .isGreaterThan(contrastLevel)
        }

        allPalettes().forEach { palette ->
            atLeast4dot45.forEach { shades -> pairContrastCheck(palette, shades, 4.45) }
            atLeast3dot0.forEach { shades -> pairContrastCheck(palette, shades, 3.0) }
        }
    }

    @Test
    fun testDynamicColorContrast() {
        // Ideally this should be 3.0, but there's colorspace conversion that causes rounding
        // errors.
        val foregroundContrast = 2.9f

        val bulkTest: BulkContrastTester =
            BulkContrastTester.of(
                // Colors against Surface [DARK]
                ContrastTester.ofBackgrounds(
                    mContext,
                    R.color.system_surface_dark,
                    R.color.system_surface_dim_dark,
                    R.color.system_surface_bright_dark,
                    R.color.system_surface_container_dark,
                    R.color.system_surface_container_high_dark,
                    R.color.system_surface_container_highest_dark,
                    R.color.system_surface_container_low_dark,
                    R.color.system_surface_container_lowest_dark,
                    R.color.system_surface_variant_dark,
                )
                    .andForegrounds(
                        4.5f,
                        R.color.system_on_surface_dark,
                        R.color.system_on_surface_variant_dark,
                        R.color.system_primary_dark,
                        R.color.system_secondary_dark,
                        R.color.system_tertiary_dark,
                        R.color.system_error_dark,
                    )
                    .andForegrounds(foregroundContrast, R.color.system_outline_dark),

                // Colors against Surface [LIGHT]
                ContrastTester.ofBackgrounds(
                    mContext,
                    R.color.system_surface_light,
                    R.color.system_surface_dim_light,
                    R.color.system_surface_bright_light,
                    R.color.system_surface_container_light,
                    R.color.system_surface_container_high_light,
                    R.color.system_surface_container_highest_light,
                    R.color.system_surface_container_low_light,
                    R.color.system_surface_container_lowest_light,
                    R.color.system_surface_variant_light,
                )
                    .andForegrounds(
                        4.5f,
                        R.color.system_on_surface_light,
                        R.color.system_on_surface_variant_light,
                        R.color.system_primary_light,
                        R.color.system_secondary_light,
                        R.color.system_tertiary_light,
                        R.color.system_error_light,
                    )
                    .andForegrounds(foregroundContrast, R.color.system_outline_light),

                // Colors against accents [DARK]
                ContrastTester.ofBackgrounds(mContext, R.color.system_primary_dark)
                    .andForegrounds(4.5f, R.color.system_on_primary_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_primary_container_dark)
                    .andForegrounds(4.5f, R.color.system_on_primary_container_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_secondary_dark)
                    .andForegrounds(4.5f, R.color.system_on_secondary_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_secondary_container_dark)
                    .andForegrounds(4.5f, R.color.system_on_secondary_container_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_tertiary_dark)
                    .andForegrounds(4.5f, R.color.system_on_tertiary_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_tertiary_container_dark)
                    .andForegrounds(4.5f, R.color.system_on_tertiary_container_dark),

                // Colors against accents [LIGHT]
                ContrastTester.ofBackgrounds(mContext, R.color.system_primary_light)
                    .andForegrounds(4.5f, R.color.system_on_primary_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_primary_container_light)
                    .andForegrounds(4.5f, R.color.system_on_primary_container_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_secondary_light)
                    .andForegrounds(4.5f, R.color.system_on_secondary_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_secondary_container_light)
                    .andForegrounds(4.5f, R.color.system_on_secondary_container_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_tertiary_light)
                    .andForegrounds(4.5f, R.color.system_on_tertiary_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_tertiary_container_light)
                    .andForegrounds(4.5f, R.color.system_on_tertiary_container_light),

                // Colors against accents [FIXED]
                ContrastTester.ofBackgrounds(
                    mContext,
                    R.color.system_primary_fixed,
                    R.color.system_primary_fixed_dim,
                )
                    .andForegrounds(
                        4.5f,
                        R.color.system_on_primary_fixed,
                        R.color.system_on_primary_fixed_variant,
                    ),
                ContrastTester.ofBackgrounds(
                    mContext,
                    R.color.system_secondary_fixed,
                    R.color.system_secondary_fixed_dim,
                )
                    .andForegrounds(
                        4.5f,
                        R.color.system_on_secondary_fixed,
                        R.color.system_on_secondary_fixed_variant,
                    ),
                ContrastTester.ofBackgrounds(
                    mContext,
                    R.color.system_tertiary_fixed,
                    R.color.system_tertiary_fixed_dim,
                )
                    .andForegrounds(
                        4.5f,
                        R.color.system_on_tertiary_fixed,
                        R.color.system_on_tertiary_fixed_variant,
                    ),

                // Auxiliary Colors [DARK]
                ContrastTester.ofBackgrounds(mContext, R.color.system_error_dark)
                    .andForegrounds(4.5f, R.color.system_on_error_dark),
                ContrastTester.ofBackgrounds(mContext, R.color.system_error_container_dark)
                    .andForegrounds(4.5f, R.color.system_on_error_container_dark),

                // Auxiliary Colors [LIGHT]
                ContrastTester.ofBackgrounds(mContext, R.color.system_error_light)
                    .andForegrounds(4.5f, R.color.system_on_error_light),
                ContrastTester.ofBackgrounds(mContext, R.color.system_error_container_light)
                    .andForegrounds(4.5f, R.color.system_on_error_container_light),
            )
        bulkTest.run()
        assertWithMessage(bulkTest.allMessages).that(bulkTest.testPassed).isTrue()
    }

    // Helper methods

    @Composable
    private fun SystemPaletteTable(params: PaletteParams) {
        val seedColors = params.colors.map { ComposeColor(Color.parseColor("#$it")) }
        val titleTextColor =
            if (Hct.fromInt(seedColors.first().toArgb()).tone > 50) {
                ComposeColor.Black
            } else {
                ComposeColor.White
            }

        val headingTextStyle =
            TextStyle(
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

        val infoTextStyle = TextStyle(
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        val swatchPadding = 15.dp
        val tablePadding = 20.dp

        PaletteTemplate(
            testName = "SystemPaletteTest",
            params = params,
            backgroundColor = seedColors.first(),
            textColor = titleTextColor
        ) {
            Column {
                listOf("_dark", "_light").forEach { modeSuffix ->
                    val groupBackgroundColor =
                        if (modeSuffix == "_dark") ComposeColor.DarkGray else ComposeColor.LightGray
                    Row(
                        modifier = Modifier.background(groupBackgroundColor).padding(tablePadding)
                    ) {
                        allColorRes.forEach { palette ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                palette.forEach { resId ->
                                    val resName = mContext.resources.getResourceEntryName(resId)
                                    val resourceName = "${resName}$modeSuffix"
                                    val colorValue = getColorByName(mContext, resourceName).toArgb()
                                    val colorHct = Hct.fromInt(colorValue)
                                    val parts = resName.split("_")
                                    val paletteName = parts[1]
                                    val shade = parts.last()
                                    val abbreviation =
                                        "${paletteName.first().uppercase()}${paletteName.last()}"

                                    val swatch = SwatchData(
                                        heading = "$abbreviation-$shade",
                                        info = "#${
                                            String.format(
                                                "%08x",
                                                colorValue
                                            ).uppercase()
                                        } | $colorHct",
                                        colorValue = colorValue
                                    )
                                    SwatchItem(
                                        swatch,
                                        headingTextStyle,
                                        infoTextStyle,
                                        swatchPadding,
                                        Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun allPalettes(): List<IntArray> {
        return allColorRes
            .stream()
            .map {
                if (it.size != 13) throw Exception("Color palettes must be 13 in size")
                it.map { resId -> mContext.getColor(resId) }.toIntArray()
            }
            .toList()
    }

    // Helper Classes

    private class ContrastTester
    private constructor(val mContext: Context, vararg val mForegrounds: Int) {
        var mBgGroups = ArrayList<Background>()

        fun checkContrastLevels(): ArrayList<String> {
            val newFailMessages = ArrayList<String>()
            mBgGroups.forEach { background ->
                newFailMessages.addAll(background.checkContrast(mForegrounds))
            }
            return newFailMessages
        }

        fun andForegrounds(contrastLevel: Float, vararg res: Int): ContrastTester {
            mBgGroups.add(Background(contrastLevel, *res))
            return this
        }

        private inner class Background(
            private val mContrasLevel: Float,
            private vararg val mEntries: Int,
        ) {
            fun checkContrast(foregrounds: IntArray): ArrayList<String> {
                val newFailMessages = ArrayList<String>()
                val res = mContext.resources

                foregrounds.forEach { fgRes ->
                    mEntries.forEach { bgRes ->
                        if (!checkPair(mContext, mContrasLevel, fgRes, bgRes)) {
                            val background = mContext.getColor(bgRes)
                            val foreground = mContext.getColor(fgRes)
                            val contrast = ColorUtils.calculateContrast(foreground, background)
                            val msg =
                                "Background Color '${res.getResourceName(bgRes)}'" +
                                        "(#${Integer.toHexString(background)}) " +
                                        "should have at least $mContrasLevel " +
                                        "contrast ratio against Foreground Color '" +
                                        res.getResourceName(fgRes) +
                                        "' (#${Integer.toHexString(foreground)}) " +
                                        " but had $contrast"

                            newFailMessages.add(msg)
                        }
                    }
                }

                return newFailMessages
            }
        }

        companion object {
            fun ofBackgrounds(context: Context, vararg res: Int): ContrastTester {
                return ContrastTester(context, *res)
            }

            fun checkPair(context: Context, minContrast: Float, fgRes: Int, bgRes: Int): Boolean {
                val background = context.getColor(bgRes)
                val foreground = context.getColor(fgRes)
                val contrast =
                    Contrast.ratioOfTones(
                        Hct.fromInt(foreground).tone,
                        Hct.fromInt(background).tone,
                    )
                return contrast > minContrast
            }
        }
    }

    private class BulkContrastTester private constructor(vararg val testsArgs: ContrastTester) {
        private val tests = testsArgs
        private val errorMessages: MutableList<String> = mutableListOf()

        val testPassed: Boolean
            get() = errorMessages.isEmpty()

        val allMessages: String
            get() = if (testPassed) "Test OK" else errorMessages.joinToString("\n")

        fun run() {
            errorMessages.clear()
            tests.forEach { test -> errorMessages.addAll(test.checkContrastLevels()) }
        }

        companion object {
            fun of(vararg testers: ContrastTester): BulkContrastTester {
                return BulkContrastTester(*testers)
            }
        }
    }
}

private val allAccent1ResIDs =
    intArrayOf(
        R.color.system_accent1_0,
        R.color.system_accent1_10,
        R.color.system_accent1_50,
        R.color.system_accent1_100,
        R.color.system_accent1_200,
        R.color.system_accent1_300,
        R.color.system_accent1_400,
        R.color.system_accent1_500,
        R.color.system_accent1_600,
        R.color.system_accent1_700,
        R.color.system_accent1_800,
        R.color.system_accent1_900,
        R.color.system_accent1_1000,
    )

private val allAccent2ResIDs =
    intArrayOf(
        R.color.system_accent2_0,
        R.color.system_accent2_10,
        R.color.system_accent2_50,
        R.color.system_accent2_100,
        R.color.system_accent2_200,
        R.color.system_accent2_300,
        R.color.system_accent2_400,
        R.color.system_accent2_500,
        R.color.system_accent2_600,
        R.color.system_accent2_700,
        R.color.system_accent2_800,
        R.color.system_accent2_900,
        R.color.system_accent2_1000,
    )

private val allAccent3ResIDs =
    intArrayOf(
        R.color.system_accent3_0,
        R.color.system_accent3_10,
        R.color.system_accent3_50,
        R.color.system_accent3_100,
        R.color.system_accent3_200,
        R.color.system_accent3_300,
        R.color.system_accent3_400,
        R.color.system_accent3_500,
        R.color.system_accent3_600,
        R.color.system_accent3_700,
        R.color.system_accent3_800,
        R.color.system_accent3_900,
        R.color.system_accent3_1000,
    )

private val allNeutral1ResIDs =
    intArrayOf(
        R.color.system_neutral1_0,
        R.color.system_neutral1_10,
        R.color.system_neutral1_50,
        R.color.system_neutral1_100,
        R.color.system_neutral1_200,
        R.color.system_neutral1_300,
        R.color.system_neutral1_400,
        R.color.system_neutral1_500,
        R.color.system_neutral1_600,
        R.color.system_neutral1_700,
        R.color.system_neutral1_800,
        R.color.system_neutral1_900,
        R.color.system_neutral1_1000,
    )

private val allNeutral2ResIDs =
    intArrayOf(
        R.color.system_neutral2_0,
        R.color.system_neutral2_10,
        R.color.system_neutral2_50,
        R.color.system_neutral2_100,
        R.color.system_neutral2_200,
        R.color.system_neutral2_300,
        R.color.system_neutral2_400,
        R.color.system_neutral2_500,
        R.color.system_neutral2_600,
        R.color.system_neutral2_700,
        R.color.system_neutral2_800,
        R.color.system_neutral2_900,
        R.color.system_neutral2_1000,
    )

val allColorRes =
    listOf(
        allAccent1ResIDs,
        allAccent2ResIDs,
        allAccent3ResIDs,
        allNeutral1ResIDs,
        allNeutral2ResIDs,
    )
