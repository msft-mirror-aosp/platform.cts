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
import android.graphics.Color
import android.graphics.cts.R as CtsR
import android.platform.test.annotations.DisabledOnRavenwood
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertWithMessage
import com.google.ux.material.libmonet.contrast.Contrast
import com.google.ux.material.libmonet.hct.Hct
import java.io.IOException
import java.io.Serializable
import java.util.Locale
import kotlin.math.abs
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Parameterized CTS test verifying Android's System Color Palette generation.
 *
 * This test checks if applying specific theme settings (source color, style, UI mode)
 * results in the expected system color palette resources. Tests run in alphabetical order
 * (`a_`, `b_`, etc.) due to `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`.
 *
 * Setup & Execution Flow:
 * 1.  **Parameters (`testData()`):** Reads combinations of source color (`color`), theme style (`style`),
 * UI mode (`mode`), and the full `expectedPalette` (65 colors) from `valid_themes.xml`.
 *
 * 2.  **Global Setup (`@BeforeClass`):** Resets system `contrast_level` to 0.0 for consistency.
 *
 * 3.  **Per-Test Setup (`@Before`):** Before each test run:
 * a. Sets the system's light/dark mode (`UiModeManager.nightMode`) to match the test's `mode`.
 * b. Constructs a JSON payload with the test's `color` and `style`.
 * c. Writes this JSON to `Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES` to trigger
 * system theme generation.
 * d. Steps 'a' and 'c' require shell permissions (`runWithShellPermissionIdentity`).
 *
 * 4.  **Palette Check (`isPaletteApplied()` / `checkExpectedPalette()`):**
 * a. This core check verifies if the currently applied system theme matches the `expectedPalette`.
 * b. It fetches the *actual* current system colors (e.g., `@color/system_accent1_100`, etc.)
 * using helper methods like `getAllAccent1Colors`.
 * c. It compares this actual palette to the `expectedPalette` for the current test case.
 * d. **Asynchronicity Handling:** Because theme application is asynchronous, `isPaletteApplied(failFast=false)`
 * (used by `a_testThemeStyles`) includes a polling loop: it repeatedly checks the colors
 * and sleeps, retrying for several seconds until the actual colors match the expected ones
 * (within a small HCT tolerance).
 *
 * 5.  **Test Execution Order & Skipping:**
 * a. `a_testThemeStyles` runs first and uses the polling `isPaletteApplied` check. It asserts
 * the palette is correct after waiting.
 * b. Subsequent tests (`b_` onwards) use `assumeTrue` with `isPaletteApplied(failFast = true)`
 * (or rely on its prior success). This means they check if the palette is *already* correct
 * without additional waiting.
 * c. If `a_testThemeStyles` fails (meaning the palette didn't apply correctly even after polling),
 * the assumptions in later tests will fail, effectively skipping their specific checks (like
 * contrast, luminance) as those checks depend on a correctly applied palette.
 *
 * @param color The source color name or hex value.
 * @param style The theme style applied (e.g., "TONAL_SPOT").
 * @param mode The UI mode ("light" or "dark").
 * @param expectedPalette An array of 65 expected integer colors for this configuration.
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisabledOnRavenwood(reason = "Cannot instantiate Parameterized runner")
class SystemPaletteTest(
    private val color: String,
    private val style: String,
    private val mode: String,
    private val expectedPalette: IntArray
) {

    companion object {
        private var initialContrast: Float = 0f

        @JvmStatic
        @BeforeClass
        fun setUp() {
            initialContrast = getInstrumentation()
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

        @Parameterized.Parameters(name = "Mode {2}, Palette {1} with color {0}")
        @JvmStatic
        fun testData(): List<Array<Serializable>> {
            val context: Context = getInstrumentation().targetContext
            val parser: XmlPullParser = context.resources.getXml(CtsR.xml.valid_themes)
            val dataList: MutableList<Array<Serializable>> = mutableListOf()

            try {
                parser.next()
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "themes")

                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    if (parser.name == "mode") {
                        parseModeTag(parser, dataList)
                    } else {
                        skipCurrentTag(parser)
                    }
                }
            } catch (e: XmlPullParserException) {
                throw RuntimeException("Error parsing XML: ${e.message}", e)
            } catch (e: IOException) {
                throw RuntimeException("Error reading XML: ${e.message}", e)
            } catch (e: Exception) {
                throw RuntimeException("Unexpected error during XML processing: ${e.message}", e)
            }

            return dataList
        }

        // XML Parsing Methods

        private fun parseModeTag(
            parser: XmlPullParser,
            dataList: MutableList<Array<Serializable>>
        ) {
            parser.require(XmlPullParser.START_TAG, null, "mode")
            val mode = parser.getAttributeValue(null, "type") ?: ""

            while (parser.nextTag() == XmlPullParser.START_TAG) {
                if (parser.name == "theme") {
                    parseThemeTag(parser, mode, dataList)
                } else {
                    skipCurrentTag(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "mode")
        }

        private fun parseThemeTag(
            parser: XmlPullParser,
            mode: String,
            dataList: MutableList<Array<Serializable>>
        ) {
            parser.require(XmlPullParser.START_TAG, null, "theme")
            val color = parser.getAttributeValue(null, "color") ?: ""

            while (parser.nextTag() == XmlPullParser.START_TAG) {
                parseStyleTag(parser, mode, color, dataList)
            }
            parser.require(XmlPullParser.END_TAG, null, "theme")
        }

        private fun parseStyleTag(
            parser: XmlPullParser,
            mode: String,
            themeColor: String,
            dataList: MutableList<Array<Serializable>>
        ) {
            val styleName = parser.name
            parser.require(XmlPullParser.START_TAG, null, styleName)

            val colorText = parser.nextText()
            val colors = parseColorString(colorText)

            dataList.add(
                arrayOf(
                    themeColor,
                    styleName.uppercase(Locale.getDefault()),
                    mode,
                    colors
                )
            )
            parser.require(XmlPullParser.END_TAG, null, styleName)
        }

        private fun parseColorString(colorString: String): IntArray {
            return colorString.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { hex ->
                    try {
                        Color.parseColor("#$hex")
                    } catch (e: IllegalArgumentException) {
                        System.err.println("Warning: Skipping invalid color value '$hex'")
                        null
                    }
                }
                .toIntArray()
        }

        private fun skipCurrentTag(parser: XmlPullParser) {
            parser.require(XmlPullParser.START_TAG, null, null)
            var depth = 1
            while (depth != 0) {
                when (parser.next()) {
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_DOCUMENT ->
                        throw XmlPullParserException("Reached end of document while skipping tag.")
                }
            }
        }
    }

    private val mContext = getInstrumentation().targetContext
    private val mUiModeManager = mContext.getSystemService(UiModeManager::class.java)
    private val jsonString = "{\"android.theme.customization.system_palette\":\"${color}\"," +
            "\"android.theme.customization.theme_style\":\"${style}\"}"
    private var mSettingString = ""

    @Before
    fun before() {
        val isTestingNightMode = mode == "dark"
        val currentMode = mUiModeManager.nightMode == MODE_NIGHT_YES
        val expectedMode = if (isTestingNightMode) MODE_NIGHT_YES else MODE_NIGHT_NO

        if (currentMode != isTestingNightMode) {
            runWithShellPermissionIdentity {
                mUiModeManager.nightMode = expectedMode
            }

            assumeTrue(mUiModeManager.nightMode == expectedMode)
        }

        val currentJson = Settings.Secure.getString(
            mContext.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES
        )

        if (currentJson != jsonString) {
            runWithShellPermissionIdentity {
                Settings.Secure.putString(
                    mContext.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    jsonString
                )
            }
        }

        mSettingString = Settings.Secure.getString(
            mContext.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES
        )

        assumeTrue("Settings not updated", mSettingString == jsonString)
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun a_testThemeStyles() {
        // THEME_CUSTOMIZATION_OVERLAY_PACKAGES is not available in Wear OS
        assumeTrue(!FeatureUtil.isWatch())

        val (hasCorrectPalette, message) = isPaletteApplied()
        assertWithMessage(message).that(hasCorrectPalette).isTrue()
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun b_testShades0and1000() {
        assumeTrue("Palette Mismatch", isPaletteApplied(true).first)

        val allPalettes = listOf(
            getAllAccent1Colors(mContext),
            getAllAccent2Colors(mContext),
            getAllAccent3Colors(mContext),
            getAllNeutral1Colors(mContext),
            getAllNeutral2Colors(mContext),
        )

        allPalettes.forEach { palette ->
            assertColor(palette.first(), Color.WHITE)
            assertColor(palette.last(), Color.BLACK)
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun c_testColorsMatchExpectedLuminance() {
        assumeTrue("Palette Mismatch", isPaletteApplied(true).first)

        val allPalettes = listOf(
            getAllAccent1Colors(mContext),
            getAllAccent2Colors(mContext),
            getAllAccent3Colors(mContext),
            getAllNeutral1Colors(mContext),
            getAllNeutral2Colors(mContext)
        )

        val labColor = doubleArrayOf(0.0, 0.0, 0.0)
        val expectedL = doubleArrayOf(
            100.0, 99.0, 95.0, 90.0, 80.0, 70.0, 60.0, 49.0, 40.0, 30.0, 20.0, 10.0, 0.0
        )

        allPalettes.forEach { palette ->
            palette.forEachIndexed { i, paletteColor ->
                val expectedColor = expectedL[i]
                ColorUtils.colorToLAB(paletteColor, labColor)
                assertWithMessage(
                    "Color ${Integer.toHexString(paletteColor)} at index $i should " +
                            "have L $expectedColor in LAB space."
                ).that(labColor[0]).isWithin(3.0).of(expectedColor)
            }
        }
    }

    @Test
    @CddTest(requirements = ["3.8.6/C-1-4,C-1-5,C-1-6"])
    fun d_testContrastRatio() {
        assumeTrue("Palette Mismatch", isPaletteApplied(true).first)

        val atLeast4dot45 = listOf(
            Pair(0, 500),
            Pair(50, 600),
            Pair(100, 600),
            Pair(200, 700),
            Pair(300, 800),
            Pair(400, 900),
            Pair(500, 1000)
        )

        val atLeast3dot0 = listOf(
            Pair(0, 400),
            Pair(50, 500),
            Pair(100, 500),
            Pair(200, 600),
            Pair(300, 700),
            Pair(400, 800),
            Pair(500, 900),
            Pair(600, 1000)
        )

        val allPalettes =
            listOf(
                getAllAccent1Colors(mContext),
                getAllAccent2Colors(mContext),
                getAllAccent3Colors(mContext),
                getAllNeutral1Colors(mContext),
                getAllNeutral2Colors(mContext)
            )

        fun pairContrastCheck(palette: IntArray, shades: Pair<Int, Int>, contrastLevel: Double) {
            val background = palette[shadeToArrayIndex(shades.first)]
            val foreground = palette[shadeToArrayIndex(shades.second)]

            val contrast = Contrast.ratioOfTones(
                Hct.fromInt(foreground).tone,
                Hct.fromInt(background).tone
            )

            assertWithMessage(
                "Shade ${shades.first} (#${Integer.toHexString(background)}) " +
                        "should have at least $contrastLevel contrast ratio against " +
                        "${shades.second} (#${Integer.toHexString(foreground)}), but had $contrast"
            ).that(contrast).isGreaterThan(contrastLevel)
        }

        allPalettes.forEach { palette ->
            atLeast4dot45.forEach { shades -> pairContrastCheck(palette, shades, 4.45) }
            atLeast3dot0.forEach { shades -> pairContrastCheck(palette, shades, 3.0) }
        }
    }

    @Test
    fun e_testDynamicColorContrast() {
        assumeTrue("Palette Mismatch", isPaletteApplied(true).first)

        // Ideally this should be 3.0, but there's colorspace conversion that causes rounding
        // errors.
        val foregroundContrast = 2.9f

        val bulkTest: BulkContrastTester = BulkContrastTester.of(
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
                R.color.system_surface_variant_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_surface_dark,
                R.color.system_on_surface_variant_dark,
                R.color.system_primary_dark,
                R.color.system_secondary_dark,
                R.color.system_tertiary_dark,
                R.color.system_error_dark
            ).andForegrounds(
                foregroundContrast,
                R.color.system_outline_dark
            ),

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
                R.color.system_surface_variant_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_surface_light,
                R.color.system_on_surface_variant_light,
                R.color.system_primary_light,
                R.color.system_secondary_light,
                R.color.system_tertiary_light,
                R.color.system_error_light
            ).andForegrounds(
                foregroundContrast,
                R.color.system_outline_light
            ),

            // Colors against accents [DARK]
            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_primary_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_primary_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_primary_container_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_primary_container_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_secondary_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_secondary_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_secondary_container_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_secondary_container_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_tertiary_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_tertiary_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_tertiary_container_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_tertiary_container_dark
            ),

            // Colors against accents [LIGHT]
            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_primary_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_primary_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_primary_container_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_primary_container_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_secondary_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_secondary_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_secondary_container_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_secondary_container_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_tertiary_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_tertiary_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_tertiary_container_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_tertiary_container_light
            ),

            // Colors against accents [FIXED]
            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_primary_fixed,
                R.color.system_primary_fixed_dim
            ).andForegrounds(
                4.5f,
                R.color.system_on_primary_fixed,
                R.color.system_on_primary_fixed_variant
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_secondary_fixed,
                R.color.system_secondary_fixed_dim
            ).andForegrounds(
                4.5f,
                R.color.system_on_secondary_fixed,
                R.color.system_on_secondary_fixed_variant
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_tertiary_fixed,
                R.color.system_tertiary_fixed_dim
            ).andForegrounds(
                4.5f,
                R.color.system_on_tertiary_fixed,
                R.color.system_on_tertiary_fixed_variant
            ),

            // Auxiliary Colors [DARK]
            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_error_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_error_dark
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_error_container_dark
            ).andForegrounds(
                4.5f,
                R.color.system_on_error_container_dark
            ),

            // Auxiliary Colors [LIGHT]
            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_error_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_error_light
            ),

            ContrastTester.ofBackgrounds(
                mContext,
                R.color.system_error_container_light
            ).andForegrounds(
                4.5f,
                R.color.system_on_error_container_light
            )
        )
        bulkTest.run()
        assertWithMessage(bulkTest.allMessages).that(bulkTest.testPassed).isTrue()
    }

    private fun getAllAccent1Colors(context: Context): IntArray {
        return getAllResourceColors(
            context,
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
            R.color.system_accent1_1000
        )
    }

    private fun getAllAccent2Colors(context: Context): IntArray {
        return getAllResourceColors(
            context,
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
            R.color.system_accent2_1000
        )
    }

    private fun getAllAccent3Colors(context: Context): IntArray {
        return getAllResourceColors(
            context,
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
            R.color.system_accent3_1000
        )
    }

    private fun getAllNeutral1Colors(context: Context): IntArray {
        return getAllResourceColors(
            context,
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
            R.color.system_neutral1_1000
        )
    }

    private fun getAllNeutral2Colors(context: Context): IntArray {
        return getAllResourceColors(
            context,
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
            R.color.system_neutral2_1000
        )
    }

    private fun getAllErrorColors(context: Context): IntArray {
        return getAllResourceColors(
            context,
            R.color.system_error_0,
            R.color.system_error_10,
            R.color.system_error_50,
            R.color.system_error_100,
            R.color.system_error_200,
            R.color.system_error_300,
            R.color.system_error_400,
            R.color.system_error_500,
            R.color.system_error_600,
            R.color.system_error_700,
            R.color.system_error_800,
            R.color.system_error_900,
            R.color.system_error_1000
        )
    }

    // Helper methods

    private fun isPaletteApplied(failFast: Boolean = false): Pair<Boolean, String> {
        var mismatches = checkExpectedPalette(mContext)
        var isPaletteApplied = mismatches.isEmpty()

        if (!failFast && !isPaletteApplied) {
            for (i in 0..20) {
                Thread.sleep(300L)

                mismatches = checkExpectedPalette(mContext)
                isPaletteApplied = mismatches.isEmpty()

                if (isPaletteApplied) break
            }
        }

        val message = if (isPaletteApplied) {
            "Palette $mSettingString is correctly set with colors: " +
                    intArrayToHexString(expectedPalette)
        } else {
            """
                    Setting:
                    $mSettingString
                    Mismatches [index](color, expected):
                    ${
                mismatches.map { (i, current, expected) ->
                    val c = if (current != null) {
                        Integer.toHexString(current)
                    } else {
                        "Null"
                    }
                    val e = if (expected != null) {
                        Integer.toHexString(expected)
                    } else {
                        "Null"
                    }

                    return@map "[$i]($c, $e) "
                }.joinToString(" ")
            }
               """.trimIndent()
        }

        return Pair(isPaletteApplied, message)
    }

    private fun checkExpectedPalette(
        context: Context,
    ): MutableList<Triple<Int, Int?, Int?>> {
        val allColors = IntArray(65)
        System.arraycopy(getAllAccent1Colors(context), 0, allColors, 0, 13)
        System.arraycopy(getAllAccent2Colors(context), 0, allColors, 13, 13)
        System.arraycopy(getAllAccent3Colors(context), 0, allColors, 26, 13)
        System.arraycopy(getAllNeutral1Colors(context), 0, allColors, 39, 13)
        System.arraycopy(getAllNeutral2Colors(context), 0, allColors, 52, 13)

        return getArraysMismatch(allColors, expectedPalette)
    }

    /**
     * Convert the Material shade to an array position.
     *
     * @param shade Shade from 0 to 1000.
     * @return index in array
     * @see .getAllAccent1Colors
     * @see .getAllNeutral1Colors
     */
    private fun shadeToArrayIndex(shade: Int): Int {
        return when (shade) {
            0 -> 0
            10 -> 1
            50 -> 2
            else -> {
                shade / 100 + 2
            }
        }
    }

    private fun assertColor(@ColorInt observed: Int, @ColorInt expected: Int) {
        Assert.assertEquals(
            "Color = ${Integer.toHexString(observed)}, " +
                    "${Integer.toHexString(expected)} expected",
            expected,
            observed
        )
    }

    private fun getAllResourceColors(context: Context, vararg resources: Int): IntArray {
        if (resources.size != 13) throw Exception("Color palettes must be 13 in size")
        return resources.map { resId -> context.getColor(resId) }.toIntArray()
    }

    private fun intArrayToHexString(src: IntArray): String {
        return src.joinToString { n -> Integer.toHexString(n) }
    }

    private fun getArraysMismatch(a: IntArray, b: IntArray): MutableList<Triple<Int, Int?, Int?>> {
        val len = a.size.coerceAtLeast(b.size)
        val mismatches: MutableList<Triple<Int, Int?, Int?>> = mutableListOf()

        repeat(len) { i ->
            val valueA = if (a.size >= i + 1) a[i] else null
            val valueB = if (b.size >= i + 1) b[i] else null

            if (valueB != valueA && isColorDifferenceTooLarge(valueA, valueB, 3)) {
                mismatches.add(Triple(i, valueA, valueB))
            }
        }

        return mismatches
    }

    private fun isColorDifferenceTooLarge(color1: Int?, color2: Int?, threshold: Int): Boolean {
        if (color1 == null || color2 == null) return true

        val hct1 = Hct.fromInt(color1)
        val hct2 = Hct.fromInt(color2)

        val diffTone = abs(hct1.tone - hct2.tone)
        val diffChroma = abs(hct1.chroma - hct2.chroma)
        val diffHue = abs(hct1.hue - hct2.hue)

        return diffTone + diffChroma + diffHue > threshold
    }

    // Helper Classes

    private class ContrastTester private constructor(
        var mContext: Context,
        vararg var mForegrounds: Int
    ) {
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
            private vararg val mEntries: Int
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
                            val msg = "Background Color '${res.getResourceName(bgRes)}'" +
                                    "(#${Integer.toHexString(mContext.getColor(bgRes))}) " +
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
                val contrast = Contrast.ratioOfTones(
                    Hct.fromInt(foreground).tone,
                    Hct.fromInt(background).tone
                )
                return contrast > minContrast
            }
        }
    }

    private class BulkContrastTester private constructor(vararg testsArgs: ContrastTester) {
        private val tests = testsArgs
        private val errorMessages: MutableList<String> = mutableListOf()

        val testPassed: Boolean get() = errorMessages.isEmpty()

        val allMessages: String
            get() =
                if (testPassed) "Test OK" else errorMessages.joinToString("\n")

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
