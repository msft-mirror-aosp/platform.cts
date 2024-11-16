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

package android.display.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Pair;
import android.view.Display;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.display.feature.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests that applications can receive display events correctly.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DisplayEventTest extends TestBase {
    private static final String TAG = "DisplayEventTest";

    private static final int MESSAGE_CALLBACK = 1;

    private static final long TEST_FAILURE_TIMEOUT_MSEC = 10000;


    private static final int DISPLAY_ADDED = 1;
    private static final int DISPLAY_CHANGED = 2;
    private static final int DISPLAY_REMOVED = 3;

    private final Object mLock = new Object();

    private Instrumentation mInstrumentation;
    private Context mContext;
    private DisplayManager mDisplayManager;

    private Display mDisplay;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
            Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);


    @Rule
    public ActivityScenarioRule<DisplayEventPropertyChangeActivity> mActivityRule =
            new ActivityScenarioRule<>(DisplayEventPropertyChangeActivity.class);

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Messenger mMessenger;
    private final LinkedBlockingQueue<Pair<Integer, Integer>> mExpectations =
            new LinkedBlockingQueue<>();
    private int mInitialMatchContentFrameRate;
    private DisplayManager.DisplayListener mDisplayListener;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        mHandlerThread = new HandlerThread("handler");
        mHandlerThread.start();
        mHandler = new TestHandler(mHandlerThread.getLooper());
        mMessenger = new Messenger(mHandler);

        mInitialMatchContentFrameRate = toSwitchingType(
                mDisplayManager.getMatchContentFrameRateUserPreference());
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        if (mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testDisplayStateChangedEvent() throws Exception {
        registerDisplayListener((int) DisplayManager.EVENT_FLAG_DISPLAY_STATE);

        // Change the display state
        switchDisplayState();

        // Validate the event was received
        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testDisplayRefreshRateChangedEvent() {
        registerDisplayListener((int) DisplayManager.EVENT_FLAG_DISPLAY_REFRESH_RATE);

        // Change the display state
        switchRefreshRate();

        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    private void registerDisplayListener(int eventFlagMask) {
        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                callback(displayId, DISPLAY_ADDED);
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                callback(displayId, DISPLAY_REMOVED);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                callback(displayId, DISPLAY_CHANGED);
            }
        };
        mDisplayManager.registerDisplayListener(mContext.getMainExecutor(), eventFlagMask,
                mDisplayListener);
    }

    /**
     * Add the received display event from the test activity to the queue
     *
     * @param event The corresponding display event
     */
    private void addDisplayEvent(int displayId, int event) {
        Log.d(TAG, "Received " + displayId + " " + event);
        mExpectations.offer(new Pair<>(displayId, event));
    }

    /**
     * Wait for the expected display event from the test activity
     *
     * @param expect The expected display event
     */
    private void waitDisplayEvent(int displayId, int expect) {
        while (true) {
            try {
                Pair<Integer, Integer> expectedPair = new Pair<>(displayId, expect);
                Pair<Integer, Integer> event = mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC,
                        TimeUnit.MILLISECONDS);
                assertNotNull(event);
                if (expectedPair.equals(event)) {
                    return;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void switchDisplayState() throws Exception {
        if (mDisplay.getState() == Display.STATE_OFF) {
            SystemUtil.runShellCommand(mInstrumentation, "cmd power wakeup");
        } else {
            SystemUtil.runShellCommand(mInstrumentation, "cmd power sleep");
        }
    }

    private void switchRefreshRate() {
        mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);

        int highestRefreshRateModeId = getHighestRefreshRateModeId();
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setModeId(highestRefreshRateModeId);
        });
    }

    private int getHighestRefreshRateModeId() {
        int refreshRateModeId = mDisplay.getMode().getModeId();
        boolean hasMultipleRefreshRates = false;
        for (Display.Mode mode : mDisplay.getSupportedModes()) {
            if (mode.getPhysicalHeight() != mDisplay.getMode().getPhysicalHeight()) {
                continue;
            }

            if (mode.getPhysicalWidth() != mDisplay.getMode().getPhysicalWidth()) {
                continue;
            }
            if (mode.getRefreshRate() != mDisplay.getMode().getRefreshRate()) {
                refreshRateModeId = mode.getModeId();
                hasMultipleRefreshRates = true;
            }
        }

        // Run the test only if multiple refresh rates are supported
        assumeTrue(hasMultipleRefreshRates);
        return refreshRateModeId;
    }

    private static int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }

    private class TestHandler extends Handler {
        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CALLBACK:
                    synchronized (mLock) {
                        addDisplayEvent(msg.arg1, msg.arg2);
                    }
                    break;
                default:
                    fail("Unexpected value: " + msg.what);
                    break;
            }
        }
    }

    private void callback(int displayId, int event) {
        try {
            Message msg = Message.obtain();
            msg.what = MESSAGE_CALLBACK;
            msg.arg1 = displayId;
            msg.arg2 = event;
            Log.d(TAG, "Msg " + msg.arg1 + " " + msg.arg2);
            mMessenger.send(msg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
