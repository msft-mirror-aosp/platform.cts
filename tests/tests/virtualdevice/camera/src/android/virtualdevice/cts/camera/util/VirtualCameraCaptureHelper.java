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

package android.virtualdevice.cts.camera.util;

import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;

import static org.junit.Assume.assumeNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.truth.Truth;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Helper class for testing capture scenarios with a virtual camera.
 */
public class VirtualCameraCaptureHelper {
    public static final long TIMEOUT_MILLIS = 2000L;
    public static final String CAMERA_NAME = "Virtual camera";
    public static final int CAMERA_WIDTH = 640;
    public static final int CAMERA_HEIGHT = 480;
    public static final int CAMERA_INPUT_FORMAT = PixelFormat.RGBA_8888;
    public static final int CAMERA_MAX_FPS = 30;

    private static final long FAILURE_TIMEOUT = 10000L;
    private static final int IMAGE_READER_MAX_IMAGES = 2;

    private final Handler mImageReaderHandler = VirtualCameraUtils.createHandler(
            "image-reader-callback");
    private final Executor mExecutor =
            ApplicationProvider.getApplicationContext().getMainExecutor();
    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;
    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;
    @Mock
    private CameraCaptureSession.CaptureCallback mCaptureCallback;
    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;
    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;
    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;
    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;
    @Captor
    private ArgumentCaptor<TotalCaptureResult> mTotalCaptureResultCaptor;

    @Nullable
    private CameraManager mCameraManager = null;
    @Nullable
    private VirtualCamera mVirtualCamera = null;
    @Nullable
    private CameraDevice mCameraDevice = null;
    @Nullable
    private ImageReader mOutputReader = null;
    @Nullable
    private CameraCaptureSession mCaptureSession = null;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice = null;

    /**
     * Initialize the helper to work with the provided virtualDevice onto which the virtual camera
     * will be created and the context used to open the camera for capture.
     */
    public void setUp(@NonNull VirtualDeviceManager.VirtualDevice virtualDevice,
            @NonNull Context context) {
        mCameraManager = Objects.requireNonNull(context).getSystemService(CameraManager.class);
        mVirtualDevice = Objects.requireNonNull(virtualDevice);
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up resources after the test has been run
     */
    public void tearDown() {
        Mockito.reset(mCaptureCallback, mCameraStateCallback, mSessionStateCallback,
                mVirtualCameraCallback);
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mVirtualCamera != null) {
            mVirtualCamera.close();
            mVirtualCamera = null;
        }
        if (mOutputReader != null) {
            mOutputReader.close();
            mOutputReader = null;
        }
    }

    /**
     * Create a virtual camera with default values.
     */
    public void createVirtualCamera() {
        createVirtualCamera(VirtualCameraCaptureHelper.CAMERA_WIDTH,
                VirtualCameraCaptureHelper.CAMERA_HEIGHT,
                VirtualCameraCaptureHelper.CAMERA_INPUT_FORMAT);
    }

