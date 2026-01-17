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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.core.view.drawToBitmap
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext
import platform.test.screenshot.ScreenshotAsserterConfig
import platform.test.screenshot.ScreenshotTestRule
import platform.test.screenshot.matchers.PixelPerfectMatcher

open class BasePaletteTest {
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
                    PathElementNoContext("spec", true) {
                        if (isOldSpec) "libmonet_2021" else "libmonet_2025"
                    }
                ),
        )
    }

    @get:Rule val screenshotTestRule by lazy { ScreenshotTestRule(goldenPathManager) }

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

    companion object {
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
            )

        val context: Context = getInstrumentation().targetContext
        val uiModeManager = context.getSystemService(UiModeManager::class.java)

        val isDynamicColorSupported: Boolean
            get() {
                return !(FeatureUtil.isAutomotive() ||
                        FeatureUtil.isTV() ||
                        FeatureUtil.isWatch())
            }

        fun isSupportedStyle(@ThemeStyle.Type style: Int): Boolean {
            return !(FeatureUtil.isWatch() &&
                    (style == ThemeStyle.MONOCHROMATIC || style == ThemeStyle.FRUIT_SALAD))
        }

        val isOldSpec =
            context.resources.getIdentifier("system_primary_dim_light", "color", "android") == 0

        private const val POLLING_TIMEOUT_MS = 10000L
        private const val WEAR_SETTLING_DELAY_MS = 1000L

        fun getTheme(context: Context): String {
            return runWithShellPermissionIdentity<String> {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                )
            } ?: ""
        }

        // returns true if the theme was actually changed, false if it was already identical.
        private fun setTheme(jsonString: String): Boolean {
            val currentJson = getTheme(context)
            if (currentJson == jsonString) {
                return false
            }

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
                runWithShellPermissionIdentity {
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                        jsonString,
                    )
                }

                runBlocking {
                    withTimeoutOrNull(POLLING_TIMEOUT_MS) { deferred.await() }
                        ?: throw IllegalStateException("Theme change timed out")
                }
            } finally {
                context.unregisterReceiver(receiver)
            }
            return true
        }

        @JvmStatic
        protected fun applyTheme(color: String, style: Int, contrast: Float, mode: Int) {
            if (!isDynamicColorSupported) return

            var configChanged = false
            val isModeDifferent = uiModeManager.nightMode != mode
            if (isModeDifferent) {
                runWithShellPermissionIdentity { uiModeManager.nightMode = mode }
                assumeTrue("Failed to set night mode", uiModeManager.nightMode == mode)
                configChanged = true
            }

            if (abs(getContrast() - contrast) > 0.001f) {
                setContrast(contrast)
                configChanged = true
            }

            val jsonString =
                """
                {
                    "android.theme.customization.system_palette":"$color",
                    "android.theme.customization.accent_color":"$color",
                    "android.theme.customization.color_source":"preset",
                    "android.theme.customization.theme_style":"${ThemeStyle.name(style)}"
                }
                """
                    .trimIndent()

            if (setTheme(jsonString)) {
                configChanged = true
            }

            // On underpowered devices like Wear, rapid system-wide configuration changes
            // can trigger binder throttling or freezing. Allow time to settle if we
            // actually changed something.
            if (configChanged && FeatureUtil.isWatch()) {
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
