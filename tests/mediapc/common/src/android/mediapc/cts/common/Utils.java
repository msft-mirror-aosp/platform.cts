/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts.common;

import static android.util.DisplayMetrics.DENSITY_HIGH;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Test utilities. */
public final class Utils {
    private static final String TAG = "PerformanceClassTestUtils";
    private static final String MEDIA_PERF_CLASS_KEY = "media-performance-class";

    private static final int sPc = getMpc();

    private static int getMpc() {
        Bundle bundle = InstrumentationRegistry.getArguments();
        String value = bundle.getString(MEDIA_PERF_CLASS_KEY);
        int valueInt = 0;
        if (value != null) {
            Log.d(TAG, "Running the tests with performance class set to " + value);
            valueInt = Integer.parseInt(value);
        } else {
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                valueInt = Build.VERSION.MEDIA_PERFORMANCE_CLASS;
            } else {
                valueInt = SystemProperties.getInt("ro.odm.build.media_performance_class", 0);
            }
        }
        return valueInt;
    }

    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public static int getMaxDisplayWidth() {
        return Arrays.stream(getContext().getSystemService(DisplayManager.class).getDisplays())
                .map(Display::getSupportedModes)
                .flatMap(Stream::of)
                .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                .orElseThrow(() -> new RuntimeException("Failed to determine max height"))
                .getPhysicalWidth();
    }

    public static int getMaxDisplayHeight() {
        return Arrays.stream(getContext().getSystemService(DisplayManager.class).getDisplays())
                .map(Display::getSupportedModes)
                .flatMap(Stream::of)
                .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                .orElseThrow(() -> new RuntimeException("Failed to determine max height"))
                .getPhysicalHeight();
    }

    public static int getMaxDisplayDim() {
        return Math.max(getMaxDisplayWidth(), getMaxDisplayHeight());
    }

    public static int getMinDisplayDim() {
        return Math.min(getMaxDisplayWidth(), getMaxDisplayHeight());
    }

    /** Get the DPI of the default display or zero if there is none */
    public static int getDisplayDpi() {
        Context ctxt = getContext();
        Display defaultDisplay =
                ctxt.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        int maxWidthPixels = getMaxDisplayWidth();
        int maxHeightPixels = getMaxDisplayHeight();
        int widthPixels = defaultDisplay.getMode().getPhysicalWidth();
        int heightPixels = defaultDisplay.getMode().getPhysicalHeight();
        DisplayMetrics metrics = ctxt.getResources().getDisplayMetrics();
        final double widthInch = (double) widthPixels / (double) metrics.xdpi;
        final double heightInch = (double) heightPixels / (double) metrics.ydpi;
        final double diagonalInch = Math.sqrt(widthInch * widthInch + heightInch * heightInch);
        final double maxDiagonalPixels =
                Math.sqrt(maxWidthPixels * maxWidthPixels + maxHeightPixels * maxHeightPixels);
        // Use max of computed dpi and advertised dpi as these values differ in some devices.
        return Math.max(
                (int) (maxDiagonalPixels / diagonalInch),
                ctxt.getResources().getConfiguration().densityDpi);
    }

    /** Get {@link ActivityManager.MemoryInfo#totalMem} in Mb. */
    public static long getTotalMemoryMb() {
        ActivityManager activityManager = getContext().getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem / 1024 / 1024;
    }

    /**
     * First defined media performance class.
     */
    private static final int FIRST_PERFORMANCE_CLASS = Build.VERSION_CODES.R;

    public static boolean isRPerfClass() {
        return sPc == Build.VERSION_CODES.R;
    }

    public static boolean isSPerfClass() {
        return sPc == Build.VERSION_CODES.S;
    }

    public static boolean isTPerfClass() {
        return sPc == Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean isBeforeTPerfClass() {
        return sPc < Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean isUPerfClass() {
        return sPc == Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    public static boolean isVPerfClass() {
        return sPc == Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    /** Latest defined media performance class. */
    private static final int LAST_PERFORMANCE_CLASS = Build.VERSION_CODES.VANILLA_ICE_CREAM;

    /**
     * Checks if the AVC codec meets the required performance preconditions.
     *
     * @param isEncoder True for encoder, false for decoder.
     * @param width frame width.
     * @param height frame height.
     * @param fps frame rate
     * @param concurrentInstancesCount required concurrent instances
     * @return True if the codec meets the preconditions, false otherwise.
     */
    private static boolean meetsAvcCodecPreconditions(boolean isEncoder, int width, int height,
             double fps, int concurrentInstancesCount) {
        String avcMediaType = MediaFormat.MIMETYPE_VIDEO_AVC;
        // It should be noted that getMaxSupportedInstances() does not make use of the configuration
        // under test. It is a predefined constant defined in xml. To verify if concurrent instances
        // of a given configuration are supported, scale the fps such that the performance point is
        // equivalent to n instances of wxh@fps in terms of throughput.
        double scaledFps = fps * concurrentInstancesCount;
        PerformancePoint ppReq = new PerformancePoint(width, height, (int) scaledFps);
        MediaCodec codec;
        try {
            codec = isEncoder ? MediaCodec.createEncoderByType(avcMediaType) :
                    MediaCodec.createDecoderByType(avcMediaType);
        } catch (IOException e) {
            Log.d(TAG, "Unable to create codec " + e);
            return false;
        }
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(avcMediaType);
        List<PerformancePoint> pps =
                caps.getVideoCapabilities().getSupportedPerformancePoints();
        if (pps == null || pps.size() == 0) {
            Log.w(TAG, info.getName() + " doesn't advertise performance points. Assuming codec "
                    + "meets the requirements");
            codec.release();
            return true;
        }
        boolean supportsRequiredRate = false;
        for (PerformancePoint pp : pps) {
            if (pp.covers(ppReq)) {
                supportsRequiredRate = true;
            }
        }

        boolean supportsRequiredSize = caps.getVideoCapabilities().isSizeSupported(width, height);
        boolean supportsRequiredInstances =
                caps.getMaxSupportedInstances() >= concurrentInstancesCount;
        codec.release();
        Log.d(TAG, info.getName() + " supports required FPS : " + supportsRequiredRate
                + ", supports required size : " + supportsRequiredSize
                + ", supports required instances : " + supportsRequiredInstances);
        return supportsRequiredRate && supportsRequiredSize && supportsRequiredInstances;
    }

    public static boolean meetsAvcCodecPreconditions() {
        // AVC Codec performance class minimum pre-requisites:
        //
        // CDD - [5.1/H-1-2] & [5.1/H-1-4]
        // - 720@30fps encoding support (6 instances concurrent)
        // - 720@30fps decoding support (6 instances concurrent)
        //
        // CDD - [5.1/H-1-6]
        // - 720@30fps encoding/decoding support (6 instances concurrent, any combination)
        //
        // CDD - [5.1/H-1-7]
        // Load:
        // - 1080p@30fps encoding support for video recording session
        // - 720p@30fps encoding support for video transcoding session
        // - 1080p@30fps decoding support for video transcoding session
        // Test:
        // - 1080p@30fps encoding support for testing initialization latency
        // - above instances have to be supported concurrently
        // NOTES: 720p@30fps can be viewed as 0.45 * 1080p@30fps in terms of throughput
        // [5.1/H-1-7] (viewed alternatively):
        // - 1080p@73.33fps encoding support
        // - 1080p@30fps decoding support
        // CDD - [5.1/H-1-2] & [5.1/H-1-4] (viewed alternatively):
        // - 1080p@80fps encoding/decoding support
        // - In terms of throughput, [5.1/H-1-2 or 4 or 6] covers [5.1/H-1-7] as well
        return meetsAvcCodecPreconditions(/* isEncoder */ true, 1920, 1080, 30, 2)
                && meetsAvcCodecPreconditions(/* isEncoder */ false, 1920, 1080, 30, 2)
                && meetsAvcCodecPreconditions(/* isEncoder */ true, 1280, 720, 30, 6)
                && meetsAvcCodecPreconditions(/* isEncoder */ false, 1280, 720, 30, 6);
    }

    public static int getPerfClass() {
        return sPc;
    }

    public static boolean isPerfClass() {
        return sPc >= FIRST_PERFORMANCE_CLASS &&
               sPc <= LAST_PERFORMANCE_CLASS;
    }

    /**
     * Does the device meet the preconditions for Media Performance Class.
     *
     * <p>Failing to meet these thresholds means we know that the device can't meet any performance
     * class requirement. If the device doesn't meet these, we save time for everyone by skipping
     * the tests that we know the device will fail.
     *
     * <p>The numbers here are slightly reduced from the strict thresholds so that we can gather
     * some information about "almost performance class" devices. This won't impact CTS results, but
     * will increase CTS runtime for those devices.
     *
     * @deprecated use android.mediapc.cts.common.Preconditions#BASELINE instead.
     */
    @Deprecated
    private static boolean meetsPerformanceClassPreconditions() {
        if (isPerfClass()) {
            return true;
        }

        // If device doesn't advertise performance class, check if this can be ruled out as a
        // candidate for performance class tests.
        return MediaUtils.isHandheld()
                // Setting the minimum memory to 2.5G so we get statistics on "Mid Tier Devices"
                // As of 2025 Q1 this is about 80% of daily active devices.
                && getTotalMemoryMb() >= (long) (2.5 * 1024L)
                // MPC requires 400 DPI. lowering to HIGH (320) to report statistics on
                // "mid tier" devices
                // As of 2025 Q1 this is about 85% of daily active devices.
                && getDisplayDpi() >= DENSITY_HIGH
                // MPC requires 1920. lowering to 1280 to report statistics on "mid tier" devices
                // As of 2025 Q1 this is about 99% of daily active devices.
                && getMaxDisplayDim() >= 1280
                // MPC requires 1080. lowering to 720 to report statistics on "mid tier" devices
                // As of 2025 Q1 this is about 99% of daily active devices.
                && getMinDisplayDim() >= 720;
    }

    /**
     * Throws an {@link org.junit.AssumptionViolatedException} if the device does not {@link
     * #meetsPerformanceClassPreconditions()}
     *
     * @deprecated use android.mediapc.cts.common.Preconditions#BASELINE instead.
     */
    @Deprecated
    public static void assumeDeviceMeetsPerformanceClassPreconditions() {
        assumeTrue(
                "Test skipped because the device does not meet the hardware requirements for "
                        + "performance class.",
                meetsPerformanceClassPreconditions());
    }

    private Utils() {}
}
