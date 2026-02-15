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

package android.service.personalcontext.cts.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.embedded.InsightSurfaceClient;
import android.view.Display;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Build/Install/Run: atest CtsPersonalContextTestCases:InsightSurfaceClientTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private Context mContext;
    @Mock private Resources mResources;
    private final Configuration mConfiguration = new Configuration();
    @Mock private InsightSurfaceClient.InsightReceiver mInsightReceiver;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final DisplayManager displayManager =
                instrumentation.getContext().getSystemService(DisplayManager.class);
        final Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        when(mContext.getDisplay()).thenReturn(display);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder"
                        + "#setMeasureSpecs",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder"
                        + "#setBackgroundColor",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder"
                        + "#setNestedScrollAxes",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder"
                        + "#setNestedScrollAxisLocked",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder"
                        + "#setThemeResourceId",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder#addReceiver",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder#build",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getMeasureSpecWidth",
                "android.service.personalcontext.embedded.InsightSurfaceClient"
                        + "#getMeasureSpecHeight",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getBackgroundColor",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getNestedScrollAxes",
                "android.service.personalcontext.embedded.InsightSurfaceClient"
                        + "#isNestedScrollAxisLocked",
                "android.service.personalcontext.embedded.InsightSurfaceClient#shouldBlur",
                "android.service.personalcontext.embedded.InsightSurfaceClient"
                        + "#getThemeResourceId",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getReceivers",
            })
    @Test
    public void testClientBuilder() {
        final int widthMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY);
        final int heightMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY);
        final Color backgroundColor = Color.valueOf(Color.RED);
        final int nestedScrollAxes = View.SCROLL_AXIS_HORIZONTAL | View.SCROLL_AXIS_VERTICAL;
        final boolean isNestedScrollAxisLocked = true;
        final boolean shouldBlur = true;
        final int themeResourceId = 7;
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setMeasureSpecs(widthMeasureSpec, heightMeasureSpec)
                        .setBackgroundColor(backgroundColor)
                        .setNestedScrollAxes(nestedScrollAxes)
                        .setNestedScrollAxisLocked(isNestedScrollAxisLocked)
                        .setShouldBlur(shouldBlur)
                        .setThemeResourceId(themeResourceId)
                        .addReceiver(mInsightReceiver)
                        .build();

        assertThat(client.getMeasureSpecWidth()).isEqualTo(widthMeasureSpec);
        assertThat(client.getMeasureSpecHeight()).isEqualTo(heightMeasureSpec);
        assertThat(client.getBackgroundColor()).isEqualTo(backgroundColor);
        assertThat(client.getNestedScrollAxes()).isEqualTo(nestedScrollAxes);
        assertThat(client.isNestedScrollAxisLocked()).isEqualTo(isNestedScrollAxisLocked);
        assertThat(client.shouldBlur()).isEqualTo(shouldBlur);
        assertThat(client.getThemeResourceId()).isEqualTo(themeResourceId);
        assertThat(client.getReceivers()).contains(mInsightReceiver);
    }
}
