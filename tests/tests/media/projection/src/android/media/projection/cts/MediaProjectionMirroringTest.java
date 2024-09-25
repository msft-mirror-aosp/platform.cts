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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.media.projection.cts;

import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.view.Surface.ROTATION_270;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.server.wm.MediaProjectionHelper;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerStateHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowMetrics;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Test {@link MediaProjection} successfully mirrors the display contents.
 *
 * <p>Validate that mirrored views are the expected size, for both full display and single app
 * capture (if offered). Instead of examining the pixels match exactly (which is historically a
 * flaky way of validating mirroring), examine the structure of the mirrored hierarchy, to ensure
 * that mirroring is initiated correctly, and any transformations are applied as expected.
 *
 * <p>Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionMirroringTest
 */
@NonMainlineTest
public class MediaProjectionMirroringTest {
    private static final String TAG = "MediaProjectionMirroringTest-FOO";
    private static final int SCREENSHOT_TIMEOUT_MS = 1000;
    // Enable debug mode to save screenshots from MediaProjection session.
    private static final boolean DEBUG_MODE = false;
    private static final String VIRTUAL_DISPLAY = "MirroringTestVD";
    private Context mContext;
    // Manage a MediaProjection capture session.
    private final MediaProjectionHelper mMediaProjectionHelper = new MediaProjectionHelper();

