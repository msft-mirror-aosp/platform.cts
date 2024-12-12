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

package android.security.cts.advancedprotection;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.HashMap;
import java.util.List;

public abstract class BaseAdvancedProtectionTest {
    protected final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    protected AdvancedProtectionManager mManager;

    private boolean mInitialApmState;
    private HashMap<Integer, Long> mInitialAllowedNetworks = new HashMap<>();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        assumeTrue(shouldTestAdvancedProtection(mInstrumentation.getContext()));
        mManager = (AdvancedProtectionManager) mInstrumentation
                .getContext().getSystemService(Context.ADVANCED_PROTECTION_SERVICE);

        setupInitialAllowedNetworks();

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);

        mInitialApmState = mManager.isAdvancedProtectionEnabled();
    }

    private static boolean shouldTestAdvancedProtection(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false;
        }
        return true;
    }

    @After
    public void teardown() throws InterruptedException {
        if (mManager == null) {
            return;
        }

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mManager.setAdvancedProtectionEnabled(mInitialApmState);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        Thread.sleep(1000);

        teardownInitialAllowedNetworks();
    }

    private void setupInitialAllowedNetworks() {
        SubscriptionManager subscriptionManager =
                mInstrumentation.getContext().getSystemService(SubscriptionManager.class);

        List<SubscriptionInfo> subscriptions =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        subscriptionManager,
                        (sm) -> sm.getActiveSubscriptionInfoList(),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        for (SubscriptionInfo subscription : subscriptions) {
            int subId = subscription.getSubscriptionId();
            TelephonyManager telephonyManager =
                    mInstrumentation
                            .getContext()
                            .getSystemService(TelephonyManager.class)
                            .createForSubscriptionId(subId);

            long allowedNetworks =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            telephonyManager,
                            (tm) ->
                                    tm.getAllowedNetworkTypesForReason(
                                            TelephonyManager
                                                    .ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G),
                            Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

            mInitialAllowedNetworks.put(subId, allowedNetworks);
        }
    }

    private void teardownInitialAllowedNetworks() {
        for (int subId : mInitialAllowedNetworks.keySet()) {
            long allowedNetworks = mInitialAllowedNetworks.get(subId);
            TelephonyManager telephonyManager =
                    mInstrumentation
                            .getContext()
                            .getSystemService(TelephonyManager.class)
                            .createForSubscriptionId(subId);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    (tm) ->
                            tm.setAllowedNetworkTypesForReason(
                                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                                    allowedNetworks),
                    Manifest.permission.MODIFY_PHONE_STATE);
        }
    }
}
