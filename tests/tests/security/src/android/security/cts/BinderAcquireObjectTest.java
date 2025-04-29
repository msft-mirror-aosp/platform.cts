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

import static org.junit.Assert.assertThrows;

import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.FileDescriptor;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BinderAcquireObjectTest extends StsExtraBusinessLogicTestCase {
    @Test
    @AsbSecurityTest(cveBugId = 402319736)
    public void testBinderAcquireObjectTest() {
        Parcel p1 = Parcel.obtain();
        FileDescriptor fIn = FileDescriptor.in;
        p1.writeFileDescriptor(fIn);
        Parcel p2 = Parcel.obtain();
        p2.writeInt(8);

        p1.setDataPosition(8);

        assertThrows(
                Exception.class,
                () -> {
                    p1.appendFrom(p2, 0, p2.dataSize());
                });
    }
}
