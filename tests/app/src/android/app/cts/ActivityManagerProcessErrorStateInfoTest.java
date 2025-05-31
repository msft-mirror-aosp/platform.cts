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

import android.app.ActivityManager;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerProcessErrorStateInfoTest {
    private static final int CONDITION = ActivityManager.ProcessErrorStateInfo.CRASHED;
    private static final String PROCESS_NAME = "processName";
    private static final int PID = 2;
    private static final int UID = 3;
    private static final String TAG = "tag";
    private static final String SHORT_MSG = "shortMsg";
    private static final String LONG_MSG = "longMsg";
    private ActivityManager.ProcessErrorStateInfo mErrorStateInfo;

    @Before
    public void setUp() {
        mErrorStateInfo = new ActivityManager.ProcessErrorStateInfo();
    }

    @Test
    public void testConstructor() {
        new ActivityManager.ProcessErrorStateInfo();
    }

    @Test
    public void testDescribeContents() {
        assertThat(mErrorStateInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() {
        mErrorStateInfo.condition = CONDITION;
        mErrorStateInfo.processName = PROCESS_NAME;
        mErrorStateInfo.pid = PID;
        mErrorStateInfo.uid = UID;
        mErrorStateInfo.tag = TAG;
        mErrorStateInfo.shortMsg = SHORT_MSG;
        mErrorStateInfo.longMsg = LONG_MSG;

        Parcel parcel = Parcel.obtain();
        mErrorStateInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.ProcessErrorStateInfo values =
            ActivityManager.ProcessErrorStateInfo.CREATOR.createFromParcel(parcel);

        assertThat(values.condition).isEqualTo(CONDITION);
        assertThat(values.processName).isEqualTo(PROCESS_NAME);
        assertThat(values.pid).isEqualTo(PID);
        assertThat(values.uid).isEqualTo(UID);
        assertThat(values.tag).isEqualTo(TAG);
        // null?
        assertThat(values.shortMsg).isEqualTo(SHORT_MSG);
        assertThat(values.longMsg).isEqualTo(LONG_MSG);
        assertThat(values.crashData).isNull(); // Deprecated field: always null
    }

    @Test
    public void testReadFromParcel() {
        mErrorStateInfo.condition = CONDITION;
        mErrorStateInfo.processName = PROCESS_NAME;
        mErrorStateInfo.pid = PID;
        mErrorStateInfo.uid = UID;
        mErrorStateInfo.tag = TAG;
        mErrorStateInfo.shortMsg = SHORT_MSG;
        mErrorStateInfo.longMsg = LONG_MSG;

        Parcel parcel = Parcel.obtain();
        mErrorStateInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.ProcessErrorStateInfo values = new ActivityManager.ProcessErrorStateInfo();
        values.readFromParcel(parcel);

        assertThat(values.condition).isEqualTo(CONDITION);
        assertThat(values.processName).isEqualTo(PROCESS_NAME);
        assertThat(values.pid).isEqualTo(PID);
        assertThat(values.uid).isEqualTo(UID);
        assertThat(values.tag).isEqualTo(TAG);
        assertThat(values.shortMsg).isEqualTo(SHORT_MSG);
        assertThat(values.longMsg).isEqualTo(LONG_MSG);
        assertThat(values.crashData).isNull(); // Deprecated field: always null
    }
}
