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

package android.server.wm.other;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;

import static com.android.graphics.surfaceflinger.flags.Flags.FLAG_READBACK_SCREENSHOT;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.DumpOnFailure;
import android.server.wm.WindowManagerTestBase;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.window.ScreenCapture;
import android.window.ScreenCapture.ScreenCaptureParams;
import android.window.ScreenCapture.ScreenCaptureResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class ScreenCaptureTest extends WindowManagerTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final DumpOnFailure mDumpOnFailure = new DumpOnFailure();

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture",
            })
    @Test
    public void capture_success() throws Exception {
        Rect contentBounds = new Rect();
        Bitmap[] bitmap = {null};

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(false /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId()).build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    ScreenCaptureResult result = receiver.waitForResult();
                    bitmap[0] = makeSoftwareBitmap(result);
                });

        verifyBitmap(Color.RED, bitmap[0], contentBounds);
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture",
            })
    @Test
    public void capture_requiresReadFrameBufferPermission() throws Exception {
        ScreenCaptureParams params = new ScreenCaptureParams.Builder(getMainDisplayId()).build();
        Executor executor = runnable -> runnable.run();
        SynchronousReceiver receiver = new SynchronousReceiver();

        ScreenCapture.capture(params, executor, receiver);

        assertThat(receiver.waitForError(), instanceOf(SecurityException.class));
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture#isScreenCaptureOptimizationEnabled",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#setCaptureMode",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_CaptureModeOptimizedSucceeds() throws Exception {
        assumeTrue(ScreenCapture.isScreenCaptureOptimizationEnabled());

        Rect contentBounds = new Rect();
        Bitmap[] bitmap = {null};

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(false /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId())
                                    .setCaptureMode(
                                            ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED)
                                    .setPixelFormat(0) // Any pixel format.
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    ScreenCaptureResult result = receiver.waitForResult();
                    bitmap[0] = makeSoftwareBitmap(result);
                });

        verifyBitmap(Color.RED, bitmap[0], contentBounds, /* tolerance */ 0.01f);
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#setCaptureMode",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_CaptureModeOptimizedFailsOnSecureWindow() throws Exception {
        Exception[] exception = {null};

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(true /*secure*/, null /*contentBounds*/);

                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId())
                                    .setCaptureMode(
                                            ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED)
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    exception[0] = receiver.waitForError();
                });

        assertThat(exception[0], instanceOf(IllegalStateException.class));
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#setCaptureMode",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#setPixelFormat",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_CaptureModeOptimizedFailsOnUnsupportedPixelFormat() throws Exception {
        assumeTrue(ScreenCapture.isScreenCaptureOptimizationEnabled());
        Exception[] exception = {null};
        // Pixel format that is not supported by CAPTURE_MODE_REQUIRE_OPTIMIZED.
        int unsupportedPixelFormat = -1;
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(true /*secure*/, null /*contentBounds*/);

                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId())
                                    .setCaptureMode(
                                            ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED)
                                    .setPixelFormat(unsupportedPixelFormat)
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    exception[0] = receiver.waitForError();
                });

        assertThat(exception[0], instanceOf(IllegalStateException.class));
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_CaptureModeDefaultRedactsSecure() throws Exception {
        Rect contentBounds = new Rect();
        Bitmap[] bitmap = {null};

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(true /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId()).build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    ScreenCaptureResult result = receiver.waitForResult();
                    bitmap[0] = makeSoftwareBitmap(result);
                });

        verifyBitmap(Color.BLACK, bitmap[0], contentBounds);
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#setPixelFormat",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_setPixelFormat() throws Exception {
        ScreenCaptureResult[] result = {null};
        int pixelFormat = HardwareBuffer.RGB_565;

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getMainDisplayId())
                                    .setPixelFormat(pixelFormat)
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    SynchronousReceiver receiver = new SynchronousReceiver();
                    ScreenCapture.capture(params, executor, receiver);
                    result[0] = receiver.waitForResult();
                });

        assertEquals(pixelFormat, result[0].getHardwareBuffer().getFormat());
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#getDisplayId",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#getCaptureMode",
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#getPixelFormat",
            })
    @Test
    public void screenCaptureParamGetters() {
        int displayId = getMainDisplayId();
        int captureMode = ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED;
        int pixelFormat = HardwareBuffer.RGB_565;

        ScreenCaptureParams params =
                new ScreenCaptureParams.Builder(displayId)
                        .setCaptureMode(captureMode)
                        .setPixelFormat(pixelFormat)
                        .build();

        assertEquals(displayId, params.getDisplayId());
        assertEquals(captureMode, params.getCaptureMode());
        assertEquals(pixelFormat, params.getPixelFormat());
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureResult#ScreenCaptureResult",
            })
    @Test
    public void screenCaptureResultCtor() {
        ColorSpace colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        HardwareBuffer hardwareBuffer =
                HardwareBuffer.create(
                        1, 1, HardwareBuffer.RGB_565, 1, HardwareBuffer.USAGE_CPU_READ_RARELY);

        ScreenCaptureResult result = new ScreenCaptureResult(colorSpace, hardwareBuffer);

        assertEquals(colorSpace, result.getColorSpace());
        assertEquals(hardwareBuffer, result.getHardwareBuffer());
    }

    void launchActivity(boolean secure, @Nullable Rect outContentBounds)
            throws InterruptedException {
        Class<?> activityClass = secure ? SecureTestActivity.class : TestActivity.class;
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(getMainDisplayId());
        Intent intent =
                new Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestActivity activity =
                (TestActivity)
                        InstrumentationRegistry.getInstrumentation()
                                .startActivitySync(intent, options.toBundle());
        activity.waitAndAssertWindowFocusState(true /*hasFocus*/);
        assertTrue(
                "Failed to wait for activity to be on top",
                waitForWindowOnTop(activity.getWindow()));

        if (outContentBounds != null) {
            outContentBounds.set(activity.waitForContentBounds());
        }
    }

    Bitmap makeSoftwareBitmap(@NonNull ScreenCaptureResult result) {
        try (HardwareBuffer hardwareBuffer = result.getHardwareBuffer()) {
            Bitmap hardwareBitmap =
                    Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
            return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
    }

    /**
     * @param tolerance - a float number range [0, 1]. The allowing ratio of total pixels that are
     *     mismatched. 0 means no tolerance.
     */
    private void verifyBitmap(int expectedColor, Bitmap bitmap, Rect contentBounds, float tolerance)
            throws AssertionError {
        int expectedMatchingPixels = contentBounds.width() * contentBounds.height();
        int actualMatchingPixels =
                new BitmapPixelChecker(expectedColor, contentBounds)
                        .getNumMatchingPixels(bitmap, contentBounds);
        mDumpOnFailure.dumpOnFailure("ScreenCaptureResult", bitmap);
        assertThat(Math.abs(actualMatchingPixels - expectedMatchingPixels))
                .isAtMost((int) (expectedMatchingPixels * tolerance));
    }

    private void verifyBitmap(int expectedColor, Bitmap bitmap, Rect contentBounds)
            throws AssertionError {
        verifyBitmap(expectedColor, bitmap, contentBounds, 0.0f);
    }

    public static class TestActivity extends FocusableActivity {

        CountDownLatch mContentBoundsLatch = new CountDownLatch(1);
        Rect mContentBounds = new Rect();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            requestWindowFeature(Window.FEATURE_NO_TITLE);

            View contentView = new View(this);
            contentView.setBackgroundColor(Color.RED);
            setContentView(contentView);

            contentView.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
                        Rect windowBounds = windowMetrics.getBounds();
                        Insets systemBarInsets = insets.getInsets(WindowInsets.Type.systemBars());
                        Insets tappableInsets =
                                insets.getInsets(WindowInsets.Type.tappableElement());
                        boolean isButtonNavigation = tappableInsets.bottom > 0;
                        boolean isDesktopMode = insets.isVisible(WindowInsets.Type.captionBar());
                        if (isDesktopMode || isButtonNavigation) {
                            // TODO(b/454352473): Use {@link insets#getRoundedCorner()} when it's
                            //  fully supported.
                            int radius = 128;
                            Insets captionBarInsets =
                                    insets.getInsets(WindowInsets.Type.captionBar());
                            mContentBounds.set(
                                    windowBounds.left + systemBarInsets.left,
                                    windowBounds.top + captionBarInsets.top + radius,
                                    windowBounds.right - systemBarInsets.right,
                                    windowBounds.bottom - systemBarInsets.bottom - radius);
                        } else {
                            mContentBounds.set(
                                    windowBounds.left + systemBarInsets.left,
                                    windowBounds.top + systemBarInsets.top,
                                    windowBounds.right - systemBarInsets.right,
                                    windowBounds.bottom - systemBarInsets.bottom);
                        }
                        mContentBoundsLatch.countDown();
                        return insets;
                    });
        }

        Rect waitForContentBounds() throws InterruptedException {
            assertTrue(mContentBoundsLatch.await(5, TimeUnit.SECONDS));
            return mContentBounds;
        }
    }

    public static class SecureTestActivity extends TestActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getWindow()
                    .setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private static class SynchronousReceiver
            implements OutcomeReceiver<ScreenCaptureResult, Exception> {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private ScreenCaptureResult mResult;
        private Exception mException;

        @Override
        public void onResult(ScreenCaptureResult result) {
            mResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(Exception exception) {
            mException = exception;
            mLatch.countDown();
        }

        public ScreenCaptureResult waitForResult() throws InterruptedException {
            assertTrue(mLatch.await(HW_TIMEOUT_MULTIPLIER * 5L, TimeUnit.SECONDS));
            assertNull(mException);
            assertNotNull(mResult);
            return mResult;
        }

        public Exception waitForError() throws InterruptedException {
            assertTrue(mLatch.await(HW_TIMEOUT_MULTIPLIER * 5L, TimeUnit.SECONDS));
            assertNull(mResult);
            assertNotNull(mException);
            return mException;
        }
    }
}
