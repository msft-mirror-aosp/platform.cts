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

package android.systemui.cts

import android.app.Instrumentation
import android.app.UiModeManager
import android.app.UiModeManager.MODE_NIGHT_YES
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.theming.ThemeStyle
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.ux.material.libmonet.dynamiccolor.ColorSpec
import com.google.ux.material.libmonet.hct.Hct
import java.io.File
import java.io.Serializable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext
import platform.test.screenshot.ScreenshotAsserterConfig
import platform.test.screenshot.ScreenshotTestRule
import platform.test.screenshot.matchers.PixelPerfectMatcher

abstract class BasePaletteTest(val params: PaletteParams) {
    protected val mInstrumentation: Instrumentation = getInstrumentation()
    protected val mContext: Context = mInstrumentation.targetContext
    protected val outDir: File =
        File(Environment.getExternalStorageDirectory(), "android.systemui.cts")

    private val goldenPathManager by lazy {
        GoldenPathManager(
            appContext = mContext,
            assetsPathRelativeToBuildRoot = "cts/tests/tests/systemui/assets/",
            deviceLocalPath = outDir.path,
            pathConfig =
                PathConfig(
                    PathElementNoContext("platform", true) {
                        if (FeatureUtil.isWatch()) "wear" else "default"
                    }
                ),
        )
    }

    @get:Rule
    val screenshotTestRule by lazy { ScreenshotTestRule(goldenPathManager) }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setupThemeAndAssumptions() {
        assumeTrue(isDynamicColorSupported)
        assumeTrue(isSupportedStyle(params.style))

        if (params != lastAppliedParams) {
            applyTheme(params.colors, params.style, params.contrastValue)
            lastAppliedParams = params
        }
    }

    protected fun assertPaletteGolden(prefix: String, table: @Composable () -> Unit) {
        val goldenName = "${prefix}_$params"
        val bitmap = generateBitmap { table() }
        assertGoldenImage(bitmap, goldenName)
    }

