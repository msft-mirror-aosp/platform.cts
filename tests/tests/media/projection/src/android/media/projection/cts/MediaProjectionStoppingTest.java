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

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.cts.MediaProjectionActivity;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.projection.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test {@link MediaProjection} stopping behavior.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionStoppingTest
 */
@FrameworkSpecificTest
public class MediaProjectionStoppingTest {
    private static final String TAG = "MediaProjectionStoppingTest";
    private static final int RECORDING_WIDTH = 500;
    private static final int RECORDING_HEIGHT = 700;
    private static final int RECORDING_DENSITY = 200;
    private static final String CALL_HELPER_START_CALL = "start_call";
    private static final String CALL_HELPER_STOP_CALL = "stop_call";

    @Rule
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public ActivityTestRule<MediaProjectionActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionActivity.class, false, false);

    private MediaProjectionActivity mActivity;
    private MediaProjection mMediaProjection;
    private MediaProjection.Callback mCallback = null;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private Context mContext;
    private int mTimeoutMs;
    private LockScreenSession mLockScreenSession;
    private TelecomManager mTelecomManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(
                () -> {
                    mContext.getPackageManager()
                            .revokeRuntimePermission(
                                    mContext.getPackageName(),
                                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                                    new UserHandle(mContext.getUserId()));
                });
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;

        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
        mLockScreenSession = new LockScreenSession(InstrumentationRegistry.getInstrumentation(),
                wmState);
    }

    @After
    public void cleanup() {
        if (mMediaProjection != null) {
            if (mCallback != null) {
                mMediaProjection.unregisterCallback(mCallback);
                mCallback = null;
            }
            cleanupVirtualDisplay();
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        mLockScreenSession.close();
        mActivityRule.finishActivity();
    }

    @Test
    @RequiresFlagsEnabled(
            android.companion.virtualdevice.flags.Flags.FLAG_MEDIA_PROJECTION_KEYGUARD_RESTRICTIONS)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionStopsOnKeyguard() throws Exception {
        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();

        try {
            mLockScreenSession.sleepDevice();

            assertWithMessage("MediaProjection not stopped in " + mTimeoutMs + "ms")
                    .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mLockScreenSession.wakeUpDevice();
            mLockScreenSession.unlockDevice();
        }
    }

    @Test
    @RequiresFlagsEnabled(
            android.companion.virtualdevice.flags.Flags.FLAG_MEDIA_PROJECTION_KEYGUARD_RESTRICTIONS)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionWithoutDisplayDoesNotStopOnKeyguard() throws Exception {
        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));

        try {
            mLockScreenSession.sleepDevice();

            assertWithMessage("MediaProjection was stopped unexpectedly")
                    .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            mLockScreenSession.wakeUpDevice();
            mLockScreenSession.unlockDevice();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STOP_MEDIA_PROJECTION_ON_CALL_END)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionStop_callStartedAfterMediaProjection_doesNotStop()
            throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM));

        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();

        try {
            startPhoneCall();
        } finally {
            endPhoneCall();
        }

        assertWithMessage("MediaProjection should not be stopped on call end")
                .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STOP_MEDIA_PROJECTION_ON_CALL_END)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionStop_callStartedBeforeMediaProjection_shouldStop()
            throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM));
        CountDownLatch latch = new CountDownLatch(1);
        try {
            startPhoneCall();

            startMediaProjection();

            mCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    latch.countDown();
                }
            };
            mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
            createVirtualDisplay();

        } finally {
            endPhoneCall();
        }

        assertWithMessage("MediaProjection was not stopped after call end")
                .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isTrue();
    }

    private void startPhoneCall() {
        runWithShellPermissionIdentity(
                () -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    mContext.getSystemService(TelephonyManager.class)
                            .registerTelephonyCallback(
                                    mContext.getMainExecutor(),
                                    new TestCallStateListener(
                                            state -> {
                                                runWithShellPermissionIdentity(
                                                        () -> {
                                                            if (mTelecomManager.isInCall()) {
                                                                latch.countDown();
                                                            }
                                                        });
                                            }));
                    mContext.startActivity(getCallHelperIntent(CALL_HELPER_START_CALL));

                    assertWithMessage("Call was not started after timeout")
                            .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS))
                            .isTrue();
                });
    }

    private void endPhoneCall() {
        runWithShellPermissionIdentity(
                () -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    mContext.getSystemService(TelephonyManager.class)
                            .registerTelephonyCallback(
                                    mContext.getMainExecutor(),
                                    new TestCallStateListener(
                                            state -> {
                                                runWithShellPermissionIdentity(
                                                        () -> {
                                                            if (!mTelecomManager.isInCall()) {
                                                                latch.countDown();
                                                            }
                                                        });
                                            }));
                    mContext.startActivity(getCallHelperIntent(CALL_HELPER_STOP_CALL));

                    assertWithMessage("Call was not ended after timeout")
                            .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS))
                            .isTrue();
                });
    }

    private Intent getCallHelperIntent(String action) {
        return new Intent(action)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setComponent(
                        new ComponentName(
                                "android.media.projection.cts.helper",
                                "android.media.projection.cts.helper.CallHelperActivity"));
    }

    private void startMediaProjection() throws Exception {
        mActivityRule.launchActivity(null);
        mActivity = mActivityRule.getActivity();
        mMediaProjection = mActivity.waitForMediaProjection();
    }

    private void createVirtualDisplay() {
        mImageReader = ImageReader.newInstance(RECORDING_WIDTH, RECORDING_HEIGHT,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                new VirtualDisplay.Callback() {
                    @Override
                    public void onStopped() {
                        super.onStopped();
                        // VirtualDisplay stopped by the system; no more frames incoming. Must
                        // release VirtualDisplay
                        Log.v(TAG, "handleVirtualDisplayStopped");
                        cleanupVirtualDisplay();
                    }
                }, new Handler(Looper.getMainLooper()));
    }

    private void cleanupVirtualDisplay() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mVirtualDisplay != null) {
            final Surface surface = mVirtualDisplay.getSurface();
            if (surface != null) {
                surface.release();
            }
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private static final class TestCallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        private final Consumer<Integer> mCallStateConsumer;

        private TestCallStateListener(Consumer<Integer> callStateConsumer) {
            mCallStateConsumer = callStateConsumer;
        }

        @Override
        public void onCallStateChanged(int state) {
            mCallStateConsumer.accept(state);
        }
    }
}
