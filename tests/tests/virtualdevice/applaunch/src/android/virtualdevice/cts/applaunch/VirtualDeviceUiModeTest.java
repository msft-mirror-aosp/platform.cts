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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.app.Activity;
import android.app.UiModeManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Tests for device-aware UI mode. */
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_UI_MODE)
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceUiModeTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private int mDisplayId;

    private int mDefaultUiModeType;
    private int mNewUiModeType;
    private int mDefaultNightMode;
    private int mNewNightMode;

    private UiModeManager mDefaultUiModeManager;
    private UiModeManager mVirtualUiModeManager;

    @Mock private Consumer<Integer> mUiModeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mVirtualDevice = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay =
                mRule.createManagedVirtualDisplay(
                        mVirtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        Display display = virtualDisplay.getDisplay();
        mDisplayId = display.getDisplayId();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mDefaultUiModeManager = context.getSystemService(UiModeManager.class);
        mVirtualUiModeManager =
                context.createDisplayContext(display).getSystemService(UiModeManager.class);

        int uiMode = context.getResources().getConfiguration().uiMode;
        mDefaultUiModeType = getUiModeType(uiMode);
        mDefaultNightMode = getNightMode(uiMode);

        mNewUiModeType =
                mDefaultUiModeType == Configuration.UI_MODE_TYPE_APPLIANCE
                        ? Configuration.UI_MODE_TYPE_NORMAL
                        : Configuration.UI_MODE_TYPE_APPLIANCE;
        mNewNightMode =
                mDefaultNightMode == Configuration.UI_MODE_NIGHT_NO
                        ? Configuration.UI_MODE_NIGHT_YES
                        : Configuration.UI_MODE_NIGHT_NO;
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode",
            "android.app.UiModeManager#getCurrentModeType",
            "android.app.UiModeManager#getNightMode"})
    @Test
    public void displayAwareUiModeManager() {
        mDefaultUiModeType = mDefaultUiModeManager.getCurrentModeType();
        mDefaultNightMode = mDefaultUiModeManager.getNightMode();

        assertThat(mVirtualUiModeManager.getCurrentModeType()).isEqualTo(mDefaultUiModeType);
        assertThat(mVirtualUiModeManager.getNightMode()).isEqualTo(mDefaultNightMode);

        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewUiModeType | mNewNightMode);

        assertThat(mVirtualUiModeManager.getCurrentModeType()).isEqualTo(mNewUiModeType);
        assertThat(mVirtualUiModeManager.getNightMode()).isEqualTo(mNewNightMode);

        assertThat(mDefaultUiModeManager.getCurrentModeType()).isEqualTo(mDefaultUiModeType);
        assertThat(mDefaultUiModeManager.getNightMode()).isEqualTo(mDefaultNightMode);

        mVirtualDevice.setDisplayUiMode(mDisplayId, Configuration.UI_MODE_TYPE_UNDEFINED);

        assertThat(mVirtualUiModeManager.getCurrentModeType()).isEqualTo(mDefaultUiModeType);
        assertThat(mVirtualUiModeManager.getNightMode()).isEqualTo(mDefaultNightMode);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeType_unownedDisplay_throws() {
        VirtualDisplay unownedDisplay = mRule.createManagedUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> mVirtualDevice.setDisplayUiMode(unownedDisplay.getDisplay().getDisplayId(),
                        mNewUiModeType | mNewNightMode));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeType_untrustedDisplay_throws() {
        VirtualDisplay untrustedDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertThrows(SecurityException.class,
                () -> mVirtualDevice.setDisplayUiMode(untrustedDisplay.getDisplay().getDisplayId(),
                        mNewUiModeType | mNewNightMode));
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeType_triggersConfigChange() {
        launchActivityOnDisplay(mDisplayId);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewUiModeType);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS)).accept(mNewUiModeType | mDefaultNightMode);

        mVirtualDevice.setDisplayUiMode(mDisplayId, Configuration.UI_MODE_TYPE_UNDEFINED);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS))
                .accept(mDefaultUiModeType | mDefaultNightMode);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayNightMode_triggersConfigChange() {
        launchActivityOnDisplay(mDisplayId);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewNightMode);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS)).accept(mDefaultUiModeType | mNewNightMode);

        mVirtualDevice.setDisplayUiMode(mDisplayId, Configuration.UI_MODE_TYPE_UNDEFINED);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS))
                .accept(mDefaultUiModeType | mDefaultNightMode);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeTypeAndNightMode_triggersConfigChange() {
        launchActivityOnDisplay(mDisplayId);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewUiModeType | mNewNightMode);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS)).accept(mNewUiModeType | mNewNightMode);

        mVirtualDevice.setDisplayUiMode(mDisplayId, Configuration.UI_MODE_TYPE_UNDEFINED);
        verify(mUiModeListener, timeout(TIMEOUT_MILLIS))
                .accept(mDefaultUiModeType | mDefaultNightMode);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeTypeAndNightModeToCurrent_doesNotTriggerConfigChange() {
        launchActivityOnDisplay(mDisplayId);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mDefaultUiModeType);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mDefaultNightMode);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mDefaultUiModeType | mDefaultNightMode);
        mVirtualDevice.setDisplayUiMode(mDisplayId, Configuration.UI_MODE_TYPE_UNDEFINED);
        verifyNoInteractions(mUiModeListener);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeTypeAndNightMode_doesNotAffectOtherDisplays() {
        launchActivityOnDisplay(Display.DEFAULT_DISPLAY);
        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewUiModeType | mNewNightMode);
        verifyNoInteractions(mUiModeListener);
    }

    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceManager.VirtualDevice#setDisplayUiMode"})
    @Test
    public void setDisplayUiModeTypeAndNightMode_newActivityLaunch() {
        mVirtualDevice.setDisplayUiMode(mDisplayId, mNewUiModeType | mNewNightMode);
        UiModeActivity activity = launchActivityOnDisplay(mDisplayId);
        assertThat(activity.mUiMode).isEqualTo(mNewUiModeType | mNewNightMode);
        verifyNoInteractions(mUiModeListener);
    }

    private UiModeActivity launchActivityOnDisplay(int displayId) {
        UiModeActivity activity = mRule.startActivityOnDisplaySync(displayId, UiModeActivity.class);
        activity.mListener = mUiModeListener;
        return activity;
    }

    private static int getUiModeType(int uiMode) {
        return uiMode & Configuration.UI_MODE_TYPE_MASK;
    }

    private static int getNightMode(int uiMode) {
        return uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    /** Activity listening to UI mode changes. */
    public static class UiModeActivity extends Activity {

        private int mUiMode;
        private Consumer<Integer> mListener;

        @Override
        public void onResume() {
            super.onResume();
            mUiMode = getResources().getConfiguration().uiMode;
        }

        @Override
        public void onConfigurationChanged(Configuration configuration) {
            super.onConfigurationChanged(configuration);
            if (configuration.uiMode != mUiMode) {
                mUiMode = configuration.uiMode;
                assertThat(mListener).isNotNull();
                mListener.accept(mUiMode);
            }
        }
    }
}