    protected fun generateBitmap(composable: @Composable () -> Unit): Bitmap {
        val future = CompletableFuture<Bitmap>()
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 0.5f, fontScale = 1.0f)
            ) {
                composable()
            }
        }
        composeTestRule.runOnIdle {
            val content = composeTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
            val composeView = content.getChildAt(0) as ComposeView
            try {
                val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                composeView.measure(widthSpec, heightSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

                future.complete(composeView.drawToBitmap())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future.get(5, TimeUnit.SECONDS)
    }

    protected fun assertGoldenImage(bitmap: Bitmap, goldenName: String) {
        screenshotTestRule
            .createScreenshotAsserter(
                ScreenshotAsserterConfig(
                    matcher = PixelPerfectMatcher(),
                    captureStrategy = { bitmap },
                )
            )
            .assertGoldenImage(goldenName)
    }

    data class PaletteParams(
        val colors: List<String>,
        val style: Int,
        val contrastName: String,
        val contrastValue: Float,
    ) : Serializable {
        override fun toString(): String =
            "${contrastName}_${colors.joinToString("_")}_${ThemeStyle.name(style)}"
    }

    companion object {
        private var lastAppliedParams: PaletteParams? = null

        val COLORS = listOf("FFB9577A", "FFB16407", "FF6E7F10", "FF008673", "FF007FB4", "FF8267C2")

        val STYLES =
            listOf(
                ThemeStyle.TONAL_SPOT,
                ThemeStyle.SPRITZ,
                ThemeStyle.VIBRANT,
                ThemeStyle.EXPRESSIVE,
                ThemeStyle.RAINBOW,
                ThemeStyle.FRUIT_SALAD,
                ThemeStyle.MONOCHROMATIC,
                ThemeStyle.CMF,
            )

        val context: Context = getInstrumentation().targetContext
        val uiModeManager = context.getSystemService(UiModeManager::class.java)

        val isDynamicColorSupported: Boolean
            get() {
                if (FeatureUtil.isWatch()) {
                    return android.server.Flags.enableThemeService() &&
                            android.server.Flags.enableWearThemeService()
                }
                return !(FeatureUtil.isAutomotive() || FeatureUtil.isTV())
            }

        val spec = ColorSpec.SpecVersion.SPEC_2026

        val specYear = "2026"

        fun isSupportedStyle(@ThemeStyle.Type style: Int): Boolean {
            val watchRestrictions = FeatureUtil.isWatch() &&
                    (style == ThemeStyle.MONOCHROMATIC || style == ThemeStyle.FRUIT_SALAD)

            val cmfRestrictions = !android.server.Flags.enableThemeService() &&
                    style == ThemeStyle.CMF

            return !watchRestrictions && !cmfRestrictions
        }

        private const val POLLING_TIMEOUT_MS = 3000L
        private const val WEAR_SETTLING_DELAY_MS = 1000L

        fun getTheme(context: Context): String {
            return runWithShellPermissionIdentity<String> {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                )
            } ?: ""
        }

        private fun waitForConfigurationChange(action: () -> Unit) {
            val deferred = CompletableDeferred<Unit>()
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                            deferred.complete(Unit)
                        }
                    }
                }
            context.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
                Context.RECEIVER_EXPORTED
            )

            try {
                action()
                runBlocking {
                    withTimeoutOrNull(POLLING_TIMEOUT_MS) { deferred.await() }
                        ?: throw IllegalStateException("Configuration change timed out")
                }
            } finally {
                context.unregisterReceiver(receiver)
            }
        }

        @JvmStatic
        protected fun applyTheme(colors: List<String>, style: Int, contrast: Float) {
            if (!isDynamicColorSupported) return

            val isModeDifferent = uiModeManager.nightMode != MODE_NIGHT_YES
            val isContrastDifferent = abs(getContrast() - contrast) > 0.001f
            val jsonString =
                """
                {
                    "android.theme.customization.system_palette":"${colors.get(0)}",
                    "android.theme.customization.accent_color":"${colors.get(1)}",
                    "android.theme.customization.color_source":"preset",
                    "android.theme.customization.theme_style":"${ThemeStyle.name(style)}"
                }
                """
                    .trimIndent()
            val isThemeDifferent = getTheme(context) != jsonString

            if (isModeDifferent || isThemeDifferent) {
                waitForConfigurationChange {
                    if (isModeDifferent) {
                        runWithShellPermissionIdentity { uiModeManager.nightMode = MODE_NIGHT_YES }
                    }
                    if (isContrastDifferent) {
                        setContrast(contrast)
                    }
                    if (isThemeDifferent) {
                        runWithShellPermissionIdentity {
                            Settings.Secure.putString(
                                context.contentResolver,
                                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                                jsonString,
                            )
                        }
                    }
                }
            } else if (isContrastDifferent) {
                setContrast(contrast)
                SystemClock.sleep(1000)
            }

            // On underpowered devices like Wear, rapid system-wide configuration changes
            // can trigger binder throttling or freezing. Allow time to settle if we
            // actually changed something.
            if ((isModeDifferent || isThemeDifferent || isContrastDifferent) &&
                FeatureUtil.isWatch()) {
                SystemClock.sleep(WEAR_SETTLING_DELAY_MS)
            }
        }

        private fun getContrast(): Float {
            return try {
                SystemUtil.runShellCommand("settings get secure contrast_level")
                    .trim()
                    .toFloatOrNull() ?: 0.0f
            } catch (e: Exception) {
                0.0f
            }
        }

        private fun setContrast(contrast: Float) {
            runWithShellPermissionIdentity {
                Settings.Secure.putFloat(context.contentResolver, "contrast_level", contrast)
            }
        }

        /**
         * Generates pairs of seed colors for theme testing.
         *
         * The second color (accent) is chosen using a `+3` offset from the primary color
         * within the [COLORS] array. This specific offset ensures the secondary color is
         * on the opposite side of the color wheel from the primary color in our 6-color
         * array, providing a robust test of the dynamic color engine's ability to handle
         * distinct, contrasting hue inputs.
         */
        fun getSeedColors(): List<List<String>> {
            return COLORS.mapIndexed { index, color ->
                val secondColor = COLORS[(index + 3) % COLORS.size]
                listOf(color, secondColor)
            }
        }
    }
}

fun getColorByName(context: Context, colorName: String): Color {
    val colorId = context.resources.getIdentifier(colorName, "color", "android")
    return Color.valueOf(context.getColor(colorId))
}

fun String.toCamelCase(): String =
    split('_').let { parts ->
        parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::titlecase) }
    }

// --- Shared Compose Components ---

