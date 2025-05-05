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
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class ActivityManager_RunningAppProcessInfoTest {

    @Test
    public void testRunningAppProcessInfo_constructor() {
        new RunningAppProcessInfo();
        new RunningAppProcessInfo("test", 100, new String[]{"com.android", "com.android.test"});
    }

    @Test
    public void testRunningAppProcessInfo_parcel() {
        final ActivityManager am =
                (ActivityManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
        final RunningAppProcessInfo rap = list.get(0);

        // test describeContents function
        assertThat(rap.describeContents()).isEqualTo(0);
        final Parcel p = Parcel.obtain();

        // test writeToParcel function
        rap.writeToParcel(p, 0);

        // test readFromParcel function
        final RunningAppProcessInfo r = new RunningAppProcessInfo();
        p.setDataPosition(0);
        r.readFromParcel(p);

        assertThat(r.pid).isEqualTo(rap.pid);
        assertThat(r.processName).isEqualTo(rap.processName);
        assertThat(r.pkgList.length).isEqualTo(rap.pkgList.length);

        for (int i = 0; i < rap.pkgList.length; i++) {
            assertThat(r.pkgList[i]).isEqualTo(rap.pkgList[i]);
        }
    }
}
