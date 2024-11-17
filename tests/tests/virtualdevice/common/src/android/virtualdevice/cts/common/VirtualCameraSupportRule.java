/*
 * Copyright 2024 The Android Open Source Project
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

package android.virtualdevice.cts.common;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.FeatureUtil.hasSystemFeature;

import static org.junit.Assume.assumeTrue;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.pm.PackageManager;
import android.hardware.HardwareBuffer;
import android.util.Log;

import androidx.annotation.Nullable;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Internal rule that checks whether virtual camera is supported by the device, before executing
 * any test.
 */
public final class VirtualCameraSupportRule implements TestRule {
    private static final int VIRTUAL_CAMERA_SUPPORT_UNKNOWN = 0;
    private static final int VIRTUAL_CAMERA_SUPPORT_AVAILABLE = 1;
    private static final int VIRTUAL_CAMERA_SUPPORT_NOT_AVAILABLE = 2;
    private static final String TAG = "VirtualCameraSupport";

    private static int sVirtualCameraSupport = VIRTUAL_CAMERA_SUPPORT_UNKNOWN;

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Nullable
    private final VirtualDeviceRule mVirtualDeviceRule;

    public VirtualCameraSupportRule() {
        mVirtualDeviceRule = null;
    }

    public VirtualCameraSupportRule(@Nullable VirtualDeviceRule virtualDeviceRule) {
        mVirtualDeviceRule = virtualDeviceRule;
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        MockitoAnnotations.initMocks(this);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeHardwareSupportsVirtualCamera();
                assumeTrue("Virtual camera not instantiable on this device",
                        getVirtualCameraSupport() != VIRTUAL_CAMERA_SUPPORT_NOT_AVAILABLE);
                statement.evaluate();
            }
        };
    }

    private int getVirtualCameraSupport() {
        if (sVirtualCameraSupport != VIRTUAL_CAMERA_SUPPORT_UNKNOWN) {
            return sVirtualCameraSupport;
        }

        if (mVirtualDeviceRule == null) {
            return VIRTUAL_CAMERA_SUPPORT_UNKNOWN;
        }

        try (VirtualDeviceManager.VirtualDevice virtualDevice =
                     mVirtualDeviceRule.createManagedVirtualDevice(
                             new VirtualDeviceParams.Builder().setDevicePolicy(POLICY_TYPE_CAMERA,
                                     DEVICE_POLICY_CUSTOM).build())) {
            VirtualCameraConfig config = new VirtualCameraConfig.Builder("dummycam")
                    .setVirtualCameraCallback(getApplicationContext().getMainExecutor(),
                            mVirtualCameraCallback)
                    .addStreamConfig(640, 480, YUV_420_888, 30)
                    .setLensFacing(LENS_FACING_BACK)
                    .build();
            try (VirtualCamera ignored = virtualDevice.createVirtualCamera(config)) {
                sVirtualCameraSupport = VIRTUAL_CAMERA_SUPPORT_AVAILABLE;
            } catch (UnsupportedOperationException e) {
                sVirtualCameraSupport = VIRTUAL_CAMERA_SUPPORT_NOT_AVAILABLE;
            }
        }
        return sVirtualCameraSupport;
    }

    private static boolean isVirtualCameraSupported() {
        sVirtualCameraSupport = VIRTUAL_CAMERA_SUPPORT_NOT_AVAILABLE;
        // Not supported on automotive
        if (hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(TAG, "Virtual Camera is not supported on Automotive");
            return false;
        }

        // Not supported on devices with GPU which can't read/write YUV
        long usage =
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE // Same as GraphicBuffer.USAGE_HW_TEXTURE
                        | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT; // Same as USAGE_GPU_FRAMEBUFFER
        if (!HardwareBuffer.isSupported(1920, 1080, HardwareBuffer.YCBCR_420_888, 1, usage)) {
            Log.i(TAG, "Virtual Camera is not supported: cannot write YUV buffer.");
            return false;
        }

        sVirtualCameraSupport = VIRTUAL_CAMERA_SUPPORT_UNKNOWN;
        return true;
    }

    /**
     * Skip the test if the current device does not support features required by virtual camera.
     */
    private void assumeHardwareSupportsVirtualCamera() {
        Assume.assumeTrue(
                "Virtual Camera not supported by this hardware. Check the log for more details.",
                isVirtualCameraSupported());
    }
}
