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

package android.hardware.multiprocess.camera.cts;

import static junit.framework.Assert.*;

import android.content.pm.PackageManager;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.OutputConfiguration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Size;

import androidx.test.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

@RunWith(JUnit4.class)
public class SharedSessionConfigurationTest {
    private static final String TAG = "SharedSessionConfigurationTest";
    private static final String SHARED_SESSION_CONFIG_CLASS_NAME = "android.hardware.camera2.params.SharedSessionConfiguration";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        PackageManager pm = InstrumentationRegistry.getTargetContext().getPackageManager();
        Assume.assumeTrue("Skipping test: not an automotive device.",
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        try {
            Class.forName(SHARED_SESSION_CONFIG_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            Assume.assumeTrue("SharedSessionConfiguration API not found, skipping tests.", false);
        }
    }

    @Test
    public void testConstructorAndGetters_validInput() throws Exception {
        Class<?> sharedSessionConfigClass = Class.forName(SHARED_SESSION_CONFIG_CLASS_NAME);
        int colorSpace = /* ColorSpace.Named.SRGB */ 0;
        long[] sharedOutputConfigs = {
            /* SURFACE_TYPE_SURFACE_VIEW */ 0,
            /* width */ 1920,
            /* height */ 1080,
            ImageFormat.YUV_420_888,
            OutputConfiguration.MIRROR_MODE_NONE,
            /* isReadOutTimestampEnabled */ 1,
            OutputConfiguration.TIMESTAMP_BASE_DEFAULT,
            DataSpace.DATASPACE_SRGB,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_COMPOSER_OVERLAY,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
            /* physicalCameraIdLen */ 1,
            /* physicalCameraId */ '1'
        };
                // Create SharedSessionConfiguration using reflection
        Constructor<?> constructor = sharedSessionConfigClass.getConstructor(int.class, long[].class);
        Object config = constructor.newInstance(colorSpace, sharedOutputConfigs);

        // Call getColorSpace() using reflection
        Method getColorSpaceMethod = sharedSessionConfigClass.getMethod("getColorSpace");
        Object colorSpaceObj = getColorSpaceMethod.invoke(config);
        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), colorSpaceObj);

        // Call getOutputStreamsInformation() using reflection
        Method getOutputStreamsInformationMethod = sharedSessionConfigClass.getMethod("getOutputStreamsInformation");
        List<?> outputs = (List<?>) getOutputStreamsInformationMethod.invoke(config);
        assertEquals(1, outputs.size());

        // Verify the SharedOutputConfiguration object using reflection
        Object outputConfig = outputs.get(0);
        Class<?> sharedOutputConfigClass = outputConfig.getClass();

