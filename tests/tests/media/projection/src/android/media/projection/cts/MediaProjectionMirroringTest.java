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

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.view.Surface;
import android.view.WindowMetrics;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
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
@FrameworkSpecificTest
public class MediaProjectionMirroringTest {
    private static final String TAG = "MediaProjectionMirroringTest";
    private static final int TOLERANCE = 1;
    private static final int TIMEOUT_MS = 1000;
    private Context mContext;

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(mContext.getUserId()));
        });
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testDisplayCapture() throws Exception {
        Intent testActivityIntent = new Intent(mContext, Activity.class);
        // Start full screen capture.
        mMediaProjectionRule.startMediaProjection();

        final WindowMetrics maxWindowMetrics =
                mMediaProjectionRule.getActivity().getWindowManager().getMaximumWindowMetrics();

        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(
                        maxWindowMetrics.getBounds().width(),
                        maxWindowMetrics.getBounds().height());

        try (ActivityScenario<Activity> activityScenario =
                ActivityScenario.launch(testActivityIntent)) {
            activityScenario.onActivity(
                    activity -> {
                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account for
                        // possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        activity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);
                        validateMirroredHierarchy(
                                activity,
                                virtualDisplay.getDisplay().getDisplayId(),
                                new Point(activityRect.width(), activityRect.height()));
                    });
        }
    }

    // Validate that the mirrored hierarchy is the expected size after rotating the default display.
    @Test
    public void testDisplayCapture_rotation() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        // Start full screen capture.
        mMediaProjectionRule.startMediaProjection();
        Activity activity = mMediaProjectionRule.getActivity();

        final WindowMetrics maxWindowMetrics =
                activity.getWindowManager().getMaximumWindowMetrics();
        final int initialRotation = activity.getDisplay().getRotation();

        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(
                        maxWindowMetrics.getBounds().width(),
                        maxWindowMetrics.getBounds().height());

        Intent testActivityIntent = new Intent(mContext, TestRotationActivity.class);

        try (ActivityScenario<TestRotationActivity> activityScenario =
                        ActivityScenario.launch(testActivityIntent);
                RotationSession rotationSession = new RotationSession(mWmState); ) {
            rotateDeviceAndWaitForActivity(rotationSession, initialRotation);
            // Re-fetch the activity since reference may have been modified during rotation.
            activityScenario.onActivity(
                    testActivity -> {
                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account for
                        // possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        testActivity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

                        final Point mirroredSize =
                                calculateScaledMirroredActivitySize(
                                        testActivity.getWindowManager().getCurrentWindowMetrics(),
                                        virtualDisplay,
                                        new Point(activityRect.width(), activityRect.height()));
                        validateMirroredHierarchy(
                                testActivity,
                                virtualDisplay.getDisplay().getDisplayId(),
                                mirroredSize);
                    });
        }
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testSingleAppCapture() throws Exception {
        final ActivityOptions.LaunchCookie launchCookie = new ActivityOptions.LaunchCookie();

        // Select single app capture if supported.
        mMediaProjectionRule.startMediaProjection(launchCookie);

        try (ActivityScenario<Activity> activityScenario =
                ActivityScenario.launch(
                        new Intent(mContext, Activity.class),
                        createActivityScenarioWithLaunchCookie(launchCookie))) {
            activityScenario.onActivity(
                    activity -> {
                        final WindowMetrics maxWindowMetrics =
                                activity.getWindowManager().getMaximumWindowMetrics();

                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account
                        // for possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        activity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);
                        Log.d(
                                TAG,
                                "Source Activity: MaxMetrics="
                                        + maxWindowMetrics.getBounds()
                                        + " VisibleRect="
                                        + activityRect);

                        try {
                            // Start capture of the single app.
                            VirtualDisplay virtualDisplay =
                                    mMediaProjectionRule.createVirtualDisplay(
                                            maxWindowMetrics.getBounds().width(),
                                            maxWindowMetrics.getBounds().height());
                            Log.d(
                                    TAG,
                                    "Virtual Display created with size: "
                                            + virtualDisplay.getSurface().getDefaultSize());

                            final Point mirroredSize =
                                    calculateScaledMirroredActivitySize(
                                            maxWindowMetrics,
                                            virtualDisplay,
                                            new Point(activityRect.width(), activityRect.height()));
                            validateMirroredHierarchy(
                                    activity,
                                    virtualDisplay.getDisplay().getDisplayId(),
                                    mirroredSize);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
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

    /**
     * Rotates the device 90 degrees & waits for the display & activity configuration to stabilize.
     */
    private void rotateDeviceAndWaitForActivity(
            @NonNull RotationSession rotationSession, @Surface.Rotation int initialRotation) {
        // Rotate the device by 90 degrees
        rotationSession.set((initialRotation + 1) % (ROTATION_270 + 1),
                /* waitForDeviceRotation=*/ true);
        try {
            waitForStableWindowGeometry(Duration.ofMillis(TIMEOUT_MS));
        } catch (InterruptedException e) {
            Log.e(TAG, "Unable to wait for window to stabilize after rotation: " + e.getMessage());
        }
    }

    /**
     * Calculates the expected size of a mirrored activity on a VirtualDisplay.
     *
     * <p>In "Single App" capture, the system mirrors the entire Task container. This container is
     * scaled to fit the VirtualDisplay using a "fit-center" approach, preserving aspect ratio. The
     * scale factor is determined by the ratio between the container's bounds and the
     * VirtualDisplay's surface size.
     *
     * @param containerMetrics The metrics of the container being mirrored (usually the Task).
     * @param virtualDisplay The VirtualDisplay where the content is mirrored and scaled.
     * @param activitySize The original size of the activity being mirrored. If null, the
     *     container's size is used.
     * @return The expected size of the mirrored activity on the VirtualDisplay.
     */
    private static Point calculateScaledMirroredActivitySize(
            @NonNull WindowMetrics containerMetrics,
            @NonNull VirtualDisplay virtualDisplay,
            @Nullable Point activitySize) {
        // The container being scaled is defined by containerMetrics.
        final Rect containerBounds = containerMetrics.getBounds();

        // Find the size of the surface we are mirroring to.
        final Point surfaceSize = virtualDisplay.getSurface().getDefaultSize();

        // System scaling logic: scale = min(target_width / source_width, target_height /
        // source_height)
        float scaleX = (float) surfaceSize.x / containerBounds.width();
        float scaleY = (float) surfaceSize.y / containerBounds.height();
        float scale = Math.min(scaleX, scaleY);

        // The activity size to be scaled.
        final Point sourceSize =
                activitySize != null
                        ? activitySize
                        : new Point(containerBounds.width(), containerBounds.height());

        int mirroredWidth = Math.round(sourceSize.x * scale);
        int mirroredHeight = Math.round(sourceSize.y * scale);

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
                windowInfo -> {
                    int widthDiff = Math.abs(windowInfo.bounds.width() - expectedWindowSize.x);
                    int heightDiff = Math.abs(windowInfo.bounds.height() - expectedWindowSize.y);
                    Log.d(
                            TAG,
                            "Checking Window: "
                                    + windowInfo
                                    + " RawBounds="
                                    + windowInfo.bounds
                                    + " Expected="
                                    + expectedWindowSize
                                    + " Diff=("
                                    + widthDiff
                                    + ","
                                    + heightDiff
                                    + ")");
                    return widthDiff <= TOLERANCE && heightDiff <= TOLERANCE;
                };
        Supplier<IBinder> taskWindowTokenSupplier =
                activity.getWindow().getDecorView()::getWindowToken;
        try {
            Log.e(TAG, "WindowToken: " + taskWindowTokenSupplier.get());
            boolean condition = waitForWindowInfo(hasExpectedDimensions, Duration.ofSeconds(5),
                    taskWindowTokenSupplier, virtualDisplayId);
            assertAndDumpWindowState(TAG,
                    "Mirrored activity isn't the expected size of " + expectedWindowSize,
                    condition);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        mWmState.computeState();
        final boolean isFixedToUserRotation = mWmState.isFixedToUserRotation();
        return (supportsLandscape && supportsPortrait && !isFixedToUserRotation)
                || (!supportsLandscape && !supportsPortrait && !isFixedToUserRotation);
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
}
