/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.companion.virtualdevice.flags.Flags;
import android.graphics.PointF;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;

import com.android.cts.input.DefaultPointerSpeedRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class VirtualMouseTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualMouseTestDevice";

    private static final float EPSILON = 0.001f;

    @Rule
    public final DefaultPointerSpeedRule mDefaultPointerSpeedRule = new DefaultPointerSpeedRule();

    private VirtualMouse mVirtualMouse;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualMouse = VirtualInputDeviceCreator.createAndPrepareMouse(mVirtualDevice, DEVICE_NAME,
                mVirtualDisplay.getDisplay()).getDevice();
    }

    @Test
    public void sendButtonEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_PRESS, startPosition.x, startPosition.y,
                MotionEvent.BUTTON_PRIMARY, 1f /* pressure */);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        final MotionEvent buttonReleaseEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_RELEASE, startPosition.x, startPosition.y,
                0 /* buttonState */, 0f /* pressure */);
        buttonReleaseEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_DOWN, startPosition.x,
                        startPosition.y, MotionEvent.BUTTON_PRIMARY, 1f /* pressure */),
                buttonPressEvent,
                buttonReleaseEvent,
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_UP, startPosition.x,
                        startPosition.y, 0 /* buttonState */, 0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */)));
    }

    @Test
    public void sendRelativeEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(relativeChangeY)
                .setRelativeX(relativeChangeX)
                .build());
        final float firstStopPositionX = startPosition.x + relativeChangeX;
        final float firstStopPositionY = startPosition.y + relativeChangeY;
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        firstStopPositionX, firstStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, relativeChangeX, relativeChangeY, 0f /* hScroll */,
                        0f /* vScroll */)));
        final PointF cursorPosition1 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                firstStopPositionX, cursorPosition1.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                firstStopPositionY, cursorPosition1.y, EPSILON);

        final float secondStopPositionX = firstStopPositionX - relativeChangeX;
        final float secondStopPositionY = firstStopPositionY - relativeChangeY;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_MOVE,
                        secondStopPositionX, secondStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, -relativeChangeX, -relativeChangeY, 0f /* hScroll */,
                        0f /* vScroll */)));
        final PointF cursorPosition2 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                secondStopPositionX, cursorPosition2.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                secondStopPositionY, cursorPosition2.y, EPSILON);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "getAllHighResScrollValues")
    public void sendHighResScrollEventX(float scroll) {
        verifyScrollX(scroll);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "getAllHighResScrollValues")
    public void sendHighResScrollEventY(float scroll) {
        verifyScrollY(scroll);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "getAllScrollValues")
    public void sendScrollEventX(float scroll) {
        verifyScrollX(scroll);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "getAllScrollValues")
    public void sendScrollEventY(float scroll) {
        verifyScrollY(scroll);
    }

    @Test
    public void sendButtonEvent_withoutCreateVirtualDevicePermission_throwsException() {
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualMouse.sendButtonEvent(
                                new VirtualMouseButtonEvent.Builder()
                                        .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                                        .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                                        .build())));
    }

    @Test
    public void sendRelativeEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualMouse.sendRelativeEvent(
                                new VirtualMouseRelativeEvent.Builder()
                                        .setRelativeY(relativeChangeY)
                                        .setRelativeX(relativeChangeX)
                                        .build())));
    }

    @Test
    public void sendScrollEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float moveX = 0f;
        final float moveY = 1f;
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualMouse.sendScrollEvent(
                                new VirtualMouseScrollEvent.Builder()
                                        .setYAxisMovement(moveY)
                                        .setXAxisMovement(moveX)
                                        .build())));
    }

    @Test
    public void testStartingCursorPosition() {
        // The virtual display is 100x100px, running from [0,99]. Half of this is 49.5, and
        // we assume the pointer for a new display begins at the center.
        final int displayWidth = mVirtualDisplay.getDisplay().getMode().getPhysicalWidth();
        final int displayHeight = mVirtualDisplay.getDisplay().getMode().getPhysicalHeight();
        final PointF startPosition = new PointF((displayWidth - 1) / 2f, (displayHeight - 1) / 2f);
        // Trigger a position update without moving the cursor off the starting position.
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_PRESS, startPosition.x, startPosition.y,
                MotionEvent.BUTTON_PRIMARY, 1f /* pressure */);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_DOWN, startPosition.x,
                        startPosition.y, MotionEvent.BUTTON_PRIMARY, 1f /* pressure */),
                buttonPressEvent));

        final PointF position = mVirtualMouse.getCursorPosition();

        assertEquals("Cursor position x differs", startPosition.x, position.x, EPSILON);
        assertEquals("Cursor position y differs", startPosition.y, position.y, EPSILON);
    }

    @Test
    public void createVirtualMouse_nullArguments_throwsEception() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualMouse(null));
    }

    private void verifyScrollX(float scroll) {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(0f)
                .setXAxisMovement(scroll)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_SCROLL,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */,
                        0f /* relativeX */, 0f /* relativeY */, scroll /* hScroll */,
                        0f /* vScroll */)));
    }

    private void verifyScrollY(float scroll) {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(scroll)
                .setXAxisMovement(0f)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_SCROLL,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */,
                        0f /* relativeX */, 0f /* relativeY */, 0f /* hScroll */,
                        scroll /* vScroll */)));
    }

    private static Float[] getAllScrollValues() {
        return new Float[] {
                -1f, 1f,
        };
    }

    private static Float[] getAllHighResScrollValues() {
        return new Float[] {
                0.1f, 0.5f, 1f, -0.1f, -0.5f, -1f,
        };
    }
}
