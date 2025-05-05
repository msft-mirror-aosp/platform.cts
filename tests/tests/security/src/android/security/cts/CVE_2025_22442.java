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

package android.security.cts;

import static android.os.UserManager.DISALLOW_BLUETOOTH_SHARING;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.DexClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_22442 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 382064697)
    public void testPocCVE_2025_22442() {
        try {
            // Load UserRestrictionsUtils class from services.jar
            final DexClassLoader dexClassLoader =
                    new DexClassLoader(
                            "/system/framework/services.jar",
                            null /* optimizedDirectory */,
                            null /* librarySearchPath */,
                            DexClassLoader.getSystemClassLoader());
            final Class<?> userRestrictionsUtilsClass =
                    dexClassLoader.loadClass("com.android.server.pm.UserRestrictionsUtils");

            // Invoke UserRestrictionsUtils#getDefaultEnabledForManagedProfiles()
            Method method =
                    userRestrictionsUtilsClass.getDeclaredMethod(
                            "getDefaultEnabledForManagedProfiles");
            method.setAccessible(true);
            Set<String> defaultRestrictions = (Set<String>) method.invoke(null /* class object */);

            // Assumption fail if 'DISALLOW_BLUETOOTH_SHARING' is not a default restrictions.
            // 'DISALLOW_BLUETOOTH_SHARING' should be present in the list regardless of the fix.
            // This check ensures that the list of restrictions is fetched properly.
            assume().that(defaultRestrictions.contains(DISALLOW_BLUETOOTH_SHARING)).isTrue();

            // Fail the test if 'DISALLOW_DEBUGGING_FEATURES' is not a default restrictions
            assertWithMessage(
                            "Device is vulnerable to b/382064697. Managed users can use debugging"
                                + " feature by default")
                    .that(defaultRestrictions.contains(DISALLOW_DEBUGGING_FEATURES))
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
