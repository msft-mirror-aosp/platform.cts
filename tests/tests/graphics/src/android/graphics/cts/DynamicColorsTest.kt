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

package android.graphics.cts

import android.R
import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.cts.utils.Material2021SpecMatcher
import android.os.Environment
import android.os.UserManager
import android.platform.test.annotations.DisabledOnRavenwood
import android.provider.Settings
import android.testing.PollingCheck
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.NonNull
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.systemui.monet.Style
import com.google.ux.material.libmonet.hct.Hct
import java.io.File
import java.io.Serializable
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
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
class DynamicColorsTest(
    private val color: String,
    private val style: String,
    private val contrast: String,
) {
    val isSupportedDevice =!(
        FeatureUtil.isTV() ||
        FeatureUtil.isWatch() ||
        FeatureUtil.isAutomotive() ||
        /* TODO:b/362682063 - Remove this once the bug is fixed */
        UserManager.isHeadlessSystemUserMode())

    private val mContext: Context = getInstrumentation().targetContext
    private val isOldSpec: Boolean =
        mContext.resources.getIdentifier("system_primary_dim_light", "color", "android") == 0

    private val allTokenNames = if (isOldSpec) TOKEN_NAMES_2021 else TOKENS_NAMES_2025

    /** The output directory for generated goldens */
    @NonNull
    private val outDir: File =
        File(Environment.getExternalStorageDirectory(), "android.graphics.cts")

    private val goldenPathManager =
        GoldenPathManager(
            appContext = mContext,
            assetsPathRelativeToBuildRoot = "cts/tests/tests/graphics/assets/",
            deviceLocalPath = outDir.path,
            pathConfig =
                PathConfig(
                    PathElementNoContext("spec", true) {
                        if (isOldSpec) "libmonet_2021" else "libmonet_2025"
                    }
                ),
        )

    private val screenshotTestRule = ScreenshotTestRule(goldenPathManager)
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(composeTestRule).around(screenshotTestRule)

    companion object {
        private var initialContrast: Float = 0f

        @JvmStatic
        @Parameterized.Parameters(name = "{2}_{0}_{1}")
        fun testData(): List<Array<Serializable>> {
            val dataList: MutableList<Array<Serializable>> = mutableListOf()
            val styles =
                intArrayOf(
                    Style.SPRITZ,
                    Style.TONAL_SPOT,
                    Style.VIBRANT,
                    Style.EXPRESSIVE,
                    Style.RAINBOW,
                    Style.FRUIT_SALAD,
                    Style.MONOCHROMATIC,
                )
            val colors =
                listOf("FFB9577A", "FFB16407", "FF6E7F10", "FF008673", "FF007FB4", "FF8267C2")
            val contrastModes = listOf("low", "medium", "high")

            contrastModes.forEach { mode ->
                colors.forEach { color ->
                    styles.forEach { style ->
                        dataList.add(arrayOf(color, Style.name(style), mode))
                    }
                }
            }
            return dataList
        }

        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        @Parameterized.BeforeParam
        fun setup(color: String, style: String, contrast: String) {
            val context = getInstrumentation().targetContext
            val contrastValue =
                when (contrast) {
                    "low" -> 0.0f
                    "medium" -> 0.5f
                    else -> 1.0f
                }

            if (abs(getCurrentContrastLevel() - contrastValue) > 0.01f) {
                runShellCommand("settings put secure contrast_level $contrastValue")
                PollingCheck.waitFor(3000L) {
                    abs(getCurrentContrastLevel() - contrastValue) < 0.01f
                }
            }

            val jsonString =
                """
                {
                    "android.theme.customization.system_palette":"$color",
                    "android.theme.customization.accent_color":"$color",
                    "android.theme.customization.color_source":"preset",
                    "android.theme.customization.theme_style":"$style"
                }
                """
                    .trimIndent()

            val currentJson =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                )

            if (currentJson != jsonString) {
                val keyColorBefore =
                    context.getColor(R.color.system_palette_key_color_primary_dark).toHexString()
                runWithShellPermissionIdentity {
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                        jsonString,
                    )
                }
                PollingCheck.waitFor(3000L) {
                    val newColor =
                        context
                            .getColor(R.color.system_palette_key_color_primary_dark)
                            .toHexString()
                    keyColorBefore != newColor
                }
            }
        }

        @JvmStatic
        @BeforeClass
        fun before() {
            initialContrast = getCurrentContrastLevel()
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            runShellCommand("settings put secure contrast_level $initialContrast")
        }

        private fun getCurrentContrastLevel(): Float =
            getInstrumentation().targetContext.getSystemService(UiModeManager::class.java).contrast
    }

    @Test
    fun testDynamicColors() {
        assumeTrue(isSupportedDevice)

        val goldenName = "Dynamic_${contrast}_${color}_$style".replace("#", "")
        screenshotTestRule
            .createScreenshotAsserter(
                ScreenshotAsserterConfig(
                    matcher = if (isOldSpec) Material2021SpecMatcher() else PixelPerfectMatcher(),
                    captureStrategy = ::generateTokenBitmap,
                )
            )
            .assertGoldenImage(goldenName)
    }

    private fun generateTokenBitmap(): Bitmap {
        val allSwatches = arrayListOf<SwatchData>()

        allTokenNames.forEach { tokenName ->
            val typeGroup =
                if (tokenName.contains("_fixed")) {
                    listOf(GroupType.FIXED)
                } else {
                    listOf(GroupType.LIGHT, GroupType.DARK)
                }

            typeGroup.forEach { type ->
                val suffix = if (type != GroupType.FIXED) "_" + type.name.lowercase() else ""
                val resourceName = "system_$tokenName$suffix"
                val colorValue = getColorByName(mContext, resourceName).toArgb()
                val colorHct = Hct.fromInt(colorValue)
                allSwatches.add(
                    SwatchData(
                        type,
                        tokenName.toCamelCase(),
                        "${
                            String.format("#%08x", colorValue).uppercase()
                        } | $colorHct",
                        colorValue,
                        colorHct.tone > 50,
                    )
                )
            }
        }

        val deferredBitmap = CompletableDeferred<Bitmap>()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 0.5f, fontScale = 1.0f)
            ) {
                DynamicColorsTable(
                    title =
                        "${DynamicColorsTest::class.simpleName} - " +
                            "spec: ${if (isOldSpec) "2021" else "2025"} | " +
                            "seed: $color | " +
                            "style: $style | " +
                            "contrast: $contrast",
                    color = color,
                    allSwatches = allSwatches,
                )
            }
        }

        composeTestRule.runOnIdle {
            try {
                val activity = composeTestRule.activity
                val contentView = activity.findViewById<ViewGroup>(R.id.content)
                val composeView =
                    findComposeView(contentView)
                        ?: throw IllegalStateException("ComposeView not found")

                val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                composeView.measure(widthSpec, heightSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                deferredBitmap.complete(composeView.drawToBitmap())
            } catch (e: Exception) {
                deferredBitmap.completeExceptionally(e)
            }
        }

        return runBlocking {
            withTimeoutOrNull(5000L) { deferredBitmap.await() }
                ?: throw IllegalStateException("Bitmap capture timed out with CompletableDeferred")
        }
    }

    private fun findComposeView(viewGroup: ViewGroup): ComposeView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ComposeView) {
                return child
            } else if (child is ViewGroup) {
                val result = findComposeView(child)
                if (result != null) return result
            }
        }
        return null
    }

    data class SwatchData(
        val type: GroupType,
        val heading: String,
        val info: String,
        val colorValue: Int,
        val isTextDark: Boolean,
    )

    enum class GroupType {
        DARK,
        LIGHT,
        FIXED,
    }

    private fun String.toCamelCase(): String =
        split('_').let { parts ->
            parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::titlecase) }
        }

    private fun getColorByName(context: Context, colorName: String): Color {
        val colorId = context.resources.getIdentifier(colorName, "color", "android")
        return Color.valueOf(context.getColor(colorId))
    }
}

