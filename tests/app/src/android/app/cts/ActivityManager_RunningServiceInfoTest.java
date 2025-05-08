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
import android.app.stubs.MockActivity;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ActivityManager_RunningServiceInfoTest {
    private ActivityManager.RunningServiceInfo mRunningServiceInfo;
    private ComponentName mService;
    private static final String PROCESS = "process";

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mRunningServiceInfo = new ActivityManager.RunningServiceInfo();
        mService = new ComponentName(context, MockActivity.class);

        mRunningServiceInfo.service = mService;
        mRunningServiceInfo.pid = 1;
        mRunningServiceInfo.process = PROCESS;
        mRunningServiceInfo.foreground = true;
        mRunningServiceInfo.activeSince = 1L;
        mRunningServiceInfo.started = true;
        mRunningServiceInfo.clientCount = 2;
        mRunningServiceInfo.crashCount = 1;
        mRunningServiceInfo.lastActivityTime = 1L;
        mRunningServiceInfo.restarting = 1L;
    }

    @Test
    public void testConstructor() {
        new ActivityManager.RunningServiceInfo();
    }

    @Test
    public void testDescribeContents() {
        assertThat(mRunningServiceInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() throws Exception {
        Parcel parcel = Parcel.obtain();
        mRunningServiceInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.RunningServiceInfo values =
            ActivityManager.RunningServiceInfo.CREATOR.createFromParcel(parcel);

        assertThat(values.service).isEqualTo(mService);
        assertThat(values.pid).isEqualTo(1);
        assertThat(values.process).isEqualTo(PROCESS);
        assertThat(values.foreground).isTrue();
        assertThat(values.activeSince).isEqualTo(1L);
        assertThat(values.started).isTrue();
        assertThat(values.clientCount).isEqualTo(2);
        assertThat(values.crashCount).isEqualTo(1);
        assertThat(values.lastActivityTime).isEqualTo(1L);
        assertThat(values.restarting).isEqualTo(1L);
    }

    @Test
    public void testReadFromParcel() {
        Parcel parcel = Parcel.obtain();
        mRunningServiceInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.RunningServiceInfo values =
            new ActivityManager.RunningServiceInfo();
        values.readFromParcel(parcel);

        assertThat(values.service).isEqualTo(mService);
        assertThat(values.pid).isEqualTo(1);
        assertThat(values.process).isEqualTo(PROCESS);
        assertThat(values.foreground).isTrue();
        assertThat(values.activeSince).isEqualTo(1L);
        assertThat(values.started).isTrue();
        assertThat(values.clientCount).isEqualTo(2);
        assertThat(values.crashCount).isEqualTo(1);
        assertThat(values.lastActivityTime).isEqualTo(1L);
        assertThat(values.restarting).isEqualTo(1L);
    }
}
