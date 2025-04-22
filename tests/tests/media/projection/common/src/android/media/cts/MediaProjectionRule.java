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
package android.media.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.test.core.app.ActivityScenario;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A test rule for testing {@link MediaProjection} and related components
 *
 * <p>The test rule can be configured to show {@link MediaProjectionRule#withConsent()}, or skip
 * {@link MediaProjectionRule#skipConsent()}, the MediaProjection consent flow in the tests.
 * Skipping is generally recommended to keep test runtime shorter unless the test is validating
 * things about the consent flow.
 *
 * <p>To start a new MediaProjection session call {@link #startMediaProjection()} and its overloads.
 *
 * <p>If the test needs more granularity:
 * <li>Show the MediaProjection consent flow with:
 * {@link #showMediaProjectionConsent(MediaProjectionConfig)}</li>
 * <li>Show + grant consent for the MediaProjection session with:
 * {@link #authorizeMediaProjection(ActivityOptions.LaunchCookie)}</li>
 * <li>Start a new MediaProjection session: {@link #newMediaProjectionSession()}</li>
 */
public class MediaProjectionRule implements TestRule {
    private static final String TAG = "MediaProjectionRule";
    // Enable debug mode to save screenshots from MediaProjection session.
    private static final boolean DEBUG_MODE = false;
    private static final int RECORDING_WIDTH = 500;
    private static final int RECORDING_HEIGHT = 700;
    private static final int RECORDING_DENSITY = 200;
    private static final int TIMEOUT_MS = 3000;
    private static final int SCREENSHOT_TIMEOUT_MS = 1000;

    private final MediaProjection.Callback mEmptyMediaProjectionCallback =
            new MediaProjection.Callback() {};
    private final MediaProjectionTrackerRule mMediaProjectionTrackerRule =
            new MediaProjectionTrackerRule();
    private final Context mContext = getInstrumentation().getTargetContext();
    private final DisplayManager mDisplayManager = mContext.getSystemService(DisplayManager.class);
    private final MediaProjectionManager mMediaProjectionManager =
            mContext.getSystemService(MediaProjectionManager.class);

    private boolean mSkipConsent = true;
    private android.media.cts.MediaProjectionActivity mActivity;
    private String mTestName;

    @Override
    public Statement apply(Statement base, Description description) {
        assertThat(mMediaProjectionManager).isNotNull();
        assertThat(mDisplayManager).isNotNull();

        mTestName = String.format("%s#%s", description.getClassName(), description.getMethodName());
        return RuleChain.outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
                .around(mMediaProjectionTrackerRule)
                .apply(base, description);
    }

    /**
     * Enables that tests fully show the MediaProjection consent flow and pass through it by using
     * UiAutomator. This makes the tests less efficient as clicking through the consent flow takes
     * time. Only set for tests that absolutely need to assert details in the consent flow.
     */
    public void enableConsentFlow() {
        mSkipConsent = false;
    }

    /** Get an instance of MediaProjectionManager. */
    public MediaProjectionManager getMediaProjectionManager() {
        return mMediaProjectionManager;
    }

    /**
     * Starts the MediaProjection consent flow but doesn't actually start a MediaProjection session.
     */
    public void showMediaProjectionConsent(@Nullable MediaProjectionConfig config)
            throws Exception {
        showMediaProjectionConsent(config, null, null);
    }

    private void showMediaProjectionConsent(
            @Nullable MediaProjectionConfig config,
            @Nullable ActivityOptions.LaunchCookie launchCookie,
            @Nullable String foregroundServiceClass)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Intent intent =
                new Intent(mContext, android.media.cts.MediaProjectionActivity.class)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK);

        if (config != null) {
            intent.putExtra(android.media.cts.MediaProjectionActivity.EXTRA_MP_CONFIG, config);
        }
        if (launchCookie != null) {
            intent.putExtra(
                    android.media.cts.MediaProjectionActivity.EXTRA_LAUNCH_COOKIE, launchCookie);
        }
        if (foregroundServiceClass != null) {
            intent.putExtra(
                    android.media.cts.MediaProjectionActivity.EXTRA_FOREGROUND_SERVICE_CLASS,
                    foregroundServiceClass);
        }
        if (mSkipConsent) {
            intent.putExtra(android.media.cts.MediaProjectionActivity.EXTRA_SKIP_CONSENT, true);
        }

        mMediaProjectionTrackerRule.mActivityScenario = launchConsentActivity(intent);
        mMediaProjectionTrackerRule.mActivityScenario.onActivity(
                activity -> {
                    mActivity = activity;
                    latch.countDown();
                });
        assertWithMessage("MediaProjectionActivity not started")
                .that(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
    }

    private ActivityScenario<android.media.cts.MediaProjectionActivity> launchConsentActivity(
            Intent intent) throws Exception {
        if (mSkipConsent) {
            // By adopting Shell permission identity we hold the CAPTURE_VIDEO_OUTPUT permission
            // This causes MediaProjectionPermissionActivity to instantly approve the request.
            return SystemUtil.callWithShellPermissionIdentity(
                    () -> ActivityScenario.launch(intent));
        }
        return ActivityScenario.launch(intent);
    }

    /** Start a MediaProjection session. */
    public MediaProjection startMediaProjection() throws Exception {
        return startMediaProjection(null, null, null, false);
    }

    /** Start a MediaProjection session (with a custom foregroundService class). */
    public MediaProjection startMediaProjection(String foregroundServiceClass) throws Exception {
        return startMediaProjection(null, null, foregroundServiceClass, false);
    }

    /** Start a MediaProjection session (with a custom MediaProjectionConfig). */
    public MediaProjection startMediaProjection(MediaProjectionConfig config) throws Exception {
        return startMediaProjection(config, null, null, false);
    }

    /** Start a MediaProjection session for a specific LaunchCookie. */
    public MediaProjection startMediaProjection(ActivityOptions.LaunchCookie launchCookie)
            throws Exception {
        return startMediaProjection(null, launchCookie, null, false);
    }

    /** Start a MediaProjection session for a specific LaunchCookie. */
    public MediaProjection startMediaProjection(boolean skipDefaultCallback) throws Exception {
        return startMediaProjection(null, null, null, skipDefaultCallback);
    }

    private MediaProjection startMediaProjection(
            @Nullable MediaProjectionConfig config,
            @Nullable ActivityOptions.LaunchCookie launchCookie,
            @Nullable String foregroundServiceClass,
            boolean skipDefaultCallback)
            throws Exception {
        showMediaProjectionConsent(config, launchCookie, foregroundServiceClass);
        mActivity.performMediaProjectionConsent();
        mMediaProjectionTrackerRule.mMediaProjection = mActivity.startMediaProjection();
        mMediaProjectionTrackerRule.mActivityScenario.close();
        if (!skipDefaultCallback) {
            // Register an empty callback. Tests that want to validate callback behavior can still
            // register individual callbacks.
            registerCallback(mEmptyMediaProjectionCallback);
        }
        return mMediaProjectionTrackerRule.mMediaProjection;
    }

    /**
     * Launches the MediaProjectionActivity and performs the consent flow. Does not start a new
     * MediaProjection session. Call {@link MediaProjectionRule#newMediaProjectionSession()} to
     * start a new MediaProjection session.
     */
    public void authorizeMediaProjection(@Nullable ActivityOptions.LaunchCookie launchCookie)
            throws Exception {
        showMediaProjectionConsent(null, launchCookie, null);
        mActivity.performMediaProjectionConsent();
    }

    /**
     * Creates a new MediaProjection session. Requires showing and passing the MediaProjection
     * beforehand.
     *
     * <p>Use {@link #authorizeMediaProjection(android.app.ActivityOptions.LaunchCookie)} to pass
     * the consent flow first.
     */
    public MediaProjection newMediaProjectionSession() throws Exception {
        mMediaProjectionTrackerRule.mMediaProjection = mActivity.startMediaProjection();
        return mMediaProjectionTrackerRule.mMediaProjection;
    }

    /** Register a MediaProjection.Callback. */
    public void registerCallback(MediaProjection.Callback callback) {
        if (mMediaProjectionTrackerRule.mMediaProjection == null) {
            throw new IllegalStateException("MediaProjection not yet started.");
        }
        mMediaProjectionTrackerRule.mCallbacks.add(callback);
        mMediaProjectionTrackerRule.mMediaProjection.registerCallback(
                callback, mMediaProjectionTrackerRule.mBackgroundHandler);
    }

    /** Create a VirtualDisplay for the MediaProjection. */
    public VirtualDisplay createVirtualDisplay() throws InterruptedException {
        return createVirtualDisplay(
                mMediaProjectionTrackerRule.mMediaProjection, RECORDING_WIDTH, RECORDING_HEIGHT);
    }

    /** Create a VirtualDisplay for a MediaProjection. */
    public VirtualDisplay createVirtualDisplay(MediaProjection mediaProjection)
            throws InterruptedException {
        return createVirtualDisplay(mediaProjection, RECORDING_WIDTH, RECORDING_HEIGHT);
    }

    /** Create a VirtualDisplay for a MediaProjection. */
    public VirtualDisplay createVirtualDisplay(int width, int height) throws InterruptedException {
        return createVirtualDisplay(mMediaProjectionTrackerRule.mMediaProjection, width, height);
    }

    /** Create a VirtualDisplay for the MediaProjection with specific dimensions. */
    public VirtualDisplay createVirtualDisplay(
            MediaProjection mediaProjection, int width, int height) throws InterruptedException {
        if (mediaProjection == null) {
            throw new IllegalStateException("MediaProjection not yet started.");
        }

        TestDisplayListener displayListener = new TestDisplayListener(mDisplayManager);
        mMediaProjectionTrackerRule.mDisplayListeners.add(displayListener);
        mDisplayManager.registerDisplayListener(displayListener,
                mMediaProjectionTrackerRule.mBackgroundHandler);

        ImageReader imageReader =
                ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, /* maxImages= */ 1);
        mMediaProjectionTrackerRule.mImageReaders.add(imageReader);
        CountDownLatch mScreenshotCountDownLatch = new CountDownLatch(1);
        if (DEBUG_MODE) {
            ScreenshotListener screenshotListener =
                    new ScreenshotListener(mTestName, mScreenshotCountDownLatch);
            imageReader.setOnImageAvailableListener(
                    screenshotListener, mMediaProjectionTrackerRule.mBackgroundHandler);
        }
        VirtualDisplay virtualDisplay =
                mediaProjection.createVirtualDisplay(
                        mTestName,
                        width,
                        height,
                        RECORDING_DENSITY,
                        VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null);
        assertWithMessage("Could not create VirtualDisplay").that(virtualDisplay).isNotNull();
        mMediaProjectionTrackerRule.mVirtualDisplays.add(virtualDisplay);

        if (DEBUG_MODE) {
            // wait until we've received a screenshot
            try {
                assertThat(
                                mScreenshotCountDownLatch.await(
                                        SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        displayListener.awaitDisplayIdOn(virtualDisplay.getDisplay().getDisplayId());
        displayListener.unregister();
        return virtualDisplay;
    }

    /** Get the Activity hosting the MediaProjection consent flow. */
    public android.media.cts.MediaProjectionActivity getActivity() {
        return mActivity;
    }

    /** Save MediaProjection's screenshots to the device to help debug test failures. */
    public static class ScreenshotListener implements ImageReader.OnImageAvailableListener {
        private final CountDownLatch mCountDownLatch;
        private final String mMethodName;
        private int mCurrentScreenshot = 0;
        // How often to save an image
        private static final int SCREENSHOT_FREQUENCY = 5;

        public ScreenshotListener(@NonNull String methodName, @NonNull CountDownLatch latch) {
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
            mCountDownLatch.countDown();
            mCurrentScreenshot++;

            final Image image = reader.acquireLatestImage();

            assertThat(image).isNotNull();

            final Image.Plane plane = image.getPlanes()[0];

            assertThat(plane).isNotNull();

            final int rowPadding = plane.getRowStride() - plane.getPixelStride() * image.getWidth();
            final Bitmap bitmap =
                    Bitmap.createBitmap(
                            /* width= */ image.getWidth() + rowPadding / plane.getPixelStride(),
                            /* height= */ image.getHeight(),
                            Bitmap.Config.ARGB_8888);
            final ByteBuffer buffer = plane.getBuffer();

            assertThat(buffer).isNotNull();
            assertThat(bitmap).isNotNull(); // why null?

            bitmap.copyPixelsFromBuffer(plane.getBuffer());
            assertThat(bitmap).isNotNull(); // why null?

            try {
                // save to virtual sdcard
                final File outputDirectory =
                        new File(Environment.getExternalStorageDirectory(), "cts." + TAG);
                Log.d(TAG, "Had to create the directory? " + outputDirectory.mkdir());
                final File screenshot =
                        new File(
                                outputDirectory,
                                mMethodName
                                        + "_screenshot_"
                                        + mCurrentScreenshot
                                        + "_"
                                        + System.currentTimeMillis()
                                        + ".jpg");
                final FileOutputStream stream = new FileOutputStream(screenshot);
                assertThat(stream).isNotNull();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                stream.close();
            } catch (Exception e) {
                Log.e(TAG, "Unable to write out screenshot", e);
            } finally {
                image.close();
            }
        }
    }

    private static final class TestDisplayListener implements DisplayManager.DisplayListener {
        private final DisplayManager mDisplayManager;
        private final BlockingQueue<Integer> mDisplaysThatAreOn = new LinkedBlockingQueue<>();

        public TestDisplayListener(DisplayManager displayManager) {
            mDisplayManager = displayManager;
        }

        public void awaitDisplayIdOn(int displayId) throws InterruptedException {
            String message = String.format("Display %d was not ON after timeout", displayId);
            int retries = 10;
            Integer display;
            do {
                display = mDisplaysThatAreOn.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                retries--;
            } while (display != null && display != displayId && retries > 0);
            assertWithMessage(message).that(display).isEqualTo(displayId);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            checkDisplayState(displayId);
        }

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            checkDisplayState(displayId);
        }

        private void checkDisplayState(int displayId) {
            Display display = mDisplayManager.getDisplay(displayId);
            if (display != null && display.getState() == Display.STATE_ON) {
                mDisplaysThatAreOn.offer(displayId);
            }
        }

        public void unregister() {
            mDisplayManager.unregisterDisplayListener(this);
        }
    }

    /**
     * Internal rule that tracks the Active MediaProjection session and resources and ensures they
     * are properly closed and released after the test.
     */
    private static final class MediaProjectionTrackerRule extends ExternalResource {

        private HandlerThread mBackgroundThread;

        final Set<MediaProjection.Callback> mCallbacks = new ArraySet<>();
        final Set<ImageReader> mImageReaders = new ArraySet<>();
        final Set<VirtualDisplay> mVirtualDisplays = new ArraySet<>();
        final Set<TestDisplayListener> mDisplayListeners = new ArraySet<>();

        Handler mBackgroundHandler;
        ActivityScenario<android.media.cts.MediaProjectionActivity> mActivityScenario;
        MediaProjection mMediaProjection;

        @Override
        protected void before() {
            mBackgroundThread = new HandlerThread("BackgroundThread");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }

        @Override
        protected void after() {
            if (mMediaProjection != null) {
                for (MediaProjection.Callback callback : mCallbacks) {
                    mMediaProjection.unregisterCallback(callback);
                }
                mCallbacks.clear();
                mMediaProjection.stop();
                mMediaProjection = null;
            }

            cleanupVirtualDisplay();

            for (TestDisplayListener displayListener : mDisplayListeners) {
                displayListener.unregister();
            }
            mDisplayListeners.clear();

            if (mActivityScenario != null) {
                mActivityScenario.close();
            }
            stopBackgroundThread();
        }

        void cleanupVirtualDisplay() {
            for (ImageReader imageReader : mImageReaders) {
                imageReader.close();
            }
            mImageReaders.clear();

            for (VirtualDisplay virtualDisplay : mVirtualDisplays) {
                final Surface surface = virtualDisplay.getSurface();
                if (surface != null) {
                    surface.release();
                }
                virtualDisplay.release();
            }
            mVirtualDisplays.clear();
        }

        public void stopBackgroundThread() {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundHandler = null;
            }
        }
    }
}
