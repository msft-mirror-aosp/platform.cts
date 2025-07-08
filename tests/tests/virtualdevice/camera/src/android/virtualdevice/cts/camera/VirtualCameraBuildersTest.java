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

package android.virtualdevice.cts.camera;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertArrayEquals;

import android.companion.virtual.camera.CameraCharacteristicsBuilder;
import android.companion.virtual.camera.CaptureResultBuilder;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Range;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraBuildersTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC =
            VirtualCameraConfig.SENSOR_ORIENTATION_180;
    private static final int CAMERA_LENS_FACING_CHARACTERISTIC =
            CameraCharacteristics.LENS_FACING_BACK;
    private static final int[] CAMERA_CONTROL_AE_MODES_CHARACTERISTIC = {
        CameraCharacteristics.CONTROL_AE_MODE_ON
    };
    private static final Float CAMERA_MAX_ZOOM_CHARACTERISTIC = 10.5f;
    private static final List<CameraCharacteristics.Key<?>> CAMERA_AVAILABLE_CHARACTERISTICS_KEYS =
            List.of(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                    CameraCharacteristics.LENS_FACING,
                    CameraCharacteristics.SENSOR_ORIENTATION);
    private static final List<CaptureRequest.Key<?>> CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS =
            List.of(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE);
    private static final List<CaptureResult.Key<?>> CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS =
            List.of(CaptureResult.CONTROL_AE_LOCK, CaptureResult.CONTROL_AE_STATE);
    private static final List<CaptureRequest.Key<?>> CAMERA_AVAILABLE_SESSION_KEYS =
            List.of(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);
    private static final List<CaptureRequest.Key<?>> CAMERA_UPDATED_AVAILABLE_CAPTURE_REQUEST_KEYS =
            List.of(CaptureRequest.CONTROL_SCENE_MODE);

    // CameraCharacteristicsBuilder
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void buildCameraCharacteristics_matches() {
        CameraCharacteristics characteristics =
                new CameraCharacteristicsBuilder()
                        .set(CameraCharacteristics.LENS_FACING, CAMERA_LENS_FACING_CHARACTERISTIC)
                        .set(CameraCharacteristics.SENSOR_ORIENTATION,
                                CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC)
                        .set(CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
                        .set(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                                CAMERA_CONTROL_AE_MODES_CHARACTERISTIC)
                        .set(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
                                CAMERA_MAX_ZOOM_CHARACTERISTIC)
                        .set(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL)
                        .setAvailableCharacteristicsKeys(CAMERA_AVAILABLE_CHARACTERISTICS_KEYS)
                        .setAvailableCaptureRequestKeys(CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS)
                        .setAvailableCaptureResultKeys(CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS)
                        .setAvailableSessionKeys(CAMERA_AVAILABLE_SESSION_KEYS)
                        .build();

        assertThat(characteristics.get(CameraCharacteristics.LENS_FACING))
                .isEqualTo(CAMERA_LENS_FACING_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION))
                .isEqualTo(CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)).isEqualTo(true);
        assertThat(characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES))
                .isEqualTo(CAMERA_CONTROL_AE_MODES_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))
                .isEqualTo(CAMERA_MAX_ZOOM_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
                .isEqualTo(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL);
        assertThat(characteristics.getAvailableCaptureRequestKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS);
        assertThat(characteristics.getAvailableCaptureResultKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS);
        assertThat(characteristics.getAvailableSessionKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_SESSION_KEYS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void cameraCharacteristicsBuilder_buildsCopy() {
        final Size expectedPixelArraySize = new Size(1920, 1080);
        final int[] expectedAfModes = {
            CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            CameraCharacteristics.CONTROL_AF_MODE_AUTO
        };
        Range<Integer>[] expectedFpsRanges = new Range[] {new Range<>(15, 30), new Range<>(20, 30)};
        Range<Long> expectedExposureRange = new Range<>(100L, 64000L);

        CameraCharacteristicsBuilder characteristicsBuilder =
                new CameraCharacteristicsBuilder()
                        .set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                                expectedPixelArraySize)
                        .set(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES, expectedAfModes)
                        .set(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
                                expectedFpsRanges)
                        .setAvailableCaptureRequestKeys(CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS)
                        .setAvailableCaptureResultKeys(CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS)
                        .setAvailableSessionKeys(CAMERA_AVAILABLE_SESSION_KEYS);

        CameraCharacteristics characteristics1 = characteristicsBuilder.build();
        assertEquals(
                expectedPixelArraySize,
                characteristics1.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
        assertArrayEquals(
                expectedAfModes,
                characteristics1.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));
        assertArrayEquals(
                expectedFpsRanges,
                characteristics1.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        assertThat(characteristics1.getAvailableCaptureRequestKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS);
        assertThat(characteristics1.getAvailableCaptureResultKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS);
        assertThat(characteristics1.getAvailableSessionKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_SESSION_KEYS);

        CameraCharacteristicsBuilder secondCharacteristicsBuilder =
                new CameraCharacteristicsBuilder(characteristics1);
        secondCharacteristicsBuilder.set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, null);
        secondCharacteristicsBuilder.set(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE, expectedExposureRange);
        secondCharacteristicsBuilder.setAvailableCaptureRequestKeys(
                CAMERA_UPDATED_AVAILABLE_CAPTURE_REQUEST_KEYS);
        secondCharacteristicsBuilder.setAvailableSessionKeys(null);

        CameraCharacteristics characteristics2 = secondCharacteristicsBuilder.build();
        assertNull(characteristics2.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
        assertArrayEquals(
                expectedAfModes,
                characteristics2.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));
        assertArrayEquals(
                expectedFpsRanges,
                characteristics2.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        assertEquals(
                expectedExposureRange,
                characteristics2.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE));
        assertThat(characteristics2.getAvailableCaptureRequestKeys())
                .containsAtLeastElementsIn(CAMERA_UPDATED_AVAILABLE_CAPTURE_REQUEST_KEYS);
        assertThat(characteristics2.getAvailableCaptureRequestKeys())
                .containsNoneIn(CAMERA_AVAILABLE_CAPTURE_REQUEST_KEYS);
        assertThat(characteristics2.getAvailableCaptureResultKeys())
                .containsAtLeastElementsIn(CAMERA_AVAILABLE_CAPTURE_RESULT_KEYS);
        assertNull(characteristics2.getAvailableSessionKeys());
    }

    // CaptureResultBuilder
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void buildCaptureResult_matches() {
        CaptureResult captureResult = new CaptureResultBuilder()
                .set(CaptureResult.CONTROL_AE_MODE, CaptureResult.CONTROL_AE_MODE_ON)
                .set(CaptureResult.COLOR_CORRECTION_MODE,
                        CaptureResult.COLOR_CORRECTION_ABERRATION_MODE_OFF)
                .set(CaptureResult.CONTROL_AUTOFRAMING, CaptureResult.CONTROL_AUTOFRAMING_ON)
                .build();

        assertThat(captureResult.get(CaptureResult.CONTROL_AE_MODE))
                .isEqualTo(CaptureResult.CONTROL_AE_MODE_ON);
        assertThat(captureResult.get(CaptureResult.COLOR_CORRECTION_MODE))
                .isEqualTo(CaptureResult.COLOR_CORRECTION_ABERRATION_MODE_OFF);
        assertThat(captureResult.get(CaptureResult.CONTROL_AUTOFRAMING))
                .isEqualTo(CaptureResult.CONTROL_AUTOFRAMING_ON);
        // unset keys should be null
        assertNull(captureResult.get(CaptureResult.CONTROL_AF_MODE));
        assertNull(captureResult.get(CaptureResult.CONTROL_AE_PRIORITY_MODE));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void captureRequestBuilder_buildsCopy() {
        CaptureResultBuilder captureResultBuilder = new CaptureResultBuilder()
                .set(CaptureResult.CONTROL_AE_MODE, CaptureResult.CONTROL_AE_MODE_ON)
                .set(CaptureResult.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_MODE_OFF);

        CaptureResult captureResult1 = captureResultBuilder.build();

        assertThat(captureResult1.get(CaptureResult.CONTROL_AE_MODE))
                .isEqualTo(CaptureResult.CONTROL_AE_MODE_ON);
        assertNull(captureResult1.get(CaptureResult.CONTROL_AE_STATE));
        assertThat(captureResult1.get(CaptureResult.CONTROL_AF_MODE))
                .isEqualTo(CaptureResult.CONTROL_AF_MODE_OFF);

        CaptureResultBuilder secondCaptureResultBuilder = new CaptureResultBuilder(captureResult1);
        secondCaptureResultBuilder.set(CaptureResult.CONTROL_AE_MODE, null);
        secondCaptureResultBuilder.set(
                CaptureResult.CONTROL_AE_STATE, CaptureResult.CONTROL_AE_STATE_LOCKED);
        secondCaptureResultBuilder.set(
                CaptureResult.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_MODE_MACRO);

        CaptureResult captureResult2 = secondCaptureResultBuilder.build();

        assertNull(captureResult2.get(CaptureResult.CONTROL_AE_MODE));
        assertThat(captureResult2.get(CaptureResult.CONTROL_AE_STATE))
                .isEqualTo(CaptureResult.CONTROL_AE_STATE_LOCKED);
        assertThat(captureResult2.get(CaptureResult.CONTROL_AF_MODE))
                .isEqualTo(CaptureResult.CONTROL_AF_MODE_MACRO);
    }
}
