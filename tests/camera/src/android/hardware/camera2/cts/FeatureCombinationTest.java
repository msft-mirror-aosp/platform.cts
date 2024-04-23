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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.AspectRatio;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.JPEG;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.MAXIMUM;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.PREVIEW;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.PRIV;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.S1080P;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.S1440P;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.S720P;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.YUV;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import static android.hardware.camera2.cts.CameraTestUtils.isSessionConfigWithParamsSupported;
import static android.hardware.camera2.cts.CameraTestUtils.isSessionConfigWithParamsSupportedChecked;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.CameraDeviceSetup;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.ImageDropperListener;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageReader;
import android.mediapc.cts.common.CameraRequirement;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for feature combinations.
 */

@RunWith(Parameterized.class)
public final class FeatureCombinationTest extends Camera2AndroidTestCase {
    private static final String TAG = "FeatureCombinationTest";
    private static final int NUM_BUFFERS_BURST = 5;
    private static final int NUM_WARMUP_BUFFERS = 2;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final TestName mTestName = new TestName();

    /**
     * Test for making sure that all expected stream combinations are consistent in that
     * if isSessionConfigWithParamsSupported returns true, session creation and
     * streaming works.
     *
     * Max JPEG size is 1080p due to the Media Performance Class would filter all JPEG
     * resolutions smaller than 1080P.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_FEATURE_COMBINATION_QUERY, Flags.FLAG_CAMERA_DEVICE_SETUP})
    public void testIsSessionConfigurationSupported() throws Exception {
        // Note: This must match the required stream combinations defined in
        // CameraCharacteristcs#INFO_SESSION_CONFIGURATION_QUERY_VERSION.
        final int[][] legacyCombinations = {
            // Simple preview, GPU video processing, or no-preview video recording
            {PRIV, MAXIMUM},
            {PRIV, PREVIEW},
            {PRIV, S1440P},   // 1920 * 1440, 4:3; 2560 * 1440, 16:9
            {PRIV, S1080P},   // 1440 * 1080, 4:3; 1920 * 1080, 16:9
            {PRIV, S720P},    // 960 * 720, 4:3; 1280 * 720, 16:9
            // In-application video/image processing
            {YUV, MAXIMUM},
            {YUV, PREVIEW},
            {YUV, S1440P},
            {YUV, S1080P},
            {YUV, S720P},
            // Standard still imaging.
            {PRIV, PREVIEW, JPEG, MAXIMUM},
            {PRIV, S1440P,  JPEG, MAXIMUM},
            {PRIV, S1080P,  JPEG, MAXIMUM},
            {PRIV, S720P,   JPEG, MAXIMUM},
            {PRIV, S1440P,  JPEG, S1440P},
            {PRIV, S1080P,  JPEG, S1080P},
            {PRIV, S720P,   JPEG, S1080P},
            // In-app processing plus still capture.
            {YUV,  PREVIEW, JPEG, MAXIMUM},
            {YUV,  S1440P,  JPEG, MAXIMUM},
            {YUV,  S1080P,  JPEG, MAXIMUM},
            {YUV,  S720P,   JPEG, MAXIMUM},
            // Standard recording.
            {PRIV, PREVIEW, PRIV, PREVIEW},
            {PRIV, S1440P,  PRIV, S1440P},
            {PRIV, S1080P,  PRIV, S1080P},
            {PRIV, S720P,   PRIV, S720P},
            // Preview plus in-app processing.
            {PRIV, PREVIEW, YUV,  PREVIEW},
            {PRIV, S1440P,  YUV,  S1440P},
            {PRIV, S1080P,  YUV,  S1080P},
            {PRIV, S720P,   YUV,  S720P},
        };

        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticInfo = mAllStaticInfo.get(id);
            if (!staticInfo.isColorOutputSupported()) {
                Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                continue;
            }
            CameraCharacteristics characteristics = staticInfo.getCharacteristics();
            boolean supportSessionConfigurationQuery = characteristics.get(
                    CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION)
                    > Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
            if (!supportSessionConfigurationQuery) {
                Log.i(TAG, "Camera " + id + " doesn't support session configuration query");
                continue;
            }

            openDevice(id);
            CameraDeviceSetup cameraDeviceSetup = mCameraManager.getCameraDeviceSetup(id);

            try {
                for (int[] c : legacyCombinations) {
                    boolean testAspectRatios = false;
                    for (int i = 0; i < c.length; i += 2) {
                        if (c[i + 1] == S1440P || c[i + 1] == S1080P || c[i + 1] == S720P) {
                            testAspectRatios = true;
                            break;
                        }
                    }

                    if (testAspectRatios) {
                        // Test 16:9 version of the combination
                        testIsSessionConfigurationSupported(
                                cameraDeviceSetup, AspectRatio.AR_16_9, c);
                        // Test 4:3 version of the combination
                        testIsSessionConfigurationSupported(
                                cameraDeviceSetup, AspectRatio.AR_4_3, c);
                    } else {
                        testIsSessionConfigurationSupported(
                                cameraDeviceSetup, AspectRatio.ARBITRARY, c);
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private void testIsSessionConfigurationSupported(CameraDeviceSetup cameraDeviceSetup,
            AspectRatio aspectRatio, int[] combination) throws Exception {
        MaxStreamSizes maxStreamSizes = new MaxStreamSizes(mStaticInfo,
                cameraDeviceSetup.getId(), mContext, aspectRatio);

        Set<Long> dynamicRangeProfiles = mStaticInfo.getAvailableDynamicRangeProfilesChecked();
        int[] videoStabilizationModes =
                mStaticInfo.getAvailableVideoStabilizationModesChecked();
        Range<Integer>[] fpsRanges = mStaticInfo.getAeAvailableTargetFpsRangesChecked();
        boolean hasReadoutTimestamp = mStaticInfo.isReadoutTimestampSupported();

        for (Long dynamicProfile : dynamicRangeProfiles) {
            // Setup outputs
            List<OutputConfiguration> outputConfigs = new ArrayList<>();
            List<Pair<OutputConfiguration, Surface>> outputConfigs2Steps = new ArrayList<>();
            List<SurfaceTexture> privTargets = new ArrayList<SurfaceTexture>();
            List<ImageReader> jpegTargets = new ArrayList<ImageReader>();
            List<ImageReader> yuvTargets = new ArrayList<ImageReader>();

            if (dynamicProfile != DynamicRangeProfiles.STANDARD
                    && dynamicProfile != DynamicRangeProfiles.HLG10) {
                // Only test dynamicRangeProfile STANDARD and HLG10
                continue;
            }

            long minFrameDuration = setupConfigurationTargets(combination, maxStreamSizes,
                    privTargets, jpegTargets, yuvTargets, outputConfigs, outputConfigs2Steps,
                    NUM_BUFFERS_BURST, dynamicProfile, /*hasUseCase*/ false);
            if (minFrameDuration == -1) {
                // Stream combination isn't valid.
                continue;
            }

            for (int stabilizationMode : videoStabilizationModes) {
                if (stabilizationMode == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                    // Skip video stabilization mode ON
                    continue;
                }
                for (Range<Integer> fpsRange : fpsRanges) {
                    if ((fpsRange.getUpper() != 60) && (fpsRange.getUpper() != 30)) {
                        // Skip fps ranges that are not 30fps or 60fps.
                        continue;
                    }
                    if (minFrameDuration > 1e9 * 1.01 / fpsRange.getUpper()) {
                        // Skip the fps range because the minFrameDuration cannot meet the
                        // required range
                        continue;
                    }

                    String combinationStr = MaxStreamSizes.combinationToString(combination)
                            + ", dynamicRangeProfile " + dynamicProfile
                            + ", stabilizationMode " + stabilizationMode
                            + ", fpsRange " + fpsRange.toString();

                    boolean haveSession = false;
                    try {
                        CaptureRequest.Builder builder = cameraDeviceSetup.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                stabilizationMode);
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                        CaptureRequest request = builder.build();

                        boolean isSessionConfigSupported = isSessionConfigWithParamsSupported(
                                cameraDeviceSetup, mHandler, outputConfigs,
                                SessionConfiguration.SESSION_REGULAR, request);
                        boolean isIncompleteSessionConfigSupported =
                                isSessionConfigWithParamsSupportedChecked(cameraDeviceSetup,
                                        outputConfigs2Steps, SessionConfiguration.SESSION_REGULAR,
                                        request);
                        mCollector.expectEquals("isSessionConfigurationSupported return value "
                                + "isn't consistent between completed and incompleted "
                                + "SessionConfiguration!", isSessionConfigSupported,
                                isIncompleteSessionConfigSupported);

                        if (!isSessionConfigSupported) {
                            Log.i(TAG, String.format("Session configuration from combination [%s],"
                                    + " not supported", combinationStr));
                            continue;
                        }

                        haveSession =
                                verifyCombinationStreaming(outputConfigs, dynamicProfile,
                                        stabilizationMode, fpsRange, combinationStr,
                                        hasReadoutTimestamp, /*mpc*/false);
                    } catch (Throwable e) {
                        mCollector.addMessage(String.format(
                                "Output combination %s failed due to: %s",
                                combinationStr, e.getMessage()));
                    }
                    if (haveSession) {
                        try {
                            Log.i(TAG, String.format(
                                    "Done with camera %s, config %s, closing session",
                                    cameraDeviceSetup.getId(), combinationStr));
                            stopCapture(/*fast*/false);
                        } catch (Throwable e) {
                            mCollector.addMessage(String.format(
                                    "Closing down for output combination %s failed due to: %s",
                                    combinationStr, e.getMessage()));
                        }
                    }
                }
            }

            for (SurfaceTexture target : privTargets) {
                target.release();
            }
            for (ImageReader target : jpegTargets) {
                target.close();
            }
            for (ImageReader target : yuvTargets) {
                target.close();
            }
        }

    }

    private boolean verifyCombinationStreaming(
            List<OutputConfiguration> outputConfigs, Long dynamicProfile,
            int stabilizationMode, @Nullable Range<Integer> fpsRange,
            String combinationStr, boolean checkReadoutTimeStamp, boolean mpc)
            throws Exception {

        createSessionByConfigs(outputConfigs);
        boolean haveSession = true;

        Set<List<Surface>> surfaceSets = new HashSet<>();
        List<Surface> surfaceList = new ArrayList<>();
        List<Surface> secondarySurfaceList = new ArrayList<>();
        for (OutputConfiguration config : outputConfigs) {
            if (dynamicProfile == config.getDynamicRangeProfile()) {
                surfaceList.add(config.getSurface());
            } else {
                secondarySurfaceList.add(config.getSurface());
            }
        }

        // Check if STANDARD and HLG10 can co-exist. If they co-exist, request
        // all surfaces at the same time. Otherwise, store surfaces with
        // incompatible dynamic range profiles in 2 separate sets, and request
        // the 2 sets separately.
        if (dynamicProfile != DynamicRangeProfiles.STANDARD) {
            CameraCharacteristics characteristics =
                    mStaticInfo.getCharacteristics();
            DynamicRangeProfiles profiles = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
            Set<Long> compatibleProfiles =
                    profiles.getProfileCaptureRequestConstraints(dynamicProfile);
            if (compatibleProfiles.contains(DynamicRangeProfiles.STANDARD)) {
                surfaceList.addAll(secondarySurfaceList);
            } else if (!secondarySurfaceList.isEmpty()) {
                surfaceSets.add(secondarySurfaceList);
            }
        }
        surfaceSets.add(surfaceList);

        for (List<Surface> surfaces : surfaceSets) {
            CaptureRequest.Builder builderForSession = mCamera.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            builderForSession.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    stabilizationMode);
            if (fpsRange != null) {
                builderForSession.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        fpsRange);
            }

            for (Surface s : surfaces) {
                builderForSession.addTarget(s);
            }
            List<CaptureRequest> burst = new ArrayList<>();
            for (int i = 0; i < NUM_BUFFERS_BURST; i++) {
                burst.add(builderForSession.build());
            }

            SimpleCaptureCallback captureCallback = new SimpleCaptureCallback();
            mCameraSession.captureBurst(burst, captureCallback, mHandler);

            List<Long> readoutTimestamps = null;
            if (checkReadoutTimeStamp) {
                readoutTimestamps =
                        captureCallback.getReadoutStartTimestamps(NUM_BUFFERS_BURST);
            }
            CaptureResult[] results = new CaptureResult[NUM_BUFFERS_BURST];

            for (int i = 0; i < NUM_BUFFERS_BURST; i++) {
                CaptureResult result = captureCallback.getCaptureResult(
                        CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                mCollector.expectNotNull("Result is null for combination "
                        + combinationStr, result);
                results[i] = result;

                // Check video stabiliztaion mode
                Integer videoStabilizationMode = result.get(
                        CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
                mCollector.expectEquals(
                        "Stabilization mode doesn't match for combination "
                                + combinationStr,
                        videoStabilizationMode, stabilizationMode, mpc);

                // Check frame rate
                if (fpsRange != null && fpsRange.getUpper().equals(fpsRange.getLower())) {
                    Range<Integer> resultFpsRange = result.get(
                            CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
                    mCollector.expectEquals("resultFpsRange doesn't match for "
                                    + "combination " + combinationStr,
                            resultFpsRange, fpsRange);
                    Long frameDuration = result.get(
                            CaptureResult.SENSOR_FRAME_DURATION);
                    mCollector.expectInRange("frameDuration doesn't match for "
                                    + combinationStr,
                            (double) frameDuration,
                            /*min=*/ (1e9 / fpsRange.getUpper()) * 0.95,
                            /*max=*/ (1e9 / fpsRange.getLower()) * 1.05);

                    // Check readout timestamp intervals to make sure targetFpsRange
                    // is met. Skip for Cuttlefish due to performance reasons.
                    // TODO: (b/321824310) Remove the check once cuttlefish
                    // performance is improved.
                    if (!MediaUtils.onCuttlefish() && i > NUM_WARMUP_BUFFERS
                            && readoutTimestamps != null) {
                        long readoutInterval = readoutTimestamps.get(i)
                                - readoutTimestamps.get(i - 1);
                        mCollector.expectInRange(
                                "Timestamp readout interval doesn't match for "
                                        + combinationStr,
                                (double) readoutInterval,
                                /*min=*/ (1e9 / fpsRange.getUpper()) * 0.95,
                                /*max=*/ (1e9 / fpsRange.getLower()) * 1.05);
                    }
                }
            }
        }
        return haveSession;
    }

    /**
     * Tests whether a combination of streams and the HLG10 dynamic range profile is supported by
     *  the device
     * @param combination the V Perf class combination tested
     * @param cameraDeviceSetup cameraDeviceSetup to call isSessionConfigurationSupported on.
     * @param maxStreamSizes maximum stream sizes for the device being tested
     * @throws Exception on camera2 api call operation failure
     */
    private void testVPerfClassCombination(int[] combination,
                                           @Nullable CameraDeviceSetup cameraDeviceSetup,
                                           MaxStreamSizes maxStreamSizes) throws Exception {
        // Setup outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<SurfaceTexture> privTargets = new ArrayList<SurfaceTexture>();
        List<ImageReader> jpegTargets = new ArrayList<ImageReader>();

        setupConfigurationTargets(combination, maxStreamSizes,
                privTargets, jpegTargets, null/*yuvTargets*/, outputConfigs,
                null/*outputConfigs2Steps*/,
                NUM_BUFFERS_BURST, DynamicRangeProfiles.HLG10, /*hasUseCase*/ false);

        String combinationStr = MaxStreamSizes.combinationToString(combination);

        boolean haveSession = false;
        try {
            if (cameraDeviceSetup != null) {
                CaptureRequest.Builder builder = cameraDeviceSetup.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
                CaptureRequest request = builder.build();

                boolean isSessionConfigSupported = isSessionConfigWithParamsSupported(
                        cameraDeviceSetup, mHandler, outputConfigs,
                        SessionConfiguration.SESSION_REGULAR, request);
                mCollector.expectEquals(String.format(
                        "isSessionConfigurationSupported false for combination %s",
                        combinationStr), isSessionConfigSupported, true, /*mpc*/true);
            }

            haveSession = verifyCombinationStreaming(outputConfigs, DynamicRangeProfiles.HLG10,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION,
                    /*fpsRange*/null, combinationStr,
                    /*checkReadoutTimeStamp*/false, /*mpc*/true);

        } catch (Throwable e) {
            Log.e(TAG, String.format("Output combination %s failed due to: %s",
                            combinationStr, e.getMessage()));
            mCollector.addMPCFailure();
        }
        if (haveSession) {
            try {
                Log.i(TAG, String.format(
                        "Done with camera %s, config %s, closing session",
                        mCamera.getId(), combinationStr));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(String.format(
                        "Closing down for output combination %s failed due to: %s",
                        combinationStr, e.getMessage()));
            }
        }

        for (SurfaceTexture target : privTargets) {
            target.release();
        }
        for (ImageReader target : jpegTargets) {
            target.close();
        }
    }

    @Test
    @AppModeFull(reason = "Media Performance class test not applicable to instant apps")
    @CddTest(requirements = {"2.2.7.2/7.5/H-1-19"})
    @RequiresFlagsEnabled({Flags.FLAG_FEATURE_COMBINATION_QUERY, Flags.FLAG_CAMERA_DEVICE_SETUP})
    public void testVPerfClassRequirements() throws Exception {
        assumeFalse("Media performance class tests not applicable if shell permission is adopted",
                mAdoptShellPerm);
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        CameraRequirement.HLGCombinationRequirement hlgCombinationRequirement =
                pce.addR7_5__H_1_19();
        // Note: This must match the required stream combinations defined in [7.5/H-1-19]
        final int[][] hlg10Combinations = {
                // HLG10 preview + JPEG Snapshot
                {PRIV, S1080P, JPEG, MAXIMUM},
                {PRIV, S720P, JPEG, MAXIMUM},
        };
        String rearId =  CameraTestUtils.getPrimaryRearCamera(mCameraManager,
                getCameraIdsUnderTest());
        if (rearId == null) {
            Log.e(TAG, "Primary rear camera not available");
            hlgCombinationRequirement.setHLGCombinationSupported(false);
            pce.submitAndCheck();
            return;
        }
        try {
            // Check for static characteristics advertising HGL10 support
            openDevice(rearId);
            Set<Long> dynamicRangeProfiles = mStaticInfo.getAvailableDynamicRangeProfilesChecked();
            mCollector.expectEquals(
                    "Primary rear camera does not advertise HLG10 dynamic range profile",
                    dynamicRangeProfiles.contains(DynamicRangeProfiles.HLG10), true,
                    /*mpc*/ true);

            CameraCharacteristics characteristics = mStaticInfo.getCharacteristics();
            boolean supportSessionConfigurationQuery = characteristics.get(
                    CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION)
                    > Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

            CameraDeviceSetup cameraDeviceSetup = null;
            if (supportSessionConfigurationQuery) {
                cameraDeviceSetup = mCameraManager.getCameraDeviceSetup(rearId);
                mCollector.expectTrue("SESSION_CONFIGURATION_QUERY supported but " +
                        " CameraDeviceSetup invalid", cameraDeviceSetup != null);
            }
            // Runtime test of HLG10 combinations mandated by MPC
            for (int[] c : hlg10Combinations) {
                MaxStreamSizes maxStreamSizes = new MaxStreamSizes(mStaticInfo, mCamera.getId(),
                        mContext, AspectRatio.AR_16_9);
                testVPerfClassCombination(c, cameraDeviceSetup, maxStreamSizes);
            }
        } catch (Exception e) {
            Log.i(TAG, String.format("MPC requirement failed due to %s", e.getMessage()));
            mCollector.addMPCFailure();
        } finally {
            closeDevice(rearId);
        }
        hlgCombinationRequirement.setHLGCombinationSupported(mCollector.getMPCStatus());
        pce.submitAndCheck();
    }

    private long setupConfigurationTargets(int[] configs, MaxStreamSizes maxSizes,
            List<SurfaceTexture> privTargets, List<ImageReader> jpegTargets,
            List<ImageReader> yuvTargets, List<OutputConfiguration> outputConfigs,
            @Nullable List<Pair<OutputConfiguration, Surface>> outputConfigs2Steps,
            int numBuffers, Long dynamicProfile, boolean hasUseCase) {
        ImageDropperListener imageDropperListener = new ImageDropperListener();

        long frameDuration = -1;
        for (int i = 0; i < configs.length; i += (hasUseCase ? 3 : 2)) {
            int format = configs[i];
            int sizeLimit = configs[i + 1];

            Size targetSize = null;
            switch (format) {
                case PRIV: {
                    targetSize = maxSizes.getOutputSizeForFormat(PRIV, sizeLimit);
                    SurfaceTexture target = new SurfaceTexture(/*random int*/1);
                    target.setDefaultBufferSize(targetSize.getWidth(), targetSize.getHeight());
                    Surface textureSurface = new Surface(target);
                    OutputConfiguration config = new OutputConfiguration(textureSurface);
                    OutputConfiguration configNoSurface = new OutputConfiguration(targetSize,
                            SurfaceTexture.class);
                    config.setDynamicRangeProfile(dynamicProfile);
                    configNoSurface.setDynamicRangeProfile(dynamicProfile);
                    if (hasUseCase) {
                        config.setStreamUseCase(configs[i + 2]);
                        configNoSurface.setStreamUseCase(configs[i + 2]);
                    }
                    outputConfigs.add(config);
                    if (outputConfigs2Steps != null) {
                        outputConfigs2Steps.add(new Pair<>(configNoSurface, textureSurface));
                    }
                    privTargets.add(target);
                    break;
                }
                case JPEG: {
                    targetSize = maxSizes.getOutputSizeForFormat(JPEG, sizeLimit);
                    ImageReader target = ImageReader.newInstance(
                            targetSize.getWidth(), targetSize.getHeight(), JPEG, numBuffers);
                    target.setOnImageAvailableListener(imageDropperListener, mHandler);
                    OutputConfiguration config = new OutputConfiguration(target.getSurface());
                    OutputConfiguration configNoSurface = new OutputConfiguration(
                            format, targetSize);
                    if (hasUseCase) {
                        config.setStreamUseCase(configs[i + 2]);
                        configNoSurface.setStreamUseCase(configs[i + 2]);
                    }
                    outputConfigs.add(config);
                    if (outputConfigs2Steps != null) {
                        outputConfigs2Steps.add(new Pair<>(configNoSurface, target.getSurface()));
                    }
                    jpegTargets.add(target);
                    break;
                }
                case YUV: {
                    if (dynamicProfile == DynamicRangeProfiles.HLG10) {
                        format = ImageFormat.YCBCR_P010;
                    }
                    targetSize = maxSizes.getOutputSizeForFormat(YUV, sizeLimit);
                    ImageReader target = ImageReader.newInstance(
                            targetSize.getWidth(), targetSize.getHeight(), format, numBuffers);
                    target.setOnImageAvailableListener(imageDropperListener, mHandler);
                    OutputConfiguration config = new OutputConfiguration(target.getSurface());
                    OutputConfiguration configNoSurface = new OutputConfiguration(
                            format, targetSize);
                    config.setDynamicRangeProfile(dynamicProfile);
                    configNoSurface.setDynamicRangeProfile(dynamicProfile);
                    if (hasUseCase) {
                        config.setStreamUseCase(configs[i + 2]);
                        configNoSurface.setStreamUseCase(configs[i + 2]);
                    }
                    outputConfigs.add(config);
                    if (outputConfigs2Steps != null) {
                        outputConfigs2Steps.add(new Pair<>(configNoSurface, target.getSurface()));
                    }
                    yuvTargets.add(target);
                    break;
                }
                default:
                    fail("Unknown output format " + format);
            }

            Map<Size, Long> minFrameDurations =
                    mStaticInfo.getAvailableMinFrameDurationsForFormatChecked(format);
            if (minFrameDurations.containsKey(targetSize)
                    && minFrameDurations.get(targetSize) > frameDuration) {
                frameDuration = minFrameDurations.get(targetSize);
            }
        }

        return frameDuration;
    }
}