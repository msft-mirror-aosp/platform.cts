/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the Licnse.
 */

package android.mediapc.cts.common;

import android.hardware.camera2.CameraMetadata;
import android.os.Build;

public class CameraRequirement {
    public static class Camera240FpsRequirement extends Requirement {
        private static final String TAG = Camera240FpsRequirement.class.getSimpleName();

        private Camera240FpsRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRear240FpsSupported(boolean rear240FpsSupported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_240FPS_SUPPORTED,
                    rear240FpsSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-9] MUST have a rear-facing primary camera supporting 720p
         * or 1080p @ 240fps.
         */
        public static Camera240FpsRequirement create240FpsReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_240FPS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new Camera240FpsRequirement(RequirementConstants.R7_5__H_1_9, requirement);
        }
    }

    public static class CameraExtensionRequirement extends Requirement {
        private static final String TAG =
                CameraExtensionRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private CameraExtensionRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setCamera2NightExtensionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA2_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA2_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            }
        }

        public void setCameraXNightExtensionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERAX_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERAX_EXTENSION_NIGHT_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-15] MUST support Night mode extensions via both CameraX and
         * Camera2 extensions for primary cameras.
         */
        public static CameraExtensionRequirement createCameraExtensionReq() {
            RequiredMeasurement<Boolean> rearCamera2NightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA2_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontCamera2NightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA2_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            RequiredMeasurement<Boolean> rearCameraXNightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERAX_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontCameraXNightRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERAX_EXTENSION_NIGHT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new CameraExtensionRequirement(RequirementConstants.R7_5__H_1_15,
                    rearCamera2NightRequirement, frontCamera2NightRequirement,
                    rearCameraXNightRequirement, frontCameraXNightRequirement);
        }
    }

    public static class CameraUltraHdrRequirement extends Requirement {
        private static final String TAG = CameraUltraHdrRequirement.class.getSimpleName();

        private CameraUltraHdrRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setFrontCameraUltraHdrSupported(boolean ultraHdrSupported) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_ULTRA_HDR_SUPPORTED,
                    ultraHdrSupported);
        }

        public void setRearCameraUltraHdrSupported(boolean ultraHdrSupported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_ULTRA_HDR_SUPPORTED,
                    ultraHdrSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-20] MUST by default output JPEG_R for the primary rear
         * and primary front cameras in the default camera app.
         **/
        public static CameraUltraHdrRequirement createUltraHdrReq() {

            RequiredMeasurement<Boolean> rearUltraHdrRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_ULTRA_HDR_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            RequiredMeasurement<Boolean> frontUltraHdrRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_ULTRA_HDR_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new CameraUltraHdrRequirement(RequirementConstants.R7_5__H_1_20,
                    rearUltraHdrRequirement, frontUltraHdrRequirement);
        }
    }

    public static class CameraLatencyRequirement extends Requirement {
        private static final String TAG = CameraTimestampSourceRequirement.class.getSimpleName();

        private CameraLatencyRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearCameraLatency(float latency) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_LATENCY, latency);
        }

        public void setFrontCameraLatency(float latency) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_LATENCY, latency);
        }

        /**
         * [2.2.7.2/7.5/H-1-5] MUST have camera2 JPEG capture latency < 1000ms for 1080p resolution
         * as measured by the CTS camera PerformanceTest under ITS lighting conditions
         * (3000K) for both primary cameras.
         */
        public static CameraLatencyRequirement createJpegLatencyReq() {
            RequiredMeasurement<Float> rearJpegLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1000.0f)
                    .build();
            RequiredMeasurement<Float> frontJpegLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1000.0f)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 1000.0f)
                    .build();

            return new CameraLatencyRequirement(RequirementConstants.R7_5__H_1_5,
                    rearJpegLatency, frontJpegLatency);
        }

        /**
         * [2.2.7.2/7.5/H-1-6] MUST have camera2 startup latency (open camera to first
         * preview frame) < 600ms (S and below) or 500ms (T and above) as measured by the CTS camera
         * PerformanceTest under ITS lighting conditions (3000K) for both primary cameras.
         */
        public static CameraLatencyRequirement createLaunchLatencyReq() {
            RequiredMeasurement<Float> rearLaunchLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 500.0f)
                    .build();
            RequiredMeasurement<Float> frontLaunchLatency = RequiredMeasurement
                    .<Float>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_LATENCY)
                    .setPredicate(RequirementConstants.FLOAT_LTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.S, 600.0f)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 500.0f)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 500.0f)
                    .build();

            return new CameraLatencyRequirement(RequirementConstants.R7_5__H_1_6,
                    rearLaunchLatency, frontLaunchLatency);
        }
    }

    public static class CameraRawRequirement extends Requirement {
        private static final String TAG = CameraRawRequirement.class.getSimpleName();

        private CameraRawRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearRawSupported(boolean rearRawSupported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_RAW_SUPPORTED,
                    rearRawSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-8] MUST support CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW and
         * android.graphics.ImageFormat.RAW_SENSOR for the primary back camera.
         */
        public static CameraRawRequirement createRawReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_RAW_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new CameraRawRequirement(RequirementConstants.R7_5__H_1_8, requirement);
        }
    }

    public static class CameraTimestampSourceRequirement extends Requirement {
        private static final String TAG = CameraTimestampSourceRequirement.class.getSimpleName();
        private static final int TIMESTAMP_REALTIME =
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;

        private CameraTimestampSourceRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearCameraTimestampSource(Integer timestampSource) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_TIMESTAMP_SOURCE,
                    timestampSource);
        }

        public void setFrontCameraTimestampSource(Integer timestampSource) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_TIMESTAMP_SOURCE,
                    timestampSource);
        }

        /**
         * [2.2.7.2/7.5/H-1-4] MUST support CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
         * for both primary cameras.
         */
        public static CameraTimestampSourceRequirement createTimestampSourceReq() {
            RequiredMeasurement<Integer> rearTimestampSource = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.REAR_CAMERA_TIMESTAMP_SOURCE)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.S, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, TIMESTAMP_REALTIME)
                    .build();
            RequiredMeasurement<Integer> frontTimestampSource = RequiredMeasurement
                    .<Integer>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_TIMESTAMP_SOURCE)
                    .setPredicate(RequirementConstants.INTEGER_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.S, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, TIMESTAMP_REALTIME)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, TIMESTAMP_REALTIME)
                    .build();

            return new CameraTimestampSourceRequirement(RequirementConstants.R7_5__H_1_4,
                    rearTimestampSource, frontTimestampSource);
        }
    }

    public static class ConcurrentRearFrontRequirement extends Requirement {
        private static final String TAG = ConcurrentRearFrontRequirement.class.getSimpleName();

        private ConcurrentRearFrontRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setConcurrentRearFrontSupported(boolean concurrentRearFrontSupported) {
            this.setMeasuredValue(RequirementConstants.CONCURRENT_REAR_FRONT_SUPPORTED,
                    concurrentRearFrontSupported);
        }

        /**
         * [2.2.7.2/7.5/H-1-11] MUST implement concurrent front-back streaming on primary cameras.
         */
        public static ConcurrentRearFrontRequirement createConcurrentRearFrontReq() {
            RequiredMeasurement<Boolean> requirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.CONCURRENT_REAR_FRONT_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new ConcurrentRearFrontRequirement(RequirementConstants.R7_5__H_1_11,
                    requirement);
        }
    }

    public static class DynamicRangeTenBitsRequirement extends Requirement {
        private static final String TAG =
                DynamicRangeTenBitsRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private DynamicRangeTenBitsRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setDynamicRangeTenBitsSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA_DYNAMIC_TENBITS_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_DYNAMIC_TENBITS_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-16] MUST support DYNAMIC_RANGE_TEN_BIT capability for
         * the primary cameras.
         */
        public static DynamicRangeTenBitsRequirement createDynamicRangeTenBitsReq() {
            RequiredMeasurement<Boolean> rearDynamicRangeTenBitsRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_DYNAMIC_TENBITS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontDynamicRangeTenBitsRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_DYNAMIC_TENBITS_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            return new DynamicRangeTenBitsRequirement(RequirementConstants.R7_5__H_1_16,
                    rearDynamicRangeTenBitsRequirement, frontDynamicRangeTenBitsRequirement);
        }
    }

    public static class HLGCombinationRequirement extends Requirement {
        private static final String TAG =
                HLGCombinationRequirement.class.getSimpleName();

        private HLGCombinationRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setHLGCombinationSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_HLG_COMBINATION_SUPPORTED,
                    supported);
        }

        /**
         * [2.2.7.2/7.5/H-1-19] MUST support PREVIEW_STABILIZATION for
         * 1080p PRIV HLG10 + max-size JPEG
         * 720p PRIV HLG10 + max-size JPEG
         * for primary rear camera
         */
        public static HLGCombinationRequirement createRearHLGCombinationReq() {
            RequiredMeasurement<Boolean> rearHLGCombinationRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_HLG_COMBINATION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            return new HLGCombinationRequirement(RequirementConstants.R7_5__H_1_19,
                    rearHLGCombinationRequirement);
        }
    }

    public static class JpegRRequirement extends Requirement {
        private static final String TAG =
                JpegRRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private JpegRRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setJpegRSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.PRIMARY_REAR_CAMERA_JPEG_R_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.PRIMARY_FRONT_CAMERA_JPEG_R_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-18] MUST support JPEG_R for the primary cameras.
         */
        public static JpegRRequirement createJpegRReq() {
            RequiredMeasurement<Boolean> rearJpegRRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_REAR_CAMERA_JPEG_R_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontJpegRRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_FRONT_CAMERA_JPEG_R_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            return new JpegRRequirement(RequirementConstants.R7_5__H_1_18,
                    rearJpegRRequirement, frontJpegRRequirement);
        }
    }

    public static class FaceDetectionRequirement extends Requirement {
        private static final String TAG =
                FaceDetectionRequirement.class.getSimpleName();

        public static int PRIMARY_REAR_CAMERA = 0;
        public static int PRIMARY_FRONT_CAMERA = 1;

        private FaceDetectionRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setFaceDetectionSupported(int camera, boolean supported) {
            if (camera == PRIMARY_REAR_CAMERA) {
                this.setMeasuredValue(RequirementConstants.REAR_CAMERA_FACE_DETECTION_SUPPORTED,
                        supported);
            } else if (camera == PRIMARY_FRONT_CAMERA) {
                this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_FACE_DETECTION_SUPPORTED,
                        supported);
            }
        }

        /**
         * [2.2.7.2/7.5/H-1-17] MUST support face detection capability
         * (STATISTICS_FACE_DETECT_MODE_SIMPLE or STATISTICS_FACE_DETECT_MODE_FULL) for the primary
         * cameras.
         */
        public static FaceDetectionRequirement createFaceDetectionReq() {
            RequiredMeasurement<Boolean> rearFaceDetectionRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_FACE_DETECTION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontFaceDetectionRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_FACE_DETECTION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            return new FaceDetectionRequirement(RequirementConstants.R7_5__H_1_17,
                    rearFaceDetectionRequirement, frontFaceDetectionRequirement);
        }
    }

    public static class LogicalMultiCameraRequirement extends Requirement {
        private static final String TAG =
                LogicalMultiCameraRequirement.class.getSimpleName();

        private LogicalMultiCameraRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearLogicalMultiCameraReqMet(boolean reqMet) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET,
                    reqMet);
        }

        /**
         * [2.2.7.2/7.5/H-1-13] MUST support LOGICAL_MULTI_CAMERA capability for the primary
         * rear-facing camera if there are greater than 1 RGB rear-facing cameras.
         */
        public static LogicalMultiCameraRequirement createLogicalMultiCameraReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_LOGICAL_MULTI_CAMERA_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new LogicalMultiCameraRequirement(RequirementConstants.R7_5__H_1_13,
                    rearRequirement);
        }
    }

    public static class PreviewStabilizationRequirement extends Requirement {
        private static final String TAG =
                PreviewStabilizationRequirement.class.getSimpleName();

        private PreviewStabilizationRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearPreviewStabilizationSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_PREVIEW_STABILIZATION_SUPPORTED,
                    supported);
        }

        /**
         * [2.2.7.2/7.5/H-1-12] MUST support CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
         * for the primary back camera.
         */
        public static PreviewStabilizationRequirement createPreviewStabilizationReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_PREVIEW_STABILIZATION_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new PreviewStabilizationRequirement(RequirementConstants.R7_5__H_1_12,
                    rearRequirement);
        }
    }

    public static class PrimaryCameraRequirement extends Requirement {
        private static final long MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION = 12000000;
        private static final long MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION = 5000000;
        private static final long MIN_FRONT_SENSOR_R_PERF_CLASS_RESOLUTION = 4000000;
        private static final String TAG = PrimaryCameraRequirement.class.getSimpleName();

        private PrimaryCameraRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setPrimaryCameraSupported(boolean hasPrimaryCamera) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_AVAILABLE,
                    hasPrimaryCamera);
        }

        public void setResolution(long resolution) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_RESOLUTION,
                    resolution);
        }

        public void setVideoSizeReqSatisfied(boolean videoSizeReqSatisfied) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED,
                    videoSizeReqSatisfied);
        }

        public void set720pVideoSizeReqSatisfied(boolean videoSizeReqSatisfied) {
            this.setMeasuredValue(
                    RequirementConstants.PRIMARY_CAMERA_720p_VIDEO_SIZE_REQ_SATISFIED,
                    videoSizeReqSatisfied);
        }

        public void set1080pVideoSizeReqSatisfied(boolean videoSizeReqSatisfied) {
            this.setMeasuredValue(
                    RequirementConstants.PRIMARY_CAMERA_1080p_VIDEO_SIZE_REQ_SATISFIED,
                    videoSizeReqSatisfied);
        }

        public void setVideoFps(double videoFps) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS, videoFps);
        }

        public void set720pVideoFps(double videoFps) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_720p_VIDEO_FPS,
                    videoFps);
        }

        public void set1080pVideoFps(double videoFps) {
            this.setMeasuredValue(RequirementConstants.PRIMARY_CAMERA_1080p_VIDEO_FPS,
                    videoFps);
        }

        /**
         * [2.2.7.2/7.5/H-1-1] MUST have a primary rear facing camera with a resolution of at
         * least 12 megapixels supporting video capture at 4k@30fps
         */
        public static PrimaryCameraRequirement createRearPrimaryCamera() {
            RequiredMeasurement<Boolean> hasPrimaryCamera = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_AVAILABLE)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .build();

            RequiredMeasurement<Long> cameraResolution = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_RESOLUTION)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(Build.VERSION_CODES.S, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.TIRAMISU, MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            MIN_BACK_SENSOR_PERF_CLASS_RESOLUTION)
                    .build();

            RequiredMeasurement<Boolean> videoSizeReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            // Split definitions of 720p and 1080p for future flexibility
            RequiredMeasurement<Boolean> videoSize720pReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_720p_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> videoSize1080pReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_1080p_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Double> videoFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.S, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 29.9)
                    .build();
            RequiredMeasurement<Double> video720pFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_720p_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 59.9)
                    .build();
            RequiredMeasurement<Double> video1080pFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_1080p_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 59.9)
                    .build();

            return new PrimaryCameraRequirement(RequirementConstants.R7_5__H_1_1,
                    hasPrimaryCamera, cameraResolution, videoSizeReqSatisfied,
                    videoFps, videoSize720pReqSatisfied, videoSize1080pReqSatisfied,
                    video720pFps, video1080pFps);
        }

        /**
         * [2.2.7.2/7.5/H-1-2] MUST have a primary front facing camera with a resolution of
         * at least 4 megapixels supporting video capture at 1080p@30fps.
         */
        public static PrimaryCameraRequirement createFrontPrimaryCamera() {
            RequiredMeasurement<Boolean> hasPrimaryCamera = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_AVAILABLE)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            RequiredMeasurement<Long> cameraResolution = RequiredMeasurement
                    .<Long>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_RESOLUTION)
                    .setPredicate(RequirementConstants.LONG_GTE)
                    .addRequiredValue(
                            Build.VERSION_CODES.R, MIN_FRONT_SENSOR_R_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.S, MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.TIRAMISU, MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                            MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .addRequiredValue(
                            Build.VERSION_CODES.VANILLA_ICE_CREAM,
                            MIN_FRONT_SENSOR_S_PERF_CLASS_RESOLUTION)
                    .build();

            RequiredMeasurement<Boolean> videoSizeReqSatisfied = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_SIZE_REQ_SATISFIED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.R, true)
                    .addRequiredValue(Build.VERSION_CODES.S, true)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            RequiredMeasurement<Double> videoFps = RequiredMeasurement
                    .<Double>builder()
                    .setId(RequirementConstants.PRIMARY_CAMERA_VIDEO_FPS)
                    .setPredicate(RequirementConstants.DOUBLE_GTE)
                    .addRequiredValue(Build.VERSION_CODES.R, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.S, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 29.9)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, 29.9)
                    .build();
            return new PrimaryCameraRequirement(RequirementConstants.R7_5__H_1_2,
                    hasPrimaryCamera, cameraResolution, videoSizeReqSatisfied,
                    videoFps);
        }
    }

    public static class StreamUseCaseRequirement extends Requirement {
        private static final String TAG =
                StreamUseCaseRequirement.class.getSimpleName();

        private StreamUseCaseRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearStreamUseCaseSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_STREAM_USECASE_SUPPORTED,
                    supported);
        }

        public void setFrontStreamUseCaseSupported(boolean supported) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_STREAM_USECASE_SUPPORTED,
                    supported);
        }

        /**
         * [2.2.7.2/7.5/H-1-14] MUST support STREAM_USE_CASE capability for both primary
         * front and primary back camera.
         */
        public static StreamUseCaseRequirement createStreamUseCaseReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_STREAM_USECASE_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_STREAM_USECASE_SUPPORTED)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new StreamUseCaseRequirement(RequirementConstants.R7_5__H_1_14,
                    rearRequirement, frontRequirement);
        }
    }

    public static class UltraWideZoomRatioRequirement extends Requirement {
        private static final String TAG =
                UltraWideZoomRatioRequirement.class.getSimpleName();

        private UltraWideZoomRatioRequirement(String id, RequiredMeasurement<?>... reqs) {
            super(id, reqs);
        }

        public void setRearUltraWideZoomRatioReqMet(boolean ultrawideZoomRatioReqMet) {
            this.setMeasuredValue(RequirementConstants.REAR_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET,
                    ultrawideZoomRatioReqMet);
        }

        public void setFrontUltraWideZoomRatioReqMet(boolean ultrawideZoomRatioReqMet) {
            this.setMeasuredValue(RequirementConstants.FRONT_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET,
                    ultrawideZoomRatioReqMet);
        }

        /**
         * [2.2.7.2/7.5/H-1-10] MUST have min ZOOM_RATIO < 1.0 for the primary cameras if
         * there is an ultrawide RGB camera facing the same direction.
         */
        public static UltraWideZoomRatioRequirement createUltrawideZoomRatioReq() {
            RequiredMeasurement<Boolean> rearRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.REAR_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();
            RequiredMeasurement<Boolean> frontRequirement = RequiredMeasurement
                    .<Boolean>builder()
                    .setId(RequirementConstants.FRONT_CAMERA_ULTRAWIDE_ZOOMRATIO_REQ_MET)
                    .setPredicate(RequirementConstants.BOOLEAN_EQ)
                    .addRequiredValue(Build.VERSION_CODES.TIRAMISU, true)
                    .addRequiredValue(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, true)
                    .addRequiredValue(Build.VERSION_CODES.VANILLA_ICE_CREAM, true)
                    .build();

            return new UltraWideZoomRatioRequirement(RequirementConstants.R7_5__H_1_10,
                    rearRequirement, frontRequirement);
        }
    }
}