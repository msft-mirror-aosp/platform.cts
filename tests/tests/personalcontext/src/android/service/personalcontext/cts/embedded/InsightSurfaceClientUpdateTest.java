/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.personalcontext.cts.embedded;

import static android.view.View.SCROLL_AXIS_HORIZONTAL;
import static android.view.View.SCROLL_AXIS_NONE;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Configuration;
import android.graphics.Color;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.embedded.InsightSurfaceClientUpdate;
import android.service.personalcontext.hint.BundleHint;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:InsightSurfaceUpdateTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientUpdateTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setMeasureSpecWidth",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setMeasureSpecHeight",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setBackgroundColor",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setNestedScrollAxes",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setNestedScrollAxisLocked",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setShouldBlur",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#setThemeResourceName",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#addHint",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#build",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getMeasureSpecWidth",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getMeasureSpecHeight",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getBackgroundColor",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getNestedScrollAxes",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#isNestedScrollAxisLocked",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getThemeResourceName",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#shouldBlur",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#getHints",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#hasUpdate",
            })
    @Test
    public void testCreateUpdate() {
        final int measureSpecWidth = 100;
        final int measureSpecHeight = 200;
        final Color backgroundColor = Color.valueOf(Color.BLUE);
        final int nestedScrollAxes = SCROLL_AXIS_HORIZONTAL;
        final boolean nestedScrollAxisLocked = true;
        final boolean shouldBlur = true;
        final String themeResourceName = "theme";
        final Configuration configuration = new Configuration();
        final BundleHint hint = new BundleHint.Builder().build();

        final InsightSurfaceClientUpdate update =
                new InsightSurfaceClientUpdate.Builder()
                        .setMeasureSpecWidth(measureSpecWidth)
                        .setMeasureSpecHeight(measureSpecHeight)
                        .setBackgroundColor(backgroundColor)
                        .setNestedScrollAxes(nestedScrollAxes)
                        .setNestedScrollAxisLocked(nestedScrollAxisLocked)
                        .setShouldBlur(shouldBlur)
                        .setThemeResourceName(themeResourceName)
                        .setConfiguration(configuration)
                        .addHint(hint)
                        .build();

        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED))
                .isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_CONFIGURATION)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_HINTS)).isTrue();

        assertThat(update.getMeasureSpecWidth()).isEqualTo(measureSpecWidth);
        assertThat(update.getMeasureSpecHeight()).isEqualTo(measureSpecHeight);
        assertThat(update.getNestedScrollAxes()).isEqualTo(nestedScrollAxes);
        assertThat(update.isNestedScrollAxisLocked()).isEqualTo(nestedScrollAxisLocked);
        assertThat(update.shouldBlur()).isEqualTo(shouldBlur);
        assertThat(update.getThemeResourceName()).isEqualTo(themeResourceName);
        assertThat(update.getBackgroundColor()).isEqualTo(backgroundColor);
        assertThat(update.getHints()).contains(hint);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate.Builder"
                        + "#build",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getMeasureSpecWidth",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getMeasureSpecHeight",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getBackgroundColor",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getNestedScrollAxes",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#isNestedScrollAxisLocked",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#shouldBlur",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate"
                        + "#getThemeResourceName",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#getHints",
                "android.service.personalcontext.embedded.InsightSurfaceClientUpdate#hasUpdate",
            })
    @Test
    public void testUpdateDefaults() {
        final InsightSurfaceClientUpdate update = new InsightSurfaceClientUpdate.Builder().build();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED))
                .isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_SHOULD_BLUR)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_CONFIGURATION)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_HINTS)).isFalse();

        assertThat(update.getMeasureSpecWidth()).isEqualTo(View.MeasureSpec.UNSPECIFIED);
        assertThat(update.getMeasureSpecHeight()).isEqualTo(View.MeasureSpec.UNSPECIFIED);
        assertThat(update.getNestedScrollAxes()).isEqualTo(SCROLL_AXIS_NONE);
        assertThat(update.isNestedScrollAxisLocked()).isEqualTo(false);
        assertThat(update.shouldBlur()).isEqualTo(false);
        assertThat(update.getThemeResourceName()).isNull();
        assertThat(update.getBackgroundColor()).isNull();
        assertThat(update.getHints()).isEmpty();
    }
}