        assertEquals((int)sharedOutputConfigs[0], ((Number)sharedOutputConfigClass.getMethod("getSurfaceType").invoke(outputConfig)).intValue());
        assertEquals(new Size((int) sharedOutputConfigs[1], (int) sharedOutputConfigs[2]), sharedOutputConfigClass.getMethod("getSize").invoke(outputConfig));
        assertEquals((int)sharedOutputConfigs[3], ((Number)sharedOutputConfigClass.getMethod("getFormat").invoke(outputConfig)).intValue());
        assertEquals((int)sharedOutputConfigs[4], ((Number)sharedOutputConfigClass.getMethod("getMirrorMode").invoke(outputConfig)).intValue());
        assertEquals(sharedOutputConfigs[5] != 0, sharedOutputConfigClass.getMethod("isReadoutTimestampEnabled").invoke(outputConfig));
        assertEquals((int)sharedOutputConfigs[6], ((Number)sharedOutputConfigClass.getMethod("getTimestampBase").invoke(outputConfig)).intValue());
        assertEquals((int)sharedOutputConfigs[7], ((Number)sharedOutputConfigClass.getMethod("getDataspace").invoke(outputConfig)).intValue());
        assertEquals(sharedOutputConfigs[8], ((Number)sharedOutputConfigClass.getMethod("getUsage").invoke(outputConfig)).longValue());
        assertEquals(sharedOutputConfigs[9], ((Number)sharedOutputConfigClass.getMethod("getStreamUseCase").invoke(outputConfig)).longValue());
        assertEquals(Character.toString((char) sharedOutputConfigs[11]), sharedOutputConfigClass.getMethod("getPhysicalCameraId").invoke(outputConfig));
    }

    @Test
    public void testConstructorAndGetters_multipleOutputs() throws Exception {
        Class<?> sharedSessionConfigClass = Class.forName(SHARED_SESSION_CONFIG_CLASS_NAME);
        int colorSpace = /* ColorSpace.Named.ADOBE_RGB */ 10;
        long[] sharedOutputConfigs = {
            /* SURFACE_TYPE_SURFACE_TEXTURE */ 1,
            /* width */ 1280,
            /* height */ 720,
            ImageFormat.JPEG,
            OutputConfiguration.MIRROR_MODE_H,
            /* isReadOutTimestampEnabled */ 0,
            OutputConfiguration.TIMESTAMP_BASE_MONOTONIC,
            DataSpace.DATASPACE_UNKNOWN,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
            /* physicalCameraIdLen */ 1,
            /* physicalCameraId */ '2',
            /* SURFACE_TYPE_MEDIA_RECORDER */ 2,
            /* width */ 640,
            /* height */ 480,
            ImageFormat.PRIVATE,
            OutputConfiguration.MIRROR_MODE_V,
            /* isReadOutTimestampEnabled */ 1,
            OutputConfiguration.TIMESTAMP_BASE_REALTIME,
            DataSpace.DATASPACE_BT2020_PQ,
            HardwareBuffer.USAGE_VIDEO_ENCODE,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
            /* physicalCameraIdLen */ 0
        };

        // Create SharedSessionConfiguration using reflection
        Constructor<?> constructor = sharedSessionConfigClass.getConstructor(int.class, long[].class);
        Object config = constructor.newInstance(colorSpace, sharedOutputConfigs);

        // Call getColorSpace() using reflection
        Method getColorSpaceMethod = sharedSessionConfigClass.getMethod("getColorSpace");
        Object colorSpaceObj = getColorSpaceMethod.invoke(config);
        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), colorSpaceObj);

        // Call getOutputStreamsInformation() using reflection
        Method getOutputStreamsInformationMethod = sharedSessionConfigClass.getMethod("getOutputStreamsInformation");
        List<?> outputs = (List<?>) getOutputStreamsInformationMethod.invoke(config);
        assertEquals(2, outputs.size());

        // Verify the first SharedOutputConfiguration object
        Object outputConfig1 = outputs.get(0);
        Class<?> sharedOutputConfigClass1 = outputConfig1.getClass();
        assertEquals((int)sharedOutputConfigs[0], ((Number)sharedOutputConfigClass1.getMethod("getSurfaceType").invoke(outputConfig1)).intValue());
        assertEquals(new Size((int) sharedOutputConfigs[1], (int) sharedOutputConfigs[2]), sharedOutputConfigClass1.getMethod("getSize").invoke(outputConfig1));
        assertEquals((int)sharedOutputConfigs[3], ((Number)sharedOutputConfigClass1.getMethod("getFormat").invoke(outputConfig1)).intValue());
        assertEquals((int)sharedOutputConfigs[4], ((Number)sharedOutputConfigClass1.getMethod("getMirrorMode").invoke(outputConfig1)).intValue());
        assertEquals(sharedOutputConfigs[5] != 0, sharedOutputConfigClass1.getMethod("isReadoutTimestampEnabled").invoke(outputConfig1));
        assertEquals((int)sharedOutputConfigs[6], ((Number)sharedOutputConfigClass1.getMethod("getTimestampBase").invoke(outputConfig1)).intValue());
        assertEquals((int)sharedOutputConfigs[7], ((Number)sharedOutputConfigClass1.getMethod("getDataspace").invoke(outputConfig1)).intValue());
        assertEquals(sharedOutputConfigs[8], ((Number)sharedOutputConfigClass1.getMethod("getUsage").invoke(outputConfig1)).longValue());
        assertEquals(sharedOutputConfigs[9], ((Number)sharedOutputConfigClass1.getMethod("getStreamUseCase").invoke(outputConfig1)).longValue());
        assertEquals(Character.toString((char) sharedOutputConfigs[11]), sharedOutputConfigClass1.getMethod("getPhysicalCameraId").invoke(outputConfig1));

        // Verify the second SharedOutputConfiguration object
        Object outputConfig2 = outputs.get(1);
        Class<?> sharedOutputConfigClass2 = outputConfig2.getClass();
        assertEquals((int)sharedOutputConfigs[12], ((Number)sharedOutputConfigClass2.getMethod("getSurfaceType").invoke(outputConfig2)).intValue());
        assertEquals(new Size((int) sharedOutputConfigs[13], (int) sharedOutputConfigs[14]), sharedOutputConfigClass2.getMethod("getSize").invoke(outputConfig2));
        assertEquals((int)sharedOutputConfigs[15], ((Number)sharedOutputConfigClass2.getMethod("getFormat").invoke(outputConfig2)).intValue());
        assertEquals((int)sharedOutputConfigs[16], ((Number)sharedOutputConfigClass2.getMethod("getMirrorMode").invoke(outputConfig2)).intValue());
        assertEquals(sharedOutputConfigs[17] != 0, sharedOutputConfigClass2.getMethod("isReadoutTimestampEnabled").invoke(outputConfig2));
        assertEquals((int)sharedOutputConfigs[18], ((Number)sharedOutputConfigClass2.getMethod("getTimestampBase").invoke(outputConfig2)).intValue());
        assertEquals((int)sharedOutputConfigs[19], ((Number)sharedOutputConfigClass2.getMethod("getDataspace").invoke(outputConfig2)).intValue());
        assertEquals(sharedOutputConfigs[20], ((Number)sharedOutputConfigClass2.getMethod("getUsage").invoke(outputConfig2)).longValue());
        assertEquals(sharedOutputConfigs[21], ((Number)sharedOutputConfigClass2.getMethod("getStreamUseCase").invoke(outputConfig2)).longValue());
        assertNull(sharedOutputConfigClass2.getMethod("getPhysicalCameraId").invoke(outputConfig2));
    }

    @Test
    public void testEmptySharedOutputConfigurations() throws Exception {
        Class<?> sharedSessionConfigClass = Class.forName(SHARED_SESSION_CONFIG_CLASS_NAME);
        int colorSpace = /* ColorSpace.Named.SRGB */ 0;
        long[] sharedOutputConfigs = {};

        // Create SharedSessionConfiguration using reflection
        Constructor<?> constructor = sharedSessionConfigClass.getConstructor(int.class, long[].class);
        Object config = constructor.newInstance(colorSpace, sharedOutputConfigs);

        // Call getColorSpace() using reflection
        Method getColorSpaceMethod = sharedSessionConfigClass.getMethod("getColorSpace");
        Object colorSpaceObj = getColorSpaceMethod.invoke(config);
        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), colorSpaceObj);

        // Call getOutputStreamsInformation() using reflection
        Method getOutputStreamsInformationMethod = sharedSessionConfigClass.getMethod("getOutputStreamsInformation");
        List<?> outputs = (List<?>) getOutputStreamsInformationMethod.invoke(config);
        assertEquals(0, outputs.size());
    }
}
