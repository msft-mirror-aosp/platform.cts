/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.app.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.stubs.ActivityManagerMemoryClassLaunchActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.server.wm.ActivityManagerTestBase;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class tests {@link ActivityManager#getMemoryClass()} by checking that the memory class
 * matches the proper screen density and by launching an application that attempts to allocate
 * memory on the heap.
 */
@RunWith(AndroidJUnit4.class)
public final class ActivityManagerMemoryClassTest {
    private Context mContext;

    private static final class ExpectedMemorySizesClass {
        private static final Map<Integer, Integer> sExpectedMemorySizeForWatch = new HashMap<>();
        private static final Map<Integer, Integer> sExpectedMemorySizeForSmallNormalScreen =
                new HashMap<>();
        private static final Map<Integer, Integer> sExpectedMemorySizeForLargeScreen =
                new HashMap<>();
        private static final Map<Integer, Integer> sExpectedMemorySizeForXLargeScreen =
                new HashMap<>();

        static {
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_LOW, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_140, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_MEDIUM, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_180, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_200, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_TV, 32);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_220, 36);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_HIGH, 36);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_260, 36);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_280, 36);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_300, 36);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XHIGH, 48);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_340, 48);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_360, 48);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_390, 48);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_400, 56);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_420, 64);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_440, 88);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_450, 88);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XXHIGH, 88);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_520, 112);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_560, 112);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_600, 138);
            sExpectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XXXHIGH, 154);
            // Backport of DENSITY_520 from Android 14 to android13-tests-dev
            sExpectedMemorySizeForWatch.put(520, 112);
            // Backport of DENSITY_390 to android14-tests-dev
            sExpectedMemorySizeForWatch.put(390, 48);
        }

        static {
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_LOW, 32);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_140, 32);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_MEDIUM, 32);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_180, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_200, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_TV, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_220, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_HIGH, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_260, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_280, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_300, 48);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XHIGH, 80);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_340, 80);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_360, 80);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_390, 80);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_400, 96);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_420, 112);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_440, 128);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_450, 128);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XXHIGH, 128);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_520, 192);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_560, 192);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_600, 228);
            sExpectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 256);
            // Backport of DENSITY_520 from Android 14 to android13-tests-dev
            sExpectedMemorySizeForSmallNormalScreen.put(520, 192);
            // Backport of DENSITY_390 to android14-tests-dev
            sExpectedMemorySizeForSmallNormalScreen.put(390, 80);
        }

        static {
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_LOW, 32);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_140, 48);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_MEDIUM, 48);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_180, 80);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_200, 80);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_TV, 80);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_220, 80);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_HIGH, 80);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_260, 96);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_280, 96);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_300, 96);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XHIGH, 128);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_340, 160);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_360, 160);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_390, 160);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_400, 192);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_420, 228);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_440, 256);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_450, 256);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XXHIGH, 256);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_520, 384);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_560, 384);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_600, 448);
            sExpectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 512);
            // Backport of DENSITY_520 from Android 14 to android13-tests-dev
            sExpectedMemorySizeForLargeScreen.put(520, 192);
            // Backport of DENSITY_390 to android14-tests-dev
            sExpectedMemorySizeForLargeScreen.put(390, 160);
        }

        static {
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_LOW, 48);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_140, 80);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_MEDIUM, 80);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_180, 96);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_200, 96);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_TV, 96);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_220, 96);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_HIGH, 96);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_260, 144);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_280, 144);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_300, 144);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XHIGH, 192);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_340, 192);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_360, 240);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_390, 240);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_400, 288);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_420, 336);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_440, 384);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_450, 384);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XXHIGH, 384);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_520, 576);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_560, 576);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_600, 672);
            sExpectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 768);
            // Backport of DENSITY_520 from Android 14 to android13-tests-dev
            sExpectedMemorySizeForXLargeScreen.put(520, 576);
            // Backport of DENSITY_390 to android14-tests-dev
            sExpectedMemorySizeForXLargeScreen.put(390, 240);
            // Backport of DENSITY_250 to android14-tests-dev
            sExpectedMemorySizeForXLargeScreen.put(250, 144);
        }

        public static Integer getExpectedMemorySize(
                int screenSize, int screenDensity, boolean isWatch) {

            if (isWatch) {
                return sExpectedMemorySizeForWatch.get(screenDensity);
            }

            switch (screenSize) {
                case Configuration.SCREENLAYOUT_SIZE_SMALL:
                case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                    return sExpectedMemorySizeForSmallNormalScreen.get(screenDensity);
                case Configuration.SCREENLAYOUT_SIZE_LARGE:
                    return sExpectedMemorySizeForLargeScreen.get(screenDensity);
                case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                    return sExpectedMemorySizeForXLargeScreen.get(screenDensity);
                default:
                    throw new IllegalArgumentException(
                            "No memory requirement specified "
                                    + " for screen layout size "
                                    + screenSize);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @CddTest(requirement = "3.7")
    @Test
    public void testGetMemoryClass() throws Exception {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        int density = resetDensityIfNeeded(uiDevice);

        int memoryClass = getMemoryClass();
        int screenDensity = getScreenDensity();
        int screenSize = getScreenSize();
        assertMemoryForScreenDensity(memoryClass, screenDensity, screenSize);

        runHeapTestApp(memoryClass);

        restoreDensityIfNeeded(uiDevice, density);
    }

    private int resetDensityIfNeeded(UiDevice device) throws Exception {
        final String output = device.executeShellCommand("wm density");
        final Pattern p = Pattern.compile("Override density: (\\d+)");
        final Matcher m = p.matcher(output);
        if (m.find()) {
            device.executeShellCommand("wm density reset");
            int restoreDensity = Integer.parseInt(m.group(1));
            return restoreDensity;
        }
        return -1;
    }

    private void restoreDensityIfNeeded(UiDevice device, int restoreDensity) throws Exception {
        if (restoreDensity > 0) {
            device.executeShellCommand("wm density " + restoreDensity);
        }
    }

    private int getMemoryClass() {
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        return activityManager.getMemoryClass();
    }

    private int getScreenDensity() {
        // Use physical screen density to more accurately measure memory usage. Compat framework
        // may scale the density of the context
        return ActivityManagerTestBase.ReportedDisplayMetrics
                .getDisplayMetrics(mContext.getDisplayId()).getPhysicalDensity();
    }

    private int getScreenSize() {
        Configuration config = mContext.getResources().getConfiguration();
        final int configScreenSize = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        final int minScreenSizeDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        // The insets size may affect screenSizeDp in different orientations. E.g., the short side
        // is 720dp as the width in portrait orientation, but when the short side is the height in
        // landscape orientation, the value will be smaller than 720dp because the insets of
        // system bars may occupy a little space. Then the screen size from Configuration will be
        // LARGE in landscape and XLARGE in portrait. So below calculation allows to return a
        // smaller size definition if the size excluding insets is lower than the size threshold.
        final Insets insets =
                getActivity(new Intent(Intent.ACTION_MAIN))
                        .getWindowManager()
                        .getCurrentWindowMetrics()
                        .getWindowInsets()
                        .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
        final int insetsSize = Math.max(insets.top + insets.bottom, insets.left + insets.right);
        final int toleranceSizeDp =
                (int)
                        (insetsSize / ((float) config.densityDpi / DisplayMetrics.DENSITY_DEFAULT)
                                + 0.5f);
        Log.i(
                "ActivityManagerMemoryClassTest",
                "getScreenSize: config="
                        + config
                        + " insets="
                        + insets
                        + " toleranceSizeDp="
                        + toleranceSizeDp);
        if (configScreenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE
                && (minScreenSizeDp - toleranceSizeDp < 720)) {
            return Configuration.SCREENLAYOUT_SIZE_LARGE;
        }
        if (configScreenSize == Configuration.SCREENLAYOUT_SIZE_LARGE
                && (minScreenSizeDp - toleranceSizeDp < 480)) {
            return Configuration.SCREENLAYOUT_SIZE_NORMAL;
        }
        return configScreenSize;
    }

    private void assertMemoryForScreenDensity(int memoryClass, int screenDensity, int screenSize) {
        boolean isWatch =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        int expectedMinimumMemory =
                ExpectedMemorySizesClass.getExpectedMemorySize(screenSize, screenDensity, isWatch);

        assertWithMessage(
                        "Expected to have at least "
                                + expectedMinimumMemory
                                + "mb of memory for screen density "
                                + screenDensity)
                .that(memoryClass >= expectedMinimumMemory)
                .isTrue();
    }

    private void runHeapTestApp(int memoryClass) throws InterruptedException {
        Intent intent = new Intent();
        intent.putExtra(ActivityManagerMemoryClassLaunchActivity.MEMORY_CLASS_EXTRA, memoryClass);
        ActivityManagerMemoryClassLaunchActivity activity =
                (ActivityManagerMemoryClassLaunchActivity) getActivity(intent);
        assertWithMessage(
                        "The test application couldn't allocate memory close to the amount "
                                + " specified by the memory class.")
                .that(activity.getResult())
                .isEqualTo(Activity.RESULT_OK);
    }

    private Activity getActivity(Intent intent) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        final String targetPackage = mContext.getPackageName();
        intent.setClassName(
                targetPackage, ActivityManagerMemoryClassLaunchActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityManagerMemoryClassLaunchActivity activity =
                (ActivityManagerMemoryClassLaunchActivity)
                        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return activity;
    }
}
