/*
 * Copyright 2024 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.companion.virtualdevice.flags.Flags;
import android.hardware.input.VirtualRotaryEncoder;
import android.hardware.input.VirtualRotaryEncoderScrollEvent;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualRotaryEncoderTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualRotaryEncoderTestDevice";

    private VirtualRotaryEncoder mVirtualRotary;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualRotary = VirtualInputDeviceCreator.createAndPrepareRotary(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
    }

    @Test
    public void createVirtualRotary_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualRotaryEncoder(null));
    }

    @Test
    public void sendScrollEvent() {
        final float scrollAmount = 1.0f;
        mVirtualRotary.sendScrollEvent(new VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(scrollAmount)
                .build());
        verifyEvents(Collections.singletonList(
                VirtualInputEventCreator.createRotaryEvent(scrollAmount)));
    }
}
