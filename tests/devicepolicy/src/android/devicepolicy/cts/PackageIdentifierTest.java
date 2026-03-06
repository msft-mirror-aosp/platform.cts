/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.admin.flags.Flags;
import android.app.admin.PackageIdentifier;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequireFlagsEnabled({Flags.FLAG_POLICY_STREAMLINING, Flags.FLAG_POLICY_STREAMLINING_TESTS})
@RunWith(BedsteadJUnit4.class)
public final class PackageIdentifierTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String VALID_PACKAGE_NAME = "com.example.app";

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PackageIdentifier#getPackageName")
    public void packageIdentifier_getPackageName_returnsCorrectPackageName() {
        PackageIdentifier packageIdentifier = new PackageIdentifier(VALID_PACKAGE_NAME);

        assertThat(packageIdentifier.getPackageName()).isEqualTo(VALID_PACKAGE_NAME);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PackageIdentifier#PackageIdentifier")
    public void packageIdentifier_nullPackageName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new PackageIdentifier((String) null));
    }
}
