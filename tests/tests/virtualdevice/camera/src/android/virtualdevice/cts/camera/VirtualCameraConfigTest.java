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

package android.virtualdevice.cts.camera;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_180;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.assertVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.assertVirtualCameraConfigFromCharacteristics;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.getMaximumTextureSize;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.CameraCharacteristicsBuilder;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Parcel;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Range;
import android.util.Size;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraConfigTest {

    private static final String CAMERA_NAME = "Virtual Camera";
    private static final String CAMERA_NAME_EXTERNAL_1 = "First Virtual External Camera";
    private static final String CAMERA_NAME_EXTERNAL_2 = "Second Virtual External Camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = YUV_420_888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final int CAMERA_SENSOR_ORIENTATION = SENSOR_ORIENTATION_0;
    private static final int CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC = SENSOR_ORIENTATION_180;
    private static final int CAMERA_LENS_FACING = LENS_FACING_FRONT;
    private static final int CAMERA_LENS_FACING_CHARACTERISTIC = LENS_FACING_BACK;
    private static final int[] CAMERA_CONTROL_AE_MODES_CHARACTERISTIC = {CONTROL_AE_MODE_ON};
    private static final Float CAMERA_MAX_ZOOM_CHARACTERISTIC = 10.5f;
    private static final int CAMERA_INVALID_LENS_FACING = 5;
    private static final boolean CAMERA_PER_FRAME_METADATA_ENABLED = true;

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private VirtualCameraCallback mCallback;

    private VirtualDevice mVirtualDevice;
    private int mMaximumTextureSize;

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        mMaximumTextureSize = getMaximumTextureSize();
    }

    @Test
    public void virtualCameraConfigBuilder_buildsCorrectConfig() {
        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .setLensFacing(CAMERA_LENS_FACING)
                .build();

        assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallWidth_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(0 /* width */, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_largestWidth_succeeds() throws Exception {
        mVirtualDevice.createVirtualCamera(
            new VirtualCameraConfig.Builder(CAMERA_NAME)
                    .addStreamConfig(mMaximumTextureSize, CAMERA_HEIGHT, CAMERA_FORMAT,
                    CAMERA_MAX_FPS)
                    .setVirtualCameraCallback(mExecutor, mCallback)
                    .setLensFacing(CAMERA_LENS_FACING)
                    .build());
    }

    @Test
    public void virtualCameraConfig_tooLargeWidth_throwsException() throws Exception {
        assertThrows(ServiceSpecificException.class,
                () -> mVirtualDevice.createVirtualCamera(
                        new VirtualCameraConfig.Builder(CAMERA_NAME)
                                .addStreamConfig(mMaximumTextureSize + 1, CAMERA_HEIGHT,
                                        CAMERA_FORMAT, CAMERA_MAX_FPS)
                                .setVirtualCameraCallback(mExecutor, mCallback)
                                .setLensFacing(CAMERA_LENS_FACING)
                                .build()));
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallHeight_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, 0 /* height */, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_largestHeight_succeeds() throws Exception {
        mVirtualDevice.createVirtualCamera(
                new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, mMaximumTextureSize, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_tooLargeHeight_throwsException() throws Exception {
        assertThrows(ServiceSpecificException.class,
                () -> mVirtualDevice.createVirtualCamera(
                        new VirtualCameraConfig.Builder(CAMERA_NAME)
                                .addStreamConfig(CAMERA_WIDTH, mMaximumTextureSize + 1,
                                        CAMERA_FORMAT, CAMERA_MAX_FPS)
                                .setVirtualCameraCallback(mExecutor, mCallback)
                                .setLensFacing(CAMERA_LENS_FACING)
                                .build()));
    }

    @Test
    public void virtualCameraConfigBuilder_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, -1 /* format */,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooLowMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                0 /* maximumFramesPerSecond */)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooHighMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                100 /* maximumFramesPerSecond */)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(null /* name */)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullCallback_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, null /* callback */)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullExecutor_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(null /* executor */, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_missingLensFacing_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void virtualCameraConfigBuilder_unsupportedLensFacing_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setLensFacing(LENS_FACING_EXTERNAL)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_invalidLensFacing_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setLensFacing(CAMERA_INVALID_LENS_FACING)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void virtualCameraConfigBuilder_multipleExternalCamera_succeeds() {
        VirtualCameraConfig config1 = new VirtualCameraConfig.Builder(CAMERA_NAME_EXTERNAL_1)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .setLensFacing(LENS_FACING_EXTERNAL)
                .build();

        assertVirtualCameraConfig(config1, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, LENS_FACING_EXTERNAL,
                CAMERA_NAME_EXTERNAL_1);

        VirtualCameraConfig config2 = new VirtualCameraConfig.Builder(CAMERA_NAME_EXTERNAL_2)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .setLensFacing(LENS_FACING_EXTERNAL)
                .build();

        assertVirtualCameraConfig(config2, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, LENS_FACING_EXTERNAL,
                CAMERA_NAME_EXTERNAL_2);
    }

    @Test
    public void parcelAndUnparcel_matches() {
        VirtualCameraConfig original = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING,
                CAMERA_NAME, mExecutor, mCallback);

        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        final VirtualCameraConfig recreated =
                VirtualCameraConfig.CREATOR.createFromParcel(parcel);

        assertVirtualCameraConfig(recreated, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void buildCameraCharacteristics_matches() {
        CameraCharacteristics characteristics = new CameraCharacteristicsBuilder()
                .set(CameraCharacteristics.LENS_FACING, CAMERA_LENS_FACING_CHARACTERISTIC)
                .set(CameraCharacteristics.SENSOR_ORIENTATION,
                        CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC)
                .set(CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
                .set(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                        CAMERA_CONTROL_AE_MODES_CHARACTERISTIC)
                .set(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
                        CAMERA_MAX_ZOOM_CHARACTERISTIC)
                .build();

        assertThat(characteristics.get(CameraCharacteristics.LENS_FACING))
                .isEqualTo(CAMERA_LENS_FACING_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION))
                .isEqualTo(CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                .isEqualTo(true);
        assertThat(characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES))
                .isEqualTo(CAMERA_CONTROL_AE_MODES_CHARACTERISTIC);
        assertThat(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))
                .isEqualTo(CAMERA_MAX_ZOOM_CHARACTERISTIC);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void parcelAndUnparcelCharacteristics_matches() {
        CameraCharacteristics characteristics = new CameraCharacteristicsBuilder()
                .set(CameraCharacteristics.LENS_FACING, CAMERA_LENS_FACING_CHARACTERISTIC)
                .set(CameraCharacteristics.SENSOR_ORIENTATION,
                        CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC)
                .build();

        VirtualCameraConfig original = new VirtualCameraConfig.Builder(CAMERA_NAME)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setLensFacing(LENS_FACING_BACK)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .setPerFrameCameraMetadataEnabled(CAMERA_PER_FRAME_METADATA_ENABLED)
                .setCameraCharacteristics(characteristics)
                .build();

        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        final VirtualCameraConfig recreated = VirtualCameraConfig.CREATOR.createFromParcel(parcel);

        assertVirtualCameraConfig(recreated, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, LENS_FACING_BACK, CAMERA_NAME);
        assertThat(recreated.isPerFrameCameraMetadataEnabled())
                .isEqualTo(CAMERA_PER_FRAME_METADATA_ENABLED);
        assertThat(recreated.getCameraCharacteristics().get(CameraCharacteristics.LENS_FACING))
                .isEqualTo(LENS_FACING_BACK);
        assertThat(recreated.getCameraCharacteristics()
                .get(CameraCharacteristics.SENSOR_ORIENTATION))
                .isEqualTo(CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void conflictingLensFacing_throws() {
        CameraCharacteristics characteristics = new CameraCharacteristicsBuilder()
                .set(CameraCharacteristics.LENS_FACING, CAMERA_LENS_FACING_CHARACTERISTIC)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(LENS_FACING_FRONT)
                        .setCameraCharacteristics(characteristics)
                        .build());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS, Flags.FLAG_VIRTUAL_CAMERA_METADATA})
    public void virtualCameraConfigBuilder_multipleExternalCameraWithCharacteristics_succeeds() {
        CameraCharacteristics characteristics = new CameraCharacteristicsBuilder()
                .set(CameraCharacteristics.LENS_FACING, LENS_FACING_EXTERNAL)
                .set(CameraCharacteristics.SENSOR_ORIENTATION,
                        CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC)
                .build();

        VirtualCameraConfig config1 = new VirtualCameraConfig.Builder(CAMERA_NAME_EXTERNAL_1)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setCameraCharacteristics(characteristics)
                .build();

        assertVirtualCameraConfigFromCharacteristics(config1, CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC,
                LENS_FACING_EXTERNAL, CAMERA_NAME_EXTERNAL_1);

        VirtualCameraConfig config2 = new VirtualCameraConfig.Builder(CAMERA_NAME_EXTERNAL_2)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setCameraCharacteristics(characteristics)
                .build();

        assertVirtualCameraConfigFromCharacteristics(config2, CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION_CHARACTERISTIC,
                LENS_FACING_EXTERNAL, CAMERA_NAME_EXTERNAL_2);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void cameraCharacteristicsBuilder_buildsCopy() {
        final Size expectedPixelArraySize = new Size(1920, 1080);
        final int[] expectedAfModes = {
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CameraCharacteristics.CONTROL_AF_MODE_AUTO
        };
        Range<Integer>[] expectedFpsRanges = new Range[]{
                new Range<>(15, 30),
                new Range<>(20, 30)
        };
        Range<Long> expectedExposureRange = new Range<>(100L, 64000L);

        CameraCharacteristicsBuilder
                characteristicsBuilder = new CameraCharacteristicsBuilder();
        characteristicsBuilder.set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                expectedPixelArraySize);
        characteristicsBuilder.set(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                expectedAfModes);
        characteristicsBuilder.set(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
                expectedFpsRanges);

        CameraCharacteristics characteristics1 = characteristicsBuilder.build();
        assertEquals(expectedPixelArraySize,
                characteristics1.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
        assertArrayEquals(expectedAfModes,
                characteristics1.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));
        assertArrayEquals(expectedFpsRanges,
                characteristics1.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));

        CameraCharacteristicsBuilder secondCharacteristicsBuilder =
                new CameraCharacteristicsBuilder(characteristics1);
        secondCharacteristicsBuilder.set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, null);
        secondCharacteristicsBuilder.set(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE,
                expectedExposureRange);

        CameraCharacteristics characteristics2 = secondCharacteristicsBuilder.build();
        assertNull(characteristics2.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
        assertArrayEquals(expectedAfModes,
                characteristics2.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));
        assertArrayEquals(expectedFpsRanges,
                characteristics2.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        assertEquals(expectedExposureRange,
                characteristics2.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE));
    }
}
