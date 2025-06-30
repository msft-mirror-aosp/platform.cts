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

import static com.android.graphics.surfaceflinger.flags.Flags.FLAG_READBACK_SCREENSHOT;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.WindowManagerTestBase;
import android.view.Display;
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

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class ScreenCaptureTest extends WindowManagerTestBase {

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture",
            })
    @Test
    public void capture_success() throws Exception {
        SynchronousReceiver receiver = new SynchronousReceiver();
        Rect contentBounds = new Rect();

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(false /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getDefaultDisplay()).build();
                    Executor executor = runnable -> runnable.run();
                    ScreenCapture.capture(params, executor, receiver);
                });

        ScreenCaptureResult result = receiver.waitForResult();
        Bitmap bitmap = makeSoftwareBitmap(result);

        int expectedMatchingPixels = contentBounds.width() * contentBounds.height();
        int actualMatchingPixels =
                new BitmapPixelChecker(Color.RED, contentBounds)
                        .getNumMatchingPixels(bitmap, contentBounds);
        assertEquals(expectedMatchingPixels, actualMatchingPixels);
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture",
            })
    @Test
    public void capture_requiresReadFrameBufferPermission() throws Exception {
        ScreenCaptureParams params = new ScreenCaptureParams.Builder(getDefaultDisplay()).build();
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

        SynchronousReceiver receiver = new SynchronousReceiver();
        Rect contentBounds = new Rect();

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(false /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getDefaultDisplay())
                                    .setCaptureMode(
                                            ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED)
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    ScreenCapture.capture(params, executor, receiver);
                });

        ScreenCaptureResult result = receiver.waitForResult();
        Bitmap bitmap = makeSoftwareBitmap(result);

        int expectedMatchingPixels = contentBounds.width() * contentBounds.height();
        int actualMatchingPixels =
                new BitmapPixelChecker(Color.RED, contentBounds)
                        .getNumMatchingPixels(bitmap, contentBounds);
        assertEquals(expectedMatchingPixels, actualMatchingPixels);
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
        SynchronousReceiver receiver = new SynchronousReceiver();

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(true /*secure*/, null /*contentBounds*/);

                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getDefaultDisplay())
                                    .setCaptureMode(
                                            ScreenCaptureParams.CAPTURE_MODE_REQUIRE_OPTIMIZED)
                                    .build();
                    Executor executor = runnable -> runnable.run();
                    ScreenCapture.capture(params, executor, receiver);
                });

        assertThat(receiver.waitForError(), instanceOf(IllegalStateException.class));
    }

    @RequiresFlagsEnabled(FLAG_READBACK_SCREENSHOT)
    @ApiTest(
            apis = {
                "android.window.ScreenCapture.ScreenCaptureParams.Builder#build",
                "android.window.ScreenCapture#capture"
            })
    @Test
    public void capture_CaptureModeDefaultRedactsSecure() throws Exception {
        SynchronousReceiver receiver = new SynchronousReceiver();
        Rect contentBounds = new Rect();

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(true /*secure*/, contentBounds);
                    ScreenCaptureParams params =
                            new ScreenCaptureParams.Builder(getDefaultDisplay()).build();
                    Executor executor = runnable -> runnable.run();
                    ScreenCapture.capture(params, executor, receiver);
                });

        ScreenCaptureResult result = receiver.waitForResult();
        Bitmap bitmap = makeSoftwareBitmap(result);

        int expectedMatchingPixels = contentBounds.width() * contentBounds.height();
        int actualMatchingPixels =
                new BitmapPixelChecker(Color.BLACK, contentBounds)
                        .getNumMatchingPixels(bitmap, contentBounds);
        assertEquals(expectedMatchingPixels, actualMatchingPixels);
    }

    Display getDefaultDisplay() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    void launchActivity(boolean secure, @Nullable Rect outContentBounds)
            throws InterruptedException {
        Class<?> activityClass = secure ? SecureTestActivity.class : TestActivity.class;
        Intent intent =
                new Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestActivity activity =
                (TestActivity)
                        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        activity.waitAndAssertWindowFocusState(true /*hasFocus*/);
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
                        mContentBounds.set(
                                windowBounds.left + systemBarInsets.left,
                                windowBounds.top + systemBarInsets.top,
                                windowBounds.right - systemBarInsets.right,
                                windowBounds.bottom - systemBarInsets.bottom);
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
            assertTrue(mLatch.await(5, TimeUnit.SECONDS));
            assertNull(mException);
            assertNotNull(mResult);
            return mResult;
        }

        public Exception waitForError() throws InterruptedException {
            assertTrue(mLatch.await(5, TimeUnit.SECONDS));
            assertNull(mResult);
            assertNotNull(mException);
            return mException;
        }
    }
}
