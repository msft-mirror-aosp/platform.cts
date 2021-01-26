/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingExtensionSessionCallback;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestRule;
import android.hardware.camera2.params.ExtensionSessionConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Size;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.cts.helpers.CameraUtils.*;

import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CameraExtensionSessionTest extends
        ActivityInstrumentationTestCase2<CameraExtensionTestActivity> {
    private static final String TAG = "CameraExtensionSessionTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final long WAIT_FOR_COMMAND_TO_COMPLETE_MS = 5000;
    private static final long REPEATING_REQUEST_TIMEOUT_MS = 5000;
    public static final int MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS = 10000;

    private Context mContext = null;
    private SurfaceTexture mSurfaceTexture = null;
    public Camera2AndroidTestRule mTestRule = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TextureView textureView = getActivity().getTextureView();
        mContext = getInstrumentation().getTargetContext();
        mTestRule = new Camera2AndroidTestRule(mContext);
        mTestRule.before();

        mSurfaceTexture = getAvailableSurfaceTexture(WAIT_FOR_COMMAND_TO_COMPLETE_MS, textureView);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mTestRule != null) {
            mTestRule.after();
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
    }

    public CameraExtensionSessionTest() {
        super(CameraExtensionTestActivity.class);
    }

    // Verify that camera extension sessions can be created and closed as expected.
    public void testBasicExtensionLifecycle() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                OutputConfiguration outputConfig = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE,
                        new Surface(mSurfaceTexture));
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(outputConfig);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(
                                mock(CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);

                    extensionSession.close();
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }

    // Verify that regular camera sessions close as expected after creating a camera extension
    // session.
    public void testCloseCaptureSession() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                ImageReader privateReader = CameraTestUtils.makeImageReader(maxSize,
                        ImageFormat.PRIVATE, /*maxImages*/ 3, new ImageDropperListener(),
                        mTestRule.getHandler());
                OutputConfiguration privateOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, privateReader.getSurface());
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(privateOutput);
                BlockingSessionCallback regularSessionListener = new BlockingSessionCallback(
                        mock(CameraCaptureSession.StateCallback.class));
                SessionConfiguration regularConfiguration = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputConfigs,
                        new HandlerExecutor(mTestRule.getHandler()), regularSessionListener);

                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                Surface repeatingSurface = new Surface(mSurfaceTexture);
                OutputConfiguration textureOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, repeatingSurface);
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(textureOutput);
                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    mTestRule.getCamera().createCaptureSession(regularConfiguration);

                    CameraCaptureSession session =
                            regularSessionListener
                                    .waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(session);

                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    regularSessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    extensionSession.close();
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    mTestRule.closeImageReader(privateReader);
                }
            }
        }
    }

    // Verify that camera extension sessions close as expected when creating a regular capture
    // session.
    public void testCloseExtensionSession() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                ImageReader privateReader = CameraTestUtils.makeImageReader(maxSize,
                        ImageFormat.PRIVATE, /*maxImages*/ 3, new ImageDropperListener(),
                        mTestRule.getHandler());
                OutputConfiguration privateOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, privateReader.getSurface());
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(privateOutput);
                BlockingSessionCallback regularSessionListener = new BlockingSessionCallback(
                        mock(CameraCaptureSession.StateCallback.class));
                SessionConfiguration regularConfiguration = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputConfigs,
                        new HandlerExecutor(mTestRule.getHandler()), regularSessionListener);

                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                Surface surface = new Surface(mSurfaceTexture);
                OutputConfiguration textureOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, surface);
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(textureOutput);
                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    mTestRule.getCamera().createCaptureSession(regularConfiguration);
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    CameraCaptureSession session =
                            regularSessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    session.close();
                    regularSessionListener.getStateWaiter().waitForState(
                            BlockingSessionCallback.SESSION_CLOSED, SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    mTestRule.closeImageReader(privateReader);
                }
            }
        }
    }

    // Verify camera device query
    public void testGetDevice() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                OutputConfiguration privateOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE,
                        new Surface(mSurfaceTexture));
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(privateOutput);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(
                                mock(CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);

                    assertEquals("Unexpected/Invalid camera device", mTestRule.getCamera(),
                            extensionSession.getDevice());
                } finally {
                    mTestRule.closeDevice(id);
                }

                try {
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                    fail("should get TimeoutRuntimeException due to previously closed camera "
                            + "device");
                } catch (TimeoutRuntimeException e) {
                    // Expected, per API spec we should not receive any further session callbacks
                    // besides the device state 'onClosed' callback.
                }
            }
        }
    }

    // Test case for repeating/stopRepeating on all supported extensions and expected state/capture
    // callbacks.
    public void testRepeatingCapture() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize =
                        CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(),
                        maxSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback simpleCaptureCallback =
                            new SimpleCaptureCallback(captureCallbackMock);
                    CaptureRequest request = captureBuilder.build();
                    int sequenceId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()), simpleCaptureCallback);

                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureProcessStarted(extensionSession, request);

                    extensionSession.stopRepeating();

                    verify(captureCallbackMock,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession, sequenceId);

                    verify(captureCallbackMock, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    assertEquals("The sum of all onProcessStarted and onCaptureFailed" +
                                    " callback calls must match with the number of calls to " +
                                    "onCaptureStarted!",
                            simpleCaptureCallback.getTotalFramesArrived() +
                                    simpleCaptureCallback.getTotalFramesFailed(),
                            simpleCaptureCallback.getTotalFramesStarted());
                    assertTrue(String.format("The last repeating request surface timestamp " +
                                    "%d must be less than or equal to the last " +
                                    "onCaptureStarted " +
                                    "timestamp %d", mSurfaceTexture.getTimestamp(),
                            simpleCaptureCallback.getLastTimestamp()),
                            mSurfaceTexture.getTimestamp() <=
                                    simpleCaptureCallback.getLastTimestamp());
                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                }
            }
        }
    }

    // Test case for multi-frame only capture on all supported extensions and expected state
    // callbacks. Verify still frame output.
    public void testMultiFrameCapture() throws Exception {
        final int IMAGE_COUNT = 10;
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                int captureFormat = ImageFormat.YUV_420_888;
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        captureFormat);
                if (extensionSizes.isEmpty()) {
                    captureFormat = ImageFormat.JPEG;
                    extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                }
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false, 1);
                ImageReader extensionImageReader = CameraTestUtils.makeImageReader(maxSize,
                        captureFormat, /*maxImages*/ 1, imageListener,
                        mTestRule.getHandler());
                Surface imageReaderSurface = extensionImageReader.getSurface();
                OutputConfiguration readerOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, imageReaderSurface);
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(readerOutput);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.addTarget(imageReaderSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);

                    for (int i = 0; i < IMAGE_COUNT; i++) {
                        CaptureRequest request = captureBuilder.build();
                        int sequenceId = extensionSession.capture(request,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                        Image img =
                                imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                        validateImage(img, maxSize.getWidth(), maxSize.getHeight(),
                                captureFormat, null);
                        img.close();

                        verify(captureCallback, times(1))
                                .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                        verify(captureCallback,
                                timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                .onCaptureProcessStarted(extensionSession, request);
                        verify(captureCallback,
                                timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                .onCaptureSequenceCompleted(extensionSession, sequenceId);
                    }

                    verify(captureCallback, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());
                    verify(captureCallback, times(0))
                            .onCaptureFailed(any(CameraExtensionSession.class),
                                    any(CaptureRequest.class));

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    extensionImageReader.close();
                }
            }
        }
    }

    // Test case combined repeating with multi frame capture on all supported extensions.
    // Verify still frame output.
    public void testRepeatingAndCaptureCombined() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                int captureFormat = ImageFormat.YUV_420_888;
                List<Size> captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                        captureFormat);
                if (captureSizes.isEmpty()) {
                    captureFormat = ImageFormat.JPEG;
                    captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                }
                Size captureMaxSize =
                        CameraTestUtils.getMaxSize(captureSizes.toArray(new Size[0]));

                SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false
                        , 1);
                ImageReader extensionImageReader = CameraTestUtils.makeImageReader(
                        captureMaxSize, captureFormat, /*maxImages*/ 1, imageListener,
                        mTestRule.getHandler());
                Surface imageReaderSurface = extensionImageReader.getSurface();
                OutputConfiguration readerOutput = new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, imageReaderSurface);
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(readerOutput);

                // Pick a supported preview/repeating size with aspect ratio close to the
                // multi-frame capture size
                List<Size> repeatingSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxRepeatingSize =
                        CameraTestUtils.getMaxSize(repeatingSizes.toArray(new Size[0]));
                List<Size> previewSizes = getSupportedPreviewSizes(id,
                        mTestRule.getCameraManager(),
                        getPreviewSizeBound(mTestRule.getWindowManager(), PREVIEW_SIZE_BOUND));
                List<Size> supportedPreviewSizes =
                        previewSizes.stream().filter(repeatingSizes::contains).collect(
                                Collectors.toList());
                if (!supportedPreviewSizes.isEmpty()) {
                    float targetAr =
                            ((float) captureMaxSize.getWidth()) / captureMaxSize.getHeight();
                    for (Size s : supportedPreviewSizes) {
                        float currentAr = ((float) s.getWidth()) / s.getHeight();
                        if (Math.abs(targetAr - currentAr) < 0.01) {
                            maxRepeatingSize = s;
                            break;
                        }
                    }
                }

                mSurfaceTexture.setDefaultBufferSize(maxRepeatingSize.getWidth(),
                        maxRepeatingSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);
                outputConfigs.add(new OutputConfiguration(
                        OutputConfiguration.SURFACE_GROUP_ID_NONE, texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);
                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(repeatingCallbackMock);
                    CaptureRequest repeatingRequest = captureBuilder.build();
                    int repeatingSequenceId =
                            extensionSession.setRepeatingRequest(repeatingRequest,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    repeatingCaptureCallback);

                    Thread.sleep(REPEATING_REQUEST_TIMEOUT_MS);

                    verify(repeatingCallbackMock, atLeastOnce())
                            .onCaptureStarted(eq(extensionSession), eq(repeatingRequest),
                                    anyLong());
                    verify(repeatingCallbackMock, atLeastOnce())
                            .onCaptureProcessStarted(extensionSession, repeatingRequest);

                    captureBuilder = mTestRule.getCamera().createCaptureRequest(
                            android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.addTarget(imageReaderSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);

                    CaptureRequest captureRequest = captureBuilder.build();
                    int captureSequenceId = extensionSession.capture(captureRequest,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                    Image img =
                            imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                    validateImage(img, captureMaxSize.getWidth(),
                            captureMaxSize.getHeight(), captureFormat, null);
                    img.close();

                    verify(captureCallback, times(1))
                            .onCaptureStarted(eq(extensionSession), eq(captureRequest),
                                    anyLong());
                    verify(captureCallback, times(1))
                            .onCaptureProcessStarted(extensionSession, captureRequest);
                    verify(captureCallback, times(1))
                            .onCaptureSequenceCompleted(extensionSession,
                                    captureSequenceId);
                    verify(captureCallback, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());
                    verify(captureCallback, times(0))
                            .onCaptureFailed(any(CameraExtensionSession.class),
                                    any(CaptureRequest.class));

                    extensionSession.stopRepeating();

                    verify(repeatingCallbackMock,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession, repeatingSequenceId);

                    verify(repeatingCallbackMock, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    assertEquals("The sum of onCaptureProcessStarted and onCaptureFailed" +
                                    " callbacks must match with the number of calls to " +
                                    "onCaptureStarted!",
                            repeatingCaptureCallback.getTotalFramesArrived() +
                                    repeatingCaptureCallback.getTotalFramesFailed(),
                            repeatingCaptureCallback.getTotalFramesStarted());
                    assertTrue(String.format("The last repeating request surface timestamp " +
                                    "%d must be less than or equal to the last " +
                                    "onCaptureStarted " +
                                    "timestamp %d", mSurfaceTexture.getTimestamp(),
                            repeatingCaptureCallback.getLastTimestamp()),
                            mSurfaceTexture.getTimestamp() <=
                                    repeatingCaptureCallback.getLastTimestamp());

                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                    extensionImageReader.close();
                }
            }
        }
    }

    public static class SimpleCaptureCallback
            extends CameraExtensionSession.ExtensionCaptureCallback {
        private long mLastTimestamp = -1;
        private int mNumFramesArrived = 0;
        private int mNumFramesStarted = 0;
        private int mNumFramesFailed = 0;
        private boolean mNonIncreasingTimestamps = false;
        private final CameraExtensionSession.ExtensionCaptureCallback mProxy;

        public SimpleCaptureCallback(CameraExtensionSession.ExtensionCaptureCallback proxy) {
            mProxy = proxy;
        }

        @Override
        public void onCaptureStarted(CameraExtensionSession session,
                                     CaptureRequest request, long timestamp) {

            if (timestamp < mLastTimestamp) {
                mNonIncreasingTimestamps = true;
            }
            mLastTimestamp = timestamp;
            mNumFramesStarted++;
            if (mProxy != null) {
                mProxy.onCaptureStarted(session, request, timestamp);
            }
        }

        @Override
        public void onCaptureProcessStarted(CameraExtensionSession session,
                                            CaptureRequest request) {
            mNumFramesArrived++;
            if (mProxy != null) {
                mProxy.onCaptureProcessStarted(session, request);
            }
        }

        @Override
        public void onCaptureFailed(CameraExtensionSession session,
                                    CaptureRequest request) {
            mNumFramesFailed++;
            if (mProxy != null) {
                mProxy.onCaptureFailed(session, request);
            }
        }

        @Override
        public void onCaptureSequenceAborted(CameraExtensionSession session,
                                             int sequenceId) {
            if (mProxy != null) {
                mProxy.onCaptureSequenceAborted(session, sequenceId);
            }
        }

        @Override
        public void onCaptureSequenceCompleted(CameraExtensionSession session,
                                               int sequenceId) {
            if (mProxy != null) {
                mProxy.onCaptureSequenceCompleted(session, sequenceId);
            }
        }

        public int getTotalFramesArrived() {
            return mNumFramesArrived;
        }

        public int getTotalFramesStarted() {
            return mNumFramesStarted;
        }

        public int getTotalFramesFailed() {
            return mNumFramesFailed;
        }

        public long getLastTimestamp() throws IllegalStateException {
            if (mNonIncreasingTimestamps) {
                throw new IllegalStateException("Non-monotonically increasing timestamps!");
            }
            return mLastTimestamp;
        }
    }

    public void testIllegalArguments() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorCorrectionSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    try {
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to absent output surfaces");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    }

                    int captureFormat = ImageFormat.YUV_420_888;
                    List<Size> captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                    if (captureSizes.isEmpty()) {
                        captureFormat = ImageFormat.JPEG;
                        captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                                captureFormat);
                    }
                    Size captureMaxSize =
                            CameraTestUtils.getMaxSize(captureSizes.toArray(new Size[0]));

                    mSurfaceTexture.setDefaultBufferSize(1, 1);
                    Surface texturedSurface = new Surface(mSurfaceTexture);
                    outputConfigs.add(new OutputConfiguration(
                            OutputConfiguration.SURFACE_GROUP_ID_NONE, texturedSurface));
                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                    try {
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to illegal repeating request"
                                + " output surface");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    } finally {
                        outputConfigs.clear();
                    }

                    SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false,
                            1);
                    Size invalidCaptureSize = new Size(1, 1);
                    ImageReader extensionImageReader = CameraTestUtils.makeImageReader(
                            invalidCaptureSize, captureFormat, /*maxImages*/ 1,
                            imageListener, mTestRule.getHandler());
                    Surface imageReaderSurface = extensionImageReader.getSurface();
                    OutputConfiguration readerOutput = new OutputConfiguration(
                            OutputConfiguration.SURFACE_GROUP_ID_NONE, imageReaderSurface);
                    outputConfigs.add(readerOutput);
                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                    try{
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to illegal multi-frame"
                                + " request output surface");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    } finally {
                        outputConfigs.clear();
                        extensionImageReader.close();
                    }

                    // Pick a supported preview/repeating size with aspect ratio close to the
                    // multi-frame capture size
                    List<Size> repeatingSizes = extensionChars.getExtensionSupportedSizes(extension,
                            mSurfaceTexture.getClass());
                    Size maxRepeatingSize =
                            CameraTestUtils.getMaxSize(repeatingSizes.toArray(new Size[0]));
                    List<Size> previewSizes = getSupportedPreviewSizes(id,
                            mTestRule.getCameraManager(),
                            getPreviewSizeBound(mTestRule.getWindowManager(), PREVIEW_SIZE_BOUND));
                    List<Size> supportedPreviewSizes =
                            previewSizes.stream().filter(repeatingSizes::contains).collect(
                                    Collectors.toList());
                    if (!supportedPreviewSizes.isEmpty()) {
                        float targetAr =
                                ((float) captureMaxSize.getWidth()) / captureMaxSize.getHeight();
                        for (Size s : supportedPreviewSizes) {
                            float currentAr = ((float) s.getWidth()) / s.getHeight();
                            if (Math.abs(targetAr - currentAr) < 0.01) {
                                maxRepeatingSize = s;
                                break;
                            }
                        }
                    }

                    imageListener = new SimpleImageReaderListener(false, 1);
                    extensionImageReader = CameraTestUtils.makeImageReader(captureMaxSize,
                            captureFormat, /*maxImages*/ 1, imageListener, mTestRule.getHandler());
                    imageReaderSurface = extensionImageReader.getSurface();
                    readerOutput = new OutputConfiguration(OutputConfiguration.SURFACE_GROUP_ID_NONE,
                            imageReaderSurface);
                    outputConfigs.add(readerOutput);

                    mSurfaceTexture.setDefaultBufferSize(maxRepeatingSize.getWidth(),
                            maxRepeatingSize.getHeight());
                    texturedSurface = new Surface(mSurfaceTexture);
                    outputConfigs.add(new OutputConfiguration(
                            OutputConfiguration.SURFACE_GROUP_ID_NONE, texturedSurface));

                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(imageReaderSurface);
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(repeatingCallbackMock);
                    CaptureRequest repeatingRequest = captureBuilder.build();
                    try {
                        extensionSession.setRepeatingRequest(repeatingRequest,
                                new HandlerExecutor(mTestRule.getHandler()),
                                repeatingCaptureCallback);
                        fail("should get IllegalArgumentException due to illegal repeating request"
                                + " output target");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    }

                    captureBuilder = mTestRule.getCamera().createCaptureRequest(
                            android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);

                    CaptureRequest captureRequest = captureBuilder.build();
                    try {
                        extensionSession.capture(captureRequest,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                        fail("should get IllegalArgumentException due to illegal multi-frame"
                                + " request output target");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    }

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    texturedSurface.release();
                    extensionImageReader.close();

                    try {
                        extensionSession.setRepeatingRequest(captureRequest,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }

                    try {
                        extensionSession.stopRepeating();
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }

                    try {
                        extensionSession.capture(captureRequest,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }
}
