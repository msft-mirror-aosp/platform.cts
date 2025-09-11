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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.os.Process;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import dalvik.system.PathClassLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48545 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 397438392)
    public void testPocCVE_2025_48545() {
        try {
            // Fetch class loader for 'services.jar'.
            final PathClassLoader classLoader =
                    new PathClassLoader(
                            "/system/framework/services.jar", ClassLoader.getSystemClassLoader());

            // Create an object of 'Injector' which will be passed in constructor of
            // 'AccountManagerService'.
            final Class injectorClass =
                    classLoader.loadClass(
                            "com.android.server.accounts.AccountManagerService$Injector");
            final Constructor injectorConstructor =
                    injectorClass.getDeclaredConstructor(Context.class);
            injectorConstructor.setAccessible(true);
            final Object injectorInstance =
                    injectorConstructor.newInstance(getApplicationContext());

            // Create an object of 'AccountManagerService' and invoke the vulnerable function
            // 'isSystemUid'.
            final boolean isValidUID =
                    runWithShellPermissionIdentity(
                            () -> {
                                final Class accountManagerServiceClass =
                                        classLoader.loadClass(
                                                "com.android.server.accounts."
                                                        + "AccountManagerService");
                                final Constructor accountManagerServiceConstructor =
                                        accountManagerServiceClass.getDeclaredConstructor(
                                                injectorClass);
                                accountManagerServiceConstructor.setAccessible(true);
                                final Object accountManagerServiceInstance =
                                        accountManagerServiceConstructor.newInstance(
                                                injectorInstance);
                                final Method isSystemUidMethod =
                                        accountManagerServiceClass.getDeclaredMethod(
                                                "isSystemUid", int.class);
                                isSystemUidMethod.setAccessible(true);

                                // Call the vulnerable method with 'SDKSandbox' UID.
                                return (boolean)
                                        isSystemUidMethod.invoke(
                                                accountManagerServiceInstance,
                                                Process.FIRST_SDK_SANDBOX_UID);
                            },
                            INTERACT_ACROSS_USERS_FULL,
                            OBSERVE_GRANT_REVOKE_PERMISSIONS);

            // Without fix, 'SDKSandbox' UID is considered as 'system' UID and test fails. With
            // fix, the method returns false and test passes.
            assertWithMessage(
                            "Device is vulnerable to b/397438392 !!, SDKSandbox UID is"
                                    + " considered as system UID.")
                    .that(isValidUID)
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
