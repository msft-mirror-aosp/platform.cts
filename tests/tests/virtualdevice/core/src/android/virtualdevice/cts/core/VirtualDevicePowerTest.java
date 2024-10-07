/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.virtualdevice.cts.core;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtualdevice.flags.Flags;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.server.wm.UiDeviceUtils;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests to verify that power manager APIs behave as expected for virtual devices. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDevicePowerTest {

    private static final int FAST_SCREEN_OFF_TIMEOUT_MS = 500;
    private static final int DISPLAY_TIMEOUT_MS = 2000;

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.WRITE_SECURE_SETTINGS);

    private final Context mContext = getInstrumentation().getContext();
    private final ContentResolver mContentResolver = mContext.getContentResolver();

    private PowerManager mDefaultDisplayPowerManager;
    private PowerManager mVirtualDisplayPowerManager;

    private String mInitialDisplayTimeout;
    private int mInitialStayOnWhilePluggedInSetting;
    private int mMinimumScreenOffTimeoutMs;

    private VirtualDevice mVirtualDevice;
    private Display mDisplay;

    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay =
                mVirtualDeviceRule.createManagedVirtualDisplay(mVirtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder(),
                        mVirtualDisplayCallback);
        mDisplay = virtualDisplay.getDisplay();

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);

        Context defaultDisplayContext = mContext.createDisplayContext(
                displayManager.getDisplay(Display.DEFAULT_DISPLAY));
        mDefaultDisplayPowerManager = defaultDisplayContext.getSystemService(PowerManager.class);

        Context virtualDisplayContext = mContext.createDisplayContext(mDisplay);
        mVirtualDisplayPowerManager = virtualDisplayContext.getSystemService(PowerManager.class);

        mInitialDisplayTimeout =
                Settings.System.getString(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT);
        mInitialStayOnWhilePluggedInSetting =
                Settings.Global.getInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        Settings.Global.putInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);

        mMinimumScreenOffTimeoutMs = mContext.getResources().getInteger(
                Resources.getSystem().getIdentifier("config_minimumScreenOffTimeout", "integer",
                        "android"));
        mVirtualDeviceRule.runWithoutPermissions(() -> {
            UiDeviceUtils.wakeUpAndUnlock(mContext);
            return true;
        });
    }

    @After
    public void tearDown() {
        setScreenOffTimeoutMs(mInitialDisplayTimeout);
        Settings.Global.putInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                mInitialStayOnWhilePluggedInSetting);
        mVirtualDeviceRule.runWithoutPermissions(() -> {
            UiDeviceUtils.wakeUpAndUnlock(mContext);
            return true;
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void proximityOffWakeLockLevelSupported_falseOnVirtualDevice() {
        // Only PROXIMITY_SCREEN_OFF_WAKE_LOCK's availability depends on the display.
        assertThat(mVirtualDisplayPowerManager.isWakeLockLevelSupported(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
                .isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void isInteractive_screenOffTimeout_isPerPowerGroup() {
        assumeScreenOffSupported();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        setScreenOffTimeoutMs(FAST_SCREEN_OFF_TIMEOUT_MS);
        SystemClock.sleep(mMinimumScreenOffTimeoutMs);

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void isInteractive_powerButton_isPerPowerGroup() {
        assumeScreenOffSupported();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        UiDeviceUtils.pressSleepButton();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_POWER_MANAGER_APIS)
    public void newWakeLock_isPerPowerGroup() {
        assumeScreenOffSupported();

        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

        PowerManager.WakeLock wakeLock =
                mDefaultDisplayPowerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "cts");
        try {
            wakeLock.acquire();

            setScreenOffTimeoutMs(FAST_SCREEN_OFF_TIMEOUT_MS);
            SystemClock.sleep(mMinimumScreenOffTimeoutMs);

            assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
            assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();

            wakeLock.release();
            SystemClock.sleep(mMinimumScreenOffTimeoutMs);

            assertThat(mDefaultDisplayPowerManager.isInteractive()).isFalse();
            assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(
            {Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER, Flags.FLAG_DISPLAY_POWER_MANAGER_APIS})
    public void testGoToSleepAndWakeUp_turnsOffAndOnVirtualDisplay() {
        mVirtualDeviceRule.startActivityOnDisplaySync(mDisplay.getDisplayId(), Activity.class);
        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);

        mVirtualDevice.goToSleep();
        verify(mVirtualDisplayCallback, timeout(DISPLAY_TIMEOUT_MS).times(1)).onPaused();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_OFF);
        assertThat(mDefaultDisplayPowerManager.isInteractive()).isTrue();
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isFalse();

        mVirtualDevice.wakeUp();
        verify(mVirtualDisplayCallback, timeout(DISPLAY_TIMEOUT_MS).times(1)).onResumed();

        assertThat(mDisplay.getState()).isEqualTo(Display.STATE_ON);
        assertThat(mVirtualDisplayPowerManager.isInteractive()).isTrue();
    }

    private void assumeScreenOffSupported() {
        assumeFalse("Skipping test: Automotive main display is always on",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse("Skipping test: TVs may start screen saver instead of turning screen off",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }

    private void setScreenOffTimeoutMs(int timeoutMs) {
        setScreenOffTimeoutMs(String.valueOf(timeoutMs));
    }

    private void setScreenOffTimeoutMs(String timeoutMs) {
        Settings.System.putString(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs);
    }
}
