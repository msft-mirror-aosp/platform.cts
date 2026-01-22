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
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraSessionConfig;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

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

    // This needs to be bigger than kMaxWaitFirstFrame from VirtualCameraRenderThread.cc
    private static final long FAILURE_TIMEOUT = 20000L;
    private static final int IMAGE_READER_MAX_IMAGES = 2;
    private static final String TAG = "VirtualCameraCaptureHelper";

    private final Handler mImageReaderHandler = VirtualCameraUtils.createHandler(
            "image-reader-callback");

    /** Returns a {@link Handler} for image reader callbacks. */
    public Handler getImageReaderHandler() {
        return mImageReaderHandler;
    }

    private final Executor mCameraExecutor = Executors.newSingleThreadExecutor();
    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;
    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    private TestCaptureCallback mCaptureCallback;

    private TestVirtualCameraCallback mVirtualCameraCallback;
    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;
    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;

    @Nullable
    private CameraManager mCameraManager = null;
    @Nullable
    private VirtualCamera mVirtualCamera = null;
    @Nullable
    private CameraDevice mCameraDevice = null;

    private final List<ImageReader> mOutputReaders = new ArrayList<>();
    private final List<Image> mOutputImages = new ArrayList<>();
    @Nullable
    private CameraCaptureSession mCaptureSession = null;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice = null;

    /**
     * Returns an instance of {@link VirtualCameraConfig.Builder} with the mandatory parameters set.
     */
    @NonNull
    public static VirtualCameraConfig.Builder createBuilderWithDefaults(
            @NonNull String cameraName) {
        return new VirtualCameraConfig.Builder(cameraName)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_INPUT_FORMAT, CAMERA_MAX_FPS)
                .setSensorOrientation(SENSOR_ORIENTATION_0)
                .setLensFacing(LENS_FACING_BACK);
    }

    /**
     * Initialize the helper to work with the provided virtualDevice onto which the virtual camera
     * will be created and the context used to open the camera for capture.
     */
    public void setUp(@NonNull VirtualDeviceManager.VirtualDevice virtualDevice,
            @NonNull Context context) {
        mCameraManager = Objects.requireNonNull(context).getSystemService(CameraManager.class);
        mVirtualDevice = Objects.requireNonNull(virtualDevice);
        mVirtualCameraCallback = new TestVirtualCameraCallback();
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up resources after the test has been run
     */
    public void tearDown() {
        Mockito.reset(mCameraStateCallback, mSessionStateCallback);
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mVirtualCamera != null) {
            mVirtualCamera.close();
            mVirtualCamera = null;
        }
        closeAndClearImageReaders();
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
     * Create a virtual camera with the provided configuration and lens facing LENS_FACING_FRONT
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     * @param fps         fps of the input of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat,
            int fps) {
        createVirtualCamera(inputWidth, inputHeight, inputFormat,
                fps, LENS_FACING_FRONT);
    }

    /**
     * Create a virtual camera with the provided configuration
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     * @param fps         fps of the input of this virtual camera
     * @param lensFacing  lens facing of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat,
            int fps, int lensFacing) {
        VirtualCameraConfig config = createVirtualCameraConfig(inputWidth, inputHeight,
                inputFormat, fps, SENSOR_ORIENTATION_0, lensFacing,
                VirtualCameraCaptureHelper.CAMERA_NAME, mCameraExecutor,
                mVirtualCameraCallback);
        createVirtualCamera(config);
    }

    /**
     * Create a new virtual camera based on the given config.
     *
     * <p>If a custom {@link VirtualCameraCallback} is needed for the constructed virtual camera,
     * the variant of this method taking a {@link VirtualCameraCallback} must be called. Any
     * previously set callback will be overridden.
     */
    public void createVirtualCamera(@NonNull VirtualCameraConfig.Builder builder) {
        builder.setVirtualCameraCallback(mCameraExecutor, mVirtualCameraCallback);
        createVirtualCamera(builder.build());
    }

    /**
     * Create a virtual camera and allow caller to pass its own {@link VirtualCameraCallback}.
     *
     * <p>Do not use {@link VirtualCameraConfig.Builder#setVirtualCameraCallback(Executor,
     * VirtualCameraCallback)} to set a callback as it will be overridden by test callback.
     *
     * @param builder The builder to configure the virtual camera
     * @param callbackDelegate The callback instance to delegate the callbacks to.
     */
    public void createVirtualCamera(
            @NonNull VirtualCameraConfig.Builder builder,
            @NonNull VirtualCameraCallback callbackDelegate) {
        Objects.requireNonNull(
                callbackDelegate,
                "callbackDelegate must not be null when calling #createVirtualCamera(builder, "
                        + "callDelegate)");
        mVirtualCameraCallback.mCallbackDelegate = callbackDelegate;
        builder.setVirtualCameraCallback(mCameraExecutor, mVirtualCameraCallback);
        createVirtualCamera(builder.build());
    }

    private void createVirtualCamera(VirtualCameraConfig config) {
        Objects.requireNonNull(
                mVirtualDevice,
                "mVirtualDevice must not be null when calling #createVirtualCamera()");
        try {
            mVirtualCamera = mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }
    }

    /**
     * Capture a single image using the provided {@link CaptureConfiguration}.
     *
     * @param config The configuration for this capture.
     * @return The captured image or null if an issue arised.
     */
    public Image captureImage(CaptureConfiguration config) {
        List<Image> images = captureImages(config);
        assertWithMessage(
                        "To capture more than one output configuration, user captureImages()"
                                + " instead of captureImage()")
                .that(images)
                .hasSize(1);
        return images.get(0);
    }

    /**
     * Capture images using the provided {@link CaptureConfiguration}
     *
     * <p>The camera device and session will be automatically created if needed.
     *
     * @return All the captured images. The values can be null if some capture failed.
     */
    public List<Image> captureImages(CaptureConfiguration config) {
        mCaptureCallback = new TestCaptureCallback();
        mCaptureCallback.mFailOnFailedCapture = config.mFailOnCaptureError;

        try {
            List<ImageReader> readers = createOutputReaders(config);
            CameraCaptureSession cameraCaptureSession = createCaptureSession(readers);
            CameraDevice cameraDevice = cameraCaptureSession.getDevice();
            config.mInputSurfaceConsumer.accept(getInputSurface());

            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            config.mRequestBuilderModifier.accept(request);
            CountDownLatch imageReaderLatch =
                    new CountDownLatch(config.mImageCount * readers.size());
            for (int i = 0; i < readers.size(); i++) {
                ImageReader reader = readers.get(i);
                request.addTarget(reader.getSurface());
                int readerIndex = i;
                reader.setOnImageAvailableListener(
                        imageReader -> {
                            Image image = imageReader.acquireLatestImage();
                            Image previousImage = mOutputImages.get(readerIndex);
                            if (previousImage != null) {
                                previousImage.close();
                            }
                            mOutputImages.set(readerIndex, image);
                            imageReaderLatch.countDown();
                        },
                        mImageReaderHandler);
            }

            Duration capturePeriod = config.mCapturePeriod;
            if (capturePeriod != null) {
                Timer cameraCaptureTimer = new Timer("Camera Capture Timer");
                cameraCaptureTimer.scheduleAtFixedRate(
                        new TimerTask() {

                            int mRemainingCapture = config.mImageCount;

                            @Override
                            public void run() {
                                try {
                                    if (mRemainingCapture <= 0) {
                                        cancel();
                                        return;
                                    }
                                    mRemainingCapture--;
                                    Trace.beginSection(
                                            "VirtualCameraCaptureHelper.captureSingleRequest (fixed"
                                                    + " rate) metadata enabled: "
                                                    + config.mPerFrameCameraMetadataEnabled);
                                    cameraCaptureSession.captureSingleRequest(
                                            request.build(), mCameraExecutor, mCaptureCallback);
                                    Trace.endSection();
                                } catch (CameraAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        },
                        0,
                        capturePeriod.toMillis());
            } else {
                for (int i = 0; i < config.mImageCount; i++) {
                    Trace.beginSection(
                            "VirtualCameraCaptureHelper.captureSingleRequest (no rate) metadata"
                                    + " enabled: "
                                    + config.mPerFrameCameraMetadataEnabled);
                    cameraCaptureSession.captureSingleRequest(request.build(), mCameraExecutor,
                            mCaptureCallback);
                    Trace.endSection();
                }
            }

            if (config.mVerifyCaptureComplete) {
                verifyCaptureComplete(config.mImageCount);
                assertWithMessage("Timeout waiting for image readers result")
                        .that(imageReaderLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        .isTrue();
            }
            return mOutputImages;
        } catch (CameraAccessException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the {@link CameraDevice} corresponding to the virtual camera.
     */
    public CameraDevice getOrOpenCameraDevice() {
        try {
            if (mCameraDevice != null) {
                return mCameraDevice;
            }
            Objects.requireNonNull(mVirtualCamera,
                    "mVirtualCamera must not be null when calling this method.");
            Objects.requireNonNull(mCameraManager,
                    "mCameraManager must not be null when calling this method.");
            mCameraManager.openCamera(getVirtualCameraId(mVirtualCamera), mCameraExecutor,
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
        mVirtualCameraCallback.waitForSessionConfigured();
        return mVirtualCameraCallback.mConfiguredStreams.values().iterator().next();
    }

    public CameraCaptureSession createCaptureSession(Collection<ImageReader> readers)
            throws CameraAccessException {
        CameraDevice cameraDevice = getOrOpenCameraDevice();
        ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>(readers.size());
        for (ImageReader reader : readers) {
            outputConfigurations.add(new OutputConfiguration(reader.getSurface()));
        }
        cameraDevice.createCaptureSession(
                new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        mCameraExecutor,
                        mSessionStateCallback));
        mVirtualCameraCallback.waitForSessionConfigured();
        Mockito.verify(mSessionStateCallback, Mockito.timeout(TIMEOUT_MILLIS)).onConfigured(
                mCameraCaptureSessionCaptor.capture());
        if (Flags.virtualCameraMetadata()) {
            assertThat(mVirtualCameraCallback.mConfiguredSession).isNotNull();
        }
        mCaptureSession = mCameraCaptureSessionCaptor.getValue();
        return mCaptureSession;
    }

    private List<ImageReader> createOutputReaders(CaptureConfiguration config) {
        assertWithMessage(
                        "At least one output configuration must be added with "
                                + "CaptureConfiguration#addOutputFormat")
                .that(config.mOutputConfigurations)
                .isNotEmpty();

        closeAndClearImageReaders();

        List<CaptureFormat> outputConfigs = config.mOutputConfigurations;
        for (CaptureFormat outputConfiguration : outputConfigs) {
            mOutputReaders.add(
                    ImageReader.newInstance(
                            outputConfiguration.mWidth,
                            outputConfiguration.mHeight,
                            outputConfiguration.mFormat,
                            IMAGE_READER_MAX_IMAGES));
            mOutputImages.add(null);
        }

        return mOutputReaders;
    }

    private void closeAndClearImageReaders() {
        Iterator<ImageReader> iterator = mOutputReaders.iterator();
        while (iterator.hasNext()) {
            ImageReader reader = iterator.next();
            reader.close();
            iterator.remove();
        }

        Iterator<Image> imageIterator = mOutputImages.iterator();
        while (imageIterator.hasNext()) {
            Image image = imageIterator.next();
            if (image != null) {
                image.close();
            }
            imageIterator.remove();
        }
    }

    private void verifyCaptureComplete(int imageCount) {
        mVirtualCameraCallback.waitForCapture(imageCount);
        mCaptureCallback.waitForCaptures(imageCount, TIMEOUT_MILLIS);
    }

    /**
     * Check that the capture has failed at least one time and never succeeded.
     */
    public void verifyCaptureFailed() {
        mCaptureCallback.waitForCaptures(1, FAILURE_TIMEOUT);
        assertThat(mCaptureCallback.getFailedCaptureCount()).isEqualTo(1);
        assertThat(mCaptureCallback.getCaptureResults()).isEmpty();
    }

    private static String getVirtualCameraId(VirtualCamera virtualCamera) {
        // if set, the lens facing from the CameraCharacteristics have priority
        CameraCharacteristics characteristics =
                virtualCamera.getConfig().getCameraCharacteristics();
        int lensFacing =
                characteristics != null
                        ? characteristics.get(CameraCharacteristics.LENS_FACING)
                        : virtualCamera.getConfig().getLensFacing();
        return switch (lensFacing) {
            case LENS_FACING_FRONT -> VirtualCameraUtils.FRONT_CAMERA_ID;
            case LENS_FACING_BACK -> VirtualCameraUtils.BACK_CAMERA_ID;
            default -> virtualCamera.getId();
        };
    }

    /** Returns a {@link Mock} of {@link VirtualCameraCallback} */
    public TestVirtualCameraCallback getVirtualCameraCallback() {
        return mVirtualCameraCallback;
    }

    /**
     * Returns the list of all the capture result collected after the call to {@link
     * #captureImages(CaptureConfiguration)}
     */
    @NonNull
    public List<TotalCaptureResult> getCaptureResults() {
        return mCaptureCallback.getCaptureResults();
    }

    /** Returns the last capture result collected or null if no capture result was collected. */
    @Nullable
    public TotalCaptureResult getLastResult() {
        if (mCaptureCallback.getCaptureResults().isEmpty()) {
            return null;
        }
        return mCaptureCallback.getCaptureResults().getLast();
    }

    /** Returns the list of capture timestamps. */
    public List<Long> getCaptureDeviceTimestampsNanos() {
        return mCaptureCallback.getCaptureDeviceTimestamp();
    }

    public CameraCaptureSession getCameraSession() {
        return mCaptureSession;
    }

    private static final class CaptureFormat {
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;

        private CaptureFormat(int width, int height, int format) {
            mWidth = width;
            mHeight = height;
            mFormat = format;
        }
    }

    /**
     * Holds the configuration used for {@link #captureImage(CaptureConfiguration)}.
     *
     * <p>The default configuration can be used as is, all setters are optional.
     */
    public static final class CaptureConfiguration {

        private int mImageCount = 1;
        public boolean mFailOnCaptureError = true;
        private boolean mVerifyCaptureComplete = true;
        private Consumer<Surface> mInputSurfaceConsumer = (surface) -> {};
        private Consumer<CaptureRequest.Builder> mRequestBuilderModifier = (request) -> {
        };
        private final List<CaptureFormat> mOutputConfigurations = new ArrayList<>();
        private Duration mCapturePeriod = null;
        private boolean mPerFrameCameraMetadataEnabled = false;

        /**
         * Set the number of image to capture
         *
         * <p>Default is 1.
         */
        public CaptureConfiguration setImageCount(int imageCount) {
            mImageCount = imageCount;
            return this;
        }

        public CaptureConfiguration setCapturePeriod(Duration period) {
            mCapturePeriod = period;
            return this;
        }

        /**
         * Set whether the successful completion of the capture should be checked
         *
         * <p>Default is true.
         */
        public CaptureConfiguration setVerifyCaptureComplete(boolean verifyCaptureComplete) {
            mVerifyCaptureComplete = verifyCaptureComplete;
            return this;
        }

        /**
         * Set whether we should fail as soon as we get a capture error.
         *
         * <p>Default is true.
         *
         * @see CaptureCallback#onCaptureFailed(CameraCaptureSession, CaptureRequest,
         *     CaptureFailure)
         */
        public CaptureConfiguration setFailOnCaptureError(boolean failOnCaptureError) {
            mFailOnCaptureError = failOnCaptureError;
            return this;
        }

        /**
         * Set a consumer to write onto the input surface of the {@link VirtualCamera}
         *
         * <p>Default is no-op.
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
         * Add a format to be captured.
         *
         * @param width The width of the image to capture
         * @param height The height of the image to capture
         * @param format The format of the image to capture
         * @return this builder.
         */
        public CaptureConfiguration addOutputFormat(int width, int height, int format) {
            mOutputConfigurations.add(new CaptureFormat(width, height, format));
            return this;
        }

        /**
         * Set the output format of the capture surface and result
         *
         * <p>Default is {@link ImageFormat#YUV_420_888}.
         */
        public CaptureConfiguration addOutputFormat(int outputFormat) {
            return addOutputFormat(CAMERA_WIDTH, CAMERA_HEIGHT, outputFormat);
        }

        /**
         * Set if the per frame camera metadata is expected
         *
         * <p>Default is false.
         */
        public CaptureConfiguration setPerFrameCameraMetadataEnabled(
                boolean perFrameCameraMetadataEnabled) {
            mPerFrameCameraMetadataEnabled = perFrameCameraMetadataEnabled;
            return this;
        }
    }

    private static class TestCaptureCallback extends CaptureCallback {

        private final ArrayList<TotalCaptureResult> mCaptureResults = new ArrayList<>();
        private final ArrayList<Long> mCaptureResultsDeviceTimestamps = new ArrayList<>();
        private CountDownLatch mCaptureAndErrorLatch = new CountDownLatch(0);
        private int mFailedCaptureCount = 0;
        private boolean mFailOnFailedCapture = true;

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureFailure failure) {
            mFailedCaptureCount++;
            mCaptureAndErrorLatch.countDown();
            if (!mFailOnFailedCapture) {
                return;
            }
            synchronized (mCaptureResults) {
                Assert.fail(
                        ("Unexpected capture failure for request %s. Failure frame: %d , reason "
                                + "%s, imageCaptured: %s. Before this error we received %d "
                                + "successful frames")
                                .formatted(request, failure.getFrameNumber(),
                                        failureToString(failure),
                                        failure.wasImageCaptured(), mCaptureResults.size()));
            }
        }

        @NonNull
        private static Object failureToString(@NonNull CaptureFailure failure) {
            return switch (failure.getReason()) {
                case CaptureFailure.REASON_ERROR -> "REASON_ERROR";
                case CaptureFailure.REASON_FLUSHED -> "REASON_FLUSHED";
                default -> failure.getReason();
            };
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
            synchronized (mCaptureResults) {
                mCaptureResults.add(result);
            }
            mCaptureResultsDeviceTimestamps.add(SystemClock.uptimeNanos());
            mCaptureAndErrorLatch.countDown();
        }

        @NonNull
        public List<TotalCaptureResult> getCaptureResults() {
            synchronized (mCaptureResults) {
                return List.copyOf(mCaptureResults);
            }
        }

        @NonNull
        public List<Long> getCaptureDeviceTimestamp() {
            synchronized (mCaptureResultsDeviceTimestamps) {
                return List.copyOf(mCaptureResultsDeviceTimestamps);
            }
        }

        public int getFailedCaptureCount() {
            return mFailedCaptureCount;
        }

        private void waitForCaptures(int expectedCaptureNumber, long timeoutMillis) {
            int captureAndErrorCount;
            synchronized (mCaptureResults) {
                captureAndErrorCount = mCaptureResults.size() + mFailedCaptureCount;
            }
            if (captureAndErrorCount >= expectedCaptureNumber) {
                return;
            }
            int missingCaptureCount = expectedCaptureNumber - captureAndErrorCount;
            mCaptureAndErrorLatch = new CountDownLatch(missingCaptureCount);
            try {
                if (!mCaptureAndErrorLatch.await(timeoutMillis * missingCaptureCount,
                        TimeUnit.MILLISECONDS)) {
                    synchronized (mCaptureResults) {
                        captureAndErrorCount = mCaptureResults.size();
                    }
                    Assert.fail(
                            ("Timed out waiting for capture. Expected: %d, received: %d "
                                    + "successful captures and %d errors")
                                    .formatted(expectedCaptureNumber, captureAndErrorCount,
                                            mFailedCaptureCount));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for capture", e);
            }
        }
    }

    public static final class TestVirtualCameraCallback implements VirtualCameraCallback {

        private final ArrayList<CaptureRequest> mCaptureRequests = new ArrayList<>();
        private final Map<Integer, Surface> mConfiguredStreams = new ArrayMap<>();
        private final AtomicReference<CountDownLatch> mCaptureLatch = new AtomicReference<>();
        private final CountDownLatch mSessionConfiguredLatch = new CountDownLatch(1);
        public VirtualCameraCallback mCallbackDelegate = new DefaultVirtualCameraCallback();

        private VirtualCameraSessionConfig mConfiguredSession = null;
        private int mCaptureRequestCount = 0;

        private TestVirtualCameraCallback() {
            // No instantiation outside this helper
        }

        @Override
        public void onConfigureSession(
                @NonNull VirtualCameraSessionConfig virtualCameraSessionConfig,
                @Nullable ObjLongConsumer<CaptureResult> captureResultConsumer) {
            Log.d(
                    TAG,
                    "onConfigureSession() called with: virtualCameraSessionConfig = ["
                            + virtualCameraSessionConfig
                            + "], captureResultConsumer = ["
                            + captureResultConsumer
                            + "]");
            mConfiguredSession = virtualCameraSessionConfig;
            mCallbackDelegate.onConfigureSession(virtualCameraSessionConfig, captureResultConsumer);
        }

        @Override
        public void onStreamConfigured(
                int streamId, @NonNull Surface surface, int width, int height, int format) {
            Log.d(
                    TAG,
                    "onStreamConfigured() called with: streamId = ["
                            + streamId
                            + "], surface = ["
                            + surface
                            + "], width = ["
                            + width
                            + "], height = ["
                            + height
                            + "], format = ["
                            + format
                            + "]");
            mConfiguredStreams.put(streamId, surface);
            mCallbackDelegate.onStreamConfigured(streamId, surface, width, height, format);
            mSessionConfiguredLatch.countDown();
        }

        @Override
        public void onStreamClosed(int streamId) {
            Log.d(TAG, "onStreamClosed() called with: streamId = [" + streamId + "]");
            mConfiguredStreams.remove(streamId);
            mCallbackDelegate.onStreamClosed(streamId);
        }

        @Override
        public void onProcessCaptureRequest(int streamId, long frameId) {
            Log.d(
                    TAG,
                    "onProcessCaptureRequest() called with: streamId = ["
                            + streamId
                            + "], frameId = ["
                            + frameId
                            + "]");
            incrementCaptureCount();
            mCallbackDelegate.onProcessCaptureRequest(streamId, frameId);
        }

        @Override
        public void onProcessCaptureRequest(
                int streamId, long frameId, @Nullable CaptureRequest captureRequest) {
            Log.d(
                    TAG,
                    "onProcessCaptureRequest() called with: streamId = ["
                            + streamId
                            + "], frameId = ["
                            + frameId
                            + "], captureRequest = ["
                            + captureRequest
                            + "]");
            if (captureRequest != null) {
                synchronized (mCaptureRequests) {
                    mCaptureRequests.add(captureRequest);
                }
            }
            incrementCaptureCount();
            mCallbackDelegate.onProcessCaptureRequest(streamId, frameId, captureRequest);
        }

        @Override
        public void onOpenCamera() {
            mCallbackDelegate.onOpenCamera();
        }

        private void incrementCaptureCount() {
            mCaptureRequestCount++;
            CountDownLatch latch = mCaptureLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        /** Wait for {@code count} capture to be finished. */
        public void waitForCapture(int count) {
            if (count >= mCaptureRequestCount) {
                return;
            }
            if (mCaptureLatch.get() != null) {
                throw new IllegalStateException("Already waiting for capture complete");
            }
            CountDownLatch latch = new CountDownLatch(count - mCaptureRequestCount);
            mCaptureLatch.set(latch);
            try {
                if (!latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException("Timed out waiting for capture complete");
                }
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        /** Wait for the call to {@link TestVirtualCameraCallback#onConfigureSession} to return. */
        public void waitForSessionConfigured() {
            try {
                if (!mSessionConfiguredLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException("Timed out waiting for capture complete");
                }
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        /** Returns the currently configured session. */
        @Nullable
        public VirtualCameraSessionConfig getConfiguredSession() {
            return mConfiguredSession;
        }

        /** Returns the number of stream configured */
        public int getConfiguredStreamCount() {
            return mConfiguredStreams.size();
        }

        /** Returns the list of all collected capture requests. */
        @NonNull
        public List<CaptureRequest> getCaptureRequests() {
            synchronized (mCaptureRequests) {
                return List.copyOf(mCaptureRequests);
            }
        }
    }
}
