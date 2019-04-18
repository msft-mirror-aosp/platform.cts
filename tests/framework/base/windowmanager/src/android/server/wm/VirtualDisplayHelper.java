/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.server.wm.ActivityManagerTestBase.isDisplayOn;
import static android.server.wm.StateLogger.logAlways;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.fail;

import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.SystemClock;

import com.android.compatibility.common.util.SystemUtil;

import java.util.function.Predicate;

/**
 * Helper class to create virtual display.
 */
class VirtualDisplayHelper {

    private static final String VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay";
    /** See {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD}. */
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;

    private static final int DENSITY = 160;
    static final int HEIGHT = 480;
    static final int WIDTH = 800;

    private ImageReader mReader;
    private VirtualDisplay mVirtualDisplay;
    private boolean mCreated;

    void createAndWaitForDisplay(boolean requestShowWhenLocked) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            createVirtualDisplay(requestShowWhenLocked, 0 /* virtualDisplayFlags */);
            waitForDisplayState(mVirtualDisplay.getDisplay().getDisplayId() /* default */,
                    true /* on */);
            mCreated = true;
        });
    }

    int createAndWaitForPublicDisplay(boolean requestShowWhenLocked) {
        createVirtualDisplay(requestShowWhenLocked, VIRTUAL_DISPLAY_FLAG_PUBLIC);
        waitForDisplayState(mVirtualDisplay.getDisplay().getDisplayId() /* default */,
                true /* on */);
        mCreated = true;
        return mVirtualDisplay.getDisplay().getDisplayId();
    }

    void turnDisplayOff() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mVirtualDisplay.setSurface(null);
            waitForDisplayState(mVirtualDisplay.getDisplay().getDisplayId() /* displayId */,
                    false /* on */);
        });
    }

    void turnDisplayOn() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mVirtualDisplay.setSurface(mReader.getSurface());
            waitForDisplayState(mVirtualDisplay.getDisplay().getDisplayId() /* displayId */,
                    true /* on */);
        });
    }

    void releaseDisplay() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (mCreated) {
                mVirtualDisplay.release();
                mReader.close();
                waitForDisplayCondition(mVirtualDisplay.getDisplay().getDisplayId() /* displayId */,
                        onState -> onState != null && onState == false,
                        "Waiting for virtual display destroy");
            }
            mCreated = false;
        });
    }

    private void createVirtualDisplay(boolean requestShowWhenLocked, int virtualDisplayFlags) {
        mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);

        final DisplayManager displayManager = getInstrumentation()
                .getContext().getSystemService(DisplayManager.class);

        int flags = VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | virtualDisplayFlags;

        if (requestShowWhenLocked) {
            flags |= VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
        }
        mVirtualDisplay = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, WIDTH, HEIGHT, DENSITY, mReader.getSurface(), flags);
    }

    static void waitForDefaultDisplayState(boolean wantOn) {
        waitForDisplayState(DEFAULT_DISPLAY /* default */, wantOn);
    }

    private static void waitForDisplayState(int displayId, boolean wantOn) {
        waitForDisplayCondition(displayId, state -> state != null && state == wantOn,
                "Waiting for " + ((displayId == DEFAULT_DISPLAY) ? "default" : "virtual")
                        + " display "
                        + (wantOn ? "on" : "off"));
    }

    private static void waitForDisplayCondition(int displayId,
            Predicate<Boolean> condition, String message) {
        for (int retry = 1; retry <= 10; retry++) {
            if (condition.test(isDisplayOn(displayId))) {
                return;
            }
            logAlways(message + "... retry=" + retry);
            SystemClock.sleep(500);
        }
        fail(message + " failed");
    }
}
