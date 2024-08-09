/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.devicestate.DeviceStateManager;
import android.os.Build;
import android.os.Bundle;
import android.server.wm.jetpack.extensions.util.ExtensionsUtil;
import android.server.wm.jetpack.extensions.util.SidecarUtil;
import android.server.wm.jetpack.extensions.util.Version;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.DummyActivity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Screen device info collector.
 */
public final class ScreenDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);

        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);

        store.addResult("width_pixels", metrics.widthPixels);
        store.addResult("height_pixels", metrics.heightPixels);
        store.addResult("x_dpi", metrics.xdpi);
        store.addResult("y_dpi", metrics.ydpi);
        store.addResult("density", metrics.density);
        store.addResult("density_dpi", metrics.densityDpi);

        Configuration configuration = getContext().getResources().getConfiguration();
        store.addResult("screen_size", getScreenSize(configuration));
        store.addResult("smallest_screen_width_dp", configuration.smallestScreenWidthDp);

        // Add WindowManager Jetpack Library version and available display features.
        addDisplayFeaturesIfPresent(store);

        // Add device states from DeviceStateManager if available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addDeviceStatesIfAvailable(store);
        }
    }

    private void addDisplayFeaturesIfPresent(DeviceInfoStore store) throws Exception {
        // Try to get display features from extensions. If extensions is not present, try sidecar.
        // If neither is available, do nothing.
        // TODO (b/202855636) store info from both extensions and sidecar if both are present
        if (ExtensionsUtil.isExtensionVersionValid()) {
            // Extensions is available on device.
            final Version extensionVersion = ExtensionsUtil.getExtensionVersion();
            store.addResult("wm_jetpack_version",
                    "[Extensions]" + extensionVersion.toString());
            final Activity activity = ScreenDeviceInfo.this.launchActivity(
                    "com.android.compatibility.common.deviceinfo",
                    DummyActivity.class,
                    new Bundle());
            int[] displayFeatureTypes = ExtensionsUtil.getExtensionDisplayFeatureTypes(activity);
            store.addArrayResult("display_features", displayFeatureTypes);
        } else if (SidecarUtil.isSidecarVersionValid()) {
            // Sidecar is available on device.
            final Version sidecarVersion = SidecarUtil.getSidecarVersion();
            store.addResult("wm_jetpack_version", "[Sidecar]" + sidecarVersion.toString());
            final Activity activity = ScreenDeviceInfo.this.launchActivity(
                    "com.android.compatibility.common.deviceinfo",
                    DummyActivity.class,
                    new Bundle());
            int[] displayFeatureTypes = SidecarUtil.getSidecarDisplayFeatureTypes(activity);
            store.addArrayResult("display_features", displayFeatureTypes);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void addDeviceStatesIfAvailable(DeviceInfoStore store) throws IOException {
        DeviceStateManager deviceStateManager = getContext().getSystemService(
                DeviceStateManager.class);

        // Get the supported device states on device if DeviceStateManager is available
        if (deviceStateManager != null) {
            store.addArrayResult("device_states", getDeviceStateIdentifiers(deviceStateManager));
        }
    }

    /**
     * Returns the array of device state identifiers from {@link DeviceStateManager}. Due to GTS and
     * ATS running tests on many different sdk-levels, this method may be running on a newer or
     * older Android version, possibly bringing in issues if {@link DeviceStateManager}'s API
     * surface has changed. This method uses reflection to call the correct API if that has
     * occurred.
     *
     * b/329875626 for reference.
     */
    private int[] getDeviceStateIdentifiers(DeviceStateManager deviceStateManager) {
        try {

            return deviceStateManager.getSupportedStates();
        } catch (NoSuchMethodError e) {
            return getDeviceStateIdentifiersFromMethod(deviceStateManager,
                    getMethod(deviceStateManager.getClass(), "getSupportedDeviceStates"));
        }
    }

    /**
     * Attempst to retrieve the array of device state identifiers from the provided {@link Method}
     * using reflection.
     */
    private int[] getDeviceStateIdentifiersFromMethod(DeviceStateManager deviceStateManager,
            Method getSupportedDeviceStatesMethod) {
        try {
            List<Object> supportedDeviceStates =
                    (List<Object>) getSupportedDeviceStatesMethod.invoke(deviceStateManager);
            int[] identifiers = new int[supportedDeviceStates.size()];
            for (int i = 0; i < supportedDeviceStates.size(); i++) {
                Class<?> c = Class.forName("android.hardware.devicestate.DeviceState");
                int id = (int) getMethod(c, "getIdentifier").invoke(supportedDeviceStates.get(i));
                identifiers[i] = id;
            }
            return identifiers;
        } catch (Exception ignored) {
            return new int[0];
        }
    }

    /**
     * Returns the {@link Method} for the provided {@code methodName} on the provided
     * {@code classToCheck}. If that method does not exist, return {@code null};
     */
    private Method getMethod(Class<?> classToCheck, String methodName) {
        try {
            return classToCheck.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String getScreenSize(Configuration configuration) {
        int screenLayout = configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        String screenSize = String.format("0x%x", screenLayout);
        switch (screenLayout) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                screenSize = "small";
                break;

            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                screenSize = "normal";
                break;

            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                screenSize = "large";
                break;

            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                screenSize = "xlarge";
                break;

            case Configuration.SCREENLAYOUT_SIZE_UNDEFINED:
                screenSize = "undefined";
                break;
        }
        return screenSize;
    }
}
