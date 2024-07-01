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

package android.hardware.input.cts.tests;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.companion.virtualdevice.flags.Flags;
import android.hardware.input.VirtualRotaryEncoderScrollEvent;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
@RunWith(AndroidJUnit4.class)
public class VirtualRotaryEncoderScrollEventTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void parcelAndUnparcel_matches() {
        final float scrollAmount = 0.5f;
        final long eventTimeNanos = 5000L;
        final VirtualRotaryEncoderScrollEvent originalEvent =
                new VirtualRotaryEncoderScrollEvent.Builder()
                        .setScrollAmount(scrollAmount)
                        .setEventTimeNanos(eventTimeNanos)
                        .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualRotaryEncoderScrollEvent recreatedEvent =
                VirtualRotaryEncoderScrollEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different scroll amount")
                .that(originalEvent.getScrollAmount())
                .isEqualTo(recreatedEvent.getScrollAmount());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void scrollEvent_scrollAmountOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualRotaryEncoderScrollEvent.Builder().setScrollAmount(1.1f));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualRotaryEncoderScrollEvent.Builder().setScrollAmount(-1.1f));
    }

    @Test
    public void scrollEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualRotaryEncoderScrollEvent.Builder()
                        .setScrollAmount(1.0f)
                        .setEventTimeNanos(-10L));
    }

    @Test
    public void scrollEvent_valid_created() {
        final float scrollAmount = 1.0f;
        final long eventTimeNanos = 5000L;
        final VirtualRotaryEncoderScrollEvent event = new VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(scrollAmount)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect scroll direction")
                .that(event.getScrollAmount())
                .isEqualTo(scrollAmount);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
