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

package android.devicepolicy.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.flags.Flags;
import android.os.Build;

import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.flags.annotations.RequireFlagsDisabled;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class IsDeviceManagedTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    public void isDeviceManaged_deviceOwnerManaging_returnsTrue() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isDeviceManaged()).isTrue();
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled(Flags.FLAG_MANAGED_DEVICE_DEFINITION_EXTENDED)
    @RequireRunOnWorkProfile(
            isOrganizationOwned = true,
            dpc =
                    @Query(
                            targetSdkVersion =
                                    @IntegerQuery(
                                            isGreaterThanOrEqualTo =
                                                    Build.VERSION_CODES.CINNAMON_BUN)))
    public void isDeviceManaged_copeProfileOwnerManaging_flagOn_recentSDK_returnsTrue() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isDeviceManaged()).isTrue();
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireFlagsDisabled(Flags.FLAG_MANAGED_DEVICE_DEFINITION_EXTENDED)
    @RequireRunOnWorkProfile(isOrganizationOwned = true)
    public void isDeviceManaged_copeProfileOwnerManaging_flagOff_returnsFalse() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isDeviceManaged()).isFalse();
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled(Flags.FLAG_MANAGED_DEVICE_DEFINITION_EXTENDED)
    @RequireRunOnWorkProfile(
            isOrganizationOwned = true,
            dpc =
                    @Query(
                            targetSdkVersion =
                                    @IntegerQuery(isLessThan = Build.VERSION_CODES.CINNAMON_BUN)))
    public void isDeviceManaged_copeProfileOwnerManaging_flagOn_olderSDK_returnsFalse() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isDeviceManaged()).isFalse();
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    public void isDeviceManaged_byodProfileOwnerManaging_returnsFalse() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isDeviceManaged()).isFalse();
    }
}
