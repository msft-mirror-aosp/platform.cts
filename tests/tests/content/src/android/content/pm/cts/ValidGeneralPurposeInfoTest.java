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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.pm.ValidGeneralPurposeInfo;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class ValidGeneralPurposeInfoTest {
    private static final String MOCK_PURPOSE_NAME = "appFunctionality";
    private static final int MOCK_MAX_TARGET_SDK = 38;

    @Test
    public void testPurposeInfo() {
        Parcel p = Parcel.obtain();
        // Test constructor
        ValidGeneralPurposeInfo validGeneralPurposeInfo =
                new ValidGeneralPurposeInfo(MOCK_PURPOSE_NAME, MOCK_MAX_TARGET_SDK);

        // Test toString, describeContents
        assertNotNull(validGeneralPurposeInfo.toString());
        assertEquals(0, validGeneralPurposeInfo.describeContents());

        validGeneralPurposeInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        ValidGeneralPurposeInfo infoFromParcel =
                ValidGeneralPurposeInfo.CREATOR.createFromParcel(p);
        checkInfoSame(validGeneralPurposeInfo, infoFromParcel);
        p.recycle();
    }

    private void checkInfoSame(ValidGeneralPurposeInfo expected, ValidGeneralPurposeInfo actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getMaxTargetSdkVersion(), actual.getMaxTargetSdkVersion());
    }
}
