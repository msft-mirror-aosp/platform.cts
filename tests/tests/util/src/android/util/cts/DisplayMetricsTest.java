/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.util.cts;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.DisplayMetrics;
import android.view.Display;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayMetricsTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private final Context mApplication = ApplicationProvider.getApplicationContext();

    private Display initDisplay() {
        final DisplayManager displayManager = mApplication.getSystemService(DisplayManager.class);
        assertNotNull(displayManager);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        assertNotNull(display);
        return display;
    }

    @ApiTest(apis = "android.util.DisplayMetrics#setToDefaults")
    @Test
    public void testDisplayMetricsOp_defaultValues() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        outMetrics.setToDefaults();
        assertEquals(0, outMetrics.widthPixels);
        assertEquals(0, outMetrics.heightPixels);
        // according to Android emulator doc UI -scale confine density should between 0.1 to 4
        assertTrue((0.1 <= outMetrics.density) && (outMetrics.density <= 4));
        assertTrue((0.1 <= outMetrics.scaledDensity) && (outMetrics.scaledDensity <= 4));
        assertTrue(0 < outMetrics.xdpi);
        assertTrue(0 < outMetrics.ydpi);
    }

    @ApiTest(apis = "android.view.Display#getMetrics")
    @Test
    @DisabledOnRavenwood(blockedBy = {Display.class})
    public void testDisplayMetricsOp_defaultDisplay() {
        DisplayMetrics outMetrics = new DisplayMetrics();

        Display display = initDisplay();
        display.getMetrics(outMetrics);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(outMetrics);
        assertEquals(display.getHeight(), metrics.heightPixels);
        assertEquals(display.getWidth(), metrics.widthPixels);
        // according to Android emulator doc UI -scale confine density should between 0.1 to 4
        assertTrue((0.1 <= metrics.density) && (metrics.density <= 4));
        assertTrue((0.1 <= metrics.scaledDensity) && (metrics.scaledDensity <= 4));
        assertTrue(0 < metrics.xdpi);
        assertTrue(0 < metrics.ydpi);
    }

    @ApiTest(
            apis = {
                "android.view.Display#getMetrics",
                "android.content.res.Resources#getDisplayMetrics"
            })
    @Test
    @DisabledOnRavenwood(blockedBy = {Display.class})
    public void testDisplayMetricsFromResourcesAndDisplayMatch() {
        final Display display = initDisplay();
        final Context windowContext =
                mApplication.createWindowContext(display, TYPE_APPLICATION, null /* options */);
        final DisplayMetrics metricsFromResources =
                windowContext.getResources().getDisplayMetrics();
        final DisplayMetrics metricsFromDisplay = new DisplayMetrics();
        windowContext.getDisplay().getMetrics(metricsFromDisplay);

        assertEquals("widthPixels from resource must match display widthPixels",
                metricsFromResources.widthPixels, metricsFromDisplay.widthPixels);
        assertEquals("heightPixels from resource must match display heightPixels",
                metricsFromResources.heightPixels, metricsFromDisplay.heightPixels);
        assertEquals("xdpi from resource height must match display xdpi",
                metricsFromResources.xdpi, metricsFromDisplay.xdpi, 0.01);
        assertEquals("ydpi from resource height must match display ydpi",
                metricsFromResources.ydpi, metricsFromDisplay.ydpi, 0.01);
        assertEquals("Density from resource must match display density",
                metricsFromResources.density, metricsFromDisplay.density, 0.01f);
        assertEquals("ScaledDensity from resource must match display scaledDensity",
                metricsFromResources.scaledDensity, metricsFromDisplay.scaledDensity, 0.01f);
    }
}
