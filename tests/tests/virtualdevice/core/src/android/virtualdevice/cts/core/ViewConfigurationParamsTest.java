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
    public void parcelable_shouldRecreateSuccessfully() throws Exception {
        final Duration tapTimeoutDuration = Duration.ofMillis(10L);
        final Duration doubleTapTimeoutDuration = Duration.ofMillis(20L);
        final Duration doubleTapMinTimeDuration = Duration.ofMillis(30L);
        final float scrollFriction = 50f;
        final float touchSlopDp = 40f;
        final float maximumFlingVelocityDpPerSecond = 90f;
        final float minimumFlingVelocityDpPerSecond = 70f;
        ViewConfigurationParams originalParams =
                new ViewConfigurationParams.Builder()
                        .setTapTimeoutDuration(tapTimeoutDuration)
                        .setDoubleTapTimeoutDuration(doubleTapTimeoutDuration)
                        .setDoubleTapMinTimeDuration(doubleTapMinTimeDuration)
                        .setScrollFriction(scrollFriction)
                        .setMinimumFlingVelocityDpPerSecond(minimumFlingVelocityDpPerSecond)
                        .setMaximumFlingVelocityDpPerSecond(maximumFlingVelocityDpPerSecond)
                        .setTouchSlopDp(touchSlopDp)
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
        assertThat(viewConfigurationParams.getTouchSlopDp()).isEqualTo(touchSlopDp);
        assertThat(viewConfigurationParams.getMinimumFlingVelocityDpPerSecond())
                .isEqualTo(minimumFlingVelocityDpPerSecond);
        assertThat(viewConfigurationParams.getMaximumFlingVelocityDpPerSecond())
                .isEqualTo(maximumFlingVelocityDpPerSecond);
    }

    @Test
    public void noParametersSet_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ViewConfigurationParams.Builder().build());
    }

    @Test
    public void nullTapTimeoutDuration_throwsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> new ViewConfigurationParams.Builder().setTapTimeoutDuration(null).build());
    }

    @Test
    public void negativeTapTimeoutDuration_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setTapTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeTapTimeoutDuration_throwsException() throws Exception {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setTapTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullDoubleTapTimeoutDuration_throwsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(null)
                                .build());
    }

    @Test
    public void negativeDoubleTapTimeoutDuration_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeDoubleTapTimeoutDuration_throwsException() throws Exception {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapTimeoutDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void nullDoubleTapMinTimeDuration_throwsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(null)
                                .build());
    }

    @Test
    public void negativeDoubleTapMinTimeDuration_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(Duration.ofMillis(-10L))
                                .build());
    }

    @Test
    public void tooLargeDoubleTapMinTimeDuration_throwsException() throws Exception {
        long largeValue = (long) Integer.MAX_VALUE + 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setDoubleTapMinTimeDuration(Duration.ofMillis(largeValue))
                                .build());
    }

    @Test
    public void negativeTouchSlopDp_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ViewConfigurationParams.Builder().setTouchSlopDp(-10f).build());
    }

    @Test
    public void negativeMinimumFlingVelocityDpPerSecond_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityDpPerSecond(-10f)
                                .build());
    }

    @Test
    public void negativeMaximumFlingVelocityDpPerSecond_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMaximumFlingVelocityDpPerSecond(-10f)
                                .build());
    }

    @Test
    public void minimumFlingVelocityGreaterThanMaximumFlingVelocity_throwsException()
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ViewConfigurationParams.Builder()
                                .setMinimumFlingVelocityDpPerSecond(200f)
                                .setMaximumFlingVelocityDpPerSecond(100f)
                                .build());
    }
}
