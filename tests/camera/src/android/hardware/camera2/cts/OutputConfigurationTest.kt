/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.hardware.camera2.cts

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.HardwareBuffer
import android.hardware.camera2.params.OutputConfiguration
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaRecorder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Size
import android.view.SurfaceHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.internal.camera.flags.Flags
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests simple functionality of [OutputConfiguration] APIs. These are effectively unit tests for
 * the OutputConfiguration class, and don't require any interaction with the camera device.
 *
 * More thorough behavior testing is done in [CameraDeviceSetupTest] and [CameraDeviceTest].
 */
@RunWith(AndroidJUnit4::class)
class OutputConfigurationTest {

    /** Rule to ensure that @RequiresFlagsEnabled or @RequiresFlagsDisabled is respected. */
    @get:Rule val mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @ApiTest(
        apis =
            [
                "android.hardware.camera2.params.OutputConfiguration#setPhysicalCameraId",
                "android.hardware.camera2.params.OutputConfiguration#getPhysicalCameraId",
            ]
    )
    @RequiresFlagsEnabled(Flags.FLAG_OUTPUT_CONFIGURATION_GET_PHYSICAL_CAMERA_ID)
    fun testPhysicalCameraIdGetter() {
        val physicalCameraId = "23"
        val size = Size(1280, 720)
        val config = OutputConfiguration(size, SurfaceTexture::class.java)

        // Default OutputConfiguration should return null for physical camera ID.
        Assert.assertNull("OutputConfiguration must return null for physical camera ID when not " +
                "set", config.physicalCameraId)

        // Set the physical camera ID and verify that the user set value is returned.
        config.setPhysicalCameraId(physicalCameraId)
        Assert.assertEquals(
            physicalCameraId,
            config.physicalCameraId,
        )

        // Set the physical camera ID to null and verify that physicalCameraId is unset.
        config.setPhysicalCameraId(null)
        Assert.assertNull("OutputConfiguration must return null for physical camera ID when set to " +
                "null", config.physicalCameraId)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.hardware.camera2.params.OutputConfiguration#getConfiguredFormat",
                "android.hardware.camera2.params.OutputConfiguration#getConfiguredSize",
                "android.hardware.camera2.params.OutputConfiguration#getUsage",
            ]
    )
    @RequiresFlagsEnabled(Flags.FLAG_OUTPUT_CONFIGURATION_GETTER)
    fun testFormatSizeUsageGetter() {
        val size = Size(2, 2)
        val maxImages = 1

        // OutputConfiguration(Size surfaceSize, Class<T> klass)
        val classes =
            arrayOf(
                SurfaceHolder::class.java,
                SurfaceTexture::class.java,
                MediaRecorder::class.java,
                MediaCodec::class.java,
            )
        for (outputClass in classes) {
            val config = OutputConfiguration(size, outputClass)
            verifyOutputConfiguration(
                ImageFormat.PRIVATE,
                size,
                getUsageFromClass(outputClass),
                config,
            )
        }

        val formats =
            intArrayOf(
                PixelFormat.RGBA_8888,
                PixelFormat.RGBX_8888,
                PixelFormat.RGB_888,
                PixelFormat.RGB_565,
                ImageFormat.YV12,
                ImageFormat.Y8,
                ImageFormat.YCBCR_P010,
                ImageFormat.YCBCR_P210,
                ImageFormat.NV16,
                ImageFormat.NV21,
                ImageFormat.YUY2,
                ImageFormat.JPEG,
                ImageFormat.DEPTH_JPEG,
                ImageFormat.YUV_420_888,
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW_PRIVATE,
                ImageFormat.RAW10,
                ImageFormat.RAW12,
                ImageFormat.DEPTH16,
                ImageFormat.DEPTH_POINT_CLOUD,
                ImageFormat.PRIVATE,
                ImageFormat.HEIC,
                ImageFormat.HEIC_ULTRAHDR,
                ImageFormat.JPEG_R,
            )

        for (format in formats) {
            val config = OutputConfiguration(format, size)
            val expectedUsage =
                if (format == ImageFormat.PRIVATE) 0L else HardwareBuffer.USAGE_CPU_READ_OFTEN
            verifyOutputConfiguration(format, size, expectedUsage, config)
        }

        // OutputConfiguration(Surface surface)
        for (format in formats) {
            // ImageReader doesn't support NV21 format
            if (format == ImageFormat.NV21) {
                continue
            }
            val isRgb = format >= PixelFormat.RGBA_8888 && format <= PixelFormat.RGB_565
            val reader =
                if (isRgb) {
                    ImageReader.newInstance(
                        size.width,
                        size.height,
                        format,
                        maxImages,
                        HardwareBuffer.USAGE_COMPOSER_OVERLAY,
                    )
                } else {
                    ImageReader.newInstance(size.width, size.height, format, maxImages)
                }
            reader.use {
                val config = OutputConfiguration(it.surface)
                // For RGB formats, if a hardware usage flag is specified, the image format is
                // overridden to PRIVATE.
                val returnedFormat = if (isRgb) ImageFormat.PRIVATE else format
                val expectedUsage =
                    if (isRgb) {
                        HardwareBuffer.USAGE_COMPOSER_OVERLAY
                    } else if (format == ImageFormat.PRIVATE) {
                        0L
                    } else {
                        HardwareBuffer.USAGE_CPU_READ_OFTEN
                    }
                verifyOutputConfiguration(returnedFormat, size, expectedUsage, config)
            }
        }
    }

    private fun <T> getUsageFromClass(klass: Class<T>): Long {
        return when (klass) {
            SurfaceHolder::class.java ->
                HardwareBuffer.USAGE_COMPOSER_OVERLAY or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            SurfaceTexture::class.java -> HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            MediaRecorder::class.java,
            MediaCodec::class.java -> HardwareBuffer.USAGE_VIDEO_ENCODE
            else -> {
                Assert.fail("Unknown class $klass")
                0
            }
        }
    }

    private fun verifyOutputConfiguration(
        format: Int,
        size: Size,
        expectedUsage: Long,
        config: OutputConfiguration,
    ) {
        val configuredSize = config.configuredSize
        val configuredFormat = config.configuredFormat
        Assert.assertEquals(
            "OutputConfiguration surface size " +
                "$size" +
                ", but getConfiguredSize returns " +
                "$configuredSize",
            size,
            configuredSize,
        )
        Assert.assertEquals(
            "OutputConfiguration surface format " +
                "$format" +
                ", but getConfiguredFormat returns " +
                "$configuredFormat",
            format,
            configuredFormat,
        )
        if (Flags.outputConfigurationGetUsage()) {
            val usage = config.usage
            Assert.assertEquals(
                "Format $format, OutputConfiguration usage ${expectedUsage.toHexString()}, " +
                    "but getUsage returns ${usage.toHexString()}",
                expectedUsage,
                usage,
            )
        }
    }
}
