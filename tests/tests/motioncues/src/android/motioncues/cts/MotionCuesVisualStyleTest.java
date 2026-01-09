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

package android.motioncues.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.motioncues.MotionCuesVisualStyle;
import android.graphics.Color;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.compatibility.common.util.ApiTest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MotionCuesVisualStyleTest {

    private static final int TEST_COLOR = Color.RED;
    private static final int TEST_SHAPE_RES = R.drawable.ic_test;

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesVisualStyle#MotionCuesVisualStyle"})
    public void testConstructorAndGetters() {
        MotionCuesVisualStyle style = new MotionCuesVisualStyle(TEST_COLOR, TEST_SHAPE_RES);

        assertThat(style.getColor()).isEqualTo(TEST_COLOR);
        assertThat(style.getShapeRes()).isEqualTo(TEST_SHAPE_RES);
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesVisualStyle#MotionCuesVisualStyle"})
    public void testCopyConstructor() {
        MotionCuesVisualStyle original = new MotionCuesVisualStyle(TEST_COLOR, TEST_SHAPE_RES);

        MotionCuesVisualStyle copy = new MotionCuesVisualStyle(original);

        assertThat(copy.getColor()).isEqualTo(TEST_COLOR);
        assertThat(copy.getShapeRes()).isEqualTo(TEST_SHAPE_RES);
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesVisualStyle#CREATOR"})
    public void testParcelable() {
        MotionCuesVisualStyle original = new MotionCuesVisualStyle(TEST_COLOR, TEST_SHAPE_RES);
        Parcel parcel = Parcel.obtain();

        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            MotionCuesVisualStyle createdFromParcel =
                    MotionCuesVisualStyle.CREATOR.createFromParcel(parcel);

            assertThat(createdFromParcel.getColor()).isEqualTo(original.getColor());
            assertThat(createdFromParcel.getShapeRes()).isEqualTo(original.getShapeRes());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesVisualStyle#describeContents"})
    public void testDescribeContents() {
        MotionCuesVisualStyle style = new MotionCuesVisualStyle(TEST_COLOR, TEST_SHAPE_RES);

        assertThat(style.describeContents()).isEqualTo(0);
    }

    @Test
    @ApiTest(apis = {"android.app.motioncues.MotionCuesVisualStyle#CREATOR"})
    public void testNewArray() {
        MotionCuesVisualStyle[] array = MotionCuesVisualStyle.CREATOR.newArray(10);

        assertThat(array).hasLength(10);
    }
}