    /**
     * Create a virtual camera with the provided configuration
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat) {
        createVirtualCamera(inputWidth, inputHeight, inputFormat,
                VirtualCameraCaptureHelper.CAMERA_MAX_FPS);
    }

    /**
     * Create a virtual camera with the provided configuration
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     * @param fps         fps of the input of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat,
            int fps) {
        Objects.requireNonNull(mVirtualDevice,
                "mVirtualDevice must not be null when calling #createVirtualCamera()");
        VirtualCameraConfig config = createVirtualCameraConfig(inputWidth, inputHeight,
                inputFormat, fps, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                VirtualCameraCaptureHelper.CAMERA_NAME, mExecutor,
                mVirtualCameraCallback);
        try {
            mVirtualCamera = mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }
    }

    /**
     * Capture images using the provided {@link CaptureConfiguration}
     *
     * The camera device and session will be automatically created if needed.
     *
     * @return The latest captured image.
     */
    public Image captureImages(CaptureConfiguration config) {
        AtomicReference<Image> latestImageRef = new AtomicReference<>(null);

        try {
            ImageReader reader = getOrCreateOutputReader(config);
            CameraCaptureSession cameraCaptureSession = getOrCreateCaptureSession(reader);
            CameraDevice cameraDevice = cameraCaptureSession.getDevice();
            config.mInputSurfaceConsumer.accept(getInputSurface());

            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            config.mRequestBuilderModifier.accept(request);
            request.addTarget(reader.getSurface());

            CountDownLatch imageReaderLatch = new CountDownLatch(config.mImageCount);
            reader.setOnImageAvailableListener(imageReader -> {
                Image latestImage = latestImageRef.get();
                if (latestImage != null) {
                    latestImage.close();
                }
                latestImageRef.set(imageReader.acquireLatestImage());
                imageReaderLatch.countDown();
            }, mImageReaderHandler);

            for (int i = 0; i < config.mImageCount; i++) {
                cameraCaptureSession.captureSingleRequest(request.build(), mExecutor,
                        mCaptureCallback);
            }

            if (!config.mVerifyCaptureComplete) {
                return reader.acquireLatestImage();
            }

            verifyCaptureComplete(config.mImageCount);
            Truth.assertWithMessage("Timeout waiting for image reader result").that(
                    imageReaderLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            Image image = latestImageRef.getAndSet(null);
            ImageSubject.assertThat(image).isNotNull();
            return image;
        } catch (CameraAccessException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Image image = latestImageRef.getAndSet(null);
            if (image != null) {
                image.close();
            }
        }
    }

    private CameraDevice getOrOpenCameraDevice() {
        try {
            if (mCameraDevice != null) {
                return mCameraDevice;
            }
            Objects.requireNonNull(mVirtualCamera,
                    "mVirtualCamera must not be null when calling this method.");
            Objects.requireNonNull(mCameraManager,
                    "mCameraManager must not be null when calling this method.");
            mCameraManager.openCamera(getVirtualCameraId(mVirtualCamera), mExecutor,
                    mCameraStateCallback);
            Mockito.verify(mCameraStateCallback, Mockito.timeout(TIMEOUT_MILLIS)).onOpened(
                    mCameraDeviceCaptor.capture());
            mCameraDevice = mCameraDeviceCaptor.getValue();
            return mCameraDevice;
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Surface getInputSurface() {
        Surface surface = mSurfaceCaptor.getValue();
        Truth.assertThat(surface.isValid()).isTrue();
        return surface;
    }

    private CameraCaptureSession getOrCreateCaptureSession(ImageReader reader)
            throws CameraAccessException {
        if (mCaptureSession != null) {
            return mCaptureSession;
        }
        CameraDevice cameraDevice = getOrOpenCameraDevice();
        OutputConfiguration outputConfiguration = new OutputConfiguration(reader.getSurface());
        cameraDevice.createCaptureSession(
                new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        List.of(outputConfiguration), mExecutor, mSessionStateCallback));
        Mockito.verify(mSessionStateCallback, Mockito.timeout(TIMEOUT_MILLIS)).onConfigured(
                mCameraCaptureSessionCaptor.capture());
        Mockito.verify(mVirtualCameraCallback,
                Mockito.timeout(TIMEOUT_MILLIS)).onStreamConfigured(ArgumentMatchers.anyInt(),
                mSurfaceCaptor.capture(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt());
        mCaptureSession = mCameraCaptureSessionCaptor.getValue();
        return mCaptureSession;
    }

    private ImageReader getOrCreateOutputReader(CaptureConfiguration config) {
        if (mOutputReader != null && (config.mOutputFormat != mOutputReader.getImageFormat()
                || config.mHeight != mOutputReader.getHeight()
                || mOutputReader.getWidth() != config.mWidth)) {
            mOutputReader.close();
            mOutputReader = null;
        }

        if (mOutputReader == null) {
            mOutputReader = ImageReader.newInstance(config.mWidth, config.mHeight,
                    config.mOutputFormat, IMAGE_READER_MAX_IMAGES);
        }
        return mOutputReader;
    }

    private void verifyCaptureComplete(int imageCount) {
        Mockito.verify(mVirtualCameraCallback,
                Mockito.timeout(TIMEOUT_MILLIS).atLeast(imageCount)).onProcessCaptureRequest(
                ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong());
        Mockito.verify(mCaptureCallback,
                Mockito.timeout(TIMEOUT_MILLIS).atLeast(imageCount)).onCaptureCompleted(
                ArgumentMatchers.any(), ArgumentMatchers.any(),
                getTotalCaptureResultCaptor().capture());
    }

    /**
     * Check that the capture has failed at least one time and never succeeded.
     */
    public void verifyCaptureFailed() {
        Mockito.verify(mCaptureCallback, Mockito.timeout(FAILURE_TIMEOUT).times(1).description(
                "Verify that the capture has failed")).onCaptureFailed(ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.verify(mCaptureCallback, never()).onCaptureCompleted(any(), any(),
                getTotalCaptureResultCaptor().capture());
    }

    private static String getVirtualCameraId(VirtualCamera virtualCamera) {
        return switch (virtualCamera.getConfig().getLensFacing()) {
            case CameraMetadata.LENS_FACING_FRONT -> VirtualCameraUtils.FRONT_CAMERA_ID;
            case CameraMetadata.LENS_FACING_BACK -> VirtualCameraUtils.BACK_CAMERA_ID;
            default -> virtualCamera.getId();
        };
    }

    /**
     * Returns the {@link ArgumentCaptor} for the {@link TotalCaptureResult} of all the capture
     * requests
     */
    public ArgumentCaptor<TotalCaptureResult> getTotalCaptureResultCaptor() {
        return mTotalCaptureResultCaptor;
    }

    /**
     * Return a {@link Mock} of {@link CameraCaptureSession.CaptureCallback}
     */
    public CameraCaptureSession.CaptureCallback getCaptureCallback() {
        return mCaptureCallback;
    }

    /**
     * Holds the configuration used for {@link #captureImages(CaptureConfiguration)}.
     * <p>
     * The default configuration can be used as is, all setters are optional.
     */
    public static final class CaptureConfiguration {
        private int mImageCount = 1;
        private boolean mVerifyCaptureComplete = true;
        private Consumer<Surface> mInputSurfaceConsumer = (surface) -> {
        };
        private Consumer<CaptureRequest.Builder> mRequestBuilderModifier = (request) -> {
        };
        private int mWidth = CAMERA_WIDTH;
        private int mHeight = CAMERA_HEIGHT;
        private int mOutputFormat = YUV_420_888;

        /**
         * Set the number of image to capture
         * <p>
         * Default is 1.
         */
        public CaptureConfiguration setImageCount(int imageCount) {
            mImageCount = imageCount;
            return this;
        }

        /**
         * Set the whether the sucessful completion of the capture should be checked
         * <p>
         * Default is true.
         */
        public CaptureConfiguration setVerifyCaptureComplete(boolean verifyCaptureComplete) {
            mVerifyCaptureComplete = verifyCaptureComplete;
            return this;
        }

        /**
         * Set a consumer to write onto the input surface of the {@link VirtualCamera}
         * <p>
         * Default is no-op.
         */
        public CaptureConfiguration setInputSurfaceConsumer(
                Consumer<Surface> inputSurfaceConsumer) {
            mInputSurfaceConsumer = inputSurfaceConsumer;
            return this;
        }

        /**
         * Set the consumer that accepts a {@link CaptureRequest.Builder} and which can modify that
         * request.
         * <p>
         * Default is no-op.
         */
        public CaptureConfiguration setRequestBuilderModifier(
                @Nullable Consumer<CaptureRequest.Builder> requestBuilderModifier) {
            mRequestBuilderModifier = requestBuilderModifier;
            return this;
        }

        /**
         * Set the width of the capture surface and result
         * <p>
         * Default is {@link #CAMERA_WIDTH}.
         */
        public CaptureConfiguration setWidth(int width) {
            mWidth = width;
            return this;
        }

        /**
         * Set the height of the capture surface and result
         * <p>
         * Default is {@link #CAMERA_WIDTH}.
         */
        public CaptureConfiguration setHeight(int height) {
            mHeight = height;
            return this;
        }

        /**
         * Set the output format  of the capture surface and result
         * <p>
         * Default is {@link ImageFormat#YUV_420_888}.
         */
        public CaptureConfiguration setOutputFormat(int outputFormat) {
            mOutputFormat = outputFormat;
            return this;
        }
    }
}