@Composable
fun PaletteTemplate(
    testName: String,
    params: BasePaletteTest.PaletteParams,
    backgroundColor: ComposeColor,
    textColor: ComposeColor,
    content: @Composable () -> Unit
) {
    val titleTextStyle =
        TextStyle(
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

    val infoTextStyle = TextStyle(
        fontSize = 20.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
    )

    val columnGap = 40.dp
    val swatchPadding = 15.dp
    val pagePadding = 60.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(columnGap),
        modifier = Modifier.background(
            backgroundColor
        ).padding(pagePadding).width(IntrinsicSize.Max),
    ) {
        AliasedText(
            text = testName,
            color = textColor,
            style = titleTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        content()

        PaletteFooter(params, textColor, infoTextStyle, swatchPadding)
    }
}

@Composable
fun AliasedText(
    text: String,
    color: ComposeColor,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textSizePx = remember(density, style.fontSize) { with(density) { style.fontSize.toPx() } }

    // Create a native typeface from the Compose style
    val nativeTypeface = remember(style.fontWeight) {
        val nativeWeight =
            if (style.fontWeight == FontWeight.Bold) Typeface.BOLD else Typeface.NORMAL
        Typeface.create(Typeface.MONOSPACE, nativeWeight)
    }

    // Updates paint  only when inputs change
    val nativePaint = remember(color, textSizePx, nativeTypeface, style.textAlign) {
        Paint().apply {
            this.isAntiAlias = false
            this.color = color.toArgb()
            this.textSize = textSizePx
            this.typeface = nativeTypeface
            this.textAlign = when (style.textAlign) {
                TextAlign.Center -> Paint.Align.CENTER
                TextAlign.End -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
        }
    }

    val measurePolicy = remember(nativePaint, text) {
        object : MeasurePolicy {
            // Pre-calculate text dimensions using the native paint
            private val textWidth: Float by lazy { nativePaint.measureText(text) }
            private val fontMetrics: Paint.FontMetrics by lazy { nativePaint.fontMetrics }
            private val textHeight: Float by lazy { fontMetrics.descent - fontMetrics.ascent }

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int {
                return textWidth.roundToInt()
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ): Int = maxIntrinsicWidth(measurables, height)

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int {
                return textHeight.roundToInt()
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ): Int = maxIntrinsicHeight(measurables, width)

            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult {
                val width = textWidth.roundToInt().coerceIn(
                    constraints.minWidth,
                    constraints.maxWidth
                )
                val height = textHeight.roundToInt().coerceIn(
                    constraints.minHeight,
                    constraints.maxHeight
                )

                return layout(width, height) {}
            }
        }
    }

    // Create the Layout composable. Just measures and draws itself
    Layout(
        content = {},
        measurePolicy = measurePolicy,
        modifier = modifier.drawBehind {
            val xPos = when (nativePaint.textAlign) {
                Paint.Align.CENTER -> size.width / 2f
                Paint.Align.RIGHT -> size.width
                else -> 0f
            }

            val yPos = -nativePaint.fontMetrics.ascent

            drawContext.canvas.nativeCanvas.drawText(
                text,
                xPos,
                yPos,
                nativePaint
            )
        }
    )
}

data class SwatchData(
    val heading: String,
    val info: String,
    val colorValue: Int,
) {
    val isTextDark: Boolean = Hct.fromInt(colorValue).tone > 50
}

@Composable
fun SwatchItem(
    swatch: SwatchData,
    headingTextStyle: TextStyle,
    infoTextStyle: TextStyle,
    padding: Dp,
    modifier: Modifier = Modifier
) {
    val textColor = if (swatch.isTextDark) ComposeColor.Black else ComposeColor.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(padding, Alignment.CenterVertically),
        modifier =
            modifier.background(ComposeColor(swatch.colorValue)).padding(padding),
    ) {
        AliasedText(swatch.heading, style = headingTextStyle, color = textColor)
        AliasedText(swatch.info, style = infoTextStyle, color = textColor)
    }
}

@Composable
fun PaletteFooter(
    params: BasePaletteTest.PaletteParams,
    textColor: ComposeColor,
    textStyle: TextStyle,
    swatchPadding: Dp
) {
    val headingTextStyle =
        TextStyle(
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AliasedText(
            text = "| spec: ${BasePaletteTest.specYear} | " +
                    "style: ${ThemeStyle.name(params.style)} | " +
                    "contrast: ${params.contrastName} |",
            color = textColor,
            style = textStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                swatchPadding,
                Alignment.CenterHorizontally
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            params.colors.forEachIndexed { index, colorStr ->
                val colorInt = Color.parseColor("#$colorStr")
                val colorHct = Hct.fromInt(colorInt)
                val swatch = SwatchData(
                    heading = "Seed ${index + 1}",
                    info = "#${colorStr.uppercase()} | $colorHct",
                    colorValue = colorInt
                )
                SwatchItem(swatch, headingTextStyle, textStyle, swatchPadding)
            }
        }
    }
}

// --- Shared Material Tokens ---

val TOKENS_NAMES_2026 =
    listOf(
        "background",
        "control_activated",
        "control_highlight",
        "control_normal",
        "error_container",
        "error_dim",
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
        "palette_key_color_error",
        "palette_key_color_neutral_variant",
        "palette_key_color_neutral",
        "palette_key_color_primary",
        "palette_key_color_secondary",
        "palette_key_color_tertiary",
        "primary_container",
        "primary_dim",
        "primary_fixed_dim",
        "primary_fixed",
        "primary",
        "scrim",
        "secondary_container",
        "secondary_dim",
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
        "tertiary_dim",
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
