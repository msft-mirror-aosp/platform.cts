/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.net.thread.ThreadNetworkException.ERROR_FAILED_PRECONDITION;
import static android.os.UserManager.DISALLOW_THREAD_NETWORK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.content.Context;
import android.net.thread.ThreadNetworkController;
import android.net.thread.ThreadNetworkException;
import android.net.thread.ThreadNetworkManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowThreadNetwork;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.net.thread.platform.flags.Flags;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
// If the device doesn't support Thread then as long as the user restriction doesn't throw an
// exception when setting - we can assume it's fine
@RequireFeature("android.hardware.thread_network")
@RequiresFlagsEnabled(Flags.FLAG_THREAD_USER_RESTRICTION_ENABLED)
public final class ThreadNetworkTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ThreadNetworkManager sThreadNetworkManager =
            sContext.getSystemService(ThreadNetworkManager.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @CannotSetPolicyTest(policy = DisallowThreadNetwork.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_THREAD_NETWORK")
    public void setUserRestriction_disallowThreadNetwork_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_THREAD_NETWORK));
    }

    @PolicyAppliesTest(policy = DisallowThreadNetwork.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_THREAD_NETWORK")
    public void setUserRestriction_disallowThreadNetwork_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_THREAD_NETWORK);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_THREAD_NETWORK))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_THREAD_NETWORK);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowThreadNetwork.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_THREAD_NETWORK")
    public void setUserRestriction_disallowThreadNetwork_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_THREAD_NETWORK);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_THREAD_NETWORK))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_THREAD_NETWORK);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_THREAD_ENABLED_PLATFORM)
    @EnsureHasPermission(Manifest.permission.THREAD_NETWORK_PRIVILEGED)
    @EnsureHasUserRestriction(DISALLOW_THREAD_NETWORK)
    @Postsubmit(reason = "new test")
    @Test
    @ApiTest(apis = "android.os.UserManager#DISALLOW_THREAD_NETWORK")
    public void enableThread_disallowThreadNetworkIsSet_failWithFailedPrecondition()
            throws Exception {
        ThreadNetworkController controller =
                sThreadNetworkManager.getAllThreadNetworkControllers().get(0);
        CompletableFuture<Boolean> setEnabledFuture = new CompletableFuture<>();

        controller.setEnabled(
                true, sContext.getMainExecutor(), newOutcomeReceiver(setEnabledFuture));

        ExecutionException thrown = assertThrows(
                ExecutionException.class, () -> setEnabledFuture.get(1, TimeUnit.SECONDS));
        ThreadNetworkException cause = (ThreadNetworkException) thrown.getCause();
        assertThat(cause.getErrorCode()).isEqualTo(ERROR_FAILED_PRECONDITION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_THREAD_ENABLED_PLATFORM)
    @EnsureHasPermission(Manifest.permission.THREAD_NETWORK_PRIVILEGED)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_THREAD_NETWORK)
    @Postsubmit(reason = "new test")
    @Test
    @ApiTest(apis = "android.os.UserManager#DISALLOW_THREAD_NETWORK")
    public void enableThread_disallowThreadIsNotSet_success() throws Exception {
        ThreadNetworkController controller =
                sThreadNetworkManager.getAllThreadNetworkControllers().get(0);
        CompletableFuture<Boolean> setEnabledFuture = new CompletableFuture<>();

        controller.setEnabled(
                true, sContext.getMainExecutor(), newOutcomeReceiver(setEnabledFuture));

        assertThat(setEnabledFuture.get(1, TimeUnit.SECONDS)).isTrue();
    }

    // TODO(b/328393183): add the Thread API call to bedstead when there is another use case
    /**
     * Creates a {@link OutcomeReceiver} which sets the {@code future} to {@code true} when the
     * receiver is invoked with a result, or fails the {@code future} with a {@link
     * ThreadNetworkException} when the receiver is invoked with an error.
     */
    private static final OutcomeReceiver<Void, ThreadNetworkException> newOutcomeReceiver(
            CompletableFuture<Boolean> future) {
        return new OutcomeReceiver<Void, ThreadNetworkException>() {
                @Override
                public void onResult(Void v) {
                    future.complete(true);
                }
                @Override
                public void onError(ThreadNetworkException exp) {
                    future.completeExceptionally(exp);
                }
        };
    }
}
