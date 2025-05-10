/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.app.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Parcel;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerMemoryInfoTest {
    private ActivityManager.MemoryInfo mMemory;

    @Before
    public void setUp() throws Exception {
        mMemory = new ActivityManager.MemoryInfo();
    }

    @Test
    public void testDescribeContents() {
        assertThat(mMemory.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() throws Exception {
        final long advertisedMem = 200000L;
        mMemory.advertisedMem = advertisedMem;
        final long availMem = 1000L;
        mMemory.availMem = availMem;
        final long threshold = 500L;
        mMemory.threshold = threshold;
        final boolean lowMemory = true;
        mMemory.lowMemory = lowMemory;
        Parcel parcel = Parcel.obtain();
        mMemory.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.MemoryInfo values =
            ActivityManager.MemoryInfo.CREATOR.createFromParcel(parcel);
        assertThat(values.advertisedMem).isEqualTo(advertisedMem);
        assertThat(values.availMem).isEqualTo(availMem);
        assertThat(values.threshold).isEqualTo(threshold);
        assertThat(values.lowMemory).isEqualTo(lowMemory);

        // test null condition.
        try {
            mMemory.writeToParcel(null, 0);
            assertWithMessage(
                            "writeToParcel should throw out NullPointerException when Parcel is"
                                    + " null")
                    .fail();
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testReadFromParcel() {
        final long advertisedMem = 200000L;
        mMemory.advertisedMem = advertisedMem;
        final long availMem = 1000L;
        mMemory.availMem = availMem;
        final long threshold = 500L;
        mMemory.threshold = threshold;
        final boolean lowMemory = true;
        mMemory.lowMemory = lowMemory;
        Parcel parcel = Parcel.obtain();
        mMemory.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.MemoryInfo result = new ActivityManager.MemoryInfo();
        result.readFromParcel(parcel);
        assertThat(result.advertisedMem).isEqualTo(advertisedMem);
        assertThat(result.availMem).isEqualTo(availMem);
        assertThat(result.threshold).isEqualTo(threshold);
        assertThat(result.lowMemory).isEqualTo(lowMemory);

        // test null condition.
        result = new ActivityManager.MemoryInfo();
        try {
            result.readFromParcel(null);
            assertWithMessage(
                            "readFromParcel should throw out NullPointerException when Parcel is"
                                    + " null")
                    .fail();
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testGetProcessMemoryInfo() {
        // PID == 1 is the init process.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Debug.MemoryInfo[] result =
                context.getSystemService(ActivityManager.class)
                        .getProcessMemoryInfo(new int[] {1, Process.myPid(), 1});
        assertThat(result).hasLength(3);
        isEmpty(result[0]);
        isEmpty(result[2]);
        isNotEmpty(result[1]);
    }

    private static void isEmpty(Debug.MemoryInfo mi) {
        assertThat(mi.dalvikPss).isEqualTo(0);
        assertThat(mi.nativePss).isEqualTo(0);
    }

    private static void isNotEmpty(Debug.MemoryInfo mi) {
        assertThat(mi.dalvikPss).isGreaterThan(0);
        assertThat(mi.nativePss).isGreaterThan(0);
    }
}
