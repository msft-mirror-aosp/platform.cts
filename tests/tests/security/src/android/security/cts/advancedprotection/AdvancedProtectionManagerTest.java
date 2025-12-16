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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.security.Flags;

import android.util.Log;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnPrimaryUser;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSecondaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class AdvancedProtectionManagerTest extends BaseAdvancedProtectionTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int TIMEOUT_S = 3;

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testEnableProtection() {
        mManager.setAdvancedProtectionEnabled(true);
        assertTrue(mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testDisableProtection() {
        mManager.setAdvancedProtectionEnabled(false);
        assertFalse(mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    @IncludeRunOnSecondaryUser
    public void testEnableProtection_secondaryUser_throws() {
        Assume.assumeFalse(TestApis.users().current().isAdmin());
        assertThrows(SecurityException.class, () -> mManager.setAdvancedProtectionEnabled(true));
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#registerAdvancedProtectionCallback"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testRegisterCallback() throws InterruptedException {
        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        AdvancedProtectionManager.Callback callback =
                enabled -> {
                    if (onRegister.getCount() > 0) {
                        assertTrue(enabled);
                        onRegister.countDown();
                    } else {
                        assertFalse(enabled);
                        onSet.countDown();
                    }
                };

        setAdvancedProtectionEnabled(true);

        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        setAdvancedProtectionEnabled(false);

        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }

        // Cleanup
        mManager.unregisterAdvancedProtectionCallback(callback);
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#unregisterAdvancedProtectionCallback"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testUnregisterCallback() throws InterruptedException {
        // Called once on register
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);

        AdvancedProtectionManager.Callback callback =
                state -> {
                    if (onRegister.getCount() > 0) {
                        onRegister.countDown();
                    } else {
                        onSet.countDown();
                    }
                };

        setAdvancedProtectionEnabled(true);

        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.unregisterAdvancedProtectionCallback(callback);
        // Wait for the callback to be removed. This happens async, and we can't check the state
        // of the callback directly.
        Thread.sleep(TIMEOUT_S * 1000);
        setAdvancedProtectionEnabled(false);

        if (onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback called on set after unregister");
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#registerAdvancedProtectionFeatureCallback"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testRegisterFeatureCallback() throws InterruptedException {
        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        Consumer<List<AdvancedProtectionFeature>> callback =
                features -> {
                    if (onRegister.getCount() > 0) {
                        assertProvisioningMode(
                                features,
                                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                                AdvancedProtectionFeature
                                        .PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                                /* isEnabled= */ true);
                        onRegister.countDown();
                    } else {
                        assertProvisioningMode(
                                features,
                                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                                AdvancedProtectionFeature
                                        .PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                                /* isEnabled= */ false);
                        onSet.countDown();
                    }
                };

        setAdvancedProtectionEnabled(true);
        mManager.updateAdvancedProtectionFeaturesProvisioning(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G}, null);

        // Wait for the feature to be provisioned. This happens async, and we can't check the state
        // of the callback directly.
        Thread.sleep(TIMEOUT_S * 1000);

        mManager.registerAdvancedProtectionFeatureCallback(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G},
                Runnable::run,
                callback);

        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }

        setAdvancedProtectionEnabled(false);

        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }

        // Cleanup
        mManager.unregisterAdvancedProtectionFeatureCallback(callback);
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#unregisterAdvancedProtectionFeatureCallback"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testUnregisterFeatureCallback() throws InterruptedException {
        // Called once on register
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);

        Consumer<List<AdvancedProtectionFeature>> callback =
                features -> {
                    if (onRegister.getCount() > 0) {
                        onRegister.countDown();
                    } else {
                        onSet.countDown();
                    }
                };

        setAdvancedProtectionEnabled(true);

        mManager.registerAdvancedProtectionFeatureCallback(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G},
                Runnable::run,
                callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.unregisterAdvancedProtectionFeatureCallback(callback);
        // Wait for the callback to be removed. This happens async, and we can't check the state
        // of the callback directly.
        Thread.sleep(TIMEOUT_S * 1000);
        setAdvancedProtectionEnabled(false);

        if (onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback called on set after unregister");
        }
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testGetFeatures_notNull() {
        assertNotNull(mManager.getAdvancedProtectionFeatures());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testGetFeatures_withFeatureIds_returnsOnlyRequestedFeatures() {
        List<AdvancedProtectionFeature> features =
                mManager.getAdvancedProtectionFeatures(
                        new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G});

        assertEquals(1, features.size());
        assertEquals(
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G, features.get(0).getId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testGetFeatures_withFeatureIds_throwsExceptionForInvalidFeatureId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mManager.getAdvancedProtectionFeatures(new int[] {-1}));
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_provisioned() {
        mManager.setAdvancedProtectionEnabled(true);
        List<AdvancedProtectionFeature> features =
                mManager.updateAdvancedProtectionFeaturesProvisioning(
                        new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G},
                        null);
        assertProvisioningMode(
                features,
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                /* isEnabled= */ true);

        features = mManager.getAdvancedProtectionFeatures();
        assertProvisioningMode(
                features,
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                /* isEnabled= */ true);
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_deprovisioned() {
        mManager.setAdvancedProtectionEnabled(true);
        List<AdvancedProtectionFeature> features =
                mManager.updateAdvancedProtectionFeaturesProvisioning(
                        new int[] {},
                        new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G});
        assertProvisioningMode(
                features,
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN,
                /* isEnabled= */ false);

        features = mManager.getAdvancedProtectionFeatures();
        assertProvisioningMode(
                features,
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN,
                /* isEnabled= */ false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_deprovisioned_callbackCalled()
            throws InterruptedException {
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        mManager.setAdvancedProtectionEnabled(true);
        mManager.updateAdvancedProtectionFeaturesProvisioning(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G}, null);
        Consumer<List<AdvancedProtectionFeature>> callback =
                features -> {
                    if (onRegister.getCount() > 0) {
                        assertProvisioningMode(
                                features,
                                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                                AdvancedProtectionFeature
                                        .PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                                /* isEnabled= */ true);
                        onRegister.countDown();
                    } else {
                        assertProvisioningMode(
                                features,
                                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                                AdvancedProtectionFeature
                                        .PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN,
                                /* isEnabled= */ false);
                        onSet.countDown();
                    }
                };

        // Wait for the feature to be provisioned. This happens async, and we can't check the
        // state of the feature directly.
        Thread.sleep(TIMEOUT_S * 1000);

        mManager.registerAdvancedProtectionFeatureCallback(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G},
                Runnable::run,
                callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.updateAdvancedProtectionFeaturesProvisioning(
                null, new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G});
        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }

        // Cleanup
        mManager.unregisterAdvancedProtectionFeatureCallback(callback);
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_nullFeatures() {
        List<AdvancedProtectionFeature> features =
                mManager.updateAdvancedProtectionFeaturesProvisioning(null, null);
        assertTrue(features.isEmpty());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_emptyFeatures() {
        List<AdvancedProtectionFeature> features =
                mManager.updateAdvancedProtectionFeaturesProvisioning(new int[] {}, new int[] {});
        assertTrue(features.isEmpty());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    @IncludeRunOnPrimaryUser
    public void testUpdateAdvancedProtectionFeaturesProvisioning_doesntUpdateOtherFeatures() {
        mManager.setAdvancedProtectionEnabled(true);
        mManager.updateAdvancedProtectionFeaturesProvisioning(
                new int[] {AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G}, null);

        mManager.updateAdvancedProtectionFeaturesProvisioning(null, null);

        List<AdvancedProtectionFeature> features = mManager.getAdvancedProtectionFeatures();
        assertProvisioningMode(
                features,
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN,
                /* isEnabled= */ true);
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testSetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.setAdvancedProtectionEnabled(true));

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.setAdvancedProtectionEnabled(true));
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#isAdvancedProtectionEnabled"
            })
    @Test
    public void testGetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.isAdvancedProtectionEnabled());

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    public void testGetFeatures_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.getAdvancedProtectionFeatures());

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.getAdvancedProtectionFeatures());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#updateAdvancedProtectionFeaturesProvisioning"
            })
    @Test
    public void testUpdateAdvancedProtectionFeaturesProvisioning_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () ->
                        mManager.updateAdvancedProtectionFeaturesProvisioning(
                                new int[] {1}, new int[] {2}));
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures"
            })
    @Test
    public void testGetFeatures_withFeatureIds_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () ->
                        mManager.getAdvancedProtectionFeatures(
                                new int[] {
                                    AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G
                                }));
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }

    private void assertProvisioningMode(
            List<AdvancedProtectionFeature> features,
            int featureId,
            int provisioningMode,
            boolean isEnabled) {
        AdvancedProtectionFeature feature =
                features.stream().filter(f -> f.getId() == featureId).findFirst().get();
        assertEquals(featureId, feature.getId());
        assertEquals(provisioningMode, feature.getProvisioningMode());
        assertEquals(isEnabled, feature.isEnabled());
    }
}
