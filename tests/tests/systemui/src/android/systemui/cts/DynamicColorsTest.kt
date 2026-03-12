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

package android.systemui.cts

import android.graphics.Color
import android.platform.test.annotations.DisabledOnRavenwood
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ux.material.libmonet.hct.Hct
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@DisabledOnRavenwood(reason = "Cannot instantiate Parameterized runner")
class DynamicColorsTest(params: PaletteParams) : BasePaletteTest(params) {

    @Test
    fun testDynamicColors() {
        assertPaletteGolden("Dynamic") { DynamicColorsTable(params) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testData(): Collection<PaletteParams> {
            if (!isDynamicColorSupported) {
                return listOf(PaletteParams(listOf(), 0, "Unsupported", 0.0f))
            }

            val dataList = mutableListOf<PaletteParams>()
            val contrastModes = mapOf("low" to 0.0f, "medium" to 0.5f, "high" to 1.0f)

            contrastModes.forEach { (contrastName, contrastValue) ->
                getSeedColors().forEach { colors ->
                    STYLES.forEach { style ->
                        dataList.add(
                            PaletteParams(
                                colors,
                                style,
                                contrastName,
                                contrastValue
                            )
                        )
                    }
                }
            }
            return dataList
        }
    }
}

enum class GroupType {
    DARK,
    LIGHT
}

@Composable
private fun DynamicColorsTable(params: BasePaletteTest.PaletteParams) {
    val seedColors = params.colors.map { ComposeColor(Color.parseColor("#$it")) }
    val titleTextColor =
        if (Hct.fromInt(
                seedColors.first().toArgb()
            ).tone > 50
        ) {
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

    PaletteTemplate(
        testName = "DynamicColorsTest",
        params = params,
        backgroundColor = seedColors.first(),
        textColor = titleTextColor
    ) {
        Row {
            GroupType.entries.forEach { groupType ->
                val groupBackgroundColor =
                    when (groupType) {
                        GroupType.DARK -> ComposeColor.DarkGray
                        GroupType.LIGHT -> ComposeColor.LightGray
                    }
                Column(
                    verticalArrangement = Arrangement.spacedBy(swatchPadding),
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .background(groupBackgroundColor)
                            .padding(20.dp),
                ) {
                    TOKENS_NAMES_2026.forEach { tokenName ->
                        val suffix = "_" + groupType.name.lowercase()
                        val resourceName = "system_$tokenName$suffix"
                        val colorValue = getColorByName(
                            BasePaletteTest.context,
                            resourceName
                        ).toArgb()
                        val colorHct = Hct.fromInt(colorValue)

                        val swatch = SwatchData(
                            heading = tokenName.toCamelCase(),
                            info = "#${String.format("%08x", colorValue).uppercase()} | $colorHct",
                            colorValue = colorValue,
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
