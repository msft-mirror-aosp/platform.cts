/*
 * Copyright 2021 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.ImageAndMultiResStreamInfo;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleMultiResolutionImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.StreamCombinationTargets;
import static android.hardware.camera2.cts.CameraTestUtils.checkSessionConfigurationSupported;
import static android.hardware.camera2.cts.CameraTestUtils.getZoomRatiosToTest;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.*;

import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.CameraTestUtils.ZoomDirection;
import android.hardware.camera2.cts.CameraTestUtils.ZoomRange;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.MandatoryStreamCombination;
import android.hardware.camera2.params.MultiResolutionStreamConfigurationMap;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Basic test for MultiResolutionImageReader APIs.
 *
 * <p>Below image formats are tested:</p>
 *
 * <p>YUV_420_888: flexible YUV420, it is mandatory format for camera. </p>
 * <p>JPEG: used for JPEG still capture, also mandatory format. </p>
 * <p>PRIVATE: used for input for private reprocessing.</p>
 * <p>RAW: used for raw capture. </p>
 */

@RunWith(Parameterized.class)
public class MultiResolutionImageReaderTest extends Camera2AndroidTestCase {
    private static final String TAG = "MultiResolutionImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    // Number of frame (for streaming requests) to be verified.
    private static final int NUM_FRAME_VERIFIED = 6;
    // Number of frame (for streaming requests) to be verified with log processing time.
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 5;
    // Capture result timeout
    private static final int WAIT_FOR_RESULT_TIMEOUT_MS = 3000;
    private static final int CAPTURE_TIMEOUT = 1500; //ms
    private static final int CONFIGURE_TIMEOUT = 5000; //ms

    private MultiResolutionImageReader mMultiResolutionImageReader;
    private SimpleMultiResolutionImageReaderListener mListener;

