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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.BACK_CAMERA_ID;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.FRONT_CAMERA_ID;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.assertImagesSimilar;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.hasEGLExtension;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.imageHasColor;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.jpegImageToBitmap;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.loadGolden;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.toFormat;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

@RequiresFlagsEnabled({android.companion.virtual.flags.Flags.FLAG_VIRTUAL_CAMERA,
        Flags.FLAG_VIRTUAL_CAMERA_SERVICE_DISCOVERY})
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RunWith(JUnitParamsRunner.class)
public class VirtualCameraCaptureTest {
    private static final long TIMEOUT_MILLIS = 2000L;
    private static final String CAMERA_NAME = "Virtual camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_INPUT_FORMAT = PixelFormat.RGBA_8888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final int IMAGE_READER_MAX_IMAGES = 2;
    private static final String GL_EXT_YUV_target = "GL_EXT_YUV_target";

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;

    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    @Mock
    private CameraCaptureSession.CaptureCallback mCaptureCallback;

    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;

    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;

    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private Context mVirtualDeviceContext;
    private CameraManager mCameraManager;

    @Before
    public void setUp() {
        // Virtual Camera currently requires GL_EXT_YUV_target extension to process input YUV
        // buffers and perform RGB -> YUV conversion.
        // TODO(b/316108033) Remove once there's workaround for systems without GL_EXT_YUV_target
        // extension.
        assumeTrue(hasEGLExtension(GL_EXT_YUV_target));

        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        mVirtualDeviceContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        mCameraManager = mVirtualDeviceContext.getSystemService(CameraManager.class);
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void virtualCamera_captureImage_succeeds(String format) throws Exception {
        int outputPixelFormat = toFormat(format);

        try (VirtualCamera virtualCamera = createVirtualCamera()) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = createImageReader(outputPixelFormat)) {
                Image image = captureImage(cameraId, imageReader,
                        VirtualCameraUtils::paintSurfaceRed);

                assertThat(image.getFormat()).isEqualTo(outputPixelFormat);
                assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
                assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
                assertThat(imageHasColor(image, Color.RED)).isTrue();
            }
        }
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void virtualCamera_captureWithNoInput_capturesBlackImage(String format)
            throws Exception {
        int outputPixelFormat = toFormat(format);

        try (VirtualCamera virtualCamera = createVirtualCamera()) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = createImageReader(outputPixelFormat)) {
                Image image = captureImage(cameraId, imageReader,
                        (Surface surface) -> {
                        });

                assertThat(image.getFormat()).isEqualTo(outputPixelFormat);
                assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
                assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
                assertThat(imageHasColor(image, Color.BLACK)).isTrue();
            }
        }
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void virtualCamera_captureDownscaled_succeeds(String format) throws Exception {
        int outputPixelFormat = toFormat(format);
        int halfWidth = CAMERA_WIDTH / 2;
        int halfHeight = CAMERA_HEIGHT / 2;

        try (VirtualCamera virtualCamera = createVirtualCamera()) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = createImageReader(outputPixelFormat, halfWidth,
                    halfHeight)) {
                Image image = captureImage(cameraId, imageReader,
                        VirtualCameraUtils::paintSurfaceRed);

                assertThat(image.getFormat()).isEqualTo(outputPixelFormat);
                assertThat(image.getWidth()).isEqualTo(halfWidth);
                assertThat(image.getHeight()).isEqualTo(halfHeight);
                assertThat(imageHasColor(image, Color.RED)).isTrue();
            }
        }
    }

    @Test
    public void virtualCamera_renderFromMediaCodec() throws Exception {
        int width = 460;
        int height = 260;

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = ImageReader.newInstance(width, height, JPEG,
                    IMAGE_READER_MAX_IMAGES)) {

                Image image = captureImage(cameraId, imageReader,
                        new VirtualCameraUtils.VideoRenderer(R.raw.test_video));

                Bitmap bitmap = jpegImageToBitmap(image);
                Bitmap golden = loadGolden(R.raw.golden_test_video);
                assertImagesSimilar(bitmap, golden, "media_codec_virtual_camera");
            }
        }
    }

    private VirtualCamera createVirtualCamera() {
        return createVirtualCamera(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_INPUT_FORMAT);
    }

    private VirtualCamera createVirtualCamera(int inputWidth, int inputHeight, int inputFormat) {
        VirtualCameraConfig config = createVirtualCameraConfig(inputWidth, inputHeight,
                inputFormat, CAMERA_MAX_FPS, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);
        try {
            return mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }

        // Never happens.
        return null;
    }

    private static ImageReader createImageReader(@ImageFormat.Format int pixelFormat) {
        return ImageReader.newInstance(CAMERA_WIDTH, CAMERA_HEIGHT,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    private static ImageReader createImageReader(
            @ImageFormat.Format int pixelFormat, int width, int height) {
        return ImageReader.newInstance(width, height,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    private SessionConfiguration createSessionConfig(ImageReader reader) {
        OutputConfiguration outputConfiguration = new OutputConfiguration(reader.getSurface());
        return new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                List.of(outputConfiguration), mExecutor, mSessionStateCallback);
    }

    private Image captureImage(String cameraId, ImageReader reader,
            Consumer<Surface> inputSurfaceConsumer) throws CameraAccessException {
        mCameraManager.openCamera(cameraId, mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        try (CameraDevice cameraDevice = mCameraDeviceCaptor.getValue()) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigured(
                    mCameraCaptureSessionCaptor.capture());
            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamConfigured(anyInt(),
                    mSurfaceCaptor.capture(), anyInt(), anyInt(), anyInt());

            Surface inputSurface = mSurfaceCaptor.getValue();
            assertThat(inputSurface.isValid()).isTrue();
            inputSurfaceConsumer.accept(inputSurface);

            try (CameraCaptureSession cameraCaptureSession =
                         mCameraCaptureSessionCaptor.getValue()) {
                CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(reader.getSurface());
                cameraCaptureSession.captureSingleRequest(request.build(), mExecutor,
                        mCaptureCallback);

                verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                        .onProcessCaptureRequest(anyInt(), anyLong());
                verify(mCaptureCallback, timeout(TIMEOUT_MILLIS)).onCaptureCompleted(any(),
                        any(),
                        any());
                Image image = reader.acquireLatestImage();
                assertThat(image).isNotNull();
                return image;
            }
        }
    }

    private static String getVirtualCameraId(VirtualCamera virtualCamera) {
        if (Flags.cameraDeviceAwareness()) {
            switch (virtualCamera.getConfig().getLensFacing()) {
                case LENS_FACING_FRONT -> {
                    return FRONT_CAMERA_ID;
                }
                case LENS_FACING_BACK -> {
                    return BACK_CAMERA_ID;
                }
                default -> {
                    return virtualCamera.getId();
                }
            }
        }

        return virtualCamera.getId();
    }

    private static String[] getOutputPixelFormats() {
        return new String[]{"YUV_420_888", "JPEG"};
    }
}