@Composable
private fun DynamicColorsTable(
    title: String,
    color: String,
    allSwatches: List<DynamicColorsTest.SwatchData>,
) {
    val groupedSwatches = allSwatches.groupBy { it.type }
    val seedColor = ComposeColor(Color.parseColor("#$color"))
    val titleTextColor =
        if (Hct.fromInt(seedColor.toArgb()).tone > 50) ComposeColor.Black else ComposeColor.White

    val titleTextStyle =
        TextStyle(
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    val headingTextStyle =
        TextStyle(
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    val infoTextStyle =
        TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)

    val columnGap = 40.dp
    val swatchPadding = 15.dp
    val pagePadding = 60.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(columnGap),
        modifier = Modifier.background(seedColor).padding(pagePadding).width(IntrinsicSize.Max),
    ) {
        Text(
            text = title,
            color = titleTextColor,
            style = titleTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.height(IntrinsicSize.Max)) {
            DynamicColorsTest.GroupType.entries.forEach { groupType ->
                val groupBackgroundColor =
                    when (groupType) {
                        DynamicColorsTest.GroupType.DARK -> ComposeColor.DarkGray
                        DynamicColorsTest.GroupType.LIGHT -> ComposeColor.LightGray
                        DynamicColorsTest.GroupType.FIXED -> ComposeColor.Gray
                    }
                Column(
                    verticalArrangement = Arrangement.spacedBy(swatchPadding),
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .background(groupBackgroundColor)
                            .padding(columnGap / 2),
                ) {
                    val swatches = groupedSwatches[groupType] ?: emptyList()
                    swatches.forEach { swatch ->
                        SwatchItem(swatch, headingTextStyle, infoTextStyle, swatchPadding)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchItem(
    swatch: DynamicColorsTest.SwatchData,
    headingTextStyle: TextStyle,
    infoTextStyle: TextStyle,
    padding: Dp,
) {
    val textColor = if (swatch.isTextDark) ComposeColor.Black else ComposeColor.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(padding, Alignment.CenterVertically),
        modifier =
            Modifier.fillMaxWidth().background(ComposeColor(swatch.colorValue)).padding(padding),
    ) {
        Text(swatch.heading, style = headingTextStyle, color = textColor)
        Text(swatch.info, style = infoTextStyle, color = textColor)
    }
}

// Pre 2025 tokens
private val TOKEN_NAMES_2021 =
    listOf(
            "background",
            "control_activated",
            "control_highlight",
            "control_normal",
            "error_container",
            "error",
            "inverse_on_surface",
            "inverse_primary",
            "inverse_surface",
            "on_background",
            "on_error_container",
            "on_error",
            "on_primary_container",
            "on_primary_fixed_variant",
            "on_primary_fixed",
            "on_primary",
            "on_secondary_container",
            "on_secondary_fixed_variant",
            "on_secondary_fixed",
            "on_secondary",
            "on_surface_variant",
            "on_surface",
            "on_tertiary_container",
            "on_tertiary_fixed_variant",
            "on_tertiary_fixed",
            "on_tertiary",
            "outline_variant",
            "outline",
            "palette_key_color_neutral_variant",
            "palette_key_color_neutral",
            "palette_key_color_primary",
            "palette_key_color_secondary",
            "palette_key_color_tertiary",
            "primary_container",
            "primary_fixed_dim",
            "primary_fixed",
            "primary",
            "scrim",
            "secondary_container",
            "secondary_fixed_dim",
            "secondary_fixed",
            "secondary",
            "shadow",
            "surface_bright",
            "surface_container_high",
            "surface_container_highest",
            "surface_container_low",
            "surface_container_lowest",
            "surface_container",
            "surface_dim",
            "surface_tint",
            "surface_variant",
            "surface",
            "tertiary_container",
            "tertiary_fixed_dim",
            "tertiary_fixed",
            "tertiary",
            "text_hint_inverse",
            "text_primary_inverse_disable_only",
            "text_primary_inverse",
            "text_secondary_and_tertiary_inverse_disabled",
            "text_secondary_and_tertiary_inverse",
        )
        .sorted()

// tokens added in 2025
private val TOKENS_NAMES_2025 =
    TOKEN_NAMES_2021 +
        listOf(
                "error_dim",
                "palette_key_color_error",
                "primary_dim",
                "secondary_dim",
                "tertiary_dim",
            )
            .sorted()