    @Rule
    public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testMultiResolutionCaptureCharacteristics() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (VERBOSE) {
                Log.v(TAG, "Testing multi-resolution capture characteristics for Camera " + id);
            }
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            StreamConfigurationMap config = c.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] outputFormats = config.getOutputFormats();
            int[] capabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean isLogicalCamera = CameraTestUtils.contains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
            boolean isUltraHighResCamera = info.isUltraHighResolutionSensor();
            Set<String> physicalCameraIds = c.getPhysicalCameraIds();

            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution capture.");
                continue;
            }
            if (VERBOSE) {
                Log.v(TAG, "MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP: "
                        + multiResolutionMap.toString());
            }

            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            assertTrue("Camera " + id + " must be a logical multi-camera or ultra high res camera "
                    + "to support multi-resolution capture.",
                    isLogicalCamera || isUltraHighResCamera);

            for (int format : multiResolutionOutputFormats) {
                // Multi-resolution output format must be one of the supports stream configuration
                // map formats, with the exception of RAW. It's valid for the camera device not to
                // support RAW, but the multi-resolution ImageReader does.
                if (format != ImageFormat.RAW_SENSOR && format != ImageFormat.RAW10
                        && format != ImageFormat.RAW12 && format != ImageFormat.RAW_PRIVATE) {
                    assertTrue(String.format("Camera %s: multi-resolution output format %d "
                            + "isn't a supported format", id, format),
                            CameraTestUtils.contains(outputFormats, format));
                }

                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getOutputInfo(format);
                assertTrue(String.format("Camera %s supports %d multi-resolution "
                        + "outputInfo, expected at least 2", id,
                        multiResolutionStreams.size()),
                        multiResolutionStreams.size() >= 2);

                // Make sure that each multi-resolution output stream info has the maximum size
                // for that format.
                for (MultiResolutionStreamInfo streamInfo : multiResolutionStreams) {
                    String physicalCameraId = streamInfo.getPhysicalCameraId();
                    Size streamSize = new Size(streamInfo.getWidth(), streamInfo.getHeight());
                    if (!isLogicalCamera) {
                        assertTrue("Camera " + id + " is ultra high resolution camera, but " +
                                "the multi-resolution stream info camera Id  " + physicalCameraId +
                                " doesn't match", physicalCameraId.equals(id));
                    } else {
                        assertTrue("Camera " + id + "'s multi-resolution output info " +
                                "physical camera id " + physicalCameraId + " isn't valid",
                                physicalCameraIds.contains(physicalCameraId));
                    }

                    Size[] sizes = CameraTestUtils.getSupportedSizeForFormat(format,
                            physicalCameraId, mCameraManager);
                    assertTrue(String.format("Camera %s must "
                            + "support at least one output size for output "
                            + "format %d.", physicalCameraId, format),
                             sizes != null && sizes.length > 0);

                    List<Size> maxSizes = new ArrayList<Size>();
                    maxSizes.add(CameraTestUtils.getMaxSize(sizes));
                    Size[] maxResSizes = CameraTestUtils.getSupportedSizeForFormat(format,
                            physicalCameraId, mCameraManager, /*maxResolution*/true);
                    if (maxResSizes != null && maxResSizes.length > 0) {
                        maxSizes.add(CameraTestUtils.getMaxSize(maxResSizes));
                    }

                    assertTrue(String.format("Camera %s's supported multi-resolution"
                           + " size %s for physical camera %s is not one of the largest "
                           + "supported sizes %s for format %d", id, streamSize,
                           physicalCameraId, maxSizes, format),
                           maxSizes.contains(streamSize));
                }
            }
        }
    }

    @Test
    public void testMultiResolutionImageReaderJpeg() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.JPEG, /*repeating*/false,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderFlexibleYuv() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.YUV_420_888, /*repeating*/false,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderRaw() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.RAW_SENSOR, /*repeating*/false,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderPrivate() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/false,
                                                /*usage*/0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTIRESOLUTION_IMAGEREADER_USAGE_PUBLIC)
    @Test
    public void testMultiResolutionImageReaderPrivateUsage() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/false,
                                                /*usage*/HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTI_RESOLUTION_CONCURRENT_READERS)
    @Test
    public void testMultiResolutionImageReaderPrivateUsageWithBuilder() throws Exception {
        testMultiResolutionImageReaderForFormat(
                ImageFormat.PRIVATE, /*repeating*/
                false,
                /*usage*/ HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
                /*useBuilder*/ true);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingJpeg() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.JPEG, /*repeating*/true,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingFlexibleYuv() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.YUV_420_888, /*repeating*/true,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingRaw() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.RAW_SENSOR, /*repeating*/true,
                                                /*usage*/0);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingPrivate() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/true,
                                                /*usage*/0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTIRESOLUTION_IMAGEREADER_USAGE_PUBLIC)
    @Test
    public void testMultiResolutionImageReaderRepeatingPrivateUsage() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/true,
                                                /*usage*/HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
    }

    /**
     * Test for making sure the mandatory stream combinations work for multi-resolution output.
     */
    @Test
    public void testMultiResolutionMandatoryStreamCombinationTest() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            MandatoryStreamCombination[] combinations = c.get(
                            CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS);
            if (combinations == null) {
                Log.i(TAG, "No mandatory stream combinations for camera: " + id + " skip test");
                continue;
            }
            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution capture.");
                continue;
            }
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            if (multiResolutionOutputFormats.length == 0) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution output capture.");
                continue;
            }

            try {
                openDevice(id);
                for (MandatoryStreamCombination combination : combinations) {
                    if (combination.isReprocessable()) {
                        continue;
                    }

                    List<MandatoryStreamCombination.MandatoryStreamInformation> streamsInfo =
                            combination.getStreamsInformation();
                    for (MandatoryStreamCombination.MandatoryStreamInformation mandateInfo :
                            streamsInfo) {
                        boolean supportMultiResOutput = CameraTestUtils.contains(
                                multiResolutionOutputFormats, mandateInfo.getFormat());
                        if (mandateInfo.isMaximumSize() && supportMultiResOutput)  {
                            testMultiResolutionMandatoryStreamCombination(id, info, combination,
                                    multiResolutionMap);
                            break;
                        }
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTI_RESOLUTION_CONCURRENT_READERS)
    @Test
    public void testMultiResolutionImageReaderConcurrentReaders() throws Exception {
        testMultiResolutionImageReaderConcurrentReadersInternal(
                /*useReadoutTimestamp*/ false, OutputConfiguration.TIMESTAMP_BASE_DEFAULT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTI_RESOLUTION_CONCURRENT_READERS)
    @Test
    public void testMultiResolutionImageReaderConcurrentReadersWithSensorTimestamp()
            throws Exception {
        testMultiResolutionImageReaderConcurrentReadersInternal(
                /*useReadoutTimestamp*/ false, OutputConfiguration.TIMESTAMP_BASE_SENSOR);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTI_RESOLUTION_CONCURRENT_READERS)
    @Test
    public void testMultiResolutionImageReaderConcurrentReadersWithReadoutTimestamp()
            throws Exception {
        testMultiResolutionImageReaderConcurrentReadersInternal(
                /*useReadoutTimestamp*/ true, OutputConfiguration.TIMESTAMP_BASE_DEFAULT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MULTI_RESOLUTION_CONCURRENT_READERS)
    @Test
    public void testMultiResolutionImageReaderConcurrentReadersWithReadoutMonotonicTimestamp()
            throws Exception {
        testMultiResolutionImageReaderConcurrentReadersInternal(
                /*useReadoutTimestamp*/ true, OutputConfiguration.TIMESTAMP_BASE_MONOTONIC);
    }

    private void testMultiResolutionImageReaderConcurrentReadersInternal(
            boolean useReadoutTimestamp, int timestampBase) throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (VERBOSE) {
                Log.v(
                        TAG,
                        "Testing multi-resolution capture with concurrent readers for Camera "
                                + id);
            }
            StaticMetadata info = mAllStaticInfo.get(id);
            if (!info.isReadoutTimestampSupported() && useReadoutTimestamp) {
                Log.i(TAG, "Camera " + id + " doesn't support readout timestamp!");
                continue;
            }
            CameraCharacteristics c = info.getCharacteristics();
            MultiResolutionStreamConfigurationMap multiResolutionMap =
                    c.get(CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution capture.");
                continue;
            }
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            for (int format : multiResolutionOutputFormats) {
                if (multiResolutionMap.isConcurrentReadersSupported(format)) {
                    testMultiResolutionConcurrentReadersForCamera(
                            id,
                            info,
                            multiResolutionMap,
                            format,
                            useReadoutTimestamp,
                            timestampBase);
                } else {
                    testMultiResolutionConcurrentReadersNotSupported(
                            id,
                            info,
                            multiResolutionMap,
                            format,
                            useReadoutTimestamp,
                            timestampBase);
                }
            }
        }
    }

    private static class MultiResOutputSurfacesHolder {
        public List<Surface> outputSurfaces;
        public long timestamp;
        public long frameNumber;

        public MultiResOutputSurfacesHolder(
                List<Surface> outputSurfaces, long timestamp, long frameNumber) {
            this.outputSurfaces = outputSurfaces;
            this.timestamp = timestamp;
            this.frameNumber = frameNumber;
        }
    }

    private static class MultiResOutputSurfacesListener
            implements MultiResolutionImageReader.OnActiveOutputSurfacesListener {
        private final LinkedBlockingQueue<MultiResOutputSurfacesHolder> mQueue =
                new LinkedBlockingQueue<>();

        @Override
        public void onActiveOutputSurfaces(
                java.util.List<android.view.Surface> activeOutputSurfaces,
                long timestamp, long frameNumber) {
            try {
                mQueue.put(new MultiResOutputSurfacesHolder(
                        activeOutputSurfaces, timestamp, frameNumber));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onActiveOutputSurfaces");
            }
        }

        public MultiResOutputSurfacesHolder getOutputSurfaces(long timeoutMs) {
            try {
                long currentTs = -1L;
                MultiResOutputSurfacesHolder outputSurfacesHolder;
                outputSurfacesHolder = mQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (outputSurfacesHolder == null) {
                    throw new RuntimeException(
                            "Wait for an onActiveOutputSurfaces callback timed out in "
                            + timeoutMs + "ms");
                }
                return outputSurfacesHolder;

            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }
    }

    private void testMultiResolutionConcurrentReadersForCamera(
            String cameraId,
            StaticMetadata staticInfo,
            MultiResolutionStreamConfigurationMap multiResolutionMap,
            int format,
            boolean useReadoutTimestamp,
            int timestampBase)
            throws Exception {
        Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                multiResolutionMap.getOutputInfo(format);
        double[] ratiosToTest =
                getZoomRatiosToTest(
                        staticInfo,
                        /*checkSmoothZoom*/ true,
                        ZoomDirection.ZOOM_IN,
                        ZoomRange.RATIO_FULL_RANGE);

        try {
            openDevice(cameraId);

            mMultiResolutionImageReader =
                    new MultiResolutionImageReader.Builder(
                            multiResolutionStreams, format, MAX_NUM_IMAGES)
                        .setConcurrentOutputsEnabled(true)
                        .build();

            mListener =
                    new SimpleMultiResolutionImageReaderListener(
                            mMultiResolutionImageReader, MAX_NUM_IMAGES, /*acquireLatest*/ false);
            mMultiResolutionImageReader.setOnImageAvailableListener(
                    mListener, new HandlerExecutor(mHandler));

            MultiResOutputSurfacesListener surfacesListener = new MultiResOutputSurfacesListener();
            mMultiResolutionImageReader.setOnActiveOutputSurfacesListener(
                    new HandlerExecutor(mHandler), surfacesListener);

            Collection<OutputConfiguration> outputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                            mMultiResolutionImageReader);
            for (OutputConfiguration config : outputConfigs) {
                config.setReadoutTimestampEnabled(useReadoutTimestamp);
                config.setTimestampBase(timestampBase);
            }
            ArrayList<OutputConfiguration> outputConfigsList =
                    new ArrayList<OutputConfiguration>(outputConfigs);

            // Create session
            createSessionByConfigs(outputConfigsList);
            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            assertNotNull("Failed to create captureRequest", captureBuilder);
            captureBuilder.addTarget(mMultiResolutionImageReader.getSurface());

            // Capture images at different zoom ratios
            SimpleCaptureCallback listener = new SimpleCaptureCallback();
            for (Double zoomRatio : ratiosToTest) {
                captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio.floatValue());
                CaptureRequest request = captureBuilder.build();

                mCameraSession.capture(request, listener, mHandler);

                // Validate MultiResolutionImageReader.OnActiveOutputSurfaces
                long nextFrameNumber = listener.getNextCaptureStartFrameNumber();
                MultiResOutputSurfacesHolder outputSurfacesHolder =
                        surfacesListener.getOutputSurfaces(WAIT_FOR_RESULT_TIMEOUT_MS);
                assertNotNull("MultiResolutionImageReader onActiveOutputSurfaces missing!",
                        outputSurfacesHolder);
                List<Surface> outputSurfaces = outputSurfacesHolder.outputSurfaces;
                assertTrue("MultiResolutionImageReader onActiveOutputSurfaces returned "
                        + "0 surfaces. Must be at least 1",
                        outputSurfaces.size() > 0);
                assertTrue(
                        "MultiResolutionImageReader onActiveOutputSurfaces returned "
                                + outputSurfaces.size()
                                + " surfaces. Must be at most "
                                + multiResolutionStreams.size(),
                        outputSurfaces.size() <= multiResolutionStreams.size());
                assertEquals(
                        "MultiResolutionImageReader onActiveOutputSurfaces frameNumber "
                                + outputSurfacesHolder.frameNumber
                                + " doesn't match onCaptureStarted "
                                + "frameNumber "
                                + nextFrameNumber,
                        outputSurfacesHolder.frameNumber,
                        nextFrameNumber);

                /**
                 * For MultiResolutionImageReader with concurrency, the number of images to be
                 * verified could be more than the captureCount, because each sensor capture may
                 * generate concurrent outputs on a single MultiResolutionImageReader. Only validate
                 * matching SENSOR_TIMESTAMP with image timestamp if not using readout timestamp and
                 * timestamp base is DEFAULT or SENSOR.
                 */
                boolean matchSensorTimestamp =
                        !useReadoutTimestamp
                                && (timestampBase == OutputConfiguration.TIMESTAMP_BASE_DEFAULT
                                        || timestampBase
                                                == OutputConfiguration.TIMESTAMP_BASE_SENSOR);

                List<MultiResOutputSurfacesHolder> outputSurfaceHolders = new ArrayList<>();
                outputSurfaceHolders.add(outputSurfacesHolder);
                validateImage(
                        format,
                        multiResolutionStreams,
                        /*numFrameVerified*/ 1,
                        listener,
                        /*repeating*/ false,
                        matchSensorTimestamp,
                        request,
                        outputSurfaceHolders);
            }
        } finally {
            closeDevice(cameraId);

            // Close MultiResolutionImageReader
            if (mMultiResolutionImageReader != null) {
                mMultiResolutionImageReader.close();
            }
            mMultiResolutionImageReader = null;
        }
    }

    private void testMultiResolutionConcurrentReadersNotSupported(
            String cameraId,
            StaticMetadata staticInfo,
            MultiResolutionStreamConfigurationMap multiResolutionMap,
            int format,
            boolean useReadoutTimestamp,
            int timestampBase)
            throws Exception {
        Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                multiResolutionMap.getOutputInfo(format);

        try {
            openDevice(cameraId);
            mMultiResolutionImageReader =
                    new MultiResolutionImageReader.Builder(
                            multiResolutionStreams, format, MAX_NUM_IMAGES)
                        .setConcurrentOutputsEnabled(true)
                        .build();

            Collection<OutputConfiguration> outputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                            mMultiResolutionImageReader);
            for (OutputConfiguration config : outputConfigs) {
                config.setReadoutTimestampEnabled(useReadoutTimestamp);
                config.setTimestampBase(timestampBase);
            }
            ArrayList<OutputConfiguration> outputConfigsList =
                    new ArrayList<OutputConfiguration>(outputConfigs);

            // Create session
            CameraCaptureSession.StateCallback sessionListener =
                    mock(CameraCaptureSession.StateCallback.class);
            CameraCaptureSession session =
                    CameraTestUtils.configureCameraSessionWithConfig(
                            mCamera, outputConfigsList, sessionListener, mHandler);
            verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce())
                    .onConfigureFailed(any(CameraCaptureSession.class));
        } finally {
            closeDevice(cameraId);
        }
    }

    private void testMultiResolutionMandatoryStreamCombination(String cameraId,
            StaticMetadata staticInfo, MandatoryStreamCombination combination,
            MultiResolutionStreamConfigurationMap multiResStreamConfig) throws Exception {
        String log = "Testing multi-resolution mandatory stream combination: " +
                combination.getDescription() + " on camera: " + cameraId;
        Log.i(TAG, log);

        final int TIMEOUT_FOR_RESULT_MS = 1000;
        final int MIN_RESULT_COUNT = 3;

        // Set up outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();

        CameraTestUtils.setupConfigurationTargets(
                combination.getStreamsInformation(),
                targets,
                outputConfigs,
                outputSurfaces,
                MIN_RESULT_COUNT,
                /*substituteY8*/ false, /*substituteHeic*/
                false, /*physicalCameraId*/
                null,
                multiResStreamConfig,
                mHandler);

        boolean haveSession = false;
        try {
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (Surface s : outputSurfaces) {
                requestBuilder.addTarget(s);
            }

            CameraCaptureSession.CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            checkSessionConfigurationSupported(
                    mCamera,
                    mHandler,
                    outputConfigs,
                    /*inputConfig*/ null,
                    SessionConfiguration.SESSION_REGULAR,
                    mCameraManager,
                    true /*defaultSupport*/,
                    String.format(
                            "Session configuration query for multi-res combination: %s failed",
                            combination.getDescription()));

            createSessionByConfigs(outputConfigs);
            haveSession = true;
            CaptureRequest request = requestBuilder.build();
            mCameraSession.setRepeatingRequest(request, mockCaptureCallback, mHandler);

            verify(mockCaptureCallback,
                    timeout(TIMEOUT_FOR_RESULT_MS * MIN_RESULT_COUNT).atLeast(MIN_RESULT_COUNT))
                    .onCaptureCompleted(
                        eq(mCameraSession),
                        eq(request),
                        isA(TotalCaptureResult.class));
            verify(mockCaptureCallback, never()).
                    onCaptureFailed(
                        eq(mCameraSession),
                        eq(request),
                        isA(CaptureFailure.class));

        } catch (Throwable e) {
            mCollector.addMessage(
                    String.format("Mandatory multi-res stream combination: %s failed due: %s",
                    combination.getDescription(), e.getMessage()));
        }
        if (haveSession) {
            try {
                Log.i(TAG, String.format(
                        "Done with camera %s, multi-res combination: %s, closing session",
                        cameraId, combination.getDescription()));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(
                        String.format(
                                "Closing down for multi-res combination: %s failed due to: %s",
                                combination.getDescription(), e.getMessage()));
            }
        }

        targets.close();
    }

    private void testMultiResolutionImageReaderForFormat(int format, boolean repeating, long usage)
            throws Exception {
        testMultiResolutionImageReaderForFormat(format, repeating, usage, /*useBuilder*/ false);
    }

    private void testMultiResolutionImageReaderForFormat(
            int format, boolean repeating, long usage, boolean useBuilder) throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                if (VERBOSE) {
                    Log.v(
                            TAG,
                            "Testing multi-resolution capture for Camera "
                                    + id
                                    + " format "
                                    + format
                                    + " repeating "
                                    + repeating);
                }
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                CameraCharacteristics c = staticInfo.getCharacteristics();

                // Find the supported multi-resolution output stream info for the specified format
                MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                        CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
                if (multiResolutionMap == null) {
                    Log.i(TAG, "Camera " + id + " doesn't support multi-resolution image reader.");
                    continue;
                }
                int[] outputFormats = multiResolutionMap.getOutputFormats();
                if (!CameraTestUtils.contains(outputFormats, format)) {
                    Log.i(TAG, "Camera " + id + " doesn't support multi-resolution image reader "
                            + "for format " + format + " vs " + Arrays.toString(outputFormats));
                    continue;
                }
                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getOutputInfo(format);

               /* Test the multi-resolution ImageReader at different zoom ratios
                 * to give the camera device best chance to switch between
                 * physical cameras.*/
                List<Float> zoomRatios = CameraTestUtils.getCandidateZoomRatios(staticInfo);

                openDevice(id);
                multiResolutionImageReaderFormatTestByCamera(
                        format, multiResolutionStreams, zoomRatios, repeating, usage, useBuilder);
            } finally {
                closeDevice(id);
            }
        }
    }

    private void multiResolutionImageReaderFormatTestByCamera(
            int format,
            Collection<MultiResolutionStreamInfo> multiResolutionStreams,
            List<Float> zoomRatios,
            boolean repeating,
            long usage,
            boolean useBuilder)
            throws Exception {
        try {
            int numFrameVerified = repeating ? NUM_FRAME_VERIFIED : 1;

            // Create multi-resolution ImageReader
            if (usage == 0) {
                mMultiResolutionImageReader = new MultiResolutionImageReader(
                        multiResolutionStreams, format, MAX_NUM_IMAGES);
            } else if (Flags.multiResolutionConcurrentReaders() && useBuilder) {
                mMultiResolutionImageReader =
                        new MultiResolutionImageReader.Builder(
                                multiResolutionStreams, format, MAX_NUM_IMAGES)
                            .setUsage(usage)
                            .build();
            } else {
                mMultiResolutionImageReader = new MultiResolutionImageReader(
                        multiResolutionStreams, format, MAX_NUM_IMAGES, usage);
            }

            mListener = new SimpleMultiResolutionImageReaderListener(
                    mMultiResolutionImageReader, MAX_NUM_IMAGES, repeating);
            mMultiResolutionImageReader.setOnImageAvailableListener(mListener,
                    new HandlerExecutor(mHandler));

            Collection<OutputConfiguration> outputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                    mMultiResolutionImageReader);
            ArrayList<OutputConfiguration> outputConfigsList = new ArrayList<OutputConfiguration>(
                    outputConfigs);

            // Create OutputConfigurations without surfaces
            List<OutputConfiguration> outputConfigs2Steps =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                            multiResolutionStreams, format);
            // Check both OutputConfiguration lists created directly from
            // MultiResolutionImageReader and from MultiResolutionStreamInfo are the same.
            OutputConfiguration.setSurfacesForMultiResolutionOutput(
                    outputConfigs2Steps, mMultiResolutionImageReader);
            // outputConfigs2Steps and outputConfigsList are not equal because their
            // surfaceGenerationId, surfaceGroupdId will not be the same.
            assertEquals(
                    "OutputConfiguration list creates from MultiResolutionImageReader "
                            + "must match the one from MultiResolutionStreamInfo",
                    outputConfigs2Steps.size(),
                    outputConfigsList.size());
            for (int i = 0; i < outputConfigsList.size(); i++) {
                OutputConfiguration outputConfig2Step = outputConfigs2Steps.get(i);
                OutputConfiguration outputConfig = outputConfigsList.get(i);
                assertEquals(
                        "OutputConfigurations' surfaces don't match",
                        outputConfig2Step.getSurfaces(),
                        outputConfig.getSurfaces());
            }

            // Create session
            createSessionByConfigs(outputConfigsList);

            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            assertNotNull("Failed to create captureRequest", captureBuilder);
            captureBuilder.addTarget(mMultiResolutionImageReader.getSurface());

            // Capture images at different zoom ratios
            SimpleCaptureCallback listener = new SimpleCaptureCallback();
            for (Float zoomRatio : zoomRatios) {
                captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
                CaptureRequest request = captureBuilder.build();

                int sequenceId = -1;
                if (repeating) {
                    sequenceId = mCameraSession.setRepeatingRequest(request, listener, mHandler);
                } else {
                    mCameraSession.capture(request, listener, mHandler);
                }

                // Validate images
                validateImage(
                        format,
                        multiResolutionStreams,
                        numFrameVerified,
                        listener,
                        repeating,
                        /*matchSensorTimestamp*/ true,
                        request,
                        /*outputSurfaceHolders*/ null);

                if (repeating) {
                    mCameraSession.stopRepeating();
                    listener.getCaptureSequenceLastFrameNumber(sequenceId, CAPTURE_TIMEOUT);
                    listener.drain();
                }

                // Return all pending images to the ImageReader as the validateImage may
                // take a while to return and there could be many images pending.
                mMultiResolutionImageReader.flush();
                mListener.reset();
            }
        } finally {
            // Close MultiResolutionImageReader
            if (mMultiResolutionImageReader != null) {
                mMultiResolutionImageReader.close();
            }
            mMultiResolutionImageReader = null;
        }
    }

    private void validateImage(
            int format,
            Collection<MultiResolutionStreamInfo> streams,
            int captureCount,
            SimpleCaptureCallback listener,
            boolean repeating,
            boolean matchSensorTimestamp,
            CaptureRequest request,
            List<MultiResOutputSurfacesHolder> outputSurfaceHolders)
            throws Exception {
        assertTrue(outputSurfaceHolders == null || outputSurfaceHolders.size() == captureCount);

        int imageCount = captureCount;
        if (outputSurfaceHolders != null) {
            imageCount =
                    outputSurfaceHolders.stream()
                            .map(s -> s.outputSurfaces)
                            .mapToInt(List::size)
                            .sum();
        }

        // Get active physical camera id in the capture result. Only do the correlation
        // between activePhysicalCameraId with image size for:
        // - single capture for simplicity,
        // - non concurrent case, and
        // - buffer timestamp matches SENSOR_TIMESTAMP
        boolean checkActivePhysicalCameraId =
                (!repeating
                        && mStaticInfo.isActivePhysicalCameraIdSupported()
                        && outputSurfaceHolders == null
                        && matchSensorTimestamp);
        Map<Long, String> timestampToActivePhysicalId = new HashMap<>();
        if (checkActivePhysicalCameraId) {
            for (int i = 0; i < captureCount; i++) {
                CaptureResult result = listener.getCaptureResultForRequest(request, 1);
                String activePhysicalCameraId =
                        result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                mCollector.expectNotNull(
                        "Camera's capture result should contain ACTIVE_PHYSICAL_ID",
                        activePhysicalCameraId);
                long timestamp =
                        CameraTestUtils.getValueNotNull(result, CaptureResult.SENSOR_TIMESTAMP);
                timestampToActivePhysicalId.put(timestamp, activePhysicalCameraId);
            }
        }

        ImageAndMultiResStreamInfo imgAndStreamInfo;
        final int MAX_RETRY_COUNT = 20;
        int retryCount = 0;
        assertNotNull("Image listener is null", mListener);
        int numImageVerified = 0;
        while (numImageVerified < imageCount) {
            imgAndStreamInfo = mListener.getAnyImageAndInfoAvailable(CAPTURE_WAIT_TIMEOUT_MS);
            if (imgAndStreamInfo == null && retryCount < MAX_RETRY_COUNT) {
                // For acquireLatestImage, a null image may be returned.
                retryCount++;
                continue;
            }

            Image img = imgAndStreamInfo.image;
            long imageTimestamp = img.getTimestamp();
            MultiResolutionStreamInfo streamInfoForImage = imgAndStreamInfo.streamInfo;
            Surface readerSurface = imgAndStreamInfo.surface;
            if (checkActivePhysicalCameraId) {
                mCollector.expectTrue(
                        "Image timestamp "
                                + imageTimestamp
                                + " doesn't match "
                                + "any CaptureResult SENSOR_TIMESTAMP!",
                        timestampToActivePhysicalId.containsKey(imageTimestamp));
                mCollector.expectEquals(
                        String.format(
                                "Active physical camera id %s doesn't "
                                        + "match the physical camera id %s for the image",
                                timestampToActivePhysicalId.get(imageTimestamp),
                                streamInfoForImage.getPhysicalCameraId()),
                        timestampToActivePhysicalId.get(imageTimestamp),
                        streamInfoForImage.getPhysicalCameraId());
            }
            mCollector.expectEquals(String.format("Output image width %d doesn't match " +
                    " the expected width %d", img.getWidth(), streamInfoForImage.getWidth()),
                    img.getWidth(), streamInfoForImage.getWidth());
            mCollector.expectEquals(String.format("Output image height %d doesn't match " +
                    " the expected height %d", img.getHeight(), streamInfoForImage.getHeight()),
                    img.getHeight(), streamInfoForImage.getHeight());

            if (format != ImageFormat.PRIVATE) {
                CameraTestUtils.validateImage(img, img.getWidth(), img.getHeight(), format,
                        mDebugFileNameBase);
            } else {
                mCollector.expectEquals(String.format("Output image format %d doesn't match " +
                        "expected format %d", img.getFormat(), format), format, img.getFormat());
            }

            // Make sure the image size is one within streams
            boolean validSize = false;
            for (MultiResolutionStreamInfo streamInfo : streams) {
                if (streamInfoForImage.getPhysicalCameraId().equals(
                        streamInfo.getPhysicalCameraId())
                        && streamInfo.getWidth() == img.getWidth()
                        && streamInfo.getHeight() == img.getHeight()) {
                    validSize = true;
                }
            }
            mCollector.expectTrue(String.format("Camera's physical camera id + image size " +
                    "[%s: %d, %d] must be the supported multi-resolution output streams " +
                    "for current physical camera", streamInfoForImage.getPhysicalCameraId(),
                    img.getWidth(), img.getHeight()), validSize);

            HardwareBuffer hwb = img.getHardwareBuffer();
            assertNotNull("Unable to retrieve the Image's HardwareBuffer", hwb);

            // Find the expected image timestamp and its origin surface based on
            // onActiveOutputSurfaces call, and remove it from the outputSurfaceHolders.
            if (outputSurfaceHolders != null) {
                List<MultiResOutputSurfacesHolder> matchingHolders =
                        outputSurfaceHolders.stream()
                                .filter(
                                        s ->
                                                (s.timestamp == imageTimestamp
                                                        && s.outputSurfaces.contains(
                                                                readerSurface)))
                                .collect(Collectors.toList());
                mCollector.expectTrue(
                        "The output image's timestamp and origin surface "
                                + "doesn't match what's expected.",
                        matchingHolders.size() == 1);

                if (matchingHolders.size() == 1) {
                    MultiResOutputSurfacesHolder matchedHolder = matchingHolders.get(0);
                    if (VERBOSE) {
                        Log.v(
                                TAG,
                                "Remove timestamp " + imageTimestamp + " surface " + readerSurface);
                    }
                    matchedHolder.outputSurfaces.remove(readerSurface);
                    if (matchedHolder.outputSurfaces.size() == 0) {
                        outputSurfaceHolders.remove(matchedHolder);
                    }
                }
            }

            img.close();
            numImageVerified++;
            retryCount = 0;
        }

        if (outputSurfaceHolders != null) {
            // Make sure all expected images for all surfaces are returned.
            mCollector.expectTrue(
                    "Not all expected images are returned!", outputSurfaceHolders.isEmpty());
            for (MultiResOutputSurfacesHolder holder : outputSurfaceHolders) {
                for (Surface surface : holder.outputSurfaces) {
                    Log.e(
                            TAG,
                            "Still expecting timestamp "
                                    + holder.timestamp
                                    + " from surface "
                                    + surface);
                }
            }
        }
    }
}
