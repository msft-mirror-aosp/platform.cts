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

package android.security.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_API)
public class AdvancedProtectionManagerTest {
    private static final int TIMEOUT_S = 2;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private AdvancedProtectionManager mManager;
    private boolean mInitialApmState;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        assumeTrue(shouldTestAdvancedProtection(mInstrumentation.getContext()));
        mManager = (AdvancedProtectionManager) mInstrumentation
                .getContext().getSystemService(Context.ADVANCED_PROTECTION_SERVICE);
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                Manifest.permission.SET_ADVANCED_PROTECTION_MODE);

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
    public void teardown() {
        if (mManager == null) {
            return;
        }
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.SET_ADVANCED_PROTECTION_MODE);
        mManager.setAdvancedProtectionEnabled(mInitialApmState);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnableProtection() {
        mManager.setAdvancedProtectionEnabled(true);
        assertTrue(mManager.isAdvancedProtectionEnabled());
    }

    @Test
    public void testDisableProtection() {
        mManager.setAdvancedProtectionEnabled(false);
        assertFalse(mManager.isAdvancedProtectionEnabled());
    }

    @Test
    public void testRegisterCallback() throws InterruptedException {
        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        AdvancedProtectionManager.Callback callback = enabled -> {
            if (onRegister.getCount() > 0) {
                assertTrue(enabled);
                onRegister.countDown();
            } else {
                assertFalse(enabled);
                onSet.countDown();
            }
        };

        mManager.setAdvancedProtectionEnabled(true);
        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.setAdvancedProtectionEnabled(false);

        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }

        // Cleanup
        mManager.unregisterAdvancedProtectionCallback(callback);
    }

    @Test
    public void testUnregisterCallback() throws InterruptedException {
        // Called once on register
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);

        AdvancedProtectionManager.Callback callback = state -> {
            if (onRegister.getCount() > 0) {
                onRegister.countDown();
            } else {
                onSet.countDown();
            }
        };

        mManager.setAdvancedProtectionEnabled(true);
        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.unregisterAdvancedProtectionCallback(callback);
        mManager.setAdvancedProtectionEnabled(false);

        if (onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback called on set after unregister");
        }
    }

    @Test
    public void testSetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.setAdvancedProtectionEnabled(true));

        mInstrumentation.getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testGetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.isAdvancedProtectionEnabled());

        mInstrumentation.getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.isAdvancedProtectionEnabled());
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }
}
