/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_THERMAL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.SuppressLint;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ThermalUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@SuppressLint("MissingCheckFlagsRule") // TODO: b/463342925 - remove once fixed
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_THERMAL_STATUS)
public class VirtualDeviceThermalTest {

    private static final long LISTENER_TIMEOUT_MILLIS = 5000;

    @Rule public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();

    @Mock PowerManager.OnThermalStatusChangedListener mListener;

    private AutoCloseable mMockitoSession;

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
        ThermalUtils.resetThermalStatus();
    }

    @Test
    public void virtualDeviceThermalPolicy_notDynamic() {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        assertThrows(IllegalArgumentException.class, () ->
                virtualDevice.setDevicePolicy(POLICY_TYPE_THERMAL, DEVICE_POLICY_CUSTOM));

        VirtualDevice virtualDeviceCustomThermalPolicy = createDeviceWithCustomThermalPolicy();
        assertThrows(IllegalArgumentException.class, () ->
                virtualDeviceCustomThermalPolicy.setDevicePolicy(
                        POLICY_TYPE_THERMAL, DEVICE_POLICY_DEFAULT));
    }

    @Test
    public void setCurrentThermalStatus_noCustomPolicy_throws() {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        assertThrows(UnsupportedOperationException.class, () ->
                virtualDevice.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE));
    }

    @Test
    public void setCurrentThermalStatus_invalidValue_throws() {
        VirtualDevice virtualDevice = createDeviceWithCustomThermalPolicy();
        assertThrows(IllegalArgumentException.class, () ->
                virtualDevice.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE - 1));
        assertThrows(IllegalArgumentException.class, () ->
                virtualDevice.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SHUTDOWN + 1));
    }

    @Test
    public void customThermalPolicy_thermalHeadroomNotSupported() {
        VirtualDevice virtualDevice = createDeviceWithCustomThermalPolicy();
        Context deviceContext = mContext.createDeviceContext(virtualDevice.getDeviceId());
        PowerManager powerManager = deviceContext.getSystemService(PowerManager.class);

        assertThat(powerManager.getThermalHeadroom(0)).isNaN();
        assertThrows(UnsupportedOperationException.class, () ->
                powerManager.getThermalHeadroomThresholds());
        assertThrows(UnsupportedOperationException.class, () ->
                powerManager.addThermalHeadroomListener(
                    (headroom, forecastHeadroom, forecastSeconds, thresholds) -> {}));
    }

    @Test
    public void getCurrentThermalStatus_noCustomPolicy_returnsDefaultDeviceStatus()
            throws Exception {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        Context deviceContext = mContext.createDeviceContext(virtualDevice.getDeviceId());
        PowerManager powerManager = deviceContext.getSystemService(PowerManager.class);

        ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_SEVERE);
        assertThat(powerManager.getCurrentThermalStatus())
                .isEqualTo(PowerManager.THERMAL_STATUS_SEVERE);

        ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_MODERATE);
        assertThat(powerManager.getCurrentThermalStatus())
                .isEqualTo(PowerManager.THERMAL_STATUS_MODERATE);

    }

    @Test
    public void getCurrentThermalStatus_withCustomPolicy_returnsVirtualDeviceStatus()
            throws Exception {
        ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_SEVERE); // default device

        VirtualDevice virtualDevice = createDeviceWithCustomThermalPolicy();
        Context deviceContext = mContext.createDeviceContext(virtualDevice.getDeviceId());
        PowerManager powerManager = deviceContext.getSystemService(PowerManager.class);

        // No virtual device thermal status specified, default is none
        assertThat(powerManager.getCurrentThermalStatus())
                .isEqualTo(PowerManager.THERMAL_STATUS_NONE);

        virtualDevice.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE);
        assertThat(powerManager.getCurrentThermalStatus())
                .isEqualTo(PowerManager.THERMAL_STATUS_MODERATE);

        virtualDevice.close();
        // Fallback to the default device as the thermal policy is now invalid.
        assertThat(powerManager.getCurrentThermalStatus())
                .isEqualTo(PowerManager.THERMAL_STATUS_SEVERE);
    }

    @Test
    public void thermalStatusChangedListener_noCustomPolicy_receivesDefaultDeviceStatus()
            throws Exception {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        Context deviceContext = mContext.createDeviceContext(virtualDevice.getDeviceId());
        PowerManager powerManager = deviceContext.getSystemService(PowerManager.class);

        ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_SEVERE);
        powerManager.addThermalStatusListener(mListener);
        try {
            verify(mListener, timeout(LISTENER_TIMEOUT_MILLIS))
                    .onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE);

            ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_MODERATE);
            verify(mListener, timeout(LISTENER_TIMEOUT_MILLIS))
                    .onThermalStatusChanged(PowerManager.THERMAL_STATUS_MODERATE);
        } finally {
            powerManager.removeThermalStatusListener(mListener);
        }
    }

    @Test
    public void thermalStatusChangedListener_withCustomPolicy_receivesVirtualDeviceStatus()
            throws Exception {
        ThermalUtils.overrideThermalStatus(PowerManager.THERMAL_STATUS_SEVERE); // default device

        VirtualDevice virtualDevice = createDeviceWithCustomThermalPolicy();
        Context deviceContext = mContext.createDeviceContext(virtualDevice.getDeviceId());
        PowerManager powerManager = deviceContext.getSystemService(PowerManager.class);

        powerManager.addThermalStatusListener(mListener);
        try {
            // No virtual device thermal status specified, default is none
            verify(mListener, timeout(LISTENER_TIMEOUT_MILLIS))
                    .onThermalStatusChanged(PowerManager.THERMAL_STATUS_NONE);

            virtualDevice.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE);
            verify(mListener, timeout(LISTENER_TIMEOUT_MILLIS))
                    .onThermalStatusChanged(PowerManager.THERMAL_STATUS_MODERATE);

            virtualDevice.close();
            verifyNoMoreInteractions(mListener);
        } finally {
            powerManager.removeThermalStatusListener(mListener);
        }
    }

    private VirtualDevice createDeviceWithCustomThermalPolicy() {
        return mVirtualDeviceRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_THERMAL, DEVICE_POLICY_CUSTOM)
                        .build());
    }
}
