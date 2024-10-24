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

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.BACK_CAMERA_ID;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.FRONT_CAMERA_ID;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.createHandler;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.jpegImageToBitmap;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.loadBitmapFromRaw;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.paintSurface;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.toFormat;
import static android.virtualdevice.cts.camera.util.ImageSubject.assertThat;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.FeatureUtil.hasSystemFeature;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import com.google.common.collect.Range;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            GRANT_RUNTIME_PERMISSIONS).withVirtualCameraSupportCheck();

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
    private ArgumentCaptor<Integer> mCameraDeviceErrorCaptor;

    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;

    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;

    @Captor
    private ArgumentCaptor<TotalCaptureResult> mTotalCaptureResultCaptor;

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
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
        Context virtualDeviceContext = getApplicationContext().createDeviceContext(
                mVirtualDevice.getDeviceId());
        mCameraManager = virtualDeviceContext.getSystemService(CameraManager.class);
        VirtualCameraUtils.grantCameraPermission(mVirtualDevice.getDeviceId());
    }

    @Test
    public void virtualCamera_inputBuffer_doesntBlock() throws Exception {
        try (VirtualCamera virtualCamera = createVirtualCamera()) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = createImageReader(YUV_420_888)) {
                Image image = captureImage(cameraId, imageReader,
                        (Surface surface) -> {
                            // Submit 100 RED-colored buffers to virtual camera input surface.
                            // This should not block, the buffers should be consumed immediately
                            // although there are no incoming capture requests.
                            for (int i = 0; i < 100; i++) {
                                paintSurface(surface, Color.RED);
                            }
                            // Submit green buffer, expect this one will be visible.
                            paintSurface(surface, Color.GREEN);
                        });

                assertThat(image.getFormat()).isEqualTo(YUV_420_888);
                assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
                assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
                assertThat(image).hasOnlyColor(Color.GREEN);
            }
        }
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
                assertThat(image).hasOnlyColor(Color.RED);
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
                assertThat(image).hasOnlyColor(Color.BLACK);
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
                assertThat(image).hasOnlyColor(Color.RED);
            }
        }
    }

    /**
     * Test that when the input of virtual camera comes from an ImageReader, the output ouf virtual
     * camera is similar to the output of the image reader.
     */
    @Test
    public void virtualCamera_renderFromMediaCodec() throws Exception {
        // This must match the test video size to avoid down scaling the bitmap for the comparison
        // and limit at best the diff value.
        int width = 1280;
        int height = 720;
        double maxImageDiff = 20;

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader =
                         ImageReader.newInstance(width, height, JPEG, IMAGE_READER_MAX_IMAGES)) {

                VirtualCameraUtils.VideoRenderer videoRenderer =
                        new VirtualCameraUtils.VideoRenderer(R.raw.test_video);
                Image imageFromCamera = captureImage(cameraId, imageReader, videoRenderer);

                Bitmap bitmapFromVideo = videoRenderer.getGoldenBitmap();
                Bitmap bitmapFromCamera = jpegImageToBitmap(imageFromCamera);
                VirtualCameraUtils.assertImagesSimilar(
                        bitmapFromCamera, bitmapFromVideo, "renderFromMediaCodec", maxImageDiff);
            }
        }
    }

    /**
     * Test that when the input of virtual camera comes from an ImageReader, the output ouf virtual
     * camera is similar a golden file generated on a pixel device.
     */
    @Test
    public void virtualCamera_renderFromMediaCodec_golden_from_pixel() throws Exception {
        int width = 460;
        int height = 260;
        double maxImageDiff = 20;

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = ImageReader.newInstance(width, height, JPEG,
                    IMAGE_READER_MAX_IMAGES)) {

                Image imageFromCamera =
                        captureImage(
                                cameraId,
                                imageReader,
                                new VirtualCameraUtils.VideoRenderer(R.raw.test_video));

                Bitmap bitmapFromCamera = jpegImageToBitmap(imageFromCamera);
                Bitmap golden = loadBitmapFromRaw(R.raw.golden_test_video);
                VirtualCameraUtils.assertImagesSimilar(
                        bitmapFromCamera,
                        golden,
                        "renderFromMediaCodec_golden_from_pixel",
                        maxImageDiff);
            }
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void virtualCamera_captureWithTimestamp_disabled_imageWriter() throws Exception {
        int width = 460;
        int height = 260;
        long renderedTimestamp = 1;

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = ImageReader.newInstance(width, height, YUV_420_888,
                    IMAGE_READER_MAX_IMAGES)) {
                Image imageFromCamera =
                        captureImage(
                                cameraId,
                                imageReader,
                                surface -> {
                                    ImageWriter imageWriter = ImageWriter.newInstance(surface, 1,
                                            YUV_420_888);
                                    Image image = imageWriter.dequeueInputImage();
                                    image.setTimestamp(renderedTimestamp);
                                    imageWriter.queueInputImage(image);
                                    imageWriter.close();
                                });

                Long captureTimestamp = mTotalCaptureResultCaptor.getValue().get(
                        TotalCaptureResult.SENSOR_TIMESTAMP);

                // Check that the provided timestamp was not written to the image
                assertThat(imageFromCamera.getTimestamp()).isNotEqualTo(renderedTimestamp);

                // Check that the capture result has a timestamp greater than 10 seconds.
                // This basically checks that the timestamp the actual capture time, was not
                // computed from our provided seed timestamp.
                assertThat(captureTimestamp).isGreaterThan(TimeUnit.SECONDS.toNanos(10));
            }
        }
    }

    /**
     * Test that when the input of virtual camera comes from an ImageReader, the output ouf virtual
     * camera is similar a golden file generated on a pixel device.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void virtualCamera_captureWithTimestamp_imageWriter() throws Exception {
        int width = 460;
        int height = 260;
        long renderedTimestamp = 123456L;

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = ImageReader.newInstance(width, height, YUV_420_888,
                    IMAGE_READER_MAX_IMAGES)) {
                Image imageFromCamera =
                        captureImage(
                                cameraId,
                                imageReader,
                                surface -> {
                                    ImageWriter imageWriter = ImageWriter.newInstance(surface, 1,
                                            YUV_420_888);
                                    Image image = imageWriter.dequeueInputImage();
                                    image.setTimestamp(renderedTimestamp);
                                    imageWriter.queueInputImage(image);
                                    imageWriter.close();
                                });

                Long captureTimestamp = mTotalCaptureResultCaptor.getValue().get(
                        TotalCaptureResult.SENSOR_TIMESTAMP);
                assertThat(imageFromCamera.getTimestamp()).isEqualTo(renderedTimestamp);
                assertThat(captureTimestamp).isEqualTo(renderedTimestamp);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void virtualCamera_captureWithTimestamp_mediaCodec() throws Exception {
        int width = 1280;
        int height = 720;
        long renderTimestamp = 100L;
        int fps = 5; // Low FPS to keep up with our codec

        SteadyTimestampCodec steadyTimestampCodec = new SteadyTimestampCodec(width, height,
                renderTimestamp);

        try (VirtualCamera virtualCamera = createVirtualCamera(width, height, YUV_420_888, fps)) {
            String cameraId = getVirtualCameraId(virtualCamera);

            try (ImageReader imageReader = ImageReader.newInstance(width, height, YUV_420_888,
                    IMAGE_READER_MAX_IMAGES)) {
                long startTime = SystemClock.uptimeMillis();
                Image image = captureImages(cameraId, imageReader,
                        steadyTimestampCodec::setSurfaceAndStart, 3);
                long endTimestamp = renderTimestamp + (SystemClock.uptimeMillis() - startTime);
                Range<Long> timestampRange = Range.closed(renderTimestamp, endTimestamp);
                assertThat(mTotalCaptureResultCaptor.getValue()
                        .get(CaptureResult.SENSOR_TIMESTAMP)).isIn(timestampRange);
                assertThat(image.getTimestamp()).isIn(timestampRange);

            } finally {
                steadyTimestampCodec.close();
            }
        }
    }

    private VirtualCamera createVirtualCamera() {
        return createVirtualCamera(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_INPUT_FORMAT);
    }

    private VirtualCamera createVirtualCamera(int inputWidth, int inputHeight, int inputFormat) {
        return createVirtualCamera(inputWidth, inputHeight, inputFormat, CAMERA_MAX_FPS);
    }

    private VirtualCamera createVirtualCamera(int inputWidth, int inputHeight, int inputFormat,
            int fps) {
        VirtualCameraConfig config = createVirtualCameraConfig(inputWidth, inputHeight,
                inputFormat, fps, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);
        try {
            return mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }

        // Never happens.
        return null;
    }

    private static ImageReader createImageReader(int pixelFormat) {
        return ImageReader.newInstance(CAMERA_WIDTH, CAMERA_HEIGHT,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    private static ImageReader createImageReader(int pixelFormat, int width, int height) {
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
        return captureImages(cameraId, reader, inputSurfaceConsumer, 1);
    }

    private Image captureImages(String cameraId, ImageReader reader,
            Consumer<Surface> inputSurfaceConsumer, int numberOfImage)
            throws CameraAccessException {
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


                CountDownLatch imageReaderLatch = new CountDownLatch(numberOfImage);
                reader.setOnImageAvailableListener(
                        imageReader -> imageReaderLatch.countDown(),
                        createHandler("image-reader-callback"));

                for (int i = 0; i < numberOfImage; i++) {
                    cameraCaptureSession.captureSingleRequest(request.build(), mExecutor,
                            mCaptureCallback);
                }

                verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS).atLeast(numberOfImage))
                        .onProcessCaptureRequest(anyInt(), anyLong());
                verify(mCaptureCallback,
                        timeout(TIMEOUT_MILLIS).atLeast(numberOfImage)).onCaptureCompleted(any(),
                        any(),
                        mTotalCaptureResultCaptor.capture()
                );
                assertWithMessage("Timeout waiting for image reader result")
                        .that(imageReaderLatch.await(TIMEOUT_MILLIS,
                                TimeUnit.MILLISECONDS)).isTrue();
                Image image = reader.acquireLatestImage();
                assertThat(image).isNotNull();
                return image;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
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

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static String[] getOutputPixelFormats() {
        return new String[]{"YUV_420_888", "JPEG"};
    }
}
