/*
 * Copyright 2023 The Android Open Source Project
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
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_180;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_270;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_90;
import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.graphics.ImageFormat.RGB_565;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.BACK_CAMERA_ID;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.FRONT_CAMERA_ID;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.assertVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.grantCameraPermission;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.CameraCharacteristicsBuilder;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraSessionConfig;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.ArrayMap;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(JUnitParamsRunner.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraTest {

    private static final long TIMEOUT_MILLIS = 2000L;
    private static final String CAMERA_NAME = "Virtual camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = YUV_420_888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final long CAMERA_MIN_FRAME_DURATION = 1_000_000_000L;
    private static final int CAMERA_SENSOR_ORIENTATION = SENSOR_ORIENTATION_0;
    private static final int CAMERA_LENS_FACING = LENS_FACING_FRONT;
    private static final int IMAGE_READER_MAX_IMAGES = 2;
    private static final Range<Integer> CAMERA_FPS_RANGE = new Range<>(10, 20);

    @Rule
    public VirtualDeviceRule mRule =
            VirtualDeviceRule.withAdditionalPermissions(GRANT_RUNTIME_PERMISSIONS);

    @Mock
    private CameraManager.AvailabilityCallback mMockDefaultContextCameraAvailabilityCallback;

    @Mock
    private CameraManager.AvailabilityCallback mMockVdContextCameraAvailabilityCallback;

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;

    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;

    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;

    @Captor private ArgumentCaptor<VirtualCameraSessionConfig> mVirtualCameraSessionConfigCaptor;

    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;

    @Captor
    private ArgumentCaptor<Integer> mWidthCaptor;

    @Captor
    private ArgumentCaptor<Integer> mHeightCaptor;

    @Captor
    private ArgumentCaptor<Integer> mFormatCaptor;

    private CameraManager mCameraManager;
    private VirtualDevice mVirtualDevice;
    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        grantCameraPermission(mVirtualDevice.getDeviceId());
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
        if (mCameraManager != null) {
            mCameraManager.unregisterAvailabilityCallback(
                    mMockDefaultContextCameraAvailabilityCallback);
            mCameraManager.unregisterAvailabilityCallback(mMockVdContextCameraAvailabilityCallback);
        }
    }

    @Test
    public void getConfig_returnsCorrectConfig() {
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        VirtualCameraConfig config = virtualCamera.getConfig();
        assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }

    @Test
    public void defaultContext_withVirtualCamera_doesNotTriggerCameraAvailabilityCallbacks() {
        setupDefaultDeviceCameraManager();
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        String virtualCameraId = virtualCamera.getId();
        verify(mMockDefaultContextCameraAvailabilityCallback, after(TIMEOUT_MILLIS).never())
                .onCameraAvailable(virtualCameraId);

        virtualCamera.close();
        verify(mMockDefaultContextCameraAvailabilityCallback, after(TIMEOUT_MILLIS).never())
                .onCameraUnavailable(virtualCameraId);
    }

    @Test
    public void vdContext_withoutVirtualCamera_doesNotTriggerCameraAvailabilityCallbacks() {
        setupVirtualDeviceCameraManager();

        verify(mMockVdContextCameraAvailabilityCallback, after(TIMEOUT_MILLIS).never())
                .onCameraAvailable(any());
        verify(mMockVdContextCameraAvailabilityCallback, after(TIMEOUT_MILLIS).never())
                .onCameraUnavailable(any());
    }

    @Test
    public void vdContext_withVirtualFrontCamera_triggersCameraAvailabilityCallbacks() {
        setupVirtualDeviceCameraManager();
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        verify(mMockVdContextCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraAvailable(FRONT_CAMERA_ID);

        virtualCamera.close();
        verify(mMockVdContextCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraUnavailable(FRONT_CAMERA_ID);
    }

    @Test
    public void vdContext_withVirtualBackCamera_triggersCameraAvailabilityCallbacks() {
        VirtualCamera virtualCamera = createVirtualCamera(LENS_FACING_BACK);
        setupVirtualDeviceCameraManager();

        verify(mMockVdContextCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraAvailable(BACK_CAMERA_ID);

        virtualCamera.close();
        verify(mMockVdContextCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraUnavailable(BACK_CAMERA_ID);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void defaultContext_withVirtualExternalCamera_triggersCameraAvailabilityCallbacks() {
        // Create virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();
        setupDefaultDeviceCameraManager();
        VirtualCamera virtualCamera = createVirtualCamera(LENS_FACING_EXTERNAL);

        verify(mMockDefaultContextCameraAvailabilityCallback,
                timeout(TIMEOUT_MILLIS)).onCameraAvailable(
                not(or(eq(FRONT_CAMERA_ID), eq(BACK_CAMERA_ID))));

        virtualCamera.close();
        verify(mMockDefaultContextCameraAvailabilityCallback,
                timeout(TIMEOUT_MILLIS)).onCameraUnavailable(
                not(or(eq(FRONT_CAMERA_ID), eq(BACK_CAMERA_ID))));
    }

    @Test
    public void getCameraIdList_withDefaultContext_withVirtualCamera_doesNotIncludeVirtualCamera()
        throws Exception {
        setupDefaultDeviceCameraManager();
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .doesNotContain(virtualCamera.getId());
    }

    @Test
    public void getCameraIdList_withVdContext_withoutVirtualCamera_returnsEmptyList() throws Exception {
        setupVirtualDeviceCameraManager();

        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList()).isEmpty();
    }

    @Test
    public void getCameraIdList_withVdContext_withVirtualFrontCamera_includesOnlyVirtualCamera()
        throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .contains(FRONT_CAMERA_ID);
    }

    @Test
    public void getCameraIdList_withVdContext_withVirtualBackCamera_includesOnlyVirtualCamera()
        throws Exception {
        setupVirtualDeviceCameraManager();
        createVirtualCamera(LENS_FACING_BACK);

        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .contains(BACK_CAMERA_ID);
    }

    @Test
    public void defaultPolicyVdContext_canAccessDefaultCameras() throws Exception {
        setupDefaultDeviceCameraManager();
        String[] defaultCameraIds = mCameraManager.getCameraIdListNoLazy();
        // Create another virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();
        setupVirtualDeviceCameraManager();

        String[] cameraIds = mCameraManager.getCameraIdListNoLazy();
        assertThat(cameraIds).isEqualTo(defaultCameraIds);
    }

    @Test
    public void defaultPolicyVdContext_cannotAccessVirtualCamera() throws Exception {
        setupDefaultDeviceCameraManager();
        String[] defaultCameraIds = mCameraManager.getCameraIdListNoLazy();

        // Create another virtual device with default camera policy.
        VirtualDevice defaultPolicyVd = mRule.createManagedVirtualDevice();
        setupCameraManagerForDeviceId(defaultPolicyVd.getDeviceId());
        createFrontVirtualCamera();

        String[] cameraIds = mCameraManager.getCameraIdListNoLazy();
        assertThat(cameraIds).isEqualTo(defaultCameraIds);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getCameraIdList_withInvalidDeviceIdInContext_returnsEmptyList() throws Exception {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        String[] cameraIds = mCameraManager.getCameraIdList();
        assertThat(cameraIds).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getCameraIdListNoLazy_withInvalidDeviceIdInContext_returnsEmptyList()
            throws Exception {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        String[] cameraIds = mCameraManager.getCameraIdListNoLazy();
        assertThat(cameraIds).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getConcurrentCameraIds_withInvalidDeviceIdInContext_returnsEmptySet()
            throws Exception {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        Set<Set<String>> concurrentCameraIds = mCameraManager.getConcurrentCameraIds();
        assertThat(concurrentCameraIds).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void openCamera_withInvalidDeviceIdInContext_throwsException(String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.openCamera(cameraId, directExecutor(), mCameraStateCallback));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void getCameraCharacteristics_withInvalidDeviceIdInContext_throwsException(
            String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.getCameraCharacteristics(cameraId));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void isCameraDeviceSetupSupported_withInvalidDeviceIdInContext_throwsException(
            String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.isCameraDeviceSetupSupported(cameraId));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void getCameraDeviceSetup_withInvalidDeviceIdInContext_throwsException(String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.getCameraDeviceSetup(cameraId));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void isCameraDeviceSharingSupported_withInvalidDeviceIdInContext_throwsException(
            String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.isCameraDeviceSharingSupported(cameraId));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void setTorchMode_withInvalidDeviceIdInContext_throwsException(String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.setTorchMode(cameraId, true /* enabled */));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllCameraIds")
    public void turnOnTorchWithStrengthLevel_withInvalidDeviceIdInContext_throwsException(
            String cameraId) {
        setupVirtualDeviceCameraManager();
        mVirtualDevice.close();

        assertThrows(
                IllegalArgumentException.class,
                () -> mCameraManager.turnOnTorchWithStrengthLevel(cameraId, 1 /* torchStrength */));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getNumberOfCameras_withInvalidDeviceIdInContext_throwsException() {
        mVirtualDevice = mRule.createManagedVirtualDevice();
        Context vdContext =
                getApplicationContext().createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDevice.close();

        assertThat(Camera.getNumberOfCameras(vdContext)).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllLegacyCameraIds")
    public void getCameraInfo_withInvalidDeviceIdInContext_throwsException(int cameraId) {
        mVirtualDevice = mRule.createManagedVirtualDevice();
        Context vdContext =
                getApplicationContext().createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDevice.close();

        Camera.CameraInfo info = new Camera.CameraInfo();
        assertThrows(
                RuntimeException.class,
                () ->
                        Camera.getCameraInfo(
                                cameraId, vdContext, CameraManager.ROTATION_OVERRIDE_NONE, info));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    @Parameters(method = "getAllLegacyCameraIds")
    public void open_withInvalidDeviceIdInContext_throwsException(int cameraId) {
        mVirtualDevice = mRule.createManagedVirtualDevice();
        Context vdContext =
                getApplicationContext().createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDevice.close();

        assertThrows(
                RuntimeException.class,
                () -> Camera.open(cameraId, vdContext, CameraManager.ROTATION_OVERRIDE_NONE));
    }

    @Parameters(method = "getAllSensorOrientations")
    @Test
    public void virtualCamera_hasCorrectOrientation(int sensorOrientation)
            throws Exception {
        setupVirtualDeviceCameraManager();
        createVirtualCameraWithSensorOrientation(sensorOrientation);

        verifyCameraSensorOrientation(FRONT_CAMERA_ID, sensorOrientation);
    }

    @Test
    public void virtualCamera_hasCorrectMinFrameDuration() throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        verifyCameraMaximumFramesPerSecond(FRONT_CAMERA_ID, CAMERA_MAX_FPS);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void virtualCamera_hasCorrectLensFacing(int lensFacing) throws Exception {
        setupVirtualDeviceCameraManager();
        createVirtualCamera(lensFacing);

        String cameraId = getCameraIdForLensFacing(lensFacing);

        verifyCameraLensFacing(cameraId, lensFacing);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void createMultipleVirtualCameras_withSameLensFacing_failsNonExternal(int lensFacing) {
        setupDefaultDeviceCameraManager();
        createVirtualCamera(lensFacing);

        // Creating another camera with same lens facing should fail for FRONT and BACK lens facing.
        if (lensFacing == LENS_FACING_BACK || lensFacing == LENS_FACING_FRONT) {
            assertThrows(IllegalArgumentException.class, () -> createVirtualCamera(lensFacing));
        } else {
            // allow multiple external cameras
            createVirtualCamera(lensFacing);
        }
    }

    @Test
    public void createVirtualCamera_withDefaultPolicy_fails() {
        // Create virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();

        assertThrows(IllegalArgumentException.class, () -> createVirtualCamera(LENS_FACING_FRONT));
        assertThrows(IllegalArgumentException.class, () -> createVirtualCamera(LENS_FACING_BACK));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void createExternalVirtualCamera_withDefaultPolicy_fails() {
        // Create virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();

        assertThrows(IllegalArgumentException.class,
                () -> createVirtualCamera(LENS_FACING_EXTERNAL));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void createExternalVirtualCamera_withDefaultPolicy_succeeds() throws Exception {
        // Create virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();
        setupDefaultDeviceCameraManager();
        List<String> defaultCameraIds = Arrays.asList(mCameraManager.getCameraIdListNoLazy());

        createVirtualCamera(LENS_FACING_EXTERNAL);

        List<String> cameraIds = new ArrayList<>(
                Arrays.asList(mCameraManager.getCameraIdListNoLazy()));
        cameraIds.removeAll(defaultCameraIds);

        // only one added virtual external camera on the default device
        assertThat(cameraIds).hasSize(1);
    }

    @Test
    public void vdContext_withVirtualCamera_openCamera_triggersOnOpenedCallback() throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(), mCameraStateCallback);

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(FRONT_CAMERA_ID);
    }

    @Test
    public void vdContext_withVirtualCamera_close_triggersOnDisconnectedCallback() throws Exception {
        setupVirtualDeviceCameraManager();
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(), mCameraStateCallback);
        virtualCamera.close();

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS))
                .onDisconnected(mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(FRONT_CAMERA_ID);
    }

    @Test
    public void vdContext_withVirtualCamera_cameraDeviceClose_triggersOnClosedCallback()
            throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(), mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        mCameraDeviceCaptor.getValue().close();

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onClosed(
                mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(FRONT_CAMERA_ID);
    }

    @Test
    public void defaultContext_withVirtualCamera_openCamera_throwsException() {
        setupDefaultDeviceCameraManager();
        VirtualCamera virtualCamera = createFrontVirtualCamera();

        assertThrows(IllegalArgumentException.class, () ->
                mCameraManager.openCamera(virtualCamera.getId(), directExecutor(),
                        mCameraStateCallback));
    }

    @Test
    public void vdContext_withVirtualCamera_configureSessionForSupportedFormat_succeeds()
            throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        verifyConfigureSessionForSupportedFormatSucceeds(FRONT_CAMERA_ID);
    }

    @Test
    public void vdContext_withVirtualCamera_configureSessionForUnsupportedFormat_fails()
            throws Exception {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        verifyConfigureSessionForUnsupportedFormatFails(FRONT_CAMERA_ID);
    }

    @Test
    public void defaultContext_getNumberOfCameras_doesNotIncludeVirtualCamera() {
        int numberOfCamerasBeforeVirtualCamera = Camera.getNumberOfCameras();
        createFrontVirtualCamera();
        int numberOfCamerasAfterVirtualCamera = Camera.getNumberOfCameras();

        assertThat(numberOfCamerasAfterVirtualCamera).isEqualTo(numberOfCamerasBeforeVirtualCamera);
    }

    @Test
    public void defaultPolicyVdContext_getNumberOfCameras_includesDefaultCameras() {
        int defaultNumCameras = Camera.getNumberOfCameras();

        // Create another virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();
        Context vdContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        assertThat(Camera.getNumberOfCameras(vdContext)).isEqualTo(defaultNumCameras);
    }

    @Test
    public void defaultPolicyVdContext_getNumberOfCameras_doesNotIncludeVirtualCamera() {
        int numberOfCamerasBeforeVirtualCamera = Camera.getNumberOfCameras();

        createFrontVirtualCamera();

        // Create another virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();
        assertThat(Camera.getNumberOfCameras(mVirtualDevice.createContext()))
                .isEqualTo(numberOfCamerasBeforeVirtualCamera);
    }

    @Test
    public void vdContext_getNumberOfCameras_includesOnlyVirtualCamera() {
        createFrontVirtualCamera();

        Context vdContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        assertThat(Camera.getNumberOfCameras(vdContext)).isEqualTo(1);
    }

    @Test
    public void vdContext_getCameraInfo_returnsVirtualCameraInfo() {
        createFrontVirtualCamera();

        Context vdContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        assertThat(Camera.getNumberOfCameras(vdContext)).isEqualTo(1);

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(/* cameraId= */ 0, vdContext, CameraManager.ROTATION_OVERRIDE_NONE,
                info);
        assertThat(info.facing).isEqualTo(Camera.CameraInfo.CAMERA_FACING_FRONT);
        assertThat(info.orientation).isEqualTo(SENSOR_ORIENTATION_0);
    }

    @Test
    public void vdContext_legacyCameraPreview_withVirtualCamera_succeeds() throws Exception {
        createFrontVirtualCamera();

        Context vdContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        assertThat(Camera.getNumberOfCameras(vdContext)).isEqualTo(1);
        try (ImageReader imageReader = createImageReader(YUV_420_888)) {
            Camera camera = null;
            try {
                camera = Camera.open(/* cameraId= */ 0, vdContext,
                        CameraManager.ROTATION_OVERRIDE_NONE);
                camera.setPreviewSurface(imageReader.getSurface());

                camera.startPreview();
                verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamConfigured(anyInt(),
                        mSurfaceCaptor.capture(), mWidthCaptor.capture(), mHeightCaptor.capture(),
                        mFormatCaptor.capture());
                assertThat(mSurfaceCaptor.getValue().isValid()).isTrue();
                assertThat(mWidthCaptor.getValue()).isEqualTo(CAMERA_WIDTH);
                assertThat(mHeightCaptor.getValue()).isEqualTo(CAMERA_HEIGHT);
                assertThat(mFormatCaptor.getValue()).isEqualTo(YUV_420_888);
            } finally {
                if (camera != null) {
                    camera.release();
                    verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                            .onStreamClosed(anyInt());
                }
            }
        }
    }

    @Test
    public void virtualCamera_supportsMandatoryCaptureUseCases() throws Exception {
        setupVirtualDeviceCameraManager();
        try (VirtualCamera camera = createFrontVirtualCamera()) {
            long[] availableUseCases = mCameraManager.getCameraCharacteristics(
                    FRONT_CAMERA_ID).get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES);
            assertThat(availableUseCases).asList().containsExactly(
                    (long) CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT,
                    (long) CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
                    (long) CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
                    (long) CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
                    (long) CameraCharacteristics
                            .SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL,
                    (long) CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL);
        }
    }

    @Test
    public void getConcurrentCameraIds_withSingleVirtualCamera_returnsEmpty() throws Exception {
        createFrontVirtualCamera();
        setupVirtualDeviceCameraManager();

        Set<Set<String>> combinations = mCameraManager.getConcurrentCameraIds();
        assertThat(combinations).isEmpty();
    }

    @Test
    public void getConcurrentCameraIds_withMultipleVirtualCameras_returnsEmpty() throws Exception {
        createFrontVirtualCamera();
        createVirtualCamera(LENS_FACING_BACK);
        setupVirtualDeviceCameraManager();

        Set<Set<String>> combinations = mCameraManager.getConcurrentCameraIds();
        assertThat(combinations).isEmpty();
    }

    @Test
    public void isConcurrentSessionConfigurationSupported_withVirtualCamera_returnsFalse()
            throws Exception {
        createFrontVirtualCamera();
        createVirtualCamera(LENS_FACING_BACK);
        setupVirtualDeviceCameraManager();

        Map<String, SessionConfiguration> cameraIdSessionConfigMap = new ArrayMap<>();
        ArrayList<OutputConfiguration> outConfigs = new ArrayList<>();
        outConfigs.add(new OutputConfiguration(new Size(1, 1), SurfaceTexture.class));
        cameraIdSessionConfigMap.put(FRONT_CAMERA_ID,
                new SessionConfiguration(SESSION_REGULAR, outConfigs, mExecutor,
                        mSessionStateCallback));
        cameraIdSessionConfigMap.put(BACK_CAMERA_ID,
                new SessionConfiguration(SESSION_REGULAR, outConfigs, mExecutor,
                        mSessionStateCallback));
        assertThat(
                mCameraManager.isConcurrentSessionConfigurationSupported(
                        cameraIdSessionConfigMap)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_ON_OPEN)
    public void onOpenCamera_called() throws CameraAccessException {
        setupVirtualDeviceCameraManager();
        createFrontVirtualCamera();

        VirtualCameraCallback otherCallback = Mockito.mock(VirtualCameraCallback.class);

        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, LENS_FACING_BACK,
                "camera2", mExecutor, otherCallback);

        mVirtualDevice.createVirtualCamera(config);

        mCameraManager.openCamera(FRONT_CAMERA_ID, directExecutor(), mCameraStateCallback);

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS))
                .onOpened(mCameraDeviceCaptor.capture());
        verify(mVirtualCameraCallback, times(1)).onOpenCamera();
        verify(otherCallback, never()).onOpenCamera();
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void createVirtualCamera_withCameraCharacteristics_succeeds(int lensFacing)
            throws Exception {
        setupVirtualDeviceCameraManager();

        createVirtualCameraWithCharacteristics(createDefaultCameraCharacteristics(lensFacing));

        verifyConfigureSessionForSupportedFormatSucceeds(getCameraIdForLensFacing(lensFacing));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void createVirtualCamera_withPerFrameMetadataEnabled_succeeds() throws Exception {
        setupVirtualDeviceCameraManager();

        VirtualCameraConfig config =
                new VirtualCameraConfig.Builder("FrameMetadataCamera")
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mVirtualCameraCallback)
                        .setSensorOrientation(SENSOR_ORIENTATION_180)
                        .setLensFacing(LENS_FACING_BACK)
                        .setPerFrameCameraMetadataEnabled(true)
                        .build();
        mVirtualDevice.createVirtualCamera(config);

        verifyConfigureSessionForSupportedFormatSucceeds(BACK_CAMERA_ID);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void createCharacteristicsVirtualCamera_withSessionParameters_succeeds(int lensFacing)
            throws Exception {
        List<CaptureRequest.Key<?>> availableSessionKeys =
                List.of(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);

        setupVirtualDeviceCameraManager();

        CameraCharacteristics characteristics =
                new CameraCharacteristicsBuilder(createDefaultCameraCharacteristics(lensFacing))
                        .setAvailableSessionKeys(availableSessionKeys)
                        .build();

        createVirtualCameraWithCharacteristics(characteristics);

        mCameraManager.openCamera(
                getCameraIdForLensFacing(lensFacing), mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS))
                .onOpened(mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();
        CameraCharacteristics cameraCharacteristics =
                mCameraManager.getCameraCharacteristics(cameraDevice.getId());

        assertTrue(
                cameraCharacteristics.getAvailableSessionKeys() != null
                        && cameraCharacteristics
                                .getAvailableSessionKeys()
                                .containsAll(availableSessionKeys));

        try (ImageReader reader = createImageReader(YUV_420_888)) {
            SessionConfiguration requestedSessionConfiguration = createSessionConfig(reader);

            CaptureRequest.Builder captureRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, CAMERA_FPS_RANGE);

            CaptureRequest captureRequest = captureRequestBuilder.build();
            requestedSessionConfiguration.setSessionParameters(captureRequest);

            cameraDevice.createCaptureSession(requestedSessionConfiguration);

            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                    .onConfigureSession(mVirtualCameraSessionConfigCaptor.capture(), any());

            CaptureRequest sessionParameters =
                    mVirtualCameraSessionConfigCaptor.getValue().getSessionParameters();
            assertTrue(sessionParameters.getKeys().containsAll(availableSessionKeys));

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS))
                    .onConfigured(mCameraCaptureSessionCaptor.capture());
            CameraCaptureSession cameraCaptureSession = mCameraCaptureSessionCaptor.getValue();

            cameraCaptureSession.close();
        }
        cameraDevice.close();

        verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamClosed(anyInt());
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void createDefaultVirtualCamera_withSessionParameters_succeeds(int lensFacing)
            throws Exception {
        setupVirtualDeviceCameraManager();

        VirtualCameraConfig config =
                new VirtualCameraConfig.Builder("SessionMetadataCamera")
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mVirtualCameraCallback)
                        .setSensorOrientation(SENSOR_ORIENTATION_180)
                        .setLensFacing(lensFacing)
                        .build();

        mVirtualDevice.createVirtualCamera(config);
        mCameraManager.openCamera(
                getCameraIdForLensFacing(lensFacing), mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS))
                .onOpened(mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();
        CameraCharacteristics cameraCharacteristics =
                mCameraManager.getCameraCharacteristics(cameraDevice.getId());

        // Available session configuration keys are not set by default
        assertNull(cameraCharacteristics.getAvailableSessionKeys());

        try (ImageReader reader = createImageReader(YUV_420_888)) {
            SessionConfiguration requestedSessionConfiguration = createSessionConfig(reader);

            CaptureRequest.Builder captureRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, CAMERA_FPS_RANGE);

            CaptureRequest captureRequest = captureRequestBuilder.build();
            requestedSessionConfiguration.setSessionParameters(captureRequest);

            cameraDevice.createCaptureSession(requestedSessionConfiguration);

            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                    .onConfigureSession(mVirtualCameraSessionConfigCaptor.capture(), any());

            // even if set, the session params are filtered out
            CaptureRequest sessionParameters =
                    mVirtualCameraSessionConfigCaptor.getValue().getSessionParameters();
            assertTrue(sessionParameters == null || sessionParameters.getKeys().isEmpty());

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS))
                    .onConfigured(mCameraCaptureSessionCaptor.capture());
            CameraCaptureSession cameraCaptureSession = mCameraCaptureSessionCaptor.getValue();

            cameraCaptureSession.close();
        }
        cameraDevice.close();

        verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamClosed(anyInt());
    }

    private VirtualCamera createFrontVirtualCamera() {
        return createVirtualCamera(LENS_FACING_FRONT);
    }

    private VirtualCamera createVirtualCamera(int lensFacing) {
        return createVirtualCamera(lensFacing, CAMERA_SENSOR_ORIENTATION);
    }

    private VirtualCamera createVirtualCameraWithSensorOrientation(int sensorOrientation) {
        return createVirtualCamera(LENS_FACING_FRONT, sensorOrientation);
    }

    private VirtualCamera createVirtualCamera(int lensFacing, int sensorOrientation) {
        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, sensorOrientation, lensFacing,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);

        return mVirtualDevice.createVirtualCamera(config);
    }

    private VirtualCamera createVirtualCameraWithCharacteristics(
            CameraCharacteristics cameraCharacteristics) {
        VirtualCameraConfig config = new VirtualCameraConfig.Builder("CharacteristicCamera")
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mVirtualCameraCallback)
                .setCameraCharacteristics(cameraCharacteristics)
                .build();

        return mVirtualDevice.createVirtualCamera(config);
    }

    private void setupDefaultDeviceCameraManager() {
        setupCameraManagerForDeviceId(DEVICE_ID_DEFAULT);
    }

    private void setupVirtualDeviceCameraManager() {
        setupCameraManagerForDeviceId(mVirtualDevice.getDeviceId());
    }

    private void setupCameraManagerForDeviceId(int deviceId) {
        Context vdContext = getApplicationContext().createDeviceContext(deviceId);
        mCameraManager = vdContext.getSystemService(CameraManager.class);
        mCameraManager.registerAvailabilityCallback(mExecutor,
                deviceId == DEVICE_ID_DEFAULT ? mMockDefaultContextCameraAvailabilityCallback
                        : mMockVdContextCameraAvailabilityCallback);
    }

    private void verifyCameraSensorOrientation(String cameraId, int sensorOrientation)
            throws Exception {
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                cameraId, /* overrideToPortrait= */ false);
        int orientationAngleDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        assertThat(orientationAngleDegrees).isEqualTo(sensorOrientation);
    }

    private void verifyCameraMaximumFramesPerSecond(String cameraId, int maximumFramesPerSecond)
            throws Exception {
        long expectedMinFrameDuration =
                TimeUnit.SECONDS.toNanos(1) / maximumFramesPerSecond;
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap streamConfigurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        int[] outputFormats = streamConfigurationMap.getOutputFormats();
        for (int format : outputFormats) {
            Size[] sizes = streamConfigurationMap.getOutputSizes(format);
            for (Size size : sizes) {
                long minFrameDuration =
                        streamConfigurationMap.getOutputMinFrameDuration(format, size);
                assertThat(minFrameDuration).isEqualTo(expectedMinFrameDuration);
            }
        }
    }

    private void verifyCameraLensFacing(String cameraId, int lensFacing) throws Exception {
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                cameraId);
        int cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        assertThat(cameraLensFacing).isEqualTo(lensFacing);
    }

    private void verifyConfigureSessionForSupportedFormatSucceeds(String cameraId)
            throws Exception {
        mCameraManager.openCamera(cameraId, mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();

        try (ImageReader reader = createImageReader(YUV_420_888)) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamConfigured(anyInt(),
                    mSurfaceCaptor.capture(), mWidthCaptor.capture(), mHeightCaptor.capture(),
                    mFormatCaptor.capture());
            assertThat(mSurfaceCaptor.getValue().isValid()).isTrue();
            assertThat(mWidthCaptor.getValue()).isEqualTo(CAMERA_WIDTH);
            assertThat(mHeightCaptor.getValue()).isEqualTo(CAMERA_HEIGHT);
            assertThat(mFormatCaptor.getValue()).isEqualTo(YUV_420_888);

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigured(
                    mCameraCaptureSessionCaptor.capture());
            CameraCaptureSession cameraCaptureSession = mCameraCaptureSessionCaptor.getValue();

            cameraCaptureSession.close();
        }
        cameraDevice.close();

        verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamClosed(anyInt());
    }

    private void verifyConfigureSessionForUnsupportedFormatFails(String cameraId) throws Exception {
        mCameraManager.openCamera(cameraId, mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();

        try (ImageReader reader = createImageReader(RGB_565)) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigureFailed(any());
        }
    }

    private SessionConfiguration createSessionConfig(ImageReader reader) {
        OutputConfiguration outputConfiguration = new OutputConfiguration(reader.getSurface());
        return new SessionConfiguration(SESSION_REGULAR,
                List.of(outputConfiguration), mExecutor, mSessionStateCallback);
    }

    private CameraCharacteristics createDefaultCameraCharacteristics(int lensFacing) {
        return new CameraCharacteristicsBuilder()
                .set(CameraCharacteristics.LENS_FACING, lensFacing)
                .build();
    }

    private String getCameraIdForLensFacing(int lensFacing) throws Exception {
        String cameraId = "";
        if (lensFacing == LENS_FACING_BACK) {
            cameraId = BACK_CAMERA_ID;
        } else if (lensFacing == LENS_FACING_FRONT) {
            cameraId = FRONT_CAMERA_ID;
        } else {
            // get the mapped cameraId from the list of cameras in the CameraManager
            // there should be only one
            cameraId = mCameraManager.getCameraIdList()[0];
        }

        return cameraId;
    }

    private static ImageReader createImageReader(int pixelFormat) {
        return ImageReader.newInstance(CAMERA_WIDTH, CAMERA_HEIGHT,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Integer[] getAllSensorOrientations() {
        return new Integer[]{
                SENSOR_ORIENTATION_0,
                SENSOR_ORIENTATION_90,
                SENSOR_ORIENTATION_180,
                SENSOR_ORIENTATION_270
        };
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static List<Integer> getAllLensFacingDirections() {
        List<Integer> lensFacingDirections = new ArrayList<>(
                List.of(LENS_FACING_BACK, LENS_FACING_FRONT));
        if (Flags.externalVirtualCameras()) {
            lensFacingDirections.add(LENS_FACING_EXTERNAL);
        }
        return lensFacingDirections;
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static String[] getAllCameraIds() {
        return new String[] {
            BACK_CAMERA_ID, FRONT_CAMERA_ID, "1234" // possible external camera id
        };
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Integer[] getAllLegacyCameraIds() {
        return new Integer[] {
            0, 1, 5 // possible external camera id
        };
    }
}
