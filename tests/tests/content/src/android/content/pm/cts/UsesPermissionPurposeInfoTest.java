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

import android.annotation.StringRes;
import android.annotation.SuppressLint;
import android.content.pm.UsesPermissionPurposeInfo;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class UsesPermissionPurposeInfoTest {
    private static final String MOCK_PERMISSION_NAME = "example.permission";

    private static final Set<String> MOCK_PURPOSES = Set.of("purposeA", "purposeB");

    private static final Set<String> MOCK_GENERAL_PURPOSES = Set.of("purpose1", "purpose2");

    @SuppressLint("ResourceType")
    private static final @StringRes int MOCK_PURPOSE_STRING = 1234;

    @Test
    public void testPurposeInfo() {
        Parcel p = Parcel.obtain();
        // Test constructor
        UsesPermissionPurposeInfo usesPermissionPurposeInfo =
                new UsesPermissionPurposeInfo(
                        MOCK_PERMISSION_NAME,
                        MOCK_PURPOSES,
                        MOCK_GENERAL_PURPOSES,
                        MOCK_PURPOSE_STRING);

        // Test toString, describeContents
        assertNotNull(usesPermissionPurposeInfo.toString());
        assertEquals(0, usesPermissionPurposeInfo.describeContents());

        usesPermissionPurposeInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        UsesPermissionPurposeInfo infoFromParcel =
                UsesPermissionPurposeInfo.CREATOR.createFromParcel(p);
        checkInfoSame(usesPermissionPurposeInfo, infoFromParcel);
        p.recycle();
    }

    private void checkInfoSame(
            UsesPermissionPurposeInfo expected, UsesPermissionPurposeInfo actual) {
        assertEquals(expected.getPermissionName(), actual.getPermissionName());
        assertEquals(expected.getGeneralPurposes(), actual.getGeneralPurposes());
        assertEquals(expected.getPurposeStringResource(), actual.getPurposeStringResource());
    }
}
