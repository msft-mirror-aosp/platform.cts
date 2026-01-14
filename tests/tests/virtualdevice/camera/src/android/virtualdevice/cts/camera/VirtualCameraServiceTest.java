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

package android.virtualdevice.cts.camera;

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.virtualdevice.cts.camera.util.VirtualCameraCaptureHelper.CAMERA_HEIGHT;
import static android.virtualdevice.cts.camera.util.VirtualCameraCaptureHelper.CAMERA_WIDTH;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.grantCameraPermission;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.camera.util.VirtualCameraCaptureHelper;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RunWith(AndroidJUnit4.class)
public class VirtualCameraServiceTest {

    private static final long TIMEOUT_MILLIS = 5000L;
    private final VirtualCameraCaptureHelper mCaptureHelper = new VirtualCameraCaptureHelper();

    @Rule
    public VirtualDeviceRule mRule =
            VirtualDeviceRule.withAdditionalPermissions(GRANT_RUNTIME_PERMISSIONS);

    @Mock private VirtualCameraCallback mVirtualCameraCallback;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        VirtualDeviceManager.VirtualDevice virtualDevice =
                mRule.createManagedVirtualDevice(
                        new VirtualDeviceParams.Builder()
                                .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                                .build());
        Context virtualDeviceContext =
                getApplicationContext().createDeviceContext(virtualDevice.getDeviceId());
        mCaptureHelper.setUp(virtualDevice, virtualDeviceContext);
        grantCameraPermission(virtualDevice.getDeviceId());
    }

    @After
    public void tearDown() throws Exception {
        mCaptureHelper.tearDown();
        if (mMockitoSession != null) {
            mMockitoSession.close();
        }
    }

    @Test
    public void virtualCameraService_crash_notifiesStreamClosed() throws Exception {
        mCaptureHelper.createVirtualCamera(
                VirtualCameraCaptureHelper.createBuilderWithDefaults("TestCamera"),
                mVirtualCameraCallback);

        // Create reader and session
        try (ImageReader reader =
                ImageReader.newInstance(CAMERA_WIDTH, CAMERA_HEIGHT, YUV_420_888, 2)) {
            mCaptureHelper.createCaptureSession(List.of(reader));

            CameraDevice cameraDevice = mCaptureHelper.getOrOpenCameraDevice();
            CaptureRequest.Builder request =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(reader.getSurface());

            // Start repeating request
            mCaptureHelper.getCameraSession().setRepeatingRequest(request.build(), null, null);

            // Wait for stream configured
            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                    .onStreamConfigured(anyInt(), any(), anyInt(), anyInt(), anyInt());

            // Kill virtual_camera process
            executeShellCommand("pkill -9 virtual_camera");

            // Verify onStreamClosed is called
            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamClosed(anyInt());
        }
    }

    private void executeShellCommand(String command) throws Exception {
        ParcelFileDescriptor pfd =
                InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation()
                        .executeShellCommand(command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Read fully to ensure command execution
            }
        }
    }
}
