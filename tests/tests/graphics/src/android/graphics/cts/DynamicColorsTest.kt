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

import android.app.UiModeManager.MODE_NIGHT_YES
import android.content.theming.ThemeStyle
import android.graphics.Color
import android.platform.test.annotations.DisabledOnRavenwood
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ux.material.libmonet.hct.Hct
import java.io.Serializable
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@DisabledOnRavenwood(reason = "Cannot instantiate Parameterized runner")
class DynamicColorsTest(
    private val color: String,
    private val style: String,
    private val contrast: String,
) : BasePaletteTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{2}_{0}_{1}")
        fun testData(): List<Array<Serializable>> {
            val dataList: MutableList<Array<Serializable>> = mutableListOf()
            val contrastModes = listOf("low", "medium", "high")

            contrastModes.forEach { mode ->
                COLORS.forEach { color ->
                    STYLES.forEach { style ->
                        dataList.add(arrayOf(color, ThemeStyle.name(style), mode))
                    }
                }
            }
            return dataList
        }

        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        @Parameterized.BeforeParam
        fun setup(color: String, style: String, contrast: String) {
            assumeTrue(isSupportedDevice)

            val contrastValue =
                when (contrast) {
                    "low" -> 0.0f
                    "medium" -> 0.5f
                    else -> 1.0f
                }
            applyTheme(color, style, contrastValue, MODE_NIGHT_YES)
        }
    }

    @Test
    fun testDynamicColors() {
        val goldenName = "Dynamic_${contrast}_${color}_$style".replace("#", "")
        val bitmap = generateBitmap { DynamicColorsTable(color, style, contrast) }
        assertGoldenImage(bitmap, goldenName)
    }
}

private data class SwatchData(
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

@Composable
private fun DynamicColorsTable(color: String, style: String, contrast: String) {
    val allSwatches = arrayListOf<SwatchData>()
    val allTokenNames = if (BasePaletteTest.isOldSpec) TOKEN_NAMES_2021 else TOKENS_NAMES_2025

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
            val colorValue = getColorByName(BasePaletteTest.context, resourceName).toArgb()
            val colorHct = Hct.fromInt(colorValue)
            allSwatches.add(
                SwatchData(
                    type,
                    tokenName.toCamelCase(),
                    "#${String.format("%08x", colorValue).uppercase()} | $colorHct",
                    colorValue,
                    colorHct.tone > 50,
                )
            )
        }
    }

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
            text =
                "${DynamicColorsTest::class.simpleName} - " +
                    "spec: ${if (BasePaletteTest.isOldSpec) "2021" else "2025"} | " +
                    "seed: $color | " +
                    "style: $style | " +
                    "contrast: $contrast",
            color = titleTextColor,
            style = titleTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.height(IntrinsicSize.Max)) {
            GroupType.entries.forEach { groupType ->
                val groupBackgroundColor =
                    when (groupType) {
                        GroupType.DARK -> ComposeColor.DarkGray
                        GroupType.LIGHT -> ComposeColor.LightGray
                        GroupType.FIXED -> ComposeColor.Gray
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
    swatch: SwatchData,
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
