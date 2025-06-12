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

package android.graphics.cts

import android.R
import android.app.UiModeManager
import android.app.UiModeManager.MODE_NIGHT_NO
import android.app.UiModeManager.MODE_NIGHT_YES
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.cts.utils.Material2021SpecMatcher
import android.platform.test.annotations.DisabledOnRavenwood
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.systemui.monet.Style
import com.android.systemui.monet.Style.EXPRESSIVE
import com.android.systemui.monet.Style.FRUIT_SALAD
import com.android.systemui.monet.Style.MONOCHROMATIC
import com.android.systemui.monet.Style.RAINBOW
import com.android.systemui.monet.Style.SPRITZ
import com.android.systemui.monet.Style.TONAL_SPOT
import com.android.systemui.monet.Style.VIBRANT
import com.google.common.truth.Truth.assertWithMessage
import com.google.ux.material.libmonet.contrast.Contrast
import com.google.ux.material.libmonet.hct.Hct
import java.io.Serializable
import kotlin.math.ceil
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext
import platform.test.screenshot.ScreenshotAsserterConfig
import platform.test.screenshot.ScreenshotTestRule
import platform.test.screenshot.matchers.PixelPerfectMatcher

@RunWith(Parameterized::class)
@DisabledOnRavenwood(reason = "Cannot instantiate Parameterized runner")
class SystemPaletteTest(
    private val color: String,
    private val style: String,
    private val mode: String,
) {
    val mContext: Context = getInstrumentation().targetContext

    val isOldSpec: Boolean =
        mContext.resources.getIdentifier("system_primary_dim_light", "color", "android") == 0

    private val goldenPathManager = GoldenPathManager(
            appContext = mContext,
            assetsPathRelativeToBuildRoot = "cts/tests/tests/graphics/assets/",
            pathConfig = PathConfig(PathElementNoContext("spec", true){
                if (isOldSpec) "libmonet_2021" else "libmonet_2025"
            })
    )

    @get:Rule val screenshotTestRule = ScreenshotTestRule(goldenPathManager)

    @OptIn(ExperimentalStdlibApi::class)
    companion object {

        private var initialContrast: Float = 0f

        @JvmStatic
        @Parameterized.BeforeParam
        fun setup(color: String, style: String, mode: String) {
            val context = getInstrumentation().targetContext
            val uiModeManager = context.getSystemService(UiModeManager::class.java)

            val isTestingNightMode = mode == "dark"
            val currentMode = uiModeManager.nightMode == MODE_NIGHT_YES
            val expectedMode = if (isTestingNightMode) MODE_NIGHT_YES else MODE_NIGHT_NO

            if (currentMode != isTestingNightMode) {
                runWithShellPermissionIdentity { uiModeManager.nightMode = expectedMode }

                assumeTrue(uiModeManager.nightMode == expectedMode)
            }

            val currentJson =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                )

            val jsonString =
                "{\"android.theme.customization.system_palette\":\"${color}\"," +
                    "\"android.theme.customization.theme_style\":\"${style}\"}"

            val keyColorBefore =
                context.getColor(R.color.system_palette_key_color_primary_dark).toHexString()

            if (currentJson != jsonString) {
                runWithShellPermissionIdentity {
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                        jsonString,
                    )
                }
            }

            // checks for color changes
            for (i in 0..30) {
                val keyColorAfter =
                    context.getColor(R.color.system_palette_key_color_primary_dark).toHexString()

                if (keyColorBefore != keyColorAfter) {
                    break
                }

                Thread.sleep(100L)
            }
        }

        @JvmStatic
        @BeforeClass
        fun setUp() {
            initialContrast =
                getInstrumentation()
                    .targetContext
                    .getSystemService(UiModeManager::class.java)
                    .contrast
            runShellCommand("settings put secure contrast_level 0.0")
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            runShellCommand("settings put secure contrast_level $initialContrast")
        }

        @Parameterized.Parameters(name = "{2}_{0}_{1}")
        @JvmStatic
        fun testData(): List<Array<Serializable>> {
            val dataList: MutableList<Array<Serializable>> = mutableListOf()

            listOf("dark", "light").forEach { mode ->
                listOf("FFB9577A", "FFB16407", "FF6E7F10", "FF008673", "FF007FB4", "FF8267C2")
                    .forEach { color ->
                        intArrayOf(
                                SPRITZ,
                                TONAL_SPOT,
                                VIBRANT,
                                EXPRESSIVE,
                                RAINBOW,
                                FRUIT_SALAD,
                                MONOCHROMATIC,
                            )
                            .forEach { style ->
                                dataList.add(arrayOf(color, Style.name(style), mode))
                            }
                    }
            }

            return dataList
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun testSystemPalette() {
        assumeTrue(!FeatureUtil.isWatch())

        val goldenName = "Palette_${mode}_${color}_$style".replace("#", "")

        screenshotTestRule
            .createScreenshotAsserter(
                ScreenshotAsserterConfig(
                    matcher = if (isOldSpec) Material2021SpecMatcher() else PixelPerfectMatcher(),
                    captureStrategy = { generatePaletteBitmap() }
                )
            )
            .assertGoldenImage(goldenName)
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

    private fun generatePaletteBitmap(): Bitmap {
        val isDark = mode == "dark"
        val textSize = 20
        val swatchWidth = 150

        val chartTextPaint =
            Paint().apply {
                color = if (isDark) Color.WHITE else Color.BLACK
                this.textSize = textSize.toFloat()
                typeface = Typeface.MONOSPACE
                isAntiAlias = false // Try avoid errors if this runs in different devices
            }
        val swatchNamePaint =
            Paint().apply {
                color = Color.BLACK
                this.textSize = textSize.toFloat()
                typeface = Typeface.MONOSPACE
                isAntiAlias = false
                textAlign = Paint.Align.CENTER
            }
        val swatchPaint =
            Paint().apply {
                style = Paint.Style.FILL
                color = Color.BLACK
            }

        val widestShadeText = ceil(chartTextPaint.measureText("1000")).toInt()
        val spacing = 10
        val padding = IntRect(50, 50, 50, 50)
        val shadeHeight = 2000 // Not accounting for the first shade height

        val totalShadeHeight = shadeHeight + textSize // a bit extra for the first (0) shade

        val colorSpan = IntOffset(allColorRes.size * swatchWidth, totalShadeHeight)

        val colorSpanPos =
            IntOffset(padding.left + widestShadeText + spacing, padding.top + textSize + spacing)
        val bitmapSize =
            IntOffset(
                colorSpanPos.x + colorSpan.x + padding.right,
                colorSpanPos.y + colorSpan.y + padding.bottom,
            )

        val bitmap = Bitmap.createBitmap(bitmapSize.x, bitmapSize.y, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(if (isDark) Color.DKGRAY else Color.LTGRAY)

        var prevX = colorSpanPos.x.toFloat()

        fun abbreviateResName(name: String): String {
            val firstLetter = name.firstOrNull()?.uppercase() ?: ""
            val numberSequenceMatch = Regex("\\d+").find(name)
            val numberSequence = numberSequenceMatch?.value ?: ""
            return "$firstLetter$numberSequence"
        }

        allColorRes.forEachIndexed { paletteIndex, palette ->
            val newX = colorSpanPos.x + (swatchWidth * paletteIndex) + swatchWidth.toFloat()
            var prevY = colorSpanPos.y.toFloat()

            palette.forEachIndexed { resIndex, res ->
                val resName = mContext.resources.getResourceEntryName(res)
                val shade = resName.substringAfterLast("_").toInt()
                val newY = colorSpanPos.y + textSize + (shadeHeight / 1000f * shade)

                val centerY = ((prevY + newY) / 2) + (textSize / 2)

                // we should draw a shade label on the left
                if (paletteIndex == 0) {
                    canvas.drawText(
                        shade.toString(),
                        padding.left.toFloat(),
                        centerY,
                        chartTextPaint,
                    )
                }

                // we should draw the palette label on top
                val paletteName = resName.split("_")[1]
                if (resIndex == 0) {
                    canvas.drawText(paletteName, prevX, padding.top.toFloat(), chartTextPaint)
                }

                // now we draw the swatch
                swatchPaint.color = mContext.getColor(res)
                canvas.drawRect(prevX, prevY, newX, newY, swatchPaint)

                // draw swatch name abbreviation
                swatchNamePaint.color = if (shade > 500) Color.WHITE else Color.BLACK
                canvas.drawText(
                    "${abbreviateResName(paletteName)}-$shade",
                    (prevX + newX) / 2,
                    centerY,
                    swatchNamePaint,
                )

                prevY = newY
            }

            prevX = newX
        }

        return bitmap
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
    private constructor(var mContext: Context, vararg var mForegrounds: Int) {
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
                                    "(#${
                                            Integer.toHexString(mContext.getColor(bgRes))
                                        }) " +
                                    "should have at least $mContrasLevel " +
                                    "contrast ratio against Foreground Color '" +
                                    res.getResourceName(fgRes) +
                                    "' (#${Integer.toHexString(mContext.getColor(fgRes))}) " +
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

    private class BulkContrastTester private constructor(vararg testsArgs: ContrastTester) {
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
