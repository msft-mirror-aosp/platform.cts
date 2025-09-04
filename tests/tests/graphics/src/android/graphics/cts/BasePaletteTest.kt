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

package android.graphics.cts

import android.app.Instrumentation
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.theming.ThemeStyle
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.drawToBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
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
    protected val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val mContext: Context = mInstrumentation.targetContext
    protected val outDir: File =
        File(Environment.getExternalStorageDirectory(), "android.graphics.cts")

    private val goldenPathManager by lazy {
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
                    matcher = if (isOldSpec) PixelPerfectMatcher() else PixelPerfectMatcher(),
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

        val isSupportedDevice =
            !(FeatureUtil.isTV() || FeatureUtil.isWatch() || FeatureUtil.isAutomotive())
        val isOldSpec =
            context.resources.getIdentifier("system_primary_dim_light", "color", "android") == 0

        private const val POLLING_TIMEOUT_MS = 5000L

        fun getTheme(context: Context): String {
            return Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            ) ?: ""
        }

        private fun setTheme(jsonString: String) {
            val currentJson = getTheme(context)
            if (currentJson == jsonString) {
                return
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
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))

            runWithShellPermissionIdentity {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    jsonString,
                )
            }

            try {
                runBlocking {
                    withTimeoutOrNull(POLLING_TIMEOUT_MS) { deferred.await() }
                        ?: throw IllegalStateException("Theme change timed out")
                }
            } finally {
                context.unregisterReceiver(receiver)
            }
        }

        fun setContrast(contrast: Float) {
            runWithShellPermissionIdentity {
                Settings.Secure.putFloat(context.contentResolver, "contrast_level", contrast)
            }
        }

        @JvmStatic
        protected fun applyTheme(color: String, style: String, contrast: Float, mode: Int) {
            val isModeDifferent = uiModeManager.nightMode != mode
            if (isModeDifferent) {
                runWithShellPermissionIdentity { uiModeManager.nightMode = mode }
                assumeTrue(uiModeManager.nightMode == mode)
            }

            setContrast(contrast)

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
            setTheme(jsonString)
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
