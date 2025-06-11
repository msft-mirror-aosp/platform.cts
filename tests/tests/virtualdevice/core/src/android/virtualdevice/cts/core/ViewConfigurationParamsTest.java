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

package android.virtualdevice.cts.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtualdevice.flags.Flags;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VIEWCONFIGURATION_APIS)
public class ViewConfigurationParamsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        final Duration tapTimeoutDuration = Duration.ofMillis(10L);
        final Duration doubleTapTimeoutDuration = Duration.ofMillis(20L);
        final Duration doubleTapMinTimeDuration = Duration.ofMillis(30L);
        final float scrollFriction = 50f;
        final int touchSlopPixels = 40;
        final int maximumFlingVelocityPixelsPerSecond = 90;
        final int minimumFlingVelocityPixelsPerSecond = 70;
        final Duration longPressTimeoutDuration = Duration.ofMillis(110L);
        final Duration multiPressTimeoutDuration = Duration.ofMillis(120L);
        ViewConfigurationParams originalParams =
                new ViewConfigurationParams.Builder()
                        .setTapTimeoutDuration(tapTimeoutDuration)
                        .setDoubleTapTimeoutDuration(doubleTapTimeoutDuration)
                        .setDoubleTapMinTimeDuration(doubleTapMinTimeDuration)
                        .setScrollFriction(scrollFriction)
                        .setMinimumFlingVelocityPixelsPerSecond(minimumFlingVelocityPixelsPerSecond)
                        .setMaximumFlingVelocityPixelsPerSecond(maximumFlingVelocityPixelsPerSecond)
                        .setTouchSlopPixels(touchSlopPixels)
                        .setLongPressTimeoutDuration(longPressTimeoutDuration)
                        .setMultiPressTimeoutDuration(multiPressTimeoutDuration)
                        .build();

        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ViewConfigurationParams viewConfigurationParams =
                ViewConfigurationParams.CREATOR.createFromParcel(parcel);
        assertThat(viewConfigurationParams).isEqualTo(originalParams);
        assertThat(viewConfigurationParams.getTapTimeoutDuration()).isEqualTo(tapTimeoutDuration);
        assertThat(viewConfigurationParams.getDoubleTapTimeoutDuration())
                .isEqualTo(doubleTapTimeoutDuration);
        assertThat(viewConfigurationParams.getDoubleTapMinTimeDuration())
                .isEqualTo(doubleTapMinTimeDuration);
        assertThat(viewConfigurationParams.getScrollFriction()).isEqualTo(scrollFriction);
        assertThat(viewConfigurationParams.getTouchSlopPixels()).isEqualTo(touchSlopPixels);
        assertThat(viewConfigurationParams.getMinimumFlingVelocityPixelsPerSecond())
                .isEqualTo(minimumFlingVelocityPixelsPerSecond);
        assertThat(viewConfigurationParams.getMaximumFlingVelocityPixelsPerSecond())
                .isEqualTo(maximumFlingVelocityPixelsPerSecond);
        assertThat(viewConfigurationParams.getLongPressTimeoutDuration())
                .isEqualTo(longPressTimeoutDuration);
        assertThat(viewConfigurationParams.getMultiPressTimeoutDuration())
                .isEqualTo(multiPressTimeoutDuration);
    }

    @Test
    public void noParametersSet_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ViewConfigurationParams.Builder().build());
    }

    @Test
    public void nullTapTimeoutDuration_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> new ViewConfigurationParams.Builder().setTapTimeoutDuration(null).build());
    }

    @Test
    public void negativeTapTimeoutDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setTapTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeTapTimeoutDuration_throwsException() {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setTapTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullDoubleTapTimeoutDuration_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(null)
                                .build());
    }

    @Test
    public void negativeDoubleTapTimeoutDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeDoubleTapTimeoutDuration_throwsException() {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullDoubleTapMinTimeDuration_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(null)
                                .build());
    }

    @Test
    public void negativeDoubleTapMinTimeDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeDoubleTapMinTimeDuration_throwsException() {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullLongPressTimeoutDuration_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setLongPressTimeoutDuration(null)
                                .build());
    }

    @Test
    public void negativeLongPressTimeoutDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setLongPressTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeLongPressTimeoutDuration_throwsException() {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setLongPressTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullMultiPressTimeoutDuration_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMultiPressTimeoutDuration(null)
                                .build());
    }

    @Test
    public void negativeMultiPressTimeoutDuration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMultiPressTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeMultiPressTimeoutDuration_throwsException() {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMultiPressTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void negativeTouchSlopPixels_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ViewConfigurationParams.Builder().setTouchSlopPixels(-10).build());
    }

    @Test
    public void negativeMinimumFlingVelocityPixelsPerSecond_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityPixelsPerSecond(-10)
                                .build());
    }

    @Test
    public void negativeMaximumFlingVelocityPixelsPerSecond_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMaximumFlingVelocityPixelsPerSecond(-10)
                                .build());
    }

    @Test
    public void minimumFlingVelocityGreaterThanMaximumFlingVelocity_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityPixelsPerSecond(200)
                                .setMaximumFlingVelocityPixelsPerSecond(100)
                                .build());
    }
}
