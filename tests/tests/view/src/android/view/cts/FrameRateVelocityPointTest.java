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

package android.view.cts;

import static com.android.server.display.feature.flags.Flags.FLAG_FRAME_RATE_MAPPING_API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.FrameRateVelocityPoint;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FrameRateVelocityPointTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_FRAME_RATE_MAPPING_API)
    public void testConstructorAndGetters() {
        FrameRateVelocityPoint point = new FrameRateVelocityPoint(60.0f, 100.0f);
        assertEquals(60.0f, point.getFramePerSecond(), 0.0f);
        assertEquals(100.0f, point.getDpPerSecond(), 0.0f);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FRAME_RATE_MAPPING_API)
    public void testEqualsAndHashCode() {
        FrameRateVelocityPoint point1 = new FrameRateVelocityPoint(60.0f, 100.0f);
        FrameRateVelocityPoint point2 = new FrameRateVelocityPoint(60.0f, 100.0f);
        FrameRateVelocityPoint point3 = new FrameRateVelocityPoint(90.0f, 100.0f);
        FrameRateVelocityPoint point4 = new FrameRateVelocityPoint(60.0f, 200.0f);

        assertEquals(point1, point2);
        assertEquals(point1.hashCode(), point2.hashCode());

        assertNotEquals(point1, point3);
        assertNotEquals(point1.hashCode(), point3.hashCode());
        assertNotEquals(point1, point4);
        assertNotEquals(point1.hashCode(), point4.hashCode());
        assertNotEquals(point3, point4);
        assertNotEquals(point3.hashCode(), point4.hashCode());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FRAME_RATE_MAPPING_API)
    public void testParcelable() {
        FrameRateVelocityPoint originalPoint = new FrameRateVelocityPoint(120.0f, 300.0f);
        Parcel parcel = Parcel.obtain();
        originalPoint.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FrameRateVelocityPoint restoredPoint =
                FrameRateVelocityPoint.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalPoint, restoredPoint);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FRAME_RATE_MAPPING_API)
    public void testDescribeContents() {
        FrameRateVelocityPoint point = new FrameRateVelocityPoint(60.0f, 100.0f);
        assertEquals(0, point.describeContents());
    }
}
