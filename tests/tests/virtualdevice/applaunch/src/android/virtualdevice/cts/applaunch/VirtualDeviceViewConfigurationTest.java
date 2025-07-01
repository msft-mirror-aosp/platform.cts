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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.view.Display;
import android.view.ViewConfiguration;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceViewConfigurationTest {
    private static final double DELTA = 0.0005;

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIEWCONFIGURATION_APIS)
    public void viewConfiguration_defaultValuesOnVirtualDevice() {
        Activity defaultDisplayActivity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        ViewConfiguration defaultViewconfiguration = ViewConfiguration.get(defaultDisplayActivity);

        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay =
                mRule.createManagedVirtualDisplay(
                        virtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        int displayId = virtualDisplay.getDisplay().getDisplayId();

        // Launch the activity on the virtual device and verify that we get the default values.
        Activity vdActivity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        ViewConfiguration vdViewconfiguration = ViewConfiguration.get(vdActivity);
        assertEquals(
                defaultViewconfiguration.getTapTimeoutMillis(),
                vdViewconfiguration.getTapTimeoutMillis());
        assertEquals(
                defaultViewconfiguration.getDoubleTapTimeoutMillis(),
                vdViewconfiguration.getDoubleTapTimeoutMillis());
        assertEquals(
                defaultViewconfiguration.getLongPressTimeoutMillis(),
                vdViewconfiguration.getLongPressTimeoutMillis());
        assertEquals(
                defaultViewconfiguration.getMultiPressTimeoutMillis(),
                vdViewconfiguration.getMultiPressTimeoutMillis());
        assertEquals(
                defaultViewconfiguration.getScrollFrictionAmount(),
                vdViewconfiguration.getScrollFrictionAmount(),
                DELTA);
        assertEquals(
                getDimensionsDp(
                        defaultViewconfiguration.getScaledTouchSlop(), defaultDisplayActivity),
                getDimensionsDp(vdViewconfiguration.getScaledTouchSlop(), vdActivity));
        assertEquals(
                getDimensionsDp(
                        defaultViewconfiguration.getScaledMinimumFlingVelocity(),
                        defaultDisplayActivity),
                getDimensionsDp(vdViewconfiguration.getScaledMinimumFlingVelocity(), vdActivity));
        assertEquals(
                getDimensionsDp(
                        defaultViewconfiguration.getScaledMaximumFlingVelocity(),
                        defaultDisplayActivity),
                getDimensionsDp(vdViewconfiguration.getScaledMaximumFlingVelocity(), vdActivity));
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        Flags.FLAG_DEVICE_AWARE_SETTINGS_OVERRIDE
    })
    public void getLongPressTimeoutMillis_afterSettingChange_returnsNewValueOnVirtualDevice()
            throws Exception {
        verifyNewValueAfterSettingChange(
                Settings.Secure.LONG_PRESS_TIMEOUT, ViewConfiguration::getLongPressTimeoutMillis);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        Flags.FLAG_DEVICE_AWARE_SETTINGS_OVERRIDE
    })
    public void getMultiPressTimeoutMillis_afterSettingChange_returnsNewValueOnVirtualDevice()
            throws Exception {
        verifyNewValueAfterSettingChange(
                Settings.Secure.MULTI_PRESS_TIMEOUT, ViewConfiguration::getMultiPressTimeoutMillis);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS
    })
    public void getTapTimeoutMillis_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getTapTimeoutMillis();

        int overriddenValue = valueOnDefaultDevice + 1000;
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
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS
    })
    public void getDoubleTapTimeoutMillis_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getDoubleTapTimeoutMillis();

        int overriddenValue = valueOnDefaultDevice + 1000;
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
        Flags.FLAG_DEVICE_AWARE_SETTINGS_OVERRIDE
    })
    public void getLongPressTimeoutMillis_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getLongPressTimeoutMillis();

        int overriddenValue = valueOnDefaultDevice + 1000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setLongPressTimeoutDuration(Duration.ofMillis(overriddenValue))
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(overriddenValue, ViewConfiguration.get(activity).getLongPressTimeoutMillis());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice, ViewConfiguration.get(activity).getLongPressTimeoutMillis());
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        Flags.FLAG_DEVICE_AWARE_SETTINGS_OVERRIDE
    })
    public void getMultiPressTimeoutMillis_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getMultiPressTimeoutMillis();

        int overriddenValue = valueOnDefaultDevice + 1000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setMultiPressTimeoutDuration(Duration.ofMillis(overriddenValue))
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(overriddenValue, ViewConfiguration.get(activity).getMultiPressTimeoutMillis());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice, ViewConfiguration.get(activity).getMultiPressTimeoutMillis());
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScrollFrictionAmount_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        float valueOnDefaultDevice = ViewConfiguration.get(activity).getScrollFrictionAmount();

        float overriddenValue = valueOnDefaultDevice + 1000f;
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
    public void getScaledTouchSlop_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getScaledTouchSlop();

        int overriddenValue = valueOnDefaultDevice + 1000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setTouchSlopPixels(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(overriddenValue, ViewConfiguration.get(activity).getScaledTouchSlop());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(valueOnDefaultDevice, ViewConfiguration.get(activity).getScaledTouchSlop());
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScaledMinimumFlingVelocity_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getScaledMinimumFlingVelocity();

        int overriddenValue = valueOnDefaultDevice + 1000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityPixelsPerSecond(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                overriddenValue, ViewConfiguration.get(activity).getScaledMinimumFlingVelocity());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice,
                ViewConfiguration.get(activity).getScaledMinimumFlingVelocity());
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_RRO_CONSTRAINTS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO
    })
    public void getScaledMaximumFlingVelocity_customValueOnVirtualDevice() {
        Activity activity =
                mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        int valueOnDefaultDevice = ViewConfiguration.get(activity).getScaledMaximumFlingVelocity();

        int overriddenValue = valueOnDefaultDevice + 1000;
        int displayId =
                createVirtualDisplayWithinVirtualDeviceWithParams(
                        new ViewConfigurationParams.Builder()
                                .setMaximumFlingVelocityPixelsPerSecond(overriddenValue)
                                .build());

        // Launch the activity on the virtual device and verify that we get the overridden value.
        activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        assertEquals(
                overriddenValue, ViewConfiguration.get(activity).getScaledMaximumFlingVelocity());
        // Launch the activity on the default device and verify that we get the default value.
        activity = mRule.startActivityOnDisplaySync(Display.DEFAULT_DISPLAY, Activity.class);
        assertEquals(
                valueOnDefaultDevice,
                ViewConfiguration.get(activity).getScaledMaximumFlingVelocity());
    }

    private void verifyNewValueAfterSettingChange(
            String settingKey,
            Function<ViewConfiguration, Integer> viewConfigurationValueProvider) {
        final int[] defaultValue = new int[1];
        final int[] newValue = new int[1];
        final ContentResolver[] contentResolver = new ContentResolver[1];
        mRule.runWithTemporaryPermission(
                () -> {
                    Context context =
                            InstrumentationRegistry.getInstrumentation()
                                    .getTargetContext()
                                    .createContextAsUser(UserHandle.SYSTEM, 0);
                    contentResolver[0] = context.getContentResolver();
                    try {
                        defaultValue[0] = Settings.Secure.getInt(contentResolver[0], settingKey);
                    } catch (Settings.SettingNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    newValue[0] = defaultValue[0] + 1000;
                    Settings.Secure.putInt(contentResolver[0], settingKey, newValue[0]);
                },
                INTERACT_ACROSS_USERS_FULL,
                WRITE_SECURE_SETTINGS);

        try {
            VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice();
            VirtualDisplay virtualDisplay =
                    mRule.createManagedVirtualDisplay(
                            virtualDevice,
                            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
            int displayId = virtualDisplay.getDisplay().getDisplayId();

            // Launch the activity on the virtual device and verify that we get the new value.
            Activity vdActivity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
            ViewConfiguration viewconfiguration = ViewConfiguration.get(vdActivity);
            assertEquals(
                    newValue[0],
                    viewConfigurationValueProvider.apply(viewconfiguration).intValue());
        } finally {
            mRule.runWithTemporaryPermission(
                    () -> {
                        Settings.Secure.putInt(contentResolver[0], settingKey, defaultValue[0]);
                    },
                    INTERACT_ACROSS_USERS_FULL,
                    WRITE_SECURE_SETTINGS);
        }
    }

    private int createVirtualDisplayWithinVirtualDeviceWithParams(
            ViewConfigurationParams viewConfigurationParams) {
        VirtualDeviceManager.VirtualDevice virtualDevice =
                mRule.createManagedVirtualDevice(
                        new VirtualDeviceParams.Builder()
                                .setViewConfigurationParams(viewConfigurationParams)
                                .build());
        // Wait for the resource overlays to take effect.
        SystemClock.sleep(2000);
        VirtualDisplay display =
                mRule.createManagedVirtualDisplay(
                        virtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        return display.getDisplay().getDisplayId();
    }

    private static int getDimensionsDp(int pixelDimensions, Activity activity) {
        return Math.round(pixelDimensions / activity.getResources().getDisplayMetrics().density);
    }
}
