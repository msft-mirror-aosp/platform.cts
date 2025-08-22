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
package android.security.cts;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BinderAppendFromTest extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 399155883)
    public void testBinderAppendFromTest() {
        Parcel p1 = Parcel.obtain();
        p1.writeInt(1);
        p1.writeInt(1);
        Parcel p2 = Parcel.obtain();
        p2.setDataCapacity(8);
        p2.setDataPosition(100000);

        assertThrows(
                Exception.class,
                () -> {
                    p2.appendFrom(p1, 0, 8);
                });
    }

    @Test
    @AsbSecurityTest(cveBugId = 438098181)
    public void testBinderAppendFromBadSize() {
        final int numInt32P1 = 32;
        final int numInt32P2 = 10;

        Parcel p1 = Parcel.obtain();
        for (int i = 0; i < numInt32P1; i++) {
            p1.writeInt(i);
        }

        Parcel p2 = Parcel.obtain();
        for (int i = 0; i < numInt32P2; i++) {
            p2.writeInt(i);
        }

        p1.setDataPosition(0);
        p1.appendFrom(p2, 0, numInt32P2 * 4);

        p1.setDataPosition(128);
        assertThat(p1.readInt(), is(equalTo(0)));
    }
}
