/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.bedstead.nene.utils.Assert.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.flags.Flags;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireTelephonySupport;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.EmbeddedSubscription;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@RequireTelephonySupport
public final class EmbeddedSubscriptionsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // TODO(b/325267476): Figure out how to test the download operation as that requires
    //                    contacting a server

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#getSubscriptionIds"
    })
    @CanSetPolicyTest(policy = EmbeddedSubscription.class)
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled(Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    @Test
    public void getSubscriptionIds_initiallyEmpty() {
        Set<Integer> managedSubscriptions =
                sDeviceState.dpc().devicePolicyManager().getSubscriptionIds();
        assertThat(managedSubscriptions).isEmpty();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getSubscriptionIds")
    @CannotSetPolicyTest(policy = EmbeddedSubscription.class)
    @RequireFlagsEnabled(Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    @Postsubmit(reason = "new test")
    @Test
    public void getSubscriptionIds_noPermission_throws() throws Exception {
        assertThrows(SecurityException.class, () -> sDeviceState
                .dpc()
                .devicePolicyManager()
                .getSubscriptionIds());
    }
}
