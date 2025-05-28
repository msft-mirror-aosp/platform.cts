/*
 * Copyright 2025 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.companion.virtual.VirtualDeviceManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.server.wm.Condition;
import android.server.wm.UiDeviceUtils;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;
import com.android.graphics.surfaceflinger.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class SurfaceFlingerTest {

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule =
            VirtualDeviceRule.withAdditionalPermissions(
                    android.Manifest.permission.WRITE_SETTINGS,
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);

    private int mInitialAodState;
    private int mInitialStayOnWhilePluggedInSetting;

    private final Context mContext = getInstrumentation().getContext();
    private final ContentResolver mContentResolver = mContext.getContentResolver();

    @Before
    public void setUp() throws Exception {
        assumeScreenOffSupported();

        mInitialAodState =
                Settings.Secure.getInt(mContentResolver, Settings.Secure.DOZE_ALWAYS_ON, 0);
        Settings.Secure.putInt(mContentResolver, Settings.Secure.DOZE_ALWAYS_ON, 0);

        mInitialStayOnWhilePluggedInSetting =
                Settings.Global.getInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        Settings.Global.putInt(mContentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
    }

    @After
    public void tearDown() {
        Settings.Secure.putInt(mContentResolver, Settings.Secure.DOZE_ALWAYS_ON, mInitialAodState);
        Settings.Global.putInt(
                mContentResolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                mInitialStayOnWhilePluggedInSetting);
        mVirtualDeviceRule.runWithoutPermissions(
                () -> {
                    UiDeviceUtils.wakeUpAndUnlock(mContext);
                    return true;
                });
    }

    /** Test that synthetic vsync is not enabled while the default display is ON. */
    @Test
    public void defaultDisplayOn_syntheticVsyncDisabled() {
        assertSyntheticVsyncDisabled();
    }

    /** Test that synthetic vsync is enabled while the default display is OFF. */
    @Test
    public void defaultDisplayOff_syntheticVsyncEnabled() {
        UiDeviceUtils.pressSleepButton();
        assertSyntheticVsyncEnabled();
    }

    /** Test that synthetic vsync is not enabled while the virtual display is ON. */
    @RequiresFlagsEnabled(Flags.FLAG_DISABLE_SYNTHETIC_VSYNC_FOR_PERFORMANCE)
    @Test
    public void virtualDisplayOn_syntheticVsyncDisabled() {
        mVirtualDeviceRule.createManagedVirtualDisplay(
                mVirtualDeviceRule.createManagedVirtualDevice(),
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());

        UiDeviceUtils.pressSleepButton();

        assertSyntheticVsyncDisabled();
    }

    /** Test that synthetic vsync is not enabled while the virtual display is OFF. */
    @RequiresFlagsEnabled({
        Flags.FLAG_DISABLE_SYNTHETIC_VSYNC_FOR_PERFORMANCE,
        android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER
    })
    @Test
    public void virtualDisplayOff_syntheticVsyncEnabled() {
        VirtualDeviceManager.VirtualDevice virtualDevice =
                mVirtualDeviceRule.createManagedVirtualDevice();
        mVirtualDeviceRule.createManagedVirtualDisplay(
                virtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());

        UiDeviceUtils.pressSleepButton();
        virtualDevice.goToSleep();

        assertSyntheticVsyncEnabled();
    }

    private void assertSyntheticVsyncEnabled() {
        assertThat(Condition.waitFor("Wait for synthetic vsync", this::isSyntheticVsyncEnabled))
                .isTrue();
    }

    private void assertSyntheticVsyncDisabled() {
        assertThat(Condition.waitFor("Wait for synthetic vsync", () -> !isSyntheticVsyncEnabled()))
                .isTrue();
    }

    private boolean isSyntheticVsyncEnabled() {
        final String dumpsys =
                SystemUtil.runShellCommandOrThrow("dumpsys SurfaceFlinger --events").trim();
        Pattern pattern =
                Pattern.compile(".*VSyncState=\\{displayId=\\d+, count=\\d+(, synthetic)?\\}.*");
        Matcher matcher = pattern.matcher(dumpsys);
        assertThat(matcher.find()).isTrue();
        return ", synthetic".equals(matcher.group(1));
    }

    private void assumeScreenOffSupported() {
        assumeFalse(
                "Skipping test: Automotive main display is always on",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(
                "Skipping test: TVs may start screen saver instead of turning screen off",
                FeatureUtil.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }
}
