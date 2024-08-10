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

package android.virtualdevice.cts.camera;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.FRONT_CAMERA_ID;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.grantCameraPermission;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.FeatureUtil.hasSystemFeature;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RequiresFlagsEnabled({android.companion.virtual.flags.Flags.FLAG_VIRTUAL_CAMERA,
        android.companion.virtualdevice.flags.Flags.FLAG_VIRTUAL_CAMERA_SERVICE_DISCOVERY,
        android.companion.virtualdevice.flags.Flags.FLAG_CAMERA_DEVICE_AWARENESS,
        android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraPermissionTest {

    private static final long TIMEOUT_MILLIS = 2000L;
    private static final String CAMERA_NAME = "Virtual camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_INPUT_FORMAT = PixelFormat.RGBA_8888;
    private static final int CAMERA_MAX_FPS = 30;

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            GRANT_RUNTIME_PERMISSIONS).withVirtualCameraSupportCheck();

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;

    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private int mVirtualDisplayId;
    private CameraManager mCameraManager;

    @Before
    public void setUp() {
        assumeFalse("Skipping VirtualCamera E2E test on automotive platform.",
                    hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        createVirtualCamera();
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
        Context virtualDeviceContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        mCameraManager = virtualDeviceContext.getSystemService(CameraManager.class);
    }

    @Test
    public void openCamera_withoutPermission_fails() throws Exception {
        assertThrows(SecurityException.class,
                () -> mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(),
                        mCameraStateCallback));
    }

    @Test
    public void openCamera_withPermission_succeeds() throws Exception {
        VirtualCameraUtils.grantCameraPermission(mVirtualDevice.getDeviceId());

        mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(),
                mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(FRONT_CAMERA_ID);
    }

    @Test
    public void defaultDevice_hasCameraPermissionByDefault() throws Exception {
        assertThat(getPermissionState(Display.DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void virtualDevice_doesNotHaveCameraPermissionByDefault() throws Exception {
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void virtualDevice_cameraPermissionGranted() throws Exception {
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        grantCameraPermission(mVirtualDevice.getDeviceId());
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    private void createVirtualCamera() {
        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_INPUT_FORMAT, CAMERA_MAX_FPS, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);
        try {
            mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }
    }

    private int getPermissionState(int displayId) {
        Activity activity = mRule.startActivityOnDisplaySync(displayId, Activity.class);
        int permissionState = activity.checkSelfPermission(CAMERA);
        activity.finish();
        return permissionState;
    }
}
