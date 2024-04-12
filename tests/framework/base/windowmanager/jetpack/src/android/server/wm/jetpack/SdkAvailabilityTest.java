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

package android.server.wm.jetpack;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.window.flags.Flags.FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityTaskManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.jetpack.extensions.util.ExtensionsUtil;
import android.server.wm.jetpack.extensions.util.SidecarUtil;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Tests for devices implementations include an Android-compatible display(s)
 * that has a minimum screen dimension greater than or equal to
 * {@link WindowManager#LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP} and support multi window.
 * For more information, read
 * <a href="https://source.android.com/docs/core/display/windowmanager-extensions">WindowManager
 * Extensions</a>
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:SdkAvailabilityTest
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.8.14/C-5-1"})
public class SdkAvailabilityTest extends WindowManagerJetpackTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue("Device's default display doesn't support multi window",
                ActivityTaskManager.supportsMultiWindow(mContext));
    }

    /**
     * MUST implement the latest available stable version of the extensions API
     * to be used by Window Manager Jetpack library, and declares the window extension
     * is enabled.
     */
    @RequiresFlagsDisabled(FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG)
    @ApiTest(apis = {
            "androidx.window.extensions.WindowExtensionsProvider#getWindowExtensions",
            "androidx.window.extensions.WindowExtensions#getVendorApiLevel",
            "android.view.WindowManager#hasWindowExtensionsEnabled"
    })
    @Test
    public void testWindowExtensionsAvailability() {
        assumeHasLargeScreenDisplayOrExtensionEnabled();
        assertTrue("WindowExtension version is not latest",
                ExtensionsUtil.isExtensionVersionLatest());
        assertTrue("Device must declared that the WindowExtension is enabled",
                WindowManager.hasWindowExtensionsEnabled());
    }

    /**
     * MUST implement the latest available stable version of the extensions API
     * to be used by Window Manager Jetpack library, and declares the window extension
     * is enabled.
     */
    @RequiresFlagsEnabled(FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG)
    @ApiTest(apis = {
            "androidx.window.extensions.WindowExtensionsProvider#getWindowExtensions",
            "androidx.window.extensions.WindowExtensions#getVendorApiLevel",
            "android.view.WindowManager#hasWindowExtensionsEnabled"
    })
    @Test
    public void testWindowExtensionsOnAllDevices() {
        assertTrue("WindowExtension version is not latest",
                ExtensionsUtil.isExtensionVersionLatest());
        assertTrue("Device must declared that the WindowExtension is enabled",
                WindowManager.hasWindowExtensionsEnabled());
    }

    /**
     * MUST support Activity Embedding APIs and make ActivityEmbeddingComponent available via
     * WindowExtensions interface.
     */
    @RequiresFlagsDisabled(FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG)
    @ApiTest(apis = {"androidx.window.extensions.WindowExtensions#getActivityEmbeddingComponent"})
    @Test
    public void testActivityEmbeddingAvailability() {
        assumeHasLargeScreenDisplay();
        WindowExtensions windowExtensions = ExtensionsUtil.getWindowExtensions();
        assertNotNull("WindowExtensions is not available", windowExtensions);
        ActivityEmbeddingComponent activityEmbeddingComponent =
                windowExtensions.getActivityEmbeddingComponent();
        assertNotNull("ActivityEmbeddingComponent is not available", activityEmbeddingComponent);
    }

    /**
     * MUST support Activity Embedding APIs and make ActivityEmbeddingComponent available via
     * WindowExtensions interface.
     */
    @RequiresFlagsEnabled(FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG)
    @ApiTest(apis = {"androidx.window.extensions.WindowExtensions#getActivityEmbeddingComponent"})
    @Test
    public void testActivityEmbeddingOnAllDevices() {
        final WindowExtensions windowExtensions = ExtensionsUtil.getWindowExtensions();
        assertNotNull("WindowExtensions is not available", windowExtensions);
        final ActivityEmbeddingComponent activityEmbeddingComponent =
                windowExtensions.getActivityEmbeddingComponent();
        assertNotNull("ActivityEmbeddingComponent is not available", activityEmbeddingComponent);
    }

    /**
     * MUST also implement the stable version of sidecar API for compatibility with older
     * applications.
     */
    @ApiTest(apis = {"androidx.window.sidecar.SidecarProvider#getApiVersion"})
    @Test
    public void testSidecarAvailability() {
        assumeHasLargeScreenDisplayOrExtensionEnabled();
        assertTrue("Sidecar is not available", SidecarUtil.isSidecarVersionValid());
    }

    private boolean hasLargeScreenDisplay() {
        final DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        return Arrays.stream(displayManager.getDisplays())
                .filter(display -> display.getType() == Display.TYPE_INTERNAL)
                .anyMatch(this::isLargeScreenDisplay);
    }

    private void assumeHasLargeScreenDisplay() {
        assumeTrue("Device does not has a minimum screen dimension greater than or equal to "
                        + WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP + "dp",
                hasLargeScreenDisplay());
    }

    private void assumeHasLargeScreenDisplayOrExtensionEnabled() {
        assumeTrue("Device does not support multi window, so window extensions features are"
                + " not enabled",
                ActivityTaskManager.supportsMultiWindow(getInstrumentation().getContext()));
        assumeTrue("Device does not has a minimum screen dimension greater than or equal to "
                        + WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP + "dp and window "
                        + "extensions are not enabled.",
                hasLargeScreenDisplay() || WindowManager.hasWindowExtensionsEnabled());
    }

    private boolean isLargeScreenDisplay(@NonNull Display display) {
        // Use WindowContext with type application overlay to prevent the metrics overridden by
        // activity bounds. Note that process configuration may still be overridden by
        // foreground Activity.
        final Context appContext = ApplicationProvider.getApplicationContext();
        final Context windowContext = appContext.createWindowContext(display,
                TYPE_APPLICATION_OVERLAY, null /* options */);
        return windowContext.getResources().getConfiguration().smallestScreenWidthDp
                >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
    }
}
