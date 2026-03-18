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

import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.Nullable;
import android.app.admin.PolicyIdentifier;
import android.app.admin.flags.Flags;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** CTS tests for ManagedEsimOutgoingTransferPolicy. */
@RunWith(BedsteadJUnit4.class)
@RequireFlagsEnabled({Flags.FLAG_MANAGED_ESIM_OUTGOING_TRANSFER_POLICY})
public final class ManagedEsimOutgoingTransferPolicyTest {

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    /**
     * Tests that setting the eSIM outgoing transfer policy to ALLOWED is correctly applied and
     * retrieved.
     */
    @Test
    @CanSetPolicyTest(policy = ManagedEsimOutgoingTransferPolicyPolicyScopeDevice.class)
    @Postsubmit(reason = "new test")
    public void setAndGetManagedEsimOutgoingTransferPolicy_allowed_returnsAllowed() {
        try {
            setManagedEsimOutgoingTransferPolicy(
                    PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_ALLOWED);

            int policy = getManagedEsimOutgoingTransferPolicy();
            assertThat(policy).isEqualTo(PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_ALLOWED);
        } finally {
            setManagedEsimOutgoingTransferPolicy(null);
        }
    }

    /**
     * Tests that setting the eSIM outgoing transfer policy to DISALLOWED is correctly applied and
     * retrieved.
     */
    @Test
    @CanSetPolicyTest(policy = ManagedEsimOutgoingTransferPolicyPolicyScopeDevice.class)
    @Postsubmit(reason = "new test")
    public void setAndGetManagedEsimOutgoingTransferPolicy_disallowed_returnsDisallowed() {
        try {
            setManagedEsimOutgoingTransferPolicy(
                    PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_DISALLOWED);

            int policy = getManagedEsimOutgoingTransferPolicy();
            assertThat(policy)
                    .isEqualTo(PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_DISALLOWED);
        } finally {
            setManagedEsimOutgoingTransferPolicy(null);
        }
    }

    @Test
    @CanSetPolicyTest(policy = ManagedEsimOutgoingTransferPolicyPolicyScopeDevice.class)
    @Postsubmit(reason = "new test")
    public void isOutgoingTransferAllowedForSubscription_unmanagedSubscription_returnsTrue() {
        try {
            setManagedEsimOutgoingTransferPolicy(
                    PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_DISALLOWED);

            int unmanagedSubId = 12345; // Arbitrary unmanaged subscription ID.

            boolean isAllowed =
                    dpc(sDeviceState)
                            .devicePolicyManager()
                            .isOutgoingTransferAllowedForSubscription(unmanagedSubId);
            assertThat(isAllowed).isTrue();
        } finally {
            setManagedEsimOutgoingTransferPolicy(null);
        }
    }

    private void setManagedEsimOutgoingTransferPolicy(@Nullable Integer policyValue) {
        dpc(sDeviceState)
                .devicePolicyManager()
                .setPolicy_integer(
                        PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_POLICY,
                        POLICY_SCOPE_DEVICE,
                        policyValue);
    }

    private int getManagedEsimOutgoingTransferPolicy() {
        return dpc(sDeviceState)
                .devicePolicyManager()
                .getPolicy_integer(
                        PolicyIdentifier.MANAGED_ESIM_OUTGOING_TRANSFER_POLICY,
                        POLICY_SCOPE_DEVICE);
    }
}
