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

package android.virtualdevice.cts.applaunch;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.view.ViewConfiguration;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled({
    Flags.FLAG_VIEWCONFIGURATION_APIS,
    android.content.res.Flags.FLAG_RRO_CONSTRAINTS
})
public class VirtualDeviceViewConfigurationTest {
    private static final double DELTA = 0.0005;

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Test
    public void getTapTimeoutMillis_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getTapTimeoutMillis();

        int overriddenValue = 5000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setTapTimeoutDuration(Duration.ofMillis(overriddenValue))
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(overriddenValue, ViewConfiguration.get(activity).getTapTimeoutMillis());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(valueOnDefaultDevice, ViewConfiguration.get(activity).getTapTimeoutMillis());
    }

    @Test
    public void getDoubleTapTimeoutMillis_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getDoubleTapTimeoutMillis();

        int overriddenValue = 5000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(Duration.ofMillis(overriddenValue))
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(overriddenValue, ViewConfiguration.get(activity).getDoubleTapTimeoutMillis());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice, ViewConfiguration.get(activity).getDoubleTapTimeoutMillis());
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScrollFrictionAmount_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        float valueOnDefaultDevice = ViewConfiguration.get(activity).getScrollFrictionAmount();

        float overriddenValue = 5000f;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setScrollFriction(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                overriddenValue, ViewConfiguration.get(activity).getScrollFrictionAmount(), DELTA);
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice,
                ViewConfiguration.get(activity).getScrollFrictionAmount(),
                DELTA);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScaledTouchSlop_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        float valueOnDefaultDevice = ViewConfiguration.get(activity).getScaledTouchSlop();

        float overriddenValue = 5000f;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setTouchSlopDp(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                getPixelDimensions(overriddenValue, activity),
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                DELTA);
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice, ViewConfiguration.get(activity).getScaledTouchSlop(), DELTA);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScaledMinimumFlingVelocity_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        float valueOnDefaultDevice =
                ViewConfiguration.get(activity).getScaledMinimumFlingVelocity();

        float overriddenValue = 5000f;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityDpPerSecond(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                getPixelDimensions(overriddenValue, activity),
                ViewConfiguration.get(activity).getScaledMinimumFlingVelocity(),
                DELTA);
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice,
                ViewConfiguration.get(activity).getScaledMinimumFlingVelocity(),
                DELTA);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScaledMaximumFlingVelocity_customValueOnVirtualDevice() throws Exception {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        float valueOnDefaultDevice =
                ViewConfiguration.get(activity).getScaledMaximumFlingVelocity();

        float overriddenValue = 5000f;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setMaximumFlingVelocityDpPerSecond(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                getPixelDimensions(overriddenValue, activity),
                ViewConfiguration.get(activity).getScaledMaximumFlingVelocity(),
                DELTA);
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice,
                ViewConfiguration.get(activity).getScaledMaximumFlingVelocity(),
                DELTA);
    }

    private int createVirtualDisplayWithinVirtualDeviceWithParams(
            ViewConfigurationParams viewConfigurationParams) {
        VirtualDeviceManager.VirtualDevice virtualDevice =
                mRule.createManagedVirtualDevice(
                        new VirtualDeviceParams.Builder()
                                .setViewConfigurationParams(viewConfigurationParams)
                                .build());
        VirtualDisplay display =
                mRule.createManagedVirtualDisplay(
                        virtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        return display.getDisplay().getDisplayId();
    }

    private static float getPixelDimensions(float densityIndependentDimensions, Activity activity) {
        return densityIndependentDimensions * activity.getResources().getDisplayMetrics().density;
    }
}
