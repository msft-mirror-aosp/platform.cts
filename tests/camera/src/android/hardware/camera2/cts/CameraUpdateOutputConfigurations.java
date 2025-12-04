/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.hardware.cts.helpers.CameraUtils.getAvailableSurfaceTexture;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2MultiViewTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.ImageReader;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

@RunWith(Parameterized.class)
@RequiresFlagsEnabled(Flags.FLAG_SEAMLESS_TRANSITIONS)
public class CameraUpdateOutputConfigurations extends Camera2MultiViewTestCase
        implements  SurfaceHolder.Callback{
    private static final String TAG = "UpdateOutputConfigs";
    private final Object mSurfaceViewLock = new Object();
    private final boolean[] mSurfaceViewValid = new boolean[2];
    private ConditionVariable mSurfaceStateDone = new ConditionVariable();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < 2; i++) {
            Camera2MultiViewCtsActivity ctsActivity = mActivityRule.getActivity();
            final SurfaceHolder holder = ctsActivity.getSurfaceView(i).getHolder();
            holder.addCallback(this);
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCameraImageReaderUpdate() throws Exception {
        final int CAPTURE_WAIT_TIMEOUT_MS = 1000;
        ImageReader reader = null;
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                openCamera(id);

                // Create image reader and surface.
                Size size = getOrderedPreviewSizes(id).getFirst();
                CameraTestUtils.ImageDropperListener dropperListener =
                        new CameraTestUtils.ImageDropperListener();
                reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                        ImageFormat.YUV_420_888, 1);
                reader.setOnImageAvailableListener(dropperListener, mHandler);

                // Configure output streams.
                List<OutputConfiguration> outputSurfaces = new ArrayList<>(1);
                OutputConfiguration outConfig = new OutputConfiguration(reader.getSurface());
                outputSurfaces.add(outConfig);

                // Start repeating capture
                CameraTestUtils.SimpleCaptureCallback
                        captureListener = new CameraTestUtils.SimpleCaptureCallback();
                int seqId = startPreviewWithConfigs(id, outputSurfaces, captureListener);

                dropperListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);

                outConfig.makeDeferredAndRemoveSurfaces();
                updateOutputConfigurations(id, outputSurfaces);

                // The affected repeating request must be flagged as complete
                captureListener.getCaptureSequenceLastFrameNumber(seqId, CAPTURE_WAIT_TIMEOUT_MS);

                // Create an entirely new ImageReader that replaces the deferred output
                dropperListener = new CameraTestUtils.ImageDropperListener();
                reader.close();
                reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                        ImageFormat.YUV_420_888, 1);
                reader.setOnImageAvailableListener(dropperListener, mHandler);
                outConfig.addSurface(reader.getSurface());

                updateOutputConfigurations(id, outputSurfaces);

                CaptureRequest.Builder requestBuilder = getCaptureBuilder(id,
                        CameraDevice.TEMPLATE_PREVIEW);
                assertNotNull("Failed to create capture request", requestBuilder);
                requestBuilder.addTarget(reader.getSurface());

                // Start capture
                seqId = capture(id, requestBuilder.build(), captureListener);

                dropperListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
                captureListener.getCaptureSequenceLastFrameNumber(seqId, CAPTURE_WAIT_TIMEOUT_MS);

            } finally {
                closeCamera(id);
                if (reader != null) {
                    reader.close();
                }
            }
        }
    }

    @Test
    public void testCameraSurfaceTextureUpdate() throws Exception {
        final int SURFACE_AVAILABLE_TIMEOUT_MS = 1000;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            boolean is10bitSupported = staticMeta.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT);

            try {
                openCamera(id);

                // Initialize preview surfaces
                Size previewSize = getOrderedPreviewSizes(id).getFirst();
                Surface[] previewSurfaces = new Surface[2];
                Class<?> previewClass = null;
                for (int i = 0; i < 2; i++) {
                    SurfaceTexture previewTexture = getAvailableSurfaceTexture(
                            SURFACE_AVAILABLE_TIMEOUT_MS, mTextureView[i]);
                    assertNotNull("Unable to get preview surface texture",
                            previewTexture);
                    previewTexture.setDefaultBufferSize(previewSize.getWidth(),
                            previewSize.getHeight());
                    previewSurfaces[i] = new Surface(previewTexture);
                    previewClass = previewTexture.getClass();
                }

                testCameraPreviewUpdate(id, previewSurfaces, is10bitSupported, previewClass,
                        previewSize);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testCameraSurfaceTextureSwap() throws Exception {
        final int SURFACE_AVAILABLE_TIMEOUT_MS = 1000;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            try {
                openCamera(id);

                // Initialize preview surfaces
                Size previewSize = getOrderedPreviewSizes(id).getFirst();
                Surface[] previewSurfaces = new Surface[2];
                for (int i = 0; i < 2; i++) {
                    SurfaceTexture previewTexture = getAvailableSurfaceTexture(
                            SURFACE_AVAILABLE_TIMEOUT_MS, mTextureView[i]);
                    assertNotNull("Unable to get preview surface texture",
                            previewTexture);
                    previewTexture.setDefaultBufferSize(previewSize.getWidth(),
                            previewSize.getHeight());
                    previewSurfaces[i] = new Surface(previewTexture);
                }

                testCameraPreviewSwap(id, previewSurfaces, previewSize);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testCameraSurfaceViewUpdate() throws Exception {
        final int SURFACE_AVAILABLE_TIMEOUT_MS = 5000;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            boolean is10bitSupported = staticMeta.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT);

            try {
                openCamera(id);

                // Initialize preview surfaces
                Size previewSize = getOrderedPreviewSizes(id).getFirst();
                Surface[] previewSurfaces = new Surface[2];
                Class<?> previewClass = null;
                for (int i = 0; i < 2; i++) {
                    Camera2MultiViewCtsActivity ctsActivity = mActivityRule.getActivity();
                    final SurfaceHolder holder = ctsActivity.getSurfaceView(i).getHolder();
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            holder.setFixedSize(previewSize.getWidth(), previewSize.getHeight());
                        }
                    });
                    boolean res = waitForSurfaceViewValid(SURFACE_AVAILABLE_TIMEOUT_MS, i);
                    assertTrue("wait for surface state change timed out", res);
                    previewSurfaces[i] = holder.getSurface();
                    assertNotNull("Preview surface is null", previewSurfaces[i]);
                    assertTrue("Preview surface is not valid",
                            previewSurfaces[i].isValid());
                    previewClass = android.view.SurfaceHolder.class;
                }

                testCameraPreviewUpdate(id, previewSurfaces, is10bitSupported, previewClass,
                        previewSize);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testCameraSurfaceViewSwap() throws Exception {
        final int SURFACE_AVAILABLE_TIMEOUT_MS = 5000;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            try {
                openCamera(id);

                // Initialize preview surfaces
                Size previewSize = getOrderedPreviewSizes(id).getFirst();
                Surface[] previewSurfaces = new Surface[2];
                for (int i = 0; i < 2; i++) {
                    Camera2MultiViewCtsActivity ctsActivity = mActivityRule.getActivity();
                    final SurfaceHolder holder = ctsActivity.getSurfaceView(i).getHolder();
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            holder.setFixedSize(previewSize.getWidth(), previewSize.getHeight());
                        }
                    });
                    boolean res = waitForSurfaceViewValid(SURFACE_AVAILABLE_TIMEOUT_MS, i);
                    assertTrue("wait for surface state change timed out", res);
                    previewSurfaces[i] = holder.getSurface();
                    assertNotNull("Preview surface is null", previewSurfaces[i]);
                    assertTrue("Preview surface is not valid",
                            previewSurfaces[i].isValid());
                }

                testCameraPreviewSwap(id, previewSurfaces, previewSize);
            } finally {
                closeCamera(id);
            }
        }
    }

    private void testCameraPreviewUpdate(String cameraId, Surface[] previewSurfaces,
            boolean check10bit, Class<?> previewClass, Size previewSize) throws Exception {
        final int PREVIEW_DURATION_MS = 1000;
        final int SWITCH_ITERATIONS = 5;
        assertNotNull(previewSurfaces);
        assertEquals(2, previewSurfaces.length);

        CameraTestUtils.ImageDropperListener dropperListener =
                new CameraTestUtils.ImageDropperListener();
        ImageReader reader = ImageReader.newInstance(previewSize.getWidth(),
                previewSize.getHeight(), ImageFormat.JPEG, 1);

        try (reader) {
            reader.setOnImageAvailableListener(dropperListener, mHandler);
            // Configure output streams.
            List<OutputConfiguration> outputSurfaces = new ArrayList<>();
            OutputConfiguration preview1Config = new OutputConfiguration(previewSurfaces[0]);
            OutputConfiguration preview2Config = new OutputConfiguration(previewSize, previewClass);
            if (check10bit) {
                preview2Config.setDynamicRangeProfile(DynamicRangeProfiles.HLG10);
            }
            OutputConfiguration stillConfig = new OutputConfiguration(reader.getSurface());
            outputSurfaces.add(preview1Config);
            outputSurfaces.add(preview2Config);
            outputSurfaces.add(stillConfig);

            // Start repeating capture
            CameraTestUtils.SimpleCaptureCallback
                    captureListener = new CameraTestUtils.SimpleCaptureCallback();
            createSessionWithConfigs(cameraId, outputSurfaces);

            List<OutputConfiguration> previewOutputList = new ArrayList<>();
            previewOutputList.add(preview1Config);
            updateRepeatingRequest(cameraId, previewOutputList, captureListener);

            SystemClock.sleep(PREVIEW_DURATION_MS);

            for (int i = 0; i < SWITCH_ITERATIONS; i++) {
                preview1Config.makeDeferredAndRemoveSurfaces();
                preview2Config.addSurface(previewSurfaces[1]);

                updateOutputConfigurations(cameraId, outputSurfaces);

                previewOutputList.clear();
                previewOutputList.add(preview2Config);
                updateRepeatingRequest(cameraId, previewOutputList, captureListener);

                SystemClock.sleep(PREVIEW_DURATION_MS);

                preview1Config.addSurface(previewSurfaces[0]);
                preview2Config.makeDeferredAndRemoveSurfaces();

                updateOutputConfigurations(cameraId, outputSurfaces);

                previewOutputList.clear();
                previewOutputList.add(preview1Config);
                updateRepeatingRequest(cameraId, previewOutputList, captureListener);

                SystemClock.sleep(PREVIEW_DURATION_MS);
            }
        }
    }

    @Test
    public void testJpegRImageReaderUpdate() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isJpegRSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support Jpeg/R, skipping");
                    continue;
                }

                openCamera(id);

                testImageReaderUpdate(id, ImageFormat.JPEG_R, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testDepthJpegImageReaderUpdate() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isDepthJpegSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support DepthJpeg, skipping");
                    continue;
                }

                openCamera(id);

                testImageReaderUpdate(id, ImageFormat.DEPTH_JPEG, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testHEICUltraHDRImageReaderUpdate() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isHeicUltraHdrSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support HEIC_ULTRAHDR, skipping");
                    continue;
                }

                openCamera(id);

                testImageReaderUpdate(id, ImageFormat.HEIC_ULTRAHDR, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    public void testImageReaderUpdate(String id, int format, StaticMetadata staticMeta)
            throws Exception {
        final int CAPTURE_WAIT_TIMEOUT_MS = 1000;
        final int BURST_SIZE = 5;
        ImageReader reader1 = null, reader2 = null;
        try {
            // Create image reader and surfaces.
            Size size= staticMeta.getAvailableSizesForFormatChecked(format,
                    StaticMetadata.StreamDirection.Output)[0];
            CameraTestUtils.ImageDropperListener dropperListener1 =
                    new CameraTestUtils.ImageDropperListener();
            reader1 = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, 1);
            reader1.setOnImageAvailableListener(dropperListener1, mHandler);
            CameraTestUtils.ImageDropperListener dropperListener2 =
                    new CameraTestUtils.ImageDropperListener();
            reader2 = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, 1);
            reader2.setOnImageAvailableListener(dropperListener2, mHandler);

            // Configure output streams.
            List<OutputConfiguration> outputSurfaces = new ArrayList<>(1);
            OutputConfiguration outConfig = new OutputConfiguration(reader1.getSurface());
            outputSurfaces.add(outConfig);
            createSessionWithConfigs(id, outputSurfaces);

            // Start burst capture
            CameraTestUtils.SimpleCaptureCallback
                    captureListener = new CameraTestUtils.SimpleCaptureCallback();
            CaptureRequest.Builder requestBuilder = getCaptureBuilder(id,
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            assertNotNull("Failed to create capture request", requestBuilder);
            requestBuilder.addTarget(reader1.getSurface());
            ArrayList<CaptureRequest> burstList = new ArrayList<>(BURST_SIZE);
            for (int i = 0; i < BURST_SIZE; i++) {
                burstList.add(requestBuilder.build());
            }
            int seqId = captureBurst(id, burstList, captureListener);

            assertTrue(dropperListener1.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS));

            // Switch readers dynamically
            outConfig.makeDeferredAndRemoveSurfaces();
            outConfig.addSurface(reader2.getSurface());
            updateOutputConfigurations(id, outputSurfaces);

            // The affected burst requests must be flagged as complete
            captureListener.getCaptureSequenceLastFrameNumber(seqId, CAPTURE_WAIT_TIMEOUT_MS);

            // The new output must not receive any frames from previous requests
            assertFalse(dropperListener2.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS));

            requestBuilder = getCaptureBuilder(id, CameraDevice.TEMPLATE_STILL_CAPTURE);
            assertNotNull("Failed to create capture request", requestBuilder);
            requestBuilder.addTarget(reader2.getSurface());
            burstList.clear();
            for (int i = 0; i < BURST_SIZE; i++) {
                burstList.add(requestBuilder.build());
            }

            // Start capture
            seqId = captureBurst(id, burstList, captureListener);
            assertTrue(dropperListener2.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS));

            outConfig.makeDeferredAndRemoveSurfaces();
            updateOutputConfigurations(id, outputSurfaces);
            captureListener.getCaptureSequenceLastFrameNumber(seqId, CAPTURE_WAIT_TIMEOUT_MS);
        } finally {
            if (reader1 != null) {
                reader1.close();
            }
            if (reader2 != null) {
                reader2.close();
            }
        }
    }

    @Test
    public void testDeferredJpegRImageReader() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isJpegRSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support Jpeg/R, skipping");
                    continue;
                }

                openCamera(id);
                testDeferredImageReader(id, ImageFormat.JPEG_R, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testDeferredHEICUltraHDRImageReader() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isHeicUltraHdrSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support HEIC_UltraHDR, skipping");
                    continue;
                }

                openCamera(id);
                testDeferredImageReader(id, ImageFormat.HEIC_ULTRAHDR, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    @Test
    public void testDeferredDepthJpegImageReader() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                StaticMetadata staticMeta =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
                if (!staticMeta.isColorOutputSupported()) {
                    continue;
                }

                if (!staticMeta.isDepthJpegSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support DEPTH_JPEG, skipping");
                    continue;
                }

                openCamera(id);
                testDeferredImageReader(id, ImageFormat.DEPTH_JPEG, staticMeta);
            } finally {
                closeCamera(id);
            }
        }
    }

    private void testDeferredImageReader(String id, int format, StaticMetadata staticMeta)
            throws Exception {
        final int CAPTURE_WAIT_TIMEOUT_MS = 1000;
        ImageReader reader = null;
        try {
            // Create image reader and surface.
            Size size= staticMeta.getAvailableSizesForFormatChecked(format,
                    StaticMetadata.StreamDirection.Output)[0];
            CameraTestUtils.ImageDropperListener dropperListener =
                    new CameraTestUtils.ImageDropperListener();
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, 1);
            reader.setOnImageAvailableListener(dropperListener, mHandler);

            // Configure output streams.
            List<OutputConfiguration> outputSurfaces = new ArrayList<>(1);
            OutputConfiguration outConfig = new OutputConfiguration(format, size);
            outputSurfaces.add(outConfig);
            createSessionWithConfigs(id, outputSurfaces);

            // Try to capture
            CameraTestUtils.SimpleCaptureCallback
                    captureListener = new CameraTestUtils.SimpleCaptureCallback();
            CaptureRequest.Builder requestBuilder = getCaptureBuilder(id,
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            assertNotNull("Failed to create capture request", requestBuilder);
            requestBuilder.addTarget(reader.getSurface());
            try {
                capture(id, requestBuilder.build(), captureListener);
                throw new Exception("Camera should not support capture on " + format +
                        " deferred output");
            } catch (IllegalArgumentException e) {
                // Expected
            }

            // Finalize
            outConfig.addSurface(reader.getSurface());
            finalizeOutputConfigs(id, outputSurfaces, captureListener);

            // Capture should be functional now
            capture(id, requestBuilder.build(), captureListener);

            dropperListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void testCameraPreviewSwap(String cameraId, Surface[] previewSurfaces,
            Size previewSize) throws Exception {
        final int PREVIEW_DURATION_MS = 1000;
        final int SWITCH_ITERATIONS = 5;
        assertNotNull(previewSurfaces);
        assertEquals(2, previewSurfaces.length);

        CameraTestUtils.ImageDropperListener dropperListener =
                new CameraTestUtils.ImageDropperListener();
        ImageReader reader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                ImageFormat.JPEG, 1);

        try (reader) {
            reader.setOnImageAvailableListener(dropperListener, mHandler);
            // Configure output streams.
            List<OutputConfiguration> outputSurfaces = new ArrayList<>();
            OutputConfiguration previewConfig = new OutputConfiguration(previewSurfaces[0]);
            OutputConfiguration stillConfig = new OutputConfiguration(reader.getSurface());
            outputSurfaces.add(previewConfig);
            outputSurfaces.add(stillConfig);

            // Start repeating capture
            CameraTestUtils.SimpleCaptureCallback
                    captureListener = new CameraTestUtils.SimpleCaptureCallback();
            createSessionWithConfigs(cameraId, outputSurfaces);

            List<OutputConfiguration> previewOutputList = new ArrayList<>();
            previewOutputList.add(previewConfig);
            updateRepeatingRequest(cameraId, previewOutputList, captureListener);

            SystemClock.sleep(PREVIEW_DURATION_MS);

            for (int i = 0; i < SWITCH_ITERATIONS; i++) {
                previewConfig.makeDeferredAndRemoveSurfaces();
                previewConfig.addSurface(previewSurfaces[1]);
                updateOutputConfigurations(cameraId, outputSurfaces);

                updateRepeatingRequest(cameraId, previewOutputList, captureListener);

                SystemClock.sleep(PREVIEW_DURATION_MS);

                previewConfig.makeDeferredAndRemoveSurfaces();
                previewConfig.addSurface(previewSurfaces[0]);
                updateOutputConfigurations(cameraId, outputSurfaces);

                updateRepeatingRequest(cameraId, previewOutputList, captureListener);

                SystemClock.sleep(PREVIEW_DURATION_MS);
            }
        }
    }

    /**
     * Wait for surface state to become valid
     */
    public boolean waitForSurfaceViewValid(int timeOutMs, int idx) {
        if (idx >= mSurfaceViewValid.length || idx < 0) {
            throw new IllegalArgumentException(
                    String.format("Illegal surface view idx: " + idx));
        }

        if (timeOutMs <= 0) {
            throw new IllegalArgumentException(
                    String.format("timeout(%d) should be a positive number", timeOutMs));
        }

        synchronized(mSurfaceViewLock) {
            if (mSurfaceViewValid[idx]) {
                return true;
            }
        }

        int waitTimeMs = timeOutMs;
        while (waitTimeMs > 0) {
            long startTimeMs = SystemClock.elapsedRealtime();
            if (!mSurfaceStateDone.block(waitTimeMs)) {
                Log.e(TAG, "Wait for surface state " + mSurfaceViewValid[idx] +
                        " timed out after " + timeOutMs + " ms");
                return false;
            } else {
                mSurfaceStateDone.close();
                synchronized(mSurfaceViewLock) {
                    if (mSurfaceViewValid[idx]) {
                        return true;
                    }
                }
            }
            waitTimeMs -= (int) (SystemClock.elapsedRealtime() - startTimeMs);
        }

        // Couldn't get expected surface state
        return false;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.i(TAG, "Surface created");
        synchronized (mSurfaceViewLock) {
            if (holder == mActivityRule.getActivity().getSurfaceView(0).getHolder()) {
                mSurfaceViewValid[0] = true;
            } else if (holder == mActivityRule.getActivity().getSurfaceView(1).getHolder()) {
                mSurfaceViewValid[1] = true;
            } else {
                Log.e(TAG, "Unknown surface created");
            }
        }
        mSurfaceStateDone.open();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
            int height) {
        Log.i(TAG, "Surface changed to: " + width + "x" + height);
        synchronized (mSurfaceViewLock) {
            if (holder == mActivityRule.getActivity().getSurfaceView(0).getHolder()) {
                mSurfaceViewValid[0] = true;
            } else if (holder == mActivityRule.getActivity().getSurfaceView(1).getHolder()) {
                mSurfaceViewValid[1] = true;
            } else {
                Log.e(TAG, "Unknown surface changed");
            }
        }
        mSurfaceStateDone.open();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Surface destroyed");
        synchronized (mSurfaceViewLock) {
            if (holder == mActivityRule.getActivity().getSurfaceView(0).getHolder()) {
                mSurfaceViewValid[0] = false;
            } else if (holder == mActivityRule.getActivity().getSurfaceView(1).getHolder()) {
                mSurfaceViewValid[1] = false;
            } else {
                Log.e(TAG, "Unknown surface destroyed");
            }
        }
        mSurfaceStateDone.open();
    }
}