    private MediaProjection mMediaProjection;
    private MediaProjection.Callback mMediaProjectionCallback =
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                }

                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                }

                @Override
                public void onCapturedContentVisibilityChanged(boolean isVisible) {
                    super.onCapturedContentVisibilityChanged(isVisible);
                }
            };
    private ImageReader mImageReader;
    private CountDownLatch mScreenshotCountDownLatch;
    private VirtualDisplay mVirtualDisplay;
    private final ActivityOptions.LaunchCookie mLaunchCookie = new ActivityOptions.LaunchCookie();
    public ActivityScenario<TestRotationActivity> mTestRotationActivityActivityScenario;
    private Activity mActivity;
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    /**
     * Whether to wait for the rotation to be stable state after testing. It can be set if the
     * display rotation may be changed by test.
     */
    private boolean mWaitForRotationOnTearDown;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(mContext.getUserId()));
        });
        mMediaProjection = null;
        if (DEBUG_MODE) {
            mScreenshotCountDownLatch = new CountDownLatch(1);
        }
    }

    @After
    public void tearDown() {
        if (mMediaProjection != null) {
            if (mMediaProjectionCallback != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjectionCallback = null;
            }
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        if (mImageReader != null) {
            mImageReader = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.getSurface().release();
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mWaitForRotationOnTearDown) {
            mWmState.waitForDisplayUnfrozen();
        }
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testDisplayCapture() {
        ActivityScenario<Activity> activityScenario =
                ActivityScenario.launch(new Intent(mContext, Activity.class));
        activityScenario.onActivity(activity -> mActivity = activity);

        final WindowMetrics maxWindowMetrics =
                mActivity.getWindowManager().getMaximumWindowMetrics();
        final Rect activityRect = new Rect();

        // Select full screen capture.
        mMediaProjectionHelper.authorizeMediaProjection();

        // Start capture of the entire display.
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();
        mVirtualDisplay = createVirtualDisplay(maxWindowMetrics.getBounds(), "testDisplayCapture");
        waitForLatestScreenshot();

        // Get the bounds of the activity on screen - use getGlobalVisibleRect to account for
        // possible insets caused by DisplayCutout
        mActivity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

        validateMirroredHierarchy(mActivity,
                mVirtualDisplay.getDisplay().getDisplayId(),
                new Point(activityRect.width(), activityRect.height()));
        activityScenario.close();
    }

    // Validate that the mirrored hierarchy is the expected size after rotating the default display.
    @Test
    public void testDisplayCapture_rotation() {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        mTestRotationActivityActivityScenario =
                ActivityScenario.launch(new Intent(mContext, TestRotationActivity.class));
        mTestRotationActivityActivityScenario.onActivity(activity -> mActivity = activity);

        final RotationSession rotationSession = createManagedRotationSession();
        final WindowMetrics maxWindowMetrics =
                mActivity.getWindowManager().getMaximumWindowMetrics();
        final Rect activityRect = new Rect();
        final int initialRotation = mActivity.getDisplay().getRotation();

        // Select full screen capture.
        mMediaProjectionHelper.authorizeMediaProjection();

        // Start capture of the entire display.
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();
        mVirtualDisplay = createVirtualDisplay(maxWindowMetrics.getBounds(),
                "testDisplayCapture_rotation");

        rotateDeviceAndWaitForActivity(rotationSession, initialRotation);

        // Get the bounds of the activity on screen - use getGlobalVisibleRect to account for
        // possible insets caused by DisplayCutout
        mActivity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

        final Point mirroredSize = calculateScaledMirroredActivitySize(
                mActivity.getWindowManager().getCurrentWindowMetrics(), mVirtualDisplay,
                new Point(activityRect.width(), activityRect.height()));
        validateMirroredHierarchy(mActivity, mVirtualDisplay.getDisplay().getDisplayId(),
                mirroredSize);

        rotationSession.close();
        mTestRotationActivityActivityScenario.close();
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testSingleAppCapture() {
        final ActivityScenario<Activity> activityScenario = ActivityScenario.launch(
                new Intent(mContext, Activity.class),
                createActivityScenarioWithLaunchCookie(mLaunchCookie)
        );
        activityScenario.onActivity(activity -> mActivity = activity);
        final WindowMetrics maxWindowMetrics =
                mActivity.getWindowManager().getMaximumWindowMetrics();
        final Rect activityRect = new Rect();

        // Select single app capture if supported.
        mMediaProjectionHelper.authorizeMediaProjection(mLaunchCookie);

        // Start capture of the single app.
        mMediaProjection = mMediaProjectionHelper.startMediaProjection();
        mVirtualDisplay = createVirtualDisplay(maxWindowMetrics.getBounds(),
                "testSingleAppCapture");
        waitForLatestScreenshot();

        // Get the bounds of the activity on screen - use getGlobalVisibleRect to account for
        // possible insets caused by DisplayCutout
        mActivity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

        validateMirroredHierarchy(mActivity,
                mVirtualDisplay.getDisplay().getDisplayId(),
                new Point(activityRect.width(), activityRect.height()));
        activityScenario.close();
    }

    // TODO (b/284968776): test single app capture in split screen

    /**
     * Returns ActivityOptions with the given launch cookie set.
     */
    private static Bundle createActivityScenarioWithLaunchCookie(
            @NonNull ActivityOptions.LaunchCookie launchCookie) {
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        activityOptions.setLaunchCookie(launchCookie);
        return activityOptions.toBundle();
    }

    private VirtualDisplay createVirtualDisplay(Rect displayBounds, String methodName) {
        mImageReader = ImageReader.newInstance(displayBounds.width(), displayBounds.height(),
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        if (DEBUG_MODE) {
            ScreenshotListener screenshotListener = new ScreenshotListener(methodName,
                    mScreenshotCountDownLatch);
            mImageReader.setOnImageAvailableListener(screenshotListener,
                    new Handler(Looper.getMainLooper()));
        }
        mMediaProjection.registerCallback(mMediaProjectionCallback,
                new Handler(Looper.getMainLooper()));
        return mMediaProjection.createVirtualDisplay(VIRTUAL_DISPLAY + "_" + methodName,
                displayBounds.width(), displayBounds.height(),
                DisplayMetrics.DENSITY_HIGH, /* flags= */ 0,
                mImageReader.getSurface(), /* callback= */
                null, new Handler(Looper.getMainLooper()));
    }

    /**
     * Rotates the device 90 degrees & waits for the display & activity configuration to stabilize.
     */
    private void rotateDeviceAndWaitForActivity(
            @NonNull RotationSession rotationSession, @Surface.Rotation int initialRotation) {
        // Rotate the device by 90 degrees
        rotationSession.set((initialRotation + 1) % (ROTATION_270 + 1),
                /* waitForDeviceRotation=*/ true);
        waitForLatestScreenshot();
        try {
            waitForStableWindowGeometry(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Unable to wait for window to stabilize after rotation: " + e.getMessage());
        }
        // Re-fetch the activity since reference may have been modified during rotation.
        mTestRotationActivityActivityScenario.onActivity(activity -> mActivity = activity);
    }

    /**
     * Calculate the size of the activity, scaled to fit on the VirtualDisplay.
     *
     * @param currentWindowMetrics The size of the source activity, before it is mirrored
     * @param virtualDisplay       The VirtualDisplay the mirrored content is sent to and scaled to
     *                             fit
     * @return The expected size of the mirrored activity on the VirtualDisplay
     */
    private static Point calculateScaledMirroredActivitySize(
            @NonNull WindowMetrics currentWindowMetrics,
            @NonNull VirtualDisplay virtualDisplay, @Nullable Point visibleBounds) {
        // Calculate the aspect ratio of the original activity.
        final Point currentBounds = new Point(currentWindowMetrics.getBounds().width(),
                currentWindowMetrics.getBounds().height());
        final float aspectRatio = currentBounds.x * 1f / currentBounds.y;
        // Find the size of the surface we are mirroring to.
        final Point surfaceSize = virtualDisplay.getSurface().getDefaultSize();
        int mirroredWidth;
        int mirroredHeight;

        // Calculate any width & height deltas caused by DisplayCutout insets
        Point sizeDifference = new Point();
        if (visibleBounds != null) {
            int widthDifference = currentBounds.x - visibleBounds.x;
            int heightDifference = currentBounds.y - visibleBounds.y;
            sizeDifference.set(widthDifference, heightDifference);
        }

        if (surfaceSize.x < surfaceSize.y) {
            // Output surface is portrait, so its width constrains. The mirrored activity is
            // scaled down to fill the width entirely, and will have horizontal black bars at the
            // top and bottom.
            // Also apply scaled insets, to handle case where device has a display cutout which
            // shifts the content horizontally when landscape.
            int adjustedHorizontalInsets = Math.round(sizeDifference.x / aspectRatio);
            int adjustedVerticalInsets = Math.round(sizeDifference.y / aspectRatio);
            mirroredWidth = surfaceSize.x - adjustedHorizontalInsets;
            mirroredHeight = Math.round(surfaceSize.x / aspectRatio) - adjustedVerticalInsets;
        } else {
            // Output surface is landscape, so its height constrains. The mirrored activity is
            // scaled down to fill the height entirely, and will have horizontal black bars on the
            // left and right.
            // Also apply scaled insets, to handle case where device has a display cutout which
            // shifts the content vertically when portrait.
            int adjustedHorizontalInsets = Math.round(sizeDifference.x * aspectRatio);
            int adjustedVerticalInsets = Math.round(sizeDifference.y * aspectRatio);
            mirroredWidth = Math.round(surfaceSize.y * aspectRatio) - adjustedHorizontalInsets;
            mirroredHeight = surfaceSize.y - adjustedVerticalInsets;
        }
        return new Point(mirroredWidth, mirroredHeight);
    }

    /**
     * Validate the given activity is in the hierarchy mirrored to the VirtualDisplay.
     *
     * <p>Note that the hierarchy is present on the VirtualDisplay because the hierarchy is mirrored
     * to the Surface provided to #createVirtualDisplay.
     *
     * @param activity           The activity that we expect to be mirrored
     * @param virtualDisplayId   The id of the virtual display we are mirroring to
     * @param expectedWindowSize The expected size of the mirrored activity
     */
    private static void validateMirroredHierarchy(
            Activity activity, int virtualDisplayId,
            @NonNull Point expectedWindowSize) {
        Predicate<WindowInfo> hasExpectedDimensions =
                windowInfo -> windowInfo.bounds.width() == expectedWindowSize.x
                        && windowInfo.bounds.height() == expectedWindowSize.y;
        Supplier<IBinder> taskWindowTokenSupplier =
                activity.getWindow().getDecorView()::getWindowToken;
        try {
            boolean condition = waitForWindowInfo(hasExpectedDimensions, 5, TimeUnit.SECONDS,
                    taskWindowTokenSupplier, virtualDisplayId);
            assertAndDumpWindowState(TAG,
                    "Mirrored activity isn't the expected size of " + expectedWindowSize,
                    condition);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private RotationSession createManagedRotationSession() {
        mWaitForRotationOnTearDown = true;
        return new RotationSession(mWmState);
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait)
                || (!supportsLandscape && !supportsPortrait);
    }

    protected boolean hasDeviceFeature(final String requiredFeature) {
        return mContext.getPackageManager()
                .hasSystemFeature(requiredFeature);
    }

    /**
     * Stub activity for launching an activity meant to be rotated.
     */
    public static class TestRotationActivity extends Activity {
        // Stub
    }

    /**
     * Wait for any screenshot that has been received already. Assumes that the countdown
     * latch is already set.
     */
    private void waitForLatestScreenshot() {
        if (DEBUG_MODE) {
            // wait until we've received a screenshot
            try {
                assertThat(mScreenshotCountDownLatch.await(SCREENSHOT_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * Save MediaProjection's screenshots to the device to help debug test failures.
     */
    public static class ScreenshotListener implements ImageReader.OnImageAvailableListener {
        private final CountDownLatch mCountDownLatch;
        private final String mMethodName;
        private int mCurrentScreenshot = 0;
        // How often to save an image
        private static final int SCREENSHOT_FREQUENCY = 5;

        public ScreenshotListener(@NonNull String methodName,
                @NonNull CountDownLatch latch) {
            mMethodName = methodName;
            mCountDownLatch = latch;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (mCurrentScreenshot % SCREENSHOT_FREQUENCY != 0) {
                Log.d(TAG, "onImageAvailable - skip this one");
                return;
            }
            Log.d(TAG, "onImageAvailable - processing");
            if (mCountDownLatch != null) {
                mCountDownLatch.countDown();
            }
            mCurrentScreenshot++;

            final Image image = reader.acquireLatestImage();

            assertThat(image).isNotNull();

            final Image.Plane plane = image.getPlanes()[0];

            assertThat(plane).isNotNull();

            final int rowPadding =
                    plane.getRowStride() - plane.getPixelStride() * image.getWidth();
            final Bitmap bitmap = Bitmap.createBitmap(
                    /* width= */ image.getWidth() + rowPadding / plane.getPixelStride(),
                    /* height= */ image.getHeight(), Bitmap.Config.ARGB_8888);
            final ByteBuffer buffer = plane.getBuffer();

            assertThat(buffer).isNotNull();
            assertThat(bitmap).isNotNull(); // why null?

            bitmap.copyPixelsFromBuffer(plane.getBuffer());
            assertThat(bitmap).isNotNull(); // why null?

            try {
                // save to virtual sdcard
                final File outputDirectory = new File(Environment.getExternalStorageDirectory(),
                        "cts." + TAG);
                Log.d(TAG, "Had to create the directory? " + outputDirectory.mkdir());
                final File screenshot = new File(outputDirectory,
                        mMethodName + "_screenshot_" + mCurrentScreenshot + "_"
                                + System.currentTimeMillis() + ".jpg");
                final FileOutputStream stream = new FileOutputStream(screenshot);
                assertThat(stream).isNotNull();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                stream.close();
                image.close();
            } catch (Exception e) {
                Log.e(TAG, "Unable to write out screenshot", e);
            }
        }
    }
}
