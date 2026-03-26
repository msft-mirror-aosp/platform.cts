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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class BaseAdvancedProtectionTest {
    private static final int TIMEOUT_S = 1;
    protected final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    protected AdvancedProtectionManager mManager;

    private boolean mInitialApmState;

    @Before
    public void setup() {
        assumeTrue(shouldTestAdvancedProtection(mInstrumentation.getContext()));
        mManager =
                (AdvancedProtectionManager)
                        mInstrumentation
                                .getContext()
                                .getSystemService(Context.ADVANCED_PROTECTION_SERVICE);

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_DEVICE_POLICY_MTE);

        mInitialApmState = mManager.isAdvancedProtectionEnabled();
        disableUsbDataProtection();
    }

    private static void disableUsbDataProtection() {
        SystemUtil.runShellCommand("cmd advanced_protection set-usb-data-protection-enabled false");
        String result =
                SystemUtil.runShellCommand(
                        "cmd advanced_protection is-usb-data-protection-enabled");
        assertTrue(result != null && result.contains("false"));
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

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        setAdvancedProtectionEnabled(mInitialApmState);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    protected void setAdvancedProtectionEnabled(boolean enabled) throws InterruptedException {
        if (enabled == mManager.isAdvancedProtectionEnabled()) {
            return;
        }

        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        AdvancedProtectionManager.Callback callback =
                bool -> {
                    if (onRegister.getCount() > 0) {
                        onRegister.countDown();
                    } else {
                        onSet.countDown();
                    }
                };
        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        // So it can be called by any user
        SystemUtil.runShellCommand("cmd advanced_protection set-protection-enabled " + enabled);
        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }
        mManager.unregisterAdvancedProtectionCallback(callback);
    }

    protected void setFeatureProvisioned(boolean provisioned, int featureId)
            throws InterruptedException {
        boolean isCurrentlyProvisioned =
                mManager.getAdvancedProtectionFeatures(new int[] {featureId})
                        .get(0)
                        .isProvisioned();
        if (provisioned == isCurrentlyProvisioned) {
            return;
        }

        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        Consumer<List<AdvancedProtectionFeature>> callback =
                features -> {
                    for (AdvancedProtectionFeature feature : features) {
                        if (feature.getId() == featureId) {
                            if (onRegister.getCount() > 0) {
                                onRegister.countDown();
                            } else {
                                onSet.countDown();
                            }
                            break;
                        }
                    }
                };
        mManager.registerAdvancedProtectionFeatureCallback(
                new int[] {featureId}, Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        // So it can be called by any user
        String cmd = provisioned ? "set-feature-provisioned" : "set-feature-deprovisioned";
        SystemUtil.runShellCommand("cmd advanced_protection " + cmd + " " + featureId);
        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }
        mManager.unregisterAdvancedProtectionFeatureCallback(callback);
    }

    protected void removeAdbProvisioning(int featureId) throws InterruptedException {
        int provisioningMode =
                mManager.getAdvancedProtectionFeatures(new int[] {featureId})
                        .get(0)
                        .getProvisioningMode();
        if (provisioningMode != AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB
                && provisioningMode
                        != AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB) {
            return;
        }
        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        Consumer<List<AdvancedProtectionFeature>> callback =
                features -> {
                    for (AdvancedProtectionFeature feature : features) {
                        if (feature.getId() == featureId) {
                            if (onRegister.getCount() > 0) {
                                onRegister.countDown();
                            } else {
                                onSet.countDown();
                            }
                            break;
                        }
                    }
                };
        mManager.registerAdvancedProtectionFeatureCallback(
                new int[] {featureId}, Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        // So it can be called by any user
        SystemUtil.runShellCommand(
                "cmd advanced_protection remove-feature-provisioning " + featureId);
        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }
        mManager.unregisterAdvancedProtectionFeatureCallback(callback);
    }
}
