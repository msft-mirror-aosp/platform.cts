/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.display.cts;

import static android.hardware.display.BrightnessCorrection.createScaleAndTranslateLog;

import static com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.BrightnessChangeEvent;
import android.hardware.display.BrightnessConfiguration;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.Display;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.server.testutils.TestUtils;

import com.google.common.collect.Range;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@AppModeFull
@MediumTest
@RunWith(JUnitParamsRunner.class)
public class BrightnessTest extends TestBase {
    private static final String TAG = "BrightnessTest";
    private static final float PERCENTAGE_DELTA = 0.05f;

    private Map<Long, BrightnessChangeEvent> mLastReadEvents = new HashMap<>();
    private DisplayManager mDisplayManager;
    private Context mContext;
    private PackageManager mPackageManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mPackageManager = mContext.getPackageManager();
        launchScreenOnActivity();
        revokePermission(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS);
        try (var usage = new PermissionCloseable(Manifest.permission.BRIGHTNESS_SLIDER_USAGE)) {
            recordSliderEvents();
        }
    }

    @Test
    public void testBrightnessSliderTracking() throws Exception {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

        // Don't run as there is no app that has permission to access slider usage.
        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.BRIGHTNESS_SLIDER_USAGE) > 0);

        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS)
                    > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            // Update brightness
            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMinimumBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            assertEquals(1, newEvents.size());
            BrightnessChangeEvent firstEvent = newEvents.get(0);
            assertValidLuxData(firstEvent);

            // Update brightness again
            newEvents = brtCloseable.changeBrightness(brtCloseable.getMaximumBrightness());
            assertEquals(1, newEvents.size());
            BrightnessChangeEvent secondEvent = newEvents.get(0);
            assertValidLuxData(secondEvent);
            assertEquals(secondEvent.lastBrightness, firstEvent.brightness, 1.0f);
            assertTrue(secondEvent.isUserSetBrightness);
            assertTrue("failed " + secondEvent.brightness + " not greater than " +
                    firstEvent.brightness, secondEvent.brightness > firstEvent.brightness);
        }
    }

    @Test
    public void testBrightnesSliderTrackingDecrease() throws Exception {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

        // Don't run as there is no app that has permission to access slider usage.
        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.BRIGHTNESS_SLIDER_USAGE) > 0);

        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS)
                    > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMiddleBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            assertEquals(1, newEvents.size());
            BrightnessChangeEvent firstEvent = newEvents.get(0);
            assertValidLuxData(firstEvent);
            // Update brightness again
            newEvents = brtCloseable.changeBrightness(brtCloseable.getMinimumBrightness());
            assertEquals(1, newEvents.size());
            BrightnessChangeEvent secondEvent = newEvents.get(0);
            assertValidLuxData(secondEvent);
            assertEquals(secondEvent.lastBrightness, firstEvent.brightness, 1.0f);
            assertTrue(secondEvent.isUserSetBrightness);
            assertTrue("failed " + secondEvent.brightness + " not less than "
                    + firstEvent.brightness, secondEvent.brightness < firstEvent.brightness);
        }
    }

    @Test
    public void testNoTrackingForManualBrightness() throws Exception {
        // Don't run as there is no app that has permission to access slider usage.
        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.BRIGHTNESS_SLIDER_USAGE) > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMinimumBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            assertTrue(newEvents.isEmpty());
            // Then change the brightness
            newEvents = brtCloseable.changeBrightness(brtCloseable.getMaximumBrightness());
            // There shouldn't be any events.
            assertTrue(newEvents.isEmpty());
        }
    }

    @Test
    public void testNoColorSampleData() throws Exception {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

          // Don't run as there is no app that has permission to access slider usage.
        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.BRIGHTNESS_SLIDER_USAGE) > 0);

        // Don't run as there is no app that has permission to push curves.
        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            // Set brightness config to not sample color.
            BrightnessConfiguration config =
                    new BrightnessConfiguration.Builder(
                            new float[]{0.0f, 1000.0f}, new float[]{20.0f, 500.0f})
                            .setShouldCollectColorSamples(false).build();
            mDisplayManager.setBrightnessConfiguration(config);

            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMinimumBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            // No color samples.
            assertEquals(0, newEvents.get(0).colorSampleDuration);
            assertNull(newEvents.get(0).colorValueBuckets);
            // No test for sampling color as support is optional.
        }
    }

    @Test
    public void testSliderUsagePermission() {
        assertThrows(SecurityException.class, mDisplayManager::getBrightnessEvents);
    }

    @Test
    public void testConfigureBrightnessPermission() {
        BrightnessConfiguration config =
            new BrightnessConfiguration.Builder(
                    new float[]{0.0f, 1000.0f},new float[]{20.0f, 500.0f})
                .setDescription("some test").build();

        assertThrows(SecurityException.class,
                () -> mDisplayManager.setBrightnessConfiguration(config));
    }

    @Test
    public void testSetGetSimpleCurve() {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

        // Don't run as there is no app that has permission to push curves.
        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        try (var brt = new PermissionCloseable(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS)) {
            var defaultConfig = mDisplayManager.getDefaultBrightnessConfiguration();
            // This might be null, meaning that the device doesn't support brightness configuration
            assumeNotNull(defaultConfig);

            BrightnessConfiguration config =
                    new BrightnessConfiguration.Builder(
                            new float[]{0.0f, 1000.0f}, new float[]{20.0f, 500.0f})
                            .addCorrectionByCategory(ApplicationInfo.CATEGORY_IMAGE,
                                    createScaleAndTranslateLog(0.80f, 0.2f))
                            .addCorrectionByPackageName("some.package.name",
                                    createScaleAndTranslateLog(0.70f, 0.1f))
                            .setShortTermModelTimeoutMillis(
                                    defaultConfig.getShortTermModelTimeoutMillis() + 1000L)
                            .setShortTermModelLowerLuxMultiplier(
                                    defaultConfig.getShortTermModelLowerLuxMultiplier() + 0.2f)
                            .setShortTermModelUpperLuxMultiplier(
                                    defaultConfig.getShortTermModelUpperLuxMultiplier() + 0.3f)
                            .setDescription("some test").build();
            mDisplayManager.setBrightnessConfiguration(config);
            BrightnessConfiguration returnedConfig = mDisplayManager.getBrightnessConfiguration();
            assertEquals(config, returnedConfig);
            assertEquals(returnedConfig.getCorrectionByCategory(ApplicationInfo.CATEGORY_IMAGE),
                    createScaleAndTranslateLog(0.80f, 0.2f));
            assertEquals(returnedConfig.getCorrectionByPackageName("some.package.name"),
                    createScaleAndTranslateLog(0.70f, 0.1f));
            assertNull(returnedConfig.getCorrectionByCategory(ApplicationInfo.CATEGORY_GAME));
            assertNull(returnedConfig.getCorrectionByPackageName("someother.package.name"));
            assertEquals(defaultConfig.getShortTermModelTimeoutMillis() + 1000L,
                    returnedConfig.getShortTermModelTimeoutMillis());
            assertEquals(defaultConfig.getShortTermModelLowerLuxMultiplier() + 0.2f,
                    returnedConfig.getShortTermModelLowerLuxMultiplier(), 0.001f);
            assertEquals(defaultConfig.getShortTermModelUpperLuxMultiplier() + 0.3f,
                    returnedConfig.getShortTermModelUpperLuxMultiplier(), 0.001f);

            // After clearing the curve we should get back the default curve.
            mDisplayManager.setBrightnessConfiguration(null);
            returnedConfig = mDisplayManager.getBrightnessConfiguration();
            assertEquals(mDisplayManager.getDefaultBrightnessConfiguration(), returnedConfig);
        }
    }

    @Test
    public void testGetDefaultCurve()  {
        // Don't run as there is no app that has permission to push curves.
        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        try (var brt = new PermissionCloseable(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS)) {
            var defaultConfig = mDisplayManager.getDefaultBrightnessConfiguration();
            assumeNotNull(defaultConfig);

            Pair<float[], float[]> curve = defaultConfig.getCurve();
            assertTrue(curve.first.length > 0);
            assertEquals(curve.first.length, curve.second.length);
            assertInRange(curve.first, 0, Float.MAX_VALUE);
            assertInRange(curve.second, 0, Float.MAX_VALUE);
            assertEquals(0.0, curve.first[0], 0.1);
            assertMonotonic(curve.first, true /*strictly increasing*/, "lux");
            assertMonotonic(curve.second, false /*strictly increasing*/, "nits");
            assertTrue(defaultConfig.getShortTermModelLowerLuxMultiplier() > 0.0f);
            assertTrue(defaultConfig.getShortTermModelLowerLuxMultiplier() < 10.0f);
            assertTrue(defaultConfig.getShortTermModelUpperLuxMultiplier() > 0.0f);
            assertTrue(defaultConfig.getShortTermModelUpperLuxMultiplier() < 10.0f);
            assertTrue(defaultConfig.getShortTermModelTimeoutMillis() > 0L);
            assertTrue(defaultConfig.getShortTermModelTimeoutMillis() < 24 * 60 * 60 * 1000L);
            assertFalse(defaultConfig.shouldCollectColorSamples());
        }
    }

    @Test
    public void testSliderEventsReflectCurves() throws Exception {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

        // Don't run as there is no app that has permission to access slider usage.
        assumeTrue(
                numberOfSystemAppsWithPermission(Manifest.permission.BRIGHTNESS_SLIDER_USAGE) > 0);
        // Don't run as there is no app that has permission to push curves.
        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        BrightnessConfiguration config =
                new BrightnessConfiguration.Builder(
                                new float[] {0.0f, 10000.0f}, new float[] {15.0f, 400.0f})
                        .setDescription("model:8")
                        .build();

        try (var brtCloseable = new BrightnessCloseable()) {
            // Update brightness while we have a custom curve.
            mDisplayManager.setBrightnessConfiguration(config);
            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMinimumBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                            (e) -> !e.isDefaultBrightnessConfig);
            assertFalse(newEvents.isEmpty());
            BrightnessChangeEvent firstEvent = newEvents.get(newEvents.size() - 1);
            assertValidLuxData(firstEvent);

            // Update brightness again now with default curve.
            mDisplayManager.setBrightnessConfiguration(null);
            newEvents = brtCloseable.changeBrightness(
                    brtCloseable.getMaximumBrightness(), (e) -> e.isDefaultBrightnessConfig);
            assertFalse(newEvents.isEmpty());
            BrightnessChangeEvent secondEvent = newEvents.get(newEvents.size() - 1);
            assertValidLuxData(secondEvent);
        }
    }

    @Test
    public void testAtMostOneAppHoldsBrightnessConfigurationPermission() {
        assertTrue(numberOfSystemAppsWithPermission(
                    Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) < 2);
    }

    @Test
    public void testSetAndGetBrightnessConfiguration() throws Exception {
        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            BrightnessConfiguration configSet =
                    new BrightnessConfiguration.Builder(
                            new float[]{0.0f, 1345.0f}, new float[]{15.0f, 250.0f})
                            .setDescription("model:8").build();
            BrightnessConfiguration configGet;

            mDisplayManager.setBrightnessConfiguration(configSet);
            configGet = mDisplayManager.getBrightnessConfiguration();

            assertNotNull(configGet);
            assertEquals(configSet, configGet);
        }
    }

    @Test
    public void testSetAndGetPerDisplay() throws Exception {
        // Only run if we have a valid ambient light sensor.
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));

        assumeTrue(numberOfSystemAppsWithPermission(
                Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS) > 0);

        try (var brtCloseable = new BrightnessCloseable()) {
            // Get a unique display id via brightness change event
            var newEvents =
                    brtCloseable.changeBrightness(
                            brtCloseable.getMinimumBrightness(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            BrightnessChangeEvent firstEvent = newEvents.get(0);
            String uniqueDisplayId = firstEvent.uniqueDisplayId;
            assertNotNull(uniqueDisplayId);

            // Set & get a configuration for that specific display
            BrightnessConfiguration configSet =
                    new BrightnessConfiguration.Builder(
                            new float[]{0.0f, 12345.0f}, new float[]{15.0f, 200.0f})
                            .setDescription("test:0").build();
            mDisplayManager.setBrightnessConfigurationForDisplay(configSet, uniqueDisplayId);
            BrightnessConfiguration returnedConfig =
                    mDisplayManager.getBrightnessConfigurationForDisplay(uniqueDisplayId);

            assertEquals(configSet, returnedConfig);

            // Set & get a different configuration for that specific display
            BrightnessConfiguration configSetTwo =
                    new BrightnessConfiguration.Builder(
                            new float[]{0.0f, 678.0f}, new float[]{15.0f, 500.0f})
                            .setDescription("test:1").build();
            mDisplayManager.setBrightnessConfigurationForDisplay(configSetTwo, uniqueDisplayId);
            BrightnessConfiguration returnedConfigTwo =
                    mDisplayManager.getBrightnessConfigurationForDisplay(uniqueDisplayId);

            assertEquals(configSetTwo, returnedConfigTwo);

            // Since brightness change event will happen on the default display, this should also
            // return the same value.
            BrightnessConfiguration unspecifiedDisplayConfig =
                    mDisplayManager.getBrightnessConfiguration();
            assertEquals(configSetTwo, unspecifiedDisplayConfig);
        }
    }

    @Test
    @Parameters({"0", "13.1", "39", "54.32", "80", "97.87", "100"})
    public void testSetBrightness_unitPercentage(float brightness) throws Exception {
        try (var brtCloseable = new BrightnessCloseable()) {
            mDisplayManager.setBrightness(
                    Display.DEFAULT_DISPLAY, brightness, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
            float actualBrightness =
                    mDisplayManager.getBrightness(
                            Display.DEFAULT_DISPLAY, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
            assertEquals(actualBrightness, brightness, /* delta= */ PERCENTAGE_DELTA);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testBrightnessChangeListener() throws Exception {
        try (var brtCloseable = new BrightnessCloseable()) {
            float brightness = 33.7f;
            CountDownLatch signal = new CountDownLatch(1);
            mDisplayManager.registerDisplayListener(
                    mContext.getMainExecutor(),
                    DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS,
                    new DisplayManager.DisplayListener() {
                        @Override
                        public void onDisplayAdded(int displayId) {}

                        @Override
                        public void onDisplayRemoved(int displayId) {}

                        @Override
                        public void onDisplayChanged(int displayId) {
                            float newBrightness =
                                    mDisplayManager.getBrightness(
                                            Display.DEFAULT_DISPLAY,
                                            DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
                            if (Math.abs(newBrightness - brightness) < PERCENTAGE_DELTA) {
                                signal.countDown();
                            }
                        }
                    });

            mDisplayManager.setBrightness(
                    Display.DEFAULT_DISPLAY, brightness, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);

            assertTrue(signal.await(5, TimeUnit.SECONDS));
        }
    }

    private void assertValidLuxData(BrightnessChangeEvent event) {
        assertNotNull(event.luxTimestamps);
        assertNotNull(event.luxValues);
        assertTrue(event.luxTimestamps.length > 0);
        assertEquals(event.luxValues.length, event.luxTimestamps.length);
        for (int i = 1; i < event.luxTimestamps.length; ++i) {
            assertTrue(event.luxTimestamps[i - 1] <= event.luxTimestamps[i]);
        }
        for (int i = 0; i < event.luxValues.length; ++i) {
            assertTrue(event.luxValues[i] >= 0.0f);
            assertTrue(event.luxValues[i] <= Float.MAX_VALUE);
            assertFalse(Float.isNaN(event.luxValues[i]));
        }
    }

    /**
     * Returns the number of system apps with the given permission.
     */
    private int numberOfSystemAppsWithPermission(String permission) {
        List<PackageInfo> packages = mContext.getPackageManager().getPackagesHoldingPermissions(
                new String[]{permission}, PackageManager.MATCH_SYSTEM_ONLY);
        packages.removeIf(packageInfo -> packageInfo.packageName.equals("com.android.shell"));
        return packages.size();
    }

    private List<BrightnessChangeEvent> getNewEvents(int expected,
            Predicate<BrightnessChangeEvent> pred) throws InterruptedException {
        List<BrightnessChangeEvent> newEvents = new ArrayList<>();
        for (int i = 0; newEvents.size() < expected && i < 20; ++i) {
            if (i != 0) {
                Thread.sleep(100);
            }
            for (BrightnessChangeEvent e : getNewEvents()) {
                if (pred.test(e)) {
                    newEvents.add(e);
                }
            }
        }
        return newEvents;
    }

    private List<BrightnessChangeEvent> getNewEvents() {
        List<BrightnessChangeEvent> newEvents = new ArrayList<>();
        List<BrightnessChangeEvent> events = mDisplayManager.getBrightnessEvents();
        for (BrightnessChangeEvent event : events) {
            if (!mLastReadEvents.containsKey(event.timeStamp)) {
                newEvents.add(event);
            }
        }
        mLastReadEvents = new HashMap<>();
        for (BrightnessChangeEvent event : events) {
            mLastReadEvents.put(event.timeStamp, event);
        }
        return newEvents;
    }

    private void recordSliderEvents() {
        mLastReadEvents = new HashMap<>();
        List<BrightnessChangeEvent> eventsBefore = mDisplayManager.getBrightnessEvents();
        for (BrightnessChangeEvent event : eventsBefore) {
            mLastReadEvents.put(event.timeStamp, event);
        }
    }

    private int getSystemSetting(String setting) {
        return Integer.parseInt(runShellCommand("settings get system " + setting));
    }

    private List<BrightnessChangeEvent> setDisplayBrightness(float value,
            Predicate<BrightnessChangeEvent> pred) {
        runShellCommand("cmd display set-brightness " + value);
        try {
            return getNewEvents(1, pred);
        } catch (InterruptedException e) {
            // If Thread.sleep gets interrupted rethrow as runtime exception to avoid annotation.
            throw new RuntimeException(e);
        }
    }

    private void grantPermission(String permission) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(mContext.getPackageName(), permission);
    }

    private void revokePermission(String permission) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .revokeRuntimePermission(mContext.getPackageName(), permission);
    }

    private static void assertInRange(float[] values, float min, float max) {
        for (int i = 0; i < values.length; i++) {
            assertFalse(Float.isNaN(values[i]));
            assertTrue(values[i] >= min);
            assertTrue(values[i] <= max);
        }
    }

    private static void assertMonotonic(float[] values, boolean strictlyIncreasing, String name) {
        if (values.length <= 1) {
            return;
        }
        float prev = values[0];
        for (int i = 1; i < values.length; i++) {
            if (prev > values[i] || (prev == values[i] && strictlyIncreasing)) {
                String condition = strictlyIncreasing ? "strictly increasing" : "monotonic";
                fail(name + " values must be " + condition);
            }
            prev = values[i];
        }
    }

    private class BrightnessCloseable extends TestUtils.CleanupExecutor {
        private float mPrevBrightness;
        private float mCurrBrightness;
        private int mPrevBrightnessMode;
        private int mCurrBrightnessMode;
        private BrightnessConfiguration mPrevConfig;
        private float mMaxBrightness;
        private float mMinBrightness;
        private PermissionCloseable mConfigureBrightnessPermission;
        private PermissionCloseable mSliderPermission;
        private PermissionContext mWriteSettingsPermission;

        /**
         * Initializes the brightness state. Anything can fail in this method, so when the close()
         * method will be called to restore the state, it needs to know which state needs to be
         * restored.
         */
        BrightnessCloseable() throws Exception {
            super(TAG);
            try {
                mConfigureBrightnessPermission =
                        new PermissionCloseable(Manifest.permission.CONFIGURE_DISPLAY_BRIGHTNESS);
                addCleanup(mConfigureBrightnessPermission::close);

                mSliderPermission =
                        new PermissionCloseable(Manifest.permission.BRIGHTNESS_SLIDER_USAGE);
                addCleanup(mSliderPermission::close);

                mWriteSettingsPermission =
                        TestApis.permissions().withPermission(Manifest.permission.WRITE_SETTINGS);
                addCleanup(mWriteSettingsPermission::close);

                mCurrBrightness =
                        mPrevBrightness =
                                brightnessIntToFloat(
                                        getSystemSetting(Settings.System.SCREEN_BRIGHTNESS));
                addCleanup(() -> changeBrightness(mPrevBrightness));

                mCurrBrightnessMode =
                        mPrevBrightnessMode =
                                getSystemSetting(Settings.System.SCREEN_BRIGHTNESS_MODE);
                addCleanup(() -> changeBrightnessMode(mPrevBrightnessMode));

                mPrevConfig = mDisplayManager.getBrightnessConfiguration();
                addCleanup(() -> mDisplayManager.setBrightnessConfiguration(mPrevConfig));

                // Enforce min brightness to get the system absolute min brightness
                changeBrightness(0f);
                mMinBrightness =
                        brightnessIntToFloat(getSystemSetting(Settings.System.SCREEN_BRIGHTNESS));
                // Enforce max brightness to get the system absolute max brightness
                changeBrightness(1.0f);
                mMaxBrightness =
                        brightnessIntToFloat(getSystemSetting(Settings.System.SCREEN_BRIGHTNESS));
                recordSliderEvents();
            } catch (Throwable e) {
                close();
                throw e;
            }
        }

        float getMinimumBrightness() {
            return mMinBrightness;
        }

        float getMaximumBrightness() {
            return mMaxBrightness;
        }

        float getMiddleBrightness() {
            return (getMinimumBrightness() + getMaximumBrightness()) / 2f;
        }

        List<BrightnessChangeEvent> changeBrightness(float newBrightness) {
            return changeBrightness(newBrightness, mCurrBrightnessMode, null);
        }

        List<BrightnessChangeEvent> changeBrightness(
                float newBrightness, Predicate<BrightnessChangeEvent> pred) {
            return changeBrightness(newBrightness, mCurrBrightnessMode, pred);
        }

        List<BrightnessChangeEvent> changeBrightness(float newBrightness, int newBrightnessMode) {
            return changeBrightness(newBrightness, newBrightnessMode, null);
        }

        List<BrightnessChangeEvent> changeBrightnessMode(int newBrightnessMode) {
            return changeBrightness(mCurrBrightness, newBrightnessMode, null);
        }

        List<BrightnessChangeEvent> changeBrightness(
                float newBrightness, int newBrightnessMode, Predicate<BrightnessChangeEvent> pred) {
            if (newBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                // This might be null, meaning that the device doesn't support autobrightness
                assumeNotNull(mDisplayManager.getDefaultBrightnessConfiguration());
            }
            if (mCurrBrightnessMode != newBrightnessMode) {
                assertTrue("brightness mode must be automatic or manual: " + newBrightnessMode,
                        newBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        || newBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                assertTrue(
                        "setTemporaryBrightnessMode failed",
                        mDisplayManager.setTemporaryBrightnessMode(
                                Display.DEFAULT_DISPLAY, newBrightnessMode));
                mCurrBrightnessMode = newBrightnessMode;
            } else {
                Log.d(
                        TAG,
                        "no brightness mode change "
                                + newBrightnessMode
                                + " for brightness "
                                + newBrightness);
            }

            List<BrightnessChangeEvent> res = List.of();
            if (mCurrBrightness != newBrightness) {
                res = setDisplayBrightness(newBrightness, pred != null ? pred : (e) -> true);
                mCurrBrightness = newBrightness;
            } else {
                Log.d(
                        TAG,
                        "no brightness change " + newBrightness + " for mode " + newBrightnessMode);
            }

            return res;
        }

        /** Converts between the int brightness system and the float brightness system. */
        private static float brightnessIntToFloat(int brightnessInt) {
            assertThat(brightnessInt).isIn(Range.closed(1, 255));
            return (float) (brightnessInt - 1) / 254f;
        }
    }

    private class PermissionCloseable implements AutoCloseable {
        private final String mPermission;

        PermissionCloseable(String permission) {
            mPermission = permission;
            grantPermission(mPermission);
        }

        @Override
        public void close() {
            revokePermission(mPermission);
        }
    }


}